package org.guido.agent.logs.provider;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import oss.guido.ch.qos.logback.classic.spi.ILoggingEvent;
import oss.guido.com.fasterxml.jackson.core.JsonGenerator;

public class GuidoJsonJsonMessageProvider extends GuidoJsonMessageProvider {
	
	private final String[] fieldNames;
	
	public GuidoJsonJsonMessageProvider(String[] fieldNames) {
		super();
		this.fieldNames = fieldNames;
	}
	
	@Override
	public String getFieldName() {
		return "metric";
	}
	
    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
    	Object[] args = event.getArgumentArray();
    	Map<String, Object> jsonMap = new LinkedHashMap<String, Object>();
    	for(int index = 0; index < fieldNames.length; index++) {
    		if(fieldNames[index] != null) {
	    		if(index == args.length) {
	    			break;
	    		}
	    		jsonMap.put(fieldNames[index], args[index]);
	    	}
    	}
    	generator.writeObjectField(getFieldName(), jsonMap);
    }
}
