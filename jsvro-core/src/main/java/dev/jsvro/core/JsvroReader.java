package dev.jsvro.core;

import dev.jsvro.core.internal.NonClosingStreams;
import dev.jsvro.core.internal.RootCodec;
import dev.jsvro.core.internal.RowDecoder;
import dev.jsvro.core.internal.SchemaValidator;
import tools.jackson.core.JsonParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class JsvroReader<T> {
    private final RootCodec root;
    private final RowDecoder rows;

    JsvroReader(RootCodec root, RowDecoder rows) {
        this.root = root;
        this.rows = rows;
    }

    public JsvroSchema schema() {
        return root.schema();
    }

    public JsvroDecoding decoding() {
        return rows.isPositional() ? JsvroDecoding.POSITIONAL : JsvroDecoding.BUFFERED;
    }

    public List<T> readList(InputStream input) {
        try (JsonParser parser = open(input)) {
            List<T> result = new ArrayList<>();
            rows(parser).forEachRemaining(result::add);
            return result;
        }
    }

    public Stream<T> readStream(InputStream input) {
        JsonParser parser = open(input);
        try {
            Iterator<T> iterator = rows(parser);
            return StreamSupport.stream(
                            Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL), false)
                    .onClose(parser::close);
        }
        catch (RuntimeException ex) {
            parser.close();
            throw ex;
        }
    }

    private JsonParser open(InputStream input) {
        Objects.requireNonNull(input, "input");
        JsonParser parser = rows.createParser(NonClosingStreams.input(input));
        try {
            if (parser.nextToken() == null) {
                throw new JsvroException("Empty JSVRO stream");
            }
            SchemaValidator.validate(root.schema(), parser);
            return parser;
        }
        catch (RuntimeException ex) {
            parser.close();
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    private Iterator<T> rows(JsonParser parser) {
        return (Iterator<T>) rows.rows(parser);
    }
}
