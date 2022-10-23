package dnsfilter.android.tests;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import dnsfilter.android.dnsserverconfig.widget.DNSServerConfigEntrySerializer;
import dnsfilter.android.dnsserverconfig.widget.DNSType;
import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry;
import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigBaseEntry;

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
}
