/**
 * 
 */
package oss.guido.com.fasterxml.jackson.databind.jsonFormatVisitors;

import oss.guido.com.fasterxml.jackson.databind.SerializerProvider;

/**
 * @author jphelan
 */
public interface JsonFormatVisitorWithSerializerProvider {
    public SerializerProvider getProvider();
    public abstract void setProvider(SerializerProvider provider);
}
