package io.github.jsvro.core;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;

interface BenchmarkFormat<T> {
    String key();

    void write(List<T> rows, OutputStream output);

    List<T> read(byte[] encoded);

    default byte[] encode(List<T> rows) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
        write(rows, output);
        return output.toByteArray();
    }
}
