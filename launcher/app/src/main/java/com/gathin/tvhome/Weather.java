package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * weather (config.xml or a theme): the current temperature and condition from a weather add-on, shown at the top
 * left of Home and handed to the wallpaper's weather groups. Like game_day, the launcher only reads what the add-on
 * publishes and stays offline; the add-on fetches when Home asks and its copy is stale, so nothing runs while Home
 * is not on screen.
 */
final class Weather {
    static final String META = "com.gathin.tvhome.weather";
    private static final String[] COLUMNS = {"temp_c", "condition", "label", "place", "is_day", "updated_ms", "high_c", "low_c", "rain_pct"};
    /** While Home stays on screen, ask again this often; the add-on decides whether that needs a fetch. */
    private static final long REREAD_MS = 15 * 60_000L;
    /** Older than this, the reading is not shown: better nothing than a morning's weather in the evening. */
    private static final long MAX_AGE_MS = 3 * 3_600_000L;

    /** One reading. condition is "clear", "cloudy", "rain", "fog" or "snow". */
    static final class Now {
        final int tempC;
        final String condition, label, place;
        final boolean isDay;
        /** Today's forecast: high and low in whole degrees (null when the add-on had none), rain chance or -1. */
        final Integer highC, lowC;
        final int rainPct;

        Now(int tempC, String condition, String label, String place, boolean isDay, Integer highC, Integer lowC, int rainPct) {
            this.tempC = tempC;
            this.condition = condition;
            this.label = label;
            this.place = place;
            this.isDay = isDay;
            this.highC = highC;
            this.lowC = lowC;
            this.rainPct = rainPct;
        }

        /** "Clear · ↑32° ↓19°", with " · Rain 40%" only when rain is likely enough to matter. */
        String line() {
            StringBuilder s = new StringBuilder(label);
            if (highC != null && lowC != null) {
                s.append(" · ↑").append(highC).append("° ↓").append(lowC).append('°');
            } else if (!place.isEmpty()) {
                s.append(" · ").append(place);
            }
            if (rainPct >= 30) s.append(" · Rain ").append(rainPct).append('%');
            return s.toString();
        }

        /** A plain symbol for the condition, drawn by the TV's own emoji font. */
        String symbol() {
            switch (condition) {
                case "rain": return "🌧";          // cloud with rain
                case "fog": return "🌫";           // fog
                case "snow": return "🌨";          // cloud with snow
                case "cloudy": return isDay ? "⛅" : "☁"; // sun behind cloud / cloud
                default: return isDay ? "☀" : "🌙"; // sun / crescent moon
            }
        }
    }

    private final Context ctx;
    private final Uri uri;
    private final Handler main;
    private final Runnable onChange;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ContentObserver observer;
    private final Runnable reread = this::periodic;
    private boolean watching;
    private Now now;

    private Weather(Context ctx, Uri uri, Handler main, Runnable onChange) {
        this.ctx = ctx;
        this.uri = uri;
        this.main = main;
        this.onChange = onChange;
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                read();
            }
        };
    }

    /** Null when the key is empty, the package is not installed, or it does not name its own provider. */
    static Weather create(Context ctx, String pkg, Handler main, Runnable onChange) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg.trim(), PackageManager.GET_META_DATA);
            String authority = info.metaData == null ? null : info.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) return null;
            // The provider must be the named package's own, not another app's that claims the authority.
            ProviderInfo provider = pm.resolveContentProvider(authority.trim(), 0);
            if (provider == null || !pkg.trim().equals(provider.packageName)) return null;
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority.trim()).appendPath("now").build();
            return new Weather(ctx, uri, main, onChange);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Home is on screen: read now, watch for changes, and ask again now and then. */
    void start() {
        if (!watching) {
            try {
                ctx.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                // The add-on's provider went away; keep what is shown.
            }
        }
        read();
        main.removeCallbacks(reread);
        main.postDelayed(reread, REREAD_MS);
    }

    /** Home is off screen: nothing runs until it is back. */
    void stop() {
        if (watching) ctx.getContentResolver().unregisterContentObserver(observer);
        watching = false;
        main.removeCallbacks(reread);
    }

    void shutdown() {
        stop();
        io.shutdownNow();
    }

    Now now() {
        return now;
    }

    /** A whole-degree column, or null when the add-on (an older one) has no such column or no value in it. */
    private static Integer degrees(Cursor c, int column) {
        if (c.getColumnCount() <= column || c.isNull(column)) return null;
        double v = c.getDouble(column);
        return Double.isNaN(v) ? null : (int) Math.round(v);
    }

    private void periodic() {
        read();
        main.postDelayed(reread, REREAD_MS);
    }

    /** One query off the main thread (the provider lives in the add-on's process), then onChange on the main thread. */
    private void read() {
        try {
            io.execute(() -> {
                Now reading = null;
                try (Cursor c = ctx.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        long updated = c.getLong(5);
                        String condition = c.getString(1);
                        if (condition != null && System.currentTimeMillis() - updated <= MAX_AGE_MS) {
                            reading = new Now((int) Math.round(c.getDouble(0)), condition,
                                    c.getString(2) == null ? "" : c.getString(2),
                                    c.getString(3) == null ? "" : c.getString(3), c.getInt(4) != 0,
                                    degrees(c, 6), degrees(c, 7), c.getColumnCount() > 8 && !c.isNull(8) ? c.getInt(8) : -1);
                        }
                    }
                } catch (RuntimeException gone) {
                    return;   // uninstalled or crashed mid-query: keep what is shown
                }
                Now result = reading;
                main.post(() -> {
                    now = result;
                    onChange.run();
                });
            });
        } catch (RejectedExecutionException shutDown) {
            // Home is being destroyed.
        }
    }
}
