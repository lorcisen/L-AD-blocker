package se.lorcisen.adblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_VPN = 1;
    private static final int REQUEST_NOTIFICATIONS = 2;
    private static final int GREEN = 0xFF2E7D32;
    private static final int RED = 0xFFC62828;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    private TextView statusText;
    private Button toggleButton;
    private TextView statsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        if (Blocklists.filter().listSize() == 0) Blocklists.loadAsync(this, false);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, REQUEST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        statusText = new TextView(this);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);

        toggleButton = new Button(this);
        toggleButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        toggleButton.setOnClickListener(v -> toggle());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        lp.topMargin = dp(12);
        root.addView(toggleButton, lp);

        statsText = new TextView(this);
        statsText.setPadding(0, dp(12), 0, dp(4));
        statsText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(statsText);

        root.addView(button("Visa senaste uppslag", v -> showLog()));
        root.addView(button("Hjälp: YouTube, webbläsare och spel", v -> showHelp()));

        root.addView(header("Blocklistor"));
        for (Settings.Source s : Settings.SOURCES) {
            CheckBox cb = new CheckBox(this);
            cb.setText(s.name);
            cb.setChecked(Settings.isSourceEnabled(this, s));
            cb.setOnCheckedChangeListener((b, checked) -> {
                Settings.setSourceEnabled(this, s, checked);
                Blocklists.updateAsync(this);
            });
            root.addView(cb);
            TextView desc = small(s.description);
            desc.setPadding(dp(32), 0, 0, dp(6));
            root.addView(desc);
        }
        root.addView(button("Uppdatera blocklistor nu", v -> {
            Blocklists.updateAsync(this);
            Toast.makeText(this, "Uppdaterar…", Toast.LENGTH_SHORT).show();
        }));

        root.addView(header("Egna listor"));
        root.addView(button("Tillåtna domäner (blockeras aldrig)",
                v -> editList("Tillåtna domäner", Settings.KEY_ALLOWLIST, Settings.DEFAULT_ALLOWLIST)));
        root.addView(button("Egna blockerade domäner",
                v -> editList("Egna blockerade domäner", Settings.KEY_BLOCKLIST, Settings.DEFAULT_BLOCKLIST)));

        root.addView(header("DNS"));
        root.addView(small("Server som används för domäner som inte blockeras:"));
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, Settings.UPSTREAM_NAMES));
        SharedPreferences prefs = Settings.prefs(this);
        spinner.setSelection(prefs.getInt(Settings.KEY_UPSTREAM, 0));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (prefs.getInt(Settings.KEY_UPSTREAM, 0) == position) return;
                prefs.edit().putInt(Settings.KEY_UPSTREAM, position).apply();
                AdBlockVpnService.restartIfRunning(MainActivity.this);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(spinner);

        CheckBox hardcoded = new CheckBox(this);
        hardcoded.setText("Fånga appar som går förbi telefonens DNS (t.ex. 8.8.8.8)");
        hardcoded.setChecked(prefs.getBoolean(Settings.KEY_CATCH_HARDCODED, true));
        hardcoded.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(Settings.KEY_CATCH_HARDCODED, checked).apply();
            AdBlockVpnService.restartIfRunning(this);
        });
        root.addView(hardcoded);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private void refresh() {
        boolean on = AdBlockVpnService.isRunning();
        statusText.setText(on ? "Skyddet är PÅ" : "Skyddet är AV");
        statusText.setTextColor(on ? GREEN : RED);
        toggleButton.setText(on ? "Stäng av" : "Starta reklamblockering");

        SharedPreferences p = Settings.prefs(this);
        long total = QueryLog.total.get();
        long blocked = QueryLog.blocked.get();
        long allTime = p.getLong(Settings.KEY_TOTAL_BLOCKED, 0) + blocked;
        long last = p.getLong(Settings.KEY_LAST_UPDATE, 0);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Blockerade nu: %d av %d uppslag", blocked, total));
        if (total > 0) sb.append(String.format(Locale.ROOT, " (%d %%)", blocked * 100 / total));
        sb.append("\nBlockerade totalt: ").append(allTime);
        sb.append("\nDomäner i blocklistorna: ").append(Blocklists.filter().listSize());
        sb.append("\nListor uppdaterade: ").append(last == 0 ? "aldrig"
                : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(last)));
        String status = Blocklists.status();
        if (!status.isEmpty()) sb.append("\n").append(status);
        statsText.setText(sb);
    }

    private void toggle() {
        if (AdBlockVpnService.isRunning()) {
            AdBlockVpnService.stop(this);
            return;
        }
        Intent consent = VpnService.prepare(this);
        if (consent != null) {
            startActivityForResult(consent, REQUEST_VPN);
        } else {
            AdBlockVpnService.start(this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) {
                AdBlockVpnService.start(this);
            } else {
                Toast.makeText(this, "VPN-tillstånd krävs för att blockera reklam. "
                        + "Stäng av andra VPN-appar och försök igen.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showLog() {
        List<QueryLog.Entry> entries = QueryLog.snapshot();
        if (entries.isEmpty()) {
            Toast.makeText(this, AdBlockVpnService.isRunning()
                    ? "Inga uppslag ännu" : "Starta skyddet först", Toast.LENGTH_SHORT).show();
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
        ArrayAdapter<QueryLog.Entry> adapter = new ArrayAdapter<QueryLog.Entry>(
                this, android.R.layout.simple_list_item_1, entries) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                QueryLog.Entry e = getItem(position);
                tv.setText((e.blocked ? "⛔ " : "✓ ") + fmt.format(new Date(e.time)) + "  " + e.domain);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tv.setTextColor(e.blocked ? RED : statsText.getCurrentTextColor());
                return tv;
            }
        };
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Senaste uppslag (tryck för att ändra)")
                .setView(list)
                .setPositiveButton("Stäng", null)
                .create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            QueryLog.Entry e = entries.get(position);
            if (e.blocked) {
                confirmAdd("Tillåt " + e.domain + "?",
                        "Gör det om en app eller sida slutar fungera.", Settings.KEY_ALLOWLIST, e.domain);
            } else {
                confirmAdd("Blockera " + e.domain + "?", null, Settings.KEY_BLOCKLIST, e.domain);
            }
        });
        dialog.show();
    }

    private void confirmAdd(String title, String message, String key, String domain) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Ja", (d, w) -> {
                    SharedPreferences p = Settings.prefs(this);
                    String text = p.getString(key, "");
                    if (!text.isEmpty() && !text.endsWith("\n")) text += "\n";
                    p.edit().putString(key, text + domain + "\n").apply();
                    // Remove it from the opposite list so the choice takes effect.
                    String other = key.equals(Settings.KEY_ALLOWLIST) ? Settings.KEY_BLOCKLIST : Settings.KEY_ALLOWLIST;
                    String otherText = p.getString(other, "");
                    StringBuilder kept = new StringBuilder();
                    for (String line : otherText.split("\n")) {
                        if (!domain.equals(DomainFilter.parseLine(line))) kept.append(line).append('\n');
                    }
                    p.edit().putString(other, kept.toString()).apply();
                    Blocklists.reloadUserLists(this);
                    Toast.makeText(this, "Sparat", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Avbryt", null)
                .show();
    }

    private void editList(String title, String key, String defaultText) {
        SharedPreferences p = Settings.prefs(this);
        EditText edit = new EditText(this);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setGravity(Gravity.TOP);
        edit.setMinLines(8);
        edit.setTypeface(Typeface.MONOSPACE);
        edit.setText(p.getString(key, defaultText));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("En domän per rad. Underdomäner räknas med.")
                .setView(edit)
                .setPositiveButton("Spara", (d, w) -> {
                    p.edit().putString(key, edit.getText().toString()).apply();
                    Blocklists.reloadUserLists(this);
                })
                .setNegativeButton("Avbryt", null)
                .show();
    }

    private void showHelp() {
        TextView tv = new TextView(this);
        int pad = dp(20);
        tv.setPadding(pad, dp(8), pad, 0);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setText(HELP);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("Hjälp")
                .setView(sv)
                .setPositiveButton("OK", null)
                .show();
    }

    private static final String HELP =
            "Hur det fungerar\n"
            + "Appen stoppar uppslag mot kända reklam- och spårningsservrar i hela telefonen – "
            + "i appar, spel och webbläsare. Den använder en lokal VPN, men all trafik stannar på "
            + "telefonen och bara DNS-uppslag går genom appen, så surfhastigheten påverkas inte.\n\n"
            + "YouTube\n"
            + "YouTube-appen hämtar reklamen från samma servrar som själva videorna. Därför kan "
            + "ingen blockerare som jobbar på det här sättet (inte heller Blokada, AdGuard DNS m.fl.) "
            + "ta bort YouTube-reklam utan att videorna slutar fungera. Det som fungerar:\n"
            + "• Firefox med tillägget uBlock Origin och youtube.com i webbläsaren\n"
            + "• Reklamfria YouTube-appar som NewPipe (F-Droid) eller ReVanced\n"
            + "• YouTube Premium\n\n"
            + "Webbläsare\n"
            + "• Stäng av \"Använd säker DNS\" i Chrome (Inställningar → Integritet och säkerhet).\n"
            + "• Sätt \"Privat DNS\" i Androids inställningar till Av eller Automatiskt.\n"
            + "Annars kan webbläsaren gå förbi blockeringen. För helt rena sidor (utan tomma "
            + "reklamrutor): Firefox + uBlock Origin.\n\n"
            + "Spel och appar\n"
            + "Reklam från bl.a. AdMob, Unity Ads, AppLovin, ironSource, Vungle och Facebook blockeras. "
            + "Vissa spel ger belöningar för att titta på reklam – de belöningarna slutar då fungera.\n\n"
            + "Alltid på\n"
            + "Inställningar → Nätverk och internet → VPN → L-AD-blocker → \"Alltid på-VPN\". "
            + "Då startar skyddet automatiskt, även efter omstart.\n\n"
            + "Om något slutar fungera\n"
            + "Öppna \"Visa senaste uppslag\", tryck på den röda domänen och välj Tillåt.\n\n"
            + "Andra VPN-appar\n"
            + "Android tillåter bara en VPN åt gången. Startar du en annan VPN stängs blockeringen av.";

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private TextView header(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(20), 0, dp(4));
        return tv;
    }

    private TextView small(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setAlpha(0.75f);
        return tv;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
