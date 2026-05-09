package com.znhaas.sdk.util;

import java.util.Locale;
import java.util.UUID;

public final class BleUuidUtils {
    private static final String BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb";

    private BleUuidUtils() {
    }

    public static UUID fromString(String uuidText) {
        if (uuidText == null || uuidText.trim().isEmpty()) {
            throw new IllegalArgumentException("UUID is empty.");
        }
        String normalized = uuidText.trim().toLowerCase(Locale.US);
        if (normalized.length() == 4) {
            normalized = "0000" + normalized + BASE_UUID_SUFFIX;
        } else if (normalized.length() == 8) {
            normalized = normalized + BASE_UUID_SUFFIX;
        }
        return UUID.fromString(normalized);
    }
}
