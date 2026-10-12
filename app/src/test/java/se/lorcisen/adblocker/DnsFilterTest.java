package se.lorcisen.adblocker;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.Collections;
import java.util.Set;

import org.junit.Test;

public class DnsFilterTest {

    static byte[] dnsQuery(String name, int type) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x12); out.write(0x34);       // ID
        out.write(0x01); out.write(0x00);       // RD
        out.write(0); out.write(1);             // QDCOUNT
        for (int i = 0; i < 6; i++) out.write(0);
        for (String label : name.split("\\.")) {
            out.write(label.length());
            out.write(label.getBytes(), 0, label.length());
        }
        out.write(0);
        out.write(type >> 8); out.write(type);
        out.write(0); out.write(1);             // class IN
        return out.toByteArray();
    }

    static byte[] ipv4Udp(byte[] payload) {
        byte[] p = new byte[28 + payload.length];
        p[0] = 0x45;
        DnsPacket.put16(p, 2, p.length);
        p[8] = 64;
        p[9] = 17;
        byte[] src = {10, 111, (byte) 222, 1};
        byte[] dst = {10, 111, (byte) 222, 2};
        System.arraycopy(src, 0, p, 12, 4);
        System.arraycopy(dst, 0, p, 16, 4);
        DnsPacket.put16(p, 20, 40000);
        DnsPacket.put16(p, 22, 53);
        DnsPacket.put16(p, 24, 8 + payload.length);
        System.arraycopy(payload, 0, p, 28, payload.length);
        return p;
    }

    @Test
    public void parsesQueryAndBuildsBlockedAnswer() {
        byte[] pkt = ipv4Udp(dnsQuery("Ads.DoubleClick.net", DnsPacket.TYPE_A));
        IpPacket.Udp udp = IpPacket.parseUdp(pkt, pkt.length);
        assertNotNull(udp);
        assertEquals(53, udp.dstPort);
        DnsPacket.Question q = DnsPacket.parseQuestion(pkt, udp.payloadOffset, udp.payloadLength);
        assertNotNull(q);
        assertEquals("ads.doubleclick.net", q.name);
        assertEquals(DnsPacket.TYPE_A, q.type);

        byte[] dns = DnsPacket.buildBlockedResponse(pkt, udp.payloadOffset, q);
        assertEquals(0x1234, DnsPacket.u16(dns, 0));
        assertEquals(0x8180, DnsPacket.u16(dns, 2));   // response, RD, RA, NOERROR
        assertEquals(1, DnsPacket.u16(dns, 6));        // one answer
        assertEquals(q.end + 16, dns.length);          // answer RR with 4-byte address
        assertEquals(4, DnsPacket.u16(dns, dns.length - 6));

        byte[] reply = IpPacket.buildUdpReply(udp, dns, dns.length);
        IpPacket.Udp back = IpPacket.parseUdp(reply, reply.length);
        assertNotNull(back);
        assertEquals(53, back.srcPort);
        assertEquals(40000, back.dstPort);
        assertArrayEquals(udp.srcAddr, back.dstAddr);
        assertEquals(0, IpPacket.checksum(reply, 0, 20)); // valid header checksum
    }

    @Test
    public void aaaaGetsSixteenByteAnswerOtherTypesNone() {
        byte[] q6 = dnsQuery("ads.example.com", DnsPacket.TYPE_AAAA);
        DnsPacket.Question q = DnsPacket.parseQuestion(q6, 0, q6.length);
        assertEquals(q.end + 28, DnsPacket.buildBlockedResponse(q6, 0, q).length);

        byte[] https = dnsQuery("ads.example.com", 65);
        q = DnsPacket.parseQuestion(https, 0, https.length);
        byte[] r = DnsPacket.buildBlockedResponse(https, 0, q);
        assertEquals(q.end, r.length);
        assertEquals(0, DnsPacket.u16(r, 6));
    }

    @Test
    public void ignoresNonDns() {
        byte[] pkt = ipv4Udp(dnsQuery("a.b", 1));
        pkt[9] = 6; // TCP
        assertNull(IpPacket.parseUdp(pkt, pkt.length));
        byte[] resp = dnsQuery("a.b", 1);
        resp[2] |= (byte) 0x80; // response flag
        assertNull(DnsPacket.parseQuestion(resp, 0, resp.length));
    }

    @Test
    public void parsesListFormats() {
        assertEquals("ads.example.com", DomainFilter.parseLine("0.0.0.0 ads.example.com # comment"));
        assertEquals("ads.example.com", DomainFilter.parseLine("127.0.0.1\tADS.example.com"));
        assertEquals("ads.example.com", DomainFilter.parseLine("||ads.example.com^"));
        assertEquals("ads.example.com", DomainFilter.parseLine("*.ads.example.com"));
        assertEquals("tracker.net", DomainFilter.parseLine("tracker.net"));
        assertNull(DomainFilter.parseLine("127.0.0.1 localhost"));
        assertNull(DomainFilter.parseLine("0.0.0.0 0.0.0.0"));
        assertNull(DomainFilter.parseLine("::1 ip6-localhost"));
        assertNull(DomainFilter.parseLine("# 0.0.0.0 x.com"));
        assertNull(DomainFilter.parseLine("! adblock comment"));
        assertNull(DomainFilter.parseLine("||example.com/path^"));
        assertNull(DomainFilter.parseLine("@@||example.com^"));
        assertNull(DomainFilter.parseLine("example.com##.banner"));
    }

    @Test
    public void matchesSubdomainsAndRespectsAllowlist() throws Exception {
        DomainFilter.Builder b = new DomainFilter.Builder();
        b.addAll(new BufferedReader(new StringReader("0.0.0.0 doubleclick.net\n||applovin.com^\n")));
        Set<String> allow = Collections.singleton("good.doubleclick.net");
        Set<String> block = Collections.singleton("custom.org");
        DomainFilter f = b.build(allow, block);

        assertEquals(2, f.listSize());
        assertTrue(f.isBlocked("doubleclick.net"));
        assertTrue(f.isBlocked("ad.g.doubleclick.net"));
        assertTrue(f.isBlocked("ms.applovin.com"));
        assertTrue(f.isBlocked("x.custom.org"));
        assertFalse(f.isBlocked("good.doubleclick.net"));
        assertFalse(f.isBlocked("a.good.doubleclick.net"));
        assertFalse(f.isBlocked("notdoubleclick.net"));
        assertFalse(f.isBlocked("youtube.com"));
        assertFalse(f.isBlocked(""));
    }
}
