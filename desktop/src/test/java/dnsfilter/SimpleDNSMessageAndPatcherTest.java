package dnsfilter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import util.ExecutionEnvironment;

public class SimpleDNSMessageAndPatcherTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private String workDir;

    @Before
    public void setUpEnvironment() throws Exception {
        workDir = tempFolder.newFolder("work").getAbsolutePath();
        ExecutionEnvironment.setEnvironment(new ExecutionEnvironment() {
            @Override
            public String getWorkDir() {
                return workDir;
            }

            @Override
            public InputStream getAsset(String path) throws IOException {
                InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
                if (in == null) {
                    throw new IOException("Asset not found: " + path);
                }
                return in;
            }
        });
    }

    @After
    public void tearDownEnvironment() {
        ExecutionEnvironment.setEnvironment(null);
        DNSResponsePatcher.init(null, null);
    }

    private static byte[] buildQueryPacket(String host, short qType) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
        for (String label : host.split("\\.")) {
            byte[] lb = label.getBytes("US-ASCII");
            out.write(lb.length);
            out.write(lb);
        }
        out.write(0);
        out.write((qType >> 8) & 0xFF);
        out.write(qType & 0xFF);
        out.write(0);
        out.write(1); // IN
        return out.toByteArray();
    }

    @Test
    public void parsesQueryProducesResponseAndPatchesBlockedDomain() throws Exception {
        byte[] queryBytes = buildQueryPacket("ads.example.com", (short) 1);
        SimpleDNSMessage query = new SimpleDNSMessage(queryBytes, 0, queryBytes.length);

        Assert.assertFalse(query.isResponse());
        Assert.assertTrue(query.isStandardQuery());
        Object[] qdata = query.getQueryData();
        Assert.assertEquals("ads.example.com", qdata[0]);
        Assert.assertEquals((short) 1, qdata[1]);

        byte[] respBuf = new byte[512];
        byte[] realIp = new byte[]{93, (byte) 184, (byte) 216, 34};
        int respLen = query.produceResponse(respBuf, 0, realIp, 60);
        byte[] responsePacket = Arrays.copyOf(respBuf, respLen);

        SimpleDNSMessage parsedResp = new SimpleDNSMessage(responsePacket, 0, responsePacket.length);
        Assert.assertTrue(parsedResp.isResponse());

        BlockedHosts hosts = new BlockedHosts(10, 10, 10);
        hosts.finalPrepare(10);
        hosts.add("ads.example.com");
        DNSResponsePatcher.init(hosts, null);

        byte[] patched = DNSResponsePatcher.patchResponse("127.0.0.1", responsePacket, 0);
        // Last 4 bytes of A response hold the resolved/blocked IPv4 address
        Assert.assertArrayEquals(
                DNSResponsePatcher.ipv4_blocked,
                Arrays.copyOfRange(patched, patched.length - 4, patched.length)
        );
    }

    @Test
    public void rejectsMalformedCompressionLoopsAndOversizedRdlength() throws Exception {
        // Truncated question after QNAME
        byte[] truncatedQuery = new byte[]{
                0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                3, 'w', 'w', 'w', 0,
                0x00, 0x01 // missing QCLASS
        };
        try {
            new SimpleDNSMessage(truncatedQuery, 0, truncatedQuery.length);
            Assert.fail("Expected IOException for truncated DNS question");
        } catch (IOException expected) {
            // expected
        }

        // Response with RDLENGTH exceeding remaining buffer
        byte[] badAnswer = new byte[]{
                0x00, 0x01, (byte) 0x81, (byte) 0x80, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
                3, 'w', 'w', 'w', 0, 0x00, 0x01, 0x00, 0x01,
                (byte) 0xC0, 0x0C, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x3C,
                0x04, 0x00, // huge RDLENGTH=1024
                1, 2, 3, 4
        };
        try {
            DNSResponsePatcher.patchResponse("127.0.0.1", badAnswer, 0);
            Assert.fail("Expected IOException for out-of-bounds RDLENGTH");
        } catch (IOException expected) {
            // expected
        }
    }
}
