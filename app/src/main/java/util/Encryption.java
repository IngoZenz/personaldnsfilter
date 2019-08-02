package util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Encryption {

    private static byte[] iv = new byte[]{12, -2, 30, 41, 101, -65, 17, -8, -91, 120, -11, 122, 13, -44, 45, 16};
    public static String INIT_STRING="HbCt830lPn";
    public static byte[] INIT_BYTES = INIT_STRING.getBytes();

    private static boolean INITIALZED = false;

    private static AlgorithmParameterSpec paramSpec;
    private static SecretKey key;
    private static String keyphrase = "";
    private static Cipher dcipher = null;
    private static Cipher ecipher = null;


    public static void init_AES(String keyphrase) throws IOException {

        if (Encryption.keyphrase.equals(keyphrase))
            return; //already initialized

        try {
            ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
            buffer.putLong(Utils.getLongStringHash(keyphrase));
            buffer.putLong(Utils.getLongStringHash(keyphrase.toLowerCase()));
            paramSpec = new IvParameterSpec(iv);
            key = new SecretKeySpec(buffer.array(), "AES");
            ecipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            ecipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);
            dcipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            dcipher.init(Cipher.DECRYPT_MODE, key, paramSpec);
            INITIALZED=true;
        } catch (Exception e) {
            throw new IOException("Encryption can not be initialized:" + e.getMessage());
        }
    }


    public static InputStream getDecryptedStream(InputStream encrypted) throws IOException {
        //return encrypted;

        if (!INITIALZED)
            throw new IOException("Encryption not initialized!");

        return new PaddingCipherInputStream(encrypted);
    }


    public static OutputStream getEncryptedOutputStream(OutputStream out, int bufSize) throws IOException {
        //return out;

        if (!INITIALZED)
            throw new IOException("Encryption not initialized!");

        return new PaddingCipherOutputStream(out, bufSize);
    }


    public static byte[] decrypt(byte[] msg) throws IOException {
        if (!INITIALZED)
            throw new IOException("Encryption not initialized!");

        try {

            synchronized (dcipher) {
                return dcipher.doFinal(msg);
            }

        } catch (Exception e) {
            throw new IOException("Decryption failed:" + e);
        }
    }

    public static byte[] encrypt(byte[] msg) throws IOException {
        if (!INITIALZED)
            throw new IOException("Encryption not initialized!");

        try {

            synchronized (ecipher) {
                return ecipher.doFinal(msg);
            }

        } catch (Exception e) {
            throw new IOException("Encryption failed:" + e);
        }
    }

}


