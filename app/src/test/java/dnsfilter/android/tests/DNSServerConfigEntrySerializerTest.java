package dnsfilter.android.tests;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import dnsfilter.android.dnsserverconfig.widget.DNSConfigEntryValidator;
import dnsfilter.android.dnsserverconfig.widget.DNSServerConfigEntrySerializer;
import dnsfilter.android.dnsserverconfig.widget.DNSServerConfigEntryValidationResult;
import dnsfilter.android.dnsserverconfig.widget.DNSType;
import dnsfilter.android.dnsserverconfig.widget.NotDeserializableException;
import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigBaseEntry;
import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry;

public class DNSServerConfigEntrySerializerTest {

    @Test
    public void DNSServerConfigEntryDeserializationTest() {
        DNSServerConfigEntrySerializer serializer = new DNSServerConfigEntrySerializer();
        HashMap<String, DNSServerConfigBaseEntry> testResults = new HashMap<String, DNSServerConfigBaseEntry>() {{
            put("127.0.0.1::5354::udp", new DNSServerConfigEntry("127.0.0.1", "5354", DNSType.UDP, true));
            put("~127.0.0.1::5354::udp", new DNSServerConfigEntry("127.0.0.1", "5354", DNSType.UDP, false));
            put("127.0.0.1::5400::UDP", new DNSServerConfigEntry("127.0.0.1", "5400", DNSType.UDP, true));
            put("127.0.0.1::5400::UdP", new DNSServerConfigEntry("127.0.0.1", "5400", DNSType.UDP, true));
            put("174.138.21.128::5003::udp", new DNSServerConfigEntry("174.138.21.128", "5003", DNSType.UDP, true));
            put("174.138.29.175::443::doh::https://doh.tiar.app/dns-query ", new DNSServerConfigEntry("174.138.29.175", "443", DNSType.DOH, "https://doh.tiar.app/dns-query", true));
            put("174.138.29.175::853::dot::dot.tiar.app", new DNSServerConfigEntry("174.138.29.175", "853", DNSType.DOT, "dot.tiar.app", true));
            put("188.166.206.224::5003::udp", new DNSServerConfigEntry("188.166.206.224", "5003", DNSType.UDP, true));
            put("192.53.175.149::443::dot::dot-sg.blahdns.com", new DNSServerConfigEntry("192.53.175.149", "443", DNSType.DOT, "dot-sg.blahdns.com", true));
            put("192.53.175.149::853::dot::dot-sg.blahdns.com", new DNSServerConfigEntry("192.53.175.149", "853", DNSType.DOT, "dot-sg.blahdns.com", true));
            put("[2001:4860:4860::8888]::853::dot::dot-sg.blahdns.com", new DNSServerConfigEntry("2001:4860:4860::8888", "853", DNSType.DOT, "dot-sg.blahdns.com", true));
            put("~[2001:4860:4860::8888]::853::dot::dot-sg.blahdns.com", new DNSServerConfigEntry("2001:4860:4860::8888", "853", DNSType.DOT, "dot-sg.blahdns.com", false));
            put("[2606:4700:4700::1111]::443::doh::https://[2606:4700:4700::1111]/dns-query",
                    new DNSServerConfigEntry("2606:4700:4700::1111", "443", DNSType.DOH, "https://[2606:4700:4700::1111]/dns-query", true));
            put("127.0.0.1", new DNSServerConfigEntry("127.0.0.1", "53", DNSType.UDP, true));
            put("~127.0.0.1", new DNSServerConfigEntry("127.0.0.1", "53", DNSType.UDP, false));
            put("127.0.0.1::500", new DNSServerConfigEntry("127.0.0.1", "500", DNSType.UDP, true));
            put("~127.0.0.1::500::dot", new DNSServerConfigEntry("127.0.0.1", "500", DNSType.DOT, false));
            put("", new DNSServerConfigEntry());
            put("~", new DNSServerConfigEntry("", false));
        }};

        for (Map.Entry<String, DNSServerConfigBaseEntry> entry : testResults.entrySet()) {
            DNSServerConfigBaseEntry deserializationResult = serializer.deserializeSafe(entry.getKey());
            Assert.assertEquals(entry.getValue(), deserializationResult);
        }
    }

