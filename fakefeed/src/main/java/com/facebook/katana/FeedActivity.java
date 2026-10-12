package com.facebook.katana;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** A feed laid out like Facebook's: each post is split into header, text, media and footer rows. */
public class FeedActivity extends Activity {
    private final List<String[]> rows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String[][] posts = {
            {"Anna Svensson", "3 tim", "Vilken fin dag vid sjön!", "#8BC34A"},
            {"NIBE", "Sponsras•Delas med: Offentligt", "Bra uppvärmning gör livet enklare", "#C62828"},
            {"Erik Larsson", "5 tim", "Ny bil idag", "#03A9F4"},
            {"Stadium Outlet", "Sponsrad", "Sport och fritid till outletpris", "#FFEB3B"},
            {"Lisa Berg", "1 d", "Middag med vänner", "#9C27B0"},
            {"Mamma", "2 d", "Grattis på födelsedagen!", "#FF9800"},
        };
        for (String[] p : posts) {
            rows.add(new String[] {"header", p[0], p[1]});
            rows.add(new String[] {"text", p[2]});
            rows.add(new String[] {"media", p[3], p[0]});
            rows.add(new String[] {"footer"});
        }
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int i) { return rows.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View convert, ViewGroup parent) { return row(rows.get(i)); }
        });
        setContentView(list);
    }

    private View row(String[] r) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(32, 16, 32, 16);
        l.setBackgroundColor(Color.WHITE);
        switch (r[0]) {
            case "header":
                l.addView(text(r[1], 18, Color.BLACK));
                l.addView(text(r[2], 14, Color.GRAY));
                break;
            case "text":
                l.addView(text(r[1], 16, Color.DKGRAY));
                break;
            case "media":
                TextView m = text(r[2], 24, Color.BLACK);
                m.setGravity(Gravity.CENTER);
                m.setBackgroundColor(Color.parseColor(r[1]));
                m.setHeight(600);
                l.addView(m);
                break;
            default:
                LinearLayout bar = new LinearLayout(this);
                for (String s : new String[] {"Gilla", "Kommentera", "Dela"}) {
                    TextView t = text(s, 15, Color.GRAY);
                    t.setPadding(0, 8, 64, 24);
                    t.setClickable(true);
                    if (s.equals("Gilla")) {
                        t.setContentDescription("Knappen Gilla. Dubbeltryck och håll ned för att reagera.");
                    }
                    bar.addView(t);
                }
                l.addView(bar);
        }
        return l;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }
}
