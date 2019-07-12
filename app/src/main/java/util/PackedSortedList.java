
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;

public class PackedSortedList implements List, RandomAccess {
	
	private boolean keepInMemory;
	private boolean persistentOutdated;
	private boolean loaded = false;
	private int object_size;
	private byte[] datapack = null;
	private int count = 0;			
	private File persistedPackFile;
	private RandomAccessFile persistedPackData = null;
	private ObjectPackagingManager objMgr;
	

	public PackedSortedList(int size, ObjectPackagingManager objMgr) {		
		this.objMgr = objMgr;
		this.object_size=objMgr.objectSize();
		datapack = new byte[size * object_size];
		keepInMemory = true;
		loaded = true;
	}

	private PackedSortedList(byte [] datapack, int count, boolean inMemory, File persistedPackFile, ObjectPackagingManager objMgr) throws IOException {
		this.objMgr = objMgr;
		this.object_size=objMgr.objectSize();
		this.datapack=datapack;		
		this.count = count;		
		this.keepInMemory = inMemory;
		this.persistedPackFile=persistedPackFile;
		persistentOutdated = false;

		if (inMemory)
			loadinMemory();
	}

	private int binarySearch(Object key) {
		return Collections.binarySearch(this, key);		
	}

	@Override
	public boolean add(Object key) {

		//write operations are NOT thread safe and need synchronization from caller!

		int pos = -(binarySearch(key) + 1);
		if (pos < 0) { // object already in list			
			return false;
		} else {
			addInternal(pos, key);
			return true;
		}
	}

	private void addInternal(int pos, Object key) {

		if (!loaded)
			loadinMemory();

		byte[] destination = datapack;
		if (count >= datapack.length/object_size) {
			destination = new byte[datapack.length+1000*object_size]; //resize for additional 1000 entries
			System.arraycopy(datapack,0, destination, 0,pos*object_size);
		}
		if (pos != count) {
			System.arraycopy(datapack, pos * object_size, destination, pos * object_size + object_size, ((count-pos)*object_size));
		}
		datapack = destination;
		objMgr.objectToBytes(key, datapack, pos * object_size);

		persistentOutdated = true;
		count++;
	}

	@Override
	public void add(int arg0, Object arg1) {
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean addAll(Collection collection) {

		//write operations are NOT thread safe and need synchronization from caller!

		Iterator it = collection.iterator();

		while (it.hasNext()) {
			add(it.next());
		}
		return true;

	}

	@Override
	public boolean addAll(int arg0, Collection arg1) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();

	}
	
	
	private int persistedPackDataRefs = 0;

	private synchronized void releaseDataPack() {

		try {
			persistedPackDataRefs--;
			//Logger.getLogger().logLine("Released reference: "+persistedPackDataRefs);
			if (persistedPackDataRefs <0)
				throw new IllegalStateException("Inconsistent State! persistedPackDataRefs = "+  persistedPackDataRefs);
			if ( persistedPackDataRefs == 0) { //no more references=>close
				persistedPackData.close();
				persistedPackData = null;
			}
		} catch (IOException e) {
			//ignore
		}

	}

	private synchronized void  aquireDataPack() throws FileNotFoundException {

		if (persistedPackData == null) {
			if (persistedPackDataRefs >0)
				throw new IllegalStateException("Inconsistent State! persistedPackData is null but there are "+persistedPackDataRefs+" references!");
			persistedPackData = new RandomAccessFile(persistedPackFile, "r");
		}
		persistedPackDataRefs++;
		//Logger.getLogger().logLine("Aquired reference: "+persistedPackDataRefs);
	}	

	@Override
	public boolean contains(Object key) {
		//parallel read is threadsafe!
		int pos = -1;
		if (!loaded) {
			try {
				//we aquire datapack here in order to ensure it does not get closed until the binarySearch completed
				aquireDataPack();
				pos = binarySearch(key);
			} catch (Exception e) {
				throw new IllegalStateException (e);
			} finally {
				releaseDataPack();
			}

		} else pos = binarySearch(key);
		return (pos > -1);
	}
	
	@Override
	public boolean containsAll(Collection arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object get(int pos) {
		//parallel read is threadsafe!

		if (pos >= count)
			return null;
		
		int offs = pos * object_size;	
		
		if (loaded)
			return objMgr.bytesToObject(datapack, offs);			
		else {
			try {
				aquireDataPack();
				byte[] obj = new byte[object_size];
				synchronized (persistedPackData) {
					persistedPackData.seek(offs);
					persistedPackData.readFully(obj);
				}
				return objMgr.bytesToObject(obj, 0);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			} finally {
				releaseDataPack();
			}
		}

	}

	@Override
	public int indexOf(Object arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isEmpty() {
		return (count ==0);
	}

	@Override
	public Iterator iterator() {
		throw new UnsupportedOperationException();
	}

	@Override
	public int lastIndexOf(Object arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ListIterator listIterator() {
		throw new UnsupportedOperationException();
	}

	@Override
	public ListIterator listIterator(int arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object remove(int arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object set(int arg0, Object arg1) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int size() {
		return count;
	}

	@Override
	public List subList(int arg0, int arg1) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object[] toArray() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object[] toArray(Object[] arg0) {
		throw new UnsupportedOperationException();
	}
	
	public void persist(String path) throws IOException  {

		//No readers and writers allowed - needs synchronization from outside

		if (persistentOutdated) {

			if (!loaded)
				throw new IOException("PackedSortedList can not be persisted when not in Memory!");

			persistedPackFile = new File(path);

			FileOutputStream out = new FileOutputStream(persistedPackFile);
			try {
				out.write(datapack, 0, count * object_size);
				out.flush();
			} finally {
				out.close();
			}
			persistentOutdated = false;
		}

		if (!keepInMemory) {
			datapack = null;
			loaded = false;
		}
	}
	
	private void loadinMemory()  {
		//No readers and writers allowed - needs synchronization from outside
		try {
			FileInputStream in = new FileInputStream(persistedPackFile);
			int size = count * objMgr.objectSize();
			datapack = new byte[size];

			int r = 0;
			int offs = 0;

			while ((r = in.read(datapack, offs, size - offs)) != -1 && offs != size)
				offs = offs + r;

			in.close();
			loaded = true;
		} catch (IOException ioe){
			throw new IllegalStateException(ioe);
		}
	}

	public static PackedSortedList load(String path, boolean inMemory, ObjectPackagingManager objMgr) throws IOException  {
		File f = new File(path);
		int size = (int) f.length(); 
		if (!f.exists() || !f.canRead())	
			throw new IOException("Cannot read "+path);
		
		return new PackedSortedList(null, size / objMgr.objectSize(), inMemory, f, objMgr);
	}
	
	public void clearAndReleaseAllMemory() {
		count = 0;
		datapack = new byte[0];
	}

}
