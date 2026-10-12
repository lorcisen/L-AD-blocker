package se.lorcisen.adblocker;

import java.nio.charset.StandardCharsets;

/** Minimal DNS message helpers: read the question and build "blocked" answers. */
final class DnsPacket {
    static final int TYPE_A = 1;
    static final int TYPE_AAAA = 28;

    private DnsPacket() {}

    /** The first question of a DNS query. */
    static final class Question {
        final String name;
        final int type;
        /** Offset just after the question section (end of QTYPE/QCLASS). */
        final int end;

        Question(String name, int type, int end) {
            this.name = name;
            this.type = type;
            this.end = end;
        }
    }

    /** Parses the first question of the DNS message at {@code buf[off..off+len)}, or returns null. */
    static Question parseQuestion(byte[] buf, int off, int len) {
        if (len < 12) return null;
        int flags = u16(buf, off + 2);
        if ((flags & 0x8000) != 0) return null; // a response, not a query
        int qdcount = u16(buf, off + 4);
        if (qdcount < 1) return null;

        StringBuilder name = new StringBuilder();
        int p = off + 12;
        int limit = off + len;
        while (true) {
            if (p >= limit) return null;
            int labelLen = buf[p] & 0xFF;
            if (labelLen == 0) {
                p++;
                break;
            }
            if ((labelLen & 0xC0) != 0) return null; // compression is not used in questions
            p++;
            if (p + labelLen > limit) return null;
            if (name.length() > 0) name.append('.');
            name.append(new String(buf, p, labelLen, StandardCharsets.ISO_8859_1));
            p += labelLen;
            if (name.length() > 255) return null;
        }
        if (p + 4 > limit) return null;
        int type = u16(buf, p);
        return new Question(name.toString().toLowerCase(java.util.Locale.ROOT), type, p + 4 - off);
    }

    /**
     * Builds an answer for a blocked query: 0.0.0.0 for A, :: for AAAA and an empty
     * NOERROR answer for every other record type.
     */
    static byte[] buildBlockedResponse(byte[] buf, int off, Question q) {
        int rdLen = q.type == TYPE_A ? 4 : q.type == TYPE_AAAA ? 16 : 0;
        boolean answer = rdLen > 0;
        int size = q.end + (answer ? 12 + rdLen : 0);
        byte[] out = new byte[size];
        System.arraycopy(buf, off, out, 0, q.end);

        int queryFlags = u16(buf, off + 2);
        int flags = 0x8000              // QR: response
                | (queryFlags & 0x7800)  // opcode
                | (queryFlags & 0x0100)  // RD
                | 0x0080;                // RA
        put16(out, 2, flags);
        put16(out, 4, 1);               // QDCOUNT
        put16(out, 6, answer ? 1 : 0);  // ANCOUNT
        put16(out, 8, 0);               // NSCOUNT
        put16(out, 10, 0);              // ARCOUNT (drops any EDNS OPT record)

        if (answer) {
            int p = q.end;
            put16(out, p, 0xC00C);      // pointer to the question name
            put16(out, p + 2, q.type);
            put16(out, p + 4, 1);       // class IN
            put32(out, p + 6, 300);     // TTL
            put16(out, p + 10, rdLen);  // RDATA is all zeros
        }
        return out;
    }

    static int u16(byte[] b, int i) {
        return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
    }

    static void put16(byte[] b, int i, int v) {
        b[i] = (byte) (v >>> 8);
        b[i + 1] = (byte) v;
    }

    static void put32(byte[] b, int i, int v) {
        b[i] = (byte) (v >>> 24);
        b[i + 1] = (byte) (v >>> 16);
        b[i + 2] = (byte) (v >>> 8);
        b[i + 3] = (byte) v;
    }
}
