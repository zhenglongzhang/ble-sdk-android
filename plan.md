# 工单 H5 AppBridge 能力规划

## Summary
- 建议新增独立 `ZnhaasAppBridge`，不要把扫码、图片、导航、WebView、网络能力塞进 `ZnhaasBleBridge`，这样 BLE 和通用 App 容器能力边界清晰。
- 第一期 Android 优先；iOS 后续按同一 JS 协议补齐。
- App 只做系统能力代理：扫码返回文本，选图/拍照返回压缩后的 base64 图片数据，真正上传由 H5 负责。

## Key APIs
- 新增 H5 对象：`window.ZnhaasAppBridge`。
- 统一事件：`window.ZnhaasApp.onNativeEvent(event)` 和浏览器事件 `ZnhaasAppEvent`。
- 事件结构保持：`{ type, data, timestamp }`，异步能力的 `data` 内带 `requestId`。
- 方法清单：
  - `scanCode(options)`：拉起扫码，返回 `scanCodeResult`，字段含 `text`、`format`、`cancelled`。
  - `chooseImage(options)`：从相册选择图片，返回 `chooseImageResult`，字段含 `images[]`。
  - `takePhoto(options)`：拍照，返回 `takePhotoResult`，字段含 `image`。
  - `setNavigationBar(config)`：设置标题和右侧菜单，点击菜单回调 `navigationMenuClick`。
  - `openWebView(config)`：打开新 WebView，返回 `openWebViewResult`。
  - `getNetworkState()`：返回当前网络状态，同时派发 `networkState`。
  - `previewImages(config)`：原生预览图片，返回 `previewImagesResult`。
- 图片默认返回：`base64`、`mimeType`、`fileName`、`width`、`height`、`sizeBytes`；默认压缩为 JPEG，`quality=0.8`，`maxWidth=1600`，避免 WebView 传输过大。

## Implementation Changes
- Android SDK 内新增 `ZnhaasAppJsBridge`，沿用当前 BLE Bridge 的注入方式和事件派发方式。
- 新增 `ZnhaasAppBridgeHostDelegate`，让宿主 App 接管导航栏和新 WebView：
  - `onSetNavigationBar(config)`
  - `onOpenWebView(request)`
  - `onNavigationMenuClick(menuId)` 由宿主点击右侧按钮时通知 Bridge 回调 H5。
- 图片选择优先用 Android Photo Picker / 兼容 Intent；拍照用 Camera Intent + FileProvider；扫码默认用离线扫码方案，避免强依赖 Google Play 服务。
- 网络状态用 `ConnectivityManager` 获取当前网络，并返回 `connected`、`type`、`validated`、`metered`。
- Demo H5 增加“扫码、选图、拍照、设置导航、打开 WebView、网络状态、图片预览”按钮和 Runtime Log。
- 文档新增《工单 H5 JSBridge 接入说明.md》，包含接口参数、返回字段、错误码、mock JSON、客户调用示例。

## Test Plan
- 扫码：成功扫码、用户取消、无相机权限、扫码失败。
- 图片：选择 1 张、多张、取消选择、大图压缩、base64 字段完整性。
- 拍照：授权成功、拒绝相机权限、取消拍照、返回图片可被 H5 上传。
- 导航：设置 title、设置右侧菜单、点击菜单后 H5 收到 `menuId`。
- WebView：打开合法 URL、非法 URL 拦截、返回打开结果。
- 网络：有网、无网、Wi-Fi、蜂窝网络、网络切换后状态正确。
- 预览：预览 URL 图片、base64 图片、多图切换、关闭预览。

## Assumptions
- 第一期只实现 Android，iOS 后续复用同一 JS 协议。
- 图片上传接口、业务鉴权、工单字段都归 H5 管，App 不直接请求客户业务服务器。
- 新 WebView 和导航栏属于宿主 App UI，SDK 提供协议和 delegate，Demo 提供默认示例。
- 技术依据参考：Android [Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker)、Android [ConnectivityManager 网络状态](https://developer.android.com/develop/connectivity/network-ops/reading-network-state)、Google [Code Scanner/ML Kit 扫码能力说明](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner)。
