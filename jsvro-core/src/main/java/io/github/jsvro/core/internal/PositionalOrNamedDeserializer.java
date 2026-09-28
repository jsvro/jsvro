package io.github.jsvro.core.internal;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.deser.std.DelegatingDeserializer;

import java.util.List;

final class PositionalOrNamedDeserializer extends DelegatingDeserializer {
    private final List<String> columns;
    private final List<String> knownColumns;
    private final DecodingReport report;
    private volatile PositionalBeanDeserializer positional;

    PositionalOrNamedDeserializer(ValueDeserializer<?> named, List<String> columns, List<String> knownColumns,
            DecodingReport report) {
        super(named);
        this.columns = columns;
        this.knownColumns = knownColumns;
        this.report = report;
    }

    @Override
    protected ValueDeserializer<?> newDelegatingInstance(ValueDeserializer<?> newDelegatee) {
        return new PositionalOrNamedDeserializer(newDelegatee, columns, knownColumns, report);
    }

    @Override
    public void resolve(DeserializationContext context) {
        super.resolve(context);
        positional();
    }

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.isExpectedStartArrayToken()) {
            return positional().deserialize(parser, context);
        }
        return _delegatee.deserialize(parser, context);
    }

    private PositionalBeanDeserializer positional() {
        PositionalBeanDeserializer current = positional;
        if (current == null) {
            current = new PositionalBeanDeserializer((BeanDeserializerBase) _delegatee, columns, knownColumns, report);
            positional = current;
        }
        return current;
    }
}
