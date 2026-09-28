package io.github.jsvro.core.internal;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.jsvro.core.JsvroCodec;
import io.github.jsvro.core.JsvroColumn;
import io.github.jsvro.core.JsvroSchema;
import io.github.jsvro.core.JsvroType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvolvedSchemaDecodingTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    record City(String name, int population) {}

    record Person(String name, int age, City city) {}

    public static class Bean {
        private String name;
        private int age;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }

        @Override
        public boolean equals(Object other) {
            return other instanceof Bean bean && Objects.equals(name, bean.name) && age == bean.age;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Lenient(String name) {}

    public static class Extras {
        public String name;
        public final Map<String, Object> extra = new HashMap<>();

        @JsonAnySetter
        public void put(String key, Object value) {
            extra.put(key, value);
        }
    }

    private static JsvroColumn string(String name) {
        return JsvroColumn.scalar(name, JsvroType.STRING);
    }

    private static JsvroColumn integer(String name) {
        return JsvroColumn.scalar(name, JsvroType.INTEGER);
    }

    @Test
    void evolvedStreamsDecodePositionally() {
        JsvroSchema incoming = new JsvroSchema(List.of(integer("age"), string("email"),
                JsvroColumn.object("city", List.of(integer("population"), string("name"), string("mayor"))),
                string("name")));

        assertTrue(new CodecFactory(mapper).rowDecoder(mapper.constructType(Person.class), incoming).isPositional());
    }

    @Test
    void evolvedRecordsDecodeLikeTheEquivalentJson() {
        assertSameAsJson(Person.class,
                "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"age\",\"type\":\"integer\"},{\"name\":\"email\",\"type\":\"string\"},"
                        + "{\"name\":\"city\",\"type\":\"object\",\"columns\":[{\"name\":\"population\",\"type\":\"integer\"},"
                        + "{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"mayor\",\"type\":\"string\"}]},"
                        + "{\"name\":\"name\",\"type\":\"string\"}]}",
                "[30,\"a@example.com\",[700000,\"Oslo\",\"Anne\"],\"Alice\"]\n[40,null,null,\"Bob\"]",
                "{\"age\":30,\"email\":\"a@example.com\",\"city\":{\"population\":700000,\"name\":\"Oslo\",\"mayor\":\"Anne\"},\"name\":\"Alice\"}",
                "{\"age\":40,\"email\":null,\"city\":null,\"name\":\"Bob\"}");
    }

    @Test
    void missingRecordColumnsGetJacksonDefaults() {
        assertSameAsJson(Person.class,
                "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"city\",\"type\":\"object\",\"columns\":"
                        + "[{\"name\":\"population\",\"type\":\"integer\"}]},{\"name\":\"age\",\"type\":\"integer\"}]}",
                "[[700000],30]",
                "{\"city\":{\"population\":700000},\"age\":30}");
    }

    @Test
    void missingPrimitiveColumnsFailLikeJson() {
        String stream = "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"name\",\"type\":\"string\"}]}\n[\"Alice\"]\n";
        Exception json = assertThrows(Exception.class, () -> mapper.readValue("{\"name\":\"Alice\"}", Person.class));

        Exception jsvro = assertThrows(Exception.class, () -> decode(mapper, stream, Person.class));

        assertEquals(json.getClass(), jsvro.getClass());
    }

    @Test
    void evolvedSetterBeansDecodeLikeTheEquivalentJson() {
        assertSameAsJson(Bean.class,
                "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"nickname\",\"type\":\"string\"},{\"name\":\"age\",\"type\":\"integer\"}]}",
                "[\"Ali\",30]",
                "{\"nickname\":\"Ali\",\"age\":30}");
    }

    @Test
    void unknownColumnsFollowJacksonsUnknownPropertyRules() {
        JsonMapper strict = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        String stream = "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"email\",\"type\":\"string\"}]}\n"
                + "[\"Alice\",\"a@example.com\"]\n";

        assertThrows(UnrecognizedPropertyException.class, () -> decode(strict, stream, Bean.class));
        assertEquals(List.of(new Lenient("Alice")), decode(strict, stream, Lenient.class));
    }

    @Test
    void anySettersReceiveUnknownColumns() {
        String stream = "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"email\",\"type\":\"string\"}]}\n"
                + "[\"Alice\",\"a@example.com\"]\n";

        Extras decoded = decode(mapper, stream, Extras.class).getFirst();

        assertEquals("Alice", decoded.name);
        assertEquals(Map.of("email", "a@example.com"), decoded.extra);
    }

    @Test
    void manyDistinctIncomingSchemasStillDecode() {
        JsvroCodec codec = new JsvroCodec(mapper);
        for (int i = 0; i < 40; i++) {
            String stream = "{\"jsvro\":\"1\",\"columns\":[{\"name\":\"extra" + i + "\",\"type\":\"integer\"},"
                    + "{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"age\",\"type\":\"integer\"}]}\n[" + i + ",\"Alice\",30]\n";
            List<Bean> decoded = codec.readList(new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)), Bean.class);
            assertEquals(30, decoded.getFirst().getAge());
            assertEquals("Alice", decoded.getFirst().getName());
        }
    }

    @Test
    void classesWithAnySettersAndCreatorsKeepTheBufferedPath() {
        JsvroSchema incoming = new JsvroSchema(List.of(string("name"), string("email")));

        assertFalse(new CodecFactory(mapper).rowDecoder(mapper.constructType(CreatorExtras.class), incoming).isPositional());
    }

    public static final class CreatorExtras {
        final String name;
        final Map<String, Object> extra = new HashMap<>();

        @com.fasterxml.jackson.annotation.JsonCreator
        CreatorExtras(@com.fasterxml.jackson.annotation.JsonProperty("name") String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @JsonAnySetter
        public void put(String key, Object value) {
            extra.put(key, value);
        }
    }

    private <T> void assertSameAsJson(Class<T> type, String header, String rows, String... jsonRows) {
        List<T> expected = new java.util.ArrayList<>();
        for (String json : jsonRows) {
            expected.add(mapper.readValue(json, type));
        }
        assertEquals(expected, decode(mapper, header + "\n" + rows + "\n", type));
    }

    private static <T> List<T> decode(JsonMapper mapper, String stream, Class<T> type) {
        return new JsvroCodec(mapper).readList(new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)), type);
    }
}
