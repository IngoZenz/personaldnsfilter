package util.http;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import util.ExecutionEnvironment;
import util.Logger;
import util.conpool.Connection;
import util.conpool.HttpProxy;

import javax.net.ssl.SSLParameters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DOHHttp2Util {

    // ---- HPACK helpers (request side, simplified) ----

    static byte[] hpackIndexed(int index) {
        // Indexed Header Field Representation: 1xxxxxxx
        return new byte[]{(byte) (0x80 | (index & 0x7F))};
    }

    static byte[] hpackLiteral(String name, String value) throws IOException {
        // Literal Header Field with Incremental Indexing, literal name.
        // We keep it simple, non-Huffman, single-byte length (<=127).
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        byte[] valueBytes = value.getBytes(StandardCharsets.US_ASCII);

        if (nameBytes.length > 127 || valueBytes.length > 127) {
            throw new IOException("Header too long for simplified HPACK literal");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // 0x40 => Literal with incremental indexing, literal name (index = 0)
        out.write(0x40);

        // Name length: 7-bit prefix, no Huffman, length < 127
        out.write(nameBytes.length & 0x7F);
        out.write(nameBytes);

        // Value length: same logic
        out.write(valueBytes.length & 0x7F);
        out.write(valueBytes);

        return out.toByteArray();
    }

    static Integer hpackIndexedStatus(int index) {
        switch (index) {
            case 8:
                return 200;
            case 9:
                return 204;
            case 10:
                return 206;
            case 11:
                return 304;
            case 12:
                return 400;
            case 13:
                return 404;
            case 14:
                return 500;
            default:
                return null;
        }
    }

    /**
     * Very conservative HPACK header block parsing:
     * - Looks for indexed headers and maps indices 8-14 to status codes.
     * - Falls back to scanning ASCII for ":status" and digits.
     */
    static int parseHeadersStatus(byte[] block) {
        int status = -1;

        if (block == null || block.length == 0) {
            return status;
        }

        // 1) Conservative indexed-header-only logic
        int p = 0;
        while (p < block.length) {
            int b = block[p] & 0xFF;

            // Indexed Header Field (1xxxxxxx)
            if ((b & 0x80) != 0) {
                int index = b & 0x7F;
                Integer s = hpackIndexedStatus(index);
                if (s != null) {
                    status = s;
                }
                p++; // we only support small single-byte indices
                continue;
            }

            // Any other pattern: stop interpreting as HPACK
            break;
        }

        if (status != -1) {
            return status;
        }

        // 2) Fallback: scan raw bytes for ":status" and parse digits after it (ASCII only)
        String ascii = new String(block, StandardCharsets.US_ASCII);
        int idx = ascii.indexOf(":status");
        if (idx >= 0) {
            int i = idx + 7;
            while (i < ascii.length() && ascii.charAt(i) <= ' ') i++;
            int start = i;
            while (i < ascii.length() && ascii.charAt(i) >= '0' && ascii.charAt(i) <= '9') i++;
            if (start < i) {
                try {
                    status = Integer.parseInt(ascii.substring(start, i));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return status;
    }

    // ---- DNS wire-format helpers ----

    static byte[] buildDnsQuery(String qname, int qtype) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Header: ID(2), Flags(2), QDCOUNT(2), ANCOUNT(2), NSCOUNT(2), ARCOUNT(2)
        writeU16(out, 0x1234);   // ID
        writeU16(out, 0x0100);   // Flags: standard query, RD=1
        writeU16(out, 1);        // QDCOUNT
        writeU16(out, 0);        // ANCOUNT
        writeU16(out, 0);        // NSCOUNT
        writeU16(out, 0);        // ARCOUNT
        // QNAME
        for (String label : qname.split("\\.")) {
            byte[] lb = label.getBytes(StandardCharsets.US_ASCII);
            out.write(lb.length);
            out.write(lb);
        }
        out.write(0x00);         // root label
        // QTYPE, QCLASS=IN(1)
        writeU16(out, qtype);
        writeU16(out, 1);
        return out.toByteArray();
    }

    static void writeU16(ByteArrayOutputStream out, int v) {
        out.write((v >> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    static int readU16(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    static class DnsAnswer {
        String name;
        int type;
        int clazz;
        int ttl;
        byte[] rdata;

        @Override
        public String toString() {
            if (type == 1 && rdata != null && rdata.length == 4) { // A
                return name + " A " + (rdata[0] & 0xFF) + "." + (rdata[1] & 0xFF) + "." +
                        (rdata[2] & 0xFF) + "." + (rdata[3] & 0xFF) + " TTL=" + ttl;
            }
            if (type == 28 && rdata != null && rdata.length == 16) { // AAAA
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 16; i += 2) {
                    int seg = ((rdata[i] & 0xFF) << 8) | (rdata[i + 1] & 0xFF);
                    sb.append(Integer.toHexString(seg));
                    if (i < 14) sb.append(':');
                }
                return name + " AAAA " + sb + " TTL=" + ttl;
            }
            return name + " TYPE=" + type + " RDLEN=" + (rdata == null ? 0 : rdata.length) + " TTL=" + ttl;
        }
    }

    static String readName(byte[] msg, int[] offRef) {
        int off = offRef[0];
        StringBuilder sb = new StringBuilder();
        int jumpedOff = -1;
        boolean jumped = false;
        while (true) {
            int len = msg[off] & 0xFF;
            if ((len & 0xC0) == 0xC0) {
                // pointer
                int ptr = ((len & 0x3F) << 8) | (msg[off + 1] & 0xFF);
                if (!jumped) {
                    jumpedOff = off + 2;
                    jumped = true;
                }
                off = ptr;
                continue;
            }
            off++;
            if (len == 0) {
                break;
            }
            if (sb.length() > 0) sb.append('.');
            for (int i = 0; i < len; i++) {
                sb.append((char) (msg[off + i] & 0xFF));
            }
            off += len;
        }
        offRef[0] = jumped ? jumpedOff : off;
        return sb.toString();
    }

    static List<DnsAnswer> parseDnsResponse(byte[] msg) {
        List<DnsAnswer> answers = new ArrayList<>();

        if (msg == null || msg.length < 12) {
            Logger.getLogger().logLine("DNS: response too short: " +
                    (msg == null ? "null" : msg.length + " bytes"));
            return answers;
        }

        int off = 0;

        int id = readU16(msg, off);
        off += 2;
        int flags = readU16(msg, off);
        off += 2;
        int qd = readU16(msg, off);
        off += 2;
        int an = readU16(msg, off);
        off += 2;
        int ns = readU16(msg, off);
        off += 2;
        int ar = readU16(msg, off);
        off += 2;

        int rcode = flags & 0x000F;

        if (an == 0) {
            Logger.getLogger().logLine(
                    "DNS: no answers. " +
                            "ID=" + id +
                            " QD=" + qd +
                            " AN=" + an +
                            " NS=" + ns +
                            " AR=" + ar +
                            " RCODE=" + rcode
            );
        }

        // Skip questions
        for (int i = 0; i < qd; i++) {
            int[] o = new int[]{off};
            String qname = readName(msg, o);
            off = o[0];

            if (off + 4 > msg.length) {
                Logger.getLogger().logLine("DNS: truncated question section");
                return answers;
            }

            int qtype = readU16(msg, off);
            off += 2;
            int qclass = readU16(msg, off);
            off += 2;
        }

        // Parse answers
        for (int i = 0; i < an; i++) {
            int[] o = new int[]{off};
            String name = readName(msg, o);
            off = o[0];

            if (off + 10 > msg.length) {
                Logger.getLogger().logLine("DNS: truncated answer header");
                return answers;
            }

            int type = readU16(msg, off);
            off += 2;
            int clazz = readU16(msg, off);
            off += 2;

            int ttl = ((msg[off] & 0xFF) << 24) |
                    ((msg[off + 1] & 0xFF) << 16) |
                    ((msg[off + 2] & 0xFF) << 8) |
                    (msg[off + 3] & 0xFF);
            off += 4;

            int rdlen = readU16(msg, off);
            off += 2;

            if (off + rdlen > msg.length) {
                Logger.getLogger().logLine("DNS: truncated RDATA (expected " + rdlen +
                        " bytes, have " + (msg.length - off) + ")");
                rdlen = Math.max(0, msg.length - off);
            }

            byte[] rdata = new byte[rdlen];
            System.arraycopy(msg, off, rdata, 0, rdlen);
            off += rdlen;

            DnsAnswer a = new DnsAnswer();
            a.name = name;
            a.type = type;
            a.clazz = clazz;
            a.ttl = ttl;
            a.rdata = rdata;

            answers.add(a);
        }

        return answers;
    }

    // ---- HTTP/2 framing helpers ----

    static void writeFrameHeader(ByteArrayOutputStream out, int length, int type, int flags, int streamId) {
        out.write((length >> 16) & 0xFF);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.write(type & 0xFF);
        out.write(flags & 0xFF);
        out.write((streamId >> 24) & 0x7F); // clear MSB (R bit)
        out.write((streamId >> 16) & 0xFF);
        out.write((streamId >> 8) & 0xFF);
        out.write(streamId & 0xFF);
    }

    static void readFully(InputStream in, byte[] buf, int len) throws IOException {
        int off = 0;
        while (off < len) {
            int r = in.read(buf, off, len - off);
            if (r == -1)
                throw new IOException("Unexpected end of stream!");
            off += r;
        }
    }

    // ---- Connection bootstrap (once) ----

    public static SSLSocket openHttp2Socket(InetSocketAddress sadr, int timeout, Proxy proxy) throws IOException {
        Socket socket = null;
        try {
            SSLContext sslContext = SSLContext.getDefault();
            if (proxy == Proxy.NO_PROXY) {
                socket = SocketChannel.open().socket();
                ExecutionEnvironment.getEnvironment().protectSocket(socket, 0);
                socket.connect(sadr, timeout);
            } else {
                if (!(proxy instanceof HttpProxy))
                    throw new IOException("Only " + HttpProxy.class.getName() + " supported for creating connection over tunnel!");
                socket = ((HttpProxy) proxy).openTunnel(sadr, timeout, true);
            }
            
            socket.setSoTimeout(timeout);

            SSLSocket sslsocket = (SSLSocket) sslContext.getSocketFactory()
                    .createSocket(socket, sadr.getHostName(), sadr.getPort(), true);
            SSLParameters params = sslsocket.getSSLParameters();
            params.setApplicationProtocols(new String[]{"h2"});
            sslsocket.setSSLParameters(params);

            sslsocket.startHandshake();
            String negotiated = sslsocket.getApplicationProtocol();
            if (!"h2".equals(negotiated)) {
                throw new IOException("HTTP/2 not negotiated; got: " + negotiated);
            }

            OutputStream out = sslsocket.getOutputStream();
            InputStream in = sslsocket.getInputStream();

            // Client preface
            out.write("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII));

            // SETTINGS (empty)
            out.write(new byte[]{
                    0x00, 0x00, 0x00, // length
                    0x04,           // type = SETTINGS
                    0x00,           // flags
                    0x00, 0x00, 0x00, 0x00 // stream id
            });
            out.flush();

            // Read initial server SETTINGS and ACK them
            boolean acked = false;
            for (int i = 0; i < 8 && !acked; i++) {
                byte[] header = new byte[9];
                readFully(in, header, 9);
                int flen = ((header[0] & 0xFF) << 16) | ((header[1] & 0xFF) << 8) | (header[2] & 0xFF);
                int ftype = header[3] & 0xFF;
                int fflags = header[4] & 0xFF;
                int streamId = ((header[5] & 0x7F) << 24) | ((header[6] & 0xFF) << 16)
                        | ((header[7] & 0xFF) << 8) | (header[8] & 0xFF);
                if (flen > 0) {
                    byte[] payload = new byte[flen];
                    readFully(in, payload, flen);
                }
                if (streamId == 0 && ftype == 0x04 && (fflags & 0x01) == 0) {
                    // Send SETTINGS ACK
                    ByteArrayOutputStream ack = new ByteArrayOutputStream();
                    writeFrameHeader(ack, 0, 0x04, 0x01, 0);
                    out.write(ack.toByteArray());
                    out.flush();
                    acked = true;
                }
            }
            return sslsocket;
        } catch (IOException eio) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e2) {
                    // ignore
                }
            }
            throw eio;
        } catch (Exception e) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e2) {
                    // ignore
                }
            }
            throw new IOException(e.getMessage(), e);
        }
    }

    // ---- Send a single DNS query over HTTP/2 and return raw DNS response ----


    public static byte[] sendDnsQuery(InetSocketAddress sadr, String path,
                                      byte[] dnsQuery, int offs, int length,
                                      int timeout, Proxy proxy) throws IOException {

        return sendDnsQuery(sadr, path, dnsQuery, offs, length, timeout, 0, proxy);
    }

    static boolean isValidDnsResponse(byte[] resp) {
        // Must be at least DNS header size
        if (resp == null || resp.length < 12) {
            return false;
        }

        // Extract DNS header fields
        int flags   = ((resp[2] & 0xFF) << 8) | (resp[3] & 0xFF);
        int qdcount = ((resp[4] & 0xFF) << 8) | (resp[5] & 0xFF);
        int ancount = ((resp[6] & 0xFF) << 8) | (resp[7] & 0xFF);

        // QR bit must be 1 (response)
        boolean qr = (flags & 0x8000) != 0;
        if (!qr) return false;

        // Opcode must be 0 (standard query)
        int opcode = (flags >> 11) & 0xF;
        if (opcode != 0) return false;

        // QDCOUNT must be >= 1 (DoH always echoes the question)
        if (qdcount < 1 || qdcount > 5) return false;

        // ANCOUNT must be reasonable (0–20 is typical)
        if (ancount < 0 || ancount > 50) return false;

        // If we reach here, the header looks sane
        return true;
    }


    private static int MAX_RETRY = 4;

    private static byte[] sendDnsQuery(InetSocketAddress sadr, String path,
                                       byte[] dnsQuery, int offs, int length,
                                       int timeout, int retryCnt, Proxy proxy) throws IOException {
        Connection con = null;
        try {
            con = Connection.connect(sadr, timeout, true, null, proxy, true);
            if (retryCnt > 0) //retry
                if (!con.isFresh()) con.refreshConnection(); //ensure fresh connection is used

            con.setSoTimeout(timeout);

            OutputStream out = con.getOutputStream();
            InputStream in = con.getInputStream();

            int streamId = con.getHttp2StreamID();
            //Logger.getLogger().logLine("*STREAMID:" + streamId);

            // ---- Build HPACK header block for the request ----
            ByteArrayOutputStream hpack = new ByteArrayOutputStream();
            // :method POST (HPACK static index 3)
            hpack.write(hpackIndexed(3)); // :method: POST
            // :scheme https (static index 7)
            hpack.write(hpackIndexed(7));
            hpack.write(hpackLiteral(":authority", sadr.getHostName()));
            hpack.write(hpackLiteral(":path", path));
            hpack.write(hpackLiteral("content-type", "application/dns-message"));
            hpack.write(hpackLiteral("accept", "application/dns-message"));
            hpack.write(hpackLiteral("content-length", Integer.toString(length)));
            byte[] headerBlock = hpack.toByteArray();

            // ---- Send HEADERS (END_HEADERS) ----
            ByteArrayOutputStream headersFrame = new ByteArrayOutputStream();
            writeFrameHeader(headersFrame, headerBlock.length, 0x01, 0x04, streamId); // HEADERS, END_HEADERS
            headersFrame.write(headerBlock);
            out.write(headersFrame.toByteArray());
            out.flush();

            // ---- Send DATA (END_STREAM) with the DNS query ----
            ByteArrayOutputStream dataFrame = new ByteArrayOutputStream();
            writeFrameHeader(dataFrame, length, 0x00, 0x01, streamId); // DATA, END_STREAM
            dataFrame.write(dnsQuery, offs, length);
            out.write(dataFrame.toByteArray());
            out.flush();

            // ---- Read response frames for this stream ----
            ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
            boolean done = false;
            int httpStatus = -1;

            ByteArrayOutputStream headerBlockBuf = null;

            while (!done) {
                byte[] header = new byte[9];
                readFully(in, header, 9);

                int flen = ((header[0] & 0xFF) << 16)
                        | ((header[1] & 0xFF) << 8)
                        | (header[2] & 0xFF);
                int ftype = header[3] & 0xFF;
                int fflags = header[4] & 0xFF;
                int sid = ((header[5] & 0x7F) << 24)
                        | ((header[6] & 0xFF) << 16)
                        | ((header[7] & 0xFF) << 8)
                        | (header[8] & 0xFF);

                byte[] payload = new byte[flen];
                if (flen > 0)
                    readFully(in, payload, flen);

                // Connection-level SETTINGS mid-stream
                if (sid == 0 && ftype == 0x04) { // SETTINGS
                    if ((fflags & 0x01) == 0) {   // not an ACK
                        ByteArrayOutputStream ack = new ByteArrayOutputStream();
                        writeFrameHeader(ack, 0, 0x04, 0x01, 0); // SETTINGS, ACK
                        out.write(ack.toByteArray());
                        out.flush();
                    }
                    continue;
                }

                // Ignore other connection-level frames for now
                if (sid == 0) {
                    continue;
                }

                // Ignore frames for other streams (we don't multiplex here)
                if (sid != streamId) {
                    continue;
                }

                if (ftype == 0x01 || ftype == 0x09) { // HEADERS or CONTINUATION
                    int payloadOffset = 0;

                    // PRIORITY flag on HEADERS: first 5 bytes are priority info
                    if (ftype == 0x01 && (fflags & 0x20) != 0) {
                        if (payload.length < 5) {
                            throw new IOException("Invalid PRIORITY field in HEADERS frame");
                        }
                        payloadOffset = 5;
                    }

                    if (headerBlockBuf == null) {
                        headerBlockBuf = new ByteArrayOutputStream();
                    }
                    if (payloadOffset < payload.length) {
                        headerBlockBuf.write(payload, payloadOffset, payload.length - payloadOffset);
                    }

                    if ((fflags & 0x04) != 0) { // END_HEADERS
                        byte[] headerBlockBytes = headerBlockBuf.toByteArray();
                        headerBlockBuf = null;

                        int st = parseHeadersStatus(headerBlockBytes);
                        if (st != -1) {
                            httpStatus = st;
                        }

                        if (httpStatus != -1 && httpStatus != 200) {
                            throw new IOException(
                                    "DoH server returned HTTP status " + httpStatus + " on stream " + streamId);
                        }

                        if ((fflags & 0x01) != 0) { // END_STREAM on HEADERS
                            done = true;
                        }
                    }

                } else if (ftype == 0x00) { // DATA
                    responseBody.write(payload);

                    int consumed = payload.length;

                    if (consumed > 0) {
                        // STREAM-LEVEL WINDOW_UPDATE
                        ByteArrayOutputStream wuStream = new ByteArrayOutputStream();
                        writeFrameHeader(wuStream, 4, 0x08, 0x00, streamId); // WINDOW_UPDATE
                        wuStream.write((consumed >> 24) & 0xFF);
                        wuStream.write((consumed >> 16) & 0xFF);
                        wuStream.write((consumed >> 8) & 0xFF);
                        wuStream.write(consumed & 0xFF);
                        out.write(wuStream.toByteArray());

                        // CONNECTION-LEVEL WINDOW_UPDATE
                        ByteArrayOutputStream wuConn = new ByteArrayOutputStream();
                        writeFrameHeader(wuConn, 4, 0x08, 0x00, 0); // connection window
                        wuConn.write((consumed >> 24) & 0xFF);
                        wuConn.write((consumed >> 16) & 0xFF);
                        wuConn.write((consumed >> 8) & 0xFF);
                        wuConn.write(consumed & 0xFF);
                        out.write(wuConn.toByteArray());

                        out.flush();
                    }

                    if ((fflags & 0x01) != 0) { // END_STREAM
                        done = true;
                    }

                } else if (ftype == 0x03) { // RST_STREAM
                    throw new IOException("Stream " + streamId + " reset by server");
                } else if (ftype == 0x07 && sid == 0) { // GOAWAY
                    //Logger.getLogger().logLine("HTTP/2 GOAWAY received, terminating connection.");
                    done = true;
                    throw new IOException("HTTP/2 GOAWAY from server");
                } else {
                    // Ignore other frame types (PING, WINDOW_UPDATE from server, etc.) for now.
                }
            }

            byte[] resp = responseBody.toByteArray();
            if (resp.length == 0) {
                throw new IOException("DoH: empty body, HTTP status=" + httpStatus + " on stream " + streamId);
            }
            if (!isValidDnsResponse(resp)) {
                throw new IOException("Received invalid DNS response from server! '"+new String(resp,0,Math.min(100, resp.length))+"'");
            }
            con.release(true);
            //dumpResponse(resp);
            return resp;

        } catch (IOException e) {
        	
            //Logger.getLogger().logLine("received "+e.getMessage()+"! Retrying = "+retrying );
            //Logger.getLogger().logException(e);
            if (con != null)
                con.release(false);
            if (retryCnt<MAX_RETRY) {
                //retry once with fresh connection
                retryCnt++;
                //Logger.getLogger().logLine("received "+e.getMessage()+"! ... retryCnt..."+retryCnt);
                return sendDnsQuery(sadr, path, dnsQuery, offs, length, timeout, retryCnt, proxy);
            } else {
                //Logger.getLogger().logLine("Already retried!!!");
                throw e;
            }
        }
    }

    private static void dumpResponse(byte[]response) {
        List<DnsAnswer> answers1 = parseDnsResponse(response);
        if (answers1.isEmpty()) {
            Logger.getLogger().logLine("  No answers parsed.");
        } else {
            for (DnsAnswer a : answers1) {
                Logger.getLogger().logLine("  " + a);
            }
        }
    }

    // ---- Demo main ----

    public static void main(String[] args) throws Exception {
        int port = 443;
        // String host = "dns.google";
        // String host = "dns.mullvad.net";
        // String host = "dns.cloudflare.com";
        String host = "dns.quad9.net";
        // String host = "dns.opendns.com";

        InetAddress iadr = InetAddress.getByName(host);
        InetSocketAddress sadr = new InetSocketAddress(iadr, port);

        for (int i = 0; i < 1; i++) {
            try {
                byte[] dnsQuery = buildDnsQuery("www.zenz-solutions.de", 1);
                System.out.println("Results for www.zenz-solutions.de:");
                dumpResponse(sendDnsQuery(sadr, "/dns-query", dnsQuery, 0, dnsQuery.length, 0, Proxy.NO_PROXY));

                dnsQuery = buildDnsQuery("www.example.com", 1);
                System.out.println("Results for www.example.com:");
                dumpResponse(sendDnsQuery(sadr, "/dns-query", dnsQuery, 0, dnsQuery.length, 0, Proxy.NO_PROXY));

            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
