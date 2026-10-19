package com.workbuddy.backscreen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Aspect-correct LED preview; nonexistent corner LEDs are never drawn. */
public class ChargePreviewView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] frame = new int[BackScreenApi.N];
    public ChargePreviewView(Context context) { super(context); }
    public void setFrame(int[] next) {
        if (next == null || next.length != BackScreenApi.N) return;
        if (java.util.Arrays.equals(frame, next)) return;
        frame = next.clone(); invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        float cell = Math.min(getWidth() / 19f, getHeight() / 25f);
        float left = (getWidth() - cell * BackScreenApi.W) / 2;
        float top = (getHeight() - cell * BackScreenApi.H) / 2;
        for (int y = 0; y < BackScreenApi.H; y++) {
            for (int x = 0; x < BackScreenApi.W; x++) {
                int i = y * BackScreenApi.W + x;
                if (!BackScreenApi.DOT_OK[i]) continue;
                int v = Math.max(0, Math.min(255, frame[i]));
                float cx = left + (x + .5f) * cell, cy = top + (y + .5f) * cell;
                if (v > 0) {
                    paint.setColor(Color.argb(v / 8, 211, 244, 182));
                    canvas.drawCircle(cx, cy, cell * .58f, paint);
                }
                paint.setColor(v == 0 ? 0xFF232B29 : Color.rgb(60 + v * 195 / 255,
                        65 + v * 190 / 255, 56 + v * 183 / 255));
                canvas.drawCircle(cx, cy, cell * .27f, paint);
            }
        }
    }
}
