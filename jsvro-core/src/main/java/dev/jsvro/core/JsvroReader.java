package dev.jsvro.core;

import dev.jsvro.core.internal.NonClosingStreams;
import dev.jsvro.core.internal.RootCodec;
import dev.jsvro.core.internal.RowDecoder;
import dev.jsvro.core.internal.SchemaReader;
import dev.jsvro.core.internal.SchemaResolution;
import tools.jackson.core.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class JsvroReader<T> {
    private final RootCodec root;
    private final RowDecoder rows;
    private final Function<JsvroSchema, RowDecoder> evolved;

    JsvroReader(RootCodec root, RowDecoder rows, Function<JsvroSchema, RowDecoder> evolved) {
        this.root = root;
        this.rows = rows;
        this.evolved = evolved;
    }

    public JsvroSchema schema() {
        return root.schema();
    }

    public JsvroDecoding decoding() {
        return rows.isPositional() ? JsvroDecoding.POSITIONAL : JsvroDecoding.BUFFERED;
    }

    public List<T> readList(InputStream input) {
        Opened<T> opened = open(input);
        try (JsonParser parser = opened.parser()) {
            List<T> result = new ArrayList<>();
            opened.rows().forEachRemaining(result::add);
            return result;
        }
    }

    public Stream<T> readStream(InputStream input) {
        Opened<T> opened = open(input);
        JsonParser parser = opened.parser();
        try {
            Iterator<T> iterator = opened.rows();
            return StreamSupport.stream(
                            Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL), false)
                    .onClose(parser::close);
        }
        catch (RuntimeException ex) {
            parser.close();
            throw ex;
        }
    }

    private Opened<T> open(InputStream input) {
        Objects.requireNonNull(input, "input");
        byte[] expected = root.headerLine();
        byte[] prefix = new byte[expected.length];
        int read;
        try {
            read = input.readNBytes(prefix, 0, prefix.length);
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        if (read == expected.length && Arrays.equals(prefix, expected)) {
            JsonParser parser = rows.createParser(NonClosingStreams.input(input));
            @SuppressWarnings("unchecked")
            Iterator<T> iterator = (Iterator<T>) rows.rows(parser);
            return new Opened<>(parser, iterator);
        }
        InputStream replayed = new SequenceInputStream(new ByteArrayInputStream(prefix, 0, read), NonClosingStreams.input(input));
        JsonParser parser = rows.createParser(replayed);
        try {
            if (parser.nextToken() == null) {
                throw new JsvroException("Empty JSVRO stream");
            }
            JsvroSchema incoming = SchemaReader.read(parser);
            RowDecoder decoder = rows;
            if (!incoming.equals(root.schema())) {
                SchemaResolution.checkCompatible(root.schema().columns(), incoming.columns());
                decoder = evolved.apply(incoming);
            }
            @SuppressWarnings("unchecked")
            Iterator<T> iterator = (Iterator<T>) decoder.rows(parser);
            return new Opened<>(parser, iterator);
        }
        catch (RuntimeException ex) {
            parser.close();
            throw ex;
        }
    }

    private record Opened<T>(JsonParser parser, Iterator<T> rows) {}
}
