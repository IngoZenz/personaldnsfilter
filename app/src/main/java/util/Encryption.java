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
    
    public static byte[] INIT_BYTES = new byte[]{45, 7, -8, 45, 6, -65, 89, 5};
    public static byte[] ENCR_INIT_BYTES;

    private static boolean INITIALZED = false;

    private static AlgorithmParameterSpec paramSpec;
    private static SecretKey key;
    private static String keyphrase = "";
    private static Cipher dcipher = null;
    private static Cipher ecipher = null;


    private static String invertStr(String str) {

        StringBuffer strBuf = new StringBuffer() ;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c))
                strBuf.append((c+"").toLowerCase());
            else
                strBuf.append((c+"").toUpperCase());
        }
        return strBuf.toString();

    }

    public static void init_AES(String keyphrase) throws IOException {

        if (Encryption.keyphrase.equals(keyphrase))
            return; //already initialized

        try {
            ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
            buffer.putLong(Utils.getLongStringHash(keyphrase));
            buffer.putLong(Utils.getLongStringHash(invertStr(keyphrase)));
            paramSpec = new IvParameterSpec(iv);
            key = new SecretKeySpec(buffer.array(), "AES");
            ecipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            ecipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);
            dcipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            dcipher.init(Cipher.DECRYPT_MODE, key, paramSpec);
            Encryption.keyphrase = keyphrase;
            INITIALZED=true;
            ENCR_INIT_BYTES = encrypt(INIT_BYTES);
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


