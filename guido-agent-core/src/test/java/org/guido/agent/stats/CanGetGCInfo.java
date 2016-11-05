package org.guido.agent.stats;

import java.util.Date;

import org.guido.agent.transformer.logger.GuidoLogger;
import org.junit.Test;
import org.mockito.Mockito;

import oss.guido.org.slf4j.Logger;

public class CanGetGCInfo {
	@Test
	public void canGetGCInfo() throws Exception {
		GuidoLogger.setGlobalLogLevel(GuidoLogger.DEBUG);
		Logger logger = Mockito.mock(Logger.class);
		MemoryUsageStats stats = new MemoryUsageStats();
		stats.init("123", logger,  10000);
		stats.start();
		new Thread(new Runnable() {
			@Override
			public void run() {
				for(;;) {
					String theString = new String("hello");
					theString += new Date().getTime();
					try {
						Thread.sleep(3);
					} catch (InterruptedException e) {
						return;
					}
				}
			}
			
		}).start();
		Thread.sleep(1000000);
	}
}
