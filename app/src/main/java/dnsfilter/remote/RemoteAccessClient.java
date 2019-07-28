package dnsfilter.remote;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

import java.io.OutputStream;
import java.net.Socket;
import java.util.Properties;

import dnsfilter.ConfigurationAccess;
import dnsfilter.DNSFilterManager;
import util.Logger;
import util.LoggerInterface;
import util.Utils;

public class RemoteAccessClient extends ConfigurationAccess {

    // Msg Type constants
    static final int LOG = 1;
    static final int LOG_LN = 2;
    static final int LOG_MSG = 3;
    static final int UPD_DNS = 4;
    static final int UPD_CON_CNT = 5;


    private String host;
    private int port;
    private String user;
    private String pwd;
    private Socket ctrlcon;
    private RemoteStream remoteStream;
    private String remote_version;
    private String last_dns="<unknown>";
    private int con_cnt = -1;
    private LoggerInterface connectedLogger;



    public RemoteAccessClient(LoggerInterface logger, String host, int port, String user, String pwd) throws IOException{

        if (logger == null)
            logger = Logger.getLogger();

        connectedLogger = logger;
        this.host=host;
        this.port=port;
        this.user = user;
        this.pwd = pwd;
        ctrlcon = initConnection();
        remoteStream = new RemoteStream(initConnection());
    }

    @Override
    public String toString() {
        return "REMOTE -> "+host+":"+port;
    }

    private class RemoteStream implements Runnable {

        Socket streamCon;
        boolean stopped = false;

        public RemoteStream(Socket streamCon) throws IOException {
            this.streamCon = streamCon;
            try {
                streamCon.getOutputStream().write(("attach\n").getBytes());
                streamCon.getOutputStream().flush();
                InputStream in = streamCon.getInputStream();
                String response = Utils.readLineFromStream(streamCon.getInputStream());
                if (!response.equals("OK")) {
                    throw new IOException(response);
                }
            } catch (IOException e) {
                connectedLogger.logLine("Remote action attach Remote Stream failed! "+e.getMessage());
                closeConnection();
                throw e;
            }

            new Thread(this).start();
        }

        @Override
        public void run() {
            byte[] msg = new byte[2048];
            try {
                while (!stopped) {
                    DataInputStream in = new DataInputStream(streamCon.getInputStream());
                    int type = in.readShort();
                    short len = in.readShort();
                    msg = getBuffer(msg, len, 2048, 1024000);

                    in.readFully(msg, 0, len);

                    switch (type) {

                        case LOG_LN:
                            connectedLogger.logLine(new String(msg, 0, len));
                            break;
                        case LOG:
                            connectedLogger.log(new String(msg, 0, len));
                            break;
                        case LOG_MSG:
                            connectedLogger.message(new String(msg, 0, len));
                            break;
                        case UPD_DNS:
                            last_dns = new String(msg, 0, len);
                            break;
                        case UPD_CON_CNT:
                            con_cnt = Integer.parseInt(new String(msg, 0, len));
                            break;
                        default:
                            throw new IOException("Unknown message type: " + type);
                    }

                }
            } catch (Exception e){
                if (!stopped) {
                    connectedLogger.logLine("Exception during RemoteStream read! " + e.toString());
                    closeConnection();
                }
            }
        }

        private byte[] getBuffer(byte[] msg, int len, int initLen, int maxLen) throws IOException{
            if (len < initLen && msg.length > initLen)
                //resize buffer for saving memory
                return  new byte[initLen];

            else if (len < initLen)
                return msg; // reuse the buffer

            else if (len > maxLen)
                throw new IOException("Buffer Overflow: "+len+" bytes!");

            else
                return new byte[len];

        }

        public void close() {
            stopped = true;
            try {
                if (streamCon!=null) {
                    streamCon.getOutputStream().write("releaseConfiguration()".getBytes());
                    streamCon.getOutputStream().flush();
                }
            } catch (IOException e) {
                connectedLogger.logLine("Exception during remote configuration release: "+e.toString());
            }
            closeSocket(streamCon);
        }
    }

    private void closeSocket(Socket s){
        if (s == null)
            return;
        try {
            s.shutdownOutput();
            s.shutdownInput();
            s.close();
        } catch (IOException e) {
            //connectedLogger.logLine("Exception during closeConnection(): " + e.toString());
        }
    }

    private void closeConnection() {

        closeSocket(ctrlcon);

        if (remoteStream != null)
            remoteStream.close();

        ctrlcon = null;
        remoteStream = null;
    }


