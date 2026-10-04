package com.farmmanagement.backend.common;

import java.util.*;
import tools.jackson.databind.JsonNode;

public final class Input {
    private Input() {}
    public static void fields(JsonNode body, String... names) {
        if (body == null || !body.isObject() || body.size() != names.length) throw new IllegalArgumentException();
        for (String name : names) if (!body.has(name)) throw new IllegalArgumentException();
    }
    public static String text(JsonNode body, String name, int max) {
        JsonNode value = body.get(name);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException();
        String result = value.asText().trim();
        if (result.isEmpty() || result.length() > max) throw new IllegalArgumentException();
        return result;
    }
    public static String password(JsonNode body, String name) {
        JsonNode value = body.get(name);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException();
        String result = value.asText();
        if (result.isEmpty() || result.length() > 128) throw new IllegalArgumentException();
        return result;
    }
    public static void strongPassword(String value) {
        if (value == null || value.length() < 12 || value.length() > 128
                || !value.matches("(?s).*\\p{L}.*") || !value.matches("(?s).*\\d.*"))
            throw new IllegalArgumentException();
    }
    public static String email(JsonNode body) {
        String email = text(body, "email", 254).toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException();
        return email;
    }
    public static List<Integer> ids(JsonNode body, String name) {
        JsonNode values = body.get(name);
        if (values == null || !values.isArray() || values.isEmpty() || values.size() > 50) throw new IllegalArgumentException();
        Set<Integer> result = new LinkedHashSet<>();
        for (JsonNode value : values) {
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() <= 0 || !result.add(value.asInt()))
                throw new IllegalArgumentException();
        }
        return List.copyOf(result);
    }
    public static List<String> codes(JsonNode body) {
        JsonNode values = body.get("permissions");
        if (values == null || !values.isArray() || values.size() > 100) throw new IllegalArgumentException();
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || !value.asText().matches("[a-z]+:[a-z]+") || !result.add(value.asText()))
                throw new IllegalArgumentException();
        }
        return List.copyOf(result);
    }
}
