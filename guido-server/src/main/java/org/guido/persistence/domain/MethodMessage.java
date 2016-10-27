package org.guido.persistence.domain;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.google.common.base.MoreObjects;

public class MethodMessage {
	private long depth;
	private long durationInNS;
	private String methodCalled;
	private String threadUuid;
	
	public long getDepth() {
		return depth;
	}
	public void setDepth(long depth) {
		this.depth = depth;
	}
	public long getDurationInNS() {
		return durationInNS;
	}
	public void setDurationInNS(long durationInNS) {
		this.durationInNS = durationInNS;
	}
	public String getMethodCalled() {
		return methodCalled;
	}
	public void setMethodCalled(String methodCalled) {
		this.methodCalled = methodCalled;
	}
	public String getThreadUuid() {
		return threadUuid;
	}
	public void setThreadUuid(String threadUuid) {
		this.threadUuid = threadUuid;
	}
	public String toString() {
		return toStringHelper(this)
				.add("depth", depth)
				.add("durationInNS", durationInNS)
				.add("methodCalled", methodCalled)
				.add("threadUuid", threadUuid)
	     		.toString();
	}
}
