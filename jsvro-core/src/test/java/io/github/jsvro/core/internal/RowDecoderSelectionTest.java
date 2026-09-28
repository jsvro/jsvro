package io.github.jsvro.core.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.jsvro.core.JsvroCodec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowDecoderSelectionTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final CodecFactory codecs = new CodecFactory(mapper);

    record Address(String city, String country) {}

    record Person(String name, int age, LocalDate born, Address address, List<Address> previous,
            Optional<Address> holiday, Map<String, Address> named) {}

    public static class Bean {
        private String name;
        private Address address;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Address getAddress() { return address; }
        public void setAddress(Address address) { this.address = address; }
    }

    record TwoShapes(Address full, @JsonIgnoreProperties("country") Address cityOnly) {}

    @Test
    void recordsAndNestedObjectsDecodePositionally() {
        assertTrue(codecs.rowDecoder(mapper.constructType(Person.class)).isPositional());
    }

    @Test
    void setterBeansDecodePositionally() {
        assertTrue(codecs.rowDecoder(mapper.constructType(Bean.class)).isPositional());
    }

    @Test
    void typesWithDifferentColumnsPerPositionFallBackToBufferedDecoding() {
        assertFalse(codecs.rowDecoder(mapper.constructType(TwoShapes.class)).isPositional());
    }

    @Test
    void positionalDecodingHandlesNestedObjectsInEveryContainer() {
        var rows = List.of(new Person("Alice", 30, LocalDate.of(1996, 3, 12), new Address("Oslo", "NO"),
                List.of(new Address("Bergen", "NO")), Optional.of(new Address("Rome", "IT")),
                Map.of("work", new Address("Stavanger", "NO"))));

        assertEquals(rows, roundTrip(Person.class, rows));
    }

    @Test
    void bufferedFallbackStillRoundTrips() {
        var rows = List.of(new TwoShapes(new Address("Oslo", "NO"), new Address("Bergen", null)));

        assertEquals(rows, roundTrip(TwoShapes.class, rows));
    }

    @Test
    void theCallersMapperIsNotModified() {
        codecs.rowDecoder(mapper.constructType(Person.class));

        assertEquals("{\"city\":\"Oslo\",\"country\":\"NO\"}",
                mapper.writeValueAsString(mapper.readValue("{\"city\":\"Oslo\",\"country\":\"NO\"}", Address.class)));
    }

    private <T> List<T> roundTrip(Class<T> type, List<T> rows) {
        JsvroCodec codec = new JsvroCodec(mapper);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.write(output, type, rows);
        return codec.readList(new ByteArrayInputStream(output.toByteArray()), type);
    }
}
