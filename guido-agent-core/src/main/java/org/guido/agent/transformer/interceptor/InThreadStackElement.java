package org.guido.agent.transformer.interceptor;

public class InThreadStackElement {
	public Object[] reference;
	public long startNanoTime;
	public long stopNanoTime;
	public long deltaTime;
	public int index;
	
	public InThreadStackElement() {
	}

	public void start(Object[] reference, int index) {
		this.reference = reference;
		this.startNanoTime = System.nanoTime();
		this.stopNanoTime = -1;
		this.deltaTime = -1;
		this.index = index;
	}

	public InThreadStackElement stop() {
		stopNanoTime = System.nanoTime();
		deltaTime = stopNanoTime - startNanoTime;
		return this;
	}
}