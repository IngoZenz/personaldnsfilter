 /* 
 PersonalHttpProxy 1.5
 Copyright (C) 2013-2019 Ingo Zenz

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

 Find the latest version at http://www.zenz-solutions.de/personalhttpproxy
 Contact:i.z@gmx.net 
 */
package util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class HugePackedSet implements Set {
	
	private final static String IDX_VERSION = "1.0"; 
	
	private ObjectPackagingManager objMgr;
	
	private int slotCount;
	
	private int count = 0;
	
	private int[] slotSizes = null; 
	
	private PackedSortedList[] subsets = null;

	private String loadedFromPath = null;
	
	public HugePackedSet(int slots, ObjectPackagingManager objMgr) {
		this.objMgr = objMgr;
		slotCount = slots;
		slotSizes = new int[slotCount];
		subsets = new PackedSortedList[slotCount];
		for (int i = 0; i < slotCount; i++)
			slotSizes[i] = 0;
	}
	
	
	private HugePackedSet(String persistencePath, PackedSortedList[] subsets, int slots, int count, ObjectPackagingManager objMgr) {
		this.loadedFromPath = persistencePath;
		this.objMgr = objMgr;
		slotCount = slots;
		this.subsets = subsets;
		this.count=count;
	}
	
	
	
	private int slotFor(Object obj) {
		int mod = obj.hashCode() % slotCount;
		return mod < 0 ? -mod : mod;
	}

	public void prepareInsert (Object obj) {
		slotSizes[slotFor(obj)]++;
	}


	public void finalPrepare() {
		for (int i = 0; i < slotCount; i++)
			subsets[i] = new PackedSortedList(slotSizes[i], objMgr);

		slotSizes = null; //save memory
	}

	public void finalPrepare(int maxCountEstimate) {
		int slotSize = ((int) (maxCountEstimate*1.2)) / slotCount;
		for (int i = 0; i < slotCount; i++)
			subsets[i] = new PackedSortedList(slotSize, objMgr);

		slotSizes = null; //save memory
	}

	@Override
	public boolean add(Object obj) {
		boolean added = subsets[slotFor(obj)].add(obj);
		if (added) count ++;
		return added;
	}

	@Override
	public boolean addAll(Collection arg0) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	@Override
	public void clear() {
		count = 0;
		loadedFromPath = null;
		slotSizes = new int[slotCount];
		for (int i = 0; i < slotCount; i++) {
			if (subsets[i] != null)
				subsets[i].clearAndReleaseAllMemory();
			slotSizes[i] = 0;
		}
	}

	@Override
	public boolean contains(Object obj) {
		if (count == 0 || slotCount == 0)
			return false;
		return subsets[slotFor(obj)].contains(obj);
	}

	@Override
	public boolean containsAll(Collection arg0) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	@Override
	public boolean isEmpty() {
		return count ==0;
	}

	@Override
	public Iterator iterator() {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	@Override
	public boolean remove(Object arg0) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	@Override
	public boolean removeAll(Collection arg0) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	@Override
	public boolean retainAll(Collection arg0) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	@Override
	public int size() {
		return count;
	}

	@Override
	public Object[] toArray() {
		throw new UnsupportedOperationException("Not supported!");
	}

	@Override
	public Object[] toArray(Object[] arg0) {
		throw new UnsupportedOperationException("Not supported!");
	}
	
	public void persist(String path) throws IOException {
		String tmpPath = path + ".tmp";
		Utils.deleteFolder(tmpPath);

		File dir = new File(tmpPath);
		if (!dir.mkdirs() && !dir.isDirectory())
			throw new IOException("Cannot create temporary index folder: " + tmpPath);

		//write index version info
		FileOutputStream out = new FileOutputStream(dir.getAbsolutePath()+"/IDX_VERSION");
		try {
			out.write(IDX_VERSION.getBytes());
			out.flush();
		} finally {
			out.close();
		}

		//write indexes
		for (int i = 0; i < slotCount; i++)
			subsets[i].persist(dir.getAbsolutePath()+"/idx"+i);

		Utils.deleteFolder(path);
		File renameDir = new File(path);
		if (!dir.renameTo(renameDir))
			throw new IOException("Failed to rename temporary index folder " + tmpPath + " to " + path);

		loadedFromPath = path;
	}

	public void updatePersist() throws IOException{
		if (loadedFromPath == null)
			throw new IOException("Can not update non persisted index!");

		File dir = new File(loadedFromPath);
		//write indexes
		for (int i = 0; i < slotCount; i++)
			subsets[i].persist(dir.getAbsolutePath()+"/idx"+i);
	}


	public static boolean checkIndexVersion(String path)throws IOException {
		File idxVersionFile = new File(path+"/IDX_VERSION");
		if (idxVersionFile.exists()) {
			FileInputStream in = new FileInputStream(idxVersionFile);
			try {
				byte[] buf = new byte[10];
				int r = in.read(buf);
				return r > 0 && new String(buf, 0, r).equals(IDX_VERSION);
			} finally {
				in.close();
			}
		} else
			return false;
	}

	public static HugePackedSet load(String path, boolean inMemory, ObjectPackagingManager objMgr) throws IOException {
		//check version
		if (!checkIndexVersion(path))
			throw new IOException("Incompatible index version - Rebuild Index!");

		// get slotcount
		int slotCount = 0;
		int count = 0;
		while (new File(path+"/idx"+slotCount).exists())
			slotCount++;

		if (slotCount == 0)
			throw new IOException("Corrupted index (0 slots found) at " + path);

		PackedSortedList[] subsets = new PackedSortedList[slotCount];
		for (int i = 0; i < subsets.length; i++) {
			subsets[i]= PackedSortedList.load(path+"/idx"+i, inMemory, objMgr);
			count = count+subsets[i].size();
		}

		return new HugePackedSet(path, subsets, slotCount, count, objMgr);

	}

}
