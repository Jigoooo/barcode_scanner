package com.barcodescanner.scanner;

import static androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.widget.Button;
import android.widget.RelativeLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.TorchState;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.barcodescanner.R;
import com.barcodescanner.databinding.MultiScanLayoutBinding;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanActivity extends AppCompatActivity {
    private ScanHelper scanHelper;

    private MultiScanLayoutBinding multiScanLayoutBinding;
    static final String SCAN_ACTIVITY_LOG = "BarcodeLog";
    ExecutorService cameraExecutor;

    int barcodeLengthLimit;
    BarcodeScannerType barcodeScannerType = BarcodeScannerType.MULTI;
    ArrayList<String> scannedBarcodes = new ArrayList<>();
    float maxSupportedZoomRatio = 2.0f;

    private Camera camera;
    private boolean isCameraClosed = true;
    private boolean usingFrontCamera = false;

    Size targetResolution;
    int screenWidth;
    int screenHeight;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        scanHelper = new ScanHelper(this);

        handleScreenOptionSize();
        setIntentParam();
        setContentViewWithBinding();
        setButtonListener();

        permissionCheckAndStartCamera();

        setScannedCountText();

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void handleScreenOptionSize() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;

        targetResolution = new Size(screenWidth, screenHeight);
    }

    private void setIntentParam() {
        String barcodeScannerTypeStr  = getIntent().getStringExtra("BARCODE_SCANNER_TYPE");

        barcodeLengthLimit = getIntent().getIntExtra("BARCODE_LENGTH_LIMIT", 0);
        barcodeScannerType = BarcodeScannerType.valueOf(barcodeScannerTypeStr);

        Log.d(SCAN_ACTIVITY_LOG, "barcodeLengthLimit: " + barcodeLengthLimit);
        Log.d(SCAN_ACTIVITY_LOG, "barcodeScannerType: " + barcodeScannerType);
    }

    private void setContentViewWithBinding() {
        multiScanLayoutBinding = MultiScanLayoutBinding.inflate(getLayoutInflater());
        setContentView(multiScanLayoutBinding.getRoot());
    }

    private void setButtonListener() {
        Button scanInitButton = multiScanLayoutBinding.scanInitButton;
        Button scanSaveButton = multiScanLayoutBinding.scanSaveButton;
        Button switchCameraButton = multiScanLayoutBinding.switchCameraButton;
        Button toggleFlashButton = multiScanLayoutBinding.toggleFlashButton;
        Button toggleScreenOrientationButton = multiScanLayoutBinding.toggleScreenOrientationButton;

        scanInitButton.setOnClickListener(v -> removeBarcodeData());
        scanSaveButton.setOnClickListener(v -> exitAndSaveBarcodeData());
        switchCameraButton.setOnClickListener(v -> switchCamera());
        toggleFlashButton.setOnClickListener(v -> toggleFlash());
        toggleScreenOrientationButton.setOnClickListener(v -> toggleScreenOrientation());
    }

    private void setScannedCountText() {
        String scannedCountText = getString(R.string.scan_count, scannedBarcodes.size());
        multiScanLayoutBinding.scannedBarcodeCount.setText(scannedCountText);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        cameraDestroy();
    }

    private void cameraDestroy() {
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        if (camera != null) {
            isCameraClosed = true;
            camera = null;
        }
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            }
            catch (ExecutionException | InterruptedException e) {
                Thread.currentThread().interrupt();

                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void permissionCheckAndStartCamera() {
        if (allPermissionsGranted()) {
            startCamera();
        }
        else {
            requestPermissions();
        }
    }

    private void exitAndSaveBarcodeData() {
        Intent returnBarcodeIntent = new Intent();

        Log.d(SCAN_ACTIVITY_LOG, "scannedBarcodes: " + scannedBarcodes);

        returnBarcodeIntent.putStringArrayListExtra("barcodeData", scannedBarcodes);

        setResult(Activity.RESULT_OK, returnBarcodeIntent);
        finish();
    }

    private void removeBarcodeData() {
        scannedBarcodes.clear();

        multiScanLayoutBinding.scannedBarcodeList.setText("");

        String scannedCountText = getString(R.string.scan_count, scannedBarcodes.size());
        multiScanLayoutBinding.scannedBarcodeCount.setText(scannedCountText);
    }

    private void switchCamera() {
        usingFrontCamera = !usingFrontCamera;

        permissionCheckAndStartCamera();
    }

    private void toggleFlash() {
        if (camera == null) return;

        CameraControl cameraControl = camera.getCameraControl();
        CameraInfo cameraInfo = camera.getCameraInfo();

        if (cameraInfo.getTorchState().getValue() != null) {
            boolean isFlashOn = cameraInfo.getTorchState().getValue() == TorchState.ON;

            cameraControl.enableTorch(!isFlashOn);
        }
    }

    private void toggleScreenOrientation() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = usingFrontCamera
                ? CameraSelector.DEFAULT_FRONT_CAMERA
                : CameraSelector.DEFAULT_BACK_CAMERA;

        Preview preview = getPreview();
        ImageAnalysis imageAnalysis = getImageAnalysis();

        ViewPort viewPort = multiScanLayoutBinding.viewFinder.getViewPort();

        if (viewPort != null) {
            UseCaseGroup useCaseGroup = new UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageAnalysis)
                    .setViewPort(viewPort)
                    .build();

            cameraProvider.unbindAll();

            camera = cameraProvider.bindToLifecycle(this, cameraSelector, useCaseGroup);
            CameraControl cameraControl = camera.getCameraControl();
            cameraControl.setLinearZoom(0.3f);

            isCameraClosed = false;
        }
        else {
            Log.d(SCAN_ACTIVITY_LOG, "ViewPort is null");
        }
    }

    private Preview getPreview() {
        Preview preview = new Preview.Builder().build();

        preview.setSurfaceProvider(multiScanLayoutBinding.viewFinder.getSurfaceProvider());

        return preview;
    }

    private BarcodeScanner getBarcodeScanner() {
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_CODE_128, Barcode.FORMAT_PDF417)
                .enableAllPotentialBarcodes()
                .setZoomSuggestionOptions(
                        new ZoomSuggestionOptions.Builder(zoomCallback)
                                .setMaxSupportedZoomRatio(maxSupportedZoomRatio)
                                .build())
                .build();

        return BarcodeScanning.getClient(options);
    }

    ZoomSuggestionOptions.ZoomCallback zoomCallback = zoomRatio -> {
        if (isCameraClosed) {
            return false;
        }

        camera.getCameraControl().setZoomRatio(zoomRatio);
        return true;
    };

    @OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private ImageAnalysis getImageAnalysis() {
        ResolutionStrategy resolutionStrategy = new ResolutionStrategy(targetResolution, FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER);

        ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
                .setResolutionStrategy(resolutionStrategy).build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(resolutionSelector)
                .build();

        BarcodeScanner barcodeScanner = getBarcodeScanner();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), image -> {
            InputImage inputImage = InputImage
                    .fromMediaImage(
                            Objects.requireNonNull(image.getImage()), image.getImageInfo().getRotationDegrees()
                    );

            barcodeScanner.process(inputImage)
                    .addOnSuccessListener(barcodes -> handleScannedBarcodes(image, barcodes))
                    .addOnFailureListener(e -> Log.e(SCAN_ACTIVITY_LOG, "Barcode scanning failed", e))
                    .addOnCompleteListener(task -> image.close());
        });

        return imageAnalysis;
    }
    private void handleScannedBarcodes(ImageProxy image, List<Barcode> barcodes) {
        try (image) {
            for (Barcode barcode : barcodes) {
                String barcodeValue = barcode.getRawValue();
                Rect boundingBox = barcode.getBoundingBox();

                if(boundingBox == null) {
                    continue;
                }

                Rect transformedBoundingBox = calculateScaleScanAreaSize(boundingBox, image);

                boolean scannedCheck = false;

                if(barcodeValue != null) {
                    scannedCheck = checkBarcodeData(barcodeValue, transformedBoundingBox);
                }

                if(!scannedCheck) {
                    return;
                }

                scannedBarcodes.add(barcodeValue);

                scanHelper.barcodeSoundsControl();
                scanHelper.updateScannedBarcodesUI(scannedBarcodes, multiScanLayoutBinding);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Rect calculateScaleScanAreaSize(Rect boundingBox, ImageProxy image) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        int rotationDegrees = image.getImageInfo().getRotationDegrees();

        float scaleX;
        float scaleY;

        if (rotationDegrees == 90) {
            scaleX = (float) screenWidth / imageHeight;
            scaleY = (float) screenHeight / imageWidth;
        }
        else {
            scaleX = (float) screenWidth / imageWidth;
            scaleY = (float) screenHeight / imageHeight;
        }

        int transformedLeft = (int) (boundingBox.left * scaleX);
        int transformedTop = (int) (boundingBox.top * scaleY);
        int transformedRight = (int) (boundingBox.right * scaleX);
        int transformedBottom = (int) (boundingBox.bottom * scaleY);

        return new Rect(transformedLeft, transformedTop, transformedRight, transformedBottom);
    }

    private boolean checkBarcodeData(String barcodeValue, Rect transformedBoundingBox) {
        if(Objects.equals(barcodeValue, "")) {
            return false;
        }

        ScanningEffectView scanningEffectView = multiScanLayoutBinding.scanningEffectView;
        scanningEffectView.setBoundingBox(transformedBoundingBox);

        if(!isBarcodeInGuideline(transformedBoundingBox)) {
            return false;
        }

        if(scannedBarcodes.size() == 1 && barcodeScannerType == BarcodeScannerType.SINGLE) {
            scanHelper.makeSnackBar("싱글 바코드 모드에서는 하나만 스캔할 수 있습니다.", multiScanLayoutBinding);
            return false;
        }

        if(barcodeLengthLimit != 0 && barcodeValue.length() != barcodeLengthLimit) {
            scanHelper.makeSnackBar("바코드의 길이를 확인해 주세요.", multiScanLayoutBinding);
            return false;
        }

        if (scannedBarcodes.contains(barcodeValue)) {
            scanHelper.makeSnackBar("이미 스캔한 바코드가 있습니다", multiScanLayoutBinding);
            return false;
        }

        return true;
    }

    private boolean isBarcodeInGuideline(Rect barcodeBoundingBox) {
        Rect guidelineRect = new Rect(
                (int)(screenWidth * 0.15),
                (int)(screenHeight * 0.30),
                (int)(screenWidth * 0.85),
                (int)(screenHeight * 0.70)
        );

        return guidelineRect.contains(barcodeBoundingBox);
    }

    private boolean allPermissionsGranted() {
        String[] permissions = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    private void requestPermissions() {
        requestPermissionLauncher.launch(new String[]{
                Manifest.permission.CAMERA
        });
    }

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Log.d(SCAN_ACTIVITY_LOG, "requestPermissionLauncher result=" + result);

                if (result.containsValue(false)) {
                    scanHelper.makeSnackBar("스캐너를 사용하려면 카메라 권한이 필요합니다.", multiScanLayoutBinding);
                }
                else {
                    startCamera();
                }
            });

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        adjustBarcodeListLayoutForOrientation(newConfig.orientation);
    }

    private void adjustBarcodeListLayoutForOrientation(int orientation) {
        RelativeLayout barcodeListLayout = multiScanLayoutBinding.scannedBarcodeScrollviewRelative;
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) barcodeListLayout.getLayoutParams();

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            layoutParams.width = 0;
            layoutParams.matchConstraintPercentWidth = 0.25f;
            layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            layoutParams.endToEnd = ConstraintLayout.LayoutParams.UNSET;
        }
        else {
            layoutParams.width = 0;
            layoutParams.matchConstraintPercentWidth = 0.5f;
            layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        }

        handleScreenOptionSize();

        permissionCheckAndStartCamera();

        barcodeListLayout.setLayoutParams(layoutParams);
    }
}
