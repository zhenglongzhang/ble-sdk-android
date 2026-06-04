## 1.H5 可调用方法

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
| `permissionsResult` | 运行时权限申请结果 |
| `deviceFound` | 扫描到 `znhaas` 设备 |
| `scanStopped` | 扫描结束 |
| `deviceReady` | 连接并完成服务发现 |
| `replyListenerEnabled` | 回包监听已开启 |
| `commandDispatched` | H5 已发起控制命令 |
| `writeSuccess` | BLE 写入成功 |
| `deviceAck` | 收到 `V1|ACK|...` 业务 ACK |
| `deviceReply` | 收到非 ACK 回包，`isReadFallback=true` 时仅表示诊断读值 |
| `error` | 扫描、连接、写入等错误 |

## 2. Demo 使用说明

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
