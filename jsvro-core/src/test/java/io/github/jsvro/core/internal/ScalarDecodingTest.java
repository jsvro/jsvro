package io.github.jsvro.core.internal;

import io.github.jsvro.core.JsvroCodec;
import io.github.jsvro.core.JsvroColumn;
import io.github.jsvro.core.JsvroException;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScalarDecodingTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    public static class Scalars {
        private String text;
        private int count;
        private long total;
        private double ratio;
        private boolean active;
        private Integer boxed;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
        public double getRatio() { return ratio; }
        public void setRatio(double ratio) { this.ratio = ratio; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public Integer getBoxed() { return boxed; }
        public void setBoxed(Integer boxed) { this.boxed = boxed; }

        @Override
        public boolean equals(Object other) {
            return other instanceof Scalars s && Objects.equals(text, s.text) && count == s.count && total == s.total
                    && ratio == s.ratio && active == s.active && Objects.equals(boxed, s.boxed);
        }

        @Override
        public int hashCode() {
            return Objects.hash(text, count, total, ratio, active, boxed);
        }

        @Override
        public String toString() {
            return text + "," + count + "," + total + "," + ratio + "," + active + "," + boxed;
        }
    }

    public static class PublicFields {
        public String name;
        public double value;
    }

    public static final class Upper extends StdDeserializer<String> {
        public Upper() {
            super(String.class);
        }

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) {
            return parser.getString().toUpperCase();
        }
    }

    public static class Custom {
        private String name;

        public String getName() { return name; }

        @JsonDeserialize(using = Upper.class)
        public void setName(String name) { this.name = name; }
    }

    public static class Validating {
        private int count;

        public int getCount() { return count; }

        public void setCount(int count) {
            if (count < 0) {
                throw new IllegalArgumentException("count must not be negative");
            }
            this.count = count;
        }
    }

    @Test
    void setterAndFieldScalarsGetSlots() {
        assertEquals(5, scalarSlots(Scalars.class));
        assertEquals(2, scalarSlots(PublicFields.class));
    }

    @Test
    void customDeserializersKeepTheGenericPath() {
        assertEquals(0, scalarSlots(Custom.class));

        Custom decoded = decode("{\"name\":\"alice\"}", Custom.class);
        assertEquals("ALICE", decoded.getName());
    }

    @Test
    void canonicalValuesDecodeLikeJson() {
        assertSameAsJson("{\"text\":\"a\",\"count\":7,\"total\":9000000000,\"ratio\":0.25,\"active\":true,\"boxed\":3}");
    }

    @Test
    void unusualTokensFallBackToJacksonsBehaviour() {
        assertSameAsJson("{\"text\":null,\"count\":\"12\",\"total\":\"13\",\"ratio\":4,\"active\":false,\"boxed\":null}");
        assertSameAsJson("{\"text\":\"a\",\"count\":null,\"total\":1,\"ratio\":1.5,\"active\":null,\"boxed\":1}");
        assertSameAsJson("{\"text\":\"a\",\"count\":1.5,\"total\":1,\"ratio\":1.5,\"active\":\"true\",\"boxed\":1}");
        assertSameAsJson("{\"text\":\"a\",\"count\":99999999999,\"total\":1,\"ratio\":1.5,\"active\":true,\"boxed\":1}");
        assertSameAsJson("{\"text\":7,\"count\":1,\"total\":1.0,\"ratio\":\"2.5\",\"active\":1,\"boxed\":\"4\"}");
    }

    @Test
    void publicFieldsAreSetDirectly() {
        PublicFields decoded = decode("{\"name\":\"x\",\"value\":1.25}", PublicFields.class);

        assertEquals("x", decoded.name);
        assertEquals(1.25, decoded.value);
    }

    @Test
    void setterFailuresSurfaceLikeJson() {
        Exception json = assertThrows(Exception.class,
                () -> mapper.readValue("{\"count\":-1}", Validating.class));
        Exception jsvro = assertThrows(Exception.class, () -> decodeJsvro("{\"count\":-1}", Validating.class));

        assertEquals(json.getClass(), jsvro.getClass());
        assertNotNull(jsvro.getCause());
        assertEquals(IllegalArgumentException.class, jsvro.getCause().getClass());
    }

    @Test
    void setterBeansRejectShortRows() {
        JsvroException failure = assertThrows(JsvroException.class, () -> decodeRow("[\"x\"]", PublicFields.class));

        assertEquals("Expected 2 positional values at row 0 but got 1", failure.getMessage());
    }

    private void assertSameAsJson(String json) {
        Object expected;
        try {
            expected = mapper.readValue(json, Scalars.class);
        }
        catch (Exception jsonFailure) {
            Exception jsvroFailure = assertThrows(Exception.class, () -> decodeJsvro(json, Scalars.class));
            assertEquals(jsonFailure.getClass(), jsvroFailure.getClass(), jsvroFailure.getMessage());
            return;
        }
        assertEquals(expected, decodeJsvro(json, Scalars.class));
    }

    private <T> T decode(String json, Class<T> type) {
        T decoded = decodeJsvro(json, type);
        assertEquals(mapper.writeValueAsString(mapper.readValue(json, type)), mapper.writeValueAsString(decoded));
        return decoded;
    }

    private <T> T decodeJsvro(String json, Class<T> type) {
        JsonNode object = mapper.readTree(json);
        ArrayNode row = mapper.createArrayNode();
        for (JsvroColumn column : new JsvroCodec(mapper).schema(type).columns()) {
            row.add(object.get(column.name()));
        }
        return decodeRow(row.toString(), type);
    }

    private <T> T decodeRow(String row, Class<T> type) {
        JsvroCodec codec = new JsvroCodec(mapper);
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        codec.write(header, type, List.of());
        String wire = header.toString(StandardCharsets.UTF_8) + row + "\n";
        return codec.readList(new ByteArrayInputStream(wire.getBytes(StandardCharsets.UTF_8)), type).getFirst();
    }

    private int scalarSlots(Class<?> type) {
        return new CodecFactory(mapper).rowDecoder(mapper.constructType(type)).scalarSlots(type);
    }
}
