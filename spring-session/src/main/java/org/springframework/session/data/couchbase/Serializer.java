package org.springframework.session.data.couchbase;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Base64Utils;
import org.springframework.util.ClassUtils;
import org.springframework.util.SerializationUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class Serializer {

    protected static final String SERIALIZED_OBJECT_PREFIX = "_$object=";

    public Map<String, Object> serializeSessionAttributes(Map<String, Object> attributes) {
        if (attributes == null) {
            return null;
        }
        Map<String, Object> serialized = new HashMap<String, Object>(attributes.size());
        for (Entry<String, Object> attribute : attributes.entrySet()) {
            if (isDeserializedObject(attribute.getValue())) {
                Object attributeValue = Base64Utils.encodeToString(SerializationUtils.serialize(attribute.getValue()));
                serialized.put(attribute.getKey(), SERIALIZED_OBJECT_PREFIX + attributeValue);
            } else {
                serialized.put(attribute.getKey(), attribute.getValue());
            }
        }
        return serialized;
    }

    public Map<String, Object> deserializeSessionAttributes(Map<String, Object> attributes) {
        if (attributes == null) {
            return null;
        }
        Map<String, Object> deserialized = new HashMap<String, Object>(attributes.size());
        for (Entry<String, Object> attribute : attributes.entrySet()) {
            Object attributeValue = attribute.getValue();
            if (isSerializedObject(attribute.getValue())) {
                String content = StringUtils.removeStart(attribute.getValue().toString(), SERIALIZED_OBJECT_PREFIX);
                attributeValue = SerializationUtils.deserialize(Base64Utils.decodeFromString(content));
            }
            deserialized.put(attribute.getKey(), attributeValue);
        }
        return deserialized;
    }

    protected boolean isDeserializedObject(Object attributeValue) {
        return attributeValue != null && !ClassUtils.isPrimitiveOrWrapper(attributeValue.getClass()) && !(attributeValue instanceof String);
    }

    protected boolean isSerializedObject(Object attributeValue) {
        return attributeValue != null && attributeValue instanceof String && StringUtils.startsWith(attributeValue.toString(), SERIALIZED_OBJECT_PREFIX);
    }
}
