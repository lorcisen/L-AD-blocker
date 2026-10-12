package se.lorcisen.adblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Hides ads that apps serve from their own servers, which DNS blocking cannot touch:
 * sponsored posts in Facebook and Instagram are covered with an overlay, and YouTube's
 * "Skip ad" button is pressed as soon as it appears.
 *
 * <p>It only looks at the screens of those apps and keeps nothing, except a layout dump when
 * the user turns on troubleshooting mode.
 */
public class AdHiderService extends AccessibilityService {
    static final String FACEBOOK = "com.facebook.katana";
    static final String INSTAGRAM = "com.instagram.android";
    static final String YOUTUBE = "com.google.android.youtube";

    static final String KEY_FACEBOOK = "hide_facebook";
    static final String KEY_INSTAGRAM = "hide_instagram";
    static final String KEY_YOUTUBE = "skip_youtube";
    static final String KEY_DIAGNOSTICS = "diagnostics";
    static final String KEY_HIDDEN_COUNT = "hidden_ads";

    /**
     * The "Sponsored" label and the like button that ends each post, per language. Only the
     * phone's language and English are searched for, because every search runs inside the other
     * app and too many would make its scrolling stutter.
     */
    private static final String[][] WORDS = {
        // language, sponsored labels..., "|", like buttons...
        {"en", "Sponsored", "|", "Like"},
        {"sv", "Sponsras", "Sponsrad", "Sponsrat", "|", "Gilla"},
        {"es", "Publicidad", "Patrocinado", "Patrocinada", "|", "Me gusta"},
        {"de", "Gesponsert", "|", "Gefällt mir"},
        {"fr", "Sponsorisé", "Sponsorisée", "|", "J’aime", "J'aime"},
        {"it", "Sponsorizzato", "Sponsorizzata", "|", "Mi piace"},
        {"pt", "Patrocinado", "Patrocinada", "|", "Curtir", "Gosto"},
        {"nl", "Gesponsord", "|", "Vind ik leuk"},
        {"pl", "Sponsorowane", "|", "Lubię to"},
        {"da", "Sponsoreret", "|", "Synes godt om", "Lik"},
        {"nb", "Sponset", "|", "Lik", "Liker"},
        {"no", "Sponset", "|", "Lik", "Liker"},
        {"fi", "Sponsoroitu", "|", "Tykkää"},
        {"tr", "Sponsorlu", "|", "Beğen"},
        {"ru", "Реклама", "|", "Нравится"},
        {"uk", "Реклама", "Спонсорована реклама", "|", "Подобається"},
        {"ja", "広告", "|", "いいね！"},
        {"zh", "赞助内容", "贊助", "|", "赞", "讚"},
        {"ko", "광고", "|", "좋아요"},
        {"in", "Bersponsor", "|", "Suka"},
        {"id", "Bersponsor", "|", "Suka"},
        {"hi", "प्रायोजित", "|", "पसंद करें"},
    };

    private static String[] sponsoredWords = {"Sponsored"};
    private static String[] likeWords = {"Like"};
    private static Pattern sponsoredPattern;
    private static Set<String> activeLangs = new HashSet<>();
    private static final String KEY_LEARNED_LANGS = "ad_hider_langs";

    /**
     * Picks the words to search for: the phone's languages (Facebook follows them by default),
     * the app's language, English, and any language seen earlier on Facebook's screen.
     */
    private static synchronized void loadWords(Set<String> learned) {
        Set<String> langs = new HashSet<>(learned);
        android.os.LocaleList system = android.content.res.Resources.getSystem().getConfiguration().getLocales();
        for (int i = 0; i < system.size(); i++) langs.add(system.get(i).getLanguage());
        langs.add(Locale.getDefault().getLanguage());
        langs.add("en");
        Set<String> sponsored = new LinkedHashSet<>();
        Set<String> like = new LinkedHashSet<>();
        for (String[] row : WORDS) {
            if (!langs.contains(row[0])) continue;
            boolean likePart = false;
            for (int i = 1; i < row.length; i++) {
                if (row[i].equals("|")) likePart = true;
                else (likePart ? like : sponsored).add(row[i]);
            }
        }
        sponsoredWords = sponsored.toArray(new String[0]);
        likeWords = like.toArray(new String[0]);
        sponsoredPattern = buildPattern(sponsored);
        activeLangs = langs;
    }

