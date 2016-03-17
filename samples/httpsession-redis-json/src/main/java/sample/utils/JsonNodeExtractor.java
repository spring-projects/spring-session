package sample.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.org.apache.xpath.internal.operations.Bool;

/**
 * Created by jitendra on 28/3/16.
 */
public class JsonNodeExtractor {

    public static String getStringValue(JsonNode jsonNode, String field) {
        return getStringValue(jsonNode, field, null);
    }

    public static String getStringValue(JsonNode jsonNode, String field, String defaultValue) {
        JsonNode node = getValue(jsonNode, field);
        return (node != null) ? node.asText(defaultValue) : defaultValue;
    }

    private static JsonNode getValue(JsonNode jsonNode, String field) {
        if (jsonNode.has(field)) {
            return jsonNode.get(field);
        } else {
            return null;
        }
    }

    public static Integer getIntValue(JsonNode jsonNode, String field) {
        return getIntValue(jsonNode, field, 0);
    }

    public static Integer getIntValue(JsonNode jsonNode, String field, Integer defaultValue) {
        JsonNode node = getValue(jsonNode, field);
        return (node != null) ? node.asInt(defaultValue) : defaultValue;
    }

    public static Boolean getBooleanValue(JsonNode jsonNode, String field) {
        return getBooleanValue(jsonNode, field, false);
    }

    public static Boolean getBooleanValue(JsonNode jsonNode, String field, Boolean defaultValue) {
        JsonNode node = getValue(jsonNode, field);
        return (node != null) ? node.asBoolean(defaultValue) : defaultValue;
    }
}
