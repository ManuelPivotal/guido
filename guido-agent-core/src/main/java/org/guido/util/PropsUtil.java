package org.guido.util;

import org.guido.agent.transformer.logger.GuidoLogger;

public class PropsUtil {
	
	//private static GuidoLogger LOG = GuidoLogger.getLogger(PropsUtil.class);
	
	public static String getPropOrEnv(String name, String defaultValue) {
		String value = System.getProperty(name);
		if(value == null) {
			value = getEnv(name);
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
			value = getEnv(name);
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
			value = getEnv(name);
		}
		if(value == null) {
			return defaultValue;
		}
		return Integer.valueOf(value);
	}
	
	public static String turnToEnvName(String name) {
		return name.toUpperCase().replaceAll("\\.", "_");
	}
	
	private static String getEnv(String name) {
		if(name == null) {
			return null;
		}
		return System.getenv(turnToEnvName(name));
	}
}
