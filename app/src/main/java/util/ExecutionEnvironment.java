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

public class ExecutionEnvironment implements ExecutionEnvironmentInterface {
	
	private static ExecutionEnvironmentInterface m_Env;
	private static ExecutionEnvironmentInterface m_default = new ExecutionEnvironment();
	
	
	public static void setEnvironment(ExecutionEnvironmentInterface env) {
		m_Env = env;
	}
	
	public static ExecutionEnvironmentInterface getEnvironment() {
		if (m_Env != null)
			return m_Env;
		else
			return m_default;
	}
	
	
	@Override
	public void wakeLock() {
		// by default do nothing
		
	}

	@Override
	public void releaseWakeLock() {
		// by default do nothing		
	}

	@Override
	public boolean debug() {
		return false;
	}

}
