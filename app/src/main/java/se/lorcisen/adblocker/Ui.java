package se.lorcisen.adblocker;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Design tokens and small view factories shared by the app's screens. */
final class Ui {
    private Ui() {}

    static final int BG = 0xFF0A0E16;
    static final int SURFACE = 0xFF121925;
    static final int SURFACE_2 = 0xFF1A2232;
    static final int STROKE = 0xFF232D40;
    static final int TEXT = 0xFFF1F5F9;
    static final int TEXT_2 = 0xFF94A3B8;
    static final int TEXT_3 = 0xFF64748B;
    static final int ACCENT = 0xFF2DD4BF;
    static final int ACCENT_2 = 0xFF22C55E;
    static final int ACCENT_3 = 0xFF38BDF8;
    static final int DANGER = 0xFFF43F5E;
    static final int WARNING = 0xFFF59E0B;

    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    static int blend(int from, int to, float t) {
        float u = 1 - t;
        return Color.argb(
                (int) (Color.alpha(from) * u + Color.alpha(to) * t),
                (int) (Color.red(from) * u + Color.red(to) * t),
                (int) (Color.green(from) * u + Color.green(to) * t),
                (int) (Color.blue(from) * u + Color.blue(to) * t));
    }

    static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static GradientDrawable roundedStroke(Context c, int color, int stroke, float radiusDp) {
        GradientDrawable d = rounded(c, color, radiusDp);
        d.setStroke(dp(c, 1), stroke);
        return d;
    }

