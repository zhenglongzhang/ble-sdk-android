package com.znhaas.sdk.callback;

import java.util.UUID;

public interface BleNotifyListener {
    void onNotifyEnabled(UUID serviceUuid, UUID characteristicUuid);

    void onNotifyDisabled(UUID serviceUuid, UUID characteristicUuid);

    void onCharacteristicChanged(UUID serviceUuid, UUID characteristicUuid, byte[] value, String hexValue);

    void onError(UUID serviceUuid, UUID characteristicUuid, String message);
}
