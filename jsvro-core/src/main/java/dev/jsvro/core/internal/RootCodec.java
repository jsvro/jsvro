package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroException;
import dev.jsvro.core.JsvroSchema;
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

public final class RootCodec {
    private final JsvroSchema schema;
    private final String header;
    private final ObjectCodec codec;
    private final Map<Class<?>, List<String>> columnsByType;
    private RowDecoder rowDecoder;

    RootCodec(JsvroSchema schema, String header, ObjectCodec codec) {
        this.schema = schema;
        this.header = header;
        this.codec = codec;
        Map<Class<?>, List<String>> collected = new HashMap<>();
        this.columnsByType = codec.collectObjectTypes(collected) ? Map.copyOf(collected) : null;
    }

    public JsvroSchema schema() {
        return schema;
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
            rowDecoder = createRowDecoder(mapper, type);
        }
        return rowDecoder;
    }

    private RowDecoder createRowDecoder(ObjectMapper mapper, JavaType type) {
        if (columnsByType != null) {
            PositionalDeserializerModifier modifier = new PositionalDeserializerModifier(columnsByType);
            ObjectMapper reading = mapper.rebuild()
                    .addModule(new SimpleModule("jsvro-positional").setDeserializerModifier(modifier))
                    .build();
            // Each row is one of several root values in the stream; the next row is not a trailing token.
            ObjectReader reader = reading.readerFor(type).without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            for (Class<?> positionalType : modifier.positionalTypes()) {
                reading.readerFor(positionalType);
            }
            if (modifier.allTypesSupported()) {
                return new RowDecoder(reader, true, schema.columns(), modifier.report());
            }
        }
        return new RowDecoder(mapper.readerFor(type), false, schema.columns(), new DecodingReport());
    }

    static SerializationContext context(JsonGenerator generator) {
        if (generator.objectWriteContext() instanceof SerializationContext context) {
            return context;
        }
        throw new JsvroException("JSVRO requires a generator created by a Jackson ObjectMapper or ObjectWriter");
    }
}
