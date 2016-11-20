package org.guido.agent.transformer.interceptor;

import static org.guido.util.ToStringHelper.toStringHelper;

import org.guido.util.ToStringHelper;

public class InThreadStackElement {
	
	static public int maxCallee = 20;
	static public void setMaxCallee(int max) {
		maxCallee = max;
	}
	static public int getMaxCallee() {
		return maxCallee;
	}
	
	public Object[] reference;
	public long startNanoTime;
	public long stopNanoTime;
	public long duration;
	public int refIndex;
	public int totalCallees = 0;
	public long calleesDuration;
	public CalleeElement[] calleeElements;
	
	public InThreadStackElement() {
		calleeElements = new CalleeElement[maxCallee];
		for(int index = 0; index < maxCallee; index++) {
			calleeElements[index] = new CalleeElement();
		}
	}

	public void start(int index, Object[] reference) {
		this.refIndex = index;
		this.reference = reference;
		this.startNanoTime = System.nanoTime();
		this.stopNanoTime = -1;
		this.duration = -1;
		this.totalCallees = 0;
		this.calleesDuration = 0;
	}
	
	public long unaccountedDuration() {
		return (duration - calleesDuration);
	}

	public void addCallee(long refIndex, long duration, String methodCalled) {
		int index = 0;
		for(index = 0; index < totalCallees; index++) {
			CalleeElement element = calleeElements[index];
			if(element.refIndex == refIndex) {
				element.totalCalls++;
				element.totalDuration += duration;
				if(duration > element.maxDuration) {
					element.maxDuration = duration;
				}
				if(duration < element.minDuration) {
					element.minDuration = duration;
				}
				calleesDuration += duration;
				return;
			}
		}
		if(totalCallees == maxCallee) {
			return; // all taken
		}
		CalleeElement element = calleeElements[totalCallees];
		totalCallees++;
		element.totalCalls = 1;
		element.refIndex = refIndex;
		element.maxDuration = element.minDuration = element.totalDuration = duration;
		element.methodCalled = methodCalled;
		calleesDuration += duration;
	}

	public InThreadStackElement stop() {
		stopNanoTime = System.nanoTime();
		duration = stopNanoTime - startNanoTime;
		return this;
	}
	
	public String toString() {
		ToStringHelper helper = toStringHelper(this)
				.add("refIndex", refIndex)
				.add("calleeElements", calleeElements);
		for(int index = 0; index < totalCallees; index++) {
			helper.add("calleeElements[" + index + "]", calleeElements[index]);
		}
		return helper.toString();
	}
	
	Object[] noCallees = new Object[0];
	
	public Object duplicateCalleeElements() {
		if(totalCallees == 0) {
			return noCallees;
		}
		Object[] elements = new Object[totalCallees];
		for(int index = 0; index < totalCallees; index++) {
			elements[index] = calleeElements[index].duplicate();
		}
		return elements;
	}
}