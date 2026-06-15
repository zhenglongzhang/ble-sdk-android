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
- 设备业务回复优先使用 Notify/Indicate 监听，v2 回复格式为 `2|R|...`
- 如果 Reply 特征只能 Read，Demo 会在写入成功后读取一次作为诊断兜底
- 按照《蓝牙录制控制协议 v2.0》封装录制控制动作
- 控制命令使用固定 14 位字段：`work_order`、`task_id`、`device_id`
- 提供 `ZnhaasBleJsBridge`，支持客户 H5 通过 WebView JSBridge 操作蓝牙

## 工程结构

- `ble-sdk`
  - SDK 库模块
- `app`
  - WebView + 本地 H5 Demo

## 录制控制协议 v2

协议统一格式：

```text
VERSION|CMD_TYPE|COMMAND|ACTION|REQ_ID|TIMESTAMP|P1|P2|P3|P4|P5|P6|P7|P8\n
```

当前下发命令固定使用：

```text
2|C|COMMAND|ACTION|REQ_ID|TIMESTAMP|work_order|task_id|device_id|||||
```

当前封装动作如下：

1. 停止录制：`COMMAND=0`，`ACTION=0`
2. 开始录制并禁用视频物理按键：`COMMAND=0`，`ACTION=1`
3. 开始录制并启用视频物理按键：`COMMAND=0`，`ACTION=2`
4. 查询状态：`COMMAND=1`，`ACTION=3`

示例：

```text
2|C|0|1|req-1705939230000|1705939230000|WO-20250122|TASK-01|31011500991325140052|||||
2|C|0|2|req-1705939230000|1705939230000|WO-20250122|TASK-01|31011500991325140052|||||
2|C|0|0|req-1705939300000|1705939300000|WO-20250122|TASK-01|31011500991325140052|||||
2|C|1|3|req-1705939400000|1705939400000||||||||
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
    implementation "com.github.zhenglongzhang:ble-sdk-android:1.0.3"
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

`requestId` 用于业务侧日志关联；当前 SDK 实际下发给设备的是 v2 固定 14 位控制报文。

如需下发固定业务字段，可调用带 `Map<String, String>` 的重载方法。当前 v2 协议只取 `work_order`、`task_id`、`device_id`：

```java
Map<String, String> extraFields = new LinkedHashMap<>();
extraFields.put("work_order", "WO-20250122");
extraFields.put("task_id", "TASK-01");
extraFields.put("device_id", "31011500991325140052");

String requestId = bleClient.disableVideoKey(extraFields, writeListener);
```

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
- `startRecord(extraFields, ...)`
- `stopRecord(extraFields, ...)`
- `queryRecordStatus(extraFields, ...)`
- `disableVideoKey(extraFields, ...)`
- `enableVideoKey(extraFields, ...)`

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
bridge.attach(); // 注入内部原生桥接对象
```

为了让 H5 使用 `window.ZnhaasBleBridge` 并直接传对象参数，页面加载完成后需要注入 JS 包装层：

```java
webView.setWebViewClient(new WebViewClient() {
    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        bridge.installJavascriptFacade();
    }
});
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

window.ZnhaasBleBridge.disableVideoKey({
  work_order: 'WO-20250122',
  task_id: 'TASK-01',
  device_id: '31011500991325140052'
})
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
- `deviceAck`：收到 v2 业务回复，结构化字段在 `data.response`
- `deviceReply`：收到非 v2 回包；`isReadFallback=true` 时仅表示诊断读值

## Demo 使用方式

1. Demo 使用 WebView 加载本地 H5：`app/src/main/assets/znhaas_ble_demo.html`
2. 在 H5 页面点击 `申请权限` 和 `开启蓝牙`
3. 点击 `开始扫描`
4. 在 H5 设备列表中点击设备卡片，自动发起连接
5. 连接成功后点击 4 个控制按钮之一
6. 在 H5 的 `Runtime Log` 查看发送结果和设备回复

## JitPack 发布

1. 将工程推到 GitHub
2. 打 tag，例如 `1.0.0`
3. 到 JitPack 页面触发构建
4. 构建成功后按页面给出的依赖坐标接入

## 本地验证

- ./gradlew :ble-sdk:publishReleasePublicationToMavenLocal
- Demo APK 输出：[`app-debug.apk`](/ble-sdk-android/app/build/outputs/apk/debug/app-debug.apk)
- 本地 Maven 产物：`~/.m2/repository/com/github/zhenglongzhang/ble-sdk-android/1.0.0/`
