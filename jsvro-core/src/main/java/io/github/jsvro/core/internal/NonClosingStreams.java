package io.github.jsvro.core.internal;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Keeps ownership of caller-provided streams with the caller while allowing Jackson parser/generator close. */
public final class NonClosingStreams {
    private NonClosingStreams() {
    }

    public static InputStream input(InputStream input) {
        return new FilterInputStream(input) {
            @Override
            public void close() {
                // Deliberately do not close the caller-owned stream.
            }
        };
    }

    public static OutputStream output(OutputStream output) {
        return new FilterOutputStream(output) {
            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                out.write(bytes, offset, length);
            }

            @Override
            public void close() throws IOException {
                flush();
                // Deliberately do not close the caller-owned stream.
            }
        };
    }
}
