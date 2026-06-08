## 1. H5 可调用方法

```js
window.ZnhaasAppBridge.scanCode()
window.ZnhaasAppBridge.takePhoto()

window.ZnhaasAppBridge.scanCode({
  source: 'work-order'
})

window.ZnhaasAppBridge.takePhoto({
  maxWidth: 1600,
  quality: 80
})
```

当前版本先支持 Android 侧扫码和拍照。图片上传由 H5 自行完成，App 只负责拉起系统能力并返回结果。

## 2. 原生事件回调

SDK 会将异步结果派发给 H5：

```js
window.ZnhaasApp = {
  onNativeEvent(event) {
    console.log(event.type, event.data)
  }
}
```

同时也会派发浏览器事件：

```js
window.addEventListener('ZnhaasAppEvent', function (event) {
  console.log(event.detail.type, event.detail.data)
})
```

事件通用结构如下：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `type` | `string` | 事件名称 |
| `data` | `object` | 事件数据 |
| `timestamp` | `number` | 原生派发事件的毫秒时间戳 |

## 3. 扫码

### 3.1 调用

```js
const requestId = window.ZnhaasAppBridge.scanCode()
```

### 3.2 返回事件

事件名：`scanCodeResult`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `requestId` | `string` | 本次扫码请求 ID |
| `text` | `string` | 扫码文本结果，用户取消时为空字符串 |
| `format` | `string` | 码类型，例如 `QR_CODE`、`CODE_128` |
| `cancelled` | `boolean` | 是否取消 |
| `message` | `string` | 可选，异常或权限拒绝说明 |

### 3.3 Mock 数据

```json
{
  "type": "scanCodeResult",
  "data": {
    "requestId": "scan-1705939230000",
    "text": "WO-20250122",
    "format": "QR_CODE",
    "cancelled": false
  },
  "timestamp": 1705939231000
}
```

用户取消或权限拒绝：

```json
{
  "type": "scanCodeResult",
  "data": {
    "requestId": "scan-1705939230000",
    "text": "",
    "format": "",
    "cancelled": true,
    "message": "Camera permission denied."
  },
  "timestamp": 1705939231000
}
```

## 4. 拍照

### 4.1 调用

```js
const requestId = window.ZnhaasAppBridge.takePhoto({
  maxWidth: 1600,
  quality: 80
})
```

参数说明：

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `maxWidth` | `number` | `1600` | 图片最长边压缩到该尺寸以内 |
| `quality` | `number` | `80` | JPEG 压缩质量，范围 `1-100` |

### 4.2 返回事件

事件名：`takePhotoResult`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `requestId` | `string` | 本次拍照请求 ID |
| `cancelled` | `boolean` | 是否取消 |
| `image.base64` | `string` | JPEG base64，不带 data URL 前缀 |
| `image.dataUrl` | `string` | 可直接用于 `<img src>` 的 data URL |
| `image.mimeType` | `string` | 固定为 `image/jpeg` |
| `image.fileName` | `string` | SDK 生成的文件名 |
| `image.width` | `number` | 压缩后图片宽度 |
| `image.height` | `number` | 压缩后图片高度 |
| `image.sizeBytes` | `number` | 压缩后图片字节数 |

异常事件名：`takePhotoError`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `requestId` | `string` | 本次拍照请求 ID |
| `cancelled` | `boolean` | 固定为 `false` |
| `message` | `string` | 错误说明 |

### 4.3 Mock 数据

```json
{
  "type": "takePhotoResult",
  "data": {
    "requestId": "photo-1705939230000",
    "cancelled": false,
    "image": {
      "base64": "/9j/4AAQSkZJRgABAQAAAQABAAD...",
      "dataUrl": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD...",
      "mimeType": "image/jpeg",
      "fileName": "photo_1705939230000.jpg",
      "width": 1200,
      "height": 1600,
      "sizeBytes": 245678
    }
  },
  "timestamp": 1705939231000
}
```

用户取消：

```json
{
  "type": "takePhotoResult",
  "data": {
    "requestId": "photo-1705939230000",
    "cancelled": true
  },
  "timestamp": 1705939231000
}
```

拍照失败：

```json
{
  "type": "takePhotoError",
  "data": {
    "requestId": "photo-1705939230000",
    "cancelled": false,
    "message": "No camera app is available."
  },
  "timestamp": 1705939231000
}
```

## 5. Demo 页面

当前 Android Demo 默认加载：

```text
file:///android_asset/znhaas_app_tests.html
```

测试页面如下：

| 页面 | 说明 |
| --- | --- |
| `znhaas_app_tests.html` | 所有测试功能入口 |
| `znhaas_scan_code_test.html` | 扫码能力测试 |
| `znhaas_take_photo_test.html` | 拍照能力测试 |
| `znhaas_ble_demo.html` | 原 BLE 控制测试 |
