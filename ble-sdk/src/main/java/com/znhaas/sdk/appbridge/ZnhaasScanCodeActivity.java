package com.znhaas.sdk.appbridge;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ZnhaasScanCodeActivity extends AppCompatActivity {
    public static final String EXTRA_REQUEST_ID = "requestId";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_FORMAT = "format";

    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;
    private boolean resultReturned;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraExecutor = Executors.newSingleThreadExecutor();
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        scanner = BarcodeScanning.getClient(options);
        setupContentView();
        startCamera();
    }

    private void setupContentView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        View scanFrame = new View(this);
        scanFrame.setBackgroundColor(Color.TRANSPARENT);
        scanFrame.setOutlineProvider(null);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(dp(250), dp(250));
        frameParams.gravity = Gravity.CENTER;
        scanFrame.setBackground(new android.graphics.drawable.GradientDrawable() {{
            setColor(Color.TRANSPARENT);
            setStroke(dp(2), Color.WHITE);
            setCornerRadius(dp(18));
        }});
        root.addView(scanFrame, frameParams);

        TextView tip = new TextView(this);
        tip.setText("请将二维码/条码放入框内");
        tip.setTextColor(Color.WHITE);
        tip.setTextSize(16);
        tip.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tipParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        tipParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
        tipParams.topMargin = dp(300);
        root.addView(tip, tipParams);

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextColor(Color.WHITE);
        cancel.setTextSize(16);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(18), dp(12), dp(18), dp(12));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });
        FrameLayout.LayoutParams cancelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        cancelParams.gravity = Gravity.TOP | Gravity.RIGHT;
        cancelParams.topMargin = dp(36);
        cancelParams.rightMargin = dp(18);
        root.addView(cancel, cancelParams);

        setContentView(root);
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = providerFuture.get();
                    bindCamera(cameraProvider);
                } catch (Exception exception) {
                    finishWithCancel();
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                analyzeFrame(imageProxy);
            }
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
        );
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeFrame(final ImageProxy imageProxy) {
        if (resultReturned || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );
        scanner.process(image)
                .addOnCompleteListener(new OnCompleteListener<List<Barcode>>() {
                    @Override
                    public void onComplete(@NonNull Task<List<Barcode>> task) {
                        try {
                            if (!resultReturned && task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                                Barcode barcode = task.getResult().get(0);
                                String value = barcode.getRawValue();
                                if (value != null && !value.trim().isEmpty()) {
                                    resultReturned = true;
                                    Intent intent = new Intent();
                                    intent.putExtra(EXTRA_REQUEST_ID, getIntent().getStringExtra(EXTRA_REQUEST_ID));
                                    intent.putExtra(EXTRA_TEXT, value);
                                    intent.putExtra(EXTRA_FORMAT, formatToText(barcode.getFormat()));
                                    setResult(Activity.RESULT_OK, intent);
                                    finish();
                                }
                            }
                        } finally {
                            imageProxy.close();
                        }
                    }
                });
    }

    private String formatToText(int format) {
        switch (format) {
            case Barcode.FORMAT_QR_CODE:
                return "QR_CODE";
            case Barcode.FORMAT_CODE_128:
                return "CODE_128";
            case Barcode.FORMAT_CODE_39:
                return "CODE_39";
            case Barcode.FORMAT_CODE_93:
                return "CODE_93";
            case Barcode.FORMAT_CODABAR:
                return "CODABAR";
            case Barcode.FORMAT_EAN_13:
                return "EAN_13";
            case Barcode.FORMAT_EAN_8:
                return "EAN_8";
            case Barcode.FORMAT_ITF:
                return "ITF";
            case Barcode.FORMAT_UPC_A:
                return "UPC_A";
            case Barcode.FORMAT_UPC_E:
                return "UPC_E";
            case Barcode.FORMAT_PDF417:
                return "PDF417";
            case Barcode.FORMAT_AZTEC:
                return "AZTEC";
            case Barcode.FORMAT_DATA_MATRIX:
                return "DATA_MATRIX";
            default:
                return "UNKNOWN";
        }
    }

    private void finishWithCancel() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (scanner != null) {
            scanner.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        super.onDestroy();
    }
}
