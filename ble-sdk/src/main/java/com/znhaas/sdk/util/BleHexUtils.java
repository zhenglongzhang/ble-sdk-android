package com.znhaas.sdk.util;

public final class BleHexUtils {
    private BleHexUtils() {
    }

    public static String toHex(byte[] value) {
        if (value == null || value.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length * 3);
        for (int index = 0; index < value.length; index++) {
            builder.append(String.format("%02X", value[index]));
            if (index < value.length - 1) {
                builder.append(' ');
            }
        }
        return builder.toString();
    }

    public static byte[] fromHex(String hex) {
        if (hex == null) {
            return new byte[0];
        }
        String normalized = hex
                .replace("0x", "")
                .replace("0X", "")
                .replaceAll("[^0-9A-Fa-f]", "");
        if (normalized.isEmpty()) {
            return new byte[0];
        }
        if (normalized.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string length must be even.");
        }
        byte[] value = new byte[normalized.length() / 2];
        for (int index = 0; index < normalized.length(); index += 2) {
            value[index / 2] = (byte) Integer.parseInt(normalized.substring(index, index + 2), 16);
        }
        return value;
    }
}
