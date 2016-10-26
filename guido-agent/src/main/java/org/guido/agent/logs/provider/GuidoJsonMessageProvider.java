package org.guido.agent.logs.provider;

import java.io.IOException;

import oss.guido.ch.qos.logback.classic.spi.ILoggingEvent;
import oss.guido.com.fasterxml.jackson.core.JsonGenerator;
import oss.guido.net.logstash.logback.composite.JsonWritingUtils;
import oss.guido.net.logstash.logback.composite.loggingevent.MessageJsonProvider;

public class GuidoJsonMessageProvider extends  MessageJsonProvider {
	
	public interface MessageAddon {
		String getAddon(ILoggingEvent event);
	}
	
	final protected MessageAddon[] addons;
	
	public GuidoJsonMessageProvider(MessageAddon[] addons) {
		super();
		this.addons = addons;
	}
	
    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
    	StringBuffer sb = new StringBuffer();
    	for(MessageAddon addon : addons) {
    		sb.append(addon.getAddon(event)).append(" ");
    	}
    	sb.append(event.getFormattedMessage());
        JsonWritingUtils.writeStringField(generator, getFieldName(), sb.toString());
    }
}
