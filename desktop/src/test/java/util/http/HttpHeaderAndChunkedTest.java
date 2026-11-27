package util.http;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import util.Utils;

public class HttpHeaderAndChunkedTest {

    @Test
    public void parsesIpv6HostHeaderWithAndWithoutPort() throws Exception {
        String reqWithPort = "GET /dns-query HTTP/1.1\r\nHost: [2001:4860:4860::8888]:8443\r\nConnection: keep-alive\r\n\r\n";
        HttpHeader hdr1 = new HttpHeader(reqWithPort, HttpHeader.REQUEST_HEADER);
        Assert.assertEquals("2001:4860:4860::8888", hdr1.remote_host_name);
        Assert.assertEquals(8443, hdr1.remote_port);
        Assert.assertFalse(hdr1.getConnectionClose());

        String reqBareIpv6 = "GET /dns-query HTTP/1.1\r\nHost: 2001:4860:4860::8888\r\n\r\n";
        HttpHeader hdr2 = new HttpHeader(reqBareIpv6, HttpHeader.REQUEST_HEADER);
        Assert.assertEquals("2001:4860:4860::8888", hdr2.remote_host_name);
        Assert.assertEquals(-1, hdr2.remote_port);
    }

    @Test
    public void rejectsUnclosedIpv6BracketAndMismatchedContentLength() throws Exception {
        try {
            new HttpHeader("GET / HTTP/1.1\r\nHost: [::1\r\n\r\n", HttpHeader.REQUEST_HEADER);
            Assert.fail("Expected IOException for unclosed IPv6 bracket");
        } catch (IOException expected) {
            // expected
        }

        try {
            new HttpHeader(
                    "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nContent-Length: 10\r\n\r\n",
                    HttpHeader.RESPONSE_HEADER
            );
            Assert.fail("Expected IOException for conflicting Content-Length headers");
        } catch (IOException expected) {
            // expected
        }
    }

    @Test
    public void detectsCompoundTransferEncodingAndParsesChunkedExtensionsAndTrailers() throws Exception {
        HttpHeader hdr = new HttpHeader(
                "HTTP/1.1 200 OK\r\nTransfer-Encoding: gzip, chunked\r\n\r\n",
                HttpHeader.RESPONSE_HEADER
        );
        Assert.assertTrue(hdr.chunkedTransfer());

        String chunkedBody =
                "5;ext=1\r\nHello\r\n" +
                "7 ; foo=bar\r\n World!\r\n" +
                "0;last=true\r\n" +
                "X-Trailer: done\r\n" +
                "\r\n";

        HttpChunkedInputStream chunked = new HttpChunkedInputStream(
                new ByteArrayInputStream(chunkedBody.getBytes("US-ASCII"))
        );
        byte[] decoded = Utils.readFully(chunked, 4);
        Assert.assertEquals("Hello World!", new String(decoded, "US-ASCII"));
        Assert.assertEquals(-1, chunked.read());
    }
}
