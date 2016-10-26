package org.guido.agent.logs.layout;

import org.guido.agent.logs.provider.GuidoJsonMessageProvider;
import org.guido.agent.logs.provider.GuidoJsonMessageProvider.MessageAddon;
import org.guido.agent.logs.provider.GuidoLogstashEncoder;
import org.junit.Test;

import oss.guido.ch.qos.logback.classic.Level;
import oss.guido.ch.qos.logback.classic.Logger;
import oss.guido.ch.qos.logback.classic.LoggerContext;
import oss.guido.ch.qos.logback.classic.spi.ILoggingEvent;
import oss.guido.ch.qos.logback.core.ConsoleAppender;

public class PatternWrapperLayoutTest {
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void canEncodeWithLayout() {
		LoggerContext loggerContext = new LoggerContext();
		
		MessageAddon[] addons = new MessageAddon[] {
				new MessageAddon() {
					@Override
					public String getAddon(ILoggingEvent event) {
						return "INFO";
					}
				},
				new MessageAddon() {
					@Override
					public String getAddon(ILoggingEvent event) {
						return "[hello]-" + event.getTimeStamp();
					}
				}
		};

		ConsoleAppender consoleAppender = new ConsoleAppender();
		GuidoLogstashEncoder consoleEncoder = new GuidoLogstashEncoder();
		consoleEncoder.setMessageProvider(new GuidoJsonMessageProvider(addons));
		consoleEncoder.start();
		
		consoleAppender.setEncoder(consoleEncoder);
		consoleAppender.setContext(loggerContext);
		
		consoleAppender.start();
		
		Logger LOG = loggerContext.getLogger("json_tcp");
		LOG.setLevel(Level.INFO);
		LOG.setAdditive(false);
		LOG.addAppender(consoleAppender);
		loggerContext.start();

		LOG.info("hello");
	}
}
