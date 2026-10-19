package com.workbuddy.backscreen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** 17x23 点阵编辑器: 手指拖动绘制, 长按擦除, 支持多帧 */
public class PixelEditorView extends View {

    public interface Listener { void onFrameEdited(); }

    private static final int LEVELS = 4; // 亮度挡位: 0/85/170/255
    private final Paint mGrid = new Paint();
    private final Paint mCell = new Paint();
    private final Paint mGhost = new Paint();
    private Listener mListener;
    private GestureDetector mDetector;

    public final List<int[]> frames = new ArrayList<>();
    public int current = 0;
    public int brush = 255;
    public boolean eraseMode = false;
    /** 显示下一帧作为洋葱皮 */
    public boolean onion = true;

    public PixelEditorView(Context c, AttributeSet a) {
        super(c, a);
        mGrid.setStyle(Paint.Style.STROKE);
        mGrid.setColor(0x22FFFFFF);
        mCell.setStyle(Paint.Style.FILL);
        mGhost.setStyle(Paint.Style.FILL);
        mGhost.setColor(0x309FE870);
        if (frames.isEmpty()) frames.add(newFrame());
        mDetector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public void onLongPress(MotionEvent e) {
                // 长按清空单格
                int[] rc = hitCell(e.getX(), e.getY());
                if (rc != null) { setCell(rc[0], rc[1], 0); invalidate(); notifyEdit(); }
            }
        });
    }

    public void setListener(Listener l) { mListener = l; }
    private void notifyEdit() { if (mListener != null) mListener.onFrameEdited(); }

    public static int[] newFrame() { return new int[BackScreenApi.N]; }

    public int[] frame() { return frames.get(current); }

    public void addFrame() {
        frames.add(current + 1, frame().clone());
        current++;
        invalidate();
    }

    public void duplicateFrame() { addFrame(); }

    public void deleteFrame() {
        if (frames.size() <= 1) { frames.set(0, newFrame()); invalidate(); return; }
        frames.remove(current);
        if (current >= frames.size()) current = frames.size() - 1;
        invalidate();
    }

    public void clearFrame() { java.util.Arrays.fill(frame(), 0); invalidate(); }

    public void fillAll() { java.util.Arrays.fill(frame(), brush); invalidate(); }

    public void setCurrent(int i) { current = i; invalidate(); }

    public void toggleOnion() { onion = !onion; invalidate(); }

    private int[] hitCell(float x, float y) {
        int cw = getWidth() / BackScreenApi.W;
        int ch = getHeight() / BackScreenApi.H;
        int cell = Math.min(cw, ch);
        if (cell <= 0) return null;
        int ox = (getWidth() - cell * BackScreenApi.W) / 2;
        int oy = (getHeight() - cell * BackScreenApi.H) / 2;
        if (x < ox || y < oy) return null;
        int col = (int) ((x - ox) / cell);
        int row = (int) ((y - oy) / cell);
        if (col < 0 || row < 0 || col >= BackScreenApi.W || row >= BackScreenApi.H) return null;
        return new int[]{row, col};
    }

    private void setCell(int row, int col, int v) {
        if (!BackScreenApi.DOT_OK[row * BackScreenApi.W + col]) return; // 圆角死区不可绘制
        frame()[row * BackScreenApi.W + col] = v;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN)
            getParent().requestDisallowInterceptTouchEvent(true);
        if (e.getActionMasked() == MotionEvent.ACTION_UP
                || e.getActionMasked() == MotionEvent.ACTION_CANCEL)
            getParent().requestDisallowInterceptTouchEvent(false);
        mDetector.onTouchEvent(e);
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN
                || e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            for (int p = 0; p < e.getPointerCount(); p++) {
                int[] rc = hitCell(e.getX(p), e.getY(p));
                if (rc != null) {
                    int v = eraseMode ? 0 : brush;
                    setCell(rc[0], rc[1], v);
                }
            }
            invalidate();
            notifyEdit();
            return true;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas cv) {
        cv.drawColor(0xFF1A211E);
        int cw = getWidth() / BackScreenApi.W;
        int ch = getHeight() / BackScreenApi.H;
        int cell = Math.min(cw, ch);
        int ox = (getWidth() - cell * BackScreenApi.W) / 2;
        int oy = (getHeight() - cell * BackScreenApi.H) / 2;
        float pad = Math.max(1f, cell * 0.06f);
        int[] f = frame();
        int[] ghost = (onion && frames.size() > 1)
                ? frames.get((current + 1) % frames.size()) : null;
        for (int r = 0; r < BackScreenApi.H; r++) {
            for (int c = 0; c < BackScreenApi.W; c++) {
                float x = ox + c * cell + pad;
                float y = oy + r * cell + pad;
                float s = cell - pad * 2;
                int v = f[r * BackScreenApi.W + c];
                if (!BackScreenApi.DOT_OK[r * BackScreenApi.W + c]) {
                    continue; // Physical corners contain no LEDs.
                } else if (v > 0) {
                    mCell.setColor(ledColor(v));
                    cv.drawRoundRect(x, y, x + s, y + s, pad * 2, pad * 2, mCell);
                } else if (ghost != null && ghost[r * BackScreenApi.W + c] > 0) {
                    cv.drawRoundRect(x, y, x + s, y + s, pad * 2, pad * 2, mGhost);
                } else {
                    mGrid.setColor(0x18FFFFFF);
                    mGrid.setStyle(Paint.Style.FILL);
                    cv.drawRoundRect(x, y, x + s, y + s, pad * 2, pad * 2, mGrid);
                    mGrid.setStyle(Paint.Style.STROKE);
                }
            }
        }
    }

    public static int ledColor(int brightness) {
        int v = Math.max(0, Math.min(255, brightness));
        return android.graphics.Color.rgb(60 + v * 195 / 255,
                65 + v * 190 / 255, 56 + v * 183 / 255);
    }

    /** 把任意 Bitmap 缩到 391 点帧 (取亮度均值) */
    public static int[] fromBitmap(android.graphics.Bitmap bmp, boolean invert) {
        int[] out = new int[BackScreenApi.N];
        int[] px = new int[BackScreenApi.W * BackScreenApi.H * 4];
        android.graphics.Bitmap small = android.graphics.Bitmap.createScaledBitmap(
                bmp, BackScreenApi.W * 2, BackScreenApi.H * 2, true);
        small.getPixels(px, 0, small.getWidth(), 0, 0,
                small.getWidth(), small.getHeight());
        for (int r = 0; r < BackScreenApi.H; r++) {
            for (int c = 0; c < BackScreenApi.W; c++) {
                long sum = 0; int cnt = 0;
                for (int dr = 0; dr < 2; dr++) for (int dc = 0; dc < 2; dc++) {
                    int idx = (r * 2 + dr) * small.getWidth() + (c * 2 + dc);
                    if (idx < px.length) {
                        int p = px[idx];
                        int lum = (int) (0.299 * ((p >> 16) & 0xFF)
                                + 0.587 * ((p >> 8) & 0xFF) + 0.114 * (p & 0xFF));
                        int alpha = (p >>> 24);
                        sum += (lum * alpha) / 255;
                        cnt++;
                    }
                }
                int v = (int) (sum / Math.max(1, cnt));
                if (invert) v = 255 - v;
                out[r * BackScreenApi.W + c] = v;
            }
        }
        return out;
    }
}
