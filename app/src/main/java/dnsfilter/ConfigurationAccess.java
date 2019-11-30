package dnsfilter;


import java.io.IOException;
import java.util.Properties;

import dnsfilter.remote.RemoteAccessClient;
import util.LoggerInterface;

public abstract class ConfigurationAccess {


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
        return new RemoteAccessClient(logger, host, port, keyphrase);
    }

    @Override
    public String toString() {
        return "LOCAL";
    }


    public boolean isLocal() {
        return true;
    }

    abstract public void releaseConfiguration() ;

    abstract public Properties getConfig() throws IOException;

    abstract public byte[] readConfig() throws IOException;

    abstract public void updateConfig(byte[] config) throws IOException;

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

    abstract public String[] getAvailableBackups() throws IOException;

    abstract public void doBackup(String name) throws IOException ;

    abstract public void doRestoreDefaults() throws IOException;

    abstract public void doRestore(String name) throws IOException;

    abstract public void wakeLock() throws IOException;

    abstract public void releaseWakeLock() throws IOException;

}