    private Socket initConnection() throws IOException {
        try {
            Socket con = new Socket(host, port);
            con.getOutputStream().write((DNSFilterManager.VERSIONID+"\n"+user + "\n" + pwd + "\n").getBytes());
            con.getOutputStream().flush();
            String response = Utils.readLineFromStream(con.getInputStream());
            if (!response.equals("OK")) {
                throw new IOException(response);
            }
            remote_version = Utils.readLineFromStream(con.getInputStream());
            last_dns = Utils.readLineFromStream(con.getInputStream());
            return con;
        } catch (IOException e) {
            connectedLogger.logLine("Exception during initConnection(): "+e.toString());
            closeConnection();
            throw e;
        }
    }

    private Socket getConnection() throws IOException {
        if (ctrlcon == null) {
            ctrlcon = initConnection();
            remoteStream = new RemoteStream(initConnection());
        }
        return ctrlcon;
    }



    private void triggerAction(String action) throws IOException {
        try {
            getConnection().getOutputStream().write((action+"\n").getBytes());
            getConnection().getOutputStream().flush();
            InputStream in = getConnection().getInputStream();
            String response = Utils.readLineFromStream(in);
            if (!response.equals("OK")) {
                throw new ConfigurationAccessException(response, null);
            }
        } catch (ConfigurationAccessException e) {
            connectedLogger.logLine("Remote action failed! "+e.getMessage());
            throw e;
        } catch (IOException e) {
            connectedLogger.logLine("Remote action "+action+" failed! "+e.getMessage());
            closeConnection();
            throw e;
        }
    }

    public boolean isLocal() {
        return false;
    }

    @Override
    public void releaseConfiguration() {
        if (remoteStream != null)
            remoteStream.close();
        try {
            if (ctrlcon!=null) {
                ctrlcon.getOutputStream().write("releaseConfiguration()".getBytes());
                ctrlcon.getOutputStream().flush();
            }
        } catch (IOException e) {
            connectedLogger.logLine("Exception during remote configuration release: "+e.toString());
        }
        closeSocket(ctrlcon);

        ctrlcon = null;
        remoteStream = null;
    }

    @Override
    public Properties getConfig() throws IOException {
        try {
            getConnection().getOutputStream().write("getConfig()\n".getBytes());
            getConnection().getOutputStream().flush();
            InputStream in = getConnection().getInputStream();
            String response = Utils.readLineFromStream(in);
            if (!response.equals("OK")) {
                throw new ConfigurationAccessException(response, null);
            }
            try {
                return (Properties) new ObjectInputStream(in).readObject();
            } catch (ClassNotFoundException e) {
                connectedLogger.logException(e);
               throw new IOException(e);
            }
        } catch (ConfigurationAccessException e) {
            connectedLogger.logLine("Remote action failed! "+e.getMessage());
            throw e;
        } catch (IOException e) {
            connectedLogger.logLine("Remote action getConfig() failed! "+e.getMessage());
            closeConnection();
            throw e;
        }
    }

    @Override
    public byte[] readConfig() throws IOException {
        try {
            getConnection().getOutputStream().write("readConfig()\n".getBytes());
            getConnection().getOutputStream().flush();
            DataInputStream in = new DataInputStream(getConnection().getInputStream());
            String response = Utils.readLineFromStream(in);
            if (!response.equals("OK")) {
                throw new ConfigurationAccessException(response, null);
            }
            byte[] buf = new byte[in.readInt()];
            in.readFully(buf);
            return buf;

        } catch (ConfigurationAccessException e) {
            connectedLogger.logLine("Remote action failed! "+e.getMessage());
            throw e;
        } catch (IOException e) {
            connectedLogger.logLine("Remote action readConfig() failed! "+e.getMessage());
            closeConnection();
            throw e;
        }
    }


    @Override
    public void updateConfig(byte[] config) throws IOException {
        try {
            InputStream in = getConnection().getInputStream();
            DataOutputStream out = new DataOutputStream(getConnection().getOutputStream());

            out.write("updateConfig()\n".getBytes());
            out.writeInt(config.length);
            out.write(config);
            out.flush();

            String response = Utils.readLineFromStream(in);
            if (!response.equals("OK")) {
                throw new ConfigurationAccessException(response, null);
            }
        } catch (ConfigurationAccessException e) {
            connectedLogger.logLine("Remote action failed! "+e.getMessage());
            throw e;
        } catch (IOException e) {
            connectedLogger.logLine("Remote action updateConfig() failed! "+e.getMessage());
            closeConnection();
            throw e;
        }
    }

