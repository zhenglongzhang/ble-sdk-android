## 1.H5 可调用方法

```js
window.ZnhaasBleBridge.getState()
window.ZnhaasBleBridge.getRequiredPermissions()
window.ZnhaasBleBridge.requestPermissions()
window.ZnhaasBleBridge.requestEnableBluetooth()
window.ZnhaasBleBridge.startScan(12000)
window.ZnhaasBleBridge.stopScan()
window.ZnhaasBleBridge.connect(address)
window.ZnhaasBleBridge.disconnect()
// window.ZnhaasBleBridge.startRecord()
window.ZnhaasBleBridge.stopRecord()
window.ZnhaasBleBridge.queryRecordStatus()
window.ZnhaasBleBridge.disableVideoKey()
// window.ZnhaasBleBridge.enableVideoKey()

window.ZnhaasBleBridge.enableVideoKey({
  work_order: 'WO-20250122',
  task_id: 'TASK-01',
  device_id: '31011500991325140052'
})

window.ZnhaasBleBridge.disableVideoKey({
  work_order: 'WO-20250122',
  task_id: 'TASK-01',
  device_id: '31011500991325140052'
})
window.ZnhaasBleBridge.writeCommand(command)
```

当前蓝牙控制命令使用 v2 固定 14 位协议。`disableVideoKey(extra)` 表示开始录制并禁用视频物理按键，`enableVideoKey(extra)` 表示开始录制并启用视频物理按键；`startRecord(extra)` 保留为兼容方法，默认等同于开始录制并禁用视频物理按键。

### 1.1 原生事件回调

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
| `deviceAck` | 收到 v2 设备回复，`data.response` 为结构化结果 |
| `deviceReply` | 收到非 v2 回复，`isReadFallback=true` 时仅表示诊断读值 |
| `error` | 扫描、连接、写入等错误 |

### 1.2 方法同步返回值

| 方法 | 返回值 | 说明 |
| --- | --- | --- |
| `getState()` | `string` | 当前状态 JSON 字符串，同时会派发 `state` 事件 |
| `getRequiredPermissions()` | `string` | 权限信息 JSON 字符串 |
| `requestEnableBluetooth()` | `boolean` | 是否成功发起开启蓝牙流程；最终是否开启成功请监听 `enableBluetoothResult` |
| `startRecord(extra)` | `string` | 本次命令的 `requestId` |
| `stopRecord(extra)` | `string` | 本次命令的 `requestId` |
| `queryRecordStatus(extra)` | `string` | 本次命令的 `requestId` |
| `disableVideoKey(extra)` | `string` | 本次命令的 `requestId` |
| `enableVideoKey(extra)` | `string` | 本次命令的 `requestId` |
| `requestPermissions()` / `startScan()` / `stopScan()` / `connect()` / `disconnect()` / `writeCommand()` | `void` | 结果通过原生事件异步返回 |

### 1.3 回调事件通用结构

所有原生回调都会使用下面的外层结构。客户 mock 时建议保持 `type`、`data`、`timestamp` 三个字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `type` | `string` | 事件名称，例如 `deviceFound`、`deviceAck` |
| `data` | `object` | 事件数据，不同事件字段不同 |
| `timestamp` | `number` | 原生派发事件的毫秒时间戳 |

```json
{
  "type": "deviceAck",
  "data": {},
  "timestamp": 1705939230010
}
```

当前 SDK 固定 UUID 如下：

| 名称 | UUID |
| --- | --- |
| Service UUID | `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` |
| Write UUID | `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` |
| Reply UUID | `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` |

### 1.4 通用 data 字段

状态类事件字段如下，适用于 `state`，`permissionsResult`，`enableBluetoothResult`，`bluetoothStateChanged`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `bluetoothSupported` | `boolean` | 当前设备是否支持 BLE |
| `bluetoothEnabled` | `boolean` | 手机蓝牙是否已开启 |
| `hasRequiredPermissions` | `boolean` | 是否已具备 SDK 所需运行时权限 |
| `scanning` | `boolean` | 是否正在扫描 |
| `connected` | `boolean` | 是否已连接设备 |
| `serviceUuid` | `string` | 固定服务 UUID |
| `writeUuid` | `string` | 固定写入特征 UUID |
| `replyUuid` | `string` | 固定回包特征 UUID |
| `success` | `boolean` | 仅 `permissionsResult`、`enableBluetoothResult` 返回，表示本次操作是否成功 |
| `granted` | `boolean` | 仅 `permissionsResult` 返回，表示权限是否授予 |
| `permissions` | `array` | 仅 `permissionsResult` 返回，权限明细列表 |
| `message` | `string` | 仅结果类事件返回，成功或失败说明 |
| `alreadyEnabled` | `boolean` | 仅 `enableBluetoothResult` 返回，蓝牙是否在调用前已开启 |
| `pending` | `boolean` | 仅 `enableBluetoothResult` 返回，是否仍在等待用户处理 |
| `userAction` | `boolean` | 仅 `enableBluetoothResult` 返回，是否拉起了系统开启蓝牙弹窗 |
| `stateCode` | `number` | 仅 `bluetoothStateChanged` 返回，Android 蓝牙状态码 |
| `stateText` | `string` | 仅 `bluetoothStateChanged` 返回，例如 `STATE_ON` |
| `enabled` | `boolean` | 仅 `bluetoothStateChanged` 返回，蓝牙是否开启 |

