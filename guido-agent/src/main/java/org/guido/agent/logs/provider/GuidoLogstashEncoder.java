package org.guido.agent.logs.provider;

import oss.guido.ch.qos.logback.classic.spi.ILoggingEvent;
import oss.guido.net.logstash.logback.composite.JsonProvider;
import oss.guido.net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders;
import oss.guido.net.logstash.logback.composite.loggingevent.MessageJsonProvider;
import oss.guido.net.logstash.logback.encoder.LogstashEncoder;

public class GuidoLogstashEncoder extends LogstashEncoder {
	
	public void setMessageProvider(GuidoJsonMessageProvider messageProvider) {
		LoggingEventJsonProviders providers = super.getFormatter().getProviders();
		for(JsonProvider<ILoggingEvent> provider : providers.getProviders()) {
			if(provider instanceof MessageJsonProvider) {
				providers.removeProvider(provider);
				break;
			}
		}
		providers.addProvider(messageProvider);
	}
}