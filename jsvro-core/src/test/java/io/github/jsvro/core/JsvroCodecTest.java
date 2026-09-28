package io.github.jsvro.core;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsvroCodecTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsvroCodec codec = new JsvroCodec(mapper);

    record Address(String street, String city, String country) {}

    record Person(String name, int age, LocalDate born, Address address, List<String> roles) {}

    @Test
    void writesSchemaOnceAndPositionalRowsRecursively() {
        var people = List.of(
                new Person("Alice", 30, LocalDate.of(1996, 3, 12),
                        new Address("Main Street 1", "Oslo", "NO"), List.of("ADMIN", "USER")),
                new Person("Bob", 40, LocalDate.of(1986, 8, 1),
                        new Address("Parkveien 4", "Bergen", "NO"), List.of("USER")));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.write(output, Person.class, people);

        String text = output.toString(StandardCharsets.UTF_8);
        String[] lines = text.strip().split("\\R");

        assertEquals(3, lines.length);
        assertEquals(
                "{\"jsvro\":\"1\",\"columns\":["
                        + "{\"name\":\"name\",\"type\":\"string\"},"
                        + "{\"name\":\"age\",\"type\":\"integer\"},"
                        + "{\"name\":\"born\",\"type\":\"date\"},"
                        + "{\"name\":\"address\",\"type\":\"object\",\"columns\":["
                        + "{\"name\":\"street\",\"type\":\"string\"},"
                        + "{\"name\":\"city\",\"type\":\"string\"},"
                        + "{\"name\":\"country\",\"type\":\"string\"}]},"
                        + "{\"name\":\"roles\",\"type\":\"array\",\"items\":{\"type\":\"string\"}}]}",
                lines[0]);
        assertEquals("[\"Alice\",30,\"1996-03-12\",[\"Main Street 1\",\"Oslo\",\"NO\"],[\"ADMIN\",\"USER\"]]", lines[1]);
        assertEquals("[\"Bob\",40,\"1986-08-01\",[\"Parkveien 4\",\"Bergen\",\"NO\"],[\"USER\"]]", lines[2]);
    }

    @Test
    void emptyResultStillWritesTheSchema() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.write(output, Person.class, List.of());

        assertEquals(1, output.toString(StandardCharsets.UTF_8).strip().split("\\R").length);
        assertTrue(output.toString(StandardCharsets.UTF_8).startsWith("{\"jsvro\":\"1\""));
    }

    @Test
    void roundTripsRows() {
        var source = List.of(
                new Person("Alice", 30, LocalDate.of(1996, 3, 12),
                        new Address("Main Street 1", "Oslo", "NO"), List.of("USER")),
                new Person("Bob", 40, LocalDate.of(1986, 8, 1),
                        new Address("Parkveien 4", "Bergen", "NO"), List.of("ADMIN")));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.write(output, Person.class, source);

        JavaType personType = mapper.constructType(Person.class);
        List<?> decoded = codec.readList(new ByteArrayInputStream(output.toByteArray()), personType);

        assertEquals(source, decoded);
        assertTrue(mapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS));
    }

    @Test
    void aHeaderWithoutMatchingColumnsDecodesLikeTheEquivalentJson() {
        String input = """
                {"jsvro":"1","columns":[{"name":"wrong","type":"string"}]}
                ["Alice"]
                """;
        Exception json = assertThrows(Exception.class, () -> mapper.readValue("{\"wrong\":\"Alice\"}", Person.class));

        Exception jsvro = assertThrows(Exception.class, () -> codec.readList(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), Person.class));

        assertEquals(json.getClass(), jsvro.getClass());
    }

    @Test
    void keepsCallerOwnedStreamsOpen() {
        CloseAwareOutputStream output = new CloseAwareOutputStream();
        codec.write(output, Person.class, List.of());
        assertFalse(output.closed);

        CloseAwareInputStream input = new CloseAwareInputStream(output.toByteArray());
        codec.readList(input, Person.class);
        assertFalse(input.closed);
    }

    @Test
    void cachesDerivedCodecs() {
        codec.schema(Person.class);
        int afterFirst = codec.cachedCodecCount();
        codec.schema(Person.class);
        assertEquals(afterFirst, codec.cachedCodecCount());
    }

    private static final class CloseAwareOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class CloseAwareInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseAwareInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
