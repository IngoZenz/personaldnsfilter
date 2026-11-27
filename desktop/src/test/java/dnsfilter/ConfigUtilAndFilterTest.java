package dnsfilter;

import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class ConfigUtilAndFilterTest {

    @Test
    public void configUtilHandlesSpacesAroundEqualsMultilineEscapingAndChangeFlag() throws Exception {
        String rawCfg =
                "# header comment\r\n" +
                "filterActive=false\r\n" +
                "fallbackDNS = 9.9.9.9\r\n";

        ConfigUtil cfg = new ConfigUtil(rawCfg.getBytes("UTF-8"));
        Assert.assertFalse(cfg.isChanged());

        cfg.updateConfigValue("filterActive", "true");
        cfg.updateConfigValue("customNote", "line1\nline2\\end");
        Assert.assertTrue(cfg.isChanged());

        byte[] updated = cfg.getConfigBytes();
        Assert.assertFalse("isChanged should reset after exporting configBytes", cfg.isChanged());

        String updatedText = new String(updated, "UTF-8");
        Assert.assertTrue(updatedText.contains("filterActive = true"));
        Assert.assertTrue(updatedText.contains("customNote = line1\\nline2\\\\end"));

        Properties reloaded = new Properties();
        reloaded.load(new java.io.ByteArrayInputStream(updated));
        Assert.assertEquals("true", reloaded.getProperty("filterActive"));
        Assert.assertEquals("line1\nline2\\end", reloaded.getProperty("customNote"));
    }

    @Test
    public void configUtilSortsFilterListsDeterministicallyEvenWithDuplicateCategories() {
        Properties props = new Properties();
        props.setProperty("filterAutoUpdateURL_switchs", "true; false; true");
        props.setProperty("filterAutoUpdateURL_IDs", "AdListB; AdListA; AdListC");
        props.setProperty("filterAutoUpdateURL", "https://example.org/b.txt; https://example.org/a.txt; https://example.org/c.txt");
        props.setProperty("filterAutoUpdateURL_categories", "Ads; Ads; Privacy");

        ConfigUtil.HostFilterList[] sorted = ConfigUtil.getConfiguredFilterLists(props);
        Assert.assertEquals(3, sorted.length);
        // Case-insensitive sort by category+id preserves distinct entries cleanly
        Assert.assertEquals("AdListA", sorted[0].id);
        Assert.assertEquals("AdListB", sorted[1].id);
        Assert.assertEquals("AdListC", sorted[2].id);
    }

    @Test
    public void blockedHostsSupportsSubdomainMatchingOverrulesAndClear() {
        BlockedHosts hosts = new BlockedHosts(10, 50, 50); // tests small maxCountEstimate -> slots >= 1
        hosts.finalPrepare(10);
        hosts.add("tracker.example");
        hosts.addOverrule("safe.tracker.example", false);
        hosts.addOverrule("*.evil-cdn.org", true);

        Assert.assertTrue(hosts.contains("tracker.example"));
        Assert.assertTrue(hosts.contains("sub.tracker.example"));
        Assert.assertFalse(hosts.contains("safe.tracker.example"));
        Assert.assertTrue(hosts.contains("img.evil-cdn.org"));
        Assert.assertFalse(hosts.contains("clean.example"));

        hosts.clear();
        Assert.assertFalse(hosts.contains("tracker.example"));
        Assert.assertFalse(hosts.contains("img.evil-cdn.org"));
    }
}
