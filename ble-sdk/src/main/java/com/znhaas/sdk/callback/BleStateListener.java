package com.znhaas.sdk.callback;

public interface BleStateListener {
    void onBluetoothStateChanged(int state, boolean enabled);
}
