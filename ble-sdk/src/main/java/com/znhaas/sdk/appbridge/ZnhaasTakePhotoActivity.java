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
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ZnhaasTakePhotoActivity extends AppCompatActivity {
    public static final String EXTRA_REQUEST_ID = "requestId";
    public static final String EXTRA_OUTPUT_PATH = "outputPath";
    public static final String EXTRA_ERROR_MESSAGE = "errorMessage";

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;
    private boolean captureInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraExecutor = Executors.newSingleThreadExecutor();
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

        TextView capture = new TextView(this);
        capture.setText("");
        capture.setGravity(Gravity.CENTER);
        capture.setBackground(new android.graphics.drawable.GradientDrawable() {{
            setShape(OVAL);
            setColor(Color.WHITE);
            setStroke(dp(4), 0x99FFFFFF);
        }});
        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePhoto();
            }
        });
        FrameLayout.LayoutParams captureParams = new FrameLayout.LayoutParams(dp(76), dp(76));
        captureParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        captureParams.bottomMargin = dp(42);
        root.addView(capture, captureParams);

        TextView tip = new TextView(this);
        tip.setText("点击下方按钮拍照");
        tip.setTextColor(Color.WHITE);
        tip.setTextSize(15);
        tip.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tipParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        tipParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        tipParams.bottomMargin = dp(126);
        root.addView(tip, tipParams);

        setContentView(root);
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = providerFuture.get();
                    bindCamera();
                } catch (Exception exception) {
                    finishWithError("Unable to start camera: " + exception.getMessage());
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        cameraProvider.unbindAll();
        try {
            CameraSelector cameraSelector = resolveCameraSelector();
            if (cameraSelector == null) {
                finishWithError("No camera hardware is available.");
                return;
            }
            cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
            );
        } catch (Exception exception) {
            finishWithError("Unable to bind camera: " + exception.getMessage());
        }
    }

    private CameraSelector resolveCameraSelector() {
        try {
            if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                return CameraSelector.DEFAULT_BACK_CAMERA;
            }
            if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                return CameraSelector.DEFAULT_FRONT_CAMERA;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void takePhoto() {
        if (captureInProgress || imageCapture == null) {
            return;
        }
        String outputPath = getIntent().getStringExtra(EXTRA_OUTPUT_PATH);
        if (outputPath == null || outputPath.trim().isEmpty()) {
            finishWithError("Photo output path is empty.");
            return;
        }
        captureInProgress = true;
        File outputFile = new File(outputPath);
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(outputFile).build();
        imageCapture.takePicture(
                outputOptions,
                cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Intent result = new Intent();
                                result.putExtra(EXTRA_REQUEST_ID, getIntent().getStringExtra(EXTRA_REQUEST_ID));
                                result.putExtra(EXTRA_OUTPUT_PATH, outputPath);
                                setResult(Activity.RESULT_OK, result);
                                finish();
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        captureInProgress = false;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                finishWithError("Photo capture failed: " + exception.getMessage());
                            }
                        });
                    }
                }
        );
    }

    private void finishWithError(String message) {
        Intent result = new Intent();
        result.putExtra(EXTRA_REQUEST_ID, getIntent().getStringExtra(EXTRA_REQUEST_ID));
        result.putExtra(EXTRA_ERROR_MESSAGE, message);
        setResult(Activity.RESULT_FIRST_USER, result);
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        super.onDestroy();
    }
}
