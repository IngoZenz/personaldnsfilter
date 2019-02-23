/* 
 PersonalDNSFilter 1.5
 Copyright (C) 2017 Ingo Zenz

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

 Find the latest version at http://www.zenz-solutions.de/personaldnsfilter
 Contact:i.z@gmx.net 
 */

package dnsfilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import util.HugePackedSet;
import util.LRUCache;
import util.ObjectPackagingManager;
import util.Utils;

public class BlockedHosts implements Set {

	private static class MyPackagingManager implements ObjectPackagingManager {

		@Override
		public int objectSize() {
			return 8;
		}

		@Override
		public Object bytesToObject(byte[] data, int offs) {
			return Utils.byteArrayToLong(data, offs);
		}

		@Override
		public void objectToBytes(Object object, byte[] data, int offs) {
			Utils.writeLongToByteArray((Long) object, data, offs);
		}
	}

	private static ObjectPackagingManager PACK_MGR = new MyPackagingManager();
	private static Object NOT_NULL = new Object();
	private LRUCache okCache;
	private LRUCache filterListCache;
	private Hashtable hostsFilterOverRule;

	private int sharedLocks = 0;
	private boolean exclusiveLock = false;

	private HugePackedSet blockedHostsHashes;

	public BlockedHosts(int maxCountEstimate, int okCacheSize, int filterListCacheSize, Hashtable hostsFilterOverRule) {
		okCache = new LRUCache(okCacheSize);
		filterListCache = new LRUCache(filterListCacheSize);
		this.hostsFilterOverRule = hostsFilterOverRule;

		int slots = maxCountEstimate / 6000;
		if ((slots % 2) == 0)
			slots++;

		blockedHostsHashes = new HugePackedSet(slots, PACK_MGR);
	}

	private BlockedHosts(HugePackedSet blockedHostsHashes, int okCacheSize, int filterListCacheSize, Hashtable hostsFilterOverRule) {
		this.blockedHostsHashes = blockedHostsHashes;
		okCache = new LRUCache(okCacheSize);
		filterListCache = new LRUCache(filterListCacheSize);
		this.hostsFilterOverRule = hostsFilterOverRule;
	}

	public void setHostsFilterOverRule(Hashtable hostsFilterOverRule){
		if (hostsFilterOverRule == null)
			throw new IllegalArgumentException("Argument null not allowed!");
		this.hostsFilterOverRule = hostsFilterOverRule;
	}

	public void clearCache (Set entries) {
		Iterator it = entries.iterator();
		while (it.hasNext()){
			Object key = it.next();
			okCache.remove(key);
			filterListCache.remove(key);
		}
	}


	synchronized public void lock(int type) {
		if (type == 0) {
			while (exclusiveLock) {
				try {
					wait();
				} catch (Exception e) {
					// ignore
				}
			}
			sharedLocks++;
		} else if (type == 1) {
			while (!(sharedLocks == 0) || (exclusiveLock)) {
				try {
					wait();
				} catch (InterruptedException e) {
					// ignore
				}
			}

			exclusiveLock = true;
		}
	}

	synchronized public void unLock(int type) {
		if (type == 0) {
			if (sharedLocks > 0) {
				sharedLocks--;
				if (sharedLocks == 0) {
					notifyAll();
				}
			}
		} else if (type == 1) {
			if (exclusiveLock) {
				exclusiveLock = false;
				notifyAll();
			}
		}
	}


	public static boolean checkIndexVersion(String path) throws IOException {
		return HugePackedSet.checkIndexVersion(path);
	}

	public static BlockedHosts loadPersistedIndex(String path, boolean inMemory, int okCacheSize, int filterListCacheSize, Hashtable hostsFilterOverRule) throws IOException {
		return new BlockedHosts(HugePackedSet.load(path, inMemory, PACK_MGR), okCacheSize, filterListCacheSize, hostsFilterOverRule);
	}

	public void persist(String path) throws IOException {
		blockedHostsHashes.persist(path);
	}

	public void prepareInsert(String host) {
		blockedHostsHashes.prepareInsert(Utils.getLongStringHash(host));
	}

	public void finalPrepare() {
		blockedHostsHashes.finalPrepare();
	}

	@Override
	public boolean add(Object host) {
		return blockedHostsHashes.add(Utils.getLongStringHash((String) host));
	}

	@Override
	public boolean contains(Object object) {

		try {
			lock(0); //shared read lock ==> block Updates of the structure

			String hostName = (String) object;
			long hosthash = Utils.getLongStringHash(hostName);

			if (hostsFilterOverRule != null) {
				Object val = hostsFilterOverRule.get(hostName);
				if (val != null)
					return ((Boolean) val).booleanValue();
			}

			if (okCache.get(hosthash) != null)
				return false;
			else if (filterListCache.get(hosthash) != null)
				return true;
			else if (contains(hostName, hosthash)) {
				filterListCache.put(hosthash, NOT_NULL);
				return true;
			} else {
				okCache.put(hosthash, NOT_NULL);
				return false;
			}
		} finally {
			unLock(0);
		}
	}


	private boolean contains(String hostName, long hosthash) {

		if (blockedHostsHashes.contains(hosthash))
			return true;

		int idx = hostName.indexOf('.');
		while (idx != -1) {
			hostName = hostName.substring(idx + 1);

			if (hostsFilterOverRule != null) {
				Object val = hostsFilterOverRule.get(hostName);
				if (val != null)
					return ((Boolean) val).booleanValue();
			}

			if (blockedHostsHashes.contains(Utils.getLongStringHash(hostName)))
				return true;
			idx = hostName.indexOf('.');
		}
		return false;
	}

	public void clear() {
		blockedHostsHashes.clear();
		filterListCache.clear();
		okCache.clear();
	}

	protected void migrateTo(BlockedHosts hostFilter) {

		// should be under exclusive lock from caller
		okCache.clear();
		okCache = hostFilter.okCache;

		filterListCache.clear();
		filterListCache = hostFilter.filterListCache;

		hostsFilterOverRule = hostFilter.hostsFilterOverRule;

		blockedHostsHashes.migrateTo(hostFilter.blockedHostsHashes);
	}

	@Override
	public boolean addAll(Collection arg0) {
		throw new UnsupportedOperationException("Not supported!");
	}

	@Override
	public boolean containsAll(Collection arg0) {
		throw new UnsupportedOperationException("Not supported!");
	}

	@Override
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return blockedHostsHashes.isEmpty();
	}

	@Override
	public Iterator iterator() {
		throw new UnsupportedOperationException("Not supported!");
	}

	@Override
	public boolean remove(Object object) {
		throw new UnsupportedOperationException("Not supported!");
	}

	@Override
	public boolean removeAll(Collection arg0) {
		throw new UnsupportedOperationException("Not supported!");
	}

	@Override
	public boolean retainAll(Collection arg0) {
		throw new UnsupportedOperationException("Not supported!");
	}

	@Override
	public int size() {
		return blockedHostsHashes.size();
	}

	@Override
	public Object[] toArray() {
		throw new UnsupportedOperationException("Not supported!");
	}

	@Override
	public Object[] toArray(Object[] array) {
		throw new UnsupportedOperationException("Not supported!");
	}


}
