package com.gathin.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The wallpaper button's picker: every picture of the theme as a thumbnail with its name, and Automatic first
 * (the theme decides: weather and time of day with wallpaper_moods). A pick is the same on-TV choice Launcher
 * settings' Wallpaper row saves, so Home rebuilds with it when it is back on screen, and a theme push clears it.
 */
public final class WallpaperPickerActivity extends Activity {
    private static final int COLUMNS = 4;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private float density;
    private Typeface inter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        try {
            inter = getResources().getFont(R.font.inter_medium);
        } catch (RuntimeException e) {
            inter = Typeface.DEFAULT;
        }
        SharedPreferences choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);

        File dir = Config.dir(this);
        List<File> pictures = new ArrayList<>(WallpaperRotation.images(dir == null ? null : new File(dir, "wallpapers")));
        Map<String, String> captions = pictures.isEmpty() ? null : WallpaperCaptions.read(pictures.get(0).getParentFile());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(64), dp(40), dp(64), dp(40));
        page.setClipChildren(false);
        page.setClipToPadding(false);

        TextView title = text("Wallpaper", 30, 0xFFFFFFFF);
        page.addView(title);
        TextView hint = text("Automatic changes the picture with the weather and the time of day.", 15, 0xB3FFFFFF);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(6);
        hintLp.bottomMargin = dp(22);
        page.addView(hint, hintLp);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        int usable = getResources().getDisplayMetrics().widthPixels - dp(128);
        int cellW = usable / COLUMNS;
        int thumbW = cellW - dp(24), thumbH = Math.round(thumbW * 9f / 16f);

        View first = tile(grid, cellW, thumbW, thumbH, "Automatic", "Weather & time", null, current == null,
                v -> pick(choices, null));
        View focus = first;
        for (File picture : pictures) {
            String name = captions != null && captions.containsKey(picture.getName())
                    ? captions.get(picture.getName()) : niceName(picture.getName());
            boolean selected = picture.getName().equals(current);
            View t = tile(grid, cellW, thumbW, thumbH, name, null, picture, selected,
                    v -> pick(choices, picture.getName()));
            if (selected) focus = t;
        }
        page.addView(grid);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xF20B0D12);
        scroll.addView(page);
        setContentView(scroll);
        View start = focus;
        start.post(start::requestFocus);
    }

    /** One picture: a rounded thumbnail (or Automatic's gradient), its name under it, a tick when it is the one shown. */
    private View tile(GridLayout grid, int cellW, int thumbW, int thumbH, String name, String sub, File picture,
                      boolean selected, View.OnClickListener onClick) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        cell.setPadding(0, dp(10), 0, dp(14));

        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(onClick);
        float radius = dp(14);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        card.setClipToOutline(true);
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF3A4A6B, 0xFF7A5A8C, 0xFFE0A070});
        fill.setCornerRadius(radius);
        card.setBackground(fill);

        if (picture != null) {
            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(thumb, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            decoder.execute(() -> {
                Bitmap b = thumbnail(picture, thumbW);
                if (b != null) main.post(() -> thumb.setImageBitmap(b));
            });
        } else {
            // Automatic: a sunrise-to-night gradient with a sun and a cloud, no single picture
            TextView glyph = text("☀ ☁ 🌙", 26, 0xFFFFFFFF);
            glyph.setGravity(Gravity.CENTER);
            card.addView(glyph, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // focus: a white rim over the picture, drawn by a frame on top
        View rim = new View(this);
        GradientDrawable rimShape = new GradientDrawable();
        rimShape.setCornerRadius(radius);
        rimShape.setStroke(dp(3), Color.WHITE);
        rim.setBackground(rimShape);
        rim.setAlpha(0f);
        card.addView(rim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (selected) {
            TextView tick = text("✓", 16, 0xFF0B0D12);
            tick.setGravity(Gravity.CENTER);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(Color.WHITE);
            tick.setBackground(dot);
            FrameLayout.LayoutParams tickLp = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP | Gravity.END);
            tickLp.topMargin = dp(8);
            tickLp.rightMargin = dp(8);
            card.addView(tick, tickLp);
        }

        TextView label = text(name, 16, selected ? 0xFFFFFFFF : 0xCCFFFFFF);
        label.setGravity(Gravity.CENTER);
        card.setOnFocusChangeListener((v, focused) -> {
            float s = focused ? 1.07f : 1f;
            card.animate().scaleX(s).scaleY(s).translationZ(focused ? dp(10) : 0).setDuration(180).start();
            rim.animate().alpha(focused ? 1f : 0f).setDuration(180).start();
            label.setTextColor(focused || selected ? 0xFFFFFFFF : 0xCCFFFFFF);
        });

        cell.addView(card, new LinearLayout.LayoutParams(thumbW, thumbH));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(thumbW, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(10);
        cell.addView(label, labelLp);
        if (sub != null) {
            TextView subText = text(sub, 13, 0x99FFFFFF);
            subText.setGravity(Gravity.CENTER);
            cell.addView(subText, new LinearLayout.LayoutParams(thumbW, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = cellW;
        grid.addView(cell, lp);
        return card;
    }

    /** Saves the pick (null: Automatic, the theme decides) the way Launcher settings does, and goes back to Home. */
    private void pick(SharedPreferences choices, String file) {
        SharedPreferences.Editor e = choices.edit();
        if (file == null) e.remove(Config.WALLPAPER_CHOICE_KEY);
        else e.putString(Config.WALLPAPER_CHOICE_KEY, file);
        e.putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
        setResult(RESULT_OK);
        finish();
    }

    /** "09-dusk-beach.jpg" -> "Dusk beach": the order number, the extension and the dashes off. */
    static String niceName(String file) {
        String n = file.replaceFirst("\\.[^.]+$", "").replaceFirst("^\\d+[-_ ]*", "").replace('-', ' ').replace('_', ' ').trim();
        if (n.isEmpty()) return file;
        return n.substring(0, 1).toUpperCase(Locale.ROOT) + n.substring(1);
    }

    private Bitmap thumbnail(File f, int width) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= width) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(inter);
        t.setSingleLine(true);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    @Override
    protected void onDestroy() {
        decoder.shutdownNow();
        super.onDestroy();
    }
}
