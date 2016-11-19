package org.guido.util;

public class ToStringHelper {
	
	private StringBuffer sb = new StringBuffer();
	private String separator = "";
	
	private ToStringHelper(Class<? extends Object> clazz) {
		sb.append("{").append(clazz.getName()).append("<");
	}
	
	public ToStringHelper add(String name, Object value) {
		sb.append(separator)
			.append(name).append("=").append((value == null ? "null" : value.toString()));
		separator = ", ";
		return this;
	}
	
	public String toString() {
		sb.append(">}");
		return sb.toString();
	}
	
	static public ToStringHelper toStringHelper(Object pThis) {
		return new ToStringHelper(pThis.getClass());
	}
}
