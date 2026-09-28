package io.github.jsvro.core;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsvroDecoderTest {
    private static final String HEADER =
            "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"age\",\"type\":\"integer\"}]}\n";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsvroCodec codec = new JsvroCodec(mapper);

    record Person(String name, int age) {}

    @Test
    void rejectsNullRows() {
        assertDecodeFails(HEADER + "[\"Alice\",30]\nnull\n", "Expected row 1 to be a JSON array but got VALUE_NULL");
    }

    @Test
    void rejectsRowsWithTooFewValues() {
        assertDecodeFails(HEADER + "[\"Alice\"]\n", "Expected 2 positional values at row 0 but got 1");
    }

    @Test
    void rejectsRowsWithTooManyValues() {
        assertDecodeFails(HEADER + "[\"Alice\",30,true]\n", "Expected 2 positional values at row 0 but got more");
    }

    @Test
    void rejectsUnsupportedVersions() {
        assertDecodeFails("{\"jsvro\":\"2\",\"columns\":[]}\n", "Unsupported JSVRO version: expected \"1\" but got \"2\"");
    }

    @Test
    void rejectsNonStringVersions() {
        assertDecodeFails("{\"jsvro\":1,\"columns\":[]}\n", "Unsupported JSVRO version: expected \"1\" but got 1");
    }

    @Test
    void rejectsEmptyStreams() {
        assertDecodeFails("", "Empty JSVRO stream");
    }

    @Test
    void differentScalarTypesAreCoercedLikeJson() {
        String input = "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"age\",\"type\":\"string\"}]}\n"
                + "[\"Alice\",\"30\"]\n";

        assertEquals(List.of(new Person("Alice", 30)), codec.readList(stream(input), Person.class));
    }

    @Test
    void structuralConflictsAreReportedWithTheirPath() {
        assertDecodeFails("{\"jsvro\":\"1\",\"columns\":[{\"name\":\"age\",\"type\":\"object\",\"columns\":[]}]}\n",
                "Schema conflict at columns[0] (\"age\"): expected a scalar but got an object");
    }

    @Test
    void acceptsHeaderPropertiesInAnyOrderAndIgnoresUnknownOnes() {
        String header = "{\"columns\":[{\"type\":\"string\",\"note\":{\"a\":[1]},\"name\":\"name\"},"
                + "{\"name\":\"age\",\"type\":\"integer\"}],\"extra\":[1,2],\"jsvro\":\"1\"}\n";

        assertEquals(List.of(new Person("Alice", 30)), codec.readList(stream(header + "[\"Alice\",30]\n"), Person.class));
    }

    @Test
    void rejectsAHeaderWithoutColumns() {
        assertDecodeFails("{\"jsvro\":\"1\"}\n", "JSVRO schema must contain a columns array");
    }

    @Test
    void missingColumnsDecodeLikeMissingJsonFields() {
        String missingName = "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"age\",\"type\":\"integer\"}]}\n[30]\n";
        assertEquals(List.of(new Person(null, 30)), codec.readList(stream(missingName), Person.class));

        String missingAge = "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"name\",\"type\":\"string\"}]}\n[\"Alice\"]\n";
        Exception json = assertThrows(Exception.class, () -> mapper.readValue("{\"name\":\"Alice\"}", Person.class));
        Exception jsvro = assertThrows(Exception.class, () -> codec.readList(stream(missingAge), Person.class));
        assertEquals(json.getClass(), jsvro.getClass());
    }

    @Test
    void aHeaderThatDiffersOnlyInWhitespaceStillDecodes() {
        String input = HEADER.replace(",", ", ").replace("\n", "\n\n") + "[\"Alice\",30]\n";

        assertEquals(List.of(new Person("Alice", 30)), codec.readList(stream(input), Person.class));
    }

    @Test
    void aStreamShorterThanTheExpectedHeaderIsStillParsed() {
        assertDecodeFails("{\"jsvro\":\"2\",\"columns\":[]}", "Unsupported JSVRO version: expected \"1\" but got \"2\"");
    }

    @Test
    void reorderedColumnsAreMatchedByName() {
        String input = "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"age\",\"type\":\"integer\"},{\"name\":\"name\",\"type\":\"string\"}]}\n"
                + "[30,\"Alice\"]\n[40,\"Bob\"]\n";

        assertEquals(List.of(new Person("Alice", 30), new Person("Bob", 40)), codec.readList(stream(input), Person.class));
    }

    @Test
    void rowsAreCheckedAgainstTheIncomingSchema() {
        String input = "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"age\",\"type\":\"integer\"},{\"name\":\"name\",\"type\":\"string\"}]}\n"
                + "[30]\n";

        assertDecodeFails(input, "Expected 2 positional values at row 0 but got 1");
    }

    @Test
    void malformedHeadersAreRejected() {
        String prefix = "{\"jsvro\":\"1\",\"columns\":[";
        assertDecodeFails(prefix + "{\"name\":\"age\",\"type\":\"money\"}]}\n",
                "Invalid schema at columns[0].type: expected a JSVRO type but got \"money\"");
        assertDecodeFails(prefix + "{\"name\":\"age\",\"type\":\"integer\"},{\"name\":\"age\",\"type\":\"integer\"}]}\n",
                "Invalid schema at columns[1].name: duplicate column \"age\"");
        assertDecodeFails(prefix + "{\"name\":\"address\",\"type\":\"object\"}]}\n",
                "Invalid schema at columns[0].columns: expected a columns array but got nothing");
        assertDecodeFails(prefix + "{\"name\":\"roles\",\"type\":\"array\"}]}\n",
                "Invalid schema at columns[0].items: expected a column object but got nothing");
        assertDecodeFails(prefix + "{\"type\":\"integer\"}]}\n",
                "Invalid schema at columns[0].name: expected a string but got nothing");
    }

    record Owner(String name, List<Pet> pets) {}

    record Pet(String kind) {}

    @Test
    void nestedColumnsAreResolvedByNameToo() {
        String input = "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"name\",\"type\":\"string\"},"
                + "{\"name\":\"pets\",\"type\":\"array\",\"items\":{\"type\":\"object\",\"columns\":["
                + "{\"name\":\"species\",\"type\":\"string\"},{\"name\":\"kind\",\"type\":\"string\"}]}}]}\n"
                + "[\"Alice\",[[\"felis catus\",\"cat\"]]]\n";

        assertEquals(List.of(new Owner("Alice", List.of(new Pet("cat")))), codec.readList(stream(input), Owner.class));
    }

    @Test
    void nestedStructuralConflictsAreReportedWithTheirPath() {
        String header = "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"name\",\"type\":\"string\"},"
                + "{\"name\":\"pets\",\"type\":\"array\",\"items\":{\"type\":\"string\"}}]}\n";

        var failure = assertThrows(JsvroException.class, () -> codec.readList(stream(header), Owner.class));
        assertEquals("Schema conflict at columns[1].items (\"item\"): expected an object but got a scalar",
                failure.getMessage());
    }

    @Test
    void readStreamDecodesLazily() {
        String input = HEADER + "[\"Alice\",30]\n[\"broken\"]\n";

        try (Stream<Person> rows = codec.readStream(stream(input), Person.class)) {
            Iterator<Person> iterator = rows.iterator();
            assertEquals(new Person("Alice", 30), iterator.next());
            assertThrows(JsvroException.class, iterator::next);
        }
    }

    @Test
    void closingTheReadStreamLeavesTheCallersInputOpen() {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream input = new ByteArrayInputStream((HEADER + "[\"Alice\",30]\n").getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() {
                closed.set(true);
            }
        };

        try (Stream<Person> rows = codec.readStream(input, Person.class)) {
            assertEquals(List.of(new Person("Alice", 30)), rows.toList());
        }
        assertFalse(closed.get());
    }

    @Test
    void writingAStreamClosesIt() {
        AtomicBoolean closed = new AtomicBoolean();
        Stream<Person> rows = Stream.of(new Person("Alice", 30)).onClose(() -> closed.set(true));

        codec.write(new ByteArrayOutputStream(), mapper.constructType(Person.class), rows);

        assertTrue(closed.get());
    }

    private void assertDecodeFails(String input, String expectedMessage) {
        var failure = assertThrows(JsvroException.class, () -> codec.readList(stream(input), Person.class));
        assertEquals(expectedMessage, failure.getMessage());
    }

    private static InputStream stream(String input) {
        return new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    }
}
