package se.lorcisen.adblocker;

import android.content.Context;
import android.content.SharedPreferences;

/** User settings and the catalogue of blocklists and DNS servers. */
final class Settings {
    private Settings() {}

    static final class Source {
        final String id;
        final String name;
        final String description;
        final String[] urls;
        final boolean defaultEnabled;

        Source(String id, String name, String description, boolean defaultEnabled, String... urls) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.urls = urls;
            this.defaultEnabled = defaultEnabled;
        }
    }

    private static final String HAGEZI = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/";

    static final Source[] SOURCES = {
        new Source("hagezi_pro", "HaGeZi Multi PRO",
                "Reklam, spårning och popup-reklam i appar, spel och webbläsare (rekommenderas)", true,
                HAGEZI + "wildcard/pro-onlydomains.txt"),
        new Source("stevenblack", "StevenBlack Unified",
                "Klassisk hosts-lista mot reklam och skadliga sidor", true,
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"),
        new Source("adaway", "AdAway",
                "Reklam i mobilappar och spel", true,
                "https://adaway.org/hosts.txt"),
        new Source("hagezi_native", "Tillverkarnas spårning",
                "Inbyggd reklam och telemetri från Samsung, Xiaomi, Huawei, Oppo/Realme, Vivo", false,
                HAGEZI + "adblock/native.samsung.txt",
                HAGEZI + "adblock/native.xiaomi.txt",
                HAGEZI + "adblock/native.huawei.txt",
                HAGEZI + "adblock/native.oppo-realme.txt",
                HAGEZI + "adblock/native.vivo.txt"),
        new Source("oisd_big", "OISD Big",
                "Stor kombinerad lista – blockerar mer men kan ibland ge fler fel", false,
                "https://big.oisd.nl/"),
    };

    static final String[] UPSTREAM_NAMES = {
        "AdGuard DNS (blockerar även reklam)",
        "Cloudflare (1.1.1.1)",
        "Quad9 (9.9.9.9)",
        "Google (8.8.8.8)",
    };
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

    /** Default allowlist: domains that blocklists sometimes catch but that break common apps. */
    static final String DEFAULT_ALLOWLIST =
            "# Domäner här blockeras aldrig (en per rad)\n";

    static final String DEFAULT_BLOCKLIST =
            "# Egna domäner att blockera (en per rad)\n";
}
