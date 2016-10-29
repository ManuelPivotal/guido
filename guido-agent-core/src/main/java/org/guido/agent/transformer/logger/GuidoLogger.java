package org.guido.agent.transformer.logger;

import static org.guido.util.ExceptionUtil.getRootCause;

import java.util.Date;

import org.guido.util.ExceptionUtil;

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

	public void error(Throwable throwable, String format, Object...objects) {
		String formattedMessage = MessageFormatter.arrayFormat(format, objects).getMessage();
		dumpOut(ERROR_LEVEL, ERROR, formattedMessage, throwable);
	}

	private void dumpOut(String level, int intLevel, String message) {
		dumpOut(level, intLevel, message, null);
	}
	
	private void dumpOut(String level, int intLevel, String message, Throwable exception) {
		if(intLevel >= globalLevel) {
			System.out.println(String.format("GuidoAgent[%s] - [%s] - %s - %s - %s",
					name,
					Thread.currentThread().getName(),
					new Date(), 
					level, 
					message));
			if (exception != null) {
				Throwable t = getRootCause(exception);
				System.out.println(t.getClass());
				String exceptionMessage = t.getMessage();
				if(message != null) {
					System.out.println(exceptionMessage);
				}
				//exception.printStackTrace();
			}
			System.out.flush();
		}
	}
}
