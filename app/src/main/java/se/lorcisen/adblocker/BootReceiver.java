package se.lorcisen.adblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.util.Log;

/** Turns the blocker back on after a reboot or an app update if it was on before. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Settings.prefs(context).getBoolean(Settings.KEY_WANT_RUNNING, false)) return;
        if (VpnService.prepare(context) != null) return; // permission was revoked
        try {
            AdBlockVpnService.start(context);
        } catch (RuntimeException e) {
            Log.w("BootReceiver", "could not start", e);
        }
    }
}
