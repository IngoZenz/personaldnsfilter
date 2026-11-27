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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;

public class FileLogger implements LoggerInterface, Runnable {

	private String logFolderPath;
	private String name;
	private long slotSize;
	private int slotCount;
	private String header;
	private long curSlotSize = 0;
	private int curSlot = 0;
	private OutputStream fout = null;
	private final LinkedList<byte[]> queue = new LinkedList<byte[]>();
	private volatile boolean closed = false;
	private volatile boolean timeStampEnabled = false;

	public FileLogger(String logFolderPath, String name, long slotSize, int slotCount, String header) throws IOException {

		if (slotSize < 1 || slotCount < 1)
			throw new IllegalArgumentException("slotSize and slotCount must not be less than 1");
		this.logFolderPath = logFolderPath+"/"+name;
		this.name = name;
		this.slotSize = slotSize;
		this.slotCount = slotCount;
		this.header = header;
		logOpen();
	}

	private void logOpen() throws IOException {

		File dir = new File(logFolderPath);
		if (!dir.exists())
			dir.mkdirs();

		long ts = 0;
		File f = null;
		for (int i = 0; i < slotCount; i++) {
			f = new File(logFolderPath + "/" + name + "_" + i + ".log");
			if (f.exists() && f.lastModified() > ts) {
				ts = f.lastModified();
				curSlotSize = f.length();
				curSlot = i;
			}
		}

		fout = new FileOutputStream(new File(logFolderPath + "/" + name + "_" + curSlot + ".log"), true);

		// Write log file header for new files
		if (curSlotSize == 0 && header != null) {
			fout.write((header + "\r\n").getBytes());
			fout.flush();
		}

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

	public void enableTimestamp(boolean enable) {
		timeStampEnabled = enable;
	}

	private void enqueue(String text) {
		if (closed)
			return;
		String msg = timeStampEnabled ? (DateRetriever.getDateString() + " " + text) : text;
		byte[] bytes = msg.getBytes();
		synchronized (queue) {
			if (!closed) {
				queue.addLast(bytes);
				queue.notifyAll();
			}
		}
	}

	@Override
	public void logLine(String txt) {
		enqueue(txt + System.getProperty("line.separator", "\n"));
	}

	@Override
	public void logException(Exception e) {
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		enqueue(sw.toString());
	}

	@Override
	public void log(String txt) {
		enqueue(txt);
	}

	@Override
	public void message(String txt) {
		log(txt);
	}

	private OutputStream getOutputStream() throws IOException {
		if (curSlotSize < slotSize)
			return fout;
		else {

			fout.flush();
			fout.close();
			curSlot = (curSlot + 1) % slotCount;
			File f = new File(logFolderPath + "/" + name + "_" + curSlot + ".log");
			fout = new FileOutputStream(f);
			curSlotSize = 0;

			// Write log file header for new files
			if (header != null) {
				fout.write((header + "\r\n").getBytes());
				fout.flush();
			}
			return fout;
		}
	}

	@Override
	public void run() {
		try {
			while (true) {
				byte[] next;
				boolean queueEmptyAfterPop;
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
					next = queue.removeFirst();
					queueEmptyAfterPop = queue.isEmpty();
				}

				try {
					OutputStream out = getOutputStream();
					out.write(next);
					curSlotSize = curSlotSize + next.length;
					if (queueEmptyAfterPop)
						out.flush();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} finally {
			if (fout != null) {
				try {
					fout.flush();
					fout.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
