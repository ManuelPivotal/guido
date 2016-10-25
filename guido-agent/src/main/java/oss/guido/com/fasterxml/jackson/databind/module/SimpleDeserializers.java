package oss.guido.com.fasterxml.jackson.databind.module;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import oss.guido.com.fasterxml.jackson.databind.BeanDescription;
import oss.guido.com.fasterxml.jackson.databind.DeserializationConfig;
import oss.guido.com.fasterxml.jackson.databind.JavaType;
import oss.guido.com.fasterxml.jackson.databind.JsonDeserializer;
import oss.guido.com.fasterxml.jackson.databind.JsonMappingException;
import oss.guido.com.fasterxml.jackson.databind.JsonNode;
import oss.guido.com.fasterxml.jackson.databind.KeyDeserializer;
import oss.guido.com.fasterxml.jackson.databind.deser.Deserializers;
import oss.guido.com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import oss.guido.com.fasterxml.jackson.databind.type.ArrayType;
import oss.guido.com.fasterxml.jackson.databind.type.ClassKey;
import oss.guido.com.fasterxml.jackson.databind.type.CollectionLikeType;
import oss.guido.com.fasterxml.jackson.databind.type.CollectionType;
import oss.guido.com.fasterxml.jackson.databind.type.MapLikeType;
import oss.guido.com.fasterxml.jackson.databind.type.MapType;

/**
 * Simple implementation {@link Deserializers} which allows registration of
 * deserializers based on raw (type erased class).
 * It can work well for basic bean and scalar type deserializers, but is not
 * a good fit for handling generic types (like {@link Map}s and {@link Collection}s
 * or array types).
 *<p>
 * Unlike {@link SimpleSerializers}, this class does not currently support generic mappings;
 * all mappings must be to exact declared deserialization type.
 */
public class SimpleDeserializers
   implements Deserializers, java.io.Serializable
{
    private static final long serialVersionUID = -3006673354353448880L;

    protected HashMap<ClassKey,JsonDeserializer<?>> _classMappings = null;

    /**
     * Flag to help find "generic" enum deserializer, if one has been registered.
     * 
     * @since 2.3
     */
    protected boolean _hasEnumDeserializer = false;
    
    /*
    /**********************************************************
    /* Life-cycle, construction and configuring
    /**********************************************************
     */
    
    public SimpleDeserializers() { }

    /**
     * @since 2.1
     */
    public SimpleDeserializers(Map<Class<?>,JsonDeserializer<?>> desers) {
        addDeserializers(desers);
    }
    
    public <T> void addDeserializer(Class<T> forClass, JsonDeserializer<? extends T> deser)
    {
        ClassKey key = new ClassKey(forClass);
        if (_classMappings == null) {
            _classMappings = new HashMap<ClassKey,JsonDeserializer<?>>();
        }
        _classMappings.put(key, deser);
        // [Issue#227]: generic Enum deserializer?
        if (forClass == Enum.class) {
            _hasEnumDeserializer = true;
        }
    }

    /**
     * @since 2.1
     */
    @SuppressWarnings("unchecked")
    public void addDeserializers(Map<Class<?>,JsonDeserializer<?>> desers)
    {
        for (Map.Entry<Class<?>,JsonDeserializer<?>> entry : desers.entrySet()) {
            Class<?> cls = entry.getKey();
            // what a mess... nominal generics safety...
            JsonDeserializer<Object> deser = (JsonDeserializer<Object>) entry.getValue();
            addDeserializer((Class<Object>) cls, deser);
        }
    }
    
    /*
    /**********************************************************
    /* Serializers implementation
    /**********************************************************
     */
    
    @Override
    public JsonDeserializer<?> findArrayDeserializer(ArrayType type,
            DeserializationConfig config, BeanDescription beanDesc,
            TypeDeserializer elementTypeDeserializer, JsonDeserializer<?> elementDeserializer)
        throws JsonMappingException
    {
        return (_classMappings == null) ? null : _classMappings.get(new ClassKey(type.getRawClass()));
    }

    @Override
    public JsonDeserializer<?> findBeanDeserializer(JavaType type,
            DeserializationConfig config, BeanDescription beanDesc)
        throws JsonMappingException
    {
        return (_classMappings == null) ? null : _classMappings.get(new ClassKey(type.getRawClass()));
    }

    @Override
    public JsonDeserializer<?> findCollectionDeserializer(CollectionType type,
            DeserializationConfig config, BeanDescription beanDesc,
            TypeDeserializer elementTypeDeserializer,
            JsonDeserializer<?> elementDeserializer)
        throws JsonMappingException
    {
        return (_classMappings == null) ? null : _classMappings.get(new ClassKey(type.getRawClass()));
    }

    @Override
    public JsonDeserializer<?> findCollectionLikeDeserializer(CollectionLikeType type,
            DeserializationConfig config, BeanDescription beanDesc,
            TypeDeserializer elementTypeDeserializer,
            JsonDeserializer<?> elementDeserializer)
        throws JsonMappingException
    {
        return (_classMappings == null) ? null : _classMappings.get(new ClassKey(type.getRawClass()));
    }
    
    @Override
    public JsonDeserializer<?> findEnumDeserializer(Class<?> type,
            DeserializationConfig config, BeanDescription beanDesc)
        throws JsonMappingException
    {
        if (_classMappings == null) {
            return null;
        }
        JsonDeserializer<?> deser = _classMappings.get(new ClassKey(type));
        if (deser == null) {
            if (_hasEnumDeserializer && type.isEnum()) {
                deser = _classMappings.get(new ClassKey(Enum.class));
            }
        }
        return deser;
    }

    @Override
    public JsonDeserializer<?> findMapDeserializer(MapType type,
            DeserializationConfig config, BeanDescription beanDesc,
            KeyDeserializer keyDeserializer,
            TypeDeserializer elementTypeDeserializer,
            JsonDeserializer<?> elementDeserializer)
        throws JsonMappingException
    {
        return (_classMappings == null) ? null : _classMappings.get(new ClassKey(type.getRawClass()));
    }

    @Override
    public JsonDeserializer<?> findMapLikeDeserializer(MapLikeType type,
            DeserializationConfig config, BeanDescription beanDesc,
            KeyDeserializer keyDeserializer,
            TypeDeserializer elementTypeDeserializer,
            JsonDeserializer<?> elementDeserializer)
        throws JsonMappingException
    {
        return (_classMappings == null) ? null : _classMappings.get(new ClassKey(type.getRawClass()));
    }
    
    @Override
    public JsonDeserializer<?> findTreeNodeDeserializer(Class<? extends JsonNode> nodeType,
            DeserializationConfig config, BeanDescription beanDesc)
        throws JsonMappingException
    {
        return (_classMappings == null) ? null : _classMappings.get(new ClassKey(nodeType));
    }
}
