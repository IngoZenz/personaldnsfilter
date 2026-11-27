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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;

public class AsyncBulkLogger implements LoggerInterface, Runnable {

	private static final int MAX_QUEUE_SIZE = 2048;

	private final LoggerInterface out;
	private final LinkedList<String> queue = new LinkedList<String>();
	private volatile boolean closed = false;
	private volatile boolean timeStampEnabled = false;
	private Thread worker;

	public AsyncBulkLogger(LoggerInterface out) throws IOException {
		this.out = out;
		logOpen();
	}

	public void enableTimestamp(boolean enable) {
		timeStampEnabled = enable;
	}

	private void logOpen() {
		worker = new Thread(this, "AsyncBulkLogger");
		worker.setDaemon(true);
		worker.start();
	}

	@Override
	public void closeLogger() {
		synchronized (queue) {
			closed = true;
			queue.notifyAll();
		}
	}

	private void enqueue(String text) {
		if (closed || text == null)
			return;
		synchronized (queue) {
			if (closed)
				return;
			if (queue.size() >= MAX_QUEUE_SIZE) {
				queue.removeFirst();
			}
			queue.addLast(text);
			queue.notifyAll();
		}
	}

	@Override
	public void logLine(String txt) {
		String prefix = timeStampEnabled ? DateRetriever.getDateString() + " " : "";
		enqueue(prefix + txt + "\n");
	}

	@Override
	public void logException(Exception e) {
		StringWriter sw = new StringWriter();
		if (timeStampEnabled)
			sw.write(DateRetriever.getDateString() + " ");
		e.printStackTrace(new PrintWriter(sw));
		enqueue(sw.toString());
	}

	@Override
	public void log(String txt) {
		String prefix = timeStampEnabled ? DateRetriever.getDateString() + " " : "";
		enqueue(prefix + txt);
	}

	@Override
	public void message(String txt) {
		synchronized (queue) {
			out.message(txt); // write message out directly as it might go to different channel
		}
	}

	@Override
	public void run() {
		StringBuilder batch = new StringBuilder();
		while (true) {
			batch.setLength(0);
			synchronized (queue) {
				while (queue.isEmpty() && !closed) {
					try {
						queue.wait();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}
				}
				if (closed && queue.isEmpty()) {
					return;
				}
				while (!queue.isEmpty() && batch.length() < 16384) {
					batch.append(queue.removeFirst());
				}
			}
			try {
				if (batch.length() > 0) {
					out.log(batch.toString());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
