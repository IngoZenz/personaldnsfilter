
 /* 
 PersonalHttpProxy 1.5
 Copyright (C) 2013-2015 Ingo Zenz

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
	
	private boolean inMemory;
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
		inMemory = true;
	}

	private PackedSortedList(byte [] datapack, int count, boolean inMemory, File persistedPackFile, ObjectPackagingManager objMgr) {
		this.objMgr = objMgr;
		this.object_size=objMgr.objectSize();
		this.datapack=datapack;		
		this.count = count;		
		this.inMemory= inMemory;		
		this.persistedPackFile=persistedPackFile;
	}

	private int binarySearch(Object key) {
		return Collections.binarySearch(this, key);		
	}

	@Override
	public boolean add(Object key) {

		int pos = -(binarySearch(key) + 1);
		if (pos < 0) { // object already in list			
			return false;
		} else {
			addInternal(pos, key);
			return true;
		}
	}

	private void addInternal(int pos, Object key) {
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
		
		count++;
	}

	@Override
	public void add(int arg0, Object arg1) {
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean addAll(Collection collection) {

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
	
	
	private void releaseDataPack(boolean openedDataPack) {
		if (openedDataPack) {
			try {
				persistedPackData.close();
			} catch (IOException e) {
				//ignore
			}
			persistedPackData = null;				
		}				
		
	}

	private boolean aquireDataPack() throws FileNotFoundException {
		if (persistedPackData == null) {
			persistedPackData = new RandomAccessFile(persistedPackFile, "r");
			return true;
		}
		return false;
			
	}	

	@Override
	public boolean contains(Object key) {
		int pos = -1;
		if (!inMemory) {
			synchronized(this) {
				boolean openedDataPack = false;				
				try {
					openedDataPack = aquireDataPack();					
					pos = binarySearch(key);					
				} catch (Exception e) {
					throw new IllegalStateException (e);
				} finally {
					releaseDataPack(openedDataPack);
				}
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
		if (pos >= count)
			return null;
		
		int offs = pos * object_size;	
		
		if (inMemory)
			return objMgr.bytesToObject(datapack, offs);			
		else {
			synchronized (this) {
				boolean openedDataPack = false;
				try {
					openedDataPack=aquireDataPack();										
					persistedPackData.seek(offs);
					byte[] obj = new byte[object_size];
					persistedPackData.readFully(obj);
					return objMgr.bytesToObject(obj, 0);					
				} catch (IOException e) {
					throw new IllegalStateException(e);
				} finally {
					releaseDataPack(openedDataPack);
				}
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
		
		if (!inMemory)
			throw new IOException("PackedSortedList can not be persisted when not in Memory!");
		
		FileOutputStream out = new FileOutputStream(path);
		out.write(datapack,0,count*object_size);
		out.flush();
		out.close();
	}
	
	public static PackedSortedList load(String path, boolean inMemory, ObjectPackagingManager objMgr) throws IOException  {
		File f = new File(path);
		int size = (int) f.length(); 
		if (!f.exists() || !f.canRead())	
			throw new IOException("Cannot read "+path);
		
		byte[] buf = null;
		
		if (inMemory) {
			FileInputStream in = new FileInputStream(f);
			buf = new byte[size];
			
			int r = 0;
			int offs = 0;
			
			while ((r = in.read(buf,offs,size-offs)) != -1 && offs != size)
				offs = offs+r;
				
			
			in.close();
		}
		
		return new PackedSortedList(buf, size / objMgr.objectSize(), inMemory, f, objMgr);		
			
	}
	
	public void clearAndReleaseAllMemory() {
		count = 0;
		datapack = new byte[0];
	}

}