    @Test
    public void rejectsMalformedIpv6BracketsAndSeparators() {
        DNSServerConfigEntrySerializer serializer = new DNSServerConfigEntrySerializer();

        try {
            serializer.deserialize("[2001:4860:4860::8888::853::dot");
            Assert.fail("Expected NotDeserializableException for unclosed bracket");
        } catch (NotDeserializableException expected) {
            // expected
        }

        try {
            serializer.deserialize("[2001:4860:4860::8888]853");
            Assert.fail("Expected NotDeserializableException for missing :: after IPv6 bracket");
        } catch (NotDeserializableException expected) {
            // expected
        }

        Assert.assertEquals(new DNSServerConfigEntry(), serializer.deserializeSafe("[2001:4860:4860::8888::853"));
    }

    @Test
    public void validatorAcceptsValidEntriesAndRejectsInvalidOnes() {
        DNSConfigEntryValidator validator = new DNSConfigEntryValidator(
                "emptyIp", "invalidIp", "emptyPort", "invalidPort", "emptyEndpoint", "invalidEndpoint"
        );

        DNSServerConfigEntry validV4 = new DNSServerConfigEntry("9.9.9.9", "53", DNSType.UDP, "", true);
        Assert.assertFalse(validator.validate(validV4).hasError());

        DNSServerConfigEntry validV6Doh = new DNSServerConfigEntry(
                "2001:4860:4860::8888", "443", DNSType.DOH, "https://dns.google/dns-query", true
        );
        Assert.assertFalse(validator.validate(validV6Doh).hasError());

        DNSServerConfigEntry validZoneV6 = new DNSServerConfigEntry("fe80::1%eth0", "65535", DNSType.DOT, "dns.example", true);
        Assert.assertFalse(validator.validate(validZoneV6).hasError());

        DNSServerConfigEntry invalidIpOctet = new DNSServerConfigEntry("256.1.1.1", "53", DNSType.UDP, "", true);
        DNSServerConfigEntryValidationResult r1 = validator.validate(invalidIpOctet);
        Assert.assertEquals("invalidIp", r1.getIpError());
        Assert.assertNull(r1.getPortError());

        DNSServerConfigEntry invalidLeadingZero = new DNSServerConfigEntry("01.1.1.1", "53", DNSType.UDP, "", true);
        Assert.assertEquals("invalidIp", validator.validate(invalidLeadingZero).getIpError());

        DNSServerConfigEntry invalidIpv6TripleColon = new DNSServerConfigEntry("2001:::1", "53", DNSType.UDP, "", true);
        Assert.assertEquals("invalidIp", validator.validate(invalidIpv6TripleColon).getIpError());

        DNSServerConfigEntry invalidPortZero = new DNSServerConfigEntry("1.1.1.1", "0", DNSType.UDP, "", true);
        Assert.assertEquals("invalidPort", validator.validate(invalidPortZero).getPortError());

        DNSServerConfigEntry invalidPortHigh = new DNSServerConfigEntry("1.1.1.1", "65536", DNSType.UDP, "", true);
        Assert.assertEquals("invalidPort", validator.validate(invalidPortHigh).getPortError());

        DNSServerConfigEntry missingDohEndpoint = new DNSServerConfigEntry("1.1.1.1", "443", DNSType.DOH, "", true);
        Assert.assertEquals("emptyEndpoint", validator.validate(missingDohEndpoint).getEndpointError());

        DNSServerConfigEntry badDohUrl = new DNSServerConfigEntry("1.1.1.1", "443", DNSType.DOH, "ftp://1.1.1.1/dns", true);
        Assert.assertEquals("invalidEndpoint", validator.validate(badDohUrl).getEndpointError());

        DNSServerConfigEntry badDotHost = new DNSServerConfigEntry("1.1.1.1", "853", DNSType.DOT, "https://dns.quad9.net", true);
        Assert.assertEquals("invalidEndpoint", validator.validate(badDotHost).getEndpointError());
    }

    @Test
    public void validationResultEqualsAndHashCodeHandleNulls() {
        DNSServerConfigEntryValidationResult a = new DNSServerConfigEntryValidationResult();
        DNSServerConfigEntryValidationResult b = new DNSServerConfigEntryValidationResult();
        Assert.assertEquals(a, b);
        Assert.assertEquals(a.hashCode(), b.hashCode());

        a.setIpError("err");
        Assert.assertNotEquals(a, b);
        Assert.assertNotEquals(b, a);
        b.setIpError("err");
        Assert.assertEquals(a, b);
        Assert.assertEquals(a.hashCode(), b.hashCode());
    }
}
