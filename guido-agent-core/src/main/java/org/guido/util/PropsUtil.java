package org.guido.util;

public class PropsUtil {
	
	public static String getPropOrEnv(String name, String defaultValue) {
		String value = System.getProperty(name);
		if(value == null) {
			value = System.getenv(name);
		}
		return (value == null) ? defaultValue : value;
	}
	
	public static String getPropOrEnv(String name) {
		return getPropOrEnv(name, null);
	}

	public static long toNano(String thresholdProp) {
		double coef = Double.valueOf(thresholdProp);
		return (long)(1000000.0 * coef);
	}

	public static boolean getPropOrEnvBoolean(String name, boolean defaultValue) {
		String value = System.getProperty(name);
		if(value == null) {
			value = System.getenv(name);
		}
		if(value == null) {
			return defaultValue;
		}
		return Boolean.parseBoolean(value);
	}

	public static boolean getPropOrEnvBoolean(String name) {
		return getPropOrEnvBoolean(name, false);
	}

	public static int getPropOrEnvInt(String name, int defaultValue) {
		String value = System.getProperty(name);
		if(value == null) {
			value = System.getenv(name);
		}
		if(value == null) {
			return defaultValue;
		}
		return Integer.valueOf(value);
	}
}
