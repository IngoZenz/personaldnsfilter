package util.http;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class DOHHttp2UtilTest {

    @Test
    public void parsesIndexedLiteralAndHuffmanStatusCodesSafely() {
        // Indexed static status 200 (index 8 => 0x88)
        Assert.assertEquals(200, DOHHttp2Util.parseHeadersStatus(new byte[]{(byte) 0x88}));
        // Indexed static status 404 (index 13 => 0x8D)
        Assert.assertEquals(404, DOHHttp2Util.parseHeadersStatus(new byte[]{(byte) 0x8D}));

        // Literal :status without indexing (index 8 = 0x08), Huffman "200" (0x82, 0x10, 0x01)
        Assert.assertEquals(200, DOHHttp2Util.parseHeadersStatus(new byte[]{0x08, (byte) 0x82, 0x10, 0x01}));

        // Dynamic table size update (0x20) + Literal :status without indexing (0x08), Huffman "403" (0x83, 0x68, 0x0C, 0xFF)
        Assert.assertEquals(403, DOHHttp2Util.parseHeadersStatus(new byte[]{0x20, 0x08, (byte) 0x83, 0x68, 0x0C, (byte) 0xFF}));

        // Truncated header block must not throw ArrayIndexOutOfBoundsException
        Assert.assertEquals(-1, DOHHttp2Util.parseHeadersStatus(new byte[]{0x08, 0x10, '4', '0'}));
    }

    @Test
    public void buildsAndParsesDnsWireFormatAndBlocksCompressionPointerLoops() throws Exception {
        byte[] query = DOHHttp2Util.buildDnsQuery("dns.example.org", 1);
        Assert.assertEquals(0x1234, DOHHttp2Util.readU16(query, 0));
        int[] off = new int[]{12};
        Assert.assertEquals("dns.example.org", DOHHttp2Util.readName(query, off));

        // Craft DNS response with self-referential compression pointer at offset 12 -> must fail with IOException
        byte[] loopMsg = new byte[14];
        loopMsg[12] = (byte) 0xC0;
        loopMsg[13] = 12;
        try {
            DOHHttp2Util.readName(loopMsg, new int[]{12});
            Assert.fail("Expected IOException for DNS compression pointer loop");
        } catch (IOException expected) {
            // expected
        }

        // Valid DNS response with 1 A record using compression pointer to question name
        ByteArrayOutputStream resp = new ByteArrayOutputStream();
        DOHHttp2Util.writeU16(resp, 0x1234); // ID
        DOHHttp2Util.writeU16(resp, 0x8180); // QR=1, RD=1, RA=1
        DOHHttp2Util.writeU16(resp, 1);      // QDCOUNT
        DOHHttp2Util.writeU16(resp, 1);      // ANCOUNT
        DOHHttp2Util.writeU16(resp, 0);      // NSCOUNT
        DOHHttp2Util.writeU16(resp, 0);      // ARCOUNT
        // Question: example.com A IN
        resp.write(7);
        resp.write("example".getBytes("US-ASCII"));
        resp.write(3);
        resp.write("com".getBytes("US-ASCII"));
        resp.write(0);
        DOHHttp2Util.writeU16(resp, 1);
        DOHHttp2Util.writeU16(resp, 1);
        // Answer: ptr to offset 12, type=1, class=1, ttl=60, rdlen=4, 93.184.216.34
        DOHHttp2Util.writeU16(resp, 0xC00C);
        DOHHttp2Util.writeU16(resp, 1);
        DOHHttp2Util.writeU16(resp, 1);
        resp.write(0);
        resp.write(0);
        resp.write(0);
        resp.write(60);
        DOHHttp2Util.writeU16(resp, 4);
        resp.write(new byte[]{93, (byte) 184, (byte) 216, 34});

        List<DOHHttp2Util.DnsAnswer> answers = DOHHttp2Util.parseDnsResponse(resp.toByteArray());
        Assert.assertEquals(1, answers.size());
        Assert.assertEquals("example.com A 93.184.216.34 TTL=60", answers.get(0).toString());
    }
}
