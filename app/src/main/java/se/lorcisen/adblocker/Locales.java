package se.lorcisen.adblocker;

import android.app.Activity;
import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/**
 * The app's language. "" means "follow the phone". On Android 13+ the choice is stored by the
 * system (and also shows up under the phone's per-app language settings); on older versions it
 * is stored in our preferences and applied to each Activity and Service context.
 */
final class Locales {
    private Locales() {}

    private static final String KEY = "app_language";

    /** Supported languages; must match res/xml/locales_config.xml and the values-* folders. */
    static final String[] TAGS = {
        "", "en", "sv", "es", "de", "fr", "it", "pt", "nl", "pl", "da", "nb", "fi",
        "tr", "ru", "uk", "ja", "zh-CN", "ko", "id", "hi",
    };

    private static final String[] NAMES = {
        null, "English", "Svenska", "Español", "Deutsch", "Français", "Italiano", "Português",
        "Nederlands", "Polski", "Dansk", "Norsk", "Suomi", "Türkçe", "Русский", "Українська",
        "日本語", "简体中文", "한국어", "Bahasa Indonesia", "हिन्दी",
    };

    static String current(Context c) {
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleList l = c.getSystemService(LocaleManager.class).getApplicationLocales();
            return l.isEmpty() ? "" : match(l.get(0));
        }
        return Settings.prefs(c).getString(KEY, "");
    }

    static void set(Activity a, String tag) {
        if (Build.VERSION.SDK_INT >= 33) {
            // The system restarts the activity with the new language.
            a.getSystemService(LocaleManager.class).setApplicationLocales(
                    tag.isEmpty() ? LocaleList.getEmptyLocaleList() : LocaleList.forLanguageTags(tag));
        } else {
            Settings.prefs(a).edit().putString(KEY, tag).commit();
            a.recreate();
        }
    }

    /** Applies the chosen language to a base context (Android 12 and older). */
    static Context wrap(Context base) {
        if (Build.VERSION.SDK_INT >= 33) return base;
        String tag = Settings.prefs(base).getString(KEY, "");
        if (tag.isEmpty()) return base;
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(tag));
        return base.createConfigurationContext(config);
    }

    static String displayName(Context c, String tag) {
        for (int i = 0; i < TAGS.length; i++) {
            if (TAGS[i].equals(tag)) return i == 0 ? c.getString(R.string.language_system) : NAMES[i];
        }
        return tag;
    }

    /** The phone's own language, written in that language (e.g. "Svenska"). */
    static String systemLanguageName(Context c) {
        Locale l = Build.VERSION.SDK_INT >= 33
                ? c.getSystemService(LocaleManager.class).getSystemLocales().get(0)
                : android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0);
        String name = l.getDisplayLanguage(l);
        return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(l) + name.substring(1);
    }

    private static String match(Locale l) {
        String full = l.toLanguageTag();
        for (String t : TAGS) if (t.equalsIgnoreCase(full)) return t;
        String lang = l.getLanguage();
        if (lang.equals("no") || lang.equals("nn")) lang = "nb";
        if (lang.equals("zh")) return "zh-CN";
        if (lang.equals("in")) return "id";
        for (String t : TAGS) if (t.equals(lang)) return t;
        return full;
    }
}
