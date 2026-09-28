package dev.jsvro.core.internal;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.jsvro.core.JsvroCodec;
import dev.jsvro.core.JsvroColumn;
import dev.jsvro.core.JsvroException;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.ser.std.StdSerializer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScalarEncodingTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    record Scalars(String text, int count, long total, double ratio, boolean active, Integer boxed) {}

    public static class PublicFields {
        public String name;
        public double value;

        PublicFields(String name, double value) {
            this.name = name;
            this.value = value;
        }
    }

    public static final class Shout extends StdSerializer<String> {
        public Shout() {
            super(String.class);
        }

        @Override
        public void serialize(String value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(value.toUpperCase());
        }

        @Override
        public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType type) {
            visitor.expectStringFormat(type);
        }
    }

    public static final class Dash extends StdSerializer<Object> {
        public Dash() {
            super(Object.class);
        }

        @Override
        public void serialize(Object value, JsonGenerator generator, SerializationContext context) {
            generator.writeString("-");
        }
    }

    record NullsAsDash(@JsonSerialize(nullsUsing = Dash.class) String name, int count) {}

    record Custom(@JsonSerialize(using = Shout.class) String name) {}

    record AsString(@JsonFormat(shape = JsonFormat.Shape.STRING) int count) {}

    record NonEmpty(@JsonInclude(JsonInclude.Include.NON_EMPTY) String name, int count) {}

    public static class Failing {
        public String getName() {
            throw new IllegalStateException("broken getter");
        }
    }

    @Test
    void recordAndFieldScalarsGetWriters() {
        assertEquals(5, scalarWriters(Scalars.class));
        assertEquals(2, scalarWriters(PublicFields.class));
    }

    @Test
    void annotatedOrSuppressedPropertiesKeepTheGenericPath() {
        assertEquals(0, scalarWriters(Custom.class));
        assertEquals(0, scalarWriters(AsString.class));
        assertEquals(1, scalarWriters(NonEmpty.class));
        assertEquals(1, scalarWriters(NullsAsDash.class));
    }

    @Test
    void customNullSerializersAreRespected() {
        assertEquals(List.of("[\"-\",1]"), rows(NullsAsDash.class, List.of(new NullsAsDash(null, 1))));
    }

    @Test
    void valuesAreWrittenExactlyAsJsonWritesThem() {
        assertSameAsJson(Scalars.class, List.of(
                new Scalars("plain", 7, 9_000_000_000L, 0.25, true, 3),
                new Scalars("quote \" and \\ and \n and æøå and \u0001", -1, Long.MIN_VALUE, -0.0, false, null),
                new Scalars(null, Integer.MAX_VALUE, Long.MAX_VALUE, Double.NaN, true, Integer.MIN_VALUE),
                new Scalars("", 0, 0, Double.POSITIVE_INFINITY, false, 0),
                new Scalars("tiny", 1, 1, Double.MIN_VALUE, true, 1),
                new Scalars("huge", 1, 1, Double.NEGATIVE_INFINITY, true, 1)));
        assertSameAsJson(PublicFields.class, List.of(new PublicFields("x", 1e-300), new PublicFields(null, 123456789.125)));
    }

    @Test
    void customAndShapedPropertiesAreWrittenAsJsonWritesThem() {
        assertSameAsJson(Custom.class, List.of(new Custom("alice")));
        assertSameAsJson(AsString.class, List.of(new AsString(7)));
    }

    @Test
    void suppressedValuesStillOccupyTheirColumn() {
        assertEquals(List.of("[null,1]"), rows(NonEmpty.class, List.of(new NonEmpty("", 1))));
    }

    @Test
    void getterFailuresNameTheProperty() {
        JsvroException failure = assertThrows(JsvroException.class, () -> rows(Failing.class, List.of(new Failing())));

        assertEquals("Could not write property 'name'", failure.getMessage());
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    private <T> void assertSameAsJson(Class<T> type, List<T> values) {
        List<JsvroColumn> columns = new JsvroCodec(mapper).schema(type).columns();
        List<String> rows = rows(type, values);
        for (int i = 0; i < values.size(); i++) {
            JsonNode json = mapper.readTree(mapper.writeValueAsString(values.get(i)));
            JsonNode row = mapper.readTree(rows.get(i));
            for (int column = 0; column < columns.size(); column++) {
                assertEquals(json.get(columns.get(column).name()), row.get(column),
                        type.getSimpleName() + "." + columns.get(column).name() + " in row " + i);
            }
        }
    }

    private <T> List<String> rows(Class<T> type, List<T> values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new JsvroCodec(mapper).write(output, type, values);
        List<String> lines = output.toString(StandardCharsets.UTF_8).lines().toList();
        return lines.subList(1, lines.size());
    }

    private int scalarWriters(Class<?> type) {
        return new CodecFactory(mapper).root(mapper.constructType(type)).rootScalarWriters();
    }
}
