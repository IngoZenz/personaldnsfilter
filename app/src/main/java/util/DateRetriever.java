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

import java.util.Calendar;

public class DateRetriever implements Runnable {

	private static int PRECISION_MILLIS = 1000;
	private static DateRetriever RETRIEVER_INSTANCE = new DateRetriever();
	
	private Thread _thread = null;
	private String current;
	private boolean picked = false;

	
	
	public static String getDateString() {
		return RETRIEVER_INSTANCE.retrieveDateString();
	}
	
	private String int2Str(int val) {
		if (val < 10)
			return "0" + val;
		else
			return "" + val;
	}

	private String dateStr(Calendar cal) {

		return (int2Str(cal.get(Calendar.MONTH) + 1)) + "/" + int2Str(cal.get(Calendar.DAY_OF_MONTH)) + "/" + cal.get(Calendar.YEAR) + " " + int2Str(cal.get(Calendar.HOUR_OF_DAY)) + ":" + int2Str(cal.get(Calendar.MINUTE)) + ":"
				+ int2Str(cal.get(Calendar.SECOND));
	}

	private synchronized String retrieveDateString() {
		picked=true;
		if (_thread != null) {
			return current;
		} else {
			current = dateStr(Calendar.getInstance());
			_thread = new Thread(this);
			_thread.setDaemon(true);
			_thread.start();
			
			return current;
		}

	}
	
	
	private void waitMillis(long millis) {
		try {
			wait(millis);
		} catch (InterruptedException e) {
			Logger.getLogger().logException(e);
		}			
		
	}

	@Override
	public synchronized void run() {
		_thread = Thread.currentThread();
		
		waitMillis(PRECISION_MILLIS); //initial date set already - will be valid for PRECISION_MILLIS milliseconds - No need to update the date
		while (picked) {
			current = dateStr(Calendar.getInstance());
			picked=false;			
			waitMillis(PRECISION_MILLIS);				
		}
		_thread = null;		
	}

}
