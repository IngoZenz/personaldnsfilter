package dnsfilter;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import dnsfilter.remote.RemoteAccessClient;
import util.LoggerInterface;

public abstract class ConfigurationAccess {

    protected ConfigUtil config_util = null;
    protected static ConfigurationAccess REMOTE;


    public static class ConfigurationAccessException extends IOException{
        public ConfigurationAccessException(String msg, IOException cause){
            super(msg, cause);
        }
        public ConfigurationAccessException(String msg){
            super(msg);
        }
    }


    static public ConfigurationAccess getLocal() {
        return DNSFilterManager.getInstance();
    }

    static public ConfigurationAccess getRemote(LoggerInterface logger, String host, int port, String keyphrase) throws IOException {
        REMOTE = new RemoteAccessClient(logger, host, port, keyphrase);
        return REMOTE;
    }

    static public ConfigurationAccess getCurrent(){
        if (REMOTE == null)
            return getLocal();
        else return REMOTE;
    }

    protected void invalidate() {
        config_util = null;
    }

    @Override
    public String toString() {
        return "LOCAL";
    }


    public boolean isLocal() {
        return true;
    }

    public ConfigUtil getConfigUtil() throws IOException {
        if (config_util == null)
            config_util = new ConfigUtil(readConfig());
        return config_util;
    }

    abstract public void releaseConfiguration() ;

    abstract public Properties getConfig() throws IOException;

    abstract public Properties getDefaultConfig() throws IOException;

    abstract public byte[] readConfig() throws IOException;

    abstract public void updateConfig(byte[] config) throws IOException;

    public abstract void updateConfigMergeDefaults(byte[] config) throws IOException;

    abstract public byte[] getAdditionalHosts(int limit) throws IOException;

    abstract public void updateAdditionalHosts(byte[] bytes) throws IOException;

    abstract public void updateFilter(String entries, boolean filter) throws IOException ;

    abstract public String getVersion() throws IOException;

    abstract public int openConnectionsCount()  throws IOException;;

    abstract public String getLastDNSAddress()  throws IOException;;

    abstract public void restart() throws IOException ;

    abstract public void stop() throws IOException ;

    abstract public long[] getFilterStatistics() throws IOException;

    abstract public void triggerUpdateFilter()  throws IOException;

    abstract public void doBackup(OutputStream out) throws IOException ;

    abstract public void doRestoreDefaults() throws IOException;

    abstract public void doRestore(InputStream in) throws IOException;

    abstract public void wakeLock() throws IOException;

    abstract public void releaseWakeLock() throws IOException;

}
