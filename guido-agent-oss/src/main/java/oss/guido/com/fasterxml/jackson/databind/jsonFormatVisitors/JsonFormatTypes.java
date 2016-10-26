package oss.guido.com.fasterxml.jackson.databind.jsonFormatVisitors;

import oss.guido.com.fasterxml.jackson.annotation.JsonCreator;
import oss.guido.com.fasterxml.jackson.annotation.JsonValue;

public enum JsonFormatTypes {
	
	STRING,
	NUMBER,
	INTEGER,
	BOOLEAN,
	OBJECT,
	ARRAY,
	NULL,
	ANY;
	
	
	@JsonValue
	public String value() {
		return this.name().toLowerCase();
	}
	
	@JsonCreator
	public static JsonFormatTypes forValue(String s) {
		return valueOf(s.toUpperCase());
	}
}