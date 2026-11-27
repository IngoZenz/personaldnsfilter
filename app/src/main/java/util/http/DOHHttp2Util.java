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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class DOHHttp2Util {

    private static final Charset US_ASCII = Charset.forName("US-ASCII");

    // ---- HPACK helpers (request side, simplified) ----

    static byte[] hpackIndexed(int index) {
        // Indexed Header Field Representation: 1xxxxxxx
        return new byte[]{(byte) (0x80 | (index & 0x7F))};
    }

    static byte[] hpackLiteral(String name, String value) throws IOException {
        // Literal Header Field with Incremental Indexing, literal name.
        // We keep it simple, non-Huffman, single-byte length (<=127).
        byte[] nameBytes = name.getBytes(US_ASCII);
        byte[] valueBytes = value.getBytes(US_ASCII);

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

    private static final int MAX_HTTP2_FRAME_SIZE = 1024 * 1024;
    private static final int MAX_POINTER_JUMPS = 64;

    // RFC 7541 Appendix B canonical 5-, 6-, and 7-bit Huffman symbols (covers ':status', digits '0'-'9', A-Z except X/Z, a-z, space, '/', '-', '.', '_')
    private static final int[] HUFFMAN_5_SYMS = {48, 49, 50, 97, 99, 101, 105, 111, 115, 116};
    private static final int[] HUFFMAN_6_SYMS = {
            32, 37, 45, 46, 47, 51, 52, 53, 54, 55, 56, 57, 61, 65, 95, 98, 100, 102, 103, 104, 108, 109, 110, 112, 114, 117
    };
    private static final int[] HUFFMAN_7_SYMS = {
            58, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 89,
            106, 107, 113, 118, 119, 120, 121, 122
    };

    private static final String[] HPACK_STATIC_NAMES = {
            null,
            ":authority", ":method", ":method", ":path", ":path", ":scheme", ":scheme",
            ":status", ":status", ":status", ":status", ":status", ":status", ":status",
            "accept-charset", "accept-encoding", "accept-language", "accept-ranges", "accept",
            "access-control-allow-origin", "age", "allow", "authorization", "cache-control",
            "content-disposition", "content-encoding", "content-language", "content-length",
            "content-location", "content-range", "content-type", "cookie", "date", "etag",
            "expect", "expires", "from", "host", "if-match", "if-modified-since",
            "if-none-match", "if-range", "if-unmodified-since", "last-modified", "link",
            "location", "max-forwards", "proxy-authenticate", "proxy-authorization", "range",
            "referer", "refresh", "retry-after", "server", "set-cookie", "strict-transport-security",
            "transfer-encoding", "user-agent", "vary", "via", "www-authenticate"
    };

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

    private static int readHpackInt(byte[] block, int[] posRef, int prefixBits) {
        int pos = posRef[0];
        if (pos >= block.length)
            return -1;
        int mask = (1 << prefixBits) - 1;
        int val = block[pos++] & mask;
        if (val < mask) {
            posRef[0] = pos;
            return val;
        }
        int m = 0;
        while (pos < block.length) {
            int b = block[pos++] & 0xFF;
            val += (b & 0x7F) << m;
            if ((b & 0x80) == 0) {
                posRef[0] = pos;
                return val;
            }
            m += 7;
            if (m > 28)
                return -1;
        }
        return -1;
    }

    private static String decodeHpackHuffmanBasic(byte[] data, int off, int len) {
        StringBuilder sb = new StringBuilder();
        int acc = 0;
        int bits = 0;
        for (int i = 0; i < len; i++) {
            acc = (acc << 8) | (data[off + i] & 0xFF);
            bits += 8;
            while (bits >= 5) {
                if (bits >= 5) {
                    int c5 = (acc >>> (bits - 5)) & 0x1F;
                    if (c5 <= 0x09) {
                        sb.append((char) HUFFMAN_5_SYMS[c5]);
                        bits -= 5;
                        continue;
                    }
                }
                if (bits >= 6) {
                    int c6 = (acc >>> (bits - 6)) & 0x3F;
                    if (c6 >= 0x14 && c6 <= 0x2D) {
                        sb.append((char) HUFFMAN_6_SYMS[c6 - 0x14]);
                        bits -= 6;
                        continue;
                    }
                }
                if (bits >= 7) {
                    int c7 = (acc >>> (bits - 7)) & 0x7F;
                    if (c7 >= 0x5C && c7 <= 0x7B) {
                        sb.append((char) HUFFMAN_7_SYMS[c7 - 0x5C]);
                        bits -= 7;
                        continue;
                    }
                    // Longer Huffman codes (8+ bits): cannot decode with 5-7 bit subset
                    return null;
                }
                break;
            }
        }
        if (bits > 7)
            return null;
        if (bits > 0) {
            int padMask = (1 << bits) - 1;
            if ((acc & padMask) != padMask)
                return null;
        }
        return sb.toString();
    }

    private static String readHpackString(byte[] block, int[] posRef) {
        if (posRef[0] >= block.length)
            return null;
        boolean huffman = (block[posRef[0]] & 0x80) != 0;
        int len = readHpackInt(block, posRef, 7);
        int start = posRef[0];
        if (len < 0 || start + len > block.length)
            return null;
        posRef[0] = start + len;
        if (!huffman) {
            return new String(block, start, len, US_ASCII);
        }
        return decodeHpackHuffmanBasic(block, start, len);
    }

    /**
     * Parses HPACK response headers to extract the HTTP :status code.
     * Supports indexed static status codes (8-14), dynamic table size updates (001xxxxx),
     * and literal/Huffman :status representations.
     */
    static int parseHeadersStatus(byte[] block) {
        int status = -1;

        if (block == null || block.length == 0) {
            return status;
        }

        int[] posRef = new int[]{0};
        while (posRef[0] < block.length) {
            int b = block[posRef[0]] & 0xFF;

            if ((b & 0x80) != 0) {
                // Indexed Header Field (1xxxxxxx)
                int index = readHpackInt(block, posRef, 7);
                if (index < 0)
                    break;
                Integer s = hpackIndexedStatus(index);
                if (s != null) {
                    return s;
                }
            } else if ((b & 0xE0) == 0x20) {
                // Dynamic Table Size Update (001xxxxx)
                int newSize = readHpackInt(block, posRef, 5);
                if (newSize < 0)
                    break;
            } else {
                // Literal Header Field: incremental indexing (01xxxxxx), without indexing (0000xxxx), or never indexed (0001xxxx)
                int prefixBits = ((b & 0xC0) == 0x40) ? 6 : 4;
                int nameIndex = readHpackInt(block, posRef, prefixBits);
                if (nameIndex < 0)
                    break;
                String name = null;
                if (nameIndex == 0) {
                    int savePos = posRef[0];
                    name = readHpackString(block, posRef);
                    if (posRef[0] == savePos && name == null)
                        break; // malformed length/bounds
                } else if (nameIndex < HPACK_STATIC_NAMES.length) {
                    name = HPACK_STATIC_NAMES[nameIndex];
                }

                int valSavePos = posRef[0];
                String value = readHpackString(block, posRef);
                if (posRef[0] == valSavePos && value == null)
                    break; // malformed length/bounds

                if (":status".equals(name) && value != null) {
                    try {
                        return Integer.parseInt(value.trim());
                    } catch (NumberFormatException ignored) {
                    }
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
            byte[] lb = label.getBytes(US_ASCII);
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

    static String readName(byte[] msg, int[] offRef) throws IOException {
        int off = offRef[0];
        StringBuilder sb = new StringBuilder();
        int jumpedOff = -1;
        boolean jumped = false;
        int jumps = 0;
        while (true) {
            if (off < 0 || off >= msg.length)
                throw new IOException("Truncated DNS domain name!");
            int len = msg[off] & 0xFF;
            if ((len & 0xC0) == 0xC0) {
                if (off + 1 >= msg.length)
                    throw new IOException("Truncated DNS compression pointer!");
                int ptr = ((len & 0x3F) << 8) | (msg[off + 1] & 0xFF);
                if (ptr >= msg.length)
                    throw new IOException("DNS compression pointer out of bounds: " + ptr);
                if (++jumps > MAX_POINTER_JUMPS)
                    throw new IOException("DNS compression pointer loop detected!");
                if (!jumped) {
                    jumpedOff = off + 2;
                    jumped = true;
                }
                off = ptr;
                continue;
            }
            if ((len & 0xC0) != 0)
                throw new IOException("Invalid DNS label type flags: 0x" + Integer.toHexString(len));
            off++;
            if (len == 0) {
                break;
            }
            if (off + len > msg.length)
                throw new IOException("Truncated DNS label of length " + len);
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

        try {
            // Skip questions
            for (int i = 0; i < qd; i++) {
                int[] o = new int[]{off};
                String qname = readName(msg, o);
                off = o[0];

                if (off + 4 > msg.length) {
                    Logger.getLogger().logLine("DNS: truncated question section");
                    return answers;
                }

                off += 4; // qtype + qclass
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
        } catch (IOException e) {
            Logger.getLogger().logLine("DNS parse error: " + e.getMessage());
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

    private static String negotiateHttp2(SSLSocket sslsocket) throws IOException {
        try {
            SSLParameters params = sslsocket.getSSLParameters();
            java.lang.reflect.Method setAlpn = SSLParameters.class.getMethod("setApplicationProtocols", String[].class);
            setAlpn.invoke(params, new Object[]{new String[]{"h2"}});
            sslsocket.setSSLParameters(params);
            sslsocket.startHandshake();
            java.lang.reflect.Method getAlpn = SSLSocket.class.getMethod("getApplicationProtocol");
            return (String) getAlpn.invoke(sslsocket);
        } catch (NoSuchMethodException e) {
            // Fallback for Android API < 29 (Conscrypt)
            try {
                java.lang.reflect.Method setAlpn = sslsocket.getClass().getMethod("setAlpnProtocols", byte[].class);
                setAlpn.invoke(sslsocket, new Object[]{new byte[]{2, 'h', '2'}});
                sslsocket.startHandshake();
                java.lang.reflect.Method getAlpn = sslsocket.getClass().getMethod("getAlpnSelectedProtocol");
                byte[] proto = (byte[]) getAlpn.invoke(sslsocket);
                return proto != null ? new String(proto, US_ASCII) : null;
            } catch (Exception e2) {
                throw new IOException("HTTP/2 ALPN negotiation not supported on this platform", e2);
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException)
                throw (IOException) cause;
            throw new IOException("HTTP/2 ALPN negotiation failed", cause != null ? cause : e);
        } catch (Exception e) {
            throw new IOException("HTTP/2 ALPN negotiation failed", e);
        }
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
        SSLSocket sslsocket = null;
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

            sslsocket = (SSLSocket) sslContext.getSocketFactory()
                    .createSocket(socket, sadr.getHostName(), sadr.getPort(), true);
            String negotiated = negotiateHttp2(sslsocket);
            if (!"h2".equals(negotiated)) {
                throw new IOException("HTTP/2 not negotiated; got: " + negotiated);
            }

            OutputStream out = sslsocket.getOutputStream();
            InputStream in = sslsocket.getInputStream();

            // Client preface
            out.write("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(US_ASCII));

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
                if (flen > MAX_HTTP2_FRAME_SIZE) {
                    throw new IOException("HTTP/2 handshake frame exceeds limit: " + flen);
                }
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
            if (!acked) {
                throw new IOException("Did not receive SETTINGS ACK from server within expected frames");
            }
            return sslsocket;

        } catch (IOException eio) {
            cleanupOnError(socket, sslsocket);
            throw eio;
        } catch (Exception e) {
            cleanupOnError(socket, sslsocket);
            throw new IOException(e.getMessage(), e);
        }
    }

    private static void cleanupOnError(Socket socket, SSLSocket sslsocket) {
        try {
            if (sslsocket != null)
                sslsocket.close();
            else if (socket!= null)
                socket.close();
        } catch (IOException e2) {
            // ignore
        }
    }

    // ---- Send a single DNS query over HTTP/2 and return raw DNS response ----

    private static final int MAX_RETRY = 4;

    static boolean isValidDnsResponse(byte[] resp) {
        // Must be at least DNS header size
        if (resp == null || resp.length < 12) {
            return false;
        }

        int flags   = ((resp[2] & 0xFF) << 8) | (resp[3] & 0xFF);
        int qdcount = ((resp[4] & 0xFF) << 8) | (resp[5] & 0xFF);
        int ancount = ((resp[6] & 0xFF) << 8) | (resp[7] & 0xFF);
        int rcode   = flags & 0x0F;

        // QR bit must be 1
        if ((flags & 0x8000) == 0) {
            return false;
        }

        // Only standard queries
        int opcode = (flags >> 11) & 0xF;
        if (opcode != 0) {
            return false;
        }

        // Successful responses should contain the question.
        // Error responses may contain only the header.
        if (rcode == 0 && (qdcount < 1 || qdcount > 5)) {
            return false;
        }

        // ANCOUNT sanity check
        if (ancount > 50) {
            return false;
        }

        return true;
    }

    public static byte[] sendDnsQuery(InetSocketAddress sadr, String path,
                                      byte[] dnsQuery, int offs, int length,
                                      int timeout, Proxy proxy) throws IOException {
        IOException lastEx = null;
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            Connection con = null;
            try {
                con = Connection.connect(sadr, timeout, true, null, proxy, true);
                if (attempt > 0 && !con.isFresh()) {
                    con.refreshConnection(); //ensure fresh connection is used
                }

                con.setSoTimeout(timeout);

                OutputStream out = con.getOutputStream();
                InputStream in = con.getInputStream();

                int streamId = con.getHttp2StreamID();

                // ---- Build HPACK header block for the request ----
                ByteArrayOutputStream hpack = new ByteArrayOutputStream();
                hpack.write(hpackIndexed(3)); // :method: POST
                hpack.write(hpackIndexed(7)); // :scheme: https
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

                    if (flen > MAX_HTTP2_FRAME_SIZE) {
                        throw new IOException("HTTP/2 frame payload exceeds limit: " + flen);
                    }

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

                    if (sid == 0) {
                        if (ftype == 0x07) { // GOAWAY
                            throw new IOException("HTTP/2 GOAWAY from server");
                        }
                        continue;
                    }

                    if (sid != streamId) {
                        continue;
                    }

                    if (ftype == 0x01 || ftype == 0x09) { // HEADERS or CONTINUATION
                        int payloadOffset = 0;

                        if (ftype == 0x01 && (fflags & 0x20) != 0) {
                            if (payload.length < 5) {
                                throw new IOException("Invalid PRIORITY field in HEADERS frame");
                            }
                            payloadOffset = 5;
                        }

                        if (headerBlockBuf == null) {
                            headerBlockBuf = new ByteArrayOutputStream();
                        }
                        int addLen = payload.length - payloadOffset;
                        if (headerBlockBuf.size() + addLen > MAX_HTTP2_FRAME_SIZE) {
                            throw new IOException("HTTP/2 header block exceeds limit");
                        }
                        if (addLen > 0) {
                            headerBlockBuf.write(payload, payloadOffset, addLen);
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
                        if (responseBody.size() + payload.length > MAX_HTTP2_FRAME_SIZE) {
                            throw new IOException("HTTP/2 response body exceeds limit");
                        }
                        responseBody.write(payload);

                        int consumed = payload.length;
                        boolean endStream = (fflags & 0x01) != 0;

                        if (consumed > 0) {
                            // Do not send stream-level WINDOW_UPDATE after END_STREAM (half-closed remote)
                            if (!endStream) {
                                ByteArrayOutputStream wuStream = new ByteArrayOutputStream();
                                writeFrameHeader(wuStream, 4, 0x08, 0x00, streamId); // WINDOW_UPDATE
                                wuStream.write((consumed >> 24) & 0xFF);
                                wuStream.write((consumed >> 16) & 0xFF);
                                wuStream.write((consumed >> 8) & 0xFF);
                                wuStream.write(consumed & 0xFF);
                                out.write(wuStream.toByteArray());
                            }

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

                        if (endStream) {
                            done = true;
                        }

                    } else if (ftype == 0x03) { // RST_STREAM
                        throw new IOException("Stream " + streamId + " reset by server");
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
                return resp;

            } catch (IOException e) {
                if (con != null)
                    con.release(false);
                lastEx = e;
            } catch (RuntimeException e) {
                if (con != null) con.release(false);
                throw new IOException("DoH: unexpected exception processing DOH2 DNS query", e);
            }
        }
        throw lastEx;
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
