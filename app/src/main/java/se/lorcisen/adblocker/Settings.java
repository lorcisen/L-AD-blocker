package se.lorcisen.adblocker;

import android.content.Context;
import android.content.SharedPreferences;

/** User settings and the catalogue of blocklists and DNS servers. */
final class Settings {
    private Settings() {}

    static final class Source {
        final String id;
        /** Product name, or null when {@link #nameRes} should be used. */
        private final String fixedName;
        private final int nameRes;
        final int descRes;
        final String[] urls;
        final boolean defaultEnabled;

        Source(String id, String fixedName, int nameRes, int descRes, boolean defaultEnabled, String... urls) {
            this.id = id;
            this.fixedName = fixedName;
            this.nameRes = nameRes;
            this.descRes = descRes;
            this.urls = urls;
            this.defaultEnabled = defaultEnabled;
        }

        String name(Context c) {
            return fixedName != null ? fixedName : c.getString(nameRes);
        }
    }

    private static final String HAGEZI = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/";

    static final Source[] SOURCES = {
        new Source("hagezi_pro", "HaGeZi Multi PRO", 0, R.string.source_hagezi_desc, true,
                HAGEZI + "wildcard/pro-onlydomains.txt"),
        new Source("stevenblack", "StevenBlack Unified", 0, R.string.source_stevenblack_desc, true,
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"),
        new Source("adaway", "AdAway", 0, R.string.source_adaway_desc, true,
                "https://adaway.org/hosts.txt"),
        new Source("hagezi_native", null, R.string.source_native_name, R.string.source_native_desc, false,
                HAGEZI + "adblock/native.samsung.txt",
                HAGEZI + "adblock/native.xiaomi.txt",
                HAGEZI + "adblock/native.huawei.txt",
                HAGEZI + "adblock/native.oppo-realme.txt",
                HAGEZI + "adblock/native.vivo.txt"),
        new Source("oisd_big", "OISD Big", 0, R.string.source_oisd_desc, false,
                "https://big.oisd.nl/"),
    };

    static final String[] UPSTREAM_NAMES = {"AdGuard DNS", "Cloudflare", "Quad9", "Google"};
    static final String[][] UPSTREAM_SERVERS = {
        {"94.140.14.14", "94.140.15.15"},
        {"1.1.1.1", "1.0.0.1"},
        {"9.9.9.9", "149.112.112.112"},
        {"8.8.8.8", "8.8.4.4"},
    };

    /** Well-known public DNS servers that apps sometimes hard-code to bypass the system DNS. */
    static final String[] HARDCODED_DNS = {
        "8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9", "149.112.112.112",
        "208.67.222.222", "208.67.220.220", "94.140.14.14", "94.140.15.15",
    };

    static final String KEY_WANT_RUNNING = "want_running";
    static final String KEY_UPSTREAM = "upstream";
    static final String KEY_CATCH_HARDCODED = "catch_hardcoded_dns";
    static final String KEY_ALLOWLIST = "allowlist";
    static final String KEY_BLOCKLIST = "custom_blocklist";
    static final String KEY_LAST_UPDATE = "last_update";
    static final String KEY_TOTAL_BLOCKED = "total_blocked";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static boolean isSourceEnabled(Context c, Source s) {
        return prefs(c).getBoolean("source_" + s.id, s.defaultEnabled);
    }

    static void setSourceEnabled(Context c, Source s, boolean enabled) {
        prefs(c).edit().putBoolean("source_" + s.id, enabled).apply();
    }

    static String[] upstreamServers(Context c) {
        int i = prefs(c).getInt(KEY_UPSTREAM, 0);
        if (i < 0 || i >= UPSTREAM_SERVERS.length) i = 0;
        return UPSTREAM_SERVERS[i];
    }
}
