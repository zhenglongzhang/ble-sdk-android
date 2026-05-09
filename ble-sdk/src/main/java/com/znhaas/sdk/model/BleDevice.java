package com.znhaas.sdk.model;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;

import java.util.Locale;
import java.util.Objects;

public class BleDevice {
    private static final String TARGET_PREFIX = "znhaas";

    private final BluetoothDevice bluetoothDevice;
    private final String name;
    private final String address;
    private final int rssi;
    private final int bondState;

    public BleDevice(BluetoothDevice bluetoothDevice, String name, String address, int rssi, int bondState) {
        this.bluetoothDevice = bluetoothDevice;
        this.name = name;
        this.address = address;
        this.rssi = rssi;
        this.bondState = bondState;
    }

    public static BleDevice from(BluetoothDevice bluetoothDevice, int rssi) {
        if (bluetoothDevice == null) {
            return null;
        }
        return new BleDevice(
                bluetoothDevice,
                safeGetName(bluetoothDevice),
                bluetoothDevice.getAddress(),
                rssi,
                bluetoothDevice.getBondState()
        );
    }

    @SuppressLint("MissingPermission")
    private static String safeGetName(BluetoothDevice bluetoothDevice) {
        try {
            return bluetoothDevice.getName();
        } catch (SecurityException exception) {
            return null;
        }
    }

    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return toDisplayName(name);
    }

    public String getAddress() {
        return address;
    }

    public int getRssi() {
        return rssi;
    }

    public int getBondState() {
        return bondState;
    }

    public boolean isZnhaasDevice() {
        return isTargetDeviceName(name);
    }

    public static boolean isTargetDeviceName(String deviceName) {
        if (deviceName == null) {
            return false;
        }
        return deviceName.trim().toLowerCase(Locale.US).startsWith(TARGET_PREFIX);
    }

    public static String toDisplayName(String deviceName) {
        if (deviceName == null || deviceName.trim().isEmpty()) {
            return "Unknown device";
        }
        String trimmed = deviceName.trim();
        if (!isTargetDeviceName(trimmed)) {
            return trimmed;
        }
        String suffix = trimmed.substring(Math.min(trimmed.length(), TARGET_PREFIX.length()));
        if (suffix.startsWith("_") || suffix.startsWith("-")) {
            suffix = suffix.substring(1);
        }
        return suffix.isEmpty() ? trimmed : suffix;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BleDevice)) {
            return false;
        }
        BleDevice bleDevice = (BleDevice) other;
        return Objects.equals(address, bleDevice.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

    @Override
    public String toString() {
        return "BleDevice{"
                + "name='" + name + '\''
                + ", address='" + address + '\''
                + ", rssi=" + rssi
                + ", bondState=" + bondState
                + '}';
    }
}
