package org.guido.agent.transformer.logger;

import static org.guido.util.ExceptionUtil.getRootCause;

import java.util.Date;

import oss.guido.org.slf4j.helpers.MessageFormatter;

public class GuidoLogger {
	static int globalLogLevel = 2; // default is error
	
	static final public int DEBUG = 0;
	static final public int INFO = 1;
	static final public int ERROR = 2;

	static final String DEBUG_LEVEL = "DEBUG";
	static final String INFO_LEVEL = "INFO";
	static final String ERROR_LEVEL = "ERROR";

	private String name;
	
	public static int setGlobalLogLevel(int level) {
		int lastLevel = globalLogLevel;
		globalLogLevel = level;
		return lastLevel;
	}

	public static void setGlobalLogLevel(String level) {
		if(level != null) {
			level = level.toUpperCase();
			if(DEBUG_LEVEL.equals(level)) {globalLogLevel = DEBUG;}
			if(INFO_LEVEL.equals(level)) {globalLogLevel = INFO;}
			if(ERROR_LEVEL.equals(level)) {globalLogLevel = ERROR;}
		}
	}
	
	protected GuidoLogger(String name) {
		this.name = name;
	}
	
	static public GuidoLogger getLogger(String name) {
		return new GuidoLogger(name);
	}

	public void debug(String format, Object...objects) {
	   String formattedMessage = MessageFormatter.arrayFormat(format, objects).getMessage();
	   dumpOut(DEBUG_LEVEL, DEBUG, formattedMessage);
	}

	public void info(String format, Object...objects) {
	   String formattedMessage = MessageFormatter.arrayFormat(format, objects).getMessage();
	   dumpOut(INFO_LEVEL, INFO, formattedMessage);
	}

	public void error(String msg, Object...objects) {
		error(null, msg, objects);
	}

	public void error(Throwable throwable, String format, Object...objects) {
		String formattedMessage = MessageFormatter.arrayFormat(format, objects).getMessage();
		dumpOut(ERROR_LEVEL, ERROR, formattedMessage, throwable);
	}

	private void dumpOut(String level, int intLevel, String message) {
		dumpOut(level, intLevel, message, null);
	}
	
	private void dumpOut(String level, int intLevel, String message, Throwable exception) {
		if(intLevel >= globalLogLevel) {
			System.out.println(String.format("GuidoAgent[%s] - [%s] - %s - %s - %s",
					name,
					Thread.currentThread().getName(),
					new Date(), 
					level, 
					message));
			if (exception != null) {
				Throwable t = getRootCause(exception);
				String errorMessage = t.getClass().getSimpleName();
				String exceptionMessage = t.getMessage();
				if(exceptionMessage != null) {
					errorMessage += " " + exceptionMessage;
				}
				System.out.println(errorMessage);
				exception.printStackTrace();
			}
			System.out.flush();
		}
	}

	public static GuidoLogger getLogger(Class<?> clazz) {
		return getLogger(clazz.getSimpleName());
	}
}
