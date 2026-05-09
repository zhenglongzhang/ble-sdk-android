package com.znhaas.sdk.callback;

import android.bluetooth.BluetoothGattService;

import com.znhaas.sdk.model.BleDevice;

import java.util.List;

public interface BleConnectionListener {
    void onDeviceConnecting(BleDevice device);

    void onDeviceConnected(BleDevice device);

    void onServicesDiscovered(BleDevice device, List<BluetoothGattService> services);

    void onDeviceReady(BleDevice device);

    void onDeviceDisconnecting(BleDevice device);

    void onDeviceDisconnected(BleDevice device);

    void onError(BleDevice device, String message);
}
