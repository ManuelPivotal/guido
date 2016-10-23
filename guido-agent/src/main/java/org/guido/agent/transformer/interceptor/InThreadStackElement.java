package org.guido.agent.transformer.interceptor;

import java.util.Map;


public class InThreadStackElement {
	public Map<String, String> reference;
	public long startNanoTime;
	public long stopNanoTime;
	public long deltaTime;
	public String sudoThreadId;
	
	public InThreadStackElement(String sudoThreadId) {
		this.sudoThreadId = sudoThreadId;
	}

	public void start(Map<String, String> reference) {
		this.reference = reference;
		this.startNanoTime = System.nanoTime();
		this.stopNanoTime = -1;
		this.deltaTime = -1;
	}

	public InThreadStackElement stop() {
		stopNanoTime = System.nanoTime();
		deltaTime = stopNanoTime - startNanoTime;
		return this;
	}
}