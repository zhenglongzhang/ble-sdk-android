# Znhaas BLE SDK 集成说明

## 1. 概要

本文档仅供龙湖内部使用，为龙湖 APP 开发提供 Android 安全帽 BLE SDK 接口说明，禁止外传。

本文档对应的 SDK 为 `Znhaas BLE SDK`，当前版本号为 `1.0.2`，最低支持 Android `API 22`。

## 2. 业务说明

本文档所述 SDK 兼容龙湖 `H07` 安全帽设备，包含以下能力说明：

- 蓝牙打开
- 蓝牙扫描
- 蓝牙连接
- 蓝牙监听设备回传数据
- 安全帽录像控制指令下发
- H5 通过 WebView JSBridge 操作蓝牙

当前 SDK 已按业务场景做专用化约束：

- 只扫描蓝牙名称前缀为 `znhaas` 的 BLE 设备
- 扫描结果展示名称只返回设备编号后缀
  - 例如：`znhaas_23070401` 显示为 `23070401`
- 固定使用 znhaas UART Service
  - Service UUID：`6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
  - Write UUID：`6E400003-B5A3-F393-E0A9-E50E24DCCA9E`
  - Reply UUID：`6E400002-B5A3-F393-E0A9-E50E24DCCA9E`
- 连接设备并完成服务发现后，SDK 会自动请求 MTU `517`
- 设备业务 ACK 优先使用 Notify/Indicate 监听，ACK 格式以 `V1|ACK|...` 开头
- 如果 Reply 特征只能 Read，可通过 SDK 读取一次作为诊断兜底；读到的 `RECORD|SUPPORTED` 属于能力说明，不代表本次命令 ACK

## 3. Android SDK 集成说明

### 3.1 添加仓库和依赖

在项目 `settings.gradle` 中添加 `JitPack` 仓库：

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

在业务模块 `build.gradle` 中添加依赖：

```gradle
dependencies {
    implementation "com.github.zhenglongzhang:ble-sdk-android:1.0.2"
    coreLibraryDesugaring "com.android.tools:desugar_jdk_libs:2.0.4"
}
```

### 3.2 编译选项

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

### 3.3 Manifest 权限配置

请在接入应用的 `AndroidManifest.xml` 中配置以下权限：

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

### 3.4 运行时权限申请

SDK 提供了权限辅助方法：

```java
String[] permissions = BlePermissionHelper.getRuntimePermissions();
```

权限规则如下：

- Android 12 及以上
  - `BLUETOOTH_SCAN`
  - `BLUETOOTH_CONNECT`
  - `ACCESS_FINE_LOCATION`
- Android 6 到 Android 11
  - `ACCESS_COARSE_LOCATION`
  - `ACCESS_FINE_LOCATION`

## 4. SDK 管理实例创建

```java
BleClient bleClient = new BleClient(context);
```

常用能力如下：

- `requestEnableBluetooth(Activity activity, int requestCode)`
- `startScan(long durationMs, BleScanListener listener)`
- `connect(String address, BleConnectionListener listener)`
- `disconnect()`
- `enableNotification(String serviceUuid, String characteristicUuid, BleNotifyListener listener)`
- `enableFixedServiceNotifications(BleNotifyListener listener)`
- `read(String serviceUuid, String characteristicUuid, BleNotifyListener listener)`
- `readFixedReply(BleNotifyListener listener)`

## 5. 蓝牙打开说明

调用示例：

```java
boolean handled = bleClient.requestEnableBluetooth(this, 1001);
```

说明：

- 若设备不支持 BLE，返回 `false`
- 若蓝牙已打开，返回 `true`
- 若蓝牙未打开，SDK 会拉起系统蓝牙开启弹窗

## 6. 蓝牙扫描说明

调用示例：

```java
bleClient.startScan(12_000L, new BleScanListener() {
    @Override
    public void onScanStarted() {
    }

    @Override
    public void onDeviceFound(BleDevice device) {
        String displayName = device.getDisplayName();
        String address = device.getAddress();
    }

    @Override
    public void onScanStopped(List<BleDevice> devices) {
    }

    @Override
    public void onScanFailed(String message) {
    }
});
```

说明：

- SDK 只回调名称前缀为 `znhaas` 的设备
- `BleDevice.getDisplayName()` 返回业务展示名称
- `BleDevice.getName()` 返回原始广播名称
- `BleDevice.getAddress()` 返回蓝牙 MAC 地址

## 7. 蓝牙连接说明

调用示例：

```java
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
```

说明：

- SDK 连接成功后会发现 GATT 服务
- 若发现固定 Notify 特征，Demo 会自动尝试订阅通知
- 连接监听可用于更新 UI、记录设备状态和错误提示

## 8. 蓝牙监听数据说明

监听示例：

```java
bleClient.enableNotification(
        BleClient.FIXED_SERVICE_UUID,
        BleClient.FIXED_NOTIFY_CHARACTERISTIC_UUID,
        new BleNotifyListener() {
            @Override
            public void onNotifyEnabled(UUID serviceUuid, UUID characteristicUuid) {
            }

            @Override
            public void onNotifyDisabled(UUID serviceUuid, UUID characteristicUuid) {
            }

            @Override
            public void onCharacteristicChanged(UUID serviceUuid, UUID characteristicUuid, byte[] value, String hexValue) {
                String ascii = new String(value, StandardCharsets.UTF_8).trim();
            }

            @Override
            public void onError(UUID serviceUuid, UUID characteristicUuid, String message) {
            }
        }
);
```

说明：

- 设备回传数据通过 `onCharacteristicChanged(...)` 回调
- 回调同时提供原始字节数组和十六进制字符串
- 对于 ASCII 协议，业务层可按 UTF-8 文本进一步解析
- 若设备 Reply 特征不支持 Notify/Indicate，但支持 Read，可在写入成功后调用 `readFixedReply(...)` 读取诊断值；业务成功与否应以 `V1|ACK|...` 格式的 ACK 为准

## 9. 录像控制指令说明

当前 SDK 按《蓝牙控制.md》封装了 5 个控制动作，并支持可选追加任意键值对扩展字段。

统一消息格式：

```text
V1|RECORD|ACTION|REQUEST_ID|TIMESTAMP
```

带业务扩展字段时格式如下，只有 key 和 value 都有值的字段才会按顺序追加：

```text
V1|RECORD|ACTION|REQUEST_ID|TIMESTAMP|key1=value1|key2=value2
```

5 个动作如下：

1. 开始录制：`startRecord(...)`
2. 停止录制：`stopRecord(...)`
3. 查询状态：`queryRecordStatus(...)`
4. 禁止视频物理按键：`disableVideoKey(...)`
5. 启用视频物理按键：`enableVideoKey(...)`

示例代码：

```java
String requestId = bleClient.startRecord(new BleWriteListener() {
    @Override
    public void onWriteSuccess(UUID serviceUuid, UUID characteristicUuid, byte[] value, String hexValue) {
    }

    @Override
    public void onError(UUID serviceUuid, UUID characteristicUuid, byte[] value, String message) {
    }
});
```

如需随指令追加业务字段：

```java
Map<String, String> extraFields = new LinkedHashMap<>();
extraFields.put("work_order", "WO-20250122");
extraFields.put("task_id", "TASK-01");

