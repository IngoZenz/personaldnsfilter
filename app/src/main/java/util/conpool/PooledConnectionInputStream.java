 /* 
 PersonalHttpProxy 1.5
 Copyright (C) 2013-2014 Ingo Zenz

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

package util.conpool;

import java.io.IOException;
import java.io.InputStream;

public class PooledConnectionInputStream extends InputStream {
	
	private InputStream in = null;
	private boolean valid = true;
	private long traffic = 0;
	
	public PooledConnectionInputStream (InputStream in) {
		this.in = in;
	}
	
	public void invalidate() {
		valid = false;
	}

	@Override
	public int read() throws IOException {
		int r = in.read();		
		if (!valid)
			throw new IllegalStateException("Invalid:"+this);
		
		if (r != -1)
			traffic++;
		
		return r;
	}
	
	@Override
	public int read(byte[] b) throws IOException {
		return read(b,0,b.length);
	}
	
	@Override
	public int read(byte[] b, int offs, int len) throws IOException {
		int r = in.read(b,offs,len);
		if (!valid)
			throw new IllegalStateException("Invalid:"+this);
		
		traffic = traffic+r;
		return r;
	}
	
	@Override
	public void close () throws IOException {
		//do nothing
	}
	
	public long getTraffic() {
		return traffic;
	}


}
