package com.znhaas.sdk.appbridge;

public class ZnhaasImageResult {
    public final String base64;
    public final String dataUrl;
    public final String mimeType;
    public final String fileName;
    public final int width;
    public final int height;
    public final int sizeBytes;

    ZnhaasImageResult(String base64, String mimeType, String fileName, int width, int height, int sizeBytes) {
        this.base64 = base64;
        this.dataUrl = "data:" + mimeType + ";base64," + base64;
        this.mimeType = mimeType;
        this.fileName = fileName;
        this.width = width;
        this.height = height;
        this.sizeBytes = sizeBytes;
    }
}
