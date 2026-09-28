package dev.jsvro.core;

import dev.jsvro.core.internal.NonClosingStreams;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsvroPreparedCodecTest {
    private final JsvroCodec codec = new JsvroCodec(JsonMapper.builder().build());

    record Person(String name, int age) {}

    record Recursive(List<Recursive> children) {}

    @Test
    void preparedWriterIsReusableAndMatchesTheFacade() {
        JsvroWriter<Person> writer = codec.writerFor(Person.class);
        List<Person> rows = List.of(new Person("Alice", 30));

        ByteArrayOutputStream first = new ByteArrayOutputStream();
        ByteArrayOutputStream second = new ByteArrayOutputStream();
        ByteArrayOutputStream facade = new ByteArrayOutputStream();
        writer.write(first, rows);
        writer.write(second, rows);
        codec.write(facade, Person.class, rows);

        assertArrayEquals(facade.toByteArray(), first.toByteArray());
        assertArrayEquals(facade.toByteArray(), second.toByteArray());
    }

    @Test
    void preparedReaderIsReusable() {
        JsvroReader<Person> reader = codec.readerFor(Person.class);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.writerFor(Person.class).write(output, List.of(new Person("Alice", 30)));

        assertEquals(List.of(new Person("Alice", 30)), reader.readList(new ByteArrayInputStream(output.toByteArray())));
        assertEquals(List.of(new Person("Alice", 30)), reader.readList(new ByteArrayInputStream(output.toByteArray())));
    }

    @Test
    void preparedHandlesAreCachedPerType() {
        assertSame(codec.writerFor(Person.class), codec.writerFor(Person.class));
        assertSame(codec.readerFor(Person.class), codec.readerFor(Person.class));
    }

    @Test
    void repeatedHasNextDoesNotSkipRows() {
        assertRowsSurviveRepeatedHasNext(Person.class, List.of(new Person("Alice", 30), new Person("Bob", 40)));
        assertRowsSurviveRepeatedHasNext(TwoShapes.class, List.of(
                new TwoShapes(new Inner("a", "b"), new Inner("c", null)), new TwoShapes(new Inner("d", "e"), null)));
    }

    record Inner(String first, String second) {}

    record TwoShapes(Inner full, @JsonIgnoreProperties("second") Inner firstOnly) {}

    private <T> void assertRowsSurviveRepeatedHasNext(Class<T> type, List<T> rows) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.writerFor(type).write(output, rows);

        try (Stream<T> stream = codec.readerFor(type).readStream(new ByteArrayInputStream(output.toByteArray()))) {
            Iterator<T> iterator = stream.iterator();
            List<T> decoded = new ArrayList<>();
            while (iterator.hasNext() && iterator.hasNext()) {
                decoded.add(iterator.next());
            }
            assertEquals(rows, decoded);
        }
    }

    @Test
    void preparingDerivesTheSchemaUpFront() {
        assertEquals(codec.schema(Person.class), codec.writerFor(Person.class).schema());
        assertThrows(JsvroException.class, () -> codec.writerFor(Recursive.class));
        assertThrows(JsvroException.class, () -> codec.readerFor(Recursive.class));
    }

    @Test
    void nonClosingOutputPassesBulkWritesThrough() throws IOException {
        AtomicInteger singleByteWrites = new AtomicInteger();
        ByteArrayOutputStream target = new ByteArrayOutputStream() {
            @Override
            public synchronized void write(int b) {
                singleByteWrites.incrementAndGet();
                super.write(b);
            }
        };

        try (OutputStream output = NonClosingStreams.output(target)) {
            output.write(new byte[] {1, 2, 3, 4}, 1, 2);
        }

        assertArrayEquals(new byte[] {2, 3}, target.toByteArray());
        assertEquals(0, singleByteWrites.get());
    }
}
