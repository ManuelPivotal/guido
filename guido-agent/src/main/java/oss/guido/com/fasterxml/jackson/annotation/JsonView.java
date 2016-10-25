package oss.guido.com.fasterxml.jackson.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used for indicating view(s) that the property
 * that is defined by method or field annotated is part of.
 *<p>
 * An example annotation would be:
 *<pre>
 *  &#064;JsonView(BasicView.class)
 *</pre>
 * which would specify that property annotated would be included
 * when processing (serializing, deserializing) View identified
 * by <code>BasicView.class</code> (or its sub-class).
 * If multiple View class identifiers are included, property will
 * be part of all of them.
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.FIELD,
	    ElementType.PARAMETER // since 2.5
})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotation
public @interface JsonView {
    /**
     * View or views that annotated element is part of. Views are identified
     * by classes, and use expected class inheritance relationship: child
     * views contain all elements parent views have, for example.
     */
    public Class<?>[] value() default { };
}