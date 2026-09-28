package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroColumn;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;

import java.util.List;
import java.util.Map;

interface ValueCodec {
    JsvroColumn column(String name);

    void write(Object value, JsonGenerator generator, SerializationContext context);

    default boolean collectObjectTypes(Map<Class<?>, List<String>> columnsByType) {
        return true;
    }

    default boolean collectIncomingTypes(JsvroColumn incoming, Map<Class<?>, List<String>> columnsByType) {
        return true;
    }
}
