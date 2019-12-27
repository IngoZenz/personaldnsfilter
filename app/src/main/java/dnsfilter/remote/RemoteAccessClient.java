package dnsfilter.remote;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Properties;

import dnsfilter.ConfigurationAccess;
import dnsfilter.DNSFilterManager;
import util.Encryption;
import util.Logger;
import util.LoggerInterface;
import util.TimeoutListener;
import util.TimoutNotificator;
import util.Utils;

public class RemoteAccessClient extends ConfigurationAccess implements TimeoutListener {

   static int CON_TIMEOUT = 15000;
   static int READ_TIMEOUT = 15000;
	
	
	// Msg Type constants
    static final int LOG = 1;
    static final int LOG_LN = 2;
    static final int LOG_MSG = 3;
    static final int UPD_DNS = 4;
    static final int UPD_CON_CNT = 5;
    static final int HEART_BEAT = 6;


    private String host;
    private int port;
    private Socket ctrlcon;
    private InputStream in;
    private OutputStream out;
    private int ctrlConId = -1;
    private RemoteStream remoteStream;
    private String remote_version;
    private String last_dns="<unknown>";
    private int con_cnt = -1;
    private LoggerInterface connectedLogger;

    boolean valid = false;
    long timeout = Long.MAX_VALUE; //heartbeat timeout for dead session detection
    int timeOutCounter = 0;



    public RemoteAccessClient(LoggerInterface logger, String host, int port, String keyphrase) throws IOException{

        if (logger == null)
            logger = Logger.getLogger();

        connectedLogger = logger;
        Encryption.init_AES(keyphrase);
        this.host=host;
        this.port=port;
        connect();
    }


    private void connect() throws IOException {
        Object[] conInfo = initConnection();
        ctrlcon = (Socket) conInfo[1];
        in = (InputStream) conInfo[2];
        out = (OutputStream) conInfo[3];
        ctrlcon.setSoTimeout(READ_TIMEOUT);
        ctrlConId = (Integer) conInfo[0];
        remoteStream = new RemoteStream(ctrlConId);
        valid = true;
    }

    @Override
    public String toString() {
        return "REMOTE -> "+host+":"+port;
    }



