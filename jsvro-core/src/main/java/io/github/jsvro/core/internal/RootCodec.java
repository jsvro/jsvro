package io.github.jsvro.core.internal;

import io.github.jsvro.core.JsvroColumn;
import io.github.jsvro.core.JsvroException;
import io.github.jsvro.core.JsvroSchema;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonSerializable;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RootCodec {
    private final JsvroSchema schema;
    private final String header;
    private final byte[] headerLine;
    private final ObjectCodec codec;
    private final Map<Class<?>, List<String>> columnsByType;
    private static final int MAX_INCOMING_PLANS = 16;

    private RowDecoder rowDecoder;
    private final Map<JsvroSchema, RowDecoder> incomingDecoders = new ConcurrentHashMap<>();

    RootCodec(JsvroSchema schema, String header, ObjectCodec codec) {
        this.schema = schema;
        this.header = header;
        this.headerLine = (header + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        this.codec = codec;
        Map<Class<?>, List<String>> collected = new HashMap<>();
        this.columnsByType = codec.collectObjectTypes(collected) ? Map.copyOf(collected) : null;
    }

    public JsvroSchema schema() {
        return schema;
    }

    public byte[] headerLine() {
        return headerLine;
    }

    int rootScalarWriters() {
        return codec.scalarWriters();
    }

    public void write(JsonGenerator generator, Iterator<?> rows) {
        generator.objectWriteContext().writeValue(generator, new JacksonSerializable.Base() {
            @Override
            public void serialize(JsonGenerator gen, SerializationContext context) {
                gen.writeRaw(header);
                gen.writeRaw('\n');
                long index = 0;
                while (rows.hasNext()) {
                    Object row = rows.next();
                    if (row == null) {
                        throw new JsvroException("Row " + index + " is null; JSVRO rows must be objects");
                    }
                    codec.write(row, gen, context);
                    gen.writeRaw('\n');
                    index++;
                }
            }

            @Override
            public void serializeWithType(JsonGenerator gen, SerializationContext context, TypeSerializer typeSerializer) {
                serialize(gen, context);
            }
        });
    }

    public synchronized RowDecoder rowDecoder(ObjectMapper mapper, JavaType type) {
        if (rowDecoder == null) {
            RowDecoder positional = columnsByType == null ? null
                    : positional(mapper, type, columnsByType, null, schema.columns());
            rowDecoder = positional != null ? positional
                    : new RowDecoder(mapper.readerFor(type), false, schema.columns(), new DecodingReport());
        }
        return rowDecoder;
    }

    public RowDecoder rowDecoderFor(ObjectMapper mapper, JavaType type, JsvroSchema incoming) {
        RowDecoder cached = incomingDecoders.get(incoming);
        if (cached != null) {
            return cached;
        }
        if (incomingDecoders.size() >= MAX_INCOMING_PLANS) {
            return rowDecoder(mapper, type).forIncoming(incoming.columns());
        }
        return incomingDecoders.computeIfAbsent(incoming, schema -> createIncoming(mapper, type, schema));
    }

    private RowDecoder createIncoming(ObjectMapper mapper, JavaType type, JsvroSchema incoming) {
        Map<Class<?>, List<String>> incomingColumns = new HashMap<>();
        if (columnsByType != null
                && codec.collectIncomingTypes(JsvroColumn.object("root", incoming.columns()), incomingColumns)) {
            RowDecoder positional = positional(mapper, type, incomingColumns, columnsByType, incoming.columns());
            if (positional != null) {
                return positional;
            }
        }
        return rowDecoder(mapper, type).forIncoming(incoming.columns());
    }

    private static RowDecoder positional(ObjectMapper mapper, JavaType type, Map<Class<?>, List<String>> columns,
            Map<Class<?>, List<String>> knownColumns, List<JsvroColumn> rowColumns) {
        PositionalDeserializerModifier modifier = knownColumns == null
                ? new PositionalDeserializerModifier(columns)
                : new PositionalDeserializerModifier(columns, knownColumns);
        ObjectMapper reading = mapper.rebuild()
                .addModule(new SimpleModule("jsvro-positional").setDeserializerModifier(modifier))
                .build();
        // Each row is one of several root values in the stream; the next row is not a trailing token.
        ObjectReader reader = reading.readerFor(type).without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        for (Class<?> positionalType : modifier.positionalTypes()) {
            reading.readerFor(positionalType);
        }
        return modifier.allTypesSupported() ? new RowDecoder(reader, true, rowColumns, modifier.report()) : null;
    }

    static SerializationContext context(JsonGenerator generator) {
        if (generator.objectWriteContext() instanceof SerializationContext context) {
            return context;
        }
        throw new JsvroException("JSVRO requires a generator created by a Jackson ObjectMapper or ObjectWriter");
    }
}
