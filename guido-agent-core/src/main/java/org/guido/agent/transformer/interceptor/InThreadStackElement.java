package org.guido.agent.transformer.interceptor;

public class InThreadStackElement {
	public Object[] reference;
	public long startNanoTime;
	public long stopNanoTime;
	public long deltaTime;
	
	public InThreadStackElement() {
	}

	public void start(Object[] reference) {
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