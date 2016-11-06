package org.guido.agent.transformer.configuration;

public class PatternMethodConfig {
	String className;
	boolean allowed;
	long threshold;
	boolean isInterface;
	
	public PatternMethodConfig(String className, long threshold, boolean allowed, boolean isInterface) {
		this.className = className;
		this.threshold = threshold;
		this.allowed = allowed;
		this.isInterface = isInterface;
	}
	public String getClassName() {return className;}
	public boolean isAllowed()   {return allowed;}
	public long getThreshold()   {return threshold;}
	public boolean isInterface() {return isInterface;}
	public String toString() {
		return "<allowed=" + allowed 
				+ ", threshold=" + threshold 
				+ ",path=" + className 
				+ "isInterace=" + isInterface
				+ ">";
	}
}