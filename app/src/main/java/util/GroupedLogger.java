package util;

import java.util.Vector;

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

	public void attachLogger(LoggerInterface logger) {

		LoggerInterface[] nestedLoggersNew = new LoggerInterface[this.nestedLoggers.length+1];
		for (int i = 0; i < this.nestedLoggers.length; i++) {
			nestedLoggersNew[i] = this.nestedLoggers[i];
		}
		nestedLoggersNew[nestedLoggers.length] = logger;
		this.nestedLoggers = nestedLoggersNew;
	}

	public void detachLogger(LoggerInterface logger){
		Vector nestedLoggers = new Vector<LoggerInterface>(this.nestedLoggers.length);

		for (int i = 0; i< this.nestedLoggers.length; i++){
			if (this.nestedLoggers[i] != logger)
				nestedLoggers.add(this.nestedLoggers[i]);
		}

		this.nestedLoggers= (LoggerInterface[]) nestedLoggers.toArray(new LoggerInterface[nestedLoggers.size()]);
	}

}
