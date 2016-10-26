package oss.guido.com.fasterxml.jackson.databind.ser.std;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Date;

import oss.guido.com.fasterxml.jackson.core.JsonGenerator;
import oss.guido.com.fasterxml.jackson.databind.JavaType;
import oss.guido.com.fasterxml.jackson.databind.JsonMappingException;
import oss.guido.com.fasterxml.jackson.databind.JsonNode;
import oss.guido.com.fasterxml.jackson.databind.SerializerProvider;
import oss.guido.com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;

/**
 * Specialized serializer that can be used as the generic key
 * serializer, when serializing {@link java.util.Map}s to JSON
 * Objects.
 */
@SuppressWarnings("serial")
public class StdKeySerializer extends StdSerializer<Object>
{
    public StdKeySerializer() { super(Object.class); }

    @Override
    public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        String str;
        Class<?> cls = value.getClass();
        
        if (cls == String.class) {
            str = (String) value;
        } else if (Date.class.isAssignableFrom(cls)) {
            provider.defaultSerializeDateKey((Date) value, jgen);
            return;
        } else if (cls == Class.class) {
            str = ((Class<?>) value).getName();
        } else {
            str = value.toString();
        }
        jgen.writeFieldName(str);
    }

    @Override
    public JsonNode getSchema(SerializerProvider provider, Type typeHint) throws JsonMappingException {
        return createSchemaNode("string");
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException {
        visitor.expectStringFormat(typeHint);
    }
}
