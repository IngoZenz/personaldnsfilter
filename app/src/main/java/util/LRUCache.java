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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class LRUCache extends LinkedHashMap {
	
	int MAX_ENTRIES;
	
	public LRUCache(int maxCount) {
		super(maxCount+1);
		this.MAX_ENTRIES=maxCount;	
	}
	
	
	@Override
	protected boolean removeEldestEntry(Map.Entry eldest) {
		return  (size() > MAX_ENTRIES);
	}
		
	
	@Override
	public synchronized Object put (Object key, Object value) {
		return super.put(key, value);
		
	}
	
	@Override
	public synchronized Object get (Object key) {
		return super.get(key);
		
	}
	
	@Override
	public synchronized Object remove (Object key) {
		return super.remove(key);
		
	}
	
	@Override
	public synchronized int size () {
		return super.size();		
	}
	
	@Override
	public synchronized int hashCode () {
		return super.hashCode();		
	}
	
	@Override
	public synchronized boolean equals (Object o) {
		return super.equals(o);	
	}
	
	@Override
	public synchronized boolean isEmpty () {
		return super.isEmpty();		
	}
	
	@Override
	public synchronized boolean containsKey (Object key) {
		return super.containsKey(key);		
	}
	
	@Override
	public synchronized boolean containsValue (Object o) {
		return super.containsValue(o);		
	}
	
	
	@Override
	public synchronized Collection values() {
		return super.values();		
	}
	
	@Override
	public synchronized Set keySet() {
		return super.keySet();		
	}
	
	@Override
	public synchronized Set entrySet() {
		return super.entrySet();		
	}
	
	@Override
	public synchronized void putAll(Map m) {
		super.putAll(m);		
	}
	
	@Override
	public synchronized void clear() {
		super.clear();		
	}


}
