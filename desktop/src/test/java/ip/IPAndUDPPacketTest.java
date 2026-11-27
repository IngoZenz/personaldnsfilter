package ip;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;

public class IPAndUDPPacketTest {

    @Test
    public void ipv4UdpPacketUpdatesAndVerifiesChecksum() throws Exception {
        byte[] packet = new byte[20 + 8 + 4];
        UDPPacket udp = UDPPacket.createUDPPacket(packet, 0, packet.length, 4);
        int[] src = IPPacket.ip2int(InetAddress.getByName("10.10.0.1"));
        int[] dst = IPPacket.ip2int(InetAddress.getByName("9.9.9.9"));

        // Payload bytes
        packet[28] = 0x12;
        packet[29] = 0x34;
        packet[30] = 0x01;
        packet[31] = 0x00;

        udp.updateHeader(64, 17, src, dst);
        udp.updateHeader(53000, 53);

        Assert.assertEquals(4, udp.getVersion());
        Assert.assertEquals(53000, udp.getSourcePort());
        Assert.assertEquals(53, udp.getDestPort());
        Assert.assertEquals(0, ((IPPacket) udp).checkCheckSum());
        Assert.assertEquals(0, udp.checkCheckSum());
        Assert.assertEquals("10.10.0.1", IPPacket.int2ip(udp.getSourceIP()).getHostAddress());
        Assert.assertEquals("9.9.9.9", IPPacket.int2ip(udp.getDestIP()).getHostAddress());
    }

    @Test
    public void rejectsTruncatedPacketsAndOutOfRangeChecksumInputs() {
        try {
            new IPPacket(new byte[10], 0, 10);
            Assert.fail("Expected IllegalArgumentException for short IP packet");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        byte[] ipv4TooShortForUdp = new byte[24];
        ipv4TooShortForUdp[0] = 0x45;
        try {
            new UDPPacket(ipv4TooShortForUdp, 0, 24);
            Assert.fail("Expected IllegalArgumentException for truncated UDP packet");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            CheckSum.chkSum(new byte[4], 2, 4);
            Assert.fail("Expected IllegalArgumentException for out-of-range checksum slice");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