String requestId = bleClient.startRecord(extraFields, writeListener);
```

说明：

- `requestId` 用于业务侧调用日志关联
- `requestId` 会拼接进设备控制报文，用于和设备返回的 `V1|ACK|...` 进行链路关联
- 扩展字段会追加在指令末尾，例如：`V1|RECORD|1|req-1705939230000|1705939230000|work_order=WO-20250122|task_id=TASK-01`

动作与协议值映射如下：

| 方法 | ACTION 值 | 说明 |
|------|-----------|------|
| `startRecord(...)` | `1` | 开始录制 |
| `stopRecord(...)` | `0` | 停止录制 |
| `queryRecordStatus(...)` | `2` | 查询录制状态 |
| `disableVideoKey(...)` | `3` | 禁止视频物理按键 |
| `enableVideoKey(...)` | `4` | 启用视频物理按键 |

## 10. 常用回调说明

### 10.1 扫描回调

`BleScanListener`

- `onScanStarted()`
- `onDeviceFound(BleDevice device)`
- `onScanStopped(List<BleDevice> devices)`
- `onScanFailed(String message)`

### 10.2 连接回调

`BleConnectionListener`

- `onDeviceConnecting(BleDevice device)`
- `onDeviceConnected(BleDevice device)`
- `onServicesDiscovered(BleDevice device, List<BluetoothGattService> services)`
- `onDeviceReady(BleDevice device)`
- `onDeviceDisconnecting(BleDevice device)`
- `onDeviceDisconnected(BleDevice device)`
- `onError(BleDevice device, String message)`

### 10.3 通知回调

`BleNotifyListener`

- `onNotifyEnabled(...)`
- `onNotifyDisabled(...)`
- `onCharacteristicChanged(...)`
- `onError(...)`

### 10.4 写入回调

`BleWriteListener`

- `onWriteSuccess(...)`
- `onError(...)`

## 11. H5 JSBridge 接入说明

### 11.1 原生 WebView 注册

宿主 App 可直接使用 SDK 提供的 `ZnhaasBleJsBridge`：

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
    bridge.onActivityResult(requestCode, resultCode);
}
```

