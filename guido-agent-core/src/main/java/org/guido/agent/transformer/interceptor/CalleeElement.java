package org.guido.agent.transformer.interceptor;

import static org.guido.util.ToStringHelper.toStringHelper;

public class CalleeElement {
	long refIndex;
	long totalCalls;
	long maxDuration;
	long minDuration;
	long totalDuration;
	String methodCalled;
	
	static public class CalleeElementBuilder {
		CalleeElement calleeElement = new CalleeElement();
		private CalleeElementBuilder() {
			
		}
		public CalleeElementBuilder withMinMaxTotalNbCallsDuration(long min, long max, long total, long nbCalls) {
			calleeElement.minDuration = min;
			calleeElement.maxDuration = max;
			calleeElement.totalDuration = total;
			calleeElement.totalCalls = nbCalls;
			return this;
		}
		public CalleeElementBuilder withMethodCalled(String method) {
			calleeElement.methodCalled = method;
			return this;
		}
		public CalleeElement build() {
			return calleeElement;
		}
	}
	
	static public CalleeElementBuilder builder() {
		return new CalleeElementBuilder();
	}
	
	public double getMax() {
		return ((double)maxDuration)/1000.00;
	}

	public double getMin() {
		return ((double)minDuration)/1000.00;
	}

	public double getAvg() {
		if(totalCalls == 0) {
			return 0.0; // should never happen as we create a callee only after a call has been achieved.
		}
		return ((double)(totalDuration)/1000.00)/(totalCalls);
	}

	public String getMethodCalled() {
		return methodCalled;
	}

	public CalleeElement duplicate() {
		CalleeElement element = new CalleeElement();
		element.refIndex = refIndex;
		element.totalCalls = totalCalls;
		element.maxDuration = maxDuration;
		element.minDuration = minDuration;
		element.totalDuration = totalDuration;
		element.methodCalled = methodCalled;
		return element;
	}

	public String toString() {
		return toStringHelper(this)
				.add("refIndex", refIndex)
				.add("totalCalls", totalCalls)
				.add("maxDuration", maxDuration)
				.add("minDuration", minDuration)
				.add("totalDuration", totalDuration)
				.add("methodCalled", methodCalled)
			.toString();
	}

}
