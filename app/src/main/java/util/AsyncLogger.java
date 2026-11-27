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
import java.util.LinkedList;

public class AsyncLogger implements LoggerInterface, Runnable {

	// Log Type constants
	static final int LOG = 1;
	static final int LOG_LN = 2;
	static final int LOG_MSG = 3;
	static final int LOG_EXC = 4;

	private static class LogEntry {
		final int type;
		final Object payload;

		LogEntry(int type, Object payload) {
			this.type = type;
			this.payload = payload;
		}
	}

	private LoggerInterface out = null;
	private final LinkedList<LogEntry> queue = new LinkedList<LogEntry>();
	private volatile boolean closed = false;

	public AsyncLogger(LoggerInterface out) throws IOException {
		this.out = out;
		logOpen();
	}

	private void logOpen() throws IOException {
		Thread t = new Thread(this);
		t.setDaemon(true);
		t.start();
	}

	@Override
	public void closeLogger() {
		synchronized (queue) {
			closed = true;
			queue.notifyAll();
		}
	}

	private void enqueue(int type, Object payload) {
		if (closed)
			return;
		synchronized (queue) {
			if (!closed) {
				queue.addLast(new LogEntry(type, payload));
				queue.notifyAll();
			}
		}
	}

	@Override
	public void logLine(String txt) {
		enqueue(LOG_LN, txt);
	}

	@Override
	public void logException(Exception e) {
		enqueue(LOG_EXC, e);
	}

	@Override
	public void log(String txt) {
		enqueue(LOG, txt);
	}

	@Override
	public void message(String txt) {
		enqueue(LOG_MSG, txt);
	}

	@Override
	public void run() {
		while (true) {
			LogEntry entry;
			synchronized (queue) {
				while (queue.isEmpty() && !closed) {
					try {
						queue.wait();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}
				}
				if (queue.isEmpty() && closed)
					break;
				entry = queue.removeFirst();
			}

			try {
				switch (entry.type) {
					case LOG_LN:
						out.logLine((String) entry.payload);
						break;
					case LOG:
						out.log((String) entry.payload);
						break;
					case LOG_MSG:
						out.message((String) entry.payload);
						break;
					case LOG_EXC:
						out.logException((Exception) entry.payload);
						break;
					default:
						throw new IOException("Unknown log Msg type: " + entry.type);
				}
			} catch (Exception e) {
				if (!closed)
					e.printStackTrace();
			}
		}
	}
}