设备字段如下，适用于 `deviceFound`，`deviceConnecting`，`deviceConnected`，`deviceReady`，`deviceDisconnecting`，`deviceDisconnected`，`connectionError`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `device.name` | `string` | 原始蓝牙名称，例如 `znhaas_23070401` |
| `device.displayName` | `string` | 展示名称，例如 `23070401` |
| `device.address` | `string` | 蓝牙 MAC 地址 |
| `device.rssi` | `number` | 信号强度 |
| `device.bondState` | `number` | Android 设备绑定状态 |
| `message` | `string` | 仅 `connectionError` 返回，错误描述 |

扫描结果字段如下：

| 事件 | data 字段 |
| --- | --- |
| `scanStarted` | 空对象 `{}` |
| `scanStopped` | `devices: BleDevice[]`，`count: number` |

服务发现字段如下，适用于 `servicesDiscovered`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `device` | `object` | 当前连接设备 |
| `services` | `array` | GATT 服务列表 |
| `services[].uuid` | `string` | 服务 UUID |
| `services[].characteristics` | `array` | 特征列表 |
| `services[].characteristics[].uuid` | `string` | 特征 UUID |
| `services[].characteristics[].properties` | `string` | 特征属性文本，例如 `READ|NOTIFY (18)` |
| `services[].characteristics[].rawProperties` | `number` | Android 原始 properties 数值 |

命令发送字段如下，适用于 `commandDispatched`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `action` | `string` | H5 调用动作，例如 `startRecord` |
| `requestId` | `string|null` | SDK 生成的请求 ID，`writeCommand` 为 `null` |
| `command` | `string` | 实际下发到 BLE 的完整指令 |
| `extraFields` | `object` | H5 传入并成功写入协议固定位置的业务字段，仅保留非空键值 |

写入和设备回包字段如下，适用于 `writeSuccess`，`writeError`，`deviceAck`，`deviceReply`，`replyListenerEnabled`，`replyListenerDisabled`，`replyChannelError`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `serviceUuid` | `string` | 服务 UUID |
| `characteristicUuid` | `string` | 特征 UUID |
| `value` | `string` | UTF-8 文本内容，`replyListenerEnabled`、`replyListenerDisabled`、`replyChannelError` 不返回 |
| `hexValue` | `string` | 十六进制内容，仅 `writeSuccess`、`deviceAck`、`deviceReply` 返回 |
| `isAck` | `boolean` | 仅 `deviceAck`、`deviceReply` 返回，是否为设备业务回复 |
| `isV2Response` | `boolean` | 仅 `deviceAck`、`deviceReply` 返回，是否为 v2 协议回复 |
| `isReadFallback` | `boolean` | 仅 `deviceAck`、`deviceReply` 返回，是否为诊断读值回退 |
| `response` | `object` | 仅 v2 回复返回，结构化业务字段 |
| `message` | `string` | 仅 `writeError`、`replyChannelError` 返回，错误描述 |

`response` 字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `command` | `string` | `0` 表示录制控制，`1` 表示状态查询 |
| `commandText` | `string` | `RECORD` 或 `STATUS` |
| `action` | `string` | `0` 停止，`1` 开始并禁用物理键，`2` 开始并启用物理键，`3` 查询 |
| `actionText` | `string` | 动作文本 |
| `requestId` | `string` | 原请求 ID |
| `workOrder` | `string` | 工单号 |
| `taskId` | `string` | 任务号 |
| `deviceId` | `string` | 设备 ID |
| `battery` | `string` | 电池电量 |
| `rssi` | `string` | 信号强度 |
| `filePath` | `string` | 停止录制成功后的文件路径 |
| `bucketName` | `string` | OSS bucket 名称 |
| `recordingState` | `string` | 状态查询时的录制状态，`0` 空闲，`1` 录制中 |
| `statusCode` | `string` | `0` 成功，`1` 失败，`2` 参数错误 |
| `statusText` | `string` | 状态码文本 |
| `success` | `boolean` | 是否成功 |

