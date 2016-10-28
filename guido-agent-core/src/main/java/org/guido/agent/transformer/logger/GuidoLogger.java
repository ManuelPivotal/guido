package org.guido.agent.transformer.logger;

import java.util.Date;

import oss.guido.org.slf4j.helpers.MessageFormatter;

public class GuidoLogger {
	static final int globalLevel = 0;
	
	static final int DEBUG = 0;
	static final int INFO = 1;
	static final int ERROR = 2;

	static final String DEBUG_LEVEL = "DEBUG";
	static final String INFO_LEVEL = "INFO";
	static final String ERROR_LEVEL = "ERROR";
	
	private String name;
	
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
	   dumpOut(DEBUG_LEVEL, DEBUG, formattedMessage);
	}

	public void error(String msg, Object...objects) {
		error(null, msg, objects);
	}

	public void error(Exception exception, String format, Object...objects) {
		String formattedMessage = MessageFormatter.arrayFormat(format, objects).getMessage();
		dumpOut(ERROR_LEVEL, ERROR, formattedMessage, exception);
	}

	private void dumpOut(String level, int intLevel, String message) {
		dumpOut(level, intLevel, message, null);
	}
	
	private void dumpOut(String level, int intLevel, String message, Exception exception) {
		if(intLevel >= globalLevel) {
			System.out.println(String.format("GuidoAgent[%s] - %d - %s - %s - %s",
					name,
					Thread.currentThread().getId(),
					new Date(), 
					level, 
					message));
			if (exception != null) {
				System.out.println(exception.getClass());
				Throwable t = getRootCause(exception);
				System.out.println(t.getClass());
				String exceptionMessage = t.getMessage();
				if(message != null) {
					System.out.println(exceptionMessage);
				}
				//t.printStackTrace();
			}
			System.out.flush();
		}
	}

	static Throwable getRootCause(Throwable throwable) {
		Throwable cause;
		while ((cause = throwable.getCause()) != null) {
			throwable = cause;
		}
		return throwable;
	}
}
