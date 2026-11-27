package dnsfilter.remote;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import dnsfilter.ConfigurationAccess;
import dnsfilter.DNSFilterManager;
import util.AsyncLogger;
import util.Encryption;
import util.GroupedLogger;
import util.Logger;
import util.LoggerInterface;
import util.SuppressRepeatingsLogger;
import util.TimeoutListener;
import util.TimoutNotificator;
import util.Utils;

public class RemoteAccessServer implements Runnable {

    private static final int MAX_PAYLOAD_SIZE = 16 * 1024 * 1024; // 16 MiB cap
    private static final int MAX_LOG_CHUNK_BYTES = 16384; // fits safely in signed short
    private static final AtomicInteger sessionId = new AtomicInteger(0);

    volatile boolean stopped = false;
    private ServerSocket server;
    private final Map<Integer, RemoteSession> sessions = new ConcurrentHashMap<Integer, RemoteSession>();


    public RemoteAccessServer(int port, String keyphrase) throws IOException{
        Encryption.init_AES(keyphrase);
        server = new ServerSocket(port);
        new Thread (this).start();
        Logger.getLogger().logLine("Started RemoteAccess Server on port "+port);
    }


    private String readStringFromStream(InputStream in, byte[] buf) throws IOException {
       int r = Utils.readLineBytesFromStream(in, buf,true, false);

       if (r == -1)
           throw new EOFException("Stream is closed!");

       return new String(buf,0,r).trim();
    }

    public void invalidate() {
        RemoteSession[] sessionarray = sessions.values().toArray(new RemoteSession[0]);
        for (int i = 0; i < sessionarray.length; i++)
            sessionarray[i].invalidate();
    }

    @Override
    public void run() {
        while (!stopped) {
            Socket con = null;
            boolean handedOff = false;
            try {
                con = server.accept();
                InputStream in = Encryption.getDecryptedStream(con.getInputStream());
                OutputStream out = Encryption.getEncryptedOutputStream(con.getOutputStream(), 1024);

                try {
                    byte[] buf = new byte[1024];
                    String version = readStringFromStream(in, buf);
                    String option = readStringFromStream(in, buf);

                    if (option.equals("new_session")) {
                        //create and start session
                        int newId = sessionId.incrementAndGet();
                        out.write("OK\n".getBytes());
                        out.write((newId+"\n").getBytes());
                        out.write((ConfigurationAccess.getLocal().getVersion() + "\n").getBytes());
                        out.write((ConfigurationAccess.getLocal().getLastDNSAddress() + "\n").getBytes());
                        out.write((ConfigurationAccess.getLocal().openConnectionsCount() + "\n").getBytes());
                        out.flush();
                        new RemoteSession(con, in, out, newId);
                        handedOff = true;
                    }
                    else if (option.equals("reconnect_session")) {
                        int id;
                        try {
                            id = Integer.parseInt(readStringFromStream(in, buf));
                        } catch (Exception e) {
                            throw new IOException(e);
                        }
                        RemoteSession session = sessions.get(id);
                        if (session == null)
                            throw new IOException("Reconnect session not found:"+id);
                        else {
                            session.reconnectSession(con, in, out);
                            out.write("OK\n".getBytes());
                            out.flush();
                            handedOff = true;
                        }
                    }
                    else throw new IOException("Invalid option: "+option);

                } catch (IOException e) {
                    try {
                        out.write(e.toString().getBytes());
                        out.flush();
                    } catch (IOException ignored) {
                        // client may already have disconnected or failed keyphrase check
                    }
                    throw e;
                }
            } catch (IOException e) {
                if (!stopped)
                    Logger.getLogger().logLine("RemoteServerException: "+e.toString());
            } finally {
                if (!handedOff && con != null) {
                    Utils.closeSocket(con);
                }
            }
        }
    }


    public void stop() {
        stopped = true;
        RemoteSession[] remoteSessions = sessions.values().toArray(new RemoteSession[0]);
        for (int i = 0; i < remoteSessions.length; i++ )
            remoteSessions[i].killSession();
        try {
            server.close();
        } catch (IOException e) {
            Logger.getLogger().logException(e);
        }
    }


    /*********************************************************/
    /*********** Inner class RemoteSession *******************/
    /*********************************************************/
    private class RemoteSession implements Runnable, TimeoutListener {

        int id;
        int connectedSessionId = -1; //connected Control Session in case of RemoteStream session
        Socket socket;
        SuppressRepeatingsLogger remoteLogger;
        boolean killed = false;
        boolean doReconnect = false;
        DataOutputStream out;
        DataInputStream in;
        boolean remoteStreamSession = false;

        long timeout = Long.MAX_VALUE; //heartbeat timeout for dead session detection
        long lastHeartBeatConfirm = System.currentTimeMillis();//last confirmed heartbeat


        private RemoteSession(Socket con, InputStream in, OutputStream out, int id) throws IOException{
            this.id = id;
            this.socket = con;
            this.out = new DataOutputStream(out);
            this.in = new DataInputStream(in);
            sessions.put(id, this);
            Logger.getLogger().logLine("New Remote Session "+id+" from :"+con);
            new Thread(this).start();
        }

        public void killSession(){
            if (killed)
                return;
            killed = true;
            TimoutNotificator.getInstance().unregister(this);

            if (remoteLogger!= null) {
                remoteLogger.closeLogger();
                ((GroupedLogger) Logger.getLogger()).detachLogger(remoteLogger);
            }

            Utils.closeSocket(socket);
            sessions.remove(id);

            if (connectedSessionId != -1) {
                //kill also connected Control Session
                RemoteSession connectedSession = (RemoteSession)sessions.get(connectedSessionId);
                if (connectedSession != null) //could already have been closed!
                    connectedSession.killSession();
            }
        }

        public synchronized void reconnectSession(Socket con, InputStream in, OutputStream out) throws IOException{
           doReconnect = true;
           Socket old = this.socket;
           this.socket = con;
           this.out = new DataOutputStream(out);
           this.in = new DataInputStream(in);
           Utils.closeSocket(old);
        }

        private byte[] readBoundedBytes(DataInputStream in) throws IOException {
            int len = in.readInt();
            if (len < 0 || len > MAX_PAYLOAD_SIZE)
                throw new ConfigurationAccess.ConfigurationAccessException("Payload length out of bounds: " + len, null);
            byte[] buf = new byte[len];
            in.readFully(buf);
            return buf;
        }

        @Override
        public void run() {
            byte[] buf = new byte[1024];
            String action="";
            while (!killed) {
                try {
                    action = readStringFromStream(in, buf);

                    if (action.equals("attach"))
                        attachStream();
                    else if (action.equals("releaseConfiguration()"))
                        killSession();
                    else if (action.equals("confirmHeartBeat()"))
                        heartBeatConfirmed();
                    else executeAction(action);
                } catch (ConfigurationAccess.ConfigurationAccessException e) {
                    Logger.getLogger().logLine("RemoteServer Exception processing "+action+"! " + e.toString());
                } catch (IOException e) {
                    if (!doReconnect) {
                        if (!killed) {
                            Logger.getLogger().logLine("Exception during RemoteServer Session read! " + e.toString());
                            killSession();
                            break;
                        }
                    } else {
                        Logger.getLogger().logLine("Reconnected Remote!");
                        doReconnect=false;
                    }
                }
            }
            Logger.getLogger().logLine("Remote Session "+id+" closed! "+socket);
        }

        private void executeAction(String action) throws IOException {

            try {

                if (action.equals("getConfig()")) {
                    Properties config = ConfigurationAccess.getLocal().getConfig();
                    out.write("OK\n".getBytes());
                    ObjectOutputStream objout = new ObjectOutputStream(out);
                    objout.writeObject(config);
                    objout.flush();
                }
                else if (action.equals("getDefaultConfig()")) {
                    Properties config = ConfigurationAccess.getLocal().getDefaultConfig();
                    out.write("OK\n".getBytes());
                    ObjectOutputStream objout = new ObjectOutputStream(out);
                    objout.writeObject(config);
                    objout.flush();
                }
                else if (action.equals("readConfig()")) {
                    byte[] result = ConfigurationAccess.getLocal().readConfig();
                    out.write("OK\n".getBytes());
                    out.writeInt(result.length);
                    out.write(result);
                    out.flush();
                } else if (action.equals("updateConfig()")) {
                    byte[] cfg = readBoundedBytes(in);
                    ConfigurationAccess.getLocal().updateConfig(cfg);
                    out.write("OK\n".getBytes());
                    out.flush();
                } else if (action.equals("updateConfigMergeDefaults()")) {
                    byte[] cfg = readBoundedBytes(in);
                    ConfigurationAccess.getLocal().updateConfigMergeDefaults(cfg);
                    out.write("OK\n".getBytes());
                    out.flush();
                } else if (action.equals("getAdditionalHosts()")) {
                    int limit = in.readInt();
                    byte[] result = ConfigurationAccess.getLocal().getAdditionalHosts(limit);
                    if (result == null)
                        throw new ConfigurationAccess.ConfigurationAccessException("additionalHosts exceeds limit (" + limit + " bytes)!", null);
                    out.write("OK\n".getBytes());
                    out.writeInt(result.length);
                    out.write(result);
                    out.flush();
                } else if (action.equals("updateAdditionalHosts()")) {
                    byte[] cfg = readBoundedBytes(in);
                    ConfigurationAccess.getLocal().updateAdditionalHosts(cfg);
                    out.write("OK\n".getBytes());
                    out.flush();
                } else if (action.equals("updateFilter()")) {
                    String entries = Utils.readLineFromStream(in).replace(";","\n");
                    boolean filter = Boolean.parseBoolean(Utils.readLineFromStream(in));
                    ConfigurationAccess.getLocal().updateFilter(entries, filter);
                    out.write("OK\n".getBytes());
                    out.flush();
                } else if (action.equals("restart()")) {
                    ConfigurationAccess.getLocal().restart();
                    out.write("OK\n".getBytes());
                    out.flush();
                } else if (action.equals("stop()")) {
                    ConfigurationAccess.getLocal().stop();
                    out.write("OK\n".getBytes());
                    out.flush();
                } else if (action.equals("getFilterStatistics()")) {
                    long[] result = ConfigurationAccess.getLocal().getFilterStatistics();
                    out.write("OK\n".getBytes());
                    out.writeLong(result[0]);
                    out.writeLong(result[1]);
                    out.flush();
                } else if (action.equals("triggerUpdateFilter()")) {
                    ConfigurationAccess.getLocal().triggerUpdateFilter();
                    out.write("OK\n".getBytes());
                    out.flush();
                } else if (action.equals("doBackup()")) {
                    ByteArrayOutputStream backupout = new ByteArrayOutputStream();
                    ConfigurationAccess.getLocal().doBackup(backupout);
                    out.write("OK\n".getBytes());
                    byte[] backup = backupout.toByteArray();
                    out.writeInt(backup.length);
                    out.write(backup);
                    out.flush();
                } else if (action.equals("doRestore()")) {
                    byte[] input = readBoundedBytes(in);
                    ConfigurationAccess.getLocal().doRestore(new ByteArrayInputStream(input));
                    out.write("OK\n".getBytes());
                    out.flush();
                } else if (action.equals("doRestoreDefaults()")) {
                    ConfigurationAccess.getLocal().doRestoreDefaults();
                    out.write("OK\n".getBytes());
                    out.flush();
                } else if (action.equals("wakeLock()")) {
                    ConfigurationAccess.getLocal().wakeLock();
                    out.write("OK\n".getBytes());
                    out.flush();
                } else if (action.equals("releaseWakeLock()")) {
                    ConfigurationAccess.getLocal().releaseWakeLock();
                    out.write("OK\n".getBytes());
                    out.flush();
                } else
                    throw new ConfigurationAccess.ConfigurationAccessException("Unknown action: " + action);

            } catch (ConfigurationAccess.ConfigurationAccessException e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                out.write((msg.replace("\n", "\t") + "\n").getBytes());
                out.flush();
            }
        }

