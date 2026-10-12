package se.lorcisen.adblocker;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Immutable set of blocked domains plus the user's allow and block lists.
 *
 * <p>The large downloaded lists are stored as a sorted array of 64-bit hashes, which takes a
 * few MB for hundreds of thousands of domains instead of the tens of MB a HashSet of strings
 * would need.
 */
final class DomainFilter {
    static final DomainFilter EMPTY =
            new DomainFilter(new long[0], Collections.emptySet(), Collections.emptySet());

    private final long[] hashes;
    private final Set<String> allow;
    private final Set<String> block;

    DomainFilter(long[] sortedHashes, Set<String> allow, Set<String> block) {
        this.hashes = sortedHashes;
        this.allow = allow;
        this.block = block;
    }

    int listSize() {
        return hashes.length;
    }

    DomainFilter withUserLists(Set<String> allow, Set<String> block) {
        return new DomainFilter(hashes, allow, block);
    }

    /** True if {@code domain} or any of its parent domains is blocked and not allowed. */
    boolean isBlocked(String domain) {
        if (domain.isEmpty()) return false;
        if (matches(domain, allow, null)) return false;
        return matches(domain, block, hashes);
    }

    private static boolean matches(String domain, Set<String> set, long[] hashes) {
        String d = domain;
        while (true) {
            if (set.contains(d)) return true;
            if (hashes != null && Arrays.binarySearch(hashes, hash(d)) >= 0) return true;
            int dot = d.indexOf('.');
            if (dot < 0) return false;
            d = d.substring(dot + 1);
        }
    }

    /** 64-bit FNV-1a. */
    static long hash(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    /** Collects hashes from hosts files, plain domain lists and simple "||domain^" rules. */
    static final class Builder {
        private long[] data = new long[1 << 16];
        private int size;

        void addAll(BufferedReader reader) throws IOException {
            String line;
            while ((line = reader.readLine()) != null) {
                String d = parseLine(line);
                if (d != null) add(d);
            }
        }

        void add(String domain) {
            if (size == data.length) data = Arrays.copyOf(data, size * 2);
            data[size++] = hash(domain);
        }

        DomainFilter build(Set<String> allow, Set<String> block) {
            long[] a = Arrays.copyOf(data, size);
            Arrays.sort(a);
            int n = 0;
            for (int i = 0; i < a.length; i++) {
                if (i == 0 || a[i] != a[i - 1]) a[n++] = a[i];
            }
            return new DomainFilter(Arrays.copyOf(a, n), allow, block);
        }
    }

    /** Extracts the domain from one line of a blocklist, or returns null. */
    static String parseLine(String raw) {
        String line = raw.trim();
        if (line.isEmpty()) return null;
        char c = line.charAt(0);
        if (c == '#' || c == '!' || c == '[' || line.startsWith("@@")) return null;
        int hash = line.indexOf('#');
        if (hash >= 0) {
            // "domain # comment" is a comment; "domain##.banner" is a cosmetic adblock rule.
            if (!Character.isWhitespace(line.charAt(hash - 1))) return null;
            line = line.substring(0, hash).trim();
        }

        if (line.startsWith("||")) {
            int end = line.indexOf('^');
            if (end < 0) return null;
            String rest = line.substring(end + 1);
            // Only plain domain rules; anything with path or extra options except $important is skipped.
            if (!rest.isEmpty() && !rest.equals("$important")) return null;
            return normalize(line.substring(2, end));
        }

        String[] parts = line.split("\\s+");
        String candidate;
        if (parts.length >= 2 && looksLikeIp(parts[0])) {
            candidate = parts[1];
        } else if (parts.length == 1) {
            candidate = parts[0];
        } else {
            return null;
        }
        return normalize(candidate);
    }

    /** Lower-cases and validates a domain; returns null for non-domains such as "localhost". */
    static String normalize(String domain) {
        String d = domain.trim().toLowerCase(Locale.ROOT);
        if (d.startsWith("*.")) d = d.substring(2);
        while (d.endsWith(".")) d = d.substring(0, d.length() - 1);
        if (d.isEmpty() || d.length() > 253 || d.indexOf('.') < 0) return null;
        if (d.startsWith(".") || d.startsWith("-") || d.contains("..")) return null;
        for (int i = 0; i < d.length(); i++) {
            char ch = d.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                    || ch == '.' || ch == '-' || ch == '_';
            if (!ok) return null;
        }
        if (d.equals("localhost.localdomain") || d.endsWith(".local") || looksLikeIp(d)) return null;
        return d;
    }

    private static boolean looksLikeIp(String s) {
        if (s.indexOf(':') >= 0) return true; // IPv6
        int dots = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '.') dots++;
            else if (ch < '0' || ch > '9') return false;
        }
        return dots == 3;
    }

    /** Parses a user-edited list (one domain per line) into a set. */
    static Set<String> parseUserList(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            String d = parseLine(line);
            if (d != null) out.add(d);
        }
        return out;
    }
}
