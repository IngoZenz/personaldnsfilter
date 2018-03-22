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

import java.util.HashSet;
import java.util.Vector;

public class TimoutNotificator implements Runnable {
	
	
	public static TimoutNotificator instance = new TimoutNotificator();
	
	public HashSet listeners = new HashSet(); 
	public boolean threadAvailable = false; 
	private boolean stopped = false;
	private volatile long  curTime = 0;
	
	
	
	public static TimoutNotificator getInstance() {
		return instance;
	}
	
	public static TimoutNotificator getNewInstance() {
		return new TimoutNotificator();
	}
	
    public synchronized void register(TimeoutListener listener) {
        
        listeners.add(listener);
         
        if (!threadAvailable) {
        	curTime = System.currentTimeMillis();
            threadAvailable = true;
            Thread t = new Thread(this);
            t.setDaemon(true);
            t.start();
        }
    }
	
	
	@Override
	public void run() {		
		Vector toListeners = new Vector();
		boolean exitThread = false;
		while (!exitThread) {			
			synchronized (this) {				
				toListeners.removeAllElements();
				curTime = System.currentTimeMillis();
				try {
					wait(1000);			
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				curTime = curTime+1000;
				if (!stopped) { 
					
					TimeoutListener[] allListeners = (TimeoutListener[]) listeners.toArray(new TimeoutListener[0]);
					for (int i = 0; i < allListeners.length; i++) {
						long tOut = allListeners[i].getTimoutTime();
						if (curTime > tOut) {
							listeners.remove(allListeners[i]);
							toListeners.add(allListeners[i]);
						}
					}
				}
				exitThread = listeners.isEmpty() || stopped;
				if (exitThread)
					threadAvailable = false;
			}
			TimeoutListener[] allToListeners = (TimeoutListener[]) toListeners.toArray(new TimeoutListener[0]);
			for (int i = 0; i < allToListeners.length; i++) {
				allToListeners[i].timeoutNotification();
			}
		}		
		
	}
	
	public synchronized void shutdown() {
		stopped = true;
		this.notifyAll();		
	}
	
	public long getCurrentTime() {
		if (threadAvailable)
			return curTime;
		else
			return System.currentTimeMillis();		
	}

	public synchronized void unregister(TimeoutListener l)  {
		listeners.remove(l);	
		
	}



}


