package se.lorcisen.adblocker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.regex.Pattern;

import org.junit.Test;

public class SponsoredLabelTest {
    private final Pattern p = AdHiderService.buildPattern(Arrays.asList("Sponsored", "Sponsrad", "Sponsrat"));

    private boolean m(String s) {
        return p.matcher(s).find();
    }

    @Test
    public void matchesLabels() {
        assertTrue(m("Sponsrad"));
        assertTrue(m("sponsrad"));
        assertTrue(m("Sponsrad · "));
        assertTrue(m("Sponsrad ·"));
        assertTrue(m("NIBE, Sponsrad · Visa mer"));
        assertTrue(m("NIBE Sponsrad"));
        assertTrue(m("Sponsored"));
        assertTrue(m("Sponsrat inlägg".replace(" inlägg", "")));
    }

    @Test
    public void ignoresOrdinaryText() {
        assertFalse(m("Detta event är sponsrade av kommunen"));
        assertFalse(m("Osponsrad"));
        assertFalse(m("Sponsradet"));
        assertFalse(m("Vi blev sponsrade igår och det var kul"));
    }
}
