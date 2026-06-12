package com.znhaas.sdk.appbridge;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ZnhaasAppJsBridge {
    public static final String DEFAULT_JS_INTERFACE_NAME = "ZnhaasAppBridge";
    public static final String DEFAULT_NATIVE_JS_INTERFACE_NAME = "__ZnhaasAppNativeBridge";
    public static final int DEFAULT_SCAN_CODE_REQUEST_CODE = 42001;
    public static final int DEFAULT_TAKE_PHOTO_REQUEST_CODE = 42002;
    public static final int DEFAULT_CAMERA_PERMISSION_REQUEST_CODE = 42003;
    private static final Set<String> ALLOWED_WEBVIEW_SCHEMES = new HashSet<>(Arrays.asList("http", "https", "file"));

    private final Activity activity;
    private final WebView webView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private PendingAction pendingCameraAction;
    private String pendingScanCodeRequestId;
    private String pendingTakePhotoRequestId;
    private Uri pendingPhotoUri;
    private File pendingPhotoFile;
    private int pendingPhotoMaxWidth = 1600;
    private int pendingPhotoQuality = 80;

    public ZnhaasAppJsBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    @SuppressLint("JavascriptInterface")
    public void attach() {
        attach(DEFAULT_JS_INTERFACE_NAME);
    }

    @SuppressLint("JavascriptInterface")
    public void attach(String interfaceName) {
        webView.addJavascriptInterface(this, DEFAULT_NATIVE_JS_INTERFACE_NAME);
    }

    public void installJavascriptFacade() {
        installJavascriptFacade(DEFAULT_JS_INTERFACE_NAME);
    }

    public void installJavascriptFacade(String interfaceName) {
        final String script = buildJavascriptFacade(interfaceName);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript(script, null);
            }
        });
    }

    public void release() {
        pendingCameraAction = null;
        pendingScanCodeRequestId = null;
        pendingTakePhotoRequestId = null;
        pendingPhotoUri = null;
        cleanupPendingPhoto();
    }

    @JavascriptInterface
    public String scanCode() {
        return scanCodeJson(null);
    }

    @JavascriptInterface
    public String scanCodeJson(String optionsJson) {
        final String requestId = buildRequestId("scan");
        pendingScanCodeRequestId = requestId;
        if (!hasCameraPermission()) {
            pendingCameraAction = PendingAction.SCAN_CODE;
            requestCameraPermission();
            emitLog("Requesting camera permission before scanCode.");
            return requestId;
        }
        startScanCodeActivity(requestId);
        return requestId;
    }

    @JavascriptInterface
    public String takePhoto() {
        return takePhotoJson(null);
    }

    @JavascriptInterface
    public String takePhotoJson(String optionsJson) {
        final String requestId = buildRequestId("photo");
        JSONObject options = parseJson(optionsJson);
        pendingTakePhotoRequestId = requestId;
        pendingPhotoMaxWidth = options.optInt("maxWidth", 1600);
        pendingPhotoQuality = options.optInt("quality", 80);
        if (!hasCameraPermission()) {
            pendingCameraAction = PendingAction.TAKE_PHOTO;
            requestCameraPermission();
            emitLog("Requesting camera permission before takePhoto.");
            return requestId;
        }
        startTakePhoto(requestId);
        return requestId;
    }

    @JavascriptInterface
    public String getNetworkState() {
        JSONObject data = buildNetworkState();
        emit("networkState", data);
        return data.toString();
    }

    @JavascriptInterface
    public String openWebView(String url) {
        JSONObject options = new JSONObject();
        put(options, "url", url);
        return openWebViewJson(options.toString());
    }

    @JavascriptInterface
    public String openWebViewJson(String optionsJson) {
        final String requestId = buildRequestId("webview");
        JSONObject options = parseJson(optionsJson);
        final String url = options.optString("url", "").trim();
        final String title = options.optString("title", "").trim();
        if (!isAllowedWebViewUrl(url)) {
            emitOpenWebViewResult(requestId, false, url, "Invalid or unsupported url.");
            return requestId;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(activity, ZnhaasWebViewActivity.class);
                intent.putExtra(ZnhaasWebViewActivity.EXTRA_URL, url);
                intent.putExtra(ZnhaasWebViewActivity.EXTRA_TITLE, title);
                activity.startActivity(intent);
                emitOpenWebViewResult(requestId, true, url, "");
            }
        });
        return requestId;
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DEFAULT_SCAN_CODE_REQUEST_CODE) {
            handleScanCodeResult(resultCode, data);
            return true;
        }
        if (requestCode == DEFAULT_TAKE_PHOTO_REQUEST_CODE) {
            handleTakePhotoResult(resultCode);
            return true;
        }
        return false;
    }

    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != DEFAULT_CAMERA_PERMISSION_REQUEST_CODE) {
            return false;
        }
        boolean granted = grantResults != null && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        PendingAction action = pendingCameraAction;
        pendingCameraAction = null;
        if (!granted) {
            emitCameraDenied(action);
            return true;
        }
        if (action == PendingAction.SCAN_CODE && pendingScanCodeRequestId != null) {
            startScanCodeActivity(pendingScanCodeRequestId);
        } else if (action == PendingAction.TAKE_PHOTO && pendingTakePhotoRequestId != null) {
            startTakePhoto(pendingTakePhotoRequestId);
        }
        return true;
    }

    private void startScanCodeActivity(final String requestId) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(activity, ZnhaasScanCodeActivity.class);
                intent.putExtra(ZnhaasScanCodeActivity.EXTRA_REQUEST_ID, requestId);
                activity.startActivityForResult(intent, DEFAULT_SCAN_CODE_REQUEST_CODE);
            }
        });
    }

    private void startTakePhoto(final String requestId) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    File dir = new File(activity.getCacheDir(), "znhaas_photos");
                    if (!dir.exists() && !dir.mkdirs()) {
                        emitPhotoError(requestId, "Unable to create photo cache directory.");
                        return;
                    }
                    pendingPhotoFile = File.createTempFile("znhaas_photo_", ".jpg", dir);
                    pendingPhotoUri = FileProvider.getUriForFile(
                            activity,
                            activity.getPackageName() + ".znhaas.fileprovider",
                            pendingPhotoFile
                    );
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    if (intent.resolveActivity(activity.getPackageManager()) == null) {
                        emitPhotoError(requestId, "No camera app is available.");
                        cleanupPendingPhoto();
                        return;
                    }
                    activity.startActivityForResult(intent, DEFAULT_TAKE_PHOTO_REQUEST_CODE);
                } catch (Exception exception) {
                    emitPhotoError(requestId, exception.getMessage());
                    cleanupPendingPhoto();
                }
            }
        });
    }

    private void handleScanCodeResult(int resultCode, Intent data) {
        String requestId = pendingScanCodeRequestId;
        pendingScanCodeRequestId = null;
        JSONObject payload = new JSONObject();
        put(payload, "requestId", requestId);
        if (resultCode == Activity.RESULT_OK && data != null) {
            put(payload, "text", data.getStringExtra(ZnhaasScanCodeActivity.EXTRA_TEXT));
            put(payload, "format", data.getStringExtra(ZnhaasScanCodeActivity.EXTRA_FORMAT));
            put(payload, "cancelled", false);
        } else {
            put(payload, "text", "");
            put(payload, "format", "");
            put(payload, "cancelled", true);
        }
        emit("scanCodeResult", payload);
    }

    private void handleTakePhotoResult(int resultCode) {
        final String requestId = pendingTakePhotoRequestId;
        final Uri photoUri = pendingPhotoUri;
        pendingTakePhotoRequestId = null;
        pendingPhotoUri = null;
        if (resultCode != Activity.RESULT_OK || photoUri == null) {
            JSONObject payload = new JSONObject();
            put(payload, "requestId", requestId);
            put(payload, "cancelled", true);
            emit("takePhotoResult", payload);
            cleanupPendingPhoto();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ZnhaasImageResult image = ZnhaasImageEncoder.encode(
                            activity,
                            photoUri,
                            "photo",
                            pendingPhotoMaxWidth,
                            pendingPhotoQuality
                    );
                    JSONObject payload = new JSONObject();
                    put(payload, "requestId", requestId);
                    put(payload, "cancelled", false);
                    put(payload, "image", imageToJson(image));
                    emit("takePhotoResult", payload);
                } catch (IOException exception) {
                    emitPhotoError(requestId, exception.getMessage());
                } finally {
                    cleanupPendingPhoto();
                }
            }
        }).start();
    }

    private void cleanupPendingPhoto() {
        if (pendingPhotoFile != null && pendingPhotoFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            pendingPhotoFile.delete();
        }
        pendingPhotoFile = null;
    }

    private void emitCameraDenied(PendingAction action) {
        if (action == PendingAction.SCAN_CODE) {
            JSONObject payload = new JSONObject();
            put(payload, "requestId", pendingScanCodeRequestId);
            put(payload, "text", "");
            put(payload, "format", "");
            put(payload, "cancelled", true);
            put(payload, "message", "Camera permission denied.");
            pendingScanCodeRequestId = null;
            emit("scanCodeResult", payload);
        } else if (action == PendingAction.TAKE_PHOTO) {
            emitPhotoError(pendingTakePhotoRequestId, "Camera permission denied.");
            pendingTakePhotoRequestId = null;
        }
    }

    private void emitPhotoError(String requestId, String message) {
        JSONObject payload = new JSONObject();
        put(payload, "requestId", requestId);
        put(payload, "cancelled", false);
        put(payload, "message", message != null ? message : "Photo capture failed.");
        emit("takePhotoError", payload);
    }

    private JSONObject imageToJson(ZnhaasImageResult image) {
        JSONObject json = new JSONObject();
        put(json, "base64", image.base64);
        put(json, "dataUrl", image.dataUrl);
        put(json, "mimeType", image.mimeType);
        put(json, "fileName", image.fileName);
        put(json, "width", image.width);
        put(json, "height", image.height);
        put(json, "sizeBytes", image.sizeBytes);
        return json;
    }

    private JSONObject buildNetworkState() {
        JSONObject data = new JSONObject();
        ConnectivityManager connectivityManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean connected = false;
        boolean validated = false;
        boolean metered = false;
        String type = "none";

        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = connectivityManager.getActiveNetwork();
                NetworkCapabilities capabilities = activeNetwork != null ? connectivityManager.getNetworkCapabilities(activeNetwork) : null;
                if (capabilities != null) {
                    connected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        type = "wifi";
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        type = "cellular";
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        type = "ethernet";
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                        type = "bluetooth";
                    } else {
                        type = "other";
                    }
                }
            } else {
                NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                connected = networkInfo != null && networkInfo.isConnected();
                validated = connected;
                metered = connectivityManager.isActiveNetworkMetered();
                if (networkInfo != null) {
                    int networkType = networkInfo.getType();
                    if (networkType == ConnectivityManager.TYPE_WIFI) {
                        type = "wifi";
                    } else if (networkType == ConnectivityManager.TYPE_MOBILE) {
                        type = "cellular";
                    } else if (networkType == ConnectivityManager.TYPE_ETHERNET) {
                        type = "ethernet";
                    } else if (networkType == ConnectivityManager.TYPE_BLUETOOTH) {
                        type = "bluetooth";
                    } else {
                        type = connected ? "other" : "none";
                    }
                }
            }
        }

        put(data, "connected", connected);
        put(data, "type", type);
        put(data, "validated", validated);
        put(data, "metered", metered);
        return data;
    }

    private boolean isAllowedWebViewUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        try {
            Uri uri = Uri.parse(url.trim());
            String scheme = uri.getScheme();
            return scheme != null && ALLOWED_WEBVIEW_SCHEMES.contains(scheme.toLowerCase(Locale.US));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void emitOpenWebViewResult(String requestId, boolean success, String url, String message) {
        JSONObject payload = new JSONObject();
        put(payload, "requestId", requestId);
        put(payload, "success", success);
        put(payload, "url", url);
        if (message != null && !message.isEmpty()) {
            put(payload, "message", message);
        }
        emit("openWebViewResult", payload);
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                ActivityCompat.requestPermissions(
                        activity,
                        new String[]{Manifest.permission.CAMERA},
                        DEFAULT_CAMERA_PERMISSION_REQUEST_CODE
                );
            }
        });
    }

    private JSONObject parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(json);
        } catch (JSONException exception) {
            emitError("options", "Invalid JSON options: " + exception.getMessage());
            return new JSONObject();
        }
    }

    private void emitError(String source, String message) {
        JSONObject payload = new JSONObject();
        put(payload, "source", source);
        put(payload, "message", message);
        emit("error", payload);
    }

    private void emitLog(String message) {
        JSONObject payload = new JSONObject();
        put(payload, "message", message);
        emit("log", payload);
    }

    private void emit(String type, JSONObject data) {
        JSONObject event = new JSONObject();
        put(event, "type", type);
        put(event, "data", data != null ? data : new JSONObject());
        put(event, "timestamp", System.currentTimeMillis());
        final String json = event.toString();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                String escaped = JSONObject.quote(json);
                webView.evaluateJavascript(
                        "window.ZnhaasApp&&window.ZnhaasApp.__dispatch&&window.ZnhaasApp.__dispatch(" + escaped + ");",
                        null
                );
            }
        });
    }

    private String buildJavascriptFacade(String interfaceName) {
        String targetName = interfaceName == null || interfaceName.trim().isEmpty()
                ? DEFAULT_JS_INTERFACE_NAME
                : interfaceName.trim();
        return "(function(){"
                + "var native=window." + DEFAULT_NATIVE_JS_INTERFACE_NAME + "||window." + targetName + ";"
                + "if(!native){return;}"
                + "window.ZnhaasApp=window.ZnhaasApp||{};"
                + "window.ZnhaasApp.__dispatch=function(payload){"
                + "var event=typeof payload==='string'?JSON.parse(payload):payload;"
                + "try{window.dispatchEvent(new CustomEvent('ZnhaasAppEvent',{detail:event}));}catch(e){}"
                + "if(typeof window.ZnhaasApp.onNativeEvent==='function'){window.ZnhaasApp.onNativeEvent(event);}"
                + "};"
                + "function stringify(options){"
                + "if(!options){return '';}"
                + "if(typeof options==='string'){return options.trim();}"
                + "if(typeof options!=='object'){return '';}"
                + "return JSON.stringify(options);"
                + "}"
                + "var facade={__isZnhaasAppFacade:true};"
                + "facade.scanCode=function(options){var json=stringify(options);return json?native.scanCodeJson(json):native.scanCode();};"
                + "facade.takePhoto=function(options){var json=stringify(options);return json?native.takePhotoJson(json):native.takePhoto();};"
                + "facade.getNetworkState=function(){var text=native.getNetworkState();try{return JSON.parse(text);}catch(e){return text;}};"
                + "facade.openWebView=function(options){if(typeof options==='string'){return native.openWebView(options);}var json=stringify(options);return native.openWebViewJson(json);};"
                + "window.__ZnhaasAppBridgeFacade=facade;"
                + "try{window." + targetName + "=facade;}catch(e){}"
                + "})();";
    }

    private String buildRequestId(String prefix) {
        return String.format(Locale.US, "%s-%d", prefix, System.currentTimeMillis());
    }

    private void put(JSONObject object, String key, Object value) {
        try {
            object.put(key, value);
        } catch (JSONException ignored) {
        }
    }

    private enum PendingAction {
        SCAN_CODE,
        TAKE_PHOTO
    }
}
