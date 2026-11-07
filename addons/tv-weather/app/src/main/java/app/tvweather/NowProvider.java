package app.tvweather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The current weather for the Clean Your TV launcher's "weather" key. Nothing is scheduled: when Home asks and the
 * saved reading is over half an hour old, one fetch from Open-Meteo (free, no account, no key) runs in the background
 * and Home is told when it lands. While Home is not on screen nothing asks, so nothing runs and the TV is never woken.
 * A failed fetch keeps the last good reading; the launcher stops showing a reading that is hours old.
 */
public final class NowProvider extends ContentProvider {
    static final String AUTHORITY = "app.tvweather.now";
    static final Uri NOW = Uri.parse("content://" + AUTHORITY + "/now");
    static final String[] COLUMNS = {"temp_c", "condition", "label", "place", "is_day", "updated_ms", "high_c", "low_c", "rain_pct"};
    private static final long STALE_MS = 30 * 60_000L;
    private static final long RETRY_MS = 10 * 60_000L;
    private static final String SAVED = "now.json";

    private static final ExecutorService ONE = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean FETCHING = new AtomicBoolean();
    private static volatile long lastFailureMs;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!"now".equals(uri.getLastPathSegment())) return null;
        Context ctx = getContext();
        if (ctx == null) return null;
        MatrixCursor c = new MatrixCursor(COLUMNS);
        JSONObject saved = read(ctx);
        long now = System.currentTimeMillis();
        if (saved != null) {
            c.addRow(new Object[] {saved.optDouble("temp_c"), saved.optString("condition"), saved.optString("label"),
                    ctx.getString(R.string.place), saved.optInt("is_day", 1), saved.optLong("updated_ms"),
                    saved.optDouble("high_c", Double.NaN), saved.optDouble("low_c", Double.NaN),
                    saved.optInt("rain_pct", -1)});
        }
        c.setNotificationUri(ctx.getContentResolver(), NOW);
        long updated = saved == null ? 0 : saved.optLong("updated_ms");
        if (now - updated >= STALE_MS && now - lastFailureMs >= RETRY_MS) refresh(ctx.getApplicationContext());
        return c;
    }

    private static void refresh(Context app) {
        if (!FETCHING.compareAndSet(false, true)) return;
        ONE.execute(() -> {
            try {
                JSONObject reading = fetch(app);
                Files.write(new File(app.getFilesDir(), SAVED).toPath(), reading.toString().getBytes(StandardCharsets.UTF_8));
                app.getContentResolver().notifyChange(NOW, null);
            } catch (IOException | JSONException | RuntimeException failed) {
                lastFailureMs = System.currentTimeMillis();
            } finally {
                FETCHING.set(false);
            }
        });
    }

    private static JSONObject fetch(Context ctx) throws IOException, JSONException {
        URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + ctx.getString(R.string.latitude)
                + "&longitude=" + ctx.getString(R.string.longitude)
                + "&current=temperature_2m,weather_code,is_day"
                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=1"
                + "&timezone=auto");
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        http.setConnectTimeout(15_000);
        http.setReadTimeout(20_000);
        http.setRequestProperty("User-Agent", "tv-weather/1.0");
        try {
            if (http.getResponseCode() != 200) throw new IOException("HTTP " + http.getResponseCode());
            JSONObject all = new JSONObject(readAll(http.getInputStream()));
            JSONObject current = all.getJSONObject("current");
            JSONObject daily = all.optJSONObject("daily");
            int code = current.getInt("weather_code");
            JSONObject out = new JSONObject();
            out.put("temp_c", current.getDouble("temperature_2m"));
            out.put("condition", condition(code));
            out.put("label", label(code));
            out.put("is_day", current.optInt("is_day", 1));
            // Today's forecast, when the answer has it: the high, the low and the chance of rain.
            if (daily != null) {
                if (daily.optJSONArray("temperature_2m_max") != null)
                    out.put("high_c", daily.getJSONArray("temperature_2m_max").optDouble(0));
                if (daily.optJSONArray("temperature_2m_min") != null)
                    out.put("low_c", daily.getJSONArray("temperature_2m_min").optDouble(0));
                if (daily.optJSONArray("precipitation_probability_max") != null)
                    out.put("rain_pct", daily.getJSONArray("precipitation_probability_max").optInt(0, -1));
            }
            out.put("updated_ms", System.currentTimeMillis());
            return out;
        } finally {
            http.disconnect();
        }
    }

    /** WMO weather code to the launcher's groups: clear, cloudy, rain, fog, snow. Storms count as rain. */
    static String condition(int code) {
        if (code <= 1) return "clear";
        if (code <= 3) return "cloudy";
        if (code == 45 || code == 48) return "fog";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "snow";
        if (code >= 51) return "rain";
        return "cloudy";
    }

    static String label(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: case 56: case 57: return "Drizzle";
            case 61: case 63: case 66: return "Rain";
            case 65: case 67: return "Heavy rain";
            case 80: case 81: return "Showers";
            case 82: return "Heavy showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm, hail";
            default: return condition(code).equals("snow") ? "Snow" : "Cloudy";
        }
    }

    private static JSONObject read(Context ctx) {
        File f = new File(ctx.getFilesDir(), SAVED);
        try {
            return f.isFile() ? new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)) : null;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream s = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 256 * 1024) throw new IOException("too large");
            }
            return out.toString("UTF-8");
        }
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd." + AUTHORITY + ".now";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
