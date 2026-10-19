package com.workbuddy.backscreen;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Consistent line icons, independent of system emoji fonts. */
final class StudioIcon extends Drawable {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int type;
    StudioIcon(int type, int color, int size) {
        this.type = type;
        p.setColor(color); p.setStrokeWidth(1.65f); p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        setBounds(0, 0, size, size);
    }
    @Override public void draw(Canvas c) {
        Rect r = getBounds();
        c.save(); c.translate(r.left, r.top); c.scale(r.width() / 24f, r.height() / 24f);
        if (type == 0) {
            c.drawRoundRect(3, 3, 21, 21, 6, 6, p);
            c.drawLine(8, 8, 8, 10, p); c.drawLine(16, 8, 16, 10, p);
            c.drawArc(7, 10, 17, 17, 25, 130, false, p);
        } else if (type == 1) {
            c.drawLine(5, 5, 19, 5, p); c.drawLine(12, 5, 12, 20, p);
            c.drawLine(8, 20, 16, 20, p);
        } else if (type == 2) {
            for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++)
                c.drawRoundRect(4+x*6, 4+y*6, 7+x*6, 7+y*6, .6f, .6f, p);
        } else if (type == 3) {
            c.drawRoundRect(6, 4, 18, 21, 3, 3, p); c.drawLine(10, 1, 14, 1, p);
            Path bolt = new Path(); bolt.moveTo(13, 7); bolt.lineTo(10, 13);
            bolt.lineTo(14, 13); bolt.lineTo(11, 18); c.drawPath(bolt, p);
        } else {
            for (int i = 0; i < 3; i++) {
                float y = 5 + i * 7, x = i == 1 ? 8 : 16;
                c.drawLine(3, y, x-3, y, p); c.drawLine(x+3, y, 21, y, p);
                c.drawCircle(x, y, 2.5f, p);
            }
        }
        c.restore();
    }
    @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { p.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
