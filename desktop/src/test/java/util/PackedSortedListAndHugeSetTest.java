package util;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class PackedSortedListAndHugeSetTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final ObjectPackagingManager INTEGER_PACK_MGR = new ObjectPackagingManager() {
        @Override
        public int objectSize() {
            return 4;
        }

        @Override
        public Object bytesToObject(byte[] data, int offs) {
            return ((data[offs] & 0xFF) << 24)
                    | ((data[offs + 1] & 0xFF) << 16)
                    | ((data[offs + 2] & 0xFF) << 8)
                    | (data[offs + 3] & 0xFF);
        }

        @Override
        public void objectToBytes(Object object, byte[] data, int offs) {
            int v = (Integer) object;
            data[offs] = (byte) (v >>> 24);
            data[offs + 1] = (byte) (v >>> 16);
            data[offs + 2] = (byte) (v >>> 8);
            data[offs + 3] = (byte) v;
        }
    };

    @Test
    public void handlesIntegerMinValueHashWithoutNegativeIndex() throws Exception {
        HugePackedSet set = new HugePackedSet(7, INTEGER_PACK_MGR);
        // Integer.MIN_VALUE has hashCode() == Integer.MIN_VALUE; -Integer.MIN_VALUE == Integer.MIN_VALUE in 32-bit int!
        // Make sure slotFor handles Integer.MIN_VALUE cleanly across odd/even slot counts.
        HugePackedSet evenSet = new HugePackedSet(8, INTEGER_PACK_MGR);
        evenSet.prepareInsert(Integer.MIN_VALUE);
        evenSet.prepareInsert(-1);
        evenSet.prepareInsert(0);
        evenSet.prepareInsert(Integer.MAX_VALUE);
        evenSet.finalPrepare();

        Assert.assertTrue(evenSet.add(Integer.MIN_VALUE));
        Assert.assertTrue(evenSet.add(-1));
        Assert.assertTrue(evenSet.add(0));
        Assert.assertTrue(evenSet.add(Integer.MAX_VALUE));
        Assert.assertFalse(evenSet.add(Integer.MIN_VALUE));

        Assert.assertTrue(evenSet.contains(Integer.MIN_VALUE));
        Assert.assertTrue(evenSet.contains(Integer.MAX_VALUE));
        Assert.assertFalse(evenSet.contains(42));
        Assert.assertEquals(4, evenSet.size());

        File idxDir = tempFolder.newFolder("hugeidx");
        evenSet.persist(idxDir.getAbsolutePath());

        HugePackedSet reloaded = HugePackedSet.load(idxDir.getAbsolutePath(), false, INTEGER_PACK_MGR);
        Assert.assertTrue(reloaded.contains(Integer.MIN_VALUE));
        Assert.assertTrue(reloaded.contains(-1));
        Assert.assertFalse(reloaded.contains(12345));

        evenSet.clear();
        Assert.assertTrue(evenSet.isEmpty());
        set.finalPrepare(10);
        set.clear();
    }

    @Test
    public void packedSortedListPersistsAndReloadsOnDemand() throws Exception {
        PackedSortedList list = new PackedSortedList(4, INTEGER_PACK_MGR);
        list.add(40);
        list.add(10);
        list.add(30);
        list.add(20);
        Assert.assertFalse(list.add(20));
        Assert.assertEquals(10, list.get(0));
        Assert.assertEquals(40, list.get(3));

        File persisted = new File(tempFolder.getRoot(), "pack.bin");
        list.persist(persisted.getAbsolutePath());

        PackedSortedList lazyLoaded = PackedSortedList.load(persisted.getAbsolutePath(), false, INTEGER_PACK_MGR);
        Assert.assertEquals(4, lazyLoaded.size());
        Assert.assertEquals(20, lazyLoaded.get(1));
        Assert.assertEquals(30, lazyLoaded.get(2));
    }

    @Test
    public void patternSequenceMatchesWildcardsAndClearsCache() {
        PatternSequence seq = new PatternSequence();
        seq.addPattern("*.tracker.example", Boolean.TRUE);
        seq.addPattern("*ads*", Boolean.FALSE);

        Assert.assertEquals(Boolean.TRUE, seq.match("sub.tracker.example"));
        Assert.assertEquals(Boolean.FALSE, seq.match("my-ads-host.org"));
        Assert.assertNull(seq.match("clean.example.org"));

        seq.clear();
        Assert.assertNull(seq.match("sub.tracker.example"));
    }
}
