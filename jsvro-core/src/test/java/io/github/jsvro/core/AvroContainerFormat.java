package io.github.jsvro.core;

import org.apache.avro.file.DataFileStream;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class AvroContainerFormat<T, A extends SpecificRecordBase> implements BenchmarkFormat<T> {
    private final A prototype;
    private final SpecificDatumWriter<A> writer;
    private final SpecificDatumReader<A> reader;
    private final Function<T, A> toAvro;
    private final Function<A, T> fromAvro;

    AvroContainerFormat(Class<A> type, A prototype, Function<T, A> toAvro, Function<A, T> fromAvro) {
        this.prototype = prototype;
        this.writer = new SpecificDatumWriter<>(type);
        this.reader = new SpecificDatumReader<>(type);
        this.toAvro = toAvro;
        this.fromAvro = fromAvro;
    }

    @Override
    public String key() {
        return "avro";
    }

    @Override
    public void write(List<T> rows, OutputStream output) {
        try (DataFileWriter<A> file = new DataFileWriter<>(writer)) {
            file.create(prototype.getSchema(), output);
            for (T row : rows) {
                file.append(toAvro.apply(row));
            }
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public List<T> read(byte[] encoded) {
        try (DataFileStream<A> file = new DataFileStream<>(new ByteArrayInputStream(encoded), reader)) {
            List<T> rows = new ArrayList<>();
            for (A row : file) {
                rows.add(fromAvro.apply(row));
            }
            return rows;
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
