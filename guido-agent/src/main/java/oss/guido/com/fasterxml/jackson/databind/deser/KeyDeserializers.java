package oss.guido.com.fasterxml.jackson.databind.deser;

import oss.guido.com.fasterxml.jackson.databind.BeanDescription;
import oss.guido.com.fasterxml.jackson.databind.DeserializationConfig;
import oss.guido.com.fasterxml.jackson.databind.JavaType;
import oss.guido.com.fasterxml.jackson.databind.JsonMappingException;
import oss.guido.com.fasterxml.jackson.databind.KeyDeserializer;

/**
 * Interface that defines API for simple extensions that can provide additional deserializers
 * for deserializer Map keys of various types, from JSON property names.
 * Access is by a single callback method; instance is to either return
 * a configured {@link KeyDeserializer} for specified type, or null to indicate that it
 * does not support handling of the type. In latter case, further calls can be made
 * for other providers; in former case returned key deserializer is used for handling of
 * key instances of specified type.
 */
public interface KeyDeserializers
{
    public KeyDeserializer findKeyDeserializer(JavaType type, DeserializationConfig config, BeanDescription beanDesc) throws JsonMappingException;
}
