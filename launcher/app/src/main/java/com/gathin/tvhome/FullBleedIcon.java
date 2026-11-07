package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/**
 * square_tiles: an adaptive icon drawn edge to edge, the way it looks as an app icon, instead of small on a tinted
 * card. Its two layers are 108 units across with the visible icon the middle 72, so each layer is drawn a quarter of
 * the bounds past every edge; the tile's rounded outline crops it, as a launcher's icon mask would.
 */
final class FullBleedIcon extends Drawable {
    private final Drawable background, foreground;
    private final int size;
    private final Rect layer = new Rect();

    private FullBleedIcon(AdaptiveIconDrawable icon) {
        background = icon.getBackground();
        foreground = icon.getForeground();
        size = icon.getIntrinsicWidth();
    }

    /** The icon full-bleed when it is an adaptive one; any other icon as it is. */
    static Drawable of(Drawable icon) {
        return icon instanceof AdaptiveIconDrawable ? new FullBleedIcon((AdaptiveIconDrawable) icon) : icon;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        int dx = b.width() / 4, dy = b.height() / 4;
        layer.set(b.left - dx, b.top - dy, b.right + dx, b.bottom + dy);
        canvas.save();
        canvas.clipRect(b);
        if (background != null) {
            background.setBounds(layer);
            background.draw(canvas);
        }
        if (foreground != null) {
            foreground.setBounds(layer);
            foreground.draw(canvas);
        }
        canvas.restore();
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    @Override
    public void setAlpha(int alpha) {
        if (background != null) background.setAlpha(alpha);
        if (foreground != null) foreground.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        if (background != null) background.setColorFilter(filter);
        if (foreground != null) foreground.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
