package util;

public class GroupedLogger implements LoggerInterface {
	LoggerInterface[] nestedLoggers;
	
	
	public GroupedLogger(LoggerInterface[] nestedLoggers) {
		this.nestedLoggers=nestedLoggers;
	}

	@Override
	public void logLine(String txt) {
		for (int i = 0; i <nestedLoggers.length; i++)
			nestedLoggers[i].logLine(txt);
	}

	@Override
	public void logException(Exception e) {
		for (int i = 0; i <nestedLoggers.length; i++)
			nestedLoggers[i].logException(e);

	}

	@Override
	public void log(String txt) {
		for (int i = 0; i <nestedLoggers.length; i++)
			nestedLoggers[i].log(txt);
	}

	@Override
	public void message(String txt) {
		for (int i = 0; i <nestedLoggers.length; i++)
			nestedLoggers[i].message(txt);
	}

	@Override
	public void closeLogger() {
		for (int i = 0; i <nestedLoggers.length; i++)
			nestedLoggers[i].closeLogger();
	}

}
