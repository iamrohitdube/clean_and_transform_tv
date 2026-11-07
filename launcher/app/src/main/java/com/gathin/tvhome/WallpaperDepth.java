package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;

import java.io.File;
import java.util.Locale;

/**
 * big_clock's depth for a wallpaper NAME.jpg, from two optional files beside it (the format of couch-tv's scenes):
 * NAME-fg.webp (or .png), the scenery with the sky transparent, drawn over the clock so hills and peaks overlap its
 * digits; and NAME-glass.jpg, a blurred, brightened copy the digits are filled with, so they look like frosted glass.
 */
final class WallpaperDepth {
    private static final String[] FG = {"-fg.webp", "-fg.png"};
    private static final String GLASS = "-glass.jpg";

    private WallpaperDepth() {
    }

    /** wallpaper_drop as a scale: 1 for 0, 1.3 for 30. */
    static float dropScale(int percent) {
        return 1f + Math.max(0, Math.min(30, percent)) / 100f;
    }

    /**
     * wallpaper_drop: zooms a full-screen picture view from its top edge, so the top stays put and the skyline moves
     * down; the bottom, behind the dock, is what gets cut off. The wallpaper and the cut-out take the same zoom.
     */
    static void drop(android.view.View view, int percent, int screenWidth) {
        float scale = dropScale(percent);
        if (scale == 1f) return;
        view.setPivotX(screenWidth / 2f);
        view.setPivotY(0f);
        view.setScaleX(scale);
        view.setScaleY(scale);
    }

    /** A cut-out or glass file, which the rotation must not show as a picture of its own. */
    static boolean isCompanion(String lowerName) {
        for (String suffix : FG) if (lowerName.endsWith(suffix)) return true;
        return lowerName.endsWith(GLASS);
    }

    /** The cut-out at screen size, with the same dim as the picture so the two match; null when there is none. */
    static Bitmap foreground(File picture, int width, int height, int dimColor) {
        File file = companion(picture, FG);
        if (file == null) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inMutable = true;
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) return null;
            if (decoded.getWidth() != width || decoded.getHeight() != height) {
                Bitmap scaled = Bitmap.createScaledBitmap(decoded, width, height, true);
                if (scaled != decoded) decoded.recycle();
                decoded = scaled;
            }
            // Darken the scenery only: SRC_ATOP leaves the transparent sky transparent.
            if (dimColor != 0) new Canvas(decoded).drawColor(dimColor, PorterDuff.Mode.SRC_ATOP);
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    /** The glass copy, small: it is blurred anyway, and the clock's shader stretches it back to screen size. */
    static Bitmap glass(File picture) {
        File file = companion(picture, new String[]{GLASS});
        if (file == null) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) return null;
            // Lifted towards white once here, so a digit over dark trees still reads as frosted glass. Done on the
            // picture rather than the clock's paint, where the filter would grey the digits' dark shadow too.
            Bitmap lifted = Bitmap.createBitmap(decoded.getWidth(), decoded.getHeight(), Bitmap.Config.ARGB_8888);
            android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
            // couch-tv's Night clock: the glass 15% brighter and 20% richer, plus a little lift so a digit over
            // dark trees does not sink into them.
            android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
            matrix.setSaturation(1.2f);
            matrix.postConcat(new android.graphics.ColorMatrix(new float[] {
                    1.15f, 0, 0, 0, 6,
                    0, 1.15f, 0, 0, 6,
                    0, 0, 1.15f, 0, 6,
                    0, 0, 0, 1, 0}));
            paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
            new Canvas(lifted).drawBitmap(decoded, 0, 0, paint);
            decoded.recycle();
            return lifted;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    private static File companion(File picture, String[] suffixes) {
        String name = picture.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        for (String suffix : suffixes) {
            File file = new File(picture.getParentFile(), base + suffix);
            if (file.isFile() && file.length() > 0) return file;
            File upper = new File(picture.getParentFile(), base + suffix.toUpperCase(Locale.ROOT));
            if (upper.isFile() && upper.length() > 0) return upper;
        }
        return null;
    }
}
