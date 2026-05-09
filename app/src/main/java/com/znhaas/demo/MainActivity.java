package com.znhaas.demo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.znhaas.demo.databinding.ActivityMainBinding;
import com.znhaas.sdk.BleClient;
import com.znhaas.sdk.callback.BleConnectionListener;
import com.znhaas.sdk.callback.BleNotifyListener;
import com.znhaas.sdk.callback.BleScanListener;
import com.znhaas.sdk.callback.BleStateListener;
import com.znhaas.sdk.callback.BleWriteListener;
import com.znhaas.sdk.model.BleDevice;
import com.znhaas.sdk.util.BleHexUtils;
import com.znhaas.sdk.util.BlePermissionHelper;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements
        BleStateListener,
        BleScanListener,
        BleConnectionListener,
        BleNotifyListener,
        BleWriteListener {

    private static final int REQUEST_ENABLE_BLUETOOTH = 1001;
    private static final int REQUEST_BLE_PERMISSIONS = 1002;
    private static final long SCAN_DURATION_MS = 12_000L;

    private ActivityMainBinding binding;
    private BleClient bleClient;
    private BleDeviceAdapter bleDeviceAdapter;
    private Runnable pendingAction;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private interface CommandTrigger {
        String trigger();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        bleClient = new BleClient(this);
        bleClient.setBleStateListener(this);

        bleDeviceAdapter = new BleDeviceAdapter(device -> {
            binding.etDeviceAddress.setText(device.getAddress());
            log("Selected device: " + device.getDisplayName() + " / " + device.getAddress());
        });
        binding.rvDevices.setLayoutManager(new LinearLayoutManager(this));
        binding.rvDevices.setAdapter(bleDeviceAdapter);

        initScreen();
        bindClicks();
        logPermissionState();
        logBluetoothState();
    }

    private void initScreen() {
        binding.etDeviceAddress.setText("");
        binding.tvUuidInfo.setText(
                "Service UUID: " + BleClient.FIXED_SERVICE_UUID + "\n"
                        + "Write UUID: " + BleClient.FIXED_WRITE_CHARACTERISTIC_UUID + "\n"
                        + "Notify UUID: " + BleClient.FIXED_NOTIFY_CHARACTERISTIC_UUID
        );
        log("Only scanning BLE names with prefix: " + BleClient.TARGET_DEVICE_NAME_PREFIX);
        log("Matched example: znhaas_23070401 -> display 23070401");
    }

    private void bindClicks() {
        binding.btnCheckBluetooth.setOnClickListener(view -> logBluetoothState());
        binding.btnEnableBluetooth.setOnClickListener(view -> ensurePermissions(() -> {
            boolean handled = bleClient.requestEnableBluetooth(this, REQUEST_ENABLE_BLUETOOTH);
            if (!handled) {
                log("This device does not support BLE.");
            } else if (bleClient.isBluetoothEnabled()) {
                log("Bluetooth is already enabled.");
            } else {
                log("Requested system Bluetooth enable dialog.");
            }
        }));
        binding.btnStartScan.setOnClickListener(view -> ensurePermissions(() -> {
            bleDeviceAdapter.clear();
            log("Start scanning znhaas BLE devices...");
            bleClient.startScan(SCAN_DURATION_MS, this);
        }));
        binding.btnStopScan.setOnClickListener(view -> {
            bleClient.stopScan();
            log("Stop scan requested.");
        });
        binding.btnConnect.setOnClickListener(view -> ensurePermissions(() -> {
            String address = binding.etDeviceAddress.getText().toString().trim();
            log("Connecting to " + address);
            bleClient.connect(address, this);
        }));
        binding.btnDisconnect.setOnClickListener(view -> {
            bleClient.disconnect();
            log("Disconnect requested.");
        });
        binding.btnStartRecord.setOnClickListener(view ->
                ensurePermissions(() -> triggerCommand("Start record", () -> bleClient.startRecord(this))));
        binding.btnStopRecord.setOnClickListener(view ->
                ensurePermissions(() -> triggerCommand("Stop record", () -> bleClient.stopRecord(this))));
        binding.btnQueryStatus.setOnClickListener(view ->
                ensurePermissions(() -> triggerCommand("Query record status", () -> bleClient.queryRecordStatus(this))));
        binding.btnDisableVideoKey.setOnClickListener(view ->
                ensurePermissions(() -> triggerCommand("Disable video key", () -> bleClient.disableVideoKey(this))));
        binding.btnEnableVideoKey.setOnClickListener(view ->
                ensurePermissions(() -> triggerCommand("Enable video key", () -> bleClient.enableVideoKey(this))));
        binding.btnClearLog.setOnClickListener(view -> binding.tvLog.setText(""));
    }

    private void triggerCommand(String label, CommandTrigger trigger) {
        String requestId = trigger.trigger();
        log(label + " sent, requestId=" + requestId);
    }

    private void ensurePermissions(Runnable action) {
        if (BlePermissionHelper.hasPermissions(this)) {
            action.run();
            return;
        }
        pendingAction = action;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(BlePermissionHelper.getRuntimePermissions(), REQUEST_BLE_PERMISSIONS);
        }
    }

    private void logPermissionState() {
        if (BlePermissionHelper.hasPermissions(this)) {
            log("BLE runtime permissions are already granted.");
        } else {
            log("BLE runtime permissions are not granted yet.");
        }
    }

    private void logBluetoothState() {
        if (!bleClient.isBluetoothSupported()) {
            log("This device does not support BLE.");
            return;
        }
        log("Bluetooth enabled: " + bleClient.isBluetoothEnabled());
    }

    @Override
    public void onBluetoothStateChanged(int state, boolean enabled) {
        String stateText;
        switch (state) {
            case BluetoothAdapter.STATE_ON:
                stateText = "STATE_ON";
                break;
            case BluetoothAdapter.STATE_TURNING_ON:
                stateText = "STATE_TURNING_ON";
                break;
            case BluetoothAdapter.STATE_OFF:
                stateText = "STATE_OFF";
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                stateText = "STATE_TURNING_OFF";
                break;
            default:
                stateText = "STATE_UNKNOWN(" + state + ")";
                break;
        }
        log("Bluetooth state changed: " + stateText + ", enabled=" + enabled);
    }

    @Override
    public void onScanStarted() {
        log("Scan started.");
    }

    @Override
    public void onDeviceFound(BleDevice device) {
        bleDeviceAdapter.upsert(device);
        log("Found target device: " + device.getDisplayName() + " / " + device.getAddress());
    }

    @Override
    public void onScanStopped(List<BleDevice> devices) {
        log("Scan stopped. Matched devices: " + devices.size());
    }

    @Override
    public void onScanFailed(String message) {
        log("Scan failed: " + message);
    }

    @Override
    public void onDeviceConnecting(BleDevice device) {
        log("Connecting: " + device.getDisplayName() + " / " + device.getAddress());
    }

    @Override
    public void onDeviceConnected(BleDevice device) {
        log("Connected: " + device.getAddress());
    }

    @Override
    public void onServicesDiscovered(BleDevice device, List<BluetoothGattService> services) {
        boolean serviceFound = false;
        boolean writeFound = false;
        boolean notifyFound = false;

        for (BluetoothGattService service : services) {
            if (!uuidMatches(service.getUuid(), BleClient.FIXED_SERVICE_UUID)) {
                continue;
            }
            serviceFound = true;
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                if (uuidMatches(characteristic.getUuid(), BleClient.FIXED_WRITE_CHARACTERISTIC_UUID)) {
                    writeFound = true;
                }
                if (uuidMatches(characteristic.getUuid(), BleClient.FIXED_NOTIFY_CHARACTERISTIC_UUID)) {
                    notifyFound = true;
                }
            }
        }

        log("Fixed service found=" + serviceFound + ", write characteristic found=" + writeFound + ", notify characteristic found=" + notifyFound);
        if (notifyFound) {
            log("Trying to enable fixed notify channel...");
            bleClient.enableNotification(BleClient.FIXED_SERVICE_UUID, BleClient.FIXED_NOTIFY_CHARACTERISTIC_UUID, this);
        }
    }

    @Override
    public void onDeviceReady(BleDevice device) {
        log("Device ready: " + device.getAddress());
    }

    @Override
    public void onDeviceDisconnecting(BleDevice device) {
        log("Disconnecting: " + device.getAddress());
    }

    @Override
    public void onDeviceDisconnected(BleDevice device) {
        log("Disconnected: " + device.getAddress());
    }

    @Override
    public void onError(BleDevice device, String message) {
        String deviceAddress = device == null ? "N/A" : device.getAddress();
        log("Connection error [" + deviceAddress + "]: " + message);
    }

    @Override
    public void onNotifyEnabled(UUID serviceUuid, UUID characteristicUuid) {
        log("Notify enabled: " + characteristicUuid);
    }

    @Override
    public void onNotifyDisabled(UUID serviceUuid, UUID characteristicUuid) {
        log("Notify disabled: " + characteristicUuid);
    }

    @Override
    public void onCharacteristicChanged(UUID serviceUuid, UUID characteristicUuid, byte[] value, String hexValue) {
        String ascii = new String(value, StandardCharsets.UTF_8).trim();
        log("Device reply [" + characteristicUuid + "]: " + ascii + " | hex=" + hexValue);
    }

    @Override
    public void onError(UUID serviceUuid, UUID characteristicUuid, String message) {
        log("Notify error [" + characteristicUuid + "]: " + message);
    }

    @Override
    public void onWriteSuccess(UUID serviceUuid, UUID characteristicUuid, byte[] value, String hexValue) {
        String ascii = new String(value, StandardCharsets.UTF_8).trim();
        log("Write success [" + characteristicUuid + "]: " + ascii + " | hex=" + BleHexUtils.toHex(value));
    }

    @Override
    public void onError(UUID serviceUuid, UUID characteristicUuid, byte[] value, String message) {
        log("Write error [" + characteristicUuid + "]: " + message);
    }

    private boolean uuidMatches(UUID uuid, String expected) {
        return uuid != null && uuid.toString().equalsIgnoreCase(expected);
    }

    private void log(String message) {
        String timestamp = timeFormat.format(new Date());
        String line = "[" + timestamp + "] " + message + "\n";
        binding.tvLog.append(line);
        binding.logScroll.post(() -> binding.logScroll.fullScroll(android.view.View.FOCUS_DOWN));
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            log("Bluetooth enable result received. Current enabled state=" + bleClient.isBluetoothEnabled());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_BLE_PERMISSIONS) {
            return;
        }
        boolean granted = true;
        for (int grantResult : grantResults) {
            if (grantResult != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }
        if (!granted) {
            pendingAction = null;
            log("BLE permissions denied.");
            return;
        }
        log("BLE permissions granted.");
        if (pendingAction != null) {
            Runnable action = pendingAction;
            pendingAction = null;
            action.run();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleClient != null) {
            bleClient.release();
        }
        binding = null;
    }
}