    static GradientDrawable accentGradient(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {ACCENT, ACCENT_2});
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** Adds a touch ripple on top of {@code content}, clipped to {@code mask}. */
    static Drawable ripple(Drawable content, Drawable mask) {
        return new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), content, mask);
    }

    static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setTypeface(tf);
        tv.setIncludeFontPadding(true);
        return tv;
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams margins(Context c, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return lp;
    }

    static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** A rounded surface card. */
    static LinearLayout card(Context c) {
        LinearLayout l = vertical(c);
        l.setBackground(roundedStroke(c, SURFACE, STROKE, 22));
        return l;
    }

    /** Small upper-case section label above a card. */
    static TextView sectionLabel(Context c, String s) {
        TextView tv = text(c, s.toUpperCase(java.util.Locale.ROOT), 12, TEXT_3, MEDIUM);
        tv.setLetterSpacing(0.12f);
        tv.setPadding(dp(c, 6), dp(c, 26), 0, dp(c, 10));
        return tv;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(STROKE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f)));
        lp.setMargins(dp(c, 18), 0, dp(c, 18), 0);
        v.setLayoutParams(lp);
        return v;
    }

    static ImageView icon(Context c, int res, int color, int sizeDp) {
        ImageView iv = new ImageView(c);
        Drawable d = c.getDrawable(res).mutate();
        d.setTint(color);
        iv.setImageDrawable(d);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    /** Title + optional subtitle block that fills the available width in a row. */
    static LinearLayout titleBlock(Context c, String title, String subtitle) {
        LinearLayout col = vertical(c);
        col.addView(text(c, title, 16, TEXT, MEDIUM));
        if (subtitle != null) {
            TextView sub = text(c, subtitle, 13, TEXT_2, REGULAR);
            sub.setPadding(0, dp(c, 2), 0, 0);
            sub.setLineSpacing(0, 1.1f);
            col.addView(sub);
        }
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return col;
    }

    static LinearLayout row(Context c) {
        LinearLayout row = horizontal(c);
        row.setPadding(dp(c, 18), dp(c, 15), dp(c, 18), dp(c, 15));
        row.setMinimumHeight(dp(c, 60));
        return row;
    }

    /** A settings row with a switch; tapping anywhere on the row toggles it. */
    static Switch switchRow(LinearLayout parent, String title, String subtitle, boolean checked) {
        Context c = parent.getContext();
        LinearLayout row = row(c);
        row.addView(titleBlock(c, title, subtitle));
        Switch sw = new Switch(c);
        sw.setChecked(checked);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[] {Color.WHITE, 0xFFCBD5E1}));
        sw.setTrackTintList(new ColorStateList(states, new int[] {ACCENT, 0xFF334155}));
        sw.setPadding(dp(c, 12), 0, 0, 0);
        row.addView(sw);
        row.setBackground(ripple(null, new ColorDrawable(Color.WHITE)));
        row.setOnClickListener(v -> sw.toggle());
        parent.addView(row);
        return sw;
    }

    /** A tappable row with a chevron. */
    static LinearLayout navRow(LinearLayout parent, String title, String subtitle, View.OnClickListener l) {
        Context c = parent.getContext();
        LinearLayout row = row(c);
        row.addView(titleBlock(c, title, subtitle));
        row.addView(icon(c, R.drawable.ic_chevron, TEXT_3, 22));
        row.setBackground(ripple(null, new ColorDrawable(Color.WHITE)));
        row.setOnClickListener(l);
        parent.addView(row);
        return row;
    }

    /** Primary call-to-action: gradient pill with dark text. */
    static TextView primaryButton(Context c, String label, View.OnClickListener l) {
        TextView b = text(c, label, 15, BG, MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 22), dp(c, 12), dp(c, 22), dp(c, 12));
        b.setBackground(ripple(accentGradient(c, 28), rounded(c, Color.WHITE, 28)));
        b.setOnClickListener(l);
        return b;
    }

    /** Secondary button: transparent with a hairline border. */
    static TextView secondaryButton(Context c, String label, View.OnClickListener l) {
        TextView b = text(c, label, 15, TEXT, MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12));
        b.setBackground(ripple(roundedStroke(c, 0x00000000, 0xFF334155, 28), rounded(c, Color.WHITE, 28)));
        b.setOnClickListener(l);
        return b;
    }

    /** Small pill used for status labels. */
    static TextView badge(Context c, String label, int color) {
        TextView b = text(c, label, 11, color, MEDIUM);
        b.setLetterSpacing(0.08f);
        b.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        b.setBackground(rounded(c, withAlpha(color, 0x26), 20));
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        return b;
    }

    /**
     * A styled modal card. {@code content} may be null. Returns the dialog (already shown).
     */
    static Dialog dialog(Context c, String title, CharSequence message, View content,
                         String positive, Runnable onPositive, String negative) {
        Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = vertical(c);
        box.setBackground(roundedStroke(c, SURFACE, STROKE, 28));
        box.setPadding(dp(c, 24), dp(c, 24), dp(c, 24), dp(c, 18));

        TextView t = text(c, title, 20, TEXT, MEDIUM);
        box.addView(t);
        if (message != null) {
            TextView m = text(c, "", 14.5f, TEXT_2, REGULAR);
            m.setText(message);
            m.setLineSpacing(0, 1.2f);
            m.setPadding(0, dp(c, 10), 0, 0);
            box.addView(m);
        }
        if (content != null) {
            LinearLayout.LayoutParams lp = matchWrap();
            lp.topMargin = dp(c, 16);
            box.addView(content, lp);
        }

        LinearLayout buttons = horizontal(c);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        buttons.setPadding(0, dp(c, 22), 0, 0);
        if (negative != null) {
            TextView n = text(c, negative, 15, TEXT_2, MEDIUM);
            n.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
            n.setBackground(ripple(null, rounded(c, Color.WHITE, 24)));
            n.setOnClickListener(v -> d.dismiss());
            buttons.addView(n);
        }
        if (positive != null) {
            TextView p = primaryButton(c, positive, v -> {
                d.dismiss();
                if (onPositive != null) onPositive.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(c, 8);
            buttons.addView(p, lp);
        }
        box.addView(buttons);

        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams a = w.getAttributes();
            a.dimAmount = 0.7f;
            w.setAttributes(a);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.getDecorView().setPadding(dp(c, 20), 0, dp(c, 20), 0);
        }
        d.show();
        return d;
    }
}
