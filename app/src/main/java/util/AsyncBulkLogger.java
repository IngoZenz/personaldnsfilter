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

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;

public class AsyncBulkLogger implements LoggerInterface, Runnable {

	private LoggerInterface out = null;
	private PipedOutputStream pout;
	private PrintStream psout;
	private PipedInputStream pin;
	private boolean closed = false;
	private boolean timeStampEnabled = false;

	public AsyncBulkLogger(LoggerInterface out) throws IOException {
		this.out = out;
		logOpen();
	}

	public void enableTimestamp(boolean enable) {
		timeStampEnabled = enable;
	}
	
	private void logOpen() throws IOException {
		pin = new PipedInputStream(10240);
		pout = new PipedOutputStream(pin);
		psout = new PrintStream(pout, true);

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


	@Override
	public void logLine(String txt) {
		synchronized (psout) {
			synchronized (pin) {
				if (timeStampEnabled)
					psout.print(DateRetriever.getDateString() + " ");
				psout.println(txt);
				pin.notifyAll();
			}
		}
	}

	@Override
	public void logException(Exception e) {
		synchronized (psout) {
			synchronized (pin) {
				if (timeStampEnabled)
					psout.print(DateRetriever.getDateString() + " ");
				e.printStackTrace(psout);
				pin.notifyAll();
			}
		}
	}

	@Override
	public void log(String txt) {
		synchronized (psout) {
			synchronized (pin) {
				if (timeStampEnabled)
					psout.print(DateRetriever.getDateString() + " ");
				psout.print(txt);
				pin.notifyAll();
			}
		}
	}

	@Override
	public void message(String txt) {
		synchronized (pin) {
			out.message(txt); // write message out directly as it might go to different channel
		}
	}

	@Override
	public void run() {

		byte[] buf = new byte[4096];
		int r = 0;
		
		while (!closed) {
			try {
				synchronized (pin) {

					while ((pin.available() <= 0) && !closed) {
						try {
							pin.wait();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					if (!closed)
						r = pin.read(buf);
				}

				if (!closed) {
					
					out.log(new String(buf, 0, r));
					
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

}
