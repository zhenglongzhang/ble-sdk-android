package com.znhaas.sdk.appbridge;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Base64;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

class ZnhaasImageEncoder {
    private static final String MIME_TYPE = "image/jpeg";
    private static final int DEFAULT_MAX_WIDTH = 1600;
    private static final int DEFAULT_QUALITY = 80;

    private ZnhaasImageEncoder() {
    }

    static ZnhaasImageResult encode(Context context, Uri uri, String filePrefix, int maxWidth, int quality) throws IOException {
        int actualMaxWidth = maxWidth > 0 ? maxWidth : DEFAULT_MAX_WIDTH;
        int actualQuality = quality > 0 && quality <= 100 ? quality : DEFAULT_QUALITY;
        ContentResolver resolver = context.getContentResolver();

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream boundsStream = resolver.openInputStream(uri);
        if (boundsStream == null) {
            throw new IOException("Unable to open image.");
        }
        try {
            BitmapFactory.decodeStream(boundsStream, null, bounds);
        } finally {
            boundsStream.close();
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, actualMaxWidth);
        InputStream bitmapStream = resolver.openInputStream(uri);
        if (bitmapStream == null) {
            throw new IOException("Unable to decode image.");
        }

        Bitmap decoded;
        try {
            decoded = BitmapFactory.decodeStream(bitmapStream, null, decodeOptions);
        } finally {
            bitmapStream.close();
        }
        if (decoded == null) {
            throw new IOException("Image decode failed.");
        }

        Bitmap rotated = rotateIfNeeded(resolver, uri, decoded);
        Bitmap scaled = scaleIfNeeded(rotated, actualMaxWidth);
        int outputWidth = scaled.getWidth();
        int outputHeight = scaled.getHeight();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, actualQuality, outputStream);
        byte[] bytes = outputStream.toByteArray();

        if (scaled != rotated) {
            scaled.recycle();
        }
        if (rotated != decoded) {
            rotated.recycle();
        }
        decoded.recycle();

        String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
        String fileName = String.format(Locale.US, "%s_%d.jpg", filePrefix, System.currentTimeMillis());
        return new ZnhaasImageResult(base64, MIME_TYPE, fileName, outputWidth, outputHeight, bytes.length);
    }

    private static int calculateSampleSize(int width, int height, int maxWidth) {
        int sampleSize = 1;
        int largest = Math.max(width, height);
        while (largest / sampleSize > maxWidth * 2) {
            sampleSize *= 2;
        }
        return Math.max(1, sampleSize);
    }

    private static Bitmap rotateIfNeeded(ContentResolver resolver, Uri uri, Bitmap bitmap) {
        try {
            InputStream inputStream = resolver.openInputStream(uri);
            if (inputStream == null) {
                return bitmap;
            }
            int orientation;
            try {
                ExifInterface exifInterface = new ExifInterface(inputStream);
                orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            } finally {
                inputStream.close();
            }
            int degrees = 0;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                degrees = 90;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                degrees = 180;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                degrees = 270;
            }
            if (degrees == 0) {
                return bitmap;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Exception ignored) {
            return bitmap;
        }
    }

    private static Bitmap scaleIfNeeded(Bitmap bitmap, int maxWidth) {
        int largest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (largest <= maxWidth) {
            return bitmap;
        }
        float scale = maxWidth / (float) largest;
        int width = Math.max(1, Math.round(bitmap.getWidth() * scale));
        int height = Math.max(1, Math.round(bitmap.getHeight() * scale));
        return Bitmap.createScaledBitmap(bitmap, width, height, true);
    }

}
