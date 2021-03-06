package oss.guido.com.fasterxml.jackson.databind.ser.std;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import oss.guido.com.fasterxml.jackson.annotation.JsonFormat;
import oss.guido.com.fasterxml.jackson.core.JsonGenerationException;
import oss.guido.com.fasterxml.jackson.core.JsonGenerator;
import oss.guido.com.fasterxml.jackson.core.JsonParser;
import oss.guido.com.fasterxml.jackson.databind.BeanProperty;
import oss.guido.com.fasterxml.jackson.databind.JavaType;
import oss.guido.com.fasterxml.jackson.databind.JsonMappingException;
import oss.guido.com.fasterxml.jackson.databind.JsonNode;
import oss.guido.com.fasterxml.jackson.databind.JsonSerializer;
import oss.guido.com.fasterxml.jackson.databind.SerializationFeature;
import oss.guido.com.fasterxml.jackson.databind.SerializerProvider;
import oss.guido.com.fasterxml.jackson.databind.introspect.Annotated;
import oss.guido.com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import oss.guido.com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;
import oss.guido.com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;
import oss.guido.com.fasterxml.jackson.databind.jsonFormatVisitors.JsonValueFormat;
import oss.guido.com.fasterxml.jackson.databind.ser.ContextualSerializer;
import oss.guido.com.fasterxml.jackson.databind.util.StdDateFormat;

@SuppressWarnings("serial")
public abstract class DateTimeSerializerBase<T>
    extends StdScalarSerializer<T>
    implements ContextualSerializer
{
    /**
     * Flag that indicates that serialization must be done as the
     * Java timestamp, regardless of other settings.
     */
    protected final Boolean _useTimestamp;
    
    /**
     * Specific format to use, if not default format: non null value
     * also indicates that serialization is to be done as JSON String,
     * not numeric timestamp, unless {@link #_useTimestamp} is true.
     */
    protected final DateFormat _customFormat;

    protected DateTimeSerializerBase(Class<T> type,
            Boolean useTimestamp, DateFormat customFormat)
    {
        super(type);
        _useTimestamp = useTimestamp;
        _customFormat = customFormat;
    }

    public abstract DateTimeSerializerBase<T> withFormat(Boolean timestamp, DateFormat customFormat);

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov,
            BeanProperty property) throws JsonMappingException
    {
        if (property != null) {
            JsonFormat.Value format = prov.getAnnotationIntrospector().findFormat((Annotated)property.getMember());
            if (format != null) {

            	// Simple case first: serialize as numeric timestamp?
                if (format.getShape().isNumeric()) {
                    return withFormat(Boolean.TRUE, null);
                }

        		Boolean asNumber = (format.getShape() == JsonFormat.Shape.STRING) ? Boolean.FALSE : null;
                // If not, do we have a pattern?
                TimeZone tz = format.getTimeZone();
                if (format.hasPattern()) {
                    String pattern = format.getPattern();
                    final Locale loc = format.hasLocale() ? format.getLocale() : prov.getLocale();
                    SimpleDateFormat df = new SimpleDateFormat(pattern, loc);
                    if (tz == null) {
                        tz = prov.getTimeZone();
                    }
                    df.setTimeZone(tz);
                    return withFormat(asNumber, df);
                }
                // If not, do we at least have a custom timezone?
                if (tz != null) {
                    DateFormat df = prov.getConfig().getDateFormat();
                    // one shortcut: with our custom format, can simplify handling a bit
                    if (df.getClass() == StdDateFormat.class) {
                        final Locale loc = format.hasLocale() ? format.getLocale() : prov.getLocale();
                        df = StdDateFormat.getISO8601Format(tz, loc);
                    } else {
                        // otherwise need to clone, re-set timezone:
                        df = (DateFormat) df.clone();
                        df.setTimeZone(tz);
                    }
                    return withFormat(asNumber, df);
                }
            }
        }
        return this;
    }

    /*
    /**********************************************************
    /* Accessors
    /**********************************************************
     */

    @Override
    public boolean isEmpty(T value) {
        // let's assume "null date" (timestamp 0) qualifies for empty
        return (value == null) || (_timestamp(value) == 0L);
    }

    protected abstract long _timestamp(T value);
    
    @Override
    public JsonNode getSchema(SerializerProvider provider, Type typeHint) {
        //todo: (ryan) add a format for the date in the schema?
        return createSchemaNode(_asTimestamp(provider) ? "number" : "string", true);
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException
    {
        _acceptJsonFormatVisitor(visitor, typeHint, _asTimestamp(visitor.getProvider()));
    }

    /*
    /**********************************************************
    /* Actual serialization
    /**********************************************************
     */

    @Override
    public abstract void serialize(T value, JsonGenerator jgen, SerializerProvider provider)
        throws IOException, JsonGenerationException;

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    
    protected boolean _asTimestamp(SerializerProvider provider)
    {
        if (_useTimestamp != null) {
            return _useTimestamp.booleanValue();
        }
        if (_customFormat == null) {
            if (provider != null) {
                return provider.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }
            // 12-Jun-2014, tatu: Is it legal not to have provider? Was NPE:ing earlier so leave a check
            throw new IllegalArgumentException("Null 'provider' passed for "+handledType().getName());
        }
        return false;
    }

    protected void _acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint,
		boolean asNumber) throws JsonMappingException
    {
        if (asNumber) {
            JsonIntegerFormatVisitor v2 = visitor.expectIntegerFormat(typeHint);
            if (v2 != null) {
                v2.numberType(JsonParser.NumberType.LONG);
                v2.format(JsonValueFormat.UTC_MILLISEC);
            }
        } else {
            JsonStringFormatVisitor v2 = visitor.expectStringFormat(typeHint);
            if (v2 != null) {
                v2.format(JsonValueFormat.DATE_TIME);
            }
        }
    }
}
