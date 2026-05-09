# Znhaas BLE SDK

一个面向 `znhaas` 设备的 Android BLE SDK 和 Demo。

当前版本已经按业务场景收敛为专用实现：

- 只扫描蓝牙名称前缀为 `znhaas` 的 BLE 设备
- 扫描结果主显示名只返回后缀编号
  - 例如：`znhaas_23070401` -> `23070401`
- 固定使用 znhaas Service
  - Service UUID: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
  - Write UUID: `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`
  - Notify UUID: `6E400003-B5A3-F393-E0A9-E50E24DCCA9E`
- 按照《蓝牙控制.md》封装 5 个控制动作
- 发送命令时不追加扩展字段

## 工程结构

- `ble-sdk`
  - SDK 库模块
- `app`
  - 可直接点击操作的 Demo

## 5 个控制动作

协议统一格式：

```text
V1|RECORD|ACTION|REQUEST_ID|TIMESTAMP\n
```

当前封装的 5 个动作如下：

1. 开始录制：`ACTION = 1`
2. 停止录制：`ACTION = 0`
3. 查询状态：`ACTION = 2`
4. 禁止视频物理按键：`ACTION = 3`
5. 启用视频物理按键：`ACTION = 4`

示例：

```text
V1|RECORD|1|1715155200000
V1|RECORD|0|1715155205000
V1|RECORD|2|1715155210000
V1|RECORD|3|1715155215000
V1|RECORD|4|1715155220000
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
    implementation "com.github.<你的GitHub用户名>:ble-sdk-android:1.0.0"
    coreLibraryDesugaring "com.android.tools:desugar_jdk_libs:2.0.4"
}
```

### 3. 开启 desugaring

因为底层使用了 Nordic 的 `Android-Scanner-Compat-Library`，接入方也需要开启：

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

## 关键 API

`BleClient` 当前已经内置业务常量：

- `BleClient.TARGET_DEVICE_NAME_PREFIX`
- `BleClient.FIXED_SERVICE_UUID`
- `BleClient.FIXED_WRITE_CHARACTERISTIC_UUID`
- `BleClient.FIXED_NOTIFY_CHARACTERISTIC_UUID`

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
- `buildRecordCommand(...)`

## Demo 使用方式

1. 打开蓝牙权限
2. 点击 `Start Scan`
3. 在列表里选择一个设备，界面会自动把 MAC 地址填入输入框
4. 点击 `Connect`
5. 连接成功后直接点击 5 个控制按钮之一
6. 在底部 `Runtime Log` 查看发送结果和设备回包

## JitPack 发布

发布前建议先把 [`gradle.properties`](/Users/zhenglongzhang/coding/ble-sdk-android/gradle.properties) 里的 `GROUP` 改成你的 GitHub 用户名对应坐标，例如：

```properties
GROUP=com.github.your_github_id
VERSION_NAME=1.0.0
POM_ARTIFACT_ID=ble-sdk-android
```

然后：

1. 将工程推到 GitHub
2. 打 tag，例如 `1.0.0`
3. 到 JitPack 页面触发构建
4. 构建成功后按页面给出的依赖坐标接入

## 本地验证

已经完成本地构建验证：

- Demo APK 输出：[`app-debug.apk`](/Users/zhenglongzhang/coding/ble-sdk-android/app/build/outputs/apk/debug/app-debug.apk)
- 本地 Maven 产物：`~/.m2/repository/com/github/your_github_id/ble-sdk-android/1.0.0/`
