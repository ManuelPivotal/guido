package oss.guido.com.fasterxml.jackson.databind.ser.std;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicReference;

import oss.guido.com.fasterxml.jackson.core.JsonGenerator;
import oss.guido.com.fasterxml.jackson.databind.JavaType;
import oss.guido.com.fasterxml.jackson.databind.JsonMappingException;
import oss.guido.com.fasterxml.jackson.databind.JsonNode;
import oss.guido.com.fasterxml.jackson.databind.JsonSerializable;
import oss.guido.com.fasterxml.jackson.databind.ObjectMapper;
import oss.guido.com.fasterxml.jackson.databind.SerializerProvider;
import oss.guido.com.fasterxml.jackson.databind.annotation.JacksonStdImpl;
import oss.guido.com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import oss.guido.com.fasterxml.jackson.databind.jsonschema.JsonSerializableSchema;
import oss.guido.com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import oss.guido.com.fasterxml.jackson.databind.node.ObjectNode;
import oss.guido.com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * Generic handler for types that implement {@link JsonSerializable}.
 *<p>
 * Note: given that this is used for anything that implements
 * interface, can not be checked for direct class equivalence.
 */
@JacksonStdImpl
@SuppressWarnings("serial")
public class SerializableSerializer
    extends StdSerializer<JsonSerializable>
{
    public final static SerializableSerializer instance = new SerializableSerializer();

    // Ugh. Should NOT need this...
    private final static AtomicReference<ObjectMapper> _mapperReference = new AtomicReference<ObjectMapper>();
    
    protected SerializableSerializer() { super(JsonSerializable.class); }

    @Override
    public void serialize(JsonSerializable value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        value.serialize(jgen, provider);
    }

    @Override
    public final void serializeWithType(JsonSerializable value, JsonGenerator jgen, SerializerProvider provider,
            TypeSerializer typeSer) throws IOException {
        value.serializeWithType(jgen, provider, typeSer);
    }
    
    @Override
    @SuppressWarnings("deprecation")
    public JsonNode getSchema(SerializerProvider provider, Type typeHint)
        throws JsonMappingException
    {
        ObjectNode objectNode = createObjectNode();
        String schemaType = "any";
        String objectProperties = null;
        String itemDefinition = null;
        if (typeHint != null) {
            Class<?> rawClass = TypeFactory.rawClass(typeHint);
            if (rawClass.isAnnotationPresent(JsonSerializableSchema.class)) {
                JsonSerializableSchema schemaInfo = rawClass.getAnnotation(JsonSerializableSchema.class);
                schemaType = schemaInfo.schemaType();
                if (!JsonSerializableSchema.NO_VALUE.equals(schemaInfo.schemaObjectPropertiesDefinition())) {
                    objectProperties = schemaInfo.schemaObjectPropertiesDefinition();
                }
                if (!JsonSerializableSchema.NO_VALUE.equals(schemaInfo.schemaItemDefinition())) {
                    itemDefinition = schemaInfo.schemaItemDefinition();
                }
            }
        }
        /* 19-Mar-2012, tatu: geez, this is butt-ugly abonimation of code...
         *    really, really should not require back ref to an ObjectMapper.
         */
        objectNode.put("type", schemaType);
        if (objectProperties != null) {
            try {
                objectNode.put("properties", _getObjectMapper().readTree(objectProperties));
            } catch (IOException e) {
                throw new JsonMappingException("Failed to parse @JsonSerializableSchema.schemaObjectPropertiesDefinition value");
            }
        }
        if (itemDefinition != null) {
            try {
                objectNode.put("items", _getObjectMapper().readTree(itemDefinition));
            } catch (IOException e) {
                throw new JsonMappingException("Failed to parse @JsonSerializableSchema.schemaItemDefinition value");
            }
        }
        // always optional, no need to specify:
        //objectNode.put("required", false);
        return objectNode;
    }
    
    private final static synchronized ObjectMapper _getObjectMapper()
    {
        ObjectMapper mapper = _mapperReference.get();
        if (mapper == null) {
            mapper = new ObjectMapper();
            _mapperReference.set(mapper);
        }
        return mapper;
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint)
        throws JsonMappingException
    {
        visitor.expectAnyFormat(typeHint);
    }
}