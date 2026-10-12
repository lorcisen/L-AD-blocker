package se.lorcisen.adblocker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A local VPN that only captures DNS traffic. Lookups of advertising domains are answered with
 * 0.0.0.0 on the phone itself; everything else is forwarded to a real DNS server. No other
 * traffic passes through the app, so speed and battery use are unaffected.
 */
public class AdBlockVpnService extends VpnService {
    private static final String TAG = "AdBlockVpn";
    static final String ACTION_START = "se.lorcisen.adblocker.START";
    static final String ACTION_STOP = "se.lorcisen.adblocker.STOP";
    static final String ACTION_RESTART = "se.lorcisen.adblocker.RESTART";
    private static final String CHANNEL = "vpn";
    private static final int NOTIFICATION_ID = 1;

    private static final String TUN_ADDRESS = "10.111.222.1";
    private static final String DNS_ADDRESS = "10.111.222.2";

    private static volatile boolean running;

    private ParcelFileDescriptor tun;
    private Thread loop;
    private ExecutorService forwarders;
    private volatile boolean stopRequested;

    static boolean isRunning() {
        return running;
    }

    static void start(Context c) {
        Intent i = new Intent(c, AdBlockVpnService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    static void stop(Context c) {
        c.startService(new Intent(c, AdBlockVpnService.class).setAction(ACTION_STOP));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Settings.prefs(this).edit().putBoolean(Settings.KEY_WANT_RUNNING, false).apply();
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_RESTART.equals(intent.getAction())) {
            if (!running) return START_NOT_STICKY;
            shutdown();
        }
        // ACTION_START from the app, or android.net.VpnService when started as "always-on VPN".
        goForeground();
        Settings.prefs(this).edit().putBoolean(Settings.KEY_WANT_RUNNING, true).apply();
        if (!running) {
            try {
                establish();
            } catch (Exception e) {
                Log.e(TAG, "start failed", e);
                shutdown();
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        return START_STICKY;
    }

    private void establish() throws IOException {
        boolean catchHardcoded = Settings.prefs(this).getBoolean(Settings.KEY_CATCH_HARDCODED, true);
        Builder b = new Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress(TUN_ADDRESS, 24)
                .addDnsServer(DNS_ADDRESS)
                .addRoute(DNS_ADDRESS, 32)
                .setBlocking(false)
                .setConfigureIntent(mainActivityIntent());
        if (catchHardcoded) {
            for (String ip : Settings.HARDCODED_DNS) b.addRoute(ip, 32);
        }
        // Only IPv4 routes are added; without this, all IPv6 traffic would be blocked.
        b.allowFamily(OsConstants.AF_INET6);
        if (Build.VERSION.SDK_INT >= 29) b.setMetered(false);

        tun = b.establish();
        if (tun == null) throw new IOException("VPN not prepared");

        Blocklists.loadAsync(this, true);
        forwarders = new ThreadPoolExecutor(4, 32, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(512), new ThreadPoolExecutor.DiscardPolicy());
        stopRequested = false;
        running = true;
        FileDescriptor fd = tun.getFileDescriptor();
        String[] upstream = Settings.upstreamServers(this);
        loop = new Thread(() -> runLoop(fd, upstream), "dns-loop");
        loop.start();
    }

    private void runLoop(FileDescriptor fd, String[] upstream) {
        byte[] buf = new byte[32767];
        StructPollfd pfd = new StructPollfd();
        pfd.fd = fd;
        pfd.events = (short) OsConstants.POLLIN;
        StructPollfd[] pfds = {pfd};
        while (!stopRequested) {
            try {
                pfd.revents = 0;
                if (Os.poll(pfds, 500) <= 0) continue;
                if ((pfd.revents & (OsConstants.POLLERR | OsConstants.POLLHUP | OsConstants.POLLNVAL)) != 0) break;
                int n = Os.read(fd, buf, 0, buf.length);
                if (n > 0) handlePacket(fd, Arrays.copyOf(buf, n), upstream);
            } catch (ErrnoException e) {
                if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) continue;
                Log.w(TAG, "tun read", e);
                break;
            } catch (Exception e) {
                Log.w(TAG, "packet", e);
            }
        }
    }

    private void handlePacket(FileDescriptor fd, byte[] pkt, String[] upstream) {
        IpPacket.Udp udp = IpPacket.parseUdp(pkt, pkt.length);
        if (udp == null || udp.dstPort != 53) return; // drop anything that isn't DNS
        DnsPacket.Question q = DnsPacket.parseQuestion(pkt, udp.payloadOffset, udp.payloadLength);
        if (q == null) return;

        boolean blocked = Blocklists.filter().isBlocked(q.name);
        QueryLog.add(q.name, blocked);
        if (blocked) {
            byte[] answer = DnsPacket.buildBlockedResponse(pkt, udp.payloadOffset, q);
            writeTun(fd, IpPacket.buildUdpReply(udp, answer, answer.length));
            return;
        }
        // Hard-coded public DNS servers are answered by that same server; our own DNS by the upstream.
        String[] servers = Arrays.equals(udp.dstAddr, ipv4(DNS_ADDRESS))
                ? upstream
                : new String[] {ipToString(udp.dstAddr)};
        forwarders.execute(() -> forward(fd, pkt, udp, servers));
    }

    private void forward(FileDescriptor fd, byte[] pkt, IpPacket.Udp udp, String[] servers) {
        for (String server : servers) {
            try (DatagramSocket socket = new DatagramSocket()) {
                protect(socket); // keep our own upstream traffic outside the VPN
                socket.setSoTimeout(4000);
                InetAddress addr = InetAddress.getByName(server);
                socket.send(new DatagramPacket(pkt, udp.payloadOffset, udp.payloadLength, addr, 53));
                byte[] resp = new byte[4096];
                DatagramPacket in = new DatagramPacket(resp, resp.length);
                socket.receive(in);
                writeTun(fd, IpPacket.buildUdpReply(udp, resp, in.getLength()));
                return;
            } catch (IOException e) {
                Log.d(TAG, "upstream " + server + " failed: " + e);
            }
            if (stopRequested) return;
        }
    }

    private synchronized void writeTun(FileDescriptor fd, byte[] packet) {
        if (stopRequested) return;
        try {
            Os.write(fd, packet, 0, packet.length);
        } catch (ErrnoException | java.io.InterruptedIOException e) {
            Log.w(TAG, "tun write", e);
        }
    }

    private void shutdown() {
        stopRequested = true;
        running = false;
        if (loop != null) {
            try {
                loop.join(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            loop = null;
        }
        if (forwarders != null) {
            forwarders.shutdownNow();
            forwarders = null;
        }
        synchronized (this) {
            if (tun != null) {
                try {
                    tun.close();
                } catch (IOException ignored) {
                }
                tun = null;
            }
        }
        long total = Settings.prefs(this).getLong(Settings.KEY_TOTAL_BLOCKED, 0);
        Settings.prefs(this).edit()
                .putLong(Settings.KEY_TOTAL_BLOCKED, total + QueryLog.blocked.getAndSet(0))
                .apply();
        QueryLog.total.set(0);
        stopForeground(true);
    }

    /** Restarts the tunnel so changed DNS settings take effect. */
    static void restartIfRunning(Context c) {
        if (!running) return;
        c.startService(new Intent(c, AdBlockVpnService.class).setAction(ACTION_RESTART));
    }

    @Override
    public void onRevoke() {
        // Another VPN was started or the user turned us off in system settings.
        Settings.prefs(this).edit().putBoolean(Settings.KEY_WANT_RUNNING, false).apply();
        shutdown();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "Reklamblockering", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, AdBlockVpnService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        Notification n = nb
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle("Reklam blockeras")
                .setContentText("Tryck för statistik och inställningar")
                .setContentIntent(mainActivityIntent())
                .setOngoing(true)
                .setColor(Ui.ACCENT)
                .addAction(new Notification.Action.Builder(null, "Stäng av", stop).build())
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (RuntimeException e) {
            // E.g. started in the background as always-on VPN; the VPN itself still works.
            Log.w(TAG, "startForeground", e);
        }
    }

    private PendingIntent mainActivityIntent() {
        return PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static byte[] ipv4(String s) {
        String[] p = s.split("\\.");
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) b[i] = (byte) Integer.parseInt(p[i]);
        return b;
    }

    private static String ipToString(byte[] a) {
        return (a[0] & 0xFF) + "." + (a[1] & 0xFF) + "." + (a[2] & 0xFF) + "." + (a[3] & 0xFF);
    }
}
