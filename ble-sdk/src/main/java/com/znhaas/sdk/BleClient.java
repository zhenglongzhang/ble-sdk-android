package com.znhaas.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.znhaas.sdk.callback.BleConnectionListener;
import com.znhaas.sdk.callback.BleNotifyListener;
import com.znhaas.sdk.callback.BleScanListener;
import com.znhaas.sdk.callback.BleStateListener;
import com.znhaas.sdk.callback.BleWriteListener;
import com.znhaas.sdk.model.BleDevice;
import com.znhaas.sdk.util.BleHexUtils;
import com.znhaas.sdk.util.BlePermissionHelper;
import com.znhaas.sdk.util.BleUuidUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.observer.ConnectionObserver;

public class BleClient {
    private static final long DEFAULT_SCAN_DURATION_MS = 10_000L;
    public static final String TARGET_DEVICE_NAME_PREFIX = "znhaas";
    public static final String FIXED_SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    public static final String FIXED_WRITE_CHARACTERISTIC_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E";
    public static final String FIXED_NOTIFY_CHARACTERISTIC_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E";
    public static final int REQUESTED_MTU = 517;

    public enum RecordAction {
        START_RECORD("1"),
        STOP_RECORD("0"),
        QUERY_STATUS("2"),
        DISABLE_VIDEO_KEY("3"),
        ENABLE_VIDEO_KEY("4");

        private final String code;

        RecordAction(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private final Context appContext;
    private final Handler mainHandler;
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private final Map<String, BleDevice> scannedDevices = new LinkedHashMap<>();

    private BleStateListener bleStateListener;
    private BleScanListener bleScanListener;
    private BleConnectionListener bleConnectionListener;
    private BluetoothLeScanner currentScanner;
    private ScanCallback currentScanCallback;
    private boolean bluetoothStateRegistered;
    private boolean scanning;
    private BleDevice currentDevice;
    private DeviceBleManager deviceBleManager;

    private final Runnable stopScanRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            dispatchState(state);
        }
    };

    public BleClient(Context context) {
        this.appContext = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.bluetoothManager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
    }

    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public boolean hasRequiredPermissions() {
        return BlePermissionHelper.hasPermissions(appContext);
    }

    public String[] getRequiredPermissions() {
        return BlePermissionHelper.getRuntimePermissions();
    }

    public boolean isScanning() {
        return scanning;
    }

    public boolean isConnected() {
        return deviceBleManager != null && deviceBleManager.isConnected();
    }

    public String getFixedServiceUuid() {
        return FIXED_SERVICE_UUID;
    }

    public String getFixedWriteCharacteristicUuid() {
        return FIXED_WRITE_CHARACTERISTIC_UUID;
    }

    public String getFixedNotifyCharacteristicUuid() {
        return FIXED_NOTIFY_CHARACTERISTIC_UUID;
    }

    public static boolean isTargetDeviceName(String deviceName) {
        return BleDevice.isTargetDeviceName(deviceName);
    }

    public static String extractDisplayName(String deviceName) {
        return BleDevice.toDisplayName(deviceName);
    }

    public List<BleDevice> getScannedDevices() {
        return new ArrayList<>(scannedDevices.values());
    }

    public List<BluetoothGattService> getDiscoveredServices() {
        if (deviceBleManager == null) {
            return Collections.emptyList();
        }
        return deviceBleManager.getDiscoveredServices();
    }

    public void setBleStateListener(BleStateListener listener) {
        this.bleStateListener = listener;
        if (listener == null) {
            unregisterBluetoothStateReceiver();
            return;
        }
        registerBluetoothStateReceiver();
        dispatchState(isBluetoothEnabled() ? BluetoothAdapter.STATE_ON : BluetoothAdapter.STATE_OFF);
    }

    public void clearBleStateListener() {
        setBleStateListener(null);
    }

