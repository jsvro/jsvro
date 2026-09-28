package io.github.jsvro.core.internal;

import io.github.jsvro.core.JsvroColumn;
import io.github.jsvro.core.JsvroException;
import io.github.jsvro.core.JsvroSchema;
import io.github.jsvro.core.JsvroType;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SchemaReader {
    private static final Map<String, JsvroType> TYPES = Arrays.stream(JsvroType.values())
            .collect(Collectors.toMap(JsvroType::wireName, Function.identity()));

    private SchemaReader() {
    }

    public static JsvroSchema read(JsonParser parser) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new JsvroException("JSVRO stream must start with a schema object");
        }
        String version = null;
        List<JsvroColumn> columns = null;
        while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
            String property = parser.currentName();
            JsonToken value = parser.nextToken();
            switch (property) {
                case "jsvro" -> {
                    if (value != JsonToken.VALUE_STRING || !JsvroSchema.CURRENT_VERSION.equals(parser.getString())) {
                        throw unsupportedVersion(describe(parser, value));
                    }
                    version = parser.getString();
                }
                case "columns" -> {
                    if (value != JsonToken.START_ARRAY) {
                        throw new JsvroException("JSVRO schema must contain a columns array");
                    }
                    columns = columns(parser, "columns");
                }
                default -> parser.skipChildren();
            }
        }
        if (version == null) {
            throw unsupportedVersion("nothing");
        }
        if (columns == null) {
            throw new JsvroException("JSVRO schema must contain a columns array");
        }
        return new JsvroSchema(version, columns);
    }

    private static List<JsvroColumn> columns(JsonParser parser, String path) {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw invalid(path, "a columns array", describe(parser, parser.currentToken()));
        }
        List<JsvroColumn> columns = new ArrayList<>();
        Set<String> names = new HashSet<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            String columnPath = path + "[" + columns.size() + "]";
            JsvroColumn column = column(parser, columnPath, true);
            if (!names.add(column.name())) {
                throw new JsvroException("Invalid schema at " + columnPath + ".name: duplicate column \"" + column.name() + "\"");
            }
            columns.add(column);
        }
        return columns;
    }

    private static JsvroColumn column(JsonParser parser, String path, boolean named) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw invalid(path, "a column object", describe(parser, parser.currentToken()));
        }
        String name = named ? null : "item";
        JsvroType type = null;
        List<JsvroColumn> columns = null;
        JsvroColumn items = null;
        while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
            String property = parser.currentName();
            JsonToken value = parser.nextToken();
            switch (property) {
                case "name" -> {
                    if (named) {
                        if (value != JsonToken.VALUE_STRING) {
                            throw invalid(path + ".name", "a string", describe(parser, value));
                        }
                        name = parser.getString();
                    }
                    else {
                        parser.skipChildren();
                    }
                }
                case "type" -> {
                    if (value != JsonToken.VALUE_STRING || !TYPES.containsKey(parser.getString())) {
                        throw invalid(path + ".type", "a JSVRO type", describe(parser, value));
                    }
                    type = TYPES.get(parser.getString());
                }
                case "columns" -> columns = columns(parser, path + ".columns");
                case "items" -> items = column(parser, path + ".items", false);
                default -> parser.skipChildren();
            }
        }
        if (name == null) {
            throw invalid(path + ".name", "a string", "nothing");
        }
        if (type == null) {
            throw invalid(path + ".type", "a JSVRO type", "nothing");
        }
        if (type == JsvroType.OBJECT && columns == null) {
            throw invalid(path + ".columns", "a columns array", "nothing");
        }
        if (type == JsvroType.ARRAY && items == null) {
            throw invalid(path + ".items", "a column object", "nothing");
        }
        return new JsvroColumn(name, type, type == JsvroType.OBJECT ? columns : List.of(),
                type == JsvroType.ARRAY ? items : null);
    }

    private static String describe(JsonParser parser, JsonToken token) {
        if (token == null) {
            return "nothing";
        }
        return switch (token) {
            case VALUE_STRING -> "\"" + parser.getString() + "\"";
            case START_OBJECT -> "an object";
            case START_ARRAY -> "an array";
            default -> parser.getString();
        };
    }

    private static JsvroException unsupportedVersion(String actual) {
        return new JsvroException("Unsupported JSVRO version: expected \"" + JsvroSchema.CURRENT_VERSION + "\" but got " + actual);
    }

    private static JsvroException invalid(String path, String expected, String actual) {
        return new JsvroException("Invalid schema at " + path + ": expected " + expected + " but got " + actual);
    }
}
