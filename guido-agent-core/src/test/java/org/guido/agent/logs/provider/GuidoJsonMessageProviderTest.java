package org.guido.agent.logs.provider;

import java.io.IOException;
import java.io.OutputStream;

import junit.framework.Assert;

import org.guido.agent.logs.provider.GuidoJsonMessageProvider;
import org.guido.agent.logs.provider.GuidoJsonMessageProvider.MessageAddon;
import org.guido.agent.logs.provider.GuidoLogstashEncoder;
import org.junit.Test;

import oss.guido.ch.qos.logback.classic.Level;
import oss.guido.ch.qos.logback.classic.Logger;
import oss.guido.ch.qos.logback.classic.LoggerContext;
import oss.guido.ch.qos.logback.classic.spi.ILoggingEvent;
import oss.guido.ch.qos.logback.core.ConsoleAppender;

public class GuidoJsonMessageProviderTest {
	
	class IntercepterConsoleAppender extends ConsoleAppender<ILoggingEvent> {
		StringBuffer output = new StringBuffer();
		long timeStamp = -1;
		class WriterOutputStream extends OutputStream {
			@Override
			public void write(int b) throws IOException {
				output.append((char)b);
			}
		}
		@Override
		public void doAppend(ILoggingEvent eventObject) {
			timeStamp = eventObject.getTimeStamp();
			super.doAppend(eventObject);
		}
		public void start() {
		    super.start();
		    setOutputStream(new WriterOutputStream());
		}
		public String getOutput() {
			return output.toString();
		}
	}
	
	@Test
	public void canEncodeWithAddons() {
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
		
		IntercepterConsoleAppender consoleAppender = new IntercepterConsoleAppender();
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
		
		String output = consoleAppender.getOutput();
		long timeStamp = consoleAppender.timeStamp;
		String expected = String.format("\"message\":\"INFO [hello]-%d hello\"", timeStamp);
		
		Assert.assertFalse("".equals(output));
		Assert.assertTrue(output.contains(expected));
	}
	
	@Test
	public void canOutputJson() {
		LoggerContext loggerContext = new LoggerContext();		
		
		IntercepterConsoleAppender consoleAppender = new IntercepterConsoleAppender();
		GuidoLogstashEncoder consoleEncoder = new GuidoLogstashEncoder();
		String[] fieldNames = new String[] {
				"helloKey"
		};
		consoleEncoder.setMessageProvider(new GuidoJsonJsonMessageProvider(fieldNames));
		consoleEncoder.start();
		
		consoleAppender.setEncoder(consoleEncoder);
		consoleAppender.setContext(loggerContext);
		consoleAppender.start();
		
		Logger LOG = loggerContext.getLogger("json_tcp");
		LOG.setLevel(Level.INFO);
		LOG.setAdditive(false);
		LOG.addAppender(consoleAppender);
		loggerContext.start();

		LOG.info("{}", new Object[]{"hello"});
		
		String output = consoleAppender.getOutput();
		System.out.println("output=" + output);
		
		long timeStamp = consoleAppender.timeStamp;
		String expected = String.format("\"message\":{\"helloKey\":\"hello\"}", timeStamp);
		
		Assert.assertFalse("".equals(output));
		Assert.assertTrue(output.contains(expected));
	}
}
