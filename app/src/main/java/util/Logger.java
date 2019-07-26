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

import java.util.Hashtable;

public class Logger implements LoggerInterface {
	private static Hashtable _loggers = new Hashtable();
	private static LoggerInterface m_logger;
	private static LoggerInterface m_default = new Logger();

	public static void setLogger(LoggerInterface logger) {
		m_logger = logger;
	}
	
	public static void setLogger(LoggerInterface logger, String id) {
		_loggers.put(id, logger);
	}
	
	public static void removeLogger(String id) {
		_loggers.remove(id);
	}

	public static LoggerInterface getLogger() {
		if (m_logger != null)
			return m_logger;
		else
			return m_default;
	}
	
	public static LoggerInterface getLogger(String id) {
		LoggerInterface l =  (LoggerInterface) _loggers.get(id);
		if (l!= null)
			return l;
		return getLogger();
	}
	

	@Override
	public void logLine(String txt) {
		System.out.println(txt);
		
	}

	@Override
	public void logException(Exception e) {
		e.printStackTrace();
		
	}

	@Override
	public void log(String txt) {
		System.out.print(txt);

	}

	@Override
	public void message(String txt) {
		System.out.println(txt);

	}

	@Override
	public void closeLogger() {
		// TODO Auto-generated method stub
	}


}
