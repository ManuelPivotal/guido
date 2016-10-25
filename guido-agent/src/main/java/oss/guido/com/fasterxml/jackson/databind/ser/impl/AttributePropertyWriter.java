package oss.guido.com.fasterxml.jackson.databind.ser.impl;

import oss.guido.com.fasterxml.jackson.annotation.JsonInclude;
import oss.guido.com.fasterxml.jackson.core.JsonGenerator;
import oss.guido.com.fasterxml.jackson.databind.JavaType;
import oss.guido.com.fasterxml.jackson.databind.SerializerProvider;
import oss.guido.com.fasterxml.jackson.databind.cfg.MapperConfig;
import oss.guido.com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import oss.guido.com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import oss.guido.com.fasterxml.jackson.databind.ser.VirtualBeanPropertyWriter;
import oss.guido.com.fasterxml.jackson.databind.util.Annotations;

/**
 * {@link VirtualBeanPropertyWriter} implementation used for
 * {@link oss.guido.com.fasterxml.jackson.databind.annotation.JsonAppend},
 * to serialize properties backed-by dynamically assignable attribute
 * values.
 * 
 * @since 2.5
 */
public class AttributePropertyWriter
    extends VirtualBeanPropertyWriter
{
    protected final String _attrName;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    protected AttributePropertyWriter(String attrName, BeanPropertyDefinition propDef,
            Annotations contextAnnotations, JavaType declaredType) {
        this(attrName, propDef, contextAnnotations, declaredType, propDef.findInclusion());
    }

    protected AttributePropertyWriter(String attrName, BeanPropertyDefinition propDef,
            Annotations contextAnnotations, JavaType declaredType,
            JsonInclude.Include inclusion)
    {
        super(propDef, contextAnnotations, declaredType,
                /* value serializer */ null, /* type serializer */ null, /* ser type */ null,
                inclusion);
        _attrName = attrName;
    }

    public static AttributePropertyWriter construct(String attrName,
            BeanPropertyDefinition propDef,
            Annotations contextAnnotations,
            JavaType declaredType)
    {
        return new AttributePropertyWriter(attrName, propDef,
                contextAnnotations, declaredType);
    }
    
    protected AttributePropertyWriter(AttributePropertyWriter base) {
        super(base);
        _attrName = base._attrName;
    }

    /**
     * Since this method should typically not be called on this sub-type,
     * default implementation simply throws an {@link IllegalStateException}.
     */
    @Override
    public VirtualBeanPropertyWriter withConfig(MapperConfig<?> config,
            AnnotatedClass declaringClass, BeanPropertyDefinition propDef, JavaType type) {
        throw new IllegalStateException("Should not be called on this type");
    }
    
    /*
    /**********************************************************
    /* Overrides for actual serialization, value access
    /**********************************************************
     */
    
    @Override
    protected Object value(Object bean, JsonGenerator jgen, SerializerProvider prov) throws Exception {
        return prov.getAttribute(_attrName);
    }
}
