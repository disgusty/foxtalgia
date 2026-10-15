package com.disgusty.foxtalgia.debug;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

/**
 * Debug builds only: checks whether the device shows a SurfaceView filled from the CPU, optionally configured like
 * GeckoView's (extras: translucent, bg, overlay, wrap).
 */
public class SurfaceTestActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String lib = getIntent().getStringExtra("load");
        if (lib != null) for (String l : lib.split(",")) System.loadLibrary(l);
        SurfaceView view = new SurfaceView(this);
        if (getIntent().getBooleanExtra("translucent", false)) view.getHolder().setFormat(PixelFormat.TRANSPARENT);
        if (getIntent().getBooleanExtra("bg", false)) view.setBackgroundColor(Color.TRANSPARENT);
        if (getIntent().getBooleanExtra("overlay", false)) view.setZOrderMediaOverlay(true);
        view.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {}

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Canvas c = holder.lockCanvas();
                if (c != null) {
                    c.drawColor(Color.RED);
                    holder.unlockCanvasAndPost(c);
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {}
        });
        if (getIntent().getBooleanExtra("wrap", false)) {
            FrameLayout frame = new FrameLayout(this);
            if (getIntent().getBooleanExtra("parentbg", false)) {
                android.graphics.drawable.StateListDrawable d = new android.graphics.drawable.StateListDrawable();
                d.addState(new int[] {android.R.attr.state_focused, -android.R.attr.state_focused},
                        new android.graphics.drawable.ColorDrawable(Color.WHITE));
                frame.setBackgroundDrawable(d);
            }
            if (getIntent().getBooleanExtra("solidbg", false)) frame.setBackgroundColor(0xff303030);
            frame.addView(view);
            setContentView(frame);
        } else {
            setContentView(view);
        }
    }
}