    private void loadWords() {
        loadWords(Settings.prefs(this).getStringSet(KEY_LEARNED_LANGS, new HashSet<>()));
    }

    /**
     * When nothing is found in the expected languages (e.g. Facebook is set to another language
     * than the phone), every few seconds tries all languages and remembers one that matches.
     */
    private void searchOtherLanguages(AccessibilityNodeInfo root, List<AccessibilityNodeInfo> out) {
        long now = SystemClock.uptimeMillis();
        if (now - lastFullSearch < 4000) return;
        lastFullSearch = now;
        for (String[] row : WORDS) {
            if (activeLangs.contains(row[0])) continue;
            List<String> words = new ArrayList<>();
            for (int i = 1; i < row.length && !row[i].equals("|"); i++) words.add(row[i]);
            Pattern p = buildPattern(words);
            for (String word : words) {
                List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(word);
                if (found == null) continue;
                for (AccessibilityNodeInfo n : found) {
                    if (!matches(p, n.getText()) && !matches(p, n.getContentDescription())) continue;
                    Set<String> learned = new HashSet<>(
                            Settings.prefs(this).getStringSet(KEY_LEARNED_LANGS, new HashSet<>()));
                    learned.add(row[0]);
                    Settings.prefs(this).edit().putStringSet(KEY_LEARNED_LANGS, learned).apply();
                    loadWords(learned);
                    out.add(n);
                    return;
                }
            }
        }
    }

    private static boolean matches(Pattern p, CharSequence cs) {
        if (cs == null) return false;
        String s = cs.toString().replace('\u00a0', ' ').trim();
        return !s.isEmpty() && s.length() <= 100 && p.matcher(s).find();
    }

    static Pattern buildPattern(Iterable<String> words) {
        StringBuilder re = new StringBuilder("(?iu)(^|[\\s,·•|])(");
        int k = 0;
        for (String w : words) re.append(k++ > 0 ? "|" : "").append(Pattern.quote(w));
        re.append(")(\\s*[·•,.|:]|\\s*$)");
        return Pattern.compile(re.toString());
    }

    private static final String SKIP_BUTTON_ID = YOUTUBE + ":id/skip_ad_button";
    private static final String[] SKIP = {
        "Skip", "Skip ad", "Skip ads", "Hoppa över", "Überspringen", "Passer", "Omitir", "Saltar",
        "Salta", "Pular", "Overslaan", "Pomiń", "Ohita", "Atla", "Пропустить", "Пропустити",
        "スキップ", "跳过", "건너뛰기", "Lewati", "Spring over", "Hopp over",
    };

    private static volatile boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<View> covers = new ArrayList<>();
    private final Set<String> countedAds = new HashSet<>();
    private final Runnable scan = this::scan;
    private WindowManager windowManager;
    private String currentPackage = "";
    private long lastScan;
    private long lastSkip;
    private long lastDump;
    private long lastFullSearch;
    private final Rect lastFeedBounds = new Rect();
    private long lastUserScroll;
    private long gestureUntil;
    private int coverColor;
    private AccessibilityNodeInfo lastFeed;
    private final LinkedHashSet<String> adRows = new LinkedHashSet<>();

    static boolean isRunning() {
        return running;
    }

    /** Whether the user has turned the service on in Android's accessibility settings. */
    static boolean isEnabled(Context c) {
        String enabled = android.provider.Settings.Secure.getString(c.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(c, AdHiderService.class);
        for (String s : enabled.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (me.equals(cn)) return true;
        }
        return false;
    }

    static File diagnosticsFile(Context c) {
        return new File(c.getFilesDir(), "diagnostics.txt");
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Locales.wrap(base));
    }

    @Override
    protected void onServiceConnected() {
        windowManager = getSystemService(WindowManager.class);
        loadWords();
        running = true;
    }

