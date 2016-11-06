package org.guido.agent.stats;

import static java.lang.management.ManagementFactory.getGarbageCollectorMXBeans;
import static java.lang.management.ManagementFactory.getOperatingSystemMXBean;

import java.lang.management.GarbageCollectorMXBean;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.management.NotificationEmitter;

import org.guido.agent.transformer.logger.GuidoLogger;
import org.guido.util.ThreadExecutorUtils;

import oss.guido.org.slf4j.Logger;

public class MemoryUsageStats implements Runnable {
	
	private static final GuidoLogger LOG = GuidoLogger.getLogger(MemoryUsageStats.class);
	
	static final Set<String> YOUNG_GC = new HashSet<String>(3);
	static final Set<String> OLD_GC = new HashSet<String>(3);

	static {
	    // young generation GC names
	    YOUNG_GC.add("PS Scavenge");
	    YOUNG_GC.add("ParNew");
	    YOUNG_GC.add("G1 Young Generation");

	    // old generation GC names
	    OLD_GC.add("PS MarkSweep");
	    OLD_GC.add("ConcurrentMarkSweep");
	    OLD_GC.add("G1 Old Generation");
	}
	
	private Logger outputLogger;
	private int pollDelay;
	private String pid;
	long lastMinorCount = 0;
    long lastMinorTime = 0;
    long lastMajorCount = 0;
    long lastMajorTime = 0;
    long lastUnknownCount = 0;
    long lastUnknownTime = 0;
    
	long minorCount = 0;
    long minorTime = 0;
    long majorCount = 0;
    long majorTime = 0;
    long unknownCount = 0;
    long unknownTime = 0;
    
    long timeSpent = 0;

	long heartBeat;

//	private long memoryCommitted;
//	private long memoryUsed;
//	private long peakThreadCount;
//	private long daemonThreadCount;
//	private long totalStartedThreadcount;
//	private long threadCount;
	
	long freeMemory, totalMemory, maxMemory, usedMemory;

	private double systemLoad;
	
	public void init(String pid, Logger outputLogger, int pollDelay) {
		this.pid = pid;
		this.outputLogger = outputLogger;
		this.pollDelay = pollDelay;
		//this.heartBeat = heartBeat;
	}
	
	public void start() {
		LOG.info("starting mem stat thread - poll time is {} ms",  pollDelay);
		ThreadExecutorUtils.newSingleThreadExecutor().submit(this);
	}	
	
	protected void installGCMonitoring() {    
	    for (GarbageCollectorMXBean gcbean : getGarbageCollectorMXBeans()) {
	    	if(gcbean instanceof NotificationEmitter) {
	    		NotificationEmitter emitter = (NotificationEmitter) gcbean;
	    		emitter.addNotificationListener(new GuidoNotificationEmitter(outputLogger), null, null);
	    	}
	    }
	}

	@Override
	public void run() {
		for(;;) {
			try {
				Thread.sleep(pollDelay);
				resetCurrentVariables();
			    getNewVariables();
			    if(newValuesDiffer() || timeToSend()) {
				    sendNewLogs();
				}
			} catch(InterruptedException ie) {
			return;
			} catch (Exception e) {
				LOG.error(e,  "exception while getting memory stats");
			}
		}
	}

	private void sendNewLogs() {
		StringBuilder sb = new StringBuilder();
		sb.append("MinorGCcount=").append(minorCount)
		        .append(" MinorGCTime=").append(minorTime)
		        .append(" MajorGCCount=").append(majorCount)
		        .append(" MajorGCTime=").append(majorTime);

		if (unknownCount > 0) {
		    sb.append(" UnknownGCCount=").append(unknownCount)
		      .append(" UnknownGCTime=").append(unknownTime);
		}
		sb
//		.append(" memCommited=").append(memoryCommitted)
//		.append(" memUsed=").append(memoryUsed)
//		.append(" peakThreadCount=").append(peakThreadCount)
//		.append(" threadCount=").append(threadCount)
		.append(" systemLoad=").append(systemLoad)
		.append(" freeMemory=").append(freeMemory)
		.append(" maxMemory=").append(maxMemory)
		.append(" totalMemory=").append(totalMemory)
		.append(" usedMemory=").append(usedMemory)
		;
		
		String statString = sb.toString();
		LOG.info("pid={} {}", pid, statString);
		outputLogger.info("pid={} {}", pid, statString);
		
		timeSpent = 0;
	}

	private void getNewVariables() {
		List<GarbageCollectorMXBean> mxBeans = getGarbageCollectorMXBeans();
		for (GarbageCollectorMXBean gc : mxBeans) {
		    long count = gc.getCollectionCount();
		    if (count >= 0) {
		        if (YOUNG_GC.contains(gc.getName())) {
		            minorCount += count;
		            minorTime += gc.getCollectionTime();
		        } else if (OLD_GC.contains(gc.getName())) {
		            majorCount += count;
		            majorTime += gc.getCollectionTime();
		        } else {
		            unknownCount += count;
		            unknownTime += gc.getCollectionTime();
		        }
		    }
		}
//		MemoryUsage memoryUsage = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
//		memoryCommitted = memoryUsage.getCommitted();
//		memoryUsed = memoryUsage.getUsed();
		
//		ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
//		
//		peakThreadCount = (long) threadMxBean.getPeakThreadCount();
//		daemonThreadCount = (long) threadMxBean.getDaemonThreadCount();
//		totalStartedThreadcount = threadMxBean.getTotalStartedThreadCount();
//		threadCount = (long) threadMxBean.getThreadCount();
		systemLoad = getOperatingSystemMXBean().getSystemLoadAverage() / 100.00;
		
		Runtime runtime = Runtime.getRuntime();
		freeMemory = runtime.freeMemory();
		maxMemory = runtime.maxMemory();
		totalMemory = runtime.totalMemory();
		usedMemory = totalMemory - freeMemory;
	}

	private void resetCurrentVariables() {
		minorCount = 0;
		minorTime = 0;
		majorCount = 0;
		majorTime = 0;
		unknownCount = 0;
		unknownTime = 0;
	}

	private boolean timeToSend() {
		timeSpent += pollDelay;
		if(timeSpent >= heartBeat) {
			timeSpent = 0;
			return true;
		}
		return false;
	}

	private boolean newValuesDiffer() {
		return true;
//		if(minorCount != lastMinorCount
//			    || minorTime != lastMinorTime
//			    || majorCount != lastMajorCount
//			    || majorTime  != lastMajorTime
//			    || unknownCount != lastUnknownCount
//			    || unknownTime != lastUnknownTime) {
//			lastMinorCount = minorCount;
//		    lastMinorTime = minorTime;
//		    lastMajorCount = majorCount;
//		    lastMajorTime = majorTime;
//		    lastUnknownCount = unknownCount;
//		    lastUnknownTime = unknownTime;
//		    return true;
//		}
//		return false;
	}
}
