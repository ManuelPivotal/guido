package org.guido.agent.transformer;

public class PerClassConfig {
	String className;
	boolean allowed;
	long threshold;
	
	public PerClassConfig(String className, long threshold, boolean allowed) {
		this.className = className;
		this.threshold = threshold;
		this.allowed = allowed;
	}
	public String getClassName() {return className;}
	public boolean isAllowed() {return allowed;}
	public long getThreshold() {return threshold;}
	
	public String toString() {
		return "allowed=" + allowed + ", threshold=" + threshold + ",path=" + className;
	}
}