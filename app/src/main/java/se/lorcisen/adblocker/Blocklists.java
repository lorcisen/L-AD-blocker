package se.lorcisen.adblocker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads, stores and loads the blocklists, and holds the filter currently in use. */
final class Blocklists {
    private static final String TAG = "Blocklists";
    private static final long AUTO_UPDATE_AGE_MS = 3L * 24 * 60 * 60 * 1000;

    private static volatile DomainFilter current = DomainFilter.EMPTY;
    private static volatile int statusRes;
    private static volatile Object statusArg;
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();

    private Blocklists() {}

    static DomainFilter filter() {
        return current;
    }

    /** A human-readable status such as "Downloading…", or "" when idle. */
    static String status(Context c) {
        int res = statusRes;
        return res == 0 ? "" : c.getString(res, statusArg);
    }

    private static void setStatus(int res, Object arg) {
        statusArg = arg;
        statusRes = res;
    }

    /** Reloads the filter from disk in the background, then downloads lists if they are stale. */
    static void loadAsync(Context context, boolean updateIfStale) {
        Context app = context.getApplicationContext();
        worker.execute(() -> {
            load(app);
            long last = Settings.prefs(app).getLong(Settings.KEY_LAST_UPDATE, 0);
            if (updateIfStale && System.currentTimeMillis() - last > AUTO_UPDATE_AGE_MS) {
                download(app);
                load(app);
            }
        });
    }

    /** Downloads all enabled lists and reloads the filter, in the background. */
    static void updateAsync(Context context) {
        Context app = context.getApplicationContext();
        worker.execute(() -> {
            download(app);
            load(app);
        });
    }

    /** Applies edited allow/block lists immediately without re-reading the big lists. */
    static void reloadUserLists(Context context) {
        SharedPreferences p = Settings.prefs(context);
        current = current.withUserLists(
                DomainFilter.parseUserList(p.getString(Settings.KEY_ALLOWLIST, "")),
                DomainFilter.parseUserList(p.getString(Settings.KEY_BLOCKLIST, "")));
    }

    private static void load(Context c) {
        setStatus(R.string.status_loading, null);
        DomainFilter.Builder b = new DomainFilter.Builder();
        try (BufferedReader r = reader(c.getAssets().open("default_hosts.txt"))) {
            b.addAll(r);
        } catch (IOException e) {
            Log.w(TAG, "default list", e);
        }
        for (Settings.Source s : Settings.SOURCES) {
            if (!Settings.isSourceEnabled(c, s)) continue;
            for (int i = 0; i < s.urls.length; i++) {
                File f = file(c, s, i);
                if (!f.exists()) continue;
                try (BufferedReader r = reader(new FileInputStream(f))) {
                    b.addAll(r);
                } catch (IOException e) {
                    Log.w(TAG, "load " + f, e);
                }
            }
        }
        SharedPreferences p = Settings.prefs(c);
        Set<String> allow = DomainFilter.parseUserList(p.getString(Settings.KEY_ALLOWLIST, ""));
        Set<String> block = DomainFilter.parseUserList(p.getString(Settings.KEY_BLOCKLIST, ""));
        current = b.build(allow, block);
        setStatus(0, null);
    }

    private static void download(Context c) {
        int failed = 0;
        for (Settings.Source s : Settings.SOURCES) {
            if (!Settings.isSourceEnabled(c, s)) continue;
            for (int i = 0; i < s.urls.length; i++) {
                setStatus(R.string.status_downloading, s.name(c));
                try {
                    fetch(s.urls[i], file(c, s, i));
                } catch (IOException e) {
                    failed++;
                    Log.w(TAG, "download " + s.urls[i], e);
                }
            }
        }
        if (failed == 0) {
            Settings.prefs(c).edit().putLong(Settings.KEY_LAST_UPDATE, System.currentTimeMillis()).apply();
        }
        setStatus(failed == 0 ? 0 : R.string.status_failed, failed);
    }

    private static void fetch(String url, File dest) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20_000);
        conn.setReadTimeout(60_000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "L-AD-blocker");
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            File tmp = new File(dest.getPath() + ".tmp");
            dest.getParentFile().mkdirs();
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            if (!tmp.renameTo(dest)) throw new IOException("rename failed");
        } finally {
            conn.disconnect();
        }
    }

    private static File file(Context c, Settings.Source s, int index) {
        return new File(new File(c.getFilesDir(), "lists"), s.id + "_" + index + ".txt");
    }

    private static BufferedReader reader(InputStream in) {
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 65536);
    }
}
