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
import java.io.OutputStream;

public class PooledConnectionOutputStream extends OutputStream {
	private OutputStream out;
	private boolean valid = true;
	private long traffic = 0;
	
	public PooledConnectionOutputStream(OutputStream out){
		this.out= out;
	}
	
	public void invalidate() {
		valid = false;
	}

	@Override
	public void write(int b) throws IOException {
		out.write(b);
		traffic++;
		if (!valid)
			throw new IllegalStateException("Invalid:"+this);
	}
	
	@Override
	public void write(byte[] b) throws IOException {
		out.write(b);
		traffic = traffic+b.length;
		if (!valid)
			throw new IllegalStateException("Invalid:"+this);

	}
	
	@Override
	public void write(byte[] b, int offs, int len) throws IOException {
		out.write(b, offs,len);
		traffic = traffic+len;
		if (!valid)
			throw new IllegalStateException("Invalid:"+this);
	}
	
	@Override
	public void close () throws IOException {
		//do nothing 
	}
	
	@Override
	public void flush() throws IOException {
		out.flush();
		if (!valid)
			throw new IllegalStateException("Invalid:"+this);
	}
	
	public long getTraffic() {
		return traffic;
	}



}
