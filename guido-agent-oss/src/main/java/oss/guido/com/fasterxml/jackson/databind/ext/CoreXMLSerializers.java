package oss.guido.com.fasterxml.jackson.databind.ext;

import java.io.IOException;
import java.util.Calendar;

import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import oss.guido.com.fasterxml.jackson.core.JsonGenerator;
import oss.guido.com.fasterxml.jackson.databind.BeanDescription;
import oss.guido.com.fasterxml.jackson.databind.BeanProperty;
import oss.guido.com.fasterxml.jackson.databind.JavaType;
import oss.guido.com.fasterxml.jackson.databind.JsonMappingException;
import oss.guido.com.fasterxml.jackson.databind.JsonSerializer;
import oss.guido.com.fasterxml.jackson.databind.SerializationConfig;
import oss.guido.com.fasterxml.jackson.databind.SerializerProvider;
import oss.guido.com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import oss.guido.com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import oss.guido.com.fasterxml.jackson.databind.ser.ContextualSerializer;
import oss.guido.com.fasterxml.jackson.databind.ser.Serializers;
import oss.guido.com.fasterxml.jackson.databind.ser.std.CalendarSerializer;
import oss.guido.com.fasterxml.jackson.databind.ser.std.StdSerializer;
import oss.guido.com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Provider for serializers of XML types that are part of full JDK 1.5, but
 * that some alleged 1.5 platforms are missing (Android, GAE).
 * And for this reason these are added using more dynamic mechanism.
 *<p>
 * Note: since many of classes defined are abstract, caller must take
 * care not to just use straight equivalency check but rather consider
 * subclassing as well.
 */
public class CoreXMLSerializers extends Serializers.Base
{
    @Override
    public JsonSerializer<?> findSerializer(SerializationConfig config,
            JavaType type, BeanDescription beanDesc)
    {
        Class<?> raw = type.getRawClass();
        if (Duration.class.isAssignableFrom(raw) || QName.class.isAssignableFrom(raw)) {
            return ToStringSerializer.instance;
        }
        if (XMLGregorianCalendar.class.isAssignableFrom(raw)) {
            return XMLGregorianCalendarSerializer.instance;
        }
        return null;
    }

    @SuppressWarnings("serial")
    public static class XMLGregorianCalendarSerializer
        extends StdSerializer<XMLGregorianCalendar>
        implements ContextualSerializer
    {
        final static XMLGregorianCalendarSerializer instance = new XMLGregorianCalendarSerializer();

        final JsonSerializer<Object> _delegate;
        
        public XMLGregorianCalendarSerializer() {
            this(CalendarSerializer.instance);
        }

        @SuppressWarnings("unchecked")
        protected XMLGregorianCalendarSerializer(JsonSerializer<?> del) {
            super(XMLGregorianCalendar.class);
            _delegate = (JsonSerializer<Object>) del;
        }

        @Override
        public JsonSerializer<?> getDelegatee() {
            return _delegate;
        }

        @Deprecated
        @Override
        public boolean isEmpty(XMLGregorianCalendar value) {
            return _delegate.isEmpty(_convert(value));
        }

        @Override
        public boolean isEmpty(SerializerProvider provider, XMLGregorianCalendar value) {
            return _delegate.isEmpty(provider, _convert(value));
        }

        @Override
        public void serialize(XMLGregorianCalendar value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            _delegate.serialize(_convert(value), jgen, provider);
        }

        @Override
        public void serializeWithType(XMLGregorianCalendar value, JsonGenerator gen, SerializerProvider provider,
                TypeSerializer typeSer) throws IOException
        {
            _delegate.serializeWithType(_convert(value), gen, provider, typeSer);
        }

        @Override
        public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException {
            _delegate.acceptJsonFormatVisitor(visitor, null);
        }

        @Override
        public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
                throws JsonMappingException {
            JsonSerializer<?> ser = prov.handlePrimaryContextualization(_delegate, property);
            if (ser != _delegate) {
                return new XMLGregorianCalendarSerializer(ser);
            }
            return this;
        }

        protected Calendar _convert(XMLGregorianCalendar input) {
            return (input == null) ? null : input.toGregorianCalendar();
        }
    }
}