错误和日志字段如下：

| 事件 | data 字段 |
| --- | --- |
| `error` | `source: string`，`message: string` |
| `log` | `message: string` |

### 1.5 Mock 数据示例

`state`：

```json
{
  "type": "state",
  "data": {
    "bluetoothSupported": true,
    "bluetoothEnabled": true,
    "hasRequiredPermissions": true,
    "scanning": false,
    "connected": false,
    "serviceUuid": "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
    "writeUuid": "6E400003-B5A3-F393-E0A9-E50E24DCCA9E",
    "replyUuid": "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
  },
  "timestamp": 1705939230000
}
```

`permissionsResult`：

```json
{
  "type": "permissionsResult",
  "data": {
    "bluetoothSupported": true,
    "bluetoothEnabled": false,
    "hasRequiredPermissions": true,
    "scanning": false,
    "connected": false,
    "serviceUuid": "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
    "writeUuid": "6E400003-B5A3-F393-E0A9-E50E24DCCA9E",
    "replyUuid": "6E400002-B5A3-F393-E0A9-E50E24DCCA9E",
    "success": true,
    "granted": true,
    "permissions": [
      {
        "name": "android.permission.BLUETOOTH_SCAN",
        "granted": true,
        "grantResult": 0
      },
      {
        "name": "android.permission.BLUETOOTH_CONNECT",
        "granted": true,
        "grantResult": 0
      }
    ],
    "message": "BLE permissions granted."
  },
  "timestamp": 1705939230001
}
```

`permissionsResult` 权限失败：

```json
{
  "type": "permissionsResult",
  "data": {
    "bluetoothSupported": true,
    "bluetoothEnabled": false,
    "hasRequiredPermissions": false,
    "scanning": false,
    "connected": false,
    "serviceUuid": "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
    "writeUuid": "6E400003-B5A3-F393-E0A9-E50E24DCCA9E",
    "replyUuid": "6E400002-B5A3-F393-E0A9-E50E24DCCA9E",
    "success": false,
    "granted": false,
    "permissions": [
      {
        "name": "android.permission.BLUETOOTH_SCAN",
        "granted": false,
        "grantResult": -1
      }
    ],
    "message": "BLE permissions denied."
  },
  "timestamp": 1705939230001
}
```

`enableBluetoothResult` 开启成功：

```json
{
  "type": "enableBluetoothResult",
  "data": {
    "bluetoothSupported": true,
    "bluetoothEnabled": true,
    "hasRequiredPermissions": true,
    "scanning": false,
    "connected": false,
    "serviceUuid": "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
    "writeUuid": "6E400003-B5A3-F393-E0A9-E50E24DCCA9E",
    "replyUuid": "6E400002-B5A3-F393-E0A9-E50E24DCCA9E",
    "success": true,
    "enabled": true,
    "userAction": true,
    "alreadyEnabled": false,
    "pending": false,
    "message": "Bluetooth enabled."
  },
  "timestamp": 1705939230002
}
```

`enableBluetoothResult` 开启失败或用户取消：

```json
{
  "type": "enableBluetoothResult",
  "data": {
    "bluetoothSupported": true,
    "bluetoothEnabled": false,
    "hasRequiredPermissions": true,
    "scanning": false,
    "connected": false,
    "serviceUuid": "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
    "writeUuid": "6E400003-B5A3-F393-E0A9-E50E24DCCA9E",
    "replyUuid": "6E400002-B5A3-F393-E0A9-E50E24DCCA9E",
    "success": false,
    "enabled": false,
    "userAction": true,
    "alreadyEnabled": false,
    "pending": false,
    "message": "Bluetooth enable cancelled or Bluetooth remains disabled."
  },
  "timestamp": 1705939230002
}
```

`deviceFound`：

```json
{
  "type": "deviceFound",
  "data": {
    "device": {
      "name": "znhaas_23070401",
      "displayName": "23070401",
      "address": "AA:BB:CC:DD:EE:FF",
      "rssi": -58,
      "bondState": 10
    }
  },
  "timestamp": 1705939230100
}
```

`scanStopped`：

```json
{
  "type": "scanStopped",
  "data": {
    "devices": [
      {
        "name": "znhaas_23070401",
        "displayName": "23070401",
        "address": "AA:BB:CC:DD:EE:FF",
        "rssi": -58,
        "bondState": 10
      }
    ],
    "count": 1
  },
  "timestamp": 1705939242000
}
```

`deviceReady`：

