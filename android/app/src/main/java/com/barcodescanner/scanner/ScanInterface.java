package com.barcodescanner.scanner;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;

public class ScanInterface extends ReactContextBaseJavaModule {
    private static final int BARCODE_RESULT_CODE = 1;
    static final String BARCODE_LOG = "BarcodeLog";
    ReactApplicationContext reactContext;

    private int listenerCount = 0;
    private BarcodeScannerType currentScannerType;
    private Promise currentPromise;

    public ScanInterface(ReactApplicationContext context) {
        super(context);

        this.reactContext = context;

        context.addActivityEventListener(mActivityEventListener);
    }

    ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {
        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
            if (requestCode == BARCODE_RESULT_CODE) {
                WritableMap toSendMultiBarcode = Arguments.createMap();

                if (resultCode == Activity.RESULT_OK) {
                    ArrayList<String> multiBarcodeList = data.getStringArrayListExtra("barcodeData");

                    Log.d(BARCODE_LOG, "multiBarcodeList: " + multiBarcodeList);

                    if(multiBarcodeList == null) {
                        return;
                    }

                    WritableArray barcodeArray = createBarcodeArray(multiBarcodeList);

                    Log.d(BARCODE_LOG, "barcodeArray: " + barcodeArray);

                    if(currentScannerType == BarcodeScannerType.MULTI) {
                        toSendMultiBarcode.putArray("multiBarcodes", barcodeArray);
                        sendEventWritableMap(reactContext, toSendMultiBarcode);
                    }
                    else if(currentScannerType == BarcodeScannerType.SINGLE) {
                        if(currentPromise == null) {
                            return;
                        }

                        currentPromise.resolve(barcodeArray.getString(0));
                    }
                }
            }
        }

        private WritableArray createBarcodeArray(ArrayList<String> multiBarcodeList) {
            WritableArray barcodeArray = Arguments.createArray();

            for (String barcode : multiBarcodeList) {
                barcodeArray.pushString(barcode);
            }

            return barcodeArray;
        }

        private void sendEventWritableMap(ReactContext reactContext, @Nullable WritableMap params) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit("multiBarcodesEvent", params);
        }
    };

    @NonNull
    @Override
    public String getName() {
        return "ScanInterface";
    }

    @ReactMethod
    public void addListener(String eventName) {
        if (listenerCount == 0) {
            Log.d(BARCODE_LOG, "addListenerCount: " + listenerCount);
        }

        listenerCount += 1;
    }

    @ReactMethod
    public void removeListeners(Integer count) {
        listenerCount -= count;

        if (listenerCount == 0) {
            Log.d(BARCODE_LOG, "removeListenersCount: " + listenerCount);
        }
    }

    @ReactMethod
    public void startScanner(String barcodeScannerType, int barcodeLengthLimit, Promise promise) {
        currentPromise = promise;

        Intent intent = new Intent(reactContext, ScanActivity.class);

        currentScannerType = BarcodeScannerType.valueOf(barcodeScannerType);

        Log.d(BARCODE_LOG, "barcodeScannerType: " + barcodeScannerType);
        Log.d(BARCODE_LOG, "barcodeLengthLimit: " + barcodeLengthLimit);

        intent.putExtra("BARCODE_SCANNER_TYPE", barcodeScannerType);
        intent.putExtra("BARCODE_LENGTH_LIMIT", barcodeLengthLimit);

        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        reactContext.startActivityForResult(intent, BARCODE_RESULT_CODE, null);
    }
}


enum BarcodeScannerType {
    GMS_SINGLE, SINGLE, MULTI
}
