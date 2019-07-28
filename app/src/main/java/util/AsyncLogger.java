/* 
PersonalHttpProxy 1.5 
Copyright (C) 2013-2016 Ingo Zenz

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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class AsyncLogger implements LoggerInterface, Runnable {

	// Log Type constants
	static final int LOG = 1;
	static final int LOG_LN = 2;
	static final int LOG_MSG = 3;
	static final int LOG_EXC = 4;

	private LoggerInterface out = null;
	private DataOutputStream pout;
	private PipedInputStream pin;
	private boolean closed = false;

	public AsyncLogger(LoggerInterface out) throws IOException {
		this.out = out;
		logOpen();
	}


	
	private void logOpen() throws IOException {
		pin = new PipedInputStream(10240);
		pout = new DataOutputStream(new PipedOutputStream(pin));


		Thread t = new Thread(this);
		t.setDaemon(true);
		t.start();
	}

	public void closeLogger() {
		synchronized (pin) {
			try {
				closed = true;
				pout.close();				
				pin.notifyAll();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}


	private void writeLog(int type, byte[] bytes) {
		try {
			pout.writeShort(type);
			pout.writeInt(bytes.length);
			pout.write(bytes);
			pout.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void logLine(String txt) {

		synchronized (pout) {
			synchronized (pin) {
				writeLog(LOG_LN, txt.getBytes());
				pin.notifyAll();
			}
		}
	}

	@Override
	public void logException(Exception e) {
		byte[] bytes =null;
		try {
			bytes = Utils.serializeObject(e);
		} catch (IOException e1) {
			e1.printStackTrace();
			return;
		}

		synchronized (pout) {
			synchronized (pin) {
				writeLog(LOG_EXC, bytes);
				pin.notifyAll();
			}
		}
	}

	@Override
	public void log(String txt) {
		synchronized (pout) {
			synchronized (pin) {
				writeLog(LOG, txt.getBytes());
				pin.notifyAll();
			}
		}
	}

	@Override
	public void message(String txt) {
		synchronized (pout) {
			synchronized (pin) {
				writeLog(LOG_MSG, txt.getBytes());
				pin.notifyAll();
			}
		}
	}

	@Override
	public void run() {

		byte[] buf = new byte[4096];
		int r = 0;
		DataInputStream in = new DataInputStream(pin);
		
		while (!closed) {
			try {
				int type= -1;
				byte[] bytes=null;

				synchronized (pin) {
					while ((pin.available() <= 0) && !closed) {
						try {
							pin.wait();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					if (closed)
						break;

					type = in.readShort();
					bytes = new byte[in.readInt()];
					in.readFully(bytes);
				}

				switch (type) {

					case LOG_LN:
						out.logLine(new String(bytes));
						break;
					case LOG:
						out.log(new String(bytes));
						break;
					case LOG_MSG:
						out.message(new String(bytes));
						break;
					case LOG_EXC:
						out.logException((Exception) Utils.deserializeObject(bytes));
						break;

					default:
						throw new IOException("Unknown log Msg type: " + type);
				}

			} catch (Exception e) {
				if (!closed)
					e.printStackTrace();
			}

		}
	}
}
