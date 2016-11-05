	package org.guido.agent.stats;

import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import org.guido.agent.transformer.logger.GuidoLogger;

import oss.guido.org.slf4j.Logger;

import com.sun.management.GarbageCollectionNotificationInfo;

public class GuidoNotificationEmitter implements NotificationListener {
	
	private Logger outputLogger;
	private static final GuidoLogger LOG = GuidoLogger.getLogger(GuidoNotificationEmitter.class);

	public GuidoNotificationEmitter(Logger outputLogger) {
		this.outputLogger = outputLogger;
	}

	@Override
	public void handleNotification(Notification notification, Object handback) {
		if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
            GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
            //get all the info and pretty print it
            long duration = info.getGcInfo().getDuration();
            String gctype = info.getGcAction();
            System.out.println(gctype + ": - " + info.getGcInfo().getId()+ " " + info.getGcName() + " (from " + info.getGcCause()+") "+duration + " milliseconds; start-end times " + info.getGcInfo().getStartTime()+ "-" + info.getGcInfo().getEndTime());
		}
	}
}
