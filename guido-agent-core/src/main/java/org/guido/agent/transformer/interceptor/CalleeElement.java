package org.guido.agent.transformer.interceptor;

import static org.guido.util.ToStringHelper.toStringHelper;
import oss.guido.com.fasterxml.jackson.annotation.JsonProperty;

public class CalleeElement implements Comparable<CalleeElement> {
	long refIndex;
	long totalCalls;
	long maxDuration;
	long minDuration;
	long totalDuration = 0;
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
	
	@JsonProperty("count")
	public long getTotalCalls() {
		return totalCalls;
	}
	
	@JsonProperty("max_duration")
	public double getMax() {
		return ((double)maxDuration)/1000.00;
	}

	@JsonProperty("min_duration")
	public double getMin() {
		return ((double)minDuration)/1000.00;
	}

	@JsonProperty("total_duration")
	public double getTotal() {
		return ((double)totalDuration)/1000.00;
	}

	@JsonProperty("avg_duration")
	public double getAvg() {
		if(totalCalls == 0) {
			return 0.0; // should never happen as we create a callee only after a call has been achieved.
		}
		double result = ((double)(totalDuration))/(totalCalls);
		return result / 1000.00;
	}
	
	@JsonProperty("name")
	public String getMethodCalled() {
		return methodCalled;
	}
	
	@Override
	public int compareTo(CalleeElement other) {
		return (int)(other.totalDuration - totalDuration);
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

	public void reset() {
		this.refIndex = 0;
		this.totalCalls = 0;
		this.maxDuration = 0;
		this.minDuration = 0;
		this.totalDuration = 0;
		this.methodCalled = null;
	}

	public String toString() {
		return toStringHelper(this)
				.add("refIndex", refIndex)
				.add("totalDuration", totalDuration)
				.add("totalCalls", getTotalCalls())
				.add("maxDuration", getMax())
				.add("minDuration", getMin())
				.add("methodCalled", methodCalled)
				.add("avg", getAvg())
			.toString();
	}

}
