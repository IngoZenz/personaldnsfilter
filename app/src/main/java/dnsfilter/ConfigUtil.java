package dnsfilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;

import util.Logger;

public class ConfigUtil {

    private Properties config = new Properties();
    private byte[] configBytes;
    boolean changed = false;

    public static class HostFilterList {
        public boolean active;
        public String category;
        public String id;
        public String url;

        public HostFilterList(boolean active, String category, String id, String url) {
            this.active = active;
            this.category = category;
            this.id = id;
            this.url = url;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (!this.getClass().equals(obj.getClass()))
                return false;

            HostFilterList hfl = (HostFilterList) obj;
            return (
                active == hfl.active &&
                (category != null ? category.equals(hfl.category) : hfl.category == null) &&
                (id != null ? id.equals(hfl.id) : hfl.id == null) &&
                (url != null ? url.equals(hfl.url) : hfl.url == null));
        }

        @Override
        public int hashCode() {
            int h = 17;
            h = 31 * h + (active ? 1 : 0);
            h = 31 * h + (category != null ? category.hashCode() : 0);
            h = 31 * h + (id != null ? id.hashCode() : 0);
            h = 31 * h + (url != null ? url.hashCode() : 0);
            return h;
        }
    }

    protected ConfigUtil(byte[] configBytes) throws IOException {
        this.configBytes = configBytes;
        config.load(new ByteArrayInputStream(configBytes));
    }

    public boolean isChanged() {
        return changed;
    }

    public byte[] getConfigBytes() throws IOException {
        if (changed) {
            updateConfigBytes();
            changed = false;
        }
        return configBytes;
    }

