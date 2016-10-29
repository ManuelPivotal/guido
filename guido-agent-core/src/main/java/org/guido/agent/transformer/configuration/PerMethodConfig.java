package org.guido.agent.transformer.configuration;

public class PerMethodConfig {
	boolean allowed;
	long threshold;
	
	public PerMethodConfig(long threshold, boolean allowed) {
		this.threshold = threshold;
		this.allowed = allowed;
	}
	public boolean isAllowed() {return allowed;}
	public void setAllowed(boolean allowed) {this.allowed = false;}
	
	public long getThreshold() {return threshold;}
	public void setThreshold(long threshold) {this.threshold = threshold;}
	
	public String toString() {
		return "allowed=" + allowed + ", threshold=" + threshold;
	}
}
