package com.znhaas.sdk.callback;

import java.util.UUID;

public interface BleWriteListener {
    void onWriteSuccess(UUID serviceUuid, UUID characteristicUuid, byte[] value, String hexValue);

    void onError(UUID serviceUuid, UUID characteristicUuid, byte[] value, String message);
}
