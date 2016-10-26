package org.guido.agent.transformer.interceptor;

public class SimpleOpInteger {
	public int value = -1;
	
	public int get() {
		return value;
	}

	public int addAndGet() {
		value++;
		return value;
	}
	public int getAndDec() {
		value--;
		return value + 1;
	}
}