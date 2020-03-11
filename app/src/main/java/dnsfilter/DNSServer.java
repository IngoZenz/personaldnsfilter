package dnsfilter;

/* PersonalDNSFilter 1.5
   Copyright (C) 2019 Ingo Zenz

   This program is free software; you can redistribute it and/or
   modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation; either version 2
   of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

   Find the latest version at http://www.zenz-solutions.de/personaldnsfilter
   Contact:i.z@gmx.net
 */

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;

import util.Logger;
import util.conpool.Connection;
import util.http.HttpHeader;

public class DNSServer {

    protected InetSocketAddress address;
    protected int timeout;
    private static int bufSize=1024;
    private static int maxBufSize= -1; //will be initialized on request
    public static final int UDP = 0; //Via UDP
    public static final int TCP = 1; //Via TCP
    public static final int DOT = 2; // DNS over TLS
    public static final int DOH = 3; // DNS of HTTPS

    private static DNSServer INSTANCE = new DNSServer(null,0,0);

    static {
        Connection.setPoolTimeoutSeconds(30);
    }

    public static int getProtoFromString(String s) throws IOException{
        s = s.toUpperCase();
        if (s.equals("UDP"))
            return UDP;
        else if (s.equals("TCP"))
            return TCP;
        else if (s.equals("DOT"))
            return DOT;
        else if (s.equals("DOH"))
            return DOH;
        else throw new IOException("Invalid Protocol: "+s);
    }



    protected DNSServer (InetAddress address, int port, int timeout){
        this.address = new InetSocketAddress(address, port);
        this.timeout = timeout;
    }

    public static DNSServer getInstance(){
        return INSTANCE;
    }

    public static int getBufSize() {return bufSize;}

    public DNSServer createDNSServer(int protocol, InetAddress address, int port, int timeout, String endPoint) throws IOException {
        switch (protocol) {
            case UDP: return new UDP(address, port, timeout);
            case TCP: return new TCP(address, port, timeout, false, endPoint);
            case DOT: return new TCP(address, port, timeout,true, endPoint);
            case DOH: return new DoH(address, port, timeout, endPoint);
            default: throw new IllegalArgumentException("Invalid protocol:"+protocol);
        }
    }

    public DNSServer createDNSServer(String spec, int timeout) throws IOException{

        String ip = null;

        if (spec.startsWith("[")) { //IPV6
            int idx = spec.indexOf("]");
            if (idx != -1) {
                ip = spec.substring(1,idx);
                spec = spec.substring(idx);
            }
        } else { // Check if String is just IP without brackets for backward compatibility
            String specUpper = spec.toUpperCase();
            if (specUpper.indexOf("::UDP") == -1 && specUpper.indexOf("::DOT") == -1 && specUpper.indexOf("::DOH") == -1 ) {
                ip = spec; //just the ip String
                spec = "";
            }
        }

        String[] entryTokens  = spec.split("::");

        if (ip == null)
            ip = entryTokens[0];
        
        int port = 53;
        if (entryTokens.length>1) {
            try {
                port = Integer.parseInt(entryTokens[1]);
            } catch (NumberFormatException nfe) {
                throw new IOException("Invalid Port!", nfe);
            }
        }
        int proto = DNSServer.UDP;
        if (entryTokens.length>2)
            proto = DNSServer.getProtoFromString(entryTokens[2]);

        String endPoint = null;
        if (entryTokens.length>3)
            endPoint = entryTokens[3];

        return getInstance().createDNSServer(proto,InetAddress.getByName(ip),port,timeout,endPoint);
    }

    public InetAddress getAddress() {
        return address.getAddress();
    }

    public int getPort() {
        return address.getPort();
    }

    public String getProtocolName(){return "";}


    @Override
    public String toString() {
        return "["+address.getAddress().getHostAddress()+"]::"+address.getPort()+"::"+getProtocolName();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || ! (obj.getClass().equals(this.getClass())))
            return false;

       return address.equals(((DNSServer) obj).address);
    }

    protected void readResponseFromStream(DataInputStream in, int size, DatagramPacket response) throws IOException {

        if (size + response.getOffset() > response.getData().length) { //existing buffer does not fit
            synchronized (DNSServer.class) {
                //Write access to static data, synchronization against the class is needed.
                //Could be optimized in future by setting the buf size per DNSServer Instance and not static.
                //However at the time the buffer is created, it is not known what will be the DNSServer used, because it be might be switched.
                if (maxBufSize == -1) {
                    //load maxBufSize config
                    try {
                        maxBufSize = Integer.parseInt(ConfigurationAccess.getLocal().getConfig().getProperty("MTU","3000"));
                    } catch (IOException eio) {
                        throw eio;
                    } catch (Exception e){
                        throw new IOException(e);
                    }
                }

                if (size + response.getOffset() < maxBufSize && bufSize < size+response.getOffset()) { //resize for future requests
                    bufSize = Math.min(1024*(((size +response.getOffset()) / 1024) +1), maxBufSize);
                    Logger.getLogger().logLine("BUFFER RESIZE:"+bufSize);
                } else if (size + response.getOffset() >= maxBufSize ) throw new IOException("Max Response Buffer to small for response of length " + size);

                response.setData(new byte[bufSize],response.getOffset(),bufSize-response.getOffset());
            }
        }
        in.readFully(response.getData(), response.getOffset(),size);
        response.setLength(size);
    }

    public void resolve(DatagramPacket request, DatagramPacket response) throws IOException {}


}

class UDP extends DNSServer {

    protected UDP(InetAddress address, int port, int timeout) {
        super(address, port, timeout);
    }

    @Override
    public String getProtocolName(){return "UDP";}

    @Override
    public void resolve(DatagramPacket request, DatagramPacket response) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        try {
            request.setSocketAddress(address);
            socket.setSoTimeout(timeout);
            try {
                socket.send(request);
            } catch (IOException eio) {
                throw new IOException("Cannot reach " + address + "!" + eio.getMessage());
            }
            try {
                socket.receive(response);
            } catch (IOException eio) {
                throw new IOException("No DNS Response from " + address);
            }
        } finally {
            socket.close();
        }
    }
}

class TCP extends DNSServer {
    boolean ssl;

    protected TCP(InetAddress address, int port, int timeout, boolean ssl, String hostName) throws IOException {
        super(address, port, timeout);
        this.ssl = ssl;

        if (hostName != null) {
            if (hostName.indexOf("://")!= -1)
                throw new IOException("Invalid hostname specified for "+getProtocolName()+": "+hostName);

            this.address = new InetSocketAddress(InetAddress.getByAddress(hostName, address.getAddress()), port);
        }
    }

    @Override
    public String getProtocolName(){
        if (ssl) return "DOT";
        else return "TCP";
    }

    @Override
    public void resolve(DatagramPacket request, DatagramPacket response) throws IOException {
        for (int i = 0; i<2; i++) { //retry once in case of EOFException (pooled connection was already closed)
            Connection con = Connection.connect(address, timeout, ssl, null, Proxy.NO_PROXY);
            con.setSoTimeout(timeout);
            try {
                DataInputStream in = new DataInputStream(con.getInputStream());
                DataOutputStream out = new DataOutputStream(con.getOutputStream());
                out.writeShort(request.getLength());
                out.write(request.getData(), request.getOffset(), request.getLength());
                out.flush();
                int size = in.readShort();
                readResponseFromStream(in, size, response);
                response.setSocketAddress(address);
                con.release(true);
                return;
            } catch (EOFException eof) {
                con.release(false);
                if (i == 1)
                    throw new IOException ("EOF when reading from "+this.toString(),eof); // retried already once, now throw exception
            } catch (IOException eio) {
                con.release(false);
                throw eio;
            }
        }
    }
}

class DoH extends DNSServer {

    String url;
    String urlHost;
    String reqTemplate;
    InetSocketAddress urlHostAddress;

    protected DoH(InetAddress address, int port, int timeout, String url) throws IOException {
        super(address, port, timeout);

        if (url== null)
            throw new IOException ("Endpoint URL not defined for DNS over HTTPS (DoH)!");

        this.url= url;
        buildTemplate();
        urlHostAddress = new InetSocketAddress(InetAddress.getByAddress(urlHost, address.getAddress()), port);
    }

    @Override
    public String getProtocolName(){return "DOH";}

    private void buildTemplate() throws IOException {
        String user_agent= "Mozilla/5.0 ("+System.getProperty("os.name")+"; "+System.getProperty("os.version")+")";
        HttpHeader REQ_TEMPLATE = new HttpHeader(HttpHeader.REQUEST_HEADER);
        REQ_TEMPLATE.setValue("User-Agent", user_agent);
        REQ_TEMPLATE.setValue("Accept", "application/dns-message");
        REQ_TEMPLATE.setValue("content-type", "application/dns-message");
        REQ_TEMPLATE.setValue("Connection", "keep-alive");
        REQ_TEMPLATE.setRequest("POST "+url+" "+"HTTP/1.1");
        REQ_TEMPLATE.setValue("Content-Length","999");

        reqTemplate = REQ_TEMPLATE.getServerRequestHeader(false);
        urlHost = REQ_TEMPLATE.remote_host_name;
    }

    private byte[] buildRequestHeader(int length) throws IOException {
       return reqTemplate.replace("\nContent-Length: 999","\nContent-Length: "+length).getBytes();
    }

    @Override
    public void resolve(DatagramPacket request, DatagramPacket response) throws IOException {

        byte[] reqHeader = buildRequestHeader(request.getLength());

        for (int i = 0; i<2; i++) { //retry once in case of EOFException (pooled connection was already closed)
            Connection con = Connection.connect(urlHostAddress, timeout, true, null, Proxy.NO_PROXY);
            try {
                OutputStream out = con.getOutputStream();
                DataInputStream in = new DataInputStream(con.getInputStream());
                out.write(reqHeader);
                out.write(request.getData(), request.getOffset(), request.getLength());
                out.flush();
                HttpHeader responseHeader = new HttpHeader(in, HttpHeader.RESPONSE_HEADER);
                if (responseHeader.getResponseCode() != 200)
                    throw new IOException("DoH failed for " + url + "! " + responseHeader.getResponseCode() + " - " + responseHeader.getResponseMessage());

                int size = (int) responseHeader.getContentLength();
                readResponseFromStream(in, size, response);
                response.setSocketAddress(address);
                con.release(true);
                return;
            } catch (EOFException eof) {
                con.release(false);
                if (i == 1)
                    throw new IOException ("EOF when reading from "+this.toString(),eof); // retried already once, now throw exception
            } catch (IOException eio) {
                con.release(false);
                throw eio;
            }
        }
    }
}


