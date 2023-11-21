package com.barcodescanner.scanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.Random;

public class ScanningEffectView extends View {
    static final String SCAN_ACTIVITY_LOG = "BarcodeLog";

    private Paint paint;
    private Random random;
    private static final int DOTS_COUNT = 90;
    private static final int DOT_SIZE = 3;

    private Rect boundingBox;

    private long lastUpdateTime = 0;
    private static final long REFRESH_DELAY = 1000;

    public ScanningEffectView(Context context) {
        super(context);
        init();
    }

    public ScanningEffectView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.RED);
        random = new Random();
    }

    public void setBoundingBox(Rect boundingBox) {
        this.boundingBox = boundingBox;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (boundingBox != null && System.currentTimeMillis() - lastUpdateTime < REFRESH_DELAY) {
            for (int i = 0; i < DOTS_COUNT; i++) {
                int x = random.nextInt(boundingBox.width()) + boundingBox.left;
                int y = random.nextInt(boundingBox.height()) + boundingBox.top;
                canvas.drawCircle(x, y, DOT_SIZE, paint);
            }
        }

        postInvalidateDelayed(100);
    }
}