    private void closeConnectionReconnect() {

        TimoutNotificator.getInstance().unregister(this);

        if (!valid)
            return;

        releaseConfiguration();
        Object sync = new Object();

        //wait a second before reconnect
         synchronized (sync) {
            try {
                sync.wait(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
             connect();
        } catch (IOException e){
             connectedLogger.logLine("Reconnect failed:"+e.toString());
             valid = false;
        }
    }


    private Object[] initConnection() throws IOException {
        Socket con=null;
        try {
            int id = -1;
            con = new Socket();
            con.connect(new InetSocketAddress(InetAddress.getByName(host),port), CON_TIMEOUT);
            con.setSoTimeout(READ_TIMEOUT);
            OutputStream out = Encryption.getEncryptedOutputStream(con.getOutputStream(), 1024);
            InputStream in = Encryption.getDecryptedStream(con.getInputStream());
            out.write((DNSFilterManager.VERSION+"\nnew_session\n").getBytes());
            out.flush();
            String response = Utils.readLineFromStream(in);
            if (!response.equals("OK")) {
                throw new IOException(response);
            }
            try {
                id = Integer.parseInt(Utils.readLineFromStream(in));
            } catch (Exception e) {
                throw new IOException(e);
            }
            remote_version = Utils.readLineFromStream(in);
            last_dns = Utils.readLineFromStream(in);
            try {
                con_cnt = Integer.parseInt(Utils.readLineFromStream(in));
            } catch (Exception e) {
                throw new IOException(e);
            }
            con.setSoTimeout(0);
            return new Object[] {id, con, in, out};
        } catch (IOException e) {
            connectedLogger.logLine("Exception during initConnection(): "+e.toString());
           if (con != null)
               Utils.closeSocket(con);
            throw e;
        }
    }



    private InputStream getInputStream() throws IOException {
        if (!valid)
            throw new IOException("Not connected!");

        return in;
    }

    private OutputStream getOutputStream() throws IOException {
        if (!valid)
            throw new IOException("Not connected!");

        return out;
    }



    private void triggerAction(String action, String paramStr) throws IOException {
        try {
            getOutputStream().write((action+"\n").getBytes());

            if (paramStr != null)
                getOutputStream().write((paramStr+"\n").getBytes());

            getOutputStream().flush();
            InputStream in = getInputStream();
            String response = Utils.readLineFromStream(in);
            if (!response.equals("OK")) {
                throw new ConfigurationAccessException(response, null);
            }
        } catch (ConfigurationAccessException e) {
            connectedLogger.logLine("Remote action failed! "+e.getMessage());
            throw e;
        } catch (IOException e) {
            connectedLogger.logLine("Remote action "+action+" failed! "+e.getMessage());
            closeConnectionReconnect();
            throw e;
        }
    }

    public boolean isLocal() {
        return false;
    }

    @Override
    public void releaseConfiguration() {

        TimoutNotificator.getInstance().unregister(this);
        valid = false;

        if (remoteStream != null)
            remoteStream.close();

        if (ctrlcon != null) {
            try {
                out.write("releaseConfiguration()".getBytes());
                out.flush();
            } catch (IOException e) {
                connectedLogger.logLine("Exception during remote configuration release: " + e.toString());
                Utils.closeSocket(ctrlcon);
            }
        }
        ctrlcon = null;
        remoteStream = null;
    }

    @Override
    public Properties getConfig() throws IOException {
        try {
            getOutputStream().write("getConfig()\n".getBytes());
            getOutputStream().flush();
            InputStream in = getInputStream();
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
            closeConnectionReconnect();
            throw e;
        }
    }

    @Override
    public byte[] readConfig() throws IOException {
        try {
            getOutputStream().write("readConfig()\n".getBytes());
            getOutputStream().flush();
            DataInputStream in = new DataInputStream(getInputStream());
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
            closeConnectionReconnect();
            throw e;
        }
    }


    @Override
    public void updateConfig(byte[] config) throws IOException {
        try {
            InputStream in = getInputStream();
            DataOutputStream out = new DataOutputStream(getOutputStream());

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
            closeConnectionReconnect();
            throw e;
        }
    }

    @Override
    public byte[] getAdditionalHosts(int limit) throws IOException {
        try {
            DataOutputStream out = new DataOutputStream(getOutputStream());
            DataInputStream in = new DataInputStream(getInputStream());

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
            closeConnectionReconnect();
            throw e;
        }
    }

    @Override
    public void updateAdditionalHosts(byte[] bytes) throws IOException {
        try {

            DataOutputStream out = new DataOutputStream(getOutputStream());
            DataInputStream in = new DataInputStream(getInputStream());

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
            closeConnectionReconnect();
            throw e;
        }
    }

    @Override
    public void updateFilter(String entries, boolean filter) throws IOException {
        try {
            OutputStream out = getOutputStream();
            InputStream in = getInputStream();
            out.write(("updateFilter()\n"+entries.replace("\n",";")+"\n"+filter+"\n").getBytes());
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
            closeConnectionReconnect();
            throw e;
        }

    }
    @Override
    public String getVersion() throws IOException {
        return remote_version;
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
       triggerAction("restart()", null);
    }

    @Override
    public void stop() throws IOException {
        triggerAction("stop()", null);
    }

    @Override
   public long[] getFilterStatistics() throws IOException {
        try {
            DataOutputStream out = new DataOutputStream(getOutputStream());
            DataInputStream in = new DataInputStream(getInputStream());
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
            closeConnectionReconnect();
            throw e;
        }
    }

    @Override
    public void triggerUpdateFilter() throws IOException {
        triggerAction("triggerUpdateFilter()", null);
    }

    @Override
    public void doBackup(String name) throws IOException {
        triggerAction("doBackup()", name);
    }

    @Override
    public String[] getAvailableBackups() throws IOException {
        try {
            DataOutputStream out = new DataOutputStream(getOutputStream());
            DataInputStream in = new DataInputStream(getInputStream());
            out.write(("getAvailableBackups()\n").getBytes());
            out.flush();

            String response = Utils.readLineFromStream(in);
            if (!response.equals("OK")) {
                throw new ConfigurationAccessException(response, null);
            }
            try {
                int cnt = Integer.parseInt(Utils.readLineFromStream(in));
                String[] result = new String[cnt];
                for (int i = 0; i < cnt; i++)
                    result[i]=Utils.readLineFromStream(in);

                return result;

            } catch (Exception e) {
                throw new IOException (e);
            }
        } catch (ConfigurationAccessException e) {
            connectedLogger.logLine("Remote action failed! "+e.getMessage());
            throw e;
        } catch (IOException e) {
            connectedLogger.logLine("Remote action  getFilterStatistics() failed! "+e.getMessage());
            closeConnectionReconnect();
            throw e;
        }
    }



    @Override
    public void doRestoreDefaults() throws IOException {
        triggerAction("doRestoreDefaults()", null);
    }

    @Override
    public void doRestore(String name) throws IOException{
        triggerAction("doRestore()", name);
    }

    @Override
    public void wakeLock() throws IOException {
        triggerAction("wakeLock()", null);
    }

    @Override
    public void releaseWakeLock() throws IOException {
        triggerAction("releaseWakeLock()", null);
    }

    private void processHeartBeat() {
        connectedLogger.message("Heart Beat!");
        timeOutCounter=0;
        setTimeout(READ_TIMEOUT);
    }


    private void setTimeout(int timeout) {
        this.timeout = System.currentTimeMillis()+timeout;
        TimoutNotificator.getInstance().register(this);
    }

    @Override
    public void timeoutNotification() {
        timeOutCounter++;
        if (timeOutCounter == 2) {
            connectedLogger.message("Remote Session is Dead!");
            connectedLogger.logLine("Remote Session is Dead! - Closing...!");
            timeOutCounter=0;
            closeConnectionReconnect();
        }
        else {
            //one more chance
            setTimeout(READ_TIMEOUT);
        }
    }

    @Override
    public long getTimoutTime() {
        return timeout;
    }

    /*****************************/
    /* Inner class Remote Stream**/
    /*****************************/

    private class RemoteStream implements Runnable {

        Socket streamCon;
        int streamConId;
        boolean stopped = false;
        DataInputStream in;
        DataOutputStream out;

        public RemoteStream(int ctrlSession) throws IOException {
            Object[] conInfo = initConnection();
            this.streamCon = (Socket) conInfo[1];
            in = new DataInputStream((InputStream) conInfo[2]);
            out = new DataOutputStream((OutputStream) conInfo[3]);
            streamConId = (Integer)conInfo[0];

            try {
                out.write(("attach\n"+ctrlSession+"\n").getBytes());
                out.flush();
                String response = Utils.readLineFromStream(in);
                if (!response.equals("OK")) {
                    throw new IOException(response);
                }
            } catch (IOException e) {
                connectedLogger.logLine("Remote action attach Remote Stream failed! "+e.getMessage());
                closeConnectionReconnect();
                throw e;
            }

            new Thread(this).start();
        }

        @Override
        public void run() {
            byte[] msg = new byte[2048];
            try {
                while (!stopped) {
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
                        case HEART_BEAT:
                            processHeartBeat();
                            confirmHeartBeat();
                            break;
                        default:
                            throw new IOException("Unknown message type: " + type);
                    }

                }
            } catch (Exception e){
                if (!stopped) {
                    connectedLogger.logLine("Exception during RemoteStream read! " + e.toString());
                    closeConnectionReconnect();
                }
            }
        }

        private void confirmHeartBeat() {
            try {
                synchronized(out) {
                    out.write("confirmHeartBeat()\n".getBytes());
                    out.flush();
                }
            } catch (IOException e) {
                connectedLogger.logLine("Exception during confirmHeartBeat()! " + e.toString());
                closeConnectionReconnect();
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

            if (streamCon != null) {
                synchronized (out) {
                    try {
                        out.write("releaseConfiguration()".getBytes());
                        out.flush();
                    } catch (IOException e) {
                        connectedLogger.logLine("Exception during remote configuration release: " + e.toString());
                    }
                    Utils.closeSocket(streamCon);
                }
            }
        }
    }



}
