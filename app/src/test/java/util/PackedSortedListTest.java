package util;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class PackedSortedListTest {

    private static class LongPackagingManager implements ObjectPackagingManager {
        public int objectSize() {
            return 8;
        }

        public Object bytesToObject(byte[] data, int offs) {
            long value = 0;
            for (int i = 0; i < 8; i++)
                value = (value << 8) | (data[offs + i] & 0xFF);
            return value;
        }

        public void objectToBytes(Object object, byte[] data, int offs) {
            long value = (Long) object;
            for (int i = 7; i >= 0; i--) {
                data[offs + i] = (byte) value;
                value >>>= 8;
            }
        }
    }

    private static final ObjectPackagingManager PACK_MGR = new LongPackagingManager();

    private File packFile;
    private File movedAway;

    @Before
    public void persistPack() throws IOException {
        packFile = File.createTempFile("packedsortedlist", ".idx");
        movedAway = new File(packFile.getPath() + ".unavailable");
        PackedSortedList list = new PackedSortedList(100, PACK_MGR);
        for (long i = 0; i < 100; i++)
            list.add(i * 2);
        list.persist(packFile.getPath());
    }

    @After
    public void cleanUp() {
        if (packFile != null)
            packFile.delete();
        if (movedAway != null)
            movedAway.delete();
    }

    @Test
    public void lookupsRecoverAfterPackFileWasTemporarilyUnavailable() throws IOException {
        PackedSortedList persisted = PackedSortedList.load(packFile.getPath(), false, PACK_MGR);
        Assert.assertTrue(persisted.contains(42L));

        Assert.assertTrue(packFile.renameTo(movedAway));
        try {
            persisted.contains(42L);
            Assert.fail("lookup without the pack file must fail");
        } catch (IllegalStateException expected) {
            Assert.assertTrue("the failure must come from the missing file, not from the reference count",
                    expected.getCause() instanceof FileNotFoundException);
        }

        Assert.assertTrue(movedAway.renameTo(packFile));
        Assert.assertTrue(persisted.contains(42L));
        Assert.assertFalse(persisted.contains(43L));
        Assert.assertEquals(42L, persisted.get(21));
    }
}
