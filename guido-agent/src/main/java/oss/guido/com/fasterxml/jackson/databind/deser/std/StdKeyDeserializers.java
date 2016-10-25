package oss.guido.com.fasterxml.jackson.databind.deser.std;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import oss.guido.com.fasterxml.jackson.databind.BeanDescription;
import oss.guido.com.fasterxml.jackson.databind.DeserializationConfig;
import oss.guido.com.fasterxml.jackson.databind.JavaType;
import oss.guido.com.fasterxml.jackson.databind.JsonDeserializer;
import oss.guido.com.fasterxml.jackson.databind.JsonMappingException;
import oss.guido.com.fasterxml.jackson.databind.KeyDeserializer;
import oss.guido.com.fasterxml.jackson.databind.deser.KeyDeserializers;
import oss.guido.com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import oss.guido.com.fasterxml.jackson.databind.util.ClassUtil;
import oss.guido.com.fasterxml.jackson.databind.util.EnumResolver;

/**
 * Helper class used to contain simple/well-known key deserializers.
 * Following kinds of Objects can be handled currently:
 *<ul>
 * <li>Primitive wrappers (Boolean, Byte, Char, Short, Integer, Float, Long, Double)</li>
 * <li>Enums (usually not needed, since EnumMap doesn't call us)</li>
 * <li>{@link java.util.Date}</li>
 * <li>{@link java.util.Calendar}</li>
 * <li>{@link java.util.UUID}</li>
 * <li>{@link java.util.Locale}</li>
 * <li>Anything with constructor that takes a single String arg
 *   (if not explicitly @JsonIgnore'd)</li>
 * <li>Anything with {@code static T valueOf(String)} factory method
 *   (if not explicitly @JsonIgnore'd)</li>
 *</ul>
 */
public class StdKeyDeserializers
    implements KeyDeserializers, java.io.Serializable
{
    private static final long serialVersionUID = 1L;
    
    public static KeyDeserializer constructEnumKeyDeserializer(EnumResolver<?> enumResolver) {
        return new StdKeyDeserializer.EnumKD(enumResolver, null);
    }

    public static KeyDeserializer constructEnumKeyDeserializer(EnumResolver<?> enumResolver,
            AnnotatedMethod factory) {
        return new StdKeyDeserializer.EnumKD(enumResolver, factory);
    }
    
    public static KeyDeserializer constructDelegatingKeyDeserializer(DeserializationConfig config,
            JavaType type, JsonDeserializer<?> deser)
    {
        return new StdKeyDeserializer.DelegatingKD(type.getRawClass(), deser);
    }
    
    public static KeyDeserializer findStringBasedKeyDeserializer(DeserializationConfig config,
            JavaType type)
    {
        /* We don't need full deserialization information, just need to
         * know creators.
         */
        BeanDescription beanDesc = config.introspect(type);
        // Ok, so: can we find T(String) constructor?
        Constructor<?> ctor = beanDesc.findSingleArgConstructor(String.class);
        if (ctor != null) {
            if (config.canOverrideAccessModifiers()) {
                ClassUtil.checkAndFixAccess(ctor);
            }
            return new StdKeyDeserializer.StringCtorKeyDeserializer(ctor);
        }
        /* or if not, "static T valueOf(String)" (or equivalent marked
         * with @JsonCreator annotation?)
         */
        Method m = beanDesc.findFactoryMethod(String.class);
        if (m != null){
            if (config.canOverrideAccessModifiers()) {
                ClassUtil.checkAndFixAccess(m);
            }
            return new StdKeyDeserializer.StringFactoryKeyDeserializer(m);
        }
        // nope, no such luck...
        return null;
    }
    
    /*
    /**********************************************************
    /* KeyDeserializers implementation
    /**********************************************************
     */
    
    @Override
    public KeyDeserializer findKeyDeserializer(JavaType type,
            DeserializationConfig config, BeanDescription beanDesc) throws JsonMappingException
    {
        Class<?> raw = type.getRawClass();
        // 23-Apr-2013, tatu: Map primitive types, just in case one was given
        if (raw.isPrimitive()) {
            raw = ClassUtil.wrapperType(raw);
        }
        return StdKeyDeserializer.forType(raw);
    }
}