若加载远程 H5，请在宿主 App 中配置网络权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 11.2 H5 可调用方法

```js
window.ZnhaasBleBridge.getState()
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

window.ZnhaasBleBridge.startRecord({
  work_order: 'WO-20250122',
  task_id: 'TASK-01'
})
window.ZnhaasBleBridge.writeCommand(command)
```

### 11.3 原生事件回调

SDK 会将原生异步事件派发给 H5：

```js
window.ZnhaasBle = {
  onNativeEvent(event) {
    console.log(event.type, event.data)
  }
}
```

同时也会派发浏览器事件：

```js
window.addEventListener('ZnhaasBleEvent', function (event) {
  console.log(event.detail.type, event.detail.data)
})
```

常用事件如下：

| 事件 | 说明 |
| --- | --- |
| `state` | 蓝牙、权限、扫描、连接状态 |
| `permissionsResult` | 运行时权限申请结果，包含 `success/granted/message` |
| `enableBluetoothResult` | 开启蓝牙结果，包含 `success/enabled/message` |
| `deviceFound` | 扫描到 `znhaas` 设备 |
| `scanStopped` | 扫描结束 |
| `deviceReady` | 连接并完成服务发现 |
| `replyListenerEnabled` | 回包监听已开启 |
| `commandDispatched` | H5 已发起控制命令 |
| `writeSuccess` | BLE 写入成功 |
| `deviceAck` | 收到 `V1|ACK|...` 业务 ACK |
| `deviceReply` | 收到非 ACK 回包，`isReadFallback=true` 时仅表示诊断读值 |
| `error` | 扫描、连接、写入等错误 |

## 12. Demo 使用说明

SDK Demo 已集成在项目 `app` 模块中，当前页面由 WebView 加载本地 H5：

```text
app/src/main/assets/znhaas_ble_demo.html
```

操作流程如下：

1. 在 H5 页面点击 `申请权限`
2. 点击 `开启蓝牙`
3. 点击 `开始扫描`
4. 在 H5 设备列表中点击目标安全帽设备
5. 连接成功后点击对应控制按钮
6. 在 H5 的 `Runtime Log` 中查看发送结果和设备 ACK

## 13. 注意事项

1. 接入前请确保手机蓝牙已打开
2. Android 12 及以上必须授予蓝牙运行时权限
3. Android 6 到 Android 11 需要位置权限，否则 BLE 扫描可能失败
4. 设备返回的通知数据建议按 UTF-8 文本解析
5. H5 正式接入时建议只对可信页面开启 JSBridge，避免任意网页调用蓝牙能力
6. 若需正式对外发布，请结合业务隐私政策与法务要求复核 SDK 权限说明

## 14. 版本信息

- SDK 名称：`Znhaas BLE SDK`
- 当前版本：`1.0.2`
- Maven 坐标：`com.github.zhenglongzhang:ble-sdk-android:1.0.2`
- 最低系统版本：`Android API 22`
