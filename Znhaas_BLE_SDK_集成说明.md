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

当前 SDK 已按业务场景做专用化约束：

- 只扫描蓝牙名称前缀为 `znhaas` 的 BLE 设备
- 扫描结果展示名称只返回设备编号后缀
  - 例如：`znhaas_23070401` 显示为 `23070401`
- 固定使用 znhaas UART Service
  - Service UUID：`6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
  - Write UUID：`6E400002-B5A3-F393-E0A9-E50E24DCCA9E`
  - Notify UUID：`6E400003-B5A3-F393-E0A9-E50E24DCCA9E`

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

## 9. 录像控制指令说明

当前 SDK 按《蓝牙控制.md》封装了 5 个控制动作，发送时不追加扩展字段。

统一消息格式：

```text
V1|RECORD|ACTION|TIMESTAMP
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

说明：

- `requestId` 用于业务侧调用日志关联
- 当前 SDK 实际发送给设备的控制报文不包含扩展字段，也不带 `requestId`

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

## 11. Demo 使用说明

SDK Demo 已集成在项目 `app` 模块中，操作流程如下：

1. 打开蓝牙权限
2. 点击 `Start Scan`
3. 在扫描列表中选择目标安全帽设备
4. 点击 `Connect`
5. 连接成功后点击对应控制按钮
6. 在 `Runtime Log` 中查看发送结果和设备回传

## 12. 注意事项

1. 接入前请确保手机蓝牙已打开
2. Android 12 及以上必须授予蓝牙运行时权限
3. Android 6 到 Android 11 需要位置权限，否则 BLE 扫描可能失败
4. 设备返回的通知数据建议按 UTF-8 文本解析
5. 若需正式对外发布，请结合业务隐私政策与法务要求复核 SDK 权限说明

## 13. 版本信息

- SDK 名称：`Znhaas BLE SDK`
- 当前版本：`1.0.2`
- Maven 坐标：`com.github.zhenglongzhang:ble-sdk-android:1.0.2`
- 最低系统版本：`Android API 22`