    @Override
    public byte[] getAdditionalHosts(int limit) throws IOException {
        try {
            DataOutputStream out = new DataOutputStream(getConnection().getOutputStream());
            DataInputStream in = new DataInputStream(getConnection().getInputStream());

            out.write(("getAdditionalHosts()\n").getBytes());
            out.writeInt(limit);
            out.flush();

            String response = Utils.readLineFromStream(in);
            if (!response.equals("OK")) {
                throw new ConfigurationAccessException(response, null);
            }
            byte[] result = new byte[in.readInt()];
            in.readFully(result);
            return result;
        } catch (ConfigurationAccessException e) {
            connectedLogger.logLine("Remote action failed! "+e.getMessage());
            throw e;
        } catch (IOException e) {
            connectedLogger.logLine("Remote action getAdditionalHosts() failed! "+e.getMessage());
            closeConnection();
            throw e;
        }
    }

    @Override
    public void updateAdditionalHosts(byte[] bytes) throws IOException {
        try {

            DataOutputStream out = new DataOutputStream(getConnection().getOutputStream());
            DataInputStream in = new DataInputStream(getConnection().getInputStream());

            out.write("updateAdditionalHosts()\n".getBytes());
            out.writeInt(bytes.length);
            out.write(bytes);
            out.flush();

            String response = Utils.readLineFromStream(in);
            if (!response.equals("OK")) {
                throw new ConfigurationAccessException(response, null);
            }
        } catch (ConfigurationAccessException e) {
            connectedLogger.logLine("Remote action failed! "+e.getMessage());
            throw e;
        } catch (IOException e) {
            connectedLogger.logLine("Remote action updateAdditionalHosts() failed! "+e.getMessage());
            closeConnection();
            throw e;
        }
    }

    @Override
    public void updateFilter(String entries, boolean filter) throws IOException {
        try {
            Socket con = getConnection();
            OutputStream out = con.getOutputStream();
            InputStream in = con.getInputStream();
            out.write(("updateFilter()\n"+entries+"\n"+filter+"\n").getBytes());
            out.flush();
            String response = Utils.readLineFromStream(in);
            if (!response.equals("OK")) {
                throw new ConfigurationAccessException(response, null);
            }
        } catch (ConfigurationAccessException e) {
            connectedLogger.logLine("Remote action failed! "+e.getMessage());
            throw e;

        } catch (IOException e) {
            connectedLogger.logLine("Remote action  updateFilter() failed! "+e.getMessage());
            closeConnection();
            throw e;
        }

    }
    @Override
    public String getVersion() throws IOException {
        return remote_version;
    }

    @Override
    public void connectLog(LoggerInterface logger) throws IOException {
        connectedLogger = logger;
    }

    @Override
    public int openConnectionsCount(){
        return con_cnt;
    }

    @Override
    public String getLastDNSAddress()  {
        return last_dns;
    }

    @Override
    public void restart() throws IOException {
       triggerAction("restart()");
    }

    @Override
    public void stop() throws IOException {
        triggerAction("stop()");
    }

    @Override
   public long[] getFilterStatistics() throws IOException {
        try {
            DataOutputStream out = new DataOutputStream(getConnection().getOutputStream());
            DataInputStream in = new DataInputStream(getConnection().getInputStream());
            out.write(("getFilterStatistics()\n").getBytes());
            out.flush();

            String response = Utils.readLineFromStream(in);
            if (!response.equals("OK")) {
                throw new ConfigurationAccessException(response, null);
            }
            return new long[] {in.readLong(), in.readLong()};
        } catch (ConfigurationAccessException e) {
            connectedLogger.logLine("Remote action failed! "+e.getMessage());
            throw e;
        } catch (IOException e) {
            connectedLogger.logLine("Remote action  getFilterStatistics() failed! "+e.getMessage());
            closeConnection();
            throw e;
        }
    }

    @Override
    public void triggerUpdateFilter() throws IOException {
        triggerAction("triggerUpdateFilter()");
    }

    @Override
    public void doBackup() throws IOException {
        triggerAction("doBackup()");
    }

    @Override
    public void doRestoreDefaults() throws IOException {
        triggerAction("doRestoreDefaults()");
    }

    @Override
    public void doRestore() throws IOException{
        triggerAction("doRestore()");
    }

    @Override
    public void wakeLock() throws IOException {
        triggerAction("wakeLock()");
    }

    @Override
    public void releaseWakeLock() throws IOException {
        triggerAction("releaseWakeLock()");
    }

}
