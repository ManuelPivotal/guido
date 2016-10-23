package org.guido.agent.transformer.logger;

import java.util.Date;

public class GuidoLogger {
	static final int globalLevel = 0;
	
	static final int DEBUG = 0;
	static final int INFO = 1;
	static final int ERROR = 2;

	static final String DEBUG_LEVEL = "DEBUG";
	static final String INFO_LEVEL = "INFO";
	static final String ERROR_LEVEL = "ERROR";

	static public void debug(String msg) {
		dumpOut(DEBUG_LEVEL, DEBUG, msg);
	}

	static public void info(String msg) {
   		dumpOut(INFO_LEVEL, INFO, msg);
	}

	static public void error(String msg) {
		error(msg, null);
	}

	private static void dumpOut(String level, int intLevel, String message) {
		dumpOut(level, intLevel, message, null);
	}
	
	private static void dumpOut(String level, int intLevel, String message, Exception exception) {
		if(intLevel < globalLevel) {
			return;
		}
		System.out.println(String.format("GuidoAgent - %d - %s - %s - %s",
				Thread.currentThread().getId(),
				new Date(), 
				level, 
				message));
	
		if (exception != null) {
			Throwable t = getRootCause(exception);
			System.out.println(t.getClass());
			System.out.println(t.getMessage());
			//t.printStackTrace();
		}
	}
	

	public static void error(String msg, Exception exception) {
		dumpOut(ERROR_LEVEL, ERROR, msg, exception);
	}

	public static Throwable getRootCause(Throwable throwable) {
		Throwable cause;
		while ((cause = throwable.getCause()) != null) {
			throwable = cause;
		}
		return throwable;
	}

	public static void noop(String string) {
	}
}
