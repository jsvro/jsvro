package io.github.jsvro.core.internal;

import io.github.jsvro.core.JsvroColumn;
import io.github.jsvro.core.JsvroException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

final class ArrayCodec implements ValueCodec {
    private final ValueCodec itemCodec;

    ArrayCodec(ValueCodec itemCodec) {
        this.itemCodec = itemCodec;
    }

    @Override
    public JsvroColumn column(String name) {
        return JsvroColumn.array(name, itemCodec.column("item"));
    }

    @Override
    public boolean collectObjectTypes(Map<Class<?>, List<String>> columnsByType) {
        return itemCodec.collectObjectTypes(columnsByType);
    }

    @Override
    public boolean collectIncomingTypes(JsvroColumn incoming, Map<Class<?>, List<String>> columnsByType) {
        return incoming.items() == null || itemCodec.collectIncomingTypes(incoming.items(), columnsByType);
    }

    @Override
    public void write(Object value, JsonGenerator generator, SerializationContext context) {
        if (value == null) {
            generator.writeNull();
            return;
        }

        generator.writeStartArray();
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                itemCodec.write(Array.get(value, i), generator, context);
            }
        }
        else if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                itemCodec.write(item, generator, context);
            }
        }
        else {
            throw new JsvroException("Cannot write " + value.getClass().getName() + " as a JSVRO array");
        }
        generator.writeEndArray();
    }
}
