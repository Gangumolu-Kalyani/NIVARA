package com.sih.nivara.assistant.tool;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small builders for the JSON Schema fragments tools describe their arguments with. */
public final class JsonSchema {

    private JsonSchema() {
        // utility class
    }

    public static Map<String, Object> string(String description, int maxLength) {
        return ordered("type", "string", "maxLength", maxLength, "description", description);
    }

    public static Map<String, Object> uuid(String description) {
        return ordered("type", "string", "format", "uuid", "description", description);
    }

    public static Map<String, Object> date(String description) {
        return ordered("type", "string", "format", "date", "description", description);
    }

    public static Map<String, Object> integer(String description, int minimum, int maximum) {
        return ordered("type", "integer", "minimum", minimum, "maximum", maximum, "description", description);
    }

    public static Map<String, Object> oneOf(String description, Enum<?>[] values) {
        List<String> names = Arrays.stream(values).map(Enum::name).toList();
        return ordered("type", "string", "enum", names, "description", description);
    }

    /** A map that keeps the order of the key/value pairs given. */
    public static Map<String, Object> ordered(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
