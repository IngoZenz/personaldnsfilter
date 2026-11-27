package util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SuppressRepeatingsLogger implements LoggerInterface {
	volatile LoggerInterface nestedLogger;
	private HashMap<String, Long> lastLogs = new HashMap<String, Long>();
	private volatile long timeRepeat = 0;
	private long lastCleanup = 0;
	DateFormat dateFormatter = null; //new SimpleDateFormat("H:mm:ss");
	String lastTS="";


	public SuppressRepeatingsLogger(LoggerInterface nestedLogger) {
		this.nestedLogger = nestedLogger;
	}

	private synchronized void addTimeStamp() {
		LoggerInterface nl = nestedLogger;
		if (dateFormatter == null || nl == null)
			return;
		String ts = dateFormatter.format(new Date());
		if ((!ts.equals(lastTS))) {
			nl.logLine(ts);
			lastTS=ts;
		}
	}


	public void setNestedLogger(LoggerInterface nestedLogger) {
		this.nestedLogger = nestedLogger;
	}

	public LoggerInterface getNestedLogger() {
		return nestedLogger;
	}

	public void setSuppressTime(long suppressTime) {
		timeRepeat = suppressTime;
	}

	public synchronized void setTimestampFormat(String timeStampPattern) {
		if (timeStampPattern != null)
			dateFormatter = new SimpleDateFormat(timeStampPattern);
		else
			dateFormatter = null;
	}

	private boolean repeatingLog(String logStr){

		long lastLogged = 0;

		synchronized (lastLogs) {
			long current = System.currentTimeMillis();

			//check when last Logged
			Long entry = lastLogs.get(logStr);
			if (entry != null)
				lastLogged = entry.longValue();

			//update lastLogged
			lastLogs.put(logStr, current);

			//trigger cleanup of old logs in case not done in last time frame
			if (current - lastCleanup > timeRepeat) {
				Map.Entry<String, Long>[] entries = lastLogs.entrySet().toArray(new Map.Entry[lastLogs.size()]);
				for (int i = 0; i < entries.length; i++){
					if (current - entries[i].getValue().longValue() > timeRepeat)
						lastLogs.remove(entries[i].getKey());
				}
				lastCleanup = current;
			}

			return (current-lastLogged <= timeRepeat);
		}
	}

	@Override
	public void logLine(String txt) {
		LoggerInterface nl = nestedLogger;
		if (nl != null && !repeatingLog(txt)) {
			addTimeStamp();
			nl.logLine(txt);
		}
	}

	@Override
	public void logException(Exception e) {
		LoggerInterface nl = nestedLogger;
		if (nl != null) {
			addTimeStamp();
			nl.logException(e);
		}
	}

	@Override
	public void log(String txt) {
		LoggerInterface nl = nestedLogger;
		if (nl != null && !repeatingLog(txt))
			nl.log(txt);
	}

	@Override
	public void message(String txt) {
		LoggerInterface nl = nestedLogger;
		if (nl != null)
			nl.message(txt);
	}

	@Override
	public void closeLogger() {
		synchronized (lastLogs) {
			lastLogs.clear();
		}
		LoggerInterface nl = nestedLogger;
		if (nl != null)
			nl.closeLogger();
	}


}
