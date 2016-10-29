package org.guido.util;

public class ExceptionUtil {
	
	public static Throwable getRootCause(Throwable throwable) {
		Throwable cause;
		while ((cause = throwable.getCause()) != null) {
			throwable = cause;
		}
		return throwable;
	}
}
