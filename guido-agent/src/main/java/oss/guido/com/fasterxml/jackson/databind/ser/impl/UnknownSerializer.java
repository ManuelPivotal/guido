package oss.guido.com.fasterxml.jackson.databind.ser.impl;

import java.io.IOException;
import java.lang.reflect.Type;

import oss.guido.com.fasterxml.jackson.core.JsonGenerationException;
import oss.guido.com.fasterxml.jackson.core.JsonGenerator;
import oss.guido.com.fasterxml.jackson.databind.JavaType;
import oss.guido.com.fasterxml.jackson.databind.JsonMappingException;
import oss.guido.com.fasterxml.jackson.databind.JsonNode;
import oss.guido.com.fasterxml.jackson.databind.SerializationFeature;
import oss.guido.com.fasterxml.jackson.databind.SerializerProvider;
import oss.guido.com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import oss.guido.com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import oss.guido.com.fasterxml.jackson.databind.ser.std.StdSerializer;

@SuppressWarnings("serial")
public class UnknownSerializer
    extends StdSerializer<Object>
{
    public UnknownSerializer() {
        super(Object.class);
    }
    
    @Override
    public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider)
        throws IOException, JsonMappingException
    {
        // 27-Nov-2009, tatu: As per [JACKSON-201] may or may not fail...
        if (provider.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS)) {
            failForEmpty(value);
        }
        // But if it's fine, we'll just output empty JSON Object:
        jgen.writeStartObject();
        jgen.writeEndObject();
    }

    @Override
    public final void serializeWithType(Object value, JsonGenerator jgen, SerializerProvider provider,
            TypeSerializer typeSer)
        throws IOException, JsonGenerationException
    {
        if (provider.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS)) {
            failForEmpty(value);
        }
        typeSer.writeTypePrefixForObject(value, jgen);
        typeSer.writeTypeSuffixForObject(value, jgen);
    }
    
    @Override
    public JsonNode getSchema(SerializerProvider provider, Type typeHint) throws JsonMappingException {
        return null;
    }
    
    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint)
        throws JsonMappingException
    { 
        visitor.expectAnyFormat(typeHint);
    }

    protected void failForEmpty(Object value) throws JsonMappingException
    {
        throw new JsonMappingException("No serializer found for class "+value.getClass().getName()+" and no properties discovered to create BeanSerializer (to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS) )");
    }
}
