package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroException;
import dev.jsvro.core.JsvroSchema;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;

import java.io.OutputStream;
import java.io.StringWriter;
import java.util.concurrent.ConcurrentHashMap;

public final class CodecFactory {
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<JavaType, Object> roots = new ConcurrentHashMap<>();

    public CodecFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public RootCodec root(JavaType type) {
        Object root = roots.computeIfAbsent(type, this::build);
        if (root instanceof JsvroException failure) {
            throw new JsvroException(failure.getMessage(), failure);
        }
        return (RootCodec) root;
    }

    public boolean supports(JavaType type) {
        return roots.computeIfAbsent(type, this::build) instanceof RootCodec;
    }

    public RowDecoder rowDecoder(JavaType type) {
        return root(type).rowDecoder(mapper, type);
    }

    public RowDecoder rowDecoder(JavaType type, JsvroSchema incoming) {
        return root(type).rowDecoderFor(mapper, type, incoming);
    }

    public int cachedCodecCount() {
        return (int) roots.values().stream().filter(RootCodec.class::isInstance).count();
    }

    private String header(JsvroSchema schema) {
        StringWriter header = new StringWriter();
        try (JsonGenerator generator = mapper.writer().createGenerator(header)) {
            SchemaWriter.write(generator, schema);
        }
        return header.toString();
    }

    private Object build(JavaType type) {
        try (JsonGenerator generator = mapper.createGenerator(OutputStream.nullOutputStream())) {
            SerializationContext context = RootCodec.context(generator);
            ObjectCodec codec = new SchemaDerivation(context).root(type);
            JsvroSchema schema = new JsvroSchema(codec.column("root").columns());
            return new RootCodec(schema, header(schema), codec);
        }
        catch (JsvroException ex) {
            return ex;
        }
        catch (JacksonException ex) {
            return new JsvroException("Cannot derive a JSVRO schema for " + type + ": " + ex.getOriginalMessage(), ex);
        }
    }
}
