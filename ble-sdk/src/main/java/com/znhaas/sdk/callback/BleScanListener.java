package com.znhaas.sdk.callback;

import com.znhaas.sdk.model.BleDevice;

import java.util.List;

public interface BleScanListener {
    void onScanStarted();

    void onDeviceFound(BleDevice device);

    void onScanStopped(List<BleDevice> devices);

    void onScanFailed(String message);
}
