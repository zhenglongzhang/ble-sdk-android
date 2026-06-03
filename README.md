# Znhaas BLE SDK

一个面向 `znhaas` 设备的 Android BLE SDK 和 Demo。

当前版本已经按业务场景收敛为专用实现：

- 只扫描蓝牙名称前缀为 `znhaas` 的 BLE 设备
- 扫描结果主显示名只返回后缀编号
  - 例如：`znhaas_23070401` -> `23070401`
- 固定使用 znhaas Service
  - Service UUID: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
  - Write UUID: `6E400003-B5A3-F393-E0A9-E50E24DCCA9E`
  - Reply UUID: `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`
- 连接设备并完成服务发现后，SDK 会自动请求 MTU `517`
- 设备业务 ACK 优先使用 Notify/Indicate 监听，ACK 格式以 `V1|ACK|...` 开头
- 如果 Reply 特征只能 Read，Demo 会在写入成功后读取一次作为诊断兜底；读到的 `RECORD|SUPPORTED` 属于能力说明，不代表本次命令 ACK
- 按照《蓝牙控制.md》封装 5 个控制动作
- 发送命令时不追加扩展字段
- 提供 `ZnhaasBleJsBridge`，支持客户 H5 通过 WebView JSBridge 操作蓝牙

## 工程结构

- `ble-sdk`
  - SDK 库模块
- `app`
  - WebView + 本地 H5 Demo

## 5 个控制动作

协议统一格式：

```text
V1|RECORD|ACTION|REQUEST_ID|TIMESTAMP
```

当前封装的 5 个动作如下：

1. 开始录制：`ACTION = 1`
2. 停止录制：`ACTION = 0`
3. 查询状态：`ACTION = 2`
4. 禁止视频物理按键：`ACTION = 3`
5. 启用视频物理按键：`ACTION = 4`

示例：

```text
V1|RECORD|1|req-1715155200000|1715155200000
V1|RECORD|0|req-1715155205000|1715155205000
V1|RECORD|2|req-1715155210000|1715155210000
V1|RECORD|3|req-1715155215000|1715155215000
V1|RECORD|4|req-1715155220000|1715155220000
```

## SDK 用法

### 1. 添加仓库

`settings.gradle`

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### 2. 添加依赖

```gradle
dependencies {
    implementation "com.github.zhenglongzhang:ble-sdk-android:1.0.2"
    coreLibraryDesugaring "com.android.tools:desugar_jdk_libs:2.0.4"
}
```

### 3. 开启 desugaring

当前工程保留了 desugaring 配置，用于兼容低版本 Android 设备上的 Java 标准库调用：

```gradle
android {
    compileOptions {
        coreLibraryDesugaringEnabled true
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
```

### 4. 运行时权限

```java
String[] permissions = BlePermissionHelper.getRuntimePermissions();
```

Android 12 及以上会申请：

- `BLUETOOTH_SCAN`
- `BLUETOOTH_CONNECT`
- `ACCESS_FINE_LOCATION`

Android 6 到 Android 11 会申请：

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`

### 5. 基本调用

```java
BleClient bleClient = new BleClient(this);

bleClient.startScan(12_000L, new BleScanListener() {
    @Override
    public void onScanStarted() {
    }

    @Override
    public void onDeviceFound(BleDevice device) {
        String display = device.getDisplayName(); // 23070401
        String address = device.getAddress();
    }

    @Override
    public void onScanStopped(List<BleDevice> devices) {
    }

    @Override
    public void onScanFailed(String message) {
    }
});

bleClient.connect("45:C9:88:38:26:9A", new BleConnectionListener() {
    @Override
    public void onDeviceConnecting(BleDevice device) {
    }

    @Override
    public void onDeviceConnected(BleDevice device) {
    }

    @Override
    public void onServicesDiscovered(BleDevice device, List<BluetoothGattService> services) {
    }

    @Override
    public void onDeviceReady(BleDevice device) {
    }

    @Override
    public void onDeviceDisconnecting(BleDevice device) {
    }

    @Override
    public void onDeviceDisconnected(BleDevice device) {
    }

    @Override
    public void onError(BleDevice device, String message) {
    }
});

