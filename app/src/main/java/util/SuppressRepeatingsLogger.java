package util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SuppressRepeatingsLogger implements LoggerInterface {
	LoggerInterface nestedLogger;
	private HashMap<String, Long> lastLogs = new HashMap<String, Long>();
	private long timeRepeat = 0;
	private long lastCleanup = 0;
	// volatile: setTimestampFormat() is called from other threads (typically null first, then the pattern)
	volatile DateFormat dateFormatter = null; //new SimpleDateFormat("H:mm:ss");
	volatile String lastTS="";


	public SuppressRepeatingsLogger(LoggerInterface nestedLogger) {
		this.nestedLogger = nestedLogger;
	}

	private void addTimeStamp() {
		// work on a snapshot: the field can be reset to null by another thread between check and use
		DateFormat formatter = dateFormatter;
		if (formatter == null)
			return;
		String ts;
		synchronized (formatter) { // DateFormat/SimpleDateFormat is not thread safe
			ts = formatter.format(new Date());
		}
		if ((!ts.equals(lastTS))) {
			nestedLogger.logLine(ts);
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

	public void setTimestampFormat(String timeStampPattern) {
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
		if (!repeatingLog(txt)) {
			addTimeStamp();
			nestedLogger.logLine(txt);
		}
	}

	@Override
	public void logException(Exception e) {
		addTimeStamp();
		nestedLogger.logException(e);
	}

	@Override
	public void log(String txt) {
		if (!repeatingLog(txt))
			nestedLogger.log(txt);
	}

	@Override
	public void message(String txt) {
		nestedLogger.message(txt);
	}

	@Override
	public void closeLogger() {
		lastLogs.clear();
		nestedLogger.closeLogger();
	}


}