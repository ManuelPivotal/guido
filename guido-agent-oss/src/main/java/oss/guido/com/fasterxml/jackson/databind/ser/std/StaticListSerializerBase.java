package oss.guido.com.fasterxml.jackson.databind.ser.std;

import java.lang.reflect.Type;
import java.util.Collection;

import oss.guido.com.fasterxml.jackson.databind.JavaType;
import oss.guido.com.fasterxml.jackson.databind.JsonMappingException;
import oss.guido.com.fasterxml.jackson.databind.JsonNode;
import oss.guido.com.fasterxml.jackson.databind.SerializerProvider;
import oss.guido.com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor;
import oss.guido.com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;

/**
 * Intermediate base class for Lists, Collections and Arrays
 * that contain static (non-dynamic) value types.
 */
@SuppressWarnings("serial")
public abstract class StaticListSerializerBase<T extends Collection<?>>
    extends StdSerializer<T>
{
    protected StaticListSerializerBase(Class<?> cls) {
        super(cls, false);
    }

    @Deprecated // since 2.5
    @Override
    public boolean isEmpty(T value) {
        return isEmpty(null, value);
    }

    @Override
    public boolean isEmpty(SerializerProvider provider, T value) {
        return (value == null) || (value.size() == 0);
    }
    
    @Override
    public JsonNode getSchema(SerializerProvider provider, Type typeHint) {
        return createSchemaNode("array", true).set("items", contentSchema());
    }
    
    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException {
        acceptContentVisitor(visitor.expectArrayFormat(typeHint));
    }

    /*
    /**********************************************************
    /* Abstract methods for sub-classes to implement
    /**********************************************************
     */

    protected abstract JsonNode contentSchema();
    
    protected abstract void acceptContentVisitor(JsonArrayFormatVisitor visitor)
        throws JsonMappingException;
}