String requestId = bleClient.startRecord(new BleWriteListener() {
    @Override
    public void onWriteSuccess(UUID serviceUuid, UUID characteristicUuid, byte[] value, String hexValue) {
    }

    @Override
    public void onError(UUID serviceUuid, UUID characteristicUuid, byte[] value, String message) {
    }
});
```

`requestId` 用于业务侧日志关联；当前 SDK 实际下发给设备的控制报文格式为 `V1|RECORD|ACTION|REQUEST_ID|TIMESTAMP`。

## 关键 API

`BleClient` 当前已经内置业务常量：

- `BleClient.TARGET_DEVICE_NAME_PREFIX`
- `BleClient.FIXED_SERVICE_UUID`
- `BleClient.FIXED_WRITE_CHARACTERISTIC_UUID`
- `BleClient.FIXED_NOTIFY_CHARACTERISTIC_UUID`
- `BleClient.REQUESTED_MTU`

控制方法：

- `startRecord(...)`
- `stopRecord(...)`
- `queryRecordStatus(...)`
- `disableVideoKey(...)`
- `enableVideoKey(...)`

辅助方法：

- `isTargetDeviceName(...)`
- `extractDisplayName(...)`
- `writeFixedAsciiCommand(...)`
- `readFixedReply(...)`
- `enableFixedServiceNotifications(...)`
- `buildRecordCommand(...)`

## H5 JSBridge 接入

宿主 App 使用 WebView 时，可直接注册 SDK 提供的 JSBridge：

```java
WebView webView = findViewById(R.id.webView);
webView.getSettings().setJavaScriptEnabled(true);
webView.getSettings().setDomStorageEnabled(true);

ZnhaasBleJsBridge bridge = new ZnhaasBleJsBridge(this, webView);
bridge.attach(); // 默认注入 window.ZnhaasBleBridge
```

宿主 Activity 需要转发权限和蓝牙开启结果：

```java
@Override
public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    bridge.onRequestPermissionsResult(requestCode, permissions, grantResults);
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    bridge.onActivityResult(requestCode);
}
```

H5 可调用：

```js
window.ZnhaasBleBridge.requestPermissions()
window.ZnhaasBleBridge.requestEnableBluetooth()
window.ZnhaasBleBridge.startScan(12000)
window.ZnhaasBleBridge.stopScan()
window.ZnhaasBleBridge.connect(address)
window.ZnhaasBleBridge.disconnect()
window.ZnhaasBleBridge.startRecord()
window.ZnhaasBleBridge.stopRecord()
window.ZnhaasBleBridge.queryRecordStatus()
window.ZnhaasBleBridge.disableVideoKey()
window.ZnhaasBleBridge.enableVideoKey()
```

原生事件统一回调到：

```js
window.ZnhaasBle = {
  onNativeEvent(event) {
    console.log(event.type, event.data)
  }
}
```

常用事件：

- `deviceFound`：扫描到 `znhaas` 设备
- `deviceReady`：连接并完成服务发现
- `writeSuccess`：命令写入成功
- `deviceAck`：收到 `V1|ACK|...` 业务 ACK
- `deviceReply`：收到非 ACK 回包；`isReadFallback=true` 时仅表示诊断读值

## Demo 使用方式

1. Demo 使用 WebView 加载本地 H5：`app/src/main/assets/znhaas_ble_demo.html`
2. 在 H5 页面点击 `申请权限` 和 `开启蓝牙`
3. 点击 `开始扫描`
4. 在 H5 设备列表中点击设备卡片，自动发起连接
5. 连接成功后点击 5 个控制按钮之一
6. 在 H5 的 `Runtime Log` 查看发送结果和设备 ACK

## JitPack 发布

1. 将工程推到 GitHub
2. 打 tag，例如 `1.0.0`
3. 到 JitPack 页面触发构建
4. 构建成功后按页面给出的依赖坐标接入

## 本地验证

- ./gradlew :ble-sdk:publishReleasePublicationToMavenLocal
- Demo APK 输出：[`app-debug.apk`](/ble-sdk-android/app/build/outputs/apk/debug/app-debug.apk)
- 本地 Maven 产物：`~/.m2/repository/com/github/zhenglongzhang/ble-sdk-android/1.0.0/`
