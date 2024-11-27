/* 
 PersonalDNSFilter 1.5
 Copyright (C) 2017 - 2019 Ingo Zenz

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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import util.ExecutionEnvironment;
import util.HugePackedSet;
import util.LRUCache;
import util.Logger;
import util.ObjectPackagingManager;
import util.PatternSequence;
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

	private int sharedLocks = 0;
	private boolean exclusiveLock = false;

	private Hashtable <String, Boolean> hostsFilterOverRule = new Hashtable<String, Boolean>();

	private HugePackedSet blockedHostsHashes;

	private PatternSequence overrulePatterns = new PatternSequence();

	public BlockedHosts(int maxCountEstimate, int okCacheSize, int filterListCacheSize) {
		okCache = new LRUCache(okCacheSize);
		filterListCache = new LRUCache(filterListCacheSize);

		if (ExecutionEnvironment.getEnvironment().debug())
			Logger.getLogger().logLine("CACHE SIZE:"+okCacheSize+", "+filterListCacheSize);

		int slots = maxCountEstimate / 6000;
		if ((slots % 2) == 0)
			slots++;

		blockedHostsHashes = new HugePackedSet(slots, PACK_MGR);
	}

	private BlockedHosts(HugePackedSet blockedHostsHashes, int okCacheSize, int filterListCacheSize) {
		this.blockedHostsHashes = blockedHostsHashes;
		okCache = new LRUCache(okCacheSize);
		filterListCache = new LRUCache(filterListCacheSize);

		if (ExecutionEnvironment.getEnvironment().debug())
			Logger.getLogger().logLine("CACHE SIZE:"+okCacheSize+", "+filterListCacheSize);
	}


	public void addOverrule(String host, boolean filter) {
		host = host.toLowerCase();

		if (host.indexOf("*") != -1) {
			overrulePatterns.addPattern(host, filter);
			clearCache(!filter);
		}
		else {
			hostsFilterOverRule.put(host, new Boolean(filter));
			clearCache(host, !filter);
		}
	}

	public void removeOverrule (String host, boolean filter) {
		host = host.toLowerCase();

		if (host.indexOf("*") != -1) {
			overrulePatterns.removePattern(host, filter);
			clearCache(filter);
		}
		else {
			Boolean val = hostsFilterOverRule.get(host);
			if (val != null && val.booleanValue() == filter) {
				hostsFilterOverRule.remove(host);
				clearCache(host, filter);
			}
		}
	}

	private void clearCache(String host, boolean filter) {
		long hostHash = Utils.getLongStringHash((String) host);
		if (filter)
			filterListCache.remove(hostHash);
		else
			okCache.remove(hostHash);
	}

	private void clearCache(boolean filter) {
		if (filter)
			filterListCache.clear();
		else
			okCache.clear();
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

	public static BlockedHosts loadPersistedIndex(String path, boolean inMemory, int okCacheSize, int filterListCacheSize) throws IOException {
		return new BlockedHosts(HugePackedSet.load(path, inMemory, PACK_MGR), okCacheSize, filterListCacheSize);
	}


	public void persist(String path) throws IOException {
		try {
			lock(1);
			blockedHostsHashes.persist(path);
		} finally {
			unLock(1);
		}
	}

	public void updatePersist() throws IOException {
		try {
			lock(1);
			blockedHostsHashes.updatePersist();
		} finally {
			unLock(1);
		}
	}


	public void prepareInsert(String host) {
		if (host.indexOf("*") == -1) //patterns are handled differently
			blockedHostsHashes.prepareInsert(Utils.getLongStringHash(host.toLowerCase()));
	}

	public void finalPrepare() {
		blockedHostsHashes.finalPrepare();
	}

	public void finalPrepare(int maxCountEstimate) {
		blockedHostsHashes.finalPrepare(maxCountEstimate);
	}


	public boolean update(Object host) throws IOException {
		try {
			lock(1);

			if (((String) host).indexOf("*") != -1)
				throw new IOException("Wildcard not supported for update:" + host);

			long hostHash = Utils.getLongStringHash((String) ((String) host).toLowerCase());
			okCache.remove(hostHash);
			filterListCache.remove(hostHash);

			return blockedHostsHashes.add(hostHash);
		} finally {
			unLock(1);
		}
	}

	@Override
	public boolean add(Object host) {
		return blockedHostsHashes.add(Utils.getLongStringHash((String) ((String) host).toLowerCase()));
	}


	@Override
	public boolean contains(Object object) {

		try {
			lock(0); //shared read lock ==> block Updates of the structure
			boolean ip = false;

			String hostName = ((String) object).toLowerCase();
			if (hostName.startsWith("%ip%")) {
				ip = true;
				hostName = hostName.substring(4);
			}
			long hosthash = Utils.getLongStringHash(hostName);

			if (okCache.get(hosthash) != null)
				return false;
			else if (filterListCache.get(hosthash) != null)
				return true;
			else if (contains(hostName, hosthash, !ip, !ip)) {
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

	private boolean contains(String hostName, long hosthash, boolean checkParent, boolean checkPattern) {

		int idx = 0;

		while (idx != -1) {

			Boolean filter = hostsFilterOverRule.get(hostName);
			if (filter != null)
				return filter.booleanValue();

			if (checkPattern) {
				Boolean patternMatch = (Boolean) overrulePatterns.match(hostName);
				if (patternMatch != null)
					return patternMatch.booleanValue();
			}

			if (blockedHostsHashes.contains(hosthash))
				return true;

			if (checkParent) {
				idx = hostName.indexOf('.');

				if (idx != -1) {
					hostName = hostName.substring(idx + 1);
					hosthash = Utils.getLongStringHash(hostName);
				}
			} else idx = -1;
		}
		return false;
	}

	public void clear() {
		blockedHostsHashes.clear();
		filterListCache.clear();
		okCache.clear();
		hostsFilterOverRule = null; // do not clear as provided from outside and reused
		overrulePatterns = null;  // do not clear as provided from outside and reused
	}

	protected void migrateTo(BlockedHosts hostFilter) {

		// should be under exclusive lock from caller
		okCache.clear();
		okCache = hostFilter.okCache;

		filterListCache.clear();
		filterListCache = hostFilter.filterListCache;

		hostsFilterOverRule = hostFilter.hostsFilterOverRule;

		overrulePatterns = hostFilter.overrulePatterns;

		//blockedHostsHashes.migrateTo(hostFilter.blockedHostsHashes);
		blockedHostsHashes = hostFilter.blockedHostsHashes;
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
