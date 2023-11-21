package com.barcodescanner.scanner;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.barcodescanner.R;
import com.barcodescanner.databinding.MultiScanLayoutBinding;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

public class ScanHelper {
    Context context;

    public ScanHelper(Context context) {
        this.context = context;
    }

    public void barcodeSoundsControl() {
        playCDMAAbbreviatedAlert();
        handleVibrator();
    }

    private void handleVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vibratorManager != null) {
                Vibrator vibrator = vibratorManager.getDefaultVibrator();

                if (vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createWaveform(new long[]{900, 900}, -1));
                }
            }
        } else {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(new long[]{900, 900}, -1));
                } else {
                    vibrator.vibrate(new long[]{900, 900}, -1);
                }
            }
        }
    }

    private void playCDMAAbbreviatedAlert() {
        ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT);

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }

        toneGen.release();
    }

    public void updateScannedBarcodesUI(List<String> scannedBarcodes, MultiScanLayoutBinding multiScanLayoutBinding) {
        StringBuilder barcodesText = new StringBuilder();

        for (String barcode : scannedBarcodes) {
            barcodesText.append(barcode).append("\n");
        }

        multiScanLayoutBinding.scannedBarcodeList.setText(barcodesText.toString());

        String scannedCountText = context.getString(R.string.scan_count, scannedBarcodes.size());

        multiScanLayoutBinding.scannedBarcodeCount.setText(scannedCountText);
    }

    public void makeSnackBar(String text, MultiScanLayoutBinding multiScanLayoutBinding) {
        Snackbar.make(multiScanLayoutBinding.viewFinder, text, BaseTransientBottomBar.LENGTH_SHORT).show();
    }
}
