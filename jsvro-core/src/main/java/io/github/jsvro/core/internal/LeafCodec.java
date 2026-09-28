package io.github.jsvro.core.internal;

import io.github.jsvro.core.JsvroColumn;
import io.github.jsvro.core.JsvroType;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

final class LeafCodec implements ValueCodec {
    private final JsvroType type;
    private final JsvroColumn items;
    private final ValueSerializer<Object> serializer;

    LeafCodec(JsvroType type, JsvroColumn items, ValueSerializer<Object> serializer) {
        this.type = type;
        this.items = items;
        this.serializer = serializer;
    }

    LeafCodec withSerializer(ValueSerializer<Object> serializer) {
        return new LeafCodec(type, items, serializer);
    }

    @Override
    public JsvroColumn column(String name) {
        return type == JsvroType.ARRAY ? JsvroColumn.array(name, items) : JsvroColumn.scalar(name, type);
    }

    @Override
    public void write(Object value, JsonGenerator generator, SerializationContext context) {
        if (value == null) {
            generator.writeNull();
        }
        else {
            serializer.serialize(value, generator, context);
        }
    }
}