    private static String escapeConfigValue(String value) {
        if (value == null)
            return "";
        return value.replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private void updateConfigBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(configBytes)));
        Set<String> writtenKeys = new HashSet<String>();
        try {
            String ln;
            while ((ln = reader.readLine()) != null) {

                String trimLN = ln.trim();

                if (!trimLN.equals("") && !trimLN.startsWith("#") && !trimLN.startsWith("!")) {
                    int idx = trimLN.indexOf('=');
                    if (idx == -1)
                        throw new IOException("broken config! " + trimLN);
                    String key = trimLN.substring(0, idx).trim();
                    writtenKeys.add(key);
                    String value = config.getProperty(key);
                    ln = key + " = " + escapeConfigValue(value);
                }

                out.write((ln + "\r\n").getBytes());
            }

            List<String> newKeys = new ArrayList<String>();
            for (String key : config.stringPropertyNames()) {
                if (!writtenKeys.contains(key)) {
                    newKeys.add(key);
                }
            }
            Collections.sort(newKeys);
            for (int i = 0; i < newKeys.size(); i++) {
                String key = newKeys.get(i);
                String value = config.getProperty(key);
                out.write((key + " = " + escapeConfigValue(value) + "\r\n").getBytes());
            }

            out.flush();
            configBytes = out.toByteArray();
        } finally {
            reader.close();
            out.close();
        }
    }

    public void updateConfigValue(String key, String value) {
        String current = config.getProperty(key);
        if (value == null) {
            config.remove(key);
        } else {
            config.setProperty(key, value);
        }
        changed = changed || (value != null && !value.equals(current)) || (value == null && current != null);
    }

    public Properties getProperties() {
        return config;
    }

    public String getConfigValue(String key, String defaultValue) {
        return config.getProperty(key, defaultValue);
    }

    public HostFilterList[] getConfiguredFilterLists() {
        return getConfiguredFilterLists(config);
    }

    public static Vector<HostFilterList> getConfiguredFilterListsAsVector(Properties config) {
        HostFilterList[] result = getConfiguredFilterLists(config);
        Vector<HostFilterList> resultVector = new Vector<>(result.length);
        for (int i = 0; i < result.length; i++)
            resultVector.addElement(result[i]);
        return resultVector;
    }

    private static class StringWrapper implements Comparable<StringWrapper> {

        final String wrapped;
        final int seq;

        private StringWrapper(String s, int seq) {
            this.wrapped = s.toUpperCase(Locale.ROOT);
            this.seq = seq;
        }

        @Override
        public int compareTo(StringWrapper arg) {
            int cmp = wrapped.compareTo(arg.wrapped);
            if (cmp != 0)
                return cmp;
            return (seq < arg.seq) ? -1 : ((seq == arg.seq) ? 0 : 1);
        }
    }

    public static HostFilterList[] getConfiguredFilterLists(Properties config) {
        String urls = config.getProperty("filterAutoUpdateURL", "");
        String url_IDs = config.getProperty("filterAutoUpdateURL_IDs", "");
        String url_switchs = config.getProperty("filterAutoUpdateURL_switchs", "");
        String url_categories = config.getProperty("filterAutoUpdateURL_categories", "");

        StringTokenizer urlTokens = new StringTokenizer(urls, ";");
        StringTokenizer urlIDTokens = new StringTokenizer(url_IDs, ";");
        StringTokenizer urlSwitchTokens = new StringTokenizer(url_switchs, ";");
        StringTokenizer categoryTokens = new StringTokenizer(url_categories, ";");

        int count = urlTokens.countTokens();
        HostFilterList[] result = new HostFilterList[count];
        TreeMap<StringWrapper, HostFilterList> resultSorted = new TreeMap<StringWrapper, HostFilterList>();

        for (int i = 0; i < count; i++) {
            String urlHost = null;
            String urlStr = urlTokens.nextToken().trim();
            String url_id = "";
            if (urlIDTokens.hasMoreTokens())
                url_id = urlIDTokens.nextToken().trim();
            else {
                URL url = null;
                try {
                    url = new URL(urlStr);
                    urlHost = url.getHost();
                    url_id = urlHost;
                } catch (MalformedURLException e) {
                    Logger.getLogger().logException(e);
                    url_id = "-";
                }
            }
            String url_category = "";
            if (categoryTokens.hasMoreTokens())
                url_category = categoryTokens.nextToken().trim();
            else if (urlHost != null)
                url_category = urlHost;
            else {
                URL url = null;
                try {
                    url = new URL(urlStr);
                    url_category = url.getHost();
                } catch (MalformedURLException e) {
                    Logger.getLogger().logException(e);
                    url_category = "-";
                }
            }
            boolean active = true;
            if (urlSwitchTokens.hasMoreTokens())
                active = Boolean.parseBoolean(urlSwitchTokens.nextToken().trim());

            resultSorted.put(new StringWrapper(url_id + url_category + urlStr + active, i), new HostFilterList(active, url_category, url_id, urlStr));
        }
        return resultSorted.values().toArray(result);
    }

    private String[] getFilterCfgStrings(HostFilterList[] filterEntries) {
        StringBuilder[] result = {new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder()};
        String dim = "";
        for (int i = 0; i < filterEntries.length; i++) {
            result[0].append(dim).append(filterEntries[i].active);
            result[1].append(dim).append(filterEntries[i].id);
            result[2].append(dim).append(filterEntries[i].url);
            result[3].append(dim).append(filterEntries[i].category);
            dim = "; ";
        }
        return new String[]{result[0].toString(), result[1].toString(), result[2].toString(), result[3].toString()};
    }

    public void setConfiguredFilterLists(HostFilterList[] filterLists) {
        String[] filterCfgStrings = getFilterCfgStrings(filterLists);
        updateConfigValue("filterAutoUpdateURL_switchs", filterCfgStrings[0]);
        updateConfigValue("filterAutoUpdateURL_IDs", filterCfgStrings[1]);
        updateConfigValue("filterAutoUpdateURL", filterCfgStrings[2]);
        updateConfigValue("filterAutoUpdateURL_categories", filterCfgStrings[3]);
    }
}
