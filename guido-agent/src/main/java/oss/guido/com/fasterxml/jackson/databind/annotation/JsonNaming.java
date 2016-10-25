package oss.guido.com.fasterxml.jackson.databind.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import oss.guido.com.fasterxml.jackson.databind.PropertyNamingStrategy;

/**
 * Annotation that can be used to indicate a {@link PropertyNamingStrategy}
 * to use for annotated class. Overrides the global (default) strategy.
 * 
 * @since 2.1
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@oss.guido.com.fasterxml.jackson.annotation.JacksonAnnotation
public @interface JsonNaming
{
    /**
     * @return Type of {@link PropertyNamingStrategy} to use, if any; default value of
     *    <code>PropertyNamingStrategy.class</code> means "no strategy specified"
     *    (and may also be used for overriding to remove otherwise applicable
     *    naming strategy)
     */
    public Class<? extends PropertyNamingStrategy> value() default PropertyNamingStrategy.class;
}
