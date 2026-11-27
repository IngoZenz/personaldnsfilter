package util;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class EncryptionAndPaddingCipherTest {

    @Test
    public void encryptDecryptRoundTripAndUniqueIv() throws Exception {
        Encryption.init_AES("test-passphrase-1!");
        byte[] plaintext = "secret-dns-remote-command-payload".getBytes("UTF-8");

        byte[] frame1 = Encryption.encrypt(plaintext);
        byte[] frame2 = Encryption.encrypt(plaintext);

        Assert.assertFalse("Each encryption should use a fresh random IV", Arrays.equals(frame1, frame2));
        Assert.assertArrayEquals(plaintext, Encryption.decrypt(frame1));
        Assert.assertArrayEquals(plaintext, Encryption.decrypt(frame2));
    }

    @Test
    public void detectsTamperedCiphertextAndWrongPassphrase() throws Exception {
        Encryption.init_AES("correct-passphrase-A");
        byte[] plaintext = "authenticated payload".getBytes("UTF-8");
        byte[] frame = Encryption.encrypt(plaintext);

        byte[] tampered = Arrays.copyOf(frame, frame.length);
        tampered[18] ^= 0x01; // flip bit inside ciphertext
        try {
            Encryption.decrypt(tampered);
            Assert.fail("Expected IOException for tampered ciphertext");
        } catch (IOException expected) {
            // expected MAC verification failure
        }

        Encryption.init_AES("different-passphrase-B");
        try {
            Encryption.decrypt(frame);
            Assert.fail("Expected IOException for wrong keyphrase");
        } catch (IOException expected) {
            // expected MAC verification failure
        }
    }

    @Test
    public void rejectsBlankKeyphraseAndTruncatedFrames() throws Exception {
        try {
            Encryption.init_AES("   ");
            Assert.fail("Expected IOException for blank keyphrase");
        } catch (IOException expected) {
            // expected
        }

        Encryption.init_AES("valid-keyphrase");
        try {
            Encryption.decrypt(new byte[16]);
            Assert.fail("Expected IOException for truncated frame");
        } catch (IOException expected) {
            // expected
        }
    }

    @Test
    public void paddingCipherStreamsRoundTripAndRejectWrongKeyAndOversizedFrame() throws Exception {
        Encryption.init_AES("stream-secret-key-1");
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        OutputStream encOut = Encryption.getEncryptedOutputStream(raw, 32);
        byte[] message = "hello-encrypted-stream-0123456789abcdefghijklmnopqrstuvwxyz".getBytes("UTF-8");
        encOut.write(message);
        encOut.close();

        byte[] encryptedBytes = raw.toByteArray();

        InputStream decIn = Encryption.getDecryptedStream(new ByteArrayInputStream(encryptedBytes));
        byte[] restored = Utils.readFully(decIn, 16);
        decIn.close();
        Assert.assertArrayEquals(message, restored);

        // Wrong keyphrase should be rejected during header validation
        Encryption.init_AES("stream-secret-key-2");
        InputStream badKeyIn = Encryption.getDecryptedStream(new ByteArrayInputStream(encryptedBytes));
        try {
            badKeyIn.read();
            Assert.fail("Expected IOException when reading stream with wrong keyphrase");
        } catch (IOException expected) {
            // expected
        }

        // Oversized frame header must fail cleanly instead of allocating huge buffer
        ByteArrayOutputStream fakeFrame = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(fakeFrame);
        dos.writeInt(Integer.MAX_VALUE);
        dos.flush();

        InputStream oversizedIn = Encryption.getDecryptedStream(new ByteArrayInputStream(fakeFrame.toByteArray()));
        try {
            oversizedIn.read();
            Assert.fail("Expected IOException for oversized frame");
        } catch (IOException expected) {
            // expected
        }
    }
}
