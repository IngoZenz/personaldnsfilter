package dnsfilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.StringTokenizer;

import util.Logger;

public class ConfigUtil {

    private Properties config = new Properties();
    private byte[] configBytes;
    boolean changed = false;

    public static class HostFilterList {
        boolean active;
        String category;
        String id;
        String url;

        public HostFilterList(boolean active, String category, String id, String url) {
            this.active = active;
            this.category = category;
            this.id = id;
            this.url = url;
        }
    }

    public ConfigUtil(byte[] configBytes) throws IOException {
        this.configBytes = configBytes;
        config.load(new ByteArrayInputStream(configBytes));
    }

    boolean isChanged() {
        return changed;
    }

    byte[] getUpdatedConfigBytes() throws IOException {
        if (changed)
            updateConfigBytes();
        return configBytes;
    }

    private void updateConfigBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String ln;

        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(configBytes)));
        while ((ln = reader.readLine()) != null) {

            String trimLN = ln.trim();

            if (!trimLN.equals("") && !trimLN.startsWith("#")) {
                int idx = trimLN.indexOf(" = ");
                if (idx == -1)
                    throw new IOException("broken config! "+trimLN);
                String key = trimLN.substring(0,idx).trim();
                String value = config.getProperty(key);

                if (value == null)
                    throw new IOException("Config is inconsistent - Missing "+key);

                ln = key+" = "+value;
            }

            out.write((ln + "\r\n").getBytes());
        }
        reader.close();
        out.flush();
        out.close();
        configBytes = out.toByteArray();
    }

    public void updateConfigValue(String key, String value) {
        String current = config.getProperty(key);
        config.setProperty(key, value);
        changed = changed || (value != null && !value.equals(current)) || (current != value) ;
    }

    public String getConfigValue(String key, String defaultValue) {
        return  config.getProperty(key, defaultValue);
    }

    public HostFilterList[] getConfiguredFilterLists() {
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
                    urlHost=url.getHost();
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

            result[i] = new HostFilterList(active, url_category,url_id, urlStr);
        }
        return result;
    }


    private String[] getFilterCfgStrings(HostFilterList[] filterEntries) {
        String[] result = {"", "", "", ""};
        String dim = "";
        for (int i = 0; i < filterEntries.length; i++) {
            result[0] = result[0] + dim + filterEntries[i].active;
            result[1] = result[1] + dim + filterEntries[i].id;
            result[2] = result[2] + dim + filterEntries[i].url;
            result[3] = result[3] + dim + filterEntries[i].category;
            dim = "; ";
        }
        return result;
    }
    public void setConfiguredFilterLists(HostFilterList[] filterLists) {
        String[] filterCfgStrings = getFilterCfgStrings(filterLists);
        updateConfigValue("filterAutoUpdateURL_switchs", filterCfgStrings[0]);
        updateConfigValue("filterAutoUpdateURL_IDs", filterCfgStrings[1]);
        updateConfigValue("filterAutoUpdateURL", filterCfgStrings[2]);
        updateConfigValue("filterAutoUpdateURL_categories", filterCfgStrings[3]);
    }

}
