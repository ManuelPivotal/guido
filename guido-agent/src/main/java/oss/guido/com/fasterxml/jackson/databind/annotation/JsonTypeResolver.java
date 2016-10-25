package oss.guido.com.fasterxml.jackson.databind.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import oss.guido.com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;

/**
 * Annotation that can be used to explicitly define custom resolver
 * used for handling serialization and deserialization of type information,
 * needed for handling of polymorphic types (or sometimes just for linking
 * abstract types to concrete types)
 *<p>
 * NOTE: since 2.4, applicable to properties as well (should have been long time
 *  ago, but problem only found then)
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.TYPE, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@oss.guido.com.fasterxml.jackson.annotation.JacksonAnnotation
public @interface JsonTypeResolver
{
    /**
     * Defines implementation class of {@link TypeResolverBuilder} which is used to construct
     * actual {@link oss.guido.com.fasterxml.jackson.databind.jsontype.TypeDeserializer} and {@link oss.guido.com.fasterxml.jackson.databind.jsontype.TypeDeserializer}
     * instances that handle reading and writing addition type information needed to support polymorphic
     * deserialization.
     */
    public Class<? extends TypeResolverBuilder<?>> value();
}