        private void attachStream() throws IOException{

            try {
                //read the ID of the corresponding control session
                connectedSessionId = Integer.parseInt(Utils.readLineFromStream(in));
                remoteStreamSession = true;
            } catch (Exception e){
                throw new IOException(e);
            }



            remoteLogger = new SuppressRepeatingsLogger(new AsyncLogger(new LoggerInterface() {

                public void sendLog(int type, String txt) {
                    synchronized (out) {
                        try {

                            //info about open connections
                            out.writeShort(RemoteAccessClient.UPD_CON_CNT);
                            byte[] msg = (ConfigurationAccess.getLocal().openConnectionsCount() + "").getBytes();
                            out.writeShort(msg.length);
                            out.write(msg);

                            //last DNS
                            out.writeShort(RemoteAccessClient.UPD_DNS);
                            msg = (ConfigurationAccess.getLocal().getLastDNSAddress()).getBytes();
                            out.writeShort(msg.length);
                            out.write(msg);

                            //the log (chunked so each packet length stays strictly within signed short bounds)
                            byte[] fullMsg = txt.getBytes();
                            if (fullMsg.length == 0) {
                                out.writeShort(type);
                                out.writeShort(0);
                            } else {
                                int offset = 0;
                                while (offset < fullMsg.length) {
                                    int chunkLen = Math.min(MAX_LOG_CHUNK_BYTES, fullMsg.length - offset);
                                    out.writeShort(type);
                                    out.writeShort(chunkLen);
                                    out.write(fullMsg, offset, chunkLen);
                                    offset += chunkLen;
                                }
                            }
                            out.flush();

                        } catch (IOException e) {
                            killSession();
                            Logger.getLogger().logLine("Exception during remote logging! " + e.toString());
                        }
                    }
                }

                @Override
                public void logLine(String txt) {
                    sendLog(RemoteAccessClient.LOG_LN, txt);
                }

                @Override
                public void logException(Exception e) {
                    StringWriter str = new StringWriter();
                    e.printStackTrace(new PrintWriter(str));
                    log(str.toString()+"\n");
                }

                @Override
                public void log(String txt) {
                    sendLog(RemoteAccessClient.LOG, txt);

                }

                @Override
                public void message(String txt) {
                    sendLog(RemoteAccessClient.LOG_MSG, txt);

                }

                @Override
                public void closeLogger() {

                }
            }));

            try {
                long repeatingLogSuppressTime = Long.parseLong(DNSFilterManager.getInstance().getConfig().getProperty("repeatingLogSuppressTime", "1000"));
                remoteLogger.setSuppressTime(repeatingLogSuppressTime);
                boolean liveLogTimestampEnabled = Boolean.parseBoolean(DNSFilterManager.getInstance().getConfig().getProperty("addLiveLogTimestamp", "false"));
                remoteLogger.setTimestampFormat(null);
                if (liveLogTimestampEnabled) {
                    String timeStampPattern = DNSFilterManager.getInstance().getConfig().getProperty("liveLogTimeStampFormat", "hh:mm:ss");
                    remoteLogger.setTimestampFormat(timeStampPattern);
                }
            } catch (Exception e) {
                throw new IOException(e);
            }

            synchronized (out) {
                try {
                    ((GroupedLogger) Logger.getLogger()).attachLogger(remoteLogger);
                } catch (ClassCastException cce) {
                    //need GroupedLogger!
                    GroupedLogger logger = new GroupedLogger(new LoggerInterface[] {Logger.getLogger(), remoteLogger});
                    Logger.setLogger(logger);
                }

                out.write("OK\n".getBytes());
                doHeartBeat(RemoteAccessClient.READ_TIMEOUT);
                //will also flush!
            }
        }

        private void doHeartBeat(int timeout) {
            try {
                synchronized (out) {
                    out.writeShort(RemoteAccessClient.HEART_BEAT);
                    out.writeShort(0); //0 length message
                    out.flush();
                }
                this.timeout = System.currentTimeMillis() + timeout;
                //register next beat
                TimoutNotificator.getInstance().register(this);
            } catch (IOException e) {
                Logger.getLogger().logLine("Heartbeat failed! " + e);
                killSession();
            }
        }

        private void invalidate() {
           if (!remoteStreamSession)
              return; // the control session not the back channel for log and other events
           try {
                synchronized (out) {
                    out.writeShort(RemoteAccessClient.INVALIDATE);
                    out.writeShort(0); //0 length message
                    out.flush();
                }
            } catch (IOException e) {
                Logger.getLogger().logLine("Invalidation failed! " + e);
           }
        }

        private void heartBeatConfirmed() {
            lastHeartBeatConfirm = System.currentTimeMillis();
        }

        private boolean checkLastConfirmedHeartBeat() {
            long delta = System.currentTimeMillis() - lastHeartBeatConfirm;
            if (delta > 2*RemoteAccessClient.READ_TIMEOUT) {
                Logger.getLogger().logLine("Heartbeat Confirmation not received - Dead Session!");
                killSession();

                return false;
            }
            else return true;
        }

        @Override
        public void timeoutNotification() {
            if (checkLastConfirmedHeartBeat())
                doHeartBeat(RemoteAccessClient.READ_TIMEOUT);
        }

        @Override
        public long getTimoutTime() {
            return timeout;
        }
    }

    /*********** end of inner class RemoteSession *******************/

}
