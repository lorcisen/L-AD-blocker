package se.lorcisen.adblocker;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_VPN = 1;
    private static final int REQUEST_NOTIFICATIONS = 2;
    private static final Locale SV = new Locale("sv", "SE");

    private static final int TAB_HOME = 0;
    private static final int TAB_ACTIVITY = 1;
    private static final int TAB_SETTINGS = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh(true);
            handler.postDelayed(this, 1000);
        }
    };
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(SV);

    private final View[] pages = new View[3];
    private final LinearLayout[] navItems = new LinearLayout[3];
    private int tab = TAB_HOME;

    // Home
    private PowerButton power;
    private TextView statusBadge;
    private TextView statusTitle;
    private TextView statusSubtitle;
    private TextView statBlocked;
    private TextView statShare;
    private TextView statTotal;
    private TextView databaseSubtitle;
    private TextView databaseStatus;
    private LinearLayout recentList;

    // Activity
    private final LogAdapter logAdapter = new LogAdapter();
    private TextView activityEmpty;
    private final TextView[] filterChips = new TextView[3];
    private int logFilter;
    private long logVersion = -1;

    // Settings
    private final ImageView[] upstreamChecks = new ImageView[Settings.UPSTREAM_NAMES.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        goEdgeToEdge();
        setContentView(buildRoot());
        if (Blocklists.filter().listSize() == 0) Blocklists.loadAsync(this, false);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, REQUEST_NOTIFICATIONS);
        }
        power.setOn(AdBlockVpnService.isRunning(), false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(false);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override
    public void onBackPressed() {
        if (tab != TAB_HOME) selectTab(TAB_HOME);
        else super.onBackPressed();
    }

    // ---------------------------------------------------------------- layout

    @SuppressWarnings("deprecation")
    private void goEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private View buildRoot() {
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);

        FrameLayout container = new FrameLayout(this);
        pages[TAB_HOME] = buildHome();
        pages[TAB_ACTIVITY] = buildActivity();
        pages[TAB_SETTINGS] = buildSettings();
        for (View p : pages) container.addView(p, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(container, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = buildNav();
        root.addView(nav, Ui.matchWrap());

        // Keep content clear of the status bar, camera cutout and gesture/navigation bar.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = i.top;
                bottom = i.bottom;
                left = i.left;
                right = i.right;
            } else {
                top = legacyInset(insets, 0);
                bottom = legacyInset(insets, 1);
                left = legacyInset(insets, 2);
                right = legacyInset(insets, 3);
            }
            container.setPadding(left, top, right, 0);
            nav.setPadding(left, Ui.dp(this, 8), right, bottom + Ui.dp(this, 8));
            return insets;
        });
        selectTab(TAB_HOME);
        return root;
    }

    @SuppressWarnings("deprecation")
    private static int legacyInset(WindowInsets in, int side) {
        switch (side) {
            case 0: return in.getSystemWindowInsetTop();
            case 1: return in.getSystemWindowInsetBottom();
            case 2: return in.getSystemWindowInsetLeft();
            default: return in.getSystemWindowInsetRight();
        }
    }

    private LinearLayout buildNav() {
        LinearLayout nav = Ui.horizontal(this);
        nav.setBackgroundColor(Ui.SURFACE);
        String[] labels = {"Hem", "Aktivitet", "Inställningar"};
        int[] icons = {R.drawable.ic_nav_home, R.drawable.ic_nav_activity, R.drawable.ic_nav_settings};
        for (int i = 0; i < 3; i++) {
            LinearLayout item = Ui.vertical(this);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            FrameLayout pill = new FrameLayout(this);
            pill.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 4));
            pill.addView(Ui.icon(this, icons[i], Ui.TEXT_3, 24),
                    new FrameLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
            item.addView(pill, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView label = Ui.text(this, labels[i], 12, Ui.TEXT_3, Ui.MEDIUM);
            label.setPadding(0, Ui.dp(this, 4), 0, 0);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            item.addView(label, Ui.matchWrap());
            int index = i;
            item.setOnClickListener(v -> selectTab(index));
            item.setBackground(Ui.ripple(null, new ColorDrawable(Color.WHITE)));
            navItems[i] = item;
            nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return nav;
    }

    private void selectTab(int index) {
        tab = index;
        for (int i = 0; i < 3; i++) {
            boolean sel = i == index;
            pages[i].setVisibility(sel ? View.VISIBLE : View.GONE);
            FrameLayout pill = (FrameLayout) navItems[i].getChildAt(0);
            ImageView icon = (ImageView) pill.getChildAt(0);
            TextView label = (TextView) navItems[i].getChildAt(1);
            icon.getDrawable().setTint(sel ? Ui.ACCENT : Ui.TEXT_3);
            label.setTextColor(sel ? Ui.TEXT : Ui.TEXT_3);
            pill.setBackground(sel ? Ui.rounded(this, Ui.withAlpha(Ui.ACCENT, 0x26), 16) : null);
        }
        if (index == TAB_ACTIVITY) refreshLog(true);
    }

    private LinearLayout pageColumn(ScrollView scroll) {
        LinearLayout col = Ui.vertical(this);
        col.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 28));
        scroll.addView(col);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        return col;
    }

    private TextView pageTitle(String s) {
        TextView t = Ui.text(this, s, 30, Ui.TEXT, Ui.MEDIUM);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), 0, Ui.dp(this, 2));
        return t;
    }

    // ---------------------------------------------------------------- home

    private View buildHome() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = pageColumn(scroll);

        LinearLayout header = Ui.horizontal(this);
        header.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), 0, 0);
        TextView brand = Ui.text(this, "", 22, Ui.TEXT, Ui.BOLD);
        SpannableStringBuilder sb = new SpannableStringBuilder("L-AD");
        int start = sb.length();
        sb.append(" Blocker");
        sb.setSpan(new ForegroundColorSpan(Ui.TEXT_2), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new TypefaceSpan("sans-serif-light"), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        brand.setText(sb);
        brand.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(brand);
        statusBadge = Ui.badge(this, "", Ui.ACCENT);
        header.addView(statusBadge);
        col.addView(header);

        power = new PowerButton(this);
        int width = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 40);
        int size = Math.min(width, Ui.dp(this, 320));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(size, size);
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        plp.topMargin = Ui.dp(this, 4);
        power.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggle();
        });
        col.addView(power, plp);

        statusTitle = Ui.text(this, "", 28, Ui.TEXT, Ui.MEDIUM);
        statusTitle.setGravity(Gravity.CENTER);
        col.addView(statusTitle, Ui.matchWrap());
        statusSubtitle = Ui.text(this, "", 15, Ui.TEXT_2, Ui.REGULAR);
        statusSubtitle.setGravity(Gravity.CENTER);
        statusSubtitle.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), 0);
        col.addView(statusSubtitle, Ui.matchWrap());

        LinearLayout stats = Ui.horizontal(this);
        stats.setPadding(0, Ui.dp(this, 28), 0, 0);
        statBlocked = statCard(stats, "Blockerat nu", 0);
        statShare = statCard(stats, "Andel reklam", 10);
        statTotal = statCard(stats, "Totalt", 10);
        col.addView(stats, Ui.matchWrap());

        col.addView(Ui.sectionLabel(this, "Skyddsdatabas"));
        LinearLayout db = Ui.card(this);
        LinearLayout dbRow = Ui.row(this);
        FrameLayout iconCircle = new FrameLayout(this);
        iconCircle.setBackground(Ui.rounded(this, Ui.withAlpha(Ui.ACCENT, 0x22), 14));
        int pad = Ui.dp(this, 10);
        iconCircle.setPadding(pad, pad, pad, pad);
        iconCircle.addView(Ui.icon(this, R.drawable.ic_shield, Ui.ACCENT, 22));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.rightMargin = Ui.dp(this, 14);
        dbRow.addView(iconCircle, ilp);
        LinearLayout dbText = Ui.titleBlock(this, "Blocklistor", "");
        databaseSubtitle = (TextView) dbText.getChildAt(1);
        dbRow.addView(dbText);
        FrameLayout refresh = new FrameLayout(this);
        refresh.setPadding(pad, pad, pad, pad);
        refresh.addView(Ui.icon(this, R.drawable.ic_refresh, Ui.TEXT, 22));
        refresh.setBackground(Ui.ripple(Ui.rounded(this, Ui.SURFACE_2, 22), Ui.rounded(this, Color.WHITE, 22)));
        refresh.setContentDescription("Uppdatera blocklistor");
        refresh.setOnClickListener(v -> {
            Blocklists.updateAsync(this);
            v.animate().rotationBy(360).setDuration(700).start();
        });
        dbRow.addView(refresh);
        db.addView(dbRow);
        databaseStatus = Ui.text(this, "", 13, Ui.ACCENT, Ui.MEDIUM);
        databaseStatus.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), Ui.dp(this, 14));
        databaseStatus.setVisibility(View.GONE);
        db.addView(databaseStatus);
        col.addView(db, Ui.matchWrap());

        col.addView(Ui.sectionLabel(this, "Senast blockerat"));
        LinearLayout recent = Ui.card(this);
        recentList = Ui.vertical(this);
        recent.addView(recentList);
        recent.addView(Ui.divider(this));
        Ui.navRow(recent, "Visa all aktivitet", null, v -> selectTab(TAB_ACTIVITY));
        col.addView(recent, Ui.matchWrap());
        return scroll;
    }

    private TextView statCard(LinearLayout parent, String label, int leftMarginDp) {
        LinearLayout card = Ui.card(this);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16));
        TextView value = Ui.text(this, "0", 22, Ui.TEXT, Ui.BOLD);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(value);
        TextView l = Ui.text(this, label, 12, Ui.TEXT_2, Ui.REGULAR);
        l.setPadding(0, Ui.dp(this, 4), 0, 0);
        l.setSingleLine(true);
        card.addView(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(this, leftMarginDp);
        parent.addView(card, lp);
        return value;
    }

    // ---------------------------------------------------------------- activity

    private View buildActivity() {
        LinearLayout page = Ui.vertical(this);
        page.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), 0);
        page.addView(pageTitle("Aktivitet"));
        TextView sub = Ui.text(this, "Tryck på en domän för att tillåta eller blockera den.", 14, Ui.TEXT_2, Ui.REGULAR);
        sub.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 14));
        page.addView(sub);

        LinearLayout chips = Ui.horizontal(this);
        String[] names = {"Alla", "Blockerade", "Tillåtna"};
        for (int i = 0; i < 3; i++) {
            TextView chip = Ui.text(this, names[i], 14, Ui.TEXT, Ui.MEDIUM);
            chip.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
            int index = i;
            chip.setOnClickListener(v -> {
                logFilter = index;
                refreshLog(true);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 8);
            chips.addView(chip, lp);
            filterChips[i] = chip;
        }
        page.addView(chips);

        FrameLayout listFrame = new FrameLayout(this);
        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(Ui.dp(this, 8));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        list.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 16));
        list.setAdapter(logAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> onLogEntryClicked(logAdapter.getItem(position)));
        listFrame.addView(list);
        activityEmpty = Ui.text(this, "", 15, Ui.TEXT_3, Ui.REGULAR);
        activityEmpty.setGravity(Gravity.CENTER);
        activityEmpty.setPadding(Ui.dp(this, 24), Ui.dp(this, 60), Ui.dp(this, 24), 0);
        listFrame.addView(activityEmpty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(listFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private final class LogAdapter extends BaseAdapter {
        private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss", SV);
        private List<QueryLog.Entry> items = new ArrayList<>();

        void set(List<QueryLog.Entry> entries) {
            items = entries;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public QueryLog.Entry getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = (LinearLayout) convertView;
            if (row == null) {
                row = Ui.horizontal(MainActivity.this);
                row.setPadding(Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 12),
                        Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 12));
                row.setBackground(Ui.ripple(Ui.roundedStroke(MainActivity.this, Ui.SURFACE, Ui.STROKE, 16),
                        Ui.rounded(MainActivity.this, Color.WHITE, 16)));
                View dot = new View(MainActivity.this);
                int d = Ui.dp(MainActivity.this, 8);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(d, d);
                dlp.rightMargin = Ui.dp(MainActivity.this, 14);
                row.addView(dot, dlp);
                LinearLayout text = Ui.titleBlock(MainActivity.this, "", "");
                TextView title = (TextView) text.getChildAt(0);
                title.setTextSize(15);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                row.addView(text);
                TextView badge = Ui.badge(MainActivity.this, "", Ui.DANGER);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.leftMargin = Ui.dp(MainActivity.this, 10);
                row.addView(badge, blp);
                row.setLayoutParams(new ListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            QueryLog.Entry e = items.get(position);
            int color = e.blocked ? Ui.DANGER : Ui.ACCENT;
            row.getChildAt(0).setBackground(Ui.rounded(MainActivity.this, color, 4));
            LinearLayout text = (LinearLayout) row.getChildAt(1);
            ((TextView) text.getChildAt(0)).setText(e.domain);
            ((TextView) text.getChildAt(1)).setText(time.format(new Date(e.time)));
            TextView badge = (TextView) row.getChildAt(2);
            badge.setText(e.blocked ? "BLOCKERAD" : "TILLÅTEN");
            badge.setTextColor(e.blocked ? Ui.DANGER : Ui.TEXT_2);
            badge.setBackground(Ui.rounded(MainActivity.this,
                    Ui.withAlpha(e.blocked ? Ui.DANGER : Ui.TEXT_2, 0x22), 20));
            return row;
        }
    }

    private void refreshLog(boolean force) {
        List<QueryLog.Entry> all = QueryLog.snapshot();
        long version = QueryLog.total.get() * 31 + all.size() + (all.isEmpty() ? 0 : all.get(0).time);
        if (!force && version == logVersion) return;
        logVersion = version;
        List<QueryLog.Entry> shown = new ArrayList<>();
        for (QueryLog.Entry e : all) {
            if (logFilter == 0 || (logFilter == 1) == e.blocked) shown.add(e);
        }
        logAdapter.set(shown);
        for (int i = 0; i < 3; i++) {
            boolean sel = i == logFilter;
            filterChips[i].setTextColor(sel ? Ui.BG : Ui.TEXT_2);
            filterChips[i].setBackground(sel ? Ui.accentGradient(this, 20)
                    : Ui.roundedStroke(this, Ui.SURFACE, Ui.STROKE, 20));
        }
        activityEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        activityEmpty.setText(AdBlockVpnService.isRunning()
                ? "Inga uppslag ännu.\nAnvänd telefonen som vanligt så visas aktiviteten här."
                : "Skyddet är avstängt.\nSlå på det på Hem-fliken.");
    }

    private void onLogEntryClicked(QueryLog.Entry e) {
        if (e.blocked) {
            Ui.dialog(this, "Tillåt domänen?",
                    e.domain + "\n\nGör detta om en app eller webbplats slutar fungera. Domänen och dess underdomäner blockeras inte längre.",
                    null, "Tillåt", () -> addToUserList(Settings.KEY_ALLOWLIST, e.domain), "Avbryt");
        } else {
            Ui.dialog(this, "Blockera domänen?",
                    e.domain + "\n\nDomänen och dess underdomäner kommer att blockeras.",
                    null, "Blockera", () -> addToUserList(Settings.KEY_BLOCKLIST, e.domain), "Avbryt");
        }
    }

    private void addToUserList(String key, String domain) {
        SharedPreferences p = Settings.prefs(this);
        String text = p.getString(key, "");
        if (!text.isEmpty() && !text.endsWith("\n")) text += "\n";
        // Remove it from the opposite list so the choice takes effect.
        String other = key.equals(Settings.KEY_ALLOWLIST) ? Settings.KEY_BLOCKLIST : Settings.KEY_ALLOWLIST;
        StringBuilder kept = new StringBuilder();
        for (String line : p.getString(other, "").split("\n")) {
            if (!domain.equals(DomainFilter.parseLine(line))) kept.append(line).append('\n');
        }
        p.edit().putString(key, text + domain + "\n").putString(other, kept.toString()).apply();
        Blocklists.reloadUserLists(this);
        Toast.makeText(this, key.equals(Settings.KEY_ALLOWLIST) ? "Domänen är tillåten" : "Domänen blockeras",
                Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- settings

    private View buildSettings() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = pageColumn(scroll);
        col.addView(pageTitle("Inställningar"));
        SharedPreferences prefs = Settings.prefs(this);

        col.addView(Ui.sectionLabel(this, "Blocklistor"));
        LinearLayout lists = Ui.card(this);
        for (int i = 0; i < Settings.SOURCES.length; i++) {
            Settings.Source s = Settings.SOURCES[i];
            if (i > 0) lists.addView(Ui.divider(this));
            Switch sw = Ui.switchRow(lists, s.name, s.description, Settings.isSourceEnabled(this, s));
            sw.setOnCheckedChangeListener((b, checked) -> {
                Settings.setSourceEnabled(this, s, checked);
                Blocklists.updateAsync(this);
            });
        }
        col.addView(lists, Ui.matchWrap());

        col.addView(Ui.sectionLabel(this, "DNS-server"));
        LinearLayout dns = Ui.card(this);
        int selected = prefs.getInt(Settings.KEY_UPSTREAM, 0);
        for (int i = 0; i < Settings.UPSTREAM_NAMES.length; i++) {
            if (i > 0) dns.addView(Ui.divider(this));
            String name = Settings.UPSTREAM_NAMES[i];
            String title = name, subtitle = null;
            int paren = name.indexOf(" (");
            if (paren > 0) {
                title = name.substring(0, paren);
                subtitle = name.substring(paren + 2, name.length() - 1);
                subtitle = Character.toUpperCase(subtitle.charAt(0)) + subtitle.substring(1);
            }
            LinearLayout row = Ui.row(this);
            row.addView(Ui.titleBlock(this, title, subtitle));
            ImageView check = Ui.icon(this, R.drawable.ic_check, Ui.ACCENT, 22);
            check.setVisibility(i == selected ? View.VISIBLE : View.INVISIBLE);
            upstreamChecks[i] = check;
            row.addView(check);
            row.setBackground(Ui.ripple(null, new ColorDrawable(Color.WHITE)));
            int index = i;
            row.setOnClickListener(v -> {
                if (prefs.getInt(Settings.KEY_UPSTREAM, 0) == index) return;
                prefs.edit().putInt(Settings.KEY_UPSTREAM, index).apply();
                for (int j = 0; j < upstreamChecks.length; j++) {
                    upstreamChecks[j].setVisibility(j == index ? View.VISIBLE : View.INVISIBLE);
                }
                AdBlockVpnService.restartIfRunning(this);
            });
            dns.addView(row);
        }
        col.addView(dns, Ui.matchWrap());

        col.addView(Ui.sectionLabel(this, "Avancerat"));
        LinearLayout adv = Ui.card(this);
        Switch hard = Ui.switchRow(adv, "Fånga hårdkodad DNS",
                "Stoppar appar som försöker gå förbi blockeringen genom att fråga 8.8.8.8, 1.1.1.1 m.fl. direkt",
                prefs.getBoolean(Settings.KEY_CATCH_HARDCODED, true));
        hard.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(Settings.KEY_CATCH_HARDCODED, checked).apply();
            AdBlockVpnService.restartIfRunning(this);
        });
        adv.addView(Ui.divider(this));
        Ui.navRow(adv, "Tillåtna domäner", "Blockeras aldrig, även om de finns i en lista",
                v -> editList("Tillåtna domäner", Settings.KEY_ALLOWLIST, Settings.DEFAULT_ALLOWLIST));
        adv.addView(Ui.divider(this));
        Ui.navRow(adv, "Egna blockerade domäner", "Blockeras alltid",
                v -> editList("Egna blockerade domäner", Settings.KEY_BLOCKLIST, Settings.DEFAULT_BLOCKLIST));
        adv.addView(Ui.divider(this));
        Ui.navRow(adv, "Alltid på", "Starta skyddet automatiskt via Androids VPN-inställningar", v -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_VPN_SETTINGS));
            } catch (RuntimeException ex) {
                Toast.makeText(this, "Kunde inte öppna VPN-inställningarna", Toast.LENGTH_SHORT).show();
            }
        });
        col.addView(adv, Ui.matchWrap());

        col.addView(Ui.sectionLabel(this, "Hjälp"));
        LinearLayout help = Ui.card(this);
        Ui.navRow(help, "YouTube, webbläsare och spel", "Vad som blockeras och tips för bästa resultat", v -> showHelp());
        help.addView(Ui.divider(this));
        Ui.navRow(help, "Om L-AD Blocker", null, v -> showAbout());
        col.addView(help, Ui.matchWrap());

        TextView footer = Ui.text(this, "L-AD Blocker " + versionName(), 12, Ui.TEXT_3, Ui.REGULAR);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, Ui.dp(this, 28), 0, 0);
        col.addView(footer, Ui.matchWrap());
        return scroll;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private void editList(String title, String key, String defaultText) {
        SharedPreferences p = Settings.prefs(this);
        EditText edit = new EditText(this);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_URI);
        edit.setGravity(Gravity.TOP);
        edit.setMinLines(7);
        edit.setMaxLines(12);
        edit.setTypeface(Typeface.MONOSPACE);
        edit.setTextSize(14);
        edit.setTextColor(Ui.TEXT);
        edit.setHintTextColor(Ui.TEXT_3);
        edit.setHint("exempel.se");
        int pad = Ui.dp(this, 14);
        edit.setPadding(pad, pad, pad, pad);
        edit.setBackground(Ui.roundedStroke(this, Ui.SURFACE_2, Ui.STROKE, 16));
        edit.setText(p.getString(key, defaultText));
        Ui.dialog(this, title, "En domän per rad. Underdomäner räknas med.", edit, "Spara", () -> {
            p.edit().putString(key, edit.getText().toString()).apply();
            Blocklists.reloadUserLists(this);
        }, "Avbryt");
    }

    private void showHelp() {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (String[] s : HELP) {
            if (sb.length() > 0) sb.append("\n\n");
            int start = sb.length();
            sb.append(s[0]);
            sb.setSpan(new ForegroundColorSpan(Ui.TEXT), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new TypefaceSpan("sans-serif-medium"), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new AbsoluteSizeSpan(16, true), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append("\n").append(s[1]);
        }
        TextView body = Ui.text(this, "", 14.5f, Ui.TEXT_2, Ui.REGULAR);
        body.setText(sb);
        body.setLineSpacing(0, 1.2f);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(body);
        sv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.55f)));
        Ui.dialog(this, "Så får du bäst resultat", null, sv, "Klar", null, null);
    }

    private void showAbout() {
        Ui.dialog(this, "Om L-AD Blocker",
                "Version " + versionName() + "\n\n"
                + "L-AD Blocker tar bort reklam och spårning i hela telefonen genom att filtrera "
                + "uppslag mot reklamservrar direkt på enheten.\n\n"
                + "Appen samlar inte in några uppgifter. Uppslag som inte blockeras skickas till "
                + "den DNS-server du valt under Inställningar.\n\n"
                + "Blocklistor: HaGeZi, StevenBlack, AdAway och OISD.",
                null, "Stäng", null, null);
    }

    private static final String[][] HELP = {
        {"Hur det fungerar",
            "Appen stoppar uppslag mot kända reklam- och spårningsservrar i hela telefonen – i appar, "
            + "spel och webbläsare. Den använder en lokal VPN: all trafik stannar på telefonen och bara "
            + "DNS-uppslag går genom appen, så hastigheten påverkas inte."},
        {"YouTube",
            "YouTube-appen hämtar reklamen från samma servrar som själva videorna, så ingen blockerare "
            + "av den här typen kan ta bort den utan att videorna slutar fungera. Det som fungerar:\n"
            + "•  Firefox med tillägget uBlock Origin\n"
            + "•  Reklamfria appar som NewPipe eller ReVanced\n"
            + "•  YouTube Premium"},
        {"Webbläsare",
            "Stäng av \"Använd säker DNS\" i Chrome (Inställningar → Integritet och säkerhet) och sätt "
            + "\"Privat DNS\" i Androids inställningar till Av eller Automatiskt. För helt rena sidor "
            + "utan tomma reklamrutor: Firefox + uBlock Origin."},
        {"Spel och appar",
            "Reklam från bl.a. AdMob, Unity Ads, AppLovin, ironSource, Vungle och Facebook blockeras. "
            + "Belöningar för att titta på reklam i spel slutar då fungera."},
        {"Alltid på",
            "Inställningar → Avancerat → Alltid på. Välj L-AD Blocker och slå på \"Alltid på-VPN\" så "
            + "startar skyddet automatiskt, även efter omstart."},
        {"Om något slutar fungera",
            "Gå till Aktivitet, tryck på den röda domänen och välj Tillåt."},
        {"Andra VPN-appar",
            "Android tillåter bara en VPN åt gången. Startar du en annan VPN stängs blockeringen av."},
    };

    // ---------------------------------------------------------------- state

    private void refresh(boolean animate) {
        boolean on = AdBlockVpnService.isRunning();
        power.setOn(on, animate);
        statusTitle.setText(on ? "Du är skyddad" : "Inte skyddad");
        statusSubtitle.setText(on
                ? "Reklam blockeras i appar, spel och webbläsare"
                : "Tryck på knappen för att börja blockera reklam");
        statusBadge.setText(on ? "●  AKTIV" : "●  AV");
        int badgeColor = on ? Ui.ACCENT : Ui.TEXT_3;
        statusBadge.setTextColor(badgeColor);
        statusBadge.setBackground(Ui.rounded(this, Ui.withAlpha(badgeColor, 0x22), 20));

        SharedPreferences p = Settings.prefs(this);
        long total = QueryLog.total.get();
        long blocked = QueryLog.blocked.get();
        statBlocked.setText(numbers.format(blocked));
        statShare.setText(total == 0 ? "–" : (blocked * 100 / total) + " %");
        statTotal.setText(compact(p.getLong(Settings.KEY_TOTAL_BLOCKED, 0) + blocked));

        long last = p.getLong(Settings.KEY_LAST_UPDATE, 0);
        int size = Blocklists.filter().listSize();
        databaseSubtitle.setText(numbers.format(size) + " domäner · "
                + (last == 0 ? "inte uppdaterad än" : "uppdaterad " + new SimpleDateFormat("d MMM HH:mm", SV).format(new Date(last))));
        String status = Blocklists.status();
        databaseStatus.setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
        databaseStatus.setText(status);

        refreshRecent();
        if (tab == TAB_ACTIVITY) refreshLog(false);
    }

    private String compact(long n) {
        if (n < 100_000) return numbers.format(n);
        if (n < 1_000_000) return (n / 1000) + " k";
        return String.format(SV, "%.1f M", n / 1_000_000.0);
    }

    private void refreshRecent() {
        recentList.removeAllViews();
        int shown = 0;
        for (QueryLog.Entry e : QueryLog.snapshot()) {
            if (!e.blocked) continue;
            if (shown > 0) recentList.addView(Ui.divider(this));
            LinearLayout row = Ui.row(this);
            row.setMinimumHeight(Ui.dp(this, 52));
            row.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
            View dot = new View(this);
            dot.setBackground(Ui.rounded(this, Ui.DANGER, 4));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8));
            dlp.rightMargin = Ui.dp(this, 14);
            row.addView(dot, dlp);
            TextView d = Ui.text(this, e.domain, 15, Ui.TEXT, Ui.REGULAR);
            d.setSingleLine(true);
            d.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            d.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(d);
            row.addView(Ui.text(this, ago(e.time), 12, Ui.TEXT_3, Ui.REGULAR));
            recentList.addView(row);
            if (++shown == 4) break;
        }
        if (shown == 0) {
            TextView empty = Ui.text(this, AdBlockVpnService.isRunning()
                    ? "Inget blockerat ännu – öppna en app eller webbplats."
                    : "Slå på skyddet för att se blockerad reklam här.", 14, Ui.TEXT_3, Ui.REGULAR);
            empty.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18));
            recentList.addView(empty);
        }
    }

    private static String ago(long time) {
        long s = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (s < 60) return "nyss";
        if (s < 3600) return (s / 60) + " min sedan";
        return (s / 3600) + " h sedan";
    }

    private void toggle() {
        if (AdBlockVpnService.isRunning()) {
            power.setOn(false, true);
            AdBlockVpnService.stop(this);
            return;
        }
        Intent consent = VpnService.prepare(this);
        if (consent != null) {
            startActivityForResult(consent, REQUEST_VPN);
        } else {
            power.setOn(true, true);
            AdBlockVpnService.start(this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_VPN) return;
        if (resultCode == RESULT_OK) {
            power.setOn(true, true);
            AdBlockVpnService.start(this);
        } else {
            Ui.dialog(this, "Tillstånd behövs",
                    "L-AD Blocker använder en lokal VPN för att stoppa reklam. Ingen trafik skickas till "
                    + "någon extern server.\n\nOm du har en annan VPN-app aktiv, stäng av den och försök igen.",
                    null, "OK", null, null);
        }
    }
}
