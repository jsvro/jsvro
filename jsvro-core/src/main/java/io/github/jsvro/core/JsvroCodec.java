package io.github.jsvro.core;

import io.github.jsvro.core.internal.CodecFactory;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * JSVRO encoder/decoder facade.
 *
 * <p>The encoder is streaming: the schema is written once followed by one positional JSON value per row.
 * The decoder buffers one row at a time and delegates final Java binding to Jackson.</p>
 *
 * <p>Caller-provided input and output streams remain caller-owned and are not closed by this class.</p>
 */
public final class JsvroCodec {
    private final ObjectMapper mapper;
    private final CodecFactory codecs;
    private final ConcurrentHashMap<JavaType, JsvroWriter<Object>> writers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<JavaType, JsvroReader<Object>> readers = new ConcurrentHashMap<>();

    public JsvroCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.codecs = new CodecFactory(mapper);
    }

    public <T> JsvroWriter<T> writerFor(Class<T> elementType) {
        @SuppressWarnings("unchecked")
        JsvroWriter<T> typed = (JsvroWriter<T>) writerFor(mapper.constructType(elementType));
        return typed;
    }

    public JsvroWriter<Object> writerFor(JavaType elementType) {
        Objects.requireNonNull(elementType, "elementType");
        return writers.computeIfAbsent(elementType, type -> new JsvroWriter<>(codecs.root(type), mapper.writer()));
    }

    public <T> JsvroReader<T> readerFor(Class<T> elementType) {
        @SuppressWarnings("unchecked")
        JsvroReader<T> typed = (JsvroReader<T>) readerFor(mapper.constructType(elementType));
        return typed;
    }

    public JsvroReader<Object> readerFor(JavaType elementType) {
        Objects.requireNonNull(elementType, "elementType");
        return readers.computeIfAbsent(elementType, type -> new JsvroReader<>(codecs.root(type), codecs.rowDecoder(type),
                incoming -> codecs.rowDecoder(type, incoming)));
    }

    public JsvroSchema schema(Class<?> elementType) {
        return schema(mapper.constructType(elementType));
    }

    public JsvroSchema schema(JavaType elementType) {
        Objects.requireNonNull(elementType, "elementType");
        return codecs.root(elementType).schema();
    }

    public boolean supports(JavaType elementType) {
        Objects.requireNonNull(elementType, "elementType");
        return codecs.supports(elementType);
    }

    public void write(OutputStream output, Class<?> elementType, Iterable<?> rows) {
        writerFor(mapper.constructType(elementType)).write(output, rows);
    }

    public void write(OutputStream output, JavaType elementType, Iterable<?> rows) {
        writerFor(elementType).write(output, rows);
    }

    public void write(OutputStream output, JavaType elementType, Stream<?> rows) {
        writerFor(elementType).write(output, rows);
    }

    public <T> List<T> readList(InputStream input, Class<T> elementType) {
        return readerFor(elementType).readList(input);
    }

    public List<?> readList(InputStream input, JavaType elementType) {
        return readerFor(elementType).readList(input);
    }

    public <T> Stream<T> readStream(InputStream input, Class<T> elementType) {
        return readerFor(elementType).readStream(input);
    }

    public Stream<?> readStream(InputStream input, JavaType elementType) {
        return readerFor(elementType).readStream(input);
    }

    public int cachedCodecCount() {
        return codecs.cachedCodecCount();
    }
}
