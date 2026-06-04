package com.znhaas.sdk.bridge;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.znhaas.sdk.BleClient;
import com.znhaas.sdk.callback.BleConnectionListener;
import com.znhaas.sdk.callback.BleNotifyListener;
import com.znhaas.sdk.callback.BleScanListener;
import com.znhaas.sdk.callback.BleStateListener;
import com.znhaas.sdk.callback.BleWriteListener;
import com.znhaas.sdk.model.BleDevice;
import com.znhaas.sdk.util.BlePermissionHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ZnhaasBleJsBridge implements
        BleStateListener,
        BleScanListener,
        BleConnectionListener,
        BleNotifyListener,
        BleWriteListener {

    public static final String DEFAULT_JS_INTERFACE_NAME = "ZnhaasBleBridge";
    public static final String DEFAULT_NATIVE_JS_INTERFACE_NAME = "__ZnhaasBleNativeBridge";
    public static final int DEFAULT_ENABLE_BLUETOOTH_REQUEST_CODE = 41001;
    public static final int DEFAULT_PERMISSION_REQUEST_CODE = 41002;
    public static final long DEFAULT_SCAN_DURATION_MS = 12_000L;
    private static final long READ_REPLY_DELAY_MS = 200L;

    private final Activity activity;
    private final WebView webView;
    private final BleClient bleClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<UUID> enabledReplyCharacteristics = new HashSet<>();

    private boolean fixedReplySupportsRead;
    private boolean pendingReadFallback;
    private boolean enableBluetoothAfterPermission;

    public ZnhaasBleJsBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.bleClient = new BleClient(activity);
        this.bleClient.setBleStateListener(this);
    }

    public BleClient getBleClient() {
        return bleClient;
    }

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
        bleClient.release();
        enabledReplyCharacteristics.clear();
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != DEFAULT_PERMISSION_REQUEST_CODE) {
            return;
        }
        boolean granted = true;
        if (grantResults == null || grantResults.length == 0) {
            granted = false;
        } else {
            for (int grantResult : grantResults) {
                if (grantResult != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
        }
        JSONObject data = baseState();
        put(data, "granted", granted);
        emitState();
        emit("permissionsResult", data);
        if (granted && enableBluetoothAfterPermission) {
            enableBluetoothAfterPermission = false;
            requestEnableBluetooth();
        } else if (!granted) {
            enableBluetoothAfterPermission = false;
        }
    }

    public void onActivityResult(int requestCode) {
        if (requestCode == DEFAULT_ENABLE_BLUETOOTH_REQUEST_CODE) {
            emitState();
        }
    }

    @JavascriptInterface
    public String getState() {
        JSONObject data = baseState();
        emit("state", data);
        return data.toString();
    }

    @JavascriptInterface
    public String getRequiredPermissions() {
        JSONArray permissions = new JSONArray();
        for (String permission : BlePermissionHelper.getRuntimePermissions()) {
            permissions.put(permission);
        }
        JSONObject data = new JSONObject();
        put(data, "permissions", permissions);
        put(data, "hasRequiredPermissions", bleClient.hasRequiredPermissions());
        return data.toString();
    }

    @JavascriptInterface
    public void requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || bleClient.hasRequiredPermissions()) {
            JSONObject data = baseState();
            put(data, "granted", true);
            emit("permissionsResult", data);
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                activity.requestPermissions(BlePermissionHelper.getRuntimePermissions(), DEFAULT_PERMISSION_REQUEST_CODE);
            }
        });
    }

    @JavascriptInterface
    public boolean requestEnableBluetooth() {
        if (!bleClient.isBluetoothSupported()) {
            emitError("bluetooth", "BLE is not supported on this device.");
            return false;
        }
        if (!bleClient.hasRequiredPermissions()) {
            enableBluetoothAfterPermission = true;
            requestPermissions();
            emitError("permission", "Missing BLE runtime permissions. Requesting permissions before enabling Bluetooth.");
            return false;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                boolean handled = bleClient.requestEnableBluetooth(activity, DEFAULT_ENABLE_BLUETOOTH_REQUEST_CODE);
                if (!handled && !bleClient.isBluetoothEnabled()) {
                    emitError("bluetooth", "Unable to request Bluetooth enable. Please check BLE permissions.");
                }
            }
        });
        return true;
    }

    @JavascriptInterface
    public void startScan() {
        startScan(DEFAULT_SCAN_DURATION_MS);
    }

    @JavascriptInterface
    public void startScan(long durationMs) {
        emitLog("Start scanning znhaas BLE devices...");
        final long actualDurationMs = durationMs > 0 ? durationMs : DEFAULT_SCAN_DURATION_MS;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                bleClient.startScan(actualDurationMs, ZnhaasBleJsBridge.this);
            }
        });
    }

    @JavascriptInterface
    public void stopScan() {
        emitLog("Stop scan requested.");
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                bleClient.stopScan();
            }
        });
    }

    @JavascriptInterface
    public void connect(String address) {
        if (address == null || address.trim().isEmpty()) {
            emitError("connect", "Device address is empty.");
            return;
        }
        fixedReplySupportsRead = false;
        pendingReadFallback = false;
        enabledReplyCharacteristics.clear();
        emitLog("Connecting to " + address);
        final String targetAddress = address.trim();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                bleClient.connect(targetAddress, ZnhaasBleJsBridge.this);
            }
        });
    }

    @JavascriptInterface
    public void disconnect() {
        emitLog("Disconnect requested.");
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                bleClient.disconnect();
            }
        });
    }

    @JavascriptInterface
    public String startRecord() {
        return sendAction("startRecord", BleClient.RecordAction.START_RECORD);
    }

    @JavascriptInterface
    public String startRecordJson(String extraFieldsJson) {
        return sendAction("startRecord", BleClient.RecordAction.START_RECORD, extraFieldsJson);
    }

    @JavascriptInterface
    public String stopRecord() {
        return sendAction("stopRecord", BleClient.RecordAction.STOP_RECORD);
    }

    @JavascriptInterface
    public String stopRecordJson(String extraFieldsJson) {
        return sendAction("stopRecord", BleClient.RecordAction.STOP_RECORD, extraFieldsJson);
    }

    @JavascriptInterface
    public String queryRecordStatus() {
        return sendAction("queryRecordStatus", BleClient.RecordAction.QUERY_STATUS);
    }

    @JavascriptInterface
    public String queryRecordStatusJson(String extraFieldsJson) {
        return sendAction("queryRecordStatus", BleClient.RecordAction.QUERY_STATUS, extraFieldsJson);
    }

    @JavascriptInterface
    public String disableVideoKey() {
        return sendAction("disableVideoKey", BleClient.RecordAction.DISABLE_VIDEO_KEY);
    }

    @JavascriptInterface
    public String disableVideoKeyJson(String extraFieldsJson) {
        return sendAction("disableVideoKey", BleClient.RecordAction.DISABLE_VIDEO_KEY, extraFieldsJson);
    }

    @JavascriptInterface
    public String enableVideoKey() {
        return sendAction("enableVideoKey", BleClient.RecordAction.ENABLE_VIDEO_KEY);
    }

    @JavascriptInterface
    public String enableVideoKeyJson(String extraFieldsJson) {
        return sendAction("enableVideoKey", BleClient.RecordAction.ENABLE_VIDEO_KEY, extraFieldsJson);
    }

    @JavascriptInterface
    public void writeCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            emitError("writeCommand", "Command is empty.");
            return;
        }
        final String finalCommand = command;
        JSONObject data = new JSONObject();
        put(data, "action", "writeCommand");
        put(data, "requestId", JSONObject.NULL);
        put(data, "command", finalCommand);
        emit("commandDispatched", data);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                bleClient.writeFixedAsciiCommand(finalCommand, ZnhaasBleJsBridge.this);
            }
        });
    }

    private String sendAction(String actionName, BleClient.RecordAction action) {
        return sendAction(actionName, action, null);
    }

    private String sendAction(String actionName, BleClient.RecordAction action, String extraFieldsJson) {
        final long timestamp = System.currentTimeMillis();
        final String requestId = "req-" + timestamp;
        final Map<String, String> extraFields = parseExtraFields(extraFieldsJson);
        final String command = bleClient.buildRecordCommand(action, requestId, timestamp, extraFields);
        JSONObject data = new JSONObject();
        put(data, "action", actionName);
        put(data, "requestId", requestId);
        put(data, "command", command);
        put(data, "extraFields", extraFieldsToJson(extraFields));
        emit("commandDispatched", data);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                bleClient.writeFixedAsciiCommand(command, ZnhaasBleJsBridge.this);
            }
        });
        return requestId;
    }

    private Map<String, String> parseExtraFields(String extraFieldsJson) {
        Map<String, String> extraFields = new LinkedHashMap<>();
        if (extraFieldsJson == null || extraFieldsJson.trim().isEmpty()) {
            return extraFields;
        }
        try {
            JSONObject json = new JSONObject(extraFieldsJson);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String normalizedKey = key != null ? key.trim() : "";
                String value = json.isNull(key) ? "" : String.valueOf(json.opt(key)).trim();
                if (!normalizedKey.isEmpty() && !value.isEmpty()) {
                    extraFields.put(normalizedKey, value);
                }
            }
        } catch (JSONException exception) {
            emitError("recordCommand", "Invalid extra fields JSON: " + exception.getMessage());
        }
        return extraFields;
    }

    private JSONObject extraFieldsToJson(Map<String, String> extraFields) {
        JSONObject json = new JSONObject();
        if (extraFields == null || extraFields.isEmpty()) {
            return json;
        }
        for (Map.Entry<String, String> entry : extraFields.entrySet()) {
            put(json, entry.getKey(), entry.getValue());
        }
        return json;
    }

    @Override
    public void onBluetoothStateChanged(int state, boolean enabled) {
        JSONObject data = baseState();
        put(data, "stateCode", state);
        put(data, "stateText", bluetoothStateText(state));
        put(data, "enabled", enabled);
        emit("bluetoothStateChanged", data);
    }

    @Override
    public void onScanStarted() {
        emit("scanStarted", new JSONObject());
    }

    @Override
    public void onDeviceFound(BleDevice device) {
        JSONObject data = new JSONObject();
        put(data, "device", deviceToJson(device));
        emit("deviceFound", data);
    }

    @Override
    public void onScanStopped(List<BleDevice> devices) {
        JSONArray array = new JSONArray();
        for (BleDevice device : devices) {
            array.put(deviceToJson(device));
        }
        JSONObject data = new JSONObject();
        put(data, "devices", array);
        put(data, "count", devices.size());
        emit("scanStopped", data);
    }

    @Override
    public void onScanFailed(String message) {
        emitError("scan", message);
    }

    @Override
    public void onDeviceConnecting(BleDevice device) {
        emitDeviceEvent("deviceConnecting", device);
    }

    @Override
    public void onDeviceConnected(BleDevice device) {
        emitDeviceEvent("deviceConnected", device);
    }

    @Override
    public void onServicesDiscovered(BleDevice device, List<BluetoothGattService> services) {
        fixedReplySupportsRead = false;
        pendingReadFallback = false;
        enabledReplyCharacteristics.clear();

        JSONArray serviceArray = new JSONArray();
        for (BluetoothGattService service : services) {
            JSONObject serviceJson = new JSONObject();
            put(serviceJson, "uuid", service.getUuid().toString());
            JSONArray characteristicArray = new JSONArray();
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                JSONObject characteristicJson = characteristicToJson(characteristic);
                characteristicArray.put(characteristicJson);
                if (uuidMatches(service.getUuid(), BleClient.FIXED_SERVICE_UUID)
                        && uuidMatches(characteristic.getUuid(), BleClient.FIXED_NOTIFY_CHARACTERISTIC_UUID)) {
                    fixedReplySupportsRead = hasProperty(characteristic, BluetoothGattCharacteristic.PROPERTY_READ);
                }
            }
            put(serviceJson, "characteristics", characteristicArray);
            serviceArray.put(serviceJson);
        }

        JSONObject data = new JSONObject();
        put(data, "device", deviceToJson(device));
        put(data, "services", serviceArray);
        emit("servicesDiscovered", data);

        bleClient.enableFixedServiceNotifications(this);
    }

    @Override
    public void onDeviceReady(BleDevice device) {
        emitDeviceEvent("deviceReady", device);
    }

    @Override
    public void onDeviceDisconnecting(BleDevice device) {
        emitDeviceEvent("deviceDisconnecting", device);
    }

    @Override
    public void onDeviceDisconnected(BleDevice device) {
        enabledReplyCharacteristics.clear();
        emitDeviceEvent("deviceDisconnected", device);
    }

    @Override
    public void onError(BleDevice device, String message) {
        JSONObject data = new JSONObject();
        put(data, "device", deviceToJson(device));
        put(data, "message", message);
        emit("connectionError", data);
    }

    @Override
    public void onNotifyEnabled(UUID serviceUuid, UUID characteristicUuid) {
        if (uuidMatches(serviceUuid, BleClient.FIXED_SERVICE_UUID)) {
            enabledReplyCharacteristics.add(characteristicUuid);
        }
        JSONObject data = new JSONObject();
        put(data, "serviceUuid", serviceUuid.toString());
        put(data, "characteristicUuid", characteristicUuid.toString());
        emit("replyListenerEnabled", data);
    }

    @Override
    public void onNotifyDisabled(UUID serviceUuid, UUID characteristicUuid) {
        if (uuidMatches(serviceUuid, BleClient.FIXED_SERVICE_UUID)) {
            enabledReplyCharacteristics.remove(characteristicUuid);
        }
        JSONObject data = new JSONObject();
        put(data, "serviceUuid", serviceUuid.toString());
        put(data, "characteristicUuid", characteristicUuid.toString());
        emit("replyListenerDisabled", data);
    }

    @Override
    public void onCharacteristicChanged(UUID serviceUuid, UUID characteristicUuid, byte[] value, String hexValue) {
        String ascii = new String(value, StandardCharsets.UTF_8).trim();
        boolean readFallbackValue = pendingReadFallback && uuidMatches(characteristicUuid, BleClient.FIXED_NOTIFY_CHARACTERISTIC_UUID);
        JSONObject data = new JSONObject();
        put(data, "serviceUuid", serviceUuid.toString());
        put(data, "characteristicUuid", characteristicUuid.toString());
        put(data, "value", ascii);
        put(data, "hexValue", hexValue);
        put(data, "isAck", ascii.startsWith("V1|ACK|"));
        put(data, "isReadFallback", readFallbackValue);
        emit(ascii.startsWith("V1|ACK|") ? "deviceAck" : "deviceReply", data);
        if (readFallbackValue) {
            pendingReadFallback = false;
        }
    }

    @Override
    public void onError(UUID serviceUuid, UUID characteristicUuid, String message) {
        JSONObject data = new JSONObject();
        put(data, "serviceUuid", serviceUuid.toString());
        put(data, "characteristicUuid", characteristicUuid.toString());
        put(data, "message", message);
        emit("replyChannelError", data);
    }

    @Override
    public void onWriteSuccess(UUID serviceUuid, UUID characteristicUuid, byte[] value, String hexValue) {
        String ascii = new String(value, StandardCharsets.UTF_8).trim();
        JSONObject data = new JSONObject();
        put(data, "serviceUuid", serviceUuid.toString());
        put(data, "characteristicUuid", characteristicUuid.toString());
        put(data, "value", ascii);
        put(data, "hexValue", hexValue);
        emit("writeSuccess", data);

        if (uuidMatches(characteristicUuid, BleClient.FIXED_WRITE_CHARACTERISTIC_UUID)
                && !hasReplyListener()
                && fixedReplySupportsRead) {
            scheduleReadFixedReply();
        }
    }

    @Override
    public void onError(UUID serviceUuid, UUID characteristicUuid, byte[] value, String message) {
        JSONObject data = new JSONObject();
        put(data, "serviceUuid", serviceUuid.toString());
        put(data, "characteristicUuid", characteristicUuid.toString());
        put(data, "value", new String(value, StandardCharsets.UTF_8).trim());
        put(data, "message", message);
        emit("writeError", data);
    }

    private boolean hasReplyListener() {
        return !enabledReplyCharacteristics.isEmpty();
    }

    private void scheduleReadFixedReply() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (bleClient.isConnected()) {
                    pendingReadFallback = true;
                    bleClient.readFixedReply(ZnhaasBleJsBridge.this);
                }
            }
        }, READ_REPLY_DELAY_MS);
    }

    private void emitState() {
        emit("state", baseState());
    }

    private JSONObject baseState() {
        JSONObject data = new JSONObject();
        put(data, "bluetoothSupported", bleClient.isBluetoothSupported());
        put(data, "bluetoothEnabled", bleClient.isBluetoothEnabled());
        put(data, "hasRequiredPermissions", bleClient.hasRequiredPermissions());
        put(data, "scanning", bleClient.isScanning());
        put(data, "connected", bleClient.isConnected());
        put(data, "serviceUuid", BleClient.FIXED_SERVICE_UUID);
        put(data, "writeUuid", BleClient.FIXED_WRITE_CHARACTERISTIC_UUID);
        put(data, "replyUuid", BleClient.FIXED_NOTIFY_CHARACTERISTIC_UUID);
        return data;
    }

    private void emitDeviceEvent(String type, BleDevice device) {
        JSONObject data = new JSONObject();
        put(data, "device", deviceToJson(device));
        emit(type, data);
    }

    private void emitError(String source, String message) {
        JSONObject data = new JSONObject();
        put(data, "source", source);
        put(data, "message", message);
        emit("error", data);
    }

    private void emitLog(String message) {
        JSONObject data = new JSONObject();
        put(data, "message", message);
        emit("log", data);
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
                        "window.ZnhaasBle&&window.ZnhaasBle.__dispatch&&window.ZnhaasBle.__dispatch(" + escaped + ");",
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
                + "var direct=['getState','getRequiredPermissions','requestPermissions','requestEnableBluetooth','startScan','stopScan','connect','disconnect','writeCommand'];"
                + "var commands=['startRecord','stopRecord','queryRecordStatus','disableVideoKey','enableVideoKey'];"
                + "function stringify(extra){"
                + "if(!extra){return '';}"
                + "if(typeof extra==='string'){return extra.trim();}"
                + "if(typeof extra!=='object'){return '';}"
                + "var out={};"
                + "Object.keys(extra).forEach(function(key){var value=extra[key]==null?'':extra[key];if(String(key).trim()&&String(value).trim()){out[key]=value;}});"
                + "return Object.keys(out).length?JSON.stringify(out):'';"
                + "}"
                + "var facade={__isZnhaasFacade:true};"
                + "direct.forEach(function(method){facade[method]=function(){return native[method].apply(native,arguments);};});"
                + "commands.forEach(function(method){facade[method]=function(extra){var json=stringify(extra);return json?native[method+'Json'](json):native[method]();};});"
                + "window.__ZnhaasBleBridgeFacade=facade;"
                + "try{window." + targetName + "=facade;}catch(e){}"
                + "})();";
    }

    private JSONObject deviceToJson(BleDevice device) {
        JSONObject json = new JSONObject();
        if (device == null) {
            return json;
        }
        put(json, "name", device.getName());
        put(json, "displayName", device.getDisplayName());
        put(json, "address", device.getAddress());
        put(json, "rssi", device.getRssi());
        put(json, "bondState", device.getBondState());
        return json;
    }

    private JSONObject characteristicToJson(BluetoothGattCharacteristic characteristic) {
        JSONObject json = new JSONObject();
        put(json, "uuid", characteristic.getUuid().toString());
        put(json, "properties", propertiesToText(characteristic));
        put(json, "rawProperties", characteristic.getProperties());
        return json;
    }

    private boolean uuidMatches(UUID uuid, String expected) {
        return uuid != null && uuid.toString().equalsIgnoreCase(expected);
    }

    private boolean hasProperty(BluetoothGattCharacteristic characteristic, int property) {
        return (characteristic.getProperties() & property) != 0;
    }

    private String propertiesToText(BluetoothGattCharacteristic characteristic) {
        StringBuilder builder = new StringBuilder();
        appendProperty(builder, characteristic, BluetoothGattCharacteristic.PROPERTY_READ, "READ");
        appendProperty(builder, characteristic, BluetoothGattCharacteristic.PROPERTY_WRITE, "WRITE");
        appendProperty(builder, characteristic, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, "WRITE_NO_RESPONSE");
        appendProperty(builder, characteristic, BluetoothGattCharacteristic.PROPERTY_NOTIFY, "NOTIFY");
        appendProperty(builder, characteristic, BluetoothGattCharacteristic.PROPERTY_INDICATE, "INDICATE");
        if (builder.length() == 0) {
            return "NONE(" + characteristic.getProperties() + ")";
        }
        return builder.append(" (").append(characteristic.getProperties()).append(")").toString();
    }

    private void appendProperty(StringBuilder builder, BluetoothGattCharacteristic characteristic, int property, String label) {
        if (!hasProperty(characteristic, property)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("|");
        }
        builder.append(label);
    }

    private String bluetoothStateText(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_ON:
                return "STATE_ON";
            case BluetoothAdapter.STATE_TURNING_ON:
                return "STATE_TURNING_ON";
            case BluetoothAdapter.STATE_OFF:
                return "STATE_OFF";
            case BluetoothAdapter.STATE_TURNING_OFF:
                return "STATE_TURNING_OFF";
            default:
                return String.format(Locale.US, "STATE_UNKNOWN(%d)", state);
        }
    }

    private void put(JSONObject object, String key, Object value) {
        try {
            object.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
