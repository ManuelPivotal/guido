package oss.guido.com.fasterxml.jackson.databind.ser.std;

import java.io.IOException;
import java.lang.annotation.Annotation;

import oss.guido.com.fasterxml.jackson.core.JsonGenerator;
import oss.guido.com.fasterxml.jackson.databind.BeanProperty;
import oss.guido.com.fasterxml.jackson.databind.JsonMappingException;
import oss.guido.com.fasterxml.jackson.databind.JsonSerializer;
import oss.guido.com.fasterxml.jackson.databind.PropertyName;
import oss.guido.com.fasterxml.jackson.databind.SerializerProvider;
import oss.guido.com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import oss.guido.com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import oss.guido.com.fasterxml.jackson.databind.node.ObjectNode;
import oss.guido.com.fasterxml.jackson.databind.ser.PropertyWriter;

/**
 * Helper class needed to support flexible filtering of Map properties
 * with generic JSON Filter functionality. Since {@link java.util.Map}s
 * are not handled as a collection of properties by Jackson (unlike POJOs),
 * bit more wrapping is required.
 */
public class MapProperty extends PropertyWriter
{
    protected final TypeSerializer _typeSerializer;

    protected final BeanProperty _property;

    protected Object _key;

    protected JsonSerializer<Object> _keySerializer, _valueSerializer;

    /**
     * @deprecated since 2.4
     */
    @Deprecated // since 2.4
    public MapProperty(TypeSerializer typeSer) {
        this(typeSer, null);
    }
    
    public MapProperty(TypeSerializer typeSer, BeanProperty prop)
    {
        _typeSerializer = typeSer;
        _property = prop;
    }

    /**
     * Deprecated method with wrong signature; value should not be assigned
     * to property, should be passed via proper call-through methods.
     * 
     * @deprecated Since 2.5, remove in 2.6
     */
    @Deprecated // since 2.5
    public void reset(Object key, Object value,
            JsonSerializer<Object> keySer, JsonSerializer<Object> valueSer) {
        reset(key, keySer, valueSer);
    }
    
    /**
     * Initialization method that needs to be called before passing
     * property to filter.
     */
    public void reset(Object key,
            JsonSerializer<Object> keySer, JsonSerializer<Object> valueSer)
    {
        _key = key;
        _keySerializer = keySer;
        _valueSerializer = valueSer;
    }
    
    @Override
    public String getName() {
        if (_key instanceof String) {
            return (String) _key;
        }
        return String.valueOf(_key);
    }

    @Override
    public PropertyName getFullName() {
        return new PropertyName(getName());
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> acls) {
        return (_property == null) ? null : _property.getAnnotation(acls);
    }

    @Override
    public <A extends Annotation> A getContextAnnotation(Class<A> acls) {
        return (_property == null) ? null : _property.getContextAnnotation(acls);
    }
    
    @Override
    public void serializeAsField(Object value, JsonGenerator jgen,
            SerializerProvider provider) throws IOException
    {
        _keySerializer.serialize(_key, jgen, provider);
        if (_typeSerializer == null) {
            _valueSerializer.serialize(value, jgen, provider);
        } else {
            _valueSerializer.serializeWithType(value, jgen, provider, _typeSerializer);
        }
    }

    @Override
    public void serializeAsOmittedField(Object value, JsonGenerator jgen,
            SerializerProvider provider) throws Exception
    {
        if (!jgen.canOmitFields()) {
            jgen.writeOmittedField(getName());
        }
    }

    @Override
    public void serializeAsElement(Object value, JsonGenerator jgen,
            SerializerProvider provider) throws Exception
    {
        if (_typeSerializer == null) {
            _valueSerializer.serialize(value, jgen, provider);
        } else {
            _valueSerializer.serializeWithType(value, jgen, provider, _typeSerializer);
        }
    }
    
    @Override
    public void serializeAsPlaceholder(Object value, JsonGenerator jgen,
            SerializerProvider provider) throws Exception
    {
        jgen.writeNull();
    }

    @Override
    public void depositSchemaProperty(JsonObjectFormatVisitor objectVisitor)
        throws JsonMappingException
    {
        // !!! TODO
    }

    @Override
    @Deprecated
    public void depositSchemaProperty(ObjectNode propertiesNode,
            SerializerProvider provider) throws JsonMappingException {
        // !!! TODO
    }
}