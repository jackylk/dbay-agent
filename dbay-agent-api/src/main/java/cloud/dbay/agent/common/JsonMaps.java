package cloud.dbay.agent.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonMaps {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private JsonMaps() {}

    public static String stringify(Object value) {
        try {
            return value == null ? null : MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON value", e);
        }
    }

    public static Map<String, Object> parse(String value) {
        if (value == null || value.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>(Map.of("raw", value));
        }
    }
}
