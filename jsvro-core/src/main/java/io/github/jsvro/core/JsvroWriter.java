package io.github.jsvro.core;

import io.github.jsvro.core.internal.NonClosingStreams;
import io.github.jsvro.core.internal.RootCodec;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.ObjectWriter;

import java.io.OutputStream;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Stream;

public final class JsvroWriter<T> {
    private static final SerializableString NO_ROOT_SEPARATOR = new SerializedString("");

    private final RootCodec root;
    private final ObjectWriter writer;

    JsvroWriter(RootCodec root, ObjectWriter writer) {
        this.root = root;
        // We write newlines ourselves; prevent Jackson from adding its default root-level space separator.
        this.writer = writer.withRootValueSeparator(NO_ROOT_SEPARATOR);
    }

    public JsvroSchema schema() {
        return root.schema();
    }

    public void write(OutputStream output, Iterable<? extends T> rows) {
        Objects.requireNonNull(rows, "rows");
        write(output, rows.iterator());
    }

    public void write(OutputStream output, Stream<? extends T> rows) {
        Objects.requireNonNull(rows, "rows");
        try (rows) {
            write(output, rows.iterator());
        }
    }

    private void write(OutputStream output, Iterator<? extends T> rows) {
        Objects.requireNonNull(output, "output");
        try (JsonGenerator generator = writer.createGenerator(NonClosingStreams.output(output))) {
            root.write(generator, rows);
            generator.flush();
        }
    }
}
