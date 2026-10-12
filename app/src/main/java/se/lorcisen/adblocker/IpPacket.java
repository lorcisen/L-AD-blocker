package se.lorcisen.adblocker;

/** Parsing and building of the IPv4/UDP packets that carry DNS through the tunnel. */
final class IpPacket {
    private IpPacket() {}

    /** A UDP datagram read from the tunnel. */
    static final class Udp {
        final byte[] srcAddr = new byte[4];
        final byte[] dstAddr = new byte[4];
        int srcPort;
        int dstPort;
        int payloadOffset;
        int payloadLength;
    }

    /** Parses an IPv4 UDP packet, or returns null for anything else (TCP, IPv6, fragments...). */
    static Udp parseUdp(byte[] pkt, int len) {
        if (len < 28) return null;
        if ((pkt[0] >> 4 & 0xF) != 4) return null;
        int ihl = (pkt[0] & 0xF) * 4;
        if (ihl < 20 || len < ihl + 8) return null;
        if ((pkt[9] & 0xFF) != 17) return null; // not UDP
        int fragment = DnsPacket.u16(pkt, 6);
        if ((fragment & 0x3FFF) != 0) return null; // fragmented: MF set or non-zero offset
        int total = Math.min(DnsPacket.u16(pkt, 2), len);
        int udpLen = DnsPacket.u16(pkt, ihl + 4);
        if (udpLen < 8 || ihl + udpLen > total) return null;

        Udp u = new Udp();
        System.arraycopy(pkt, 12, u.srcAddr, 0, 4);
        System.arraycopy(pkt, 16, u.dstAddr, 0, 4);
        u.srcPort = DnsPacket.u16(pkt, ihl);
        u.dstPort = DnsPacket.u16(pkt, ihl + 2);
        u.payloadOffset = ihl + 8;
        u.payloadLength = udpLen - 8;
        return u;
    }

    /** Builds the IPv4/UDP reply to {@code request} carrying {@code payload}. */
    static byte[] buildUdpReply(Udp request, byte[] payload, int payloadLen) {
        int total = 20 + 8 + payloadLen;
        byte[] p = new byte[total];
        p[0] = 0x45;                       // IPv4, 20-byte header
        DnsPacket.put16(p, 2, total);
        DnsPacket.put16(p, 6, 0x4000);     // don't fragment
        p[8] = 64;                         // TTL
        p[9] = 17;                         // UDP
        System.arraycopy(request.dstAddr, 0, p, 12, 4);
        System.arraycopy(request.srcAddr, 0, p, 16, 4);
        DnsPacket.put16(p, 10, checksum(p, 0, 20));

        DnsPacket.put16(p, 20, request.dstPort);
        DnsPacket.put16(p, 22, request.srcPort);
        DnsPacket.put16(p, 24, 8 + payloadLen);
        // UDP checksum 0 = "not computed", which is valid for IPv4.
        System.arraycopy(payload, 0, p, 28, payloadLen);
        return p;
    }

    static int checksum(byte[] b, int off, int len) {
        long sum = 0;
        for (int i = 0; i + 1 < len; i += 2) sum += DnsPacket.u16(b, off + i);
        if ((len & 1) != 0) sum += (b[off + len - 1] & 0xFF) << 8;
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }
}
