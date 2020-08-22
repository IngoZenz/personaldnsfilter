package util;

import java.util.HashMap;
import java.util.Map;

public class FilteringLogger implements LoggerInterface {
	LoggerInterface nestedLogger;
	private String lastLog="";
	private HashMap<String,Long> lastLogs = new HashMap<String, Long>();
	private long timeRepeat = 2000;
	private long lastCleanup = 0;
	
	
	public FilteringLogger(LoggerInterface nestedLogger) {
		this.nestedLogger = nestedLogger;
	}

	private boolean repeatingLog(String logStr){
		long current = System.currentTimeMillis();
		long lastLogged = 0;
		synchronized (lastLogs) {

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
		if (!repeatingLog(txt))
			nestedLogger.logLine(txt);
		lastLog = txt;
	}

	@Override
	public void logException(Exception e) {
		nestedLogger.logException(e);
	}

	@Override
	public void log(String txt) {
		if (!repeatingLog(txt))
			nestedLogger.log(txt);
		lastLog = txt;
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
