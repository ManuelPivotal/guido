package oss.guido.com.fasterxml.jackson.databind.deser.impl;

import java.io.IOException;

import oss.guido.com.fasterxml.jackson.core.JsonParser;
import oss.guido.com.fasterxml.jackson.core.JsonProcessingException;
import oss.guido.com.fasterxml.jackson.databind.DeserializationContext;
import oss.guido.com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * A deserializer that stores a {@link NoClassDefFoundError} error
 * and throws the stored exception when attempting to deserialize
 * a value. Null and empty values can be deserialized without error.
 */
public class NoClassDefFoundDeserializer<T> extends JsonDeserializer<T>
{
    private final NoClassDefFoundError _cause;

    public NoClassDefFoundDeserializer(NoClassDefFoundError cause)
    {
        _cause = cause;
    }

    @Override
    public T deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException
    {
        throw _cause;
    }
}