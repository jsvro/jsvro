package io.github.jsvro.core.internal;

import io.github.jsvro.core.JsvroColumn;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

final class ReferenceCodec implements ValueCodec {
    private final ValueCodec contentCodec;
    private final UnaryOperator<Object> dereference;

    private ReferenceCodec(ValueCodec contentCodec, UnaryOperator<Object> dereference) {
        this.contentCodec = contentCodec;
        this.dereference = dereference;
    }

    static ReferenceCodec forType(Class<?> referenceType, ValueCodec contentCodec) {
        if (referenceType == Optional.class) {
            return new ReferenceCodec(contentCodec, value -> ((Optional<?>) value).orElse(null));
        }
        if (referenceType == AtomicReference.class) {
            return new ReferenceCodec(contentCodec, value -> ((AtomicReference<?>) value).get());
        }
        return null;
    }

    @Override
    public JsvroColumn column(String name) {
        return contentCodec.column(name);
    }

    @Override
    public boolean collectObjectTypes(Map<Class<?>, List<String>> columnsByType) {
        return contentCodec.collectObjectTypes(columnsByType);
    }

    @Override
    public boolean collectIncomingTypes(JsvroColumn incoming, Map<Class<?>, List<String>> columnsByType) {
        return contentCodec.collectIncomingTypes(incoming, columnsByType);
    }

    @Override
    public void write(Object value, JsonGenerator generator, SerializationContext context) {
        contentCodec.write(value == null ? null : dereference.apply(value), generator, context);
    }
}
