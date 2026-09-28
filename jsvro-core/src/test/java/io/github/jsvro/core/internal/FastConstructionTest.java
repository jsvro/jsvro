package io.github.jsvro.core.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.jsvro.core.JsvroCodec;
import io.github.jsvro.core.JsvroDecoding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FastConstructionTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    record Point(int x, int y) {}

    record WithComputed(String name) {
        @JsonProperty("upper")
        public String upper() {
            return name.toUpperCase();
        }
    }

    public static final class Factory {
        private final String name;
        private final int size;

        private Factory(String name, int size) {
            this.name = name;
            this.size = size;
        }

        @JsonCreator
        public static Factory of(@JsonProperty("name") String name, @JsonProperty("size") int size) {
            return new Factory(name, size);
        }

        public String getName() { return name; }
        public int getSize() { return size; }
    }

    public static class Bean {
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    static final AtomicInteger FLAKY_CALLS = new AtomicInteger();

    record Flaky(String name) {
        Flaky {
            if (FLAKY_CALLS.getAndIncrement() == 0) {
                throw new IllegalStateException("first construction fails");
            }
        }
    }

    record Validated(String name) {
        Validated {
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }

    record Nullable(String name, Integer count) {}

    @BeforeEach
    void resetFlaky() {
        FLAKY_CALLS.set(0);
    }

    @Test
    void recordsUseTheFastCreator() {
        assertEquals(Construction.FAST, construction(Point.class));
        assertEquals(List.of(new Point(1, 2), new Point(3, 4)), roundTrip(Point.class, List.of(new Point(1, 2), new Point(3, 4))));
    }

    @Test
    void staticFactoryCreatorsUseTheFastCreator() {
        assertEquals(Construction.FAST, construction(Factory.class));

        Factory decoded = roundTrip(Factory.class, List.of(Factory.of("box", 3))).getFirst();
        assertEquals("box", decoded.getName());
        assertEquals(3, decoded.getSize());
    }

    @Test
    void serializeOnlyColumnsAreSkippedByTheFastCreator() {
        assertEquals(Construction.FAST, construction(WithComputed.class));
        assertEquals(List.of(new WithComputed("alice")), roundTrip(WithComputed.class, List.of(new WithComputed("alice"))));
    }

    @Test
    void setterBeansUseJacksonsDefaultConstruction() {
        Bean bean = new Bean();
        bean.setName("alice");

        assertEquals(Construction.JACKSON, construction(Bean.class));
        assertEquals("alice", roundTrip(Bean.class, List.of(bean)).getFirst().getName());
    }

    @Test
    void aFastCreatorFailureFallsBackToJacksonAndDemotesTheType() {
        byte[] wire = nameRows("a", "b");
        CodecFactory codecs = new CodecFactory(mapper);
        RowDecoder decoder = codecs.rowDecoder(mapper.constructType(Flaky.class));

        List<Object> decoded = readRows(decoder, wire);

        assertEquals(3, FLAKY_CALLS.get());
        assertEquals(List.of("a", "b"), decoded.stream().map(row -> ((Flaky) row).name()).toList());
        assertEquals(Construction.JACKSON, decoder.construction(Flaky.class));
    }

    @Test
    void constructorFailuresSurfaceAsJacksonDoesWithoutDemotion() {
        RowDecoder decoder = new CodecFactory(mapper).rowDecoder(mapper.constructType(Validated.class));

        var failure = assertThrows(ValueInstantiationException.class, () -> readRows(decoder, nameRows(" ")));

        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals(Construction.FAST, decoder.construction(Validated.class));
    }

    @Test
    void failOnNullCreatorPropertiesIsStillEnforced() {
        JsonMapper strict = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES).build();
        JsvroCodec codec = new JsvroCodec(strict);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.write(output, Nullable.class, Arrays.asList(new Nullable("a", null)));

        assertThrows(MismatchedInputException.class,
                () -> codec.readList(new ByteArrayInputStream(output.toByteArray()), Nullable.class));
    }

    @Test
    void readersReportHowTheyDecode() {
        JsvroCodec codec = new JsvroCodec(mapper);

        assertEquals(JsvroDecoding.POSITIONAL, codec.readerFor(Point.class).decoding());
    }

    private Construction construction(Class<?> type) {
        return new CodecFactory(mapper).rowDecoder(mapper.constructType(type)).construction(type);
    }

    private <T> byte[] write(Class<T> type, List<T> rows) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new JsvroCodec(mapper).write(output, type, rows);
        return output.toByteArray();
    }

    private <T> List<T> roundTrip(Class<T> type, List<T> rows) {
        return new JsvroCodec(mapper).readList(new ByteArrayInputStream(write(type, rows)), type);
    }

    private static byte[] nameRows(String... names) {
        StringBuilder wire = new StringBuilder("{\"jsvro\":\"1\",\"columns\":[{\"name\":\"name\",\"type\":\"string\"}]}\n");
        for (String name : names) {
            wire.append("[\"").append(name).append("\"]\n");
        }
        return wire.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static List<Object> readRows(RowDecoder decoder, byte[] wire) {
        try (JsonParser parser = decoder.createParser(new ByteArrayInputStream(wire))) {
            parser.nextToken();
            parser.skipChildren();
            List<Object> rows = new ArrayList<>();
            decoder.rows(parser).forEachRemaining(rows::add);
            return rows;
        }
    }
}
