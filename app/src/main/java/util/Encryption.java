package util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Encryption {

    private static final int IV_LENGTH = 16;
    private static final int HMAC_LENGTH = 32;
    private static final int PBKDF2_ITERATIONS = 50000;
    private static final byte[] KDF_SALT;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static final byte[] INIT_BYTES = new byte[]{45, 7, -8, 45, 6, -65, 89, 5};
    public static byte[] ENCR_INIT_BYTES;

    private static final Object LOCK = new Object();
    private static boolean initialized = false;
    private static SecretKey aesKey;
    private static SecretKey macKey;
    private static String activeKeyphrase = "";

    static {
        byte[] salt;
        try {
            salt = "personalDNSfilter-remote-v2".getBytes("UTF-8");
        } catch (Exception e) {
            salt = "personalDNSfilter-remote-v2".getBytes();
        }
        KDF_SALT = salt;
    }

    /**
     * Pure-Java RFC 2898 PBKDF2-HMAC-SHA256 using HmacSHA256 (supported on Java 8+ and Android API 1+).
     */
    private static byte[] pbkdf2HmacSha256(byte[] passwordBytes, byte[] salt, int iterations, int keyLenBytes)
            throws Exception {
        Mac prf = Mac.getInstance("HmacSHA256");
        prf.init(new SecretKeySpec(passwordBytes, "HmacSHA256"));
        int hLen = prf.getMacLength();
        int blocks = (keyLenBytes + hLen - 1) / hLen;
        byte[] derived = new byte[blocks * hLen];

        for (int blockIndex = 1; blockIndex <= blocks; blockIndex++) {
            byte[] u = new byte[salt.length + 4];
            System.arraycopy(salt, 0, u, 0, salt.length);
            u[salt.length] = (byte) ((blockIndex >>> 24) & 0xFF);
            u[salt.length + 1] = (byte) ((blockIndex >>> 16) & 0xFF);
            u[salt.length + 2] = (byte) ((blockIndex >>> 8) & 0xFF);
            u[salt.length + 3] = (byte) (blockIndex & 0xFF);

            u = prf.doFinal(u);
            byte[] t = Arrays.copyOf(u, u.length);
            for (int iter = 1; iter < iterations; iter++) {
                u = prf.doFinal(u);
                for (int k = 0; k < t.length; k++) {
                    t[k] ^= u[k];
                }
            }
            System.arraycopy(t, 0, derived, (blockIndex - 1) * hLen, hLen);
        }
        return Arrays.copyOf(derived, keyLenBytes);
    }

    public static void init_AES(String keyphrase) throws IOException {
        if (keyphrase == null || keyphrase.trim().isEmpty()) {
            throw new IOException("Encryption keyphrase must not be empty!");
        }

        synchronized (LOCK) {
            if (initialized && Encryption.activeKeyphrase.equals(keyphrase)) {
                return; // already initialized for this keyphrase
            }

            try {
                byte[] passwordBytes = keyphrase.getBytes("UTF-8");
                // 16 bytes AES-128 key + 32 bytes HMAC-SHA256 key = 48 bytes
                byte[] derived = pbkdf2HmacSha256(passwordBytes, KDF_SALT, PBKDF2_ITERATIONS, 48);
                SecretKey newAesKey = new SecretKeySpec(derived, 0, 16, "AES");
                SecretKey newMacKey = new SecretKeySpec(derived, 16, 32, "HmacSHA256");
                Arrays.fill(derived, (byte) 0);

                aesKey = newAesKey;
                macKey = newMacKey;
                activeKeyphrase = keyphrase;
                initialized = true;
                ENCR_INIT_BYTES = encrypt(INIT_BYTES);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Encryption can not be initialized: " + e.getMessage());
            }
        }
    }

    public static InputStream getDecryptedStream(InputStream encrypted) throws IOException {
        synchronized (LOCK) {
            if (!initialized)
                throw new IOException("Encryption not initialized!");
        }
        return new PaddingCipherInputStream(encrypted);
    }

    public static OutputStream getEncryptedOutputStream(OutputStream out, int bufSize) throws IOException {
        synchronized (LOCK) {
            if (!initialized)
                throw new IOException("Encryption not initialized!");
        }
        return new PaddingCipherOutputStream(out, bufSize);
    }

    /**
     * Encrypt-then-MAC: [16-byte random IV][AES/CBC/PKCS5Padding ciphertext][32-byte HMAC-SHA256 tag over IV||ciphertext].
     */
    public static byte[] encrypt(byte[] msg) throws IOException {
        SecretKey encKey;
        SecretKey authKey;
        synchronized (LOCK) {
            if (!initialized)
                throw new IOException("Encryption not initialized!");
            encKey = aesKey;
            authKey = macKey;
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, encKey, new IvParameterSpec(iv));
            byte[] ciphertext = cipher.doFinal(msg);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(authKey);
            mac.update(iv);
            mac.update(ciphertext);
            byte[] tag = mac.doFinal();

            byte[] frame = new byte[IV_LENGTH + ciphertext.length + HMAC_LENGTH];
            System.arraycopy(iv, 0, frame, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, frame, IV_LENGTH, ciphertext.length);
            System.arraycopy(tag, 0, frame, IV_LENGTH + ciphertext.length, HMAC_LENGTH);
            return frame;
        } catch (Exception e) {
            throw new IOException("Encryption failed: " + e);
        }
    }

    public static byte[] decrypt(byte[] frame) throws IOException {
        SecretKey encKey;
        SecretKey authKey;
        synchronized (LOCK) {
            if (!initialized)
                throw new IOException("Encryption not initialized!");
            encKey = aesKey;
            authKey = macKey;
        }

        if (frame == null || frame.length < IV_LENGTH + 16 + HMAC_LENGTH) {
            throw new IOException("Decryption failed: invalid ciphertext frame length");
        }

        try {
            int cipherLen = frame.length - IV_LENGTH - HMAC_LENGTH;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(authKey);
            mac.update(frame, 0, IV_LENGTH + cipherLen);
            byte[] expectedTag = mac.doFinal();

            byte[] providedTag = new byte[HMAC_LENGTH];
            System.arraycopy(frame, IV_LENGTH + cipherLen, providedTag, 0, HMAC_LENGTH);

            if (!MessageDigest.isEqual(expectedTag, providedTag)) {
                throw new IOException("Decryption failed: authentication failed (wrong keyphrase or tampered data)");
            }

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, encKey, new IvParameterSpec(frame, 0, IV_LENGTH));
            return cipher.doFinal(frame, IV_LENGTH, cipherLen);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Decryption failed: " + e);
        }
    }
}