    @SuppressWarnings("deprecation")
    public boolean requestEnableBluetooth(Activity activity, int requestCode) {
        if (!isBluetoothSupported()) {
            return false;
        }
        if (isBluetoothEnabled()) {
            return true;
        }
        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        activity.startActivityForResult(enableIntent, requestCode);
        return true;
    }

    @SuppressLint("MissingPermission")
    public void startScan(long durationMs, BleScanListener listener) {
        this.bleScanListener = listener;
        if (!isBluetoothSupported()) {
            notifyScanFailed("BLE is not supported on this device.");
            return;
        }
        if (!isBluetoothEnabled()) {
            notifyScanFailed("Bluetooth is disabled.");
            return;
        }
        if (!hasRequiredPermissions()) {
            notifyScanFailed("Missing BLE runtime permissions.");
            return;
        }

        stopScanInternal(false);
        scannedDevices.clear();
        BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            notifyScanFailed("Bluetooth LE scanner is unavailable.");
            return;
        }
        currentScanner = scanner;
        currentScanCallback = createScanCallback();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build();

        try {
            currentScanner.startScan(new ArrayList<ScanFilter>(), settings, currentScanCallback);
            scanning = true;
            if (bleScanListener != null) {
                dispatch(new Runnable() {
                    @Override
                    public void run() {
                        bleScanListener.onScanStarted();
                    }
                });
            }
            long actualDuration = durationMs > 0 ? durationMs : DEFAULT_SCAN_DURATION_MS;
            mainHandler.removeCallbacks(stopScanRunnable);
            mainHandler.postDelayed(stopScanRunnable, actualDuration);
        } catch (SecurityException exception) {
            notifyScanFailed("Scan failed because Bluetooth permissions are missing.");
        } catch (Exception exception) {
            notifyScanFailed("Scan failed: " + exception.getMessage());
        }
    }

    public void stopScan() {
        stopScanInternal(true);
    }

    @SuppressLint("MissingPermission")
    public void connect(String address, BleConnectionListener listener) {
        if (address == null || address.trim().isEmpty()) {
            notifyConnectionError(listener, null, "Device address is empty.");
            return;
        }
        if (!isBluetoothSupported()) {
            notifyConnectionError(listener, null, "BLE is not supported on this device.");
            return;
        }
        if (!isBluetoothEnabled()) {
            notifyConnectionError(listener, null, "Bluetooth is disabled.");
            return;
        }
        if (!hasRequiredPermissions()) {
            notifyConnectionError(listener, null, "Missing BLE runtime permissions.");
            return;
        }
        try {
            BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(address.trim());
            connect(bluetoothDevice, listener);
        } catch (IllegalArgumentException exception) {
            notifyConnectionError(listener, null, "Invalid BLE address: " + address);
        }
    }

    @SuppressLint("MissingPermission")
    public void connect(BleDevice device, BleConnectionListener listener) {
        if (device == null) {
            notifyConnectionError(listener, null, "Device is null.");
            return;
        }
        connect(device.getBluetoothDevice(), listener);
    }

    @SuppressLint("MissingPermission")
    public void enableNotification(String serviceUuid, String characteristicUuid, BleNotifyListener listener) {
        if (deviceBleManager == null || !deviceBleManager.isConnected()) {
            notifyNotifyError(listener, serviceUuid, characteristicUuid, "No BLE device is connected.");
            return;
        }
        try {
            deviceBleManager.enableNotification(
                    BleUuidUtils.fromString(serviceUuid),
                    BleUuidUtils.fromString(characteristicUuid),
                    listener
            );
        } catch (IllegalArgumentException exception) {
            notifyNotifyError(listener, serviceUuid, characteristicUuid, exception.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    public void enableFixedServiceNotifications(BleNotifyListener listener) {
        if (deviceBleManager == null || !deviceBleManager.isConnected()) {
            notifyNotifyError(listener, FIXED_SERVICE_UUID, FIXED_NOTIFY_CHARACTERISTIC_UUID, "No BLE device is connected.");
            return;
        }
        try {
            deviceBleManager.enableServiceNotifications(BleUuidUtils.fromString(FIXED_SERVICE_UUID), listener);
        } catch (IllegalArgumentException exception) {
            notifyNotifyError(listener, FIXED_SERVICE_UUID, FIXED_NOTIFY_CHARACTERISTIC_UUID, exception.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    public void disableNotification(String serviceUuid, String characteristicUuid) {
        if (deviceBleManager == null) {
            return;
        }
        try {
            deviceBleManager.disableNotification(
                    BleUuidUtils.fromString(serviceUuid),
                    BleUuidUtils.fromString(characteristicUuid)
            );
        } catch (IllegalArgumentException ignored) {
        }
    }

    @SuppressLint("MissingPermission")
    public void read(String serviceUuid, String characteristicUuid, BleNotifyListener listener) {
        if (deviceBleManager == null || !deviceBleManager.isConnected()) {
            notifyNotifyError(listener, serviceUuid, characteristicUuid, "No BLE device is connected.");
            return;
        }
        try {
            deviceBleManager.read(
                    BleUuidUtils.fromString(serviceUuid),
                    BleUuidUtils.fromString(characteristicUuid),
                    listener
            );
        } catch (IllegalArgumentException exception) {
            notifyNotifyError(listener, serviceUuid, characteristicUuid, exception.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    public void readFixedReply(BleNotifyListener listener) {
        read(FIXED_SERVICE_UUID, FIXED_NOTIFY_CHARACTERISTIC_UUID, listener);
    }

    @SuppressLint("MissingPermission")
    public void write(String serviceUuid, String characteristicUuid, byte[] value, BleWriteListener listener) {
        if (deviceBleManager == null || !deviceBleManager.isConnected()) {
            notifyWriteError(listener, serviceUuid, characteristicUuid, value, "No BLE device is connected.");
            return;
        }
        try {
            deviceBleManager.write(
                    BleUuidUtils.fromString(serviceUuid),
                    BleUuidUtils.fromString(characteristicUuid),
                    value,
                    listener
            );
        } catch (IllegalArgumentException exception) {
            notifyWriteError(listener, serviceUuid, characteristicUuid, value, exception.getMessage());
        }
    }

    public void disconnect() {
        if (deviceBleManager != null) {
            deviceBleManager.disconnect().enqueue();
        }
    }

    public String startRecord(BleWriteListener listener) {
        return sendRecordAction(RecordAction.START_RECORD, listener);
    }

    public String stopRecord(BleWriteListener listener) {
        return sendRecordAction(RecordAction.STOP_RECORD, listener);
    }

    public String queryRecordStatus(BleWriteListener listener) {
        return sendRecordAction(RecordAction.QUERY_STATUS, listener);
    }

    public String disableVideoKey(BleWriteListener listener) {
        return sendRecordAction(RecordAction.DISABLE_VIDEO_KEY, listener);
    }

    public String enableVideoKey(BleWriteListener listener) {
        return sendRecordAction(RecordAction.ENABLE_VIDEO_KEY, listener);
    }

    public String sendRecordAction(RecordAction action, BleWriteListener listener) {
        String requestId = buildRequestId(action);
        long timestamp = System.currentTimeMillis();
        String command = buildRecordCommand(action, requestId, timestamp);
        writeFixedAsciiCommand(command, listener);
        return requestId;
    }

    public void writeFixedAsciiCommand(String command, BleWriteListener listener) {
        byte[] payload = command.getBytes(StandardCharsets.UTF_8);
        write(FIXED_SERVICE_UUID, FIXED_WRITE_CHARACTERISTIC_UUID, payload, listener);
    }

    public String buildRecordCommand(RecordAction action, String requestId, long timestamp) {
        return "V1|RECORD|" + action.getCode() + "|" + requestId + "|" + timestamp;
    }

    public void release() {
        mainHandler.removeCallbacks(stopScanRunnable);
        stopScanInternal(false);
        unregisterBluetoothStateReceiver();
        if (deviceBleManager != null) {
            deviceBleManager.close();
            deviceBleManager = null;
        }
        bleScanListener = null;
        bleStateListener = null;
        bleConnectionListener = null;
        currentDevice = null;
    }

    @SuppressLint("MissingPermission")
    private void connect(BluetoothDevice bluetoothDevice, BleConnectionListener listener) {
        if (bluetoothDevice == null) {
            notifyConnectionError(listener, null, "BluetoothDevice is null.");
            return;
        }
        stopScanInternal(false);
        if (deviceBleManager != null) {
            deviceBleManager.close();
        }
        this.bleConnectionListener = listener;
        this.currentDevice = BleDevice.from(bluetoothDevice, 0);
        this.deviceBleManager = new DeviceBleManager(appContext, mainHandler, new InternalBleListener() {
            @Override
            public void onDeviceConnecting(BluetoothDevice device) {
                currentDevice = BleDevice.from(device, 0);
                if (bleConnectionListener != null) {
                    dispatch(new Runnable() {
                        @Override
                        public void run() {
                            bleConnectionListener.onDeviceConnecting(currentDevice);
                        }
                    });
                }
            }

            @Override
            public void onDeviceConnected(BluetoothDevice device) {
                currentDevice = BleDevice.from(device, 0);
                if (bleConnectionListener != null) {
                    dispatch(new Runnable() {
                        @Override
                        public void run() {
                            bleConnectionListener.onDeviceConnected(currentDevice);
                        }
                    });
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothDevice device, List<BluetoothGattService> services) {
                currentDevice = BleDevice.from(device, 0);
                if (bleConnectionListener != null) {
                    dispatch(new Runnable() {
                        @Override
                        public void run() {
                            bleConnectionListener.onServicesDiscovered(currentDevice, services);
                        }
                    });
                }
            }

            @Override
            public void onDeviceReady(BluetoothDevice device) {
                currentDevice = BleDevice.from(device, 0);
                if (bleConnectionListener != null) {
                    dispatch(new Runnable() {
                        @Override
                        public void run() {
                            bleConnectionListener.onDeviceReady(currentDevice);
                        }
                    });
                }
            }

            @Override
            public void onDeviceDisconnecting(BluetoothDevice device) {
                currentDevice = BleDevice.from(device, 0);
                if (bleConnectionListener != null) {
                    dispatch(new Runnable() {
                        @Override
                        public void run() {
                            bleConnectionListener.onDeviceDisconnecting(currentDevice);
                        }
                    });
                }
            }

            @Override
            public void onDeviceDisconnected(BluetoothDevice device) {
                BleDevice disconnected = BleDevice.from(device, 0);
                if (bleConnectionListener != null) {
                    dispatch(new Runnable() {
                        @Override
                        public void run() {
                            bleConnectionListener.onDeviceDisconnected(disconnected);
                        }
                    });
                }
                if (deviceBleManager != null) {
                    deviceBleManager.close();
                    deviceBleManager = null;
                }
            }

            @Override
            public void onError(BluetoothDevice device, String message) {
                BleDevice errorDevice = device != null ? BleDevice.from(device, 0) : currentDevice;
                notifyConnectionError(bleConnectionListener, errorDevice, message);
            }
        });

        deviceBleManager.connect(bluetoothDevice)
                .useAutoConnect(false)
                .retry(2, 200)
                .timeout(10_000)
                .enqueue();
    }

    private void stopScanInternal(boolean notifyStopped) {
        mainHandler.removeCallbacks(stopScanRunnable);
        boolean wasScanning = scanning;
        scanning = false;
        if (currentScanCallback != null) {
            try {
                BluetoothLeScanner scanner = currentScanner;
                if (scanner == null && bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                    scanner = bluetoothAdapter.getBluetoothLeScanner();
                }
                if (scanner != null) {
                    scanner.stopScan(currentScanCallback);
                }
            } catch (Exception ignored) {
            }
            currentScanner = null;
            currentScanCallback = null;
        }
        if (notifyStopped && wasScanning && bleScanListener != null) {
            List<BleDevice> snapshot = getScannedDevices();
            dispatch(new Runnable() {
                @Override
                public void run() {
                    bleScanListener.onScanStopped(snapshot);
                }
            });
        }
    }

    private ScanCallback createScanCallback() {
        return new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                handleScanResult(result);
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                for (ScanResult result : results) {
                    handleScanResult(result);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                scanning = false;
                notifyScanFailed("Scan failed: " + mapScanError(errorCode));
            }
        };
    }

    private void handleScanResult(ScanResult result) {
        if (result == null || result.getDevice() == null) {
            return;
        }
        final BleDevice device = BleDevice.from(result.getDevice(), result.getRssi());
        if (device == null) {
            return;
        }
        if (!device.isZnhaasDevice()) {
            return;
        }
        scannedDevices.put(device.getAddress(), device);
        if (bleScanListener != null) {
            dispatch(new Runnable() {
                @Override
                public void run() {
                    bleScanListener.onDeviceFound(device);
                }
            });
        }
    }

    private void registerBluetoothStateReceiver() {
        if (bluetoothStateRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            appContext.registerReceiver(bluetoothStateReceiver, filter);
        }
        bluetoothStateRegistered = true;
    }

    private void unregisterBluetoothStateReceiver() {
        if (!bluetoothStateRegistered) {
            return;
        }
        try {
            appContext.unregisterReceiver(bluetoothStateReceiver);
        } catch (Exception ignored) {
        }
        bluetoothStateRegistered = false;
    }

    private void dispatchState(final int state) {
        if (bleStateListener == null) {
            return;
        }
        dispatch(new Runnable() {
            @Override
            public void run() {
                bleStateListener.onBluetoothStateChanged(state, state == BluetoothAdapter.STATE_ON);
            }
        });
    }

    private void notifyScanFailed(final String message) {
        scanning = false;
        if (bleScanListener == null) {
            return;
        }
        dispatch(new Runnable() {
            @Override
            public void run() {
                bleScanListener.onScanFailed(message);
            }
        });
    }

    private void notifyConnectionError(final BleConnectionListener listener, final BleDevice device, final String message) {
        if (listener == null) {
            return;
        }
        dispatch(new Runnable() {
            @Override
            public void run() {
                listener.onError(device, message);
            }
        });
    }

    private void notifyNotifyError(BleNotifyListener listener, String serviceUuid, String characteristicUuid, String message) {
        if (listener == null) {
            return;
        }
        try {
            listener.onError(BleUuidUtils.fromString(serviceUuid), BleUuidUtils.fromString(characteristicUuid), message);
        } catch (Exception exception) {
            listener.onError(new UUID(0L, 0L), new UUID(0L, 0L), message);
        }
    }

    private void notifyWriteError(BleWriteListener listener, String serviceUuid, String characteristicUuid, byte[] value, String message) {
        if (listener == null) {
            return;
        }
        try {
            listener.onError(BleUuidUtils.fromString(serviceUuid), BleUuidUtils.fromString(characteristicUuid), value, message);
        } catch (Exception exception) {
            listener.onError(new UUID(0L, 0L), new UUID(0L, 0L), value, message);
        }
    }

    private void dispatch(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    private String mapScanError(int errorCode) {
        switch (errorCode) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                return "already started";
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                return "application registration failed";
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                return "feature unsupported";
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                return "internal error";
            default:
                return "code=" + errorCode;
        }
    }

    private String buildRequestId(RecordAction action) {
        String actionName = action.name().toLowerCase(Locale.US);
        return "req-" + System.currentTimeMillis();
    }

    private interface InternalBleListener {
        void onDeviceConnecting(BluetoothDevice device);

        void onDeviceConnected(BluetoothDevice device);

        void onServicesDiscovered(BluetoothDevice device, List<BluetoothGattService> services);

        void onDeviceReady(BluetoothDevice device);

        void onDeviceDisconnecting(BluetoothDevice device);

        void onDeviceDisconnected(BluetoothDevice device);

        void onError(BluetoothDevice device, String message);
    }

    private static final class DeviceBleManager extends BleManager {
        private final InternalBleListener internalBleListener;
        private final Map<String, BleNotifyListener> activeNotifyListeners = new ConcurrentHashMap<>();
        private final List<BluetoothGattService> discoveredServices = new ArrayList<>();
        private BluetoothGatt bluetoothGatt;

        DeviceBleManager(Context context, Handler handler, InternalBleListener internalBleListener) {
            super(context, handler);
            this.internalBleListener = internalBleListener;
            setConnectionObserver(new ConnectionObserver() {
                @Override
                public void onDeviceConnecting(BluetoothDevice device) {
                    internalBleListener.onDeviceConnecting(device);
                }

                @Override
                public void onDeviceConnected(BluetoothDevice device) {
                    internalBleListener.onDeviceConnected(device);
                }

                @Override
                public void onDeviceFailedToConnect(BluetoothDevice device, int reason) {
                    internalBleListener.onError(device, "Failed to connect, reason=" + reason);
                }

                @Override
                public void onDeviceReady(BluetoothDevice device) {
                    internalBleListener.onDeviceReady(device);
                }

                @Override
                public void onDeviceDisconnecting(BluetoothDevice device) {
                    internalBleListener.onDeviceDisconnecting(device);
                }

                @Override
                public void onDeviceDisconnected(BluetoothDevice device, int reason) {
                    internalBleListener.onDeviceDisconnected(device);
                }
            });
        }

        @Override
        protected boolean isRequiredServiceSupported(BluetoothGatt gatt) {
            bluetoothGatt = gatt;
            discoveredServices.clear();
            discoveredServices.addAll(gatt.getServices());
            internalBleListener.onServicesDiscovered(gatt.getDevice(), getDiscoveredServices());
            return true;
        }

        @Override
        protected void initialize() {
            requestMtu(REQUESTED_MTU).enqueue();
        }

        @Override
        protected void onServicesInvalidated() {
            bluetoothGatt = null;
            discoveredServices.clear();
            activeNotifyListeners.clear();
        }

        List<BluetoothGattService> getDiscoveredServices() {
            return new ArrayList<>(discoveredServices);
        }

        void enableNotification(final UUID serviceUuid, final UUID characteristicUuid, final BleNotifyListener listener) {
            final BluetoothGattCharacteristic characteristic = findCharacteristic(serviceUuid, characteristicUuid);
            if (characteristic == null) {
                listener.onError(serviceUuid, characteristicUuid, "Notify characteristic was not found.");
                return;
            }
            final String key = buildKey(serviceUuid, characteristicUuid);
            setNotificationCallback(characteristic).with((device, data) -> {
                byte[] value = data != null ? data.getValue() : new byte[0];
                BleNotifyListener activeListener = activeNotifyListeners.get(key);
                if (activeListener != null) {
                    activeListener.onCharacteristicChanged(
                            serviceUuid,
                            characteristicUuid,
                            value,
                            BleHexUtils.toHex(value)
                    );
                }
            });

            if (supportsNotify(characteristic)) {
                enableNotifications(characteristic)
                        .done(device -> {
                            activeNotifyListeners.put(key, listener);
                            listener.onNotifyEnabled(serviceUuid, characteristicUuid);
                        })
                        .fail((device, status) -> {
                            if (enableLocalNotification(characteristic)) {
                                activeNotifyListeners.put(key, listener);
                                listener.onNotifyEnabled(serviceUuid, characteristicUuid);
                                return;
                            }
                            listener.onError(
                                    serviceUuid,
                                    characteristicUuid,
                                    "Failed to enable notify, status=" + status
                            );
                        })
                        .enqueue();
                return;
            }

            if (supportsIndicate(characteristic)) {
                enableIndications(characteristic)
                        .done(device -> {
                            activeNotifyListeners.put(key, listener);
                            listener.onNotifyEnabled(serviceUuid, characteristicUuid);
                        })
                        .fail((device, status) -> {
                            if (enableLocalNotification(characteristic)) {
                                activeNotifyListeners.put(key, listener);
                                listener.onNotifyEnabled(serviceUuid, characteristicUuid);
                                return;
                            }
                            listener.onError(
                                    serviceUuid,
                                    characteristicUuid,
                                    "Failed to enable indicate, status=" + status
                            );
                        })
                        .enqueue();
                return;
            }

            if (enableLocalNotification(characteristic)) {
                activeNotifyListeners.put(key, listener);
                listener.onNotifyEnabled(serviceUuid, characteristicUuid);
                return;
            }

            listener.onError(serviceUuid, characteristicUuid, "Characteristic does not support notify or indicate.");
        }

        void enableServiceNotifications(final UUID serviceUuid, final BleNotifyListener listener) {
            if (bluetoothGatt == null) {
                listener.onError(serviceUuid, new UUID(0L, 0L), "No GATT connection is available.");
                return;
            }
            BluetoothGattService service = bluetoothGatt.getService(serviceUuid);
            if (service == null) {
                listener.onError(serviceUuid, new UUID(0L, 0L), "Fixed service was not found.");
                return;
            }
            boolean attempted = false;
            BluetoothGattCharacteristic fixedReply = service.getCharacteristic(BleUuidUtils.fromString(FIXED_NOTIFY_CHARACTERISTIC_UUID));
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                if (supportsNotify(characteristic) || supportsIndicate(characteristic)) {
                    attempted = true;
                    enableNotification(serviceUuid, characteristic.getUuid(), listener);
                }
            }
            if (fixedReply != null && !supportsNotify(fixedReply) && !supportsIndicate(fixedReply)) {
                attempted = true;
                enableNotification(serviceUuid, fixedReply.getUuid(), listener);
            }
            if (!attempted) {
                listener.onError(serviceUuid, BleUuidUtils.fromString(FIXED_NOTIFY_CHARACTERISTIC_UUID), "No reply characteristic can be listened.");
            }
        }

        void disableNotification(final UUID serviceUuid, final UUID characteristicUuid) {
            final BluetoothGattCharacteristic characteristic = findCharacteristic(serviceUuid, characteristicUuid);
            final BleNotifyListener listener = activeNotifyListeners.get(buildKey(serviceUuid, characteristicUuid));
            if (characteristic == null) {
                if (listener != null) {
                    listener.onError(serviceUuid, characteristicUuid, "Notify characteristic was not found.");
                }
                return;
            }

            if (supportsNotify(characteristic)) {
                disableNotifications(characteristic)
                        .done(device -> {
                            BleNotifyListener active = activeNotifyListeners.remove(buildKey(serviceUuid, characteristicUuid));
                            if (active != null) {
                                active.onNotifyDisabled(serviceUuid, characteristicUuid);
                            }
                        })
                        .fail((device, status) -> {
                            BleNotifyListener active = activeNotifyListeners.get(buildKey(serviceUuid, characteristicUuid));
                            if (active != null) {
                                active.onError(serviceUuid, characteristicUuid, "Failed to disable notify, status=" + status);
                            }
                        })
                        .enqueue();
                return;
            }

            if (supportsIndicate(characteristic)) {
                disableIndications(characteristic)
                        .done(device -> {
                            BleNotifyListener active = activeNotifyListeners.remove(buildKey(serviceUuid, characteristicUuid));
                            if (active != null) {
                                active.onNotifyDisabled(serviceUuid, characteristicUuid);
                            }
                        })
                        .fail((device, status) -> {
                            BleNotifyListener active = activeNotifyListeners.get(buildKey(serviceUuid, characteristicUuid));
                            if (active != null) {
                                active.onError(serviceUuid, characteristicUuid, "Failed to disable indicate, status=" + status);
                            }
                        })
                        .enqueue();
                return;
            }

            if (disableLocalNotification(characteristic)) {
                BleNotifyListener active = activeNotifyListeners.remove(buildKey(serviceUuid, characteristicUuid));
                if (active != null) {
                    active.onNotifyDisabled(serviceUuid, characteristicUuid);
                }
            }
        }

        void read(final UUID serviceUuid, final UUID characteristicUuid, final BleNotifyListener listener) {
            final BluetoothGattCharacteristic characteristic = findCharacteristic(serviceUuid, characteristicUuid);
            if (characteristic == null) {
                listener.onError(serviceUuid, characteristicUuid, "Read characteristic was not found.");
                return;
            }
            if (!supportsRead(characteristic)) {
                listener.onError(serviceUuid, characteristicUuid, "Characteristic does not support read.");
                return;
            }

            readCharacteristic(characteristic)
                    .with((device, data) -> {
                        byte[] value = data != null ? data.getValue() : new byte[0];
                        listener.onCharacteristicChanged(
                                serviceUuid,
                                characteristicUuid,
                                value,
                                BleHexUtils.toHex(value)
                        );
                    })
                    .fail((device, status) -> listener.onError(
                            serviceUuid,
                            characteristicUuid,
                            "Failed to read characteristic, status=" + status
                    ))
                    .enqueue();
        }

        void write(final UUID serviceUuid, final UUID characteristicUuid, final byte[] value, final BleWriteListener listener) {
            final BluetoothGattCharacteristic characteristic = findCharacteristic(serviceUuid, characteristicUuid);
            if (characteristic == null) {
                listener.onError(serviceUuid, characteristicUuid, value, "Write characteristic was not found.");
                return;
            }
            int writeType = supportsWriteWithoutResponse(characteristic)
                    ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
            writeCharacteristic(characteristic, value, writeType)
                    .done(device -> listener.onWriteSuccess(
                            serviceUuid,
                            characteristicUuid,
                            value,
                            BleHexUtils.toHex(value)
                    ))
                    .fail((device, status) -> listener.onError(
                            serviceUuid,
                            characteristicUuid,
                            value,
                            "Failed to write characteristic, status=" + status
                    ))
                    .enqueue();
        }

        private BluetoothGattCharacteristic findCharacteristic(UUID serviceUuid, UUID characteristicUuid) {
            if (bluetoothGatt == null) {
                return null;
            }
            BluetoothGattService service = bluetoothGatt.getService(serviceUuid);
            if (service == null) {
                return null;
            }
            return service.getCharacteristic(characteristicUuid);
        }

        private String buildKey(UUID serviceUuid, UUID characteristicUuid) {
            return serviceUuid + "#" + characteristicUuid;
        }

        private boolean supportsNotify(BluetoothGattCharacteristic characteristic) {
            return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
        }

        private boolean supportsIndicate(BluetoothGattCharacteristic characteristic) {
            return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
        }

        private boolean supportsRead(BluetoothGattCharacteristic characteristic) {
            return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0;
        }

        private boolean supportsWriteWithoutResponse(BluetoothGattCharacteristic characteristic) {
            return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
        }

        @SuppressLint("MissingPermission")
        private boolean enableLocalNotification(BluetoothGattCharacteristic characteristic) {
            return bluetoothGatt != null && bluetoothGatt.setCharacteristicNotification(characteristic, true);
        }

        @SuppressLint("MissingPermission")
        private boolean disableLocalNotification(BluetoothGattCharacteristic characteristic) {
            return bluetoothGatt != null && bluetoothGatt.setCharacteristicNotification(characteristic, false);
        }
    }
}
