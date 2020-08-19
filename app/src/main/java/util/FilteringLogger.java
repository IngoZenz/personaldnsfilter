package util;

public class FilteringLogger implements LoggerInterface {
	LoggerInterface nestedLogger;
	private String lastLog="";
	
	
	public FilteringLogger(LoggerInterface nestedLogger) {
		this.nestedLogger = nestedLogger;
	}

	@Override
	public void logLine(String txt) {
		if (!lastLog.equals(txt))
			nestedLogger.logLine(txt);
		lastLog = txt;
	}

	@Override
	public void logException(Exception e) {
		nestedLogger.logException(e);
	}

	@Override
	public void log(String txt) {
		if (!lastLog.equals(txt))
			nestedLogger.log(txt);
		lastLog = txt;
	}

	@Override
	public void message(String txt) {
			nestedLogger.message(txt);
	}

	@Override
	public void closeLogger() {
			nestedLogger.closeLogger();
	}


}
