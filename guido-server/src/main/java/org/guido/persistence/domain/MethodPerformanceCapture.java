package org.guido.persistence.domain;

import static com.google.common.base.MoreObjects.toStringHelper;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonProperty;

@Document
//@CompoundIndexes({
//    @CompoundIndex(name = "age_idx", def = "{'lastName': 1, 'age': -1}")
//})
public class MethodPerformanceCapture {
	
	@Id
	private String id;
	//@Indexed
	private String pid;
	//@Indexed
	private String threadUUID;
	//@Indexed
	private String hostName;
	//@Indexed
	private String facility;

	@JsonProperty("@timestamp")
	private Date capturedDate;
	
	private long durationInNS;
	private int depth;
	private long timeStamp;
	
	private MethodMessage message;
	
	public String getId() {
		return id;
	}
	
	public MethodMessage getMessage() {
		return message;
	}
	public void setMessage(MethodMessage message) {
		this.message = message;
	}
	public String getPid() {
		return pid;
	}
	public void setPid(String pid) {
		this.pid = pid;
	}
	public String getThreadUUID() {
		return threadUUID;
	}
	public void setThreadUUID(String threadUUID) {
		this.threadUUID = threadUUID;
	}
	public String getHostName() {
		return hostName;
	}
	public void setHostName(String hostName) {
		this.hostName = hostName;
	}
	public String getFacility() {
		return facility;
	}
	public void setFacility(String facility) {
		this.facility = facility;
	}
	public long getDurationInNS() {
		return durationInNS;
	}
	public void setDurationInNS(long durationInNS) {
		this.durationInNS = durationInNS;
	}
	public int getDepth() {
		return depth;
	}
	public void setDepth(int depth) {
		this.depth = depth;
	}
	public long getTimeStamp() {
		return timeStamp;
	}
	public void setTimeStamp(long timeStamp) {
		this.timeStamp = timeStamp;
	}
	
	public Date getCapturedDate() {
		return capturedDate;
	}
	
	public void setCapturedDate(Date capturedDate) {
		this.capturedDate = capturedDate;
	}

	public String toString() {
		return toStringHelper(this)
				.add("id", id)
				.add("pid", pid)
				.add("capturedDate", capturedDate)
				.add("threadUUID", threadUUID)
				.add("hostName", hostName)
				.add("facility", facility)
				.add("durationInNS", durationInNS)
				.add("depth", depth)
				.add("timeStamp", timeStamp)
				.add("message", message)
	      		.toString();
	}
}