```json
{
  "type": "deviceReady",
  "data": {
    "device": {
      "name": "znhaas_23070401",
      "displayName": "23070401",
      "address": "AA:BB:CC:DD:EE:FF",
      "rssi": -55,
      "bondState": 10
    }
  },
  "timestamp": 1705939243000
}
```

`commandDispatched`：

```json
{
  "type": "commandDispatched",
  "data": {
    "action": "disableVideoKey",
    "requestId": "req-1705939230000",
    "command": "2|C|0|1|req-1705939230000|1705939230000|WO-20250122|TASK-01|31011500991325140052|||||\n",
    "extraFields": {
      "work_order": "WO-20250122",
      "task_id": "TASK-01",
      "device_id": "31011500991325140052"
    }
  },
  "timestamp": 1705939230000
}
```

`writeSuccess`：

```json
{
  "type": "writeSuccess",
  "data": {
    "serviceUuid": "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
    "characteristicUuid": "6E400003-B5A3-F393-E0A9-E50E24DCCA9E",
    "value": "2|C|0|1|req-1705939230000|1705939230000|WO-20250122|TASK-01|31011500991325140052|||||",
    "hexValue": "327C437C307C317C7265712D313730353933393233303030307C313730353933393233303030307C574F2D32303235303132327C5441534B2D30317C33313031313530303939313332353134303035327C7C7C7C7C0A"
  },
  "timestamp": 1705939230005
}
```

`deviceAck`：

```json
{
  "type": "deviceAck",
  "data": {
    "serviceUuid": "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
    "characteristicUuid": "6E400002-B5A3-F393-E0A9-E50E24DCCA9E",
    "value": "2|R|0|1|req-1705939230000|1705939230010|WO-20250122|TASK-01|31011500991325140052|85|-45|||0",
    "hexValue": "327C527C307C317C7265712D313730353933393233303030307C313730353933393233303031307C574F2D32303235303132327C5441534B2D30317C33313031313530303939313332353134303035327C38357C2D34357C7C7C30",
    "isAck": true,
    "isV2Response": true,
    "isReadFallback": false,
    "response": {
      "version": "2",
      "cmdType": "R",
      "command": "0",
      "commandText": "RECORD",
      "action": "1",
      "actionText": "START_RECORD_DISABLE_KEY",
      "requestId": "req-1705939230000",
      "timestamp": "1705939230010",
      "workOrder": "WO-20250122",
      "taskId": "TASK-01",
      "deviceId": "31011500991325140052",
      "battery": "85",
      "rssi": "-45",
      "filePath": "",
      "bucketName": "",
      "statusCode": "0",
      "statusText": "SUCCESS",
      "success": true
    }
  },
  "timestamp": 1705939230010
}
```

`deviceReply`：

```json
{
  "type": "deviceReply",
  "data": {
    "serviceUuid": "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
    "characteristicUuid": "6E400002-B5A3-F393-E0A9-E50E24DCCA9E",
    "value": "V1|STATUS|RECORDING|1705939230010",
    "hexValue": "56317C5354415455537C5245434F5244494E477C31373035393339323330303130",
    "isAck": false,
    "isReadFallback": false
  },
  "timestamp": 1705939230010
}
```

`error`：

```json
{
  "type": "error",
  "data": {
    "source": "scan",
    "message": "Missing BLE runtime permissions."
  },
  "timestamp": 1705939230000
}
```

v2 协议只会下发固定业务字段：`work_order`、`task_id`、`device_id`。其中开始/停止录制要求 `work_order` 和 `task_id` 有值，`device_id` 可选；查询状态不下发这 3 个字段。H5 传入其他键不会继续拼接到协议末尾。

```js
window.ZnhaasBleBridge.disableVideoKey({
  work_order: 'WO-20250122',
  task_id: 'TASK-01',
  device_id: '31011500991325140052'
})
```

## 2. Demo 使用说明

操作流程如下：

1. 在 H5 页面点击 `申请权限`
2. 点击 `开启蓝牙`
3. 点击 `开始扫描`
4. 在 H5 设备列表中点击目标安全帽设备
5. 连接成功后点击对应控制按钮
6. 在 H5 的 `Runtime Log` 中查看发送结果和设备回复

## 3. 注意事项

1. 接入前请确保手机蓝牙已打开
2. Android 12 及以上必须授予蓝牙运行时权限
3. Android 6 到 Android 11 需要位置权限，否则 BLE 扫描可能失败
4. 设备返回的通知数据建议按 UTF-8 文本解析
5. H5 正式接入时建议只对可信页面开启 JSBridge，避免任意网页调用蓝牙能力