    @Override
    public void onDestroy() {
        running = false;
        clearCovers();
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {
        clearCovers();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        if (pkg.equals(getPackageName())) return;

        boolean feedApp = (pkg.equals(FACEBOOK) && enabled(KEY_FACEBOOK))
                || (pkg.equals(INSTAGRAM) && enabled(KEY_INSTAGRAM));
        boolean youtube = pkg.equals(YOUTUBE) && enabled(KEY_YOUTUBE);

        if (!feedApp && !youtube) {
            // Another app or the launcher came to the front: our covers must not stay on top.
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && !pkg.equals("com.android.systemui")) {
                currentPackage = pkg;
                clearCovers();
            }
            return;
        }
        if (!pkg.equals(currentPackage)) {
            currentPackage = pkg;
            lastFeed = null;
            coverColor = 0;
            clearCovers();
        }

        if (youtube) {
            skipYouTubeAd();
            return;
        }

        // Move existing covers along with the scroll right away, then rescan shortly after.
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (SystemClock.uptimeMillis() > gestureUntil) lastUserScroll = SystemClock.uptimeMillis();
            if (Build.VERSION.SDK_INT >= 28) {
                int dy = event.getScrollDeltaY();
                if (dy != 0 && dy != -1) shiftCovers(-dy);
            }
        }
        long now = SystemClock.uptimeMillis();
        handler.removeCallbacks(scan);
        if (now - lastScan > 120) scan();
        else handler.postDelayed(scan, 120);
    }

    private boolean enabled(String key) {
        return Settings.prefs(this).getBoolean(key, true);
    }

    // ------------------------------------------------------------------ Facebook / Instagram

    private void scan() {
        lastScan = SystemClock.uptimeMillis();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        CharSequence rootPkg = root.getPackageName();
        if (rootPkg == null || !(rootPkg.toString().equals(FACEBOOK) || rootPkg.toString().equals(INSTAGRAM))) {
            clearCovers();
            return;
        }
        Rect window = new Rect();
        root.getBoundsInScreen(window);

        List<Rect> rects = new ArrayList<>();
        for (AccessibilityNodeInfo label : findSponsoredLabels(root)) {
            Rect r = coverFor(label, window);
            if (r != null && r.height() > 24 && !containsSimilar(rects, r)) {
                rects.add(r);
                countAd(label, r);
            }
        }
        Rect leftover = coverLeftovers(root, rects);
        if (leftover != null && leftover.height() > 24 && !containsSimilar(rects, leftover)) rects.add(leftover);
        showCovers(rects);
        skipPastAds(rects, window);

        if (Settings.prefs(this).getBoolean(KEY_DIAGNOSTICS, false)
                && SystemClock.uptimeMillis() - lastDump > 3000) {
            lastDump = SystemClock.uptimeMillis();
            dump(root);
        }
    }

    private List<AccessibilityNodeInfo> findSponsoredLabels(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String word : sponsoredWords) {
            List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(word);
            if (found == null) continue;
            for (AccessibilityNodeInfo n : found) {
                if (!isSponsoredLabel(n.getText()) && !isSponsoredLabel(n.getContentDescription())) continue;
                Rect b = new Rect();
                n.getBoundsInScreen(b);
                if (b.isEmpty() || !seen.add(b.toShortString())) continue;
                out.add(n);
            }
        }
        if (out.isEmpty()) searchOtherLanguages(root, out);
        return out;
    }

    /**
     * True for labels such as "Sponsrad", "Sponsrad · 🌐" or "NIBE, Sponsrad · Visa mer", but
     * not for ordinary post text that happens to contain the word.
     */
    static boolean isSponsoredLabel(CharSequence cs) {
        Pattern p = sponsoredPattern;
        if (cs == null || p == null) return false;
        String s = cs.toString().replace(' ', ' ').trim();
        if (s.isEmpty() || s.length() > 100) return false;
        return p.matcher(s).find();
    }

    /**
     * The screen area of the post that {@code label} belongs to: from the top of its row in the
     * feed down to the row with the post's like button, clipped to the feed.
     */
    private Rect coverFor(AccessibilityNodeInfo label, Rect window) {
        AccessibilityNodeInfo row = label;
        AccessibilityNodeInfo feed = label.getParent();
        int depth = 0;
        while (feed != null && !feed.isScrollable() && depth++ < 40) {
            row = feed;
            feed = feed.getParent();
        }

        Rect area = new Rect();
        if (feed == null) {
            // Full-screen viewer (reels, stories): cover the whole window.
            area.set(window);
            return area;
        }
        Rect feedBounds = new Rect();
        feed.getBoundsInScreen(feedBounds);
        lastFeedBounds.set(feedBounds);
        row.getBoundsInScreen(area);

        lastFeed = feed;
        rememberRow(row);
        if (!containsLikeButton(row)) {
            int index = indexOfChild(feed, row);
            int bottom = feedBounds.bottom;
            for (int i = index + 1; index >= 0 && i < feed.getChildCount() && i <= index + 8; i++) {
                AccessibilityNodeInfo next = feed.getChild(i);
                if (next == null) continue;
                Rect nb = new Rect();
                next.getBoundsInScreen(nb);
                if (containsSponsored(next)) {
                    bottom = nb.top;
                    break;
                }
                rememberRow(next);
                if (containsLikeButton(next)) {
                    bottom = nb.bottom;
                    break;
                }
            }
            area.bottom = Math.max(area.bottom, bottom);
        }
        if (!area.intersect(feedBounds)) return null;
        return area;
    }

    /** Remembers a feed row as part of an ad, so it stays covered after the label scrolls away. */
    private void rememberRow(AccessibilityNodeInfo row) {
        String print = fingerprint(row);
        if (print.isEmpty()) return;
        adRows.remove(print);
        adRows.add(print);
        if (adRows.size() > 300) adRows.remove(adRows.iterator().next());
    }

    /**
     * Covers the rows at the top of the feed that belong to an ad whose "Sponsored" label has
     * already scrolled out of view.
     */
    private Rect coverLeftovers(AccessibilityNodeInfo root, List<Rect> covered) {
        AccessibilityNodeInfo feed = lastFeed;
        if (feed == null || !feed.refresh()) return null;
        Rect feedBounds = new Rect();
        feed.getBoundsInScreen(feedBounds);
        Rect area = null;
        for (int i = 0; i < feed.getChildCount() && i < 8; i++) {
            AccessibilityNodeInfo row = feed.getChild(i);
            if (row == null) break;
            Rect rb = new Rect();
            row.getBoundsInScreen(rb);
            if (rb.bottom <= feedBounds.top) continue; // fully scrolled away
            boolean alreadyCovered = false;
            for (Rect c : covered) if (c.contains(rb.centerX(), rb.centerY())) alreadyCovered = true;
            if (alreadyCovered || !adRows.contains(fingerprint(row))) break;
            boolean footer = containsLikeButton(row);
            // A like bar alone looks the same under every post; only cover it right after ad rows.
            if (footer && area == null) break;
            if (area == null) area = new Rect(rb);
            else area.union(rb);
            if (footer) break; // end of the ad
        }
        if (area == null || !area.intersect(feedBounds)) return null;
        return area;
    }

    /** The text of a row (and a few of its descendants), used to recognise it again later. */
    private static String fingerprint(AccessibilityNodeInfo row) {
        StringBuilder sb = new StringBuilder();
        java.util.ArrayDeque<AccessibilityNodeInfo> queue = new java.util.ArrayDeque<>();
        queue.add(row);
        int seen = 0;
        while (!queue.isEmpty() && seen++ < 16) {
            AccessibilityNodeInfo n = queue.poll();
            if (!TextUtils.isEmpty(n.getText())) sb.append(n.getText()).append('|');
            if (!TextUtils.isEmpty(n.getContentDescription())) sb.append(n.getContentDescription()).append('|');
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo c = n.getChild(i);
                if (c != null) queue.add(c);
            }
        }
        return sb.toString();
    }

    private static int indexOfChild(AccessibilityNodeInfo parent, AccessibilityNodeInfo child) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            AccessibilityNodeInfo c = parent.getChild(i);
            if (child.equals(c)) return i;
        }
        return -1;
    }

    private static boolean containsLikeButton(AccessibilityNodeInfo node) {
        for (String word : likeWords) {
            List<AccessibilityNodeInfo> found = node.findAccessibilityNodeInfosByText(word);
            if (found == null) continue;
            for (AccessibilityNodeInfo n : found) {
                if (isLikeButton(n.getText(), word) || isLikeButton(n.getContentDescription(), word)) return true;
            }
        }
        return false;
    }

    /**
     * True for like buttons such as "Gilla", "Like" or Facebook's "Knappen Gilla. Dubbeltryck och
     * håll ned…": the word on its own, within a short label.
     */
    static boolean isLikeButton(CharSequence cs, String word) {
        if (cs == null) return false;
        String s = cs.toString();
        if (s.length() > 120) return false;
        int i = 0;
        while ((i = s.toLowerCase(Locale.ROOT).indexOf(word.toLowerCase(Locale.ROOT), i)) >= 0) {
            int end = i + word.length();
            boolean startOk = i == 0 || !Character.isLetter(s.charAt(i - 1));
            boolean endOk = end == s.length() || !Character.isLetter(s.charAt(end));
            if (startOk && endOk) return true;
            i = end;
        }
        return false;
    }

    private static boolean containsSponsored(AccessibilityNodeInfo node) {
        for (String word : sponsoredWords) {
            List<AccessibilityNodeInfo> found = node.findAccessibilityNodeInfosByText(word);
            if (found == null) continue;
            for (AccessibilityNodeInfo n : found) {
                if (isSponsoredLabel(n.getText()) || isSponsoredLabel(n.getContentDescription())) return true;
            }
        }
        return false;
    }

    private static boolean startsWithWord(CharSequence cs, String word) {
        if (cs == null) return false;
        String s = cs.toString().trim();
        if (s.length() > 60 || !s.regionMatches(true, 0, word, 0, word.length())) return false;
        return s.length() == word.length() || !Character.isLetter(s.charAt(word.length()));
    }

    private static boolean containsSimilar(List<Rect> rects, Rect r) {
        for (Rect o : rects) if (Rect.intersects(o, r)) {
            o.union(r);
            return true;
        }
        return false;
    }

    private void countAd(AccessibilityNodeInfo label, Rect r) {
        // Count each ad once: identify it by the label's text plus the row's content hash.
        AccessibilityNodeInfo parent = label.getParent();
        String key = currentPackage + "|" + (parent != null ? parent.hashCode() : 0) + "|" + label.hashCode();
        if (!countedAds.add(key)) return;
        if (countedAds.size() > 500) countedAds.clear();
        long n = Settings.prefs(this).getLong(KEY_HIDDEN_COUNT, 0);
        Settings.prefs(this).edit().putLong(KEY_HIDDEN_COUNT, n + 1).apply();
    }

    // ------------------------------------------------------------------ skipping

    /**
     * Once the user has stopped scrolling and an ad has reached the upper half of the feed, scrolls
     * the feed forward by exactly the ad's height so the next post takes its place. Full-screen ads
     * (reels) are swiped away.
     */
    private void skipPastAds(List<Rect> ads, Rect window) {
        if (ads.isEmpty() || Build.VERSION.SDK_INT < 24) return;
        long now = SystemClock.uptimeMillis();
        if (now < gestureUntil) return;
        if (now - lastUserScroll < 450) {
            // Wait until the user lets go; scrolling against their finger would feel broken.
            handler.removeCallbacks(scan);
            handler.postDelayed(scan, 500);
            return;
        }
        Rect feed = lastFeedBounds;
        for (Rect ad : ads) {
            if (ad.equals(window) || feed.isEmpty()) {
                if (ad.equals(window)) swipe(window.centerX(), window.top + window.height() * 3 / 4,
                        window.top + window.height() / 4, 250, false);
                return;
            }
            if (ad.top > feed.top + feed.height() / 2) continue; // not reached yet
            int dist = ad.height();
            if (dist < Ui.dp(this, 24)) continue;
            int startY = feed.bottom - Ui.dp(this, 48);
            int endY = Math.max(feed.top + Ui.dp(this, 16),
                    startY - dist - android.view.ViewConfiguration.get(this).getScaledTouchSlop());
            swipe(feed.centerX(), startY, endY, Math.max(280, (startY - endY) / 2), true);
            return;
        }
    }

    /** A swipe that ends by holding still, so the feed moves exactly the distance and doesn't fling. */
    private void swipe(int x, int fromY, int toY, int durationMs, boolean holdAtEnd) {
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(x, fromY);
        path.lineTo(x, toY);
        gestureUntil = SystemClock.uptimeMillis() + durationMs + 600;
        android.accessibilityservice.GestureDescription.StrokeDescription stroke;
        if (holdAtEnd && Build.VERSION.SDK_INT >= 26) {
            stroke = new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, durationMs, true);
            android.accessibilityservice.GestureDescription.StrokeDescription first = stroke;
            dispatchGesture(new android.accessibilityservice.GestureDescription.Builder().addStroke(first).build(),
                    new GestureResultCallback() {
                        @Override
                        public void onCompleted(android.accessibilityservice.GestureDescription g) {
                            android.graphics.Path hold = new android.graphics.Path();
                            hold.moveTo(x, toY);
                            hold.lineTo(x, toY + 1);
                            dispatchGesture(new android.accessibilityservice.GestureDescription.Builder()
                                    .addStroke(first.continueStroke(hold, 0, 160, false)).build(), null, null);
                        }
                    }, null);
        } else {
            stroke = new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, durationMs);
            dispatchGesture(new android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build(), null, null);
        }
        handler.postDelayed(scan, durationMs + 400);
    }

    // ------------------------------------------------------------------ overlays

    private void showCovers(List<Rect> rects) {
        if (windowManager == null) return;
        while (covers.size() > rects.size()) {
            removeCover(covers.remove(covers.size() - 1));
        }
        for (int i = 0; i < rects.size(); i++) {
            Rect r = rects.get(i);
            if (i < covers.size()) {
                View v = covers.get(i);
                WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
                if (lp.x != r.left || lp.y != r.top || lp.width != r.width() || lp.height != r.height()) {
                    lp.x = r.left;
                    lp.y = r.top;
                    lp.width = r.width();
                    lp.height = r.height();
                    try {
                        windowManager.updateViewLayout(v, lp);
                    } catch (RuntimeException ignored) {
                    }
                }
            } else {
                if (covers.isEmpty() && coverColor == 0) sampleBackground(r);
                View v = makeCover();
                try {
                    windowManager.addView(v, coverParams(r));
                    covers.add(v);
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private void shiftCovers(int dy) {
        for (View v : covers) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
            lp.y += dy;
            try {
                windowManager.updateViewLayout(v, lp);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void clearCovers() {
        handler.removeCallbacks(scan);
        for (View v : covers) removeCover(v);
        covers.clear();
    }

    private void removeCover(View v) {
        try {
            windowManager.removeViewImmediate(v);
        } catch (RuntimeException ignored) {
        }
    }

    private WindowManager.LayoutParams coverParams(Rect r) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                r.width(), r.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = r.left;
        lp.y = r.top;
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return lp;
    }

    /**
     * A plain patch in the app's own background colour, so the ad simply looks like empty space
     * for the moment before it is scrolled past.
     */
    private View makeCover() {
        View v = new View(this);
        v.setBackgroundColor(backgroundColor());
        v.setContentDescription(getString(R.string.cover_text));
        return v;
    }

    private int backgroundColor() {
        if (coverColor != 0) return coverColor;
        int night = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES ? 0xFF242526 : 0xFFFFFFFF;
    }

    /** Reads the real background colour from the screen (Android 11+) and recolours the covers. */
    private void sampleBackground(Rect ad) {
        if (Build.VERSION.SDK_INT < 30 || ad.isEmpty()) return;
        int x = ad.left + Ui.dp(this, 4);
        int y = ad.top + Ui.dp(this, 6);
        try {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult result) {
                    android.graphics.Bitmap hw = android.graphics.Bitmap.wrapHardwareBuffer(
                            result.getHardwareBuffer(), result.getColorSpace());
                    result.getHardwareBuffer().close();
                    if (hw == null) return;
                    android.graphics.Bitmap bmp = hw.copy(android.graphics.Bitmap.Config.ARGB_8888, false);
                    hw.recycle();
                    if (x < bmp.getWidth() && y < bmp.getHeight()) {
                        coverColor = bmp.getPixel(x, y) | 0xFF000000;
                        for (View v : covers) v.setBackgroundColor(coverColor);
                    }
                    bmp.recycle();
                }

                @Override
                public void onFailure(int errorCode) {}
            });
        } catch (RuntimeException ignored) {
        }
    }

    // ------------------------------------------------------------------ YouTube

    private void skipYouTubeAd() {
        long now = SystemClock.uptimeMillis();
        if (now - lastSkip < 700) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo target = null;
        List<AccessibilityNodeInfo> byId = root.findAccessibilityNodeInfosByViewId(SKIP_BUTTON_ID);
        if (byId != null && !byId.isEmpty()) target = byId.get(0);
        if (target == null) {
            outer:
            for (String word : SKIP) {
                List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(word);
                if (found == null) continue;
                for (AccessibilityNodeInfo n : found) {
                    if (startsWithWord(n.getText(), word) || startsWithWord(n.getContentDescription(), word)) {
                        target = n;
                        break outer;
                    }
                }
            }
        }
        if (target == null) return;
        AccessibilityNodeInfo clickable = target;
        int depth = 0;
        while (clickable != null && !clickable.isClickable() && depth++ < 5) clickable = clickable.getParent();
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            lastSkip = now;
            long n = Settings.prefs(this).getLong(KEY_HIDDEN_COUNT, 0);
            Settings.prefs(this).edit().putLong(KEY_HIDDEN_COUNT, n + 1).apply();
        }
    }

    // ------------------------------------------------------------------ troubleshooting

    /** Writes the screen's layout (no images) so ad detection can be tuned for new app versions. */
    private void dump(AccessibilityNodeInfo root) {
        StringBuilder sb = new StringBuilder();
        sb.append("L-AD Blocker diagnostics ").append(currentPackage)
                .append(" android=").append(Build.VERSION.SDK_INT)
                .append(" locale=").append(Locale.getDefault()).append('\n');
        int[] count = {0};
        dumpNode(root, 0, sb, count);
        try (FileWriter w = new FileWriter(diagnosticsFile(this), false)) {
            w.write(sb.toString());
        } catch (IOException ignored) {
        }
    }

    private static void dumpNode(AccessibilityNodeInfo n, int depth, StringBuilder sb, int[] count) {
        if (n == null || count[0]++ > 2500 || depth > 60) return;
        Rect b = new Rect();
        n.getBoundsInScreen(b);
        for (int i = 0; i < depth; i++) sb.append(' ');
        String cls = n.getClassName() == null ? "?" : n.getClassName().toString();
        sb.append(cls.substring(cls.lastIndexOf('.') + 1));
        if (n.getViewIdResourceName() != null) sb.append(" id=").append(n.getViewIdResourceName());
        if (!TextUtils.isEmpty(n.getText())) sb.append(" t=\"").append(clip(n.getText())).append('"');
        if (!TextUtils.isEmpty(n.getContentDescription())) sb.append(" d=\"").append(clip(n.getContentDescription())).append('"');
        if (n.isScrollable()) sb.append(" SCROLL");
        if (n.isClickable()) sb.append(" CLICK");
        sb.append(' ').append(b.toShortString()).append('\n');
        for (int i = 0; i < n.getChildCount(); i++) dumpNode(n.getChild(i), depth + 1, sb, count);
    }

    private static String clip(CharSequence cs) {
        String s = cs.toString().replace('\n', ' ');
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
