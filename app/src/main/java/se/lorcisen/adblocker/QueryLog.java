package se.lorcisen.adblocker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory statistics and the most recent DNS lookups, shown in the app. */
final class QueryLog {
    private static final int MAX = 300;

    static final class Entry {
        final long time;
        final String domain;
        final boolean blocked;

        Entry(long time, String domain, boolean blocked) {
            this.time = time;
            this.domain = domain;
            this.blocked = blocked;
        }
    }

    static final AtomicLong total = new AtomicLong();
    static final AtomicLong blocked = new AtomicLong();
    private static final ArrayDeque<Entry> entries = new ArrayDeque<>();

    private QueryLog() {}

    static void add(String domain, boolean isBlocked) {
        total.incrementAndGet();
        if (isBlocked) blocked.incrementAndGet();
        synchronized (entries) {
            Entry last = entries.peekFirst();
            // Collapse the A + AAAA pair (and quick retries) of the same lookup into one row.
            if (last != null && last.domain.equals(domain) && last.blocked == isBlocked) return;
            entries.addFirst(new Entry(System.currentTimeMillis(), domain, isBlocked));
            if (entries.size() > MAX) entries.removeLast();
        }
    }

    /** Newest first. */
    static List<Entry> snapshot() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }
}
