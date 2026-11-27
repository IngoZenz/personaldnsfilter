package util;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class UtilsSecurityTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void deserializeAllowsSafeTypesAndRejectsUntrustedClassesAndProxies() throws Exception {
        Properties props = new Properties();
        props.setProperty("filterActive", "true");
        byte[] serializedProps = Utils.serializeObject(props);
        Object deserializedProps = Utils.deserializeObject(serializedProps);
        Assert.assertTrue(deserializedProps instanceof Properties);
        Assert.assertEquals("true", ((Properties) deserializedProps).getProperty("filterActive"));

        byte[] serializedErr = Utils.serializeObject(new IllegalStateException("remote-failure"));
        Object deserializedErr = Utils.deserializeObject(serializedErr);
        Assert.assertTrue(deserializedErr instanceof IllegalStateException);
        Assert.assertEquals("remote-failure", ((IllegalStateException) deserializedErr).getMessage());

        // Custom class outside java.lang/java.util/Throwable must be rejected
        byte[] untrustedBytes = Utils.serializeObject(new UntrustedPayload());
        try {
            Utils.deserializeObject(untrustedBytes);
            Assert.fail("Expected InvalidClassException for untrusted class");
        } catch (InvalidClassException expected) {
            // expected
        }

        // Dynamic proxies must be rejected
        Object proxy = Proxy.newProxyInstance(
                Runnable.class.getClassLoader(),
                new Class<?>[]{Runnable.class},
                new NoOpInvocationHandler()
        );
        byte[] proxyBytes = Utils.serializeObject(proxy);
        try {
            Utils.deserializeObject(proxyBytes);
            Assert.fail("Expected InvalidClassException for dynamic proxy");
        } catch (InvalidClassException expected) {
            // expected
        }
    }

    @Test
    public void unzipBlocksPathTraversalZipSlip() throws Exception {
        ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(zipBytes);
        zos.putNextEntry(new ZipEntry("../escaped.txt"));
        zos.write("malicious".getBytes("UTF-8"));
        zos.closeEntry();
        zos.close();

        File targetDir = tempFolder.newFolder("unzipTarget");
        File escapedFile = new File(tempFolder.getRoot(), "escaped.txt");

        try {
            Utils.unzipFile(new ZipInputStream(new ByteArrayInputStream(zipBytes.toByteArray())), targetDir.getAbsolutePath());
            Assert.fail("Expected IOException for Zip Slip entry");
        } catch (IOException expected) {
            // expected
        }
        Assert.assertFalse("Escaped file must not be created outside target dir", escapedFile.exists());
    }

    @Test
    public void getLongStringHashUsesUtf8Consistently() {
        long h1 = Utils.getLongStringHash("bücher.example");
        long h2 = Utils.getLongStringHash("bücher.example");
        long h3 = Utils.getLongStringHash("buecher.example");
        Assert.assertEquals(h1, h2);
        Assert.assertNotEquals(h1, h3);
    }

    @Test
    public void readLineBytesFromStreamEnforcesBufferBoundsAndStripsComments() throws Exception {
        byte[] input = "# comment line\nexample.org\n".getBytes("UTF-8");
        byte[] buf = new byte[64];
        ByteArrayInputStream bais = new ByteArrayInputStream(input);
        int len = Utils.readLineBytesFromStream(bais, buf, true, true);
        Assert.assertEquals("example.org\n", new String(buf, 0, len, "UTF-8"));

        byte[] smallBuf = new byte[4];
        try {
            Utils.readLineBytesFromStream(new ByteArrayInputStream("toolong\n".getBytes("UTF-8")), smallBuf, true, true);
            Assert.fail("Expected IOException on buffer overflow");
        } catch (IOException expected) {
            // expected
        }
    }

    private static class UntrustedPayload implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        String value = "blocked";
    }

    private static class NoOpInvocationHandler implements InvocationHandler, java.io.Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return null;
        }
    }
}
