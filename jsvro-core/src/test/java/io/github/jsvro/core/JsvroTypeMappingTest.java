package io.github.jsvro.core;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsvroTypeMappingTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsvroCodec codec = new JsvroCodec(mapper);

    record Address(String city, String country) {}

    public enum Desk { FX, RATES }

    record Money(BigDecimal amount) {}

    record BigCount(BigInteger count) {}

    record Clock(LocalTime time) {}

    record Elapsed(Duration duration) {}

    record Maybe(Optional<String> value) {}

    record MaybeAddress(Optional<Address> address) {}

    record Formatted(@JsonFormat(pattern = "dd.MM.yyyy") LocalDate date) {}

    record Scalars(Desk desk, boolean active, double ratio, Instant at, OffsetDateTime offset, UUID id) {}

    record Blob(byte[] data) {}

    record Attributes(Map<String, Integer> counts, Map<String, Address> addresses) {}

    record Containers(List<Address> list, Address[] array, int[] numbers) {}

    record Nested(Address address, List<String> roles) {}

    @JsonPropertyOrder({"second", "first"})
    record Renamed(@JsonProperty("first") String a, @JsonProperty("second") String b, @JsonIgnore String hidden) {}

    record SnakeCase(String firstName) {}

    @Test
    void decimalsRoundTripWithoutLosingPrecisionOrScale() {
        var rows = List.of(new Money(new BigDecimal("12345678901234567.89")), new Money(new BigDecimal("1.10")));

        Wire wire = write(Money.class, rows);

        assertEquals("[{\"name\":\"amount\",\"type\":\"decimal\"}]", wire.columns());
        assertEquals(List.of("[12345678901234567.89]", "[1.10]"), wire.rows());
        assertEquals(rows, read(wire, Money.class));
    }

    @Test
    void bigIntegersRoundTripExactly() {
        var rows = List.of(new BigCount(new BigInteger("123456789012345678901234567890")));

        Wire wire = write(BigCount.class, rows);

        assertEquals("[{\"name\":\"count\",\"type\":\"integer\"}]", wire.columns());
        assertEquals(rows, read(wire, BigCount.class));
    }

    @Test
    void localTimeIsWrittenAsJacksonsStringForm() {
        var rows = List.of(new Clock(LocalTime.of(10, 15)));

        Wire wire = write(Clock.class, rows);

        assertEquals("[{\"name\":\"time\",\"type\":\"string\"}]", wire.columns());
        assertEquals(List.of("[\"10:15:00\"]"), wire.rows());
        assertEquals(rows, read(wire, Clock.class));
    }

    @Test
    void durationIsWrittenAsJacksonsStringForm() {
        var rows = List.of(new Elapsed(Duration.ofSeconds(90)));

        Wire wire = write(Elapsed.class, rows);

        assertEquals("[{\"name\":\"duration\",\"type\":\"string\"}]", wire.columns());
        assertEquals(List.of("[\"PT1M30S\"]"), wire.rows());
        assertEquals(rows, read(wire, Elapsed.class));
    }

    @Test
    void optionalScalarsAreWrittenAsTheirContentOrNull() {
        var rows = List.of(new Maybe(Optional.of("x")), new Maybe(Optional.empty()));

        Wire wire = write(Maybe.class, rows);

        assertEquals("[{\"name\":\"value\",\"type\":\"string\"}]", wire.columns());
        assertEquals(List.of("[\"x\"]", "[null]"), wire.rows());
        assertEquals(rows, read(wire, Maybe.class));
    }

    @Test
    void optionalObjectsArePositional() {
        var rows = List.of(new MaybeAddress(Optional.of(new Address("Oslo", "NO"))), new MaybeAddress(Optional.empty()));

        Wire wire = write(MaybeAddress.class, rows);

        assertEquals("[{\"name\":\"address\",\"type\":\"object\",\"columns\":["
                + "{\"name\":\"city\",\"type\":\"string\"},{\"name\":\"country\",\"type\":\"string\"}]}]", wire.columns());
        assertEquals(List.of("[[\"Oslo\",\"NO\"]]", "[null]"), wire.rows());
        assertEquals(rows, read(wire, MaybeAddress.class));
    }

    @Test
    void propertyLevelJsonFormatIsHonoured() {
        var rows = List.of(new Formatted(LocalDate.of(2026, 9, 24)));

        Wire wire = write(Formatted.class, rows);

        assertEquals("[{\"name\":\"date\",\"type\":\"date\"}]", wire.columns());
        assertEquals(List.of("[\"24.09.2026\"]"), wire.rows());
        assertEquals(rows, read(wire, Formatted.class));
    }

    @Test
    void scalarTypesMapToTheirWireNames() {
        UUID id = UUID.fromString("5f1b0c2e-4a57-4f2f-9d59-7b1e8f7c4a10");
        var rows = List.of(new Scalars(Desk.FX, true, 0.5, Instant.parse("2026-09-24T08:00:00Z"),
                OffsetDateTime.of(2026, 9, 24, 10, 0, 0, 0, ZoneOffset.ofHours(2)), id));

        Wire wire = write(Scalars.class, rows);

        assertEquals("[{\"name\":\"desk\",\"type\":\"string\"},"
                + "{\"name\":\"active\",\"type\":\"boolean\"},"
                + "{\"name\":\"ratio\",\"type\":\"number\"},"
                + "{\"name\":\"at\",\"type\":\"datetime\"},"
                + "{\"name\":\"offset\",\"type\":\"datetime\"},"
                + "{\"name\":\"id\",\"type\":\"uuid\"}]", wire.columns());
        assertEquals(List.of("[\"FX\",true,0.5,\"2026-09-24T08:00:00Z\",\"2026-09-24T10:00:00+02:00\",\"" + id + "\"]"),
                wire.rows());
        assertEquals(plainJsonRoundTrip(rows, Scalars.class), read(wire, Scalars.class));
    }

    @Test
    void byteArraysAreBinary() {
        byte[] data = {1, 2, 3};

        Wire wire = write(Blob.class, List.of(new Blob(data)));

        assertEquals("[{\"name\":\"data\",\"type\":\"binary\"}]", wire.columns());
        assertEquals(List.of("[\"AQID\"]"), wire.rows());
        assertArrayEquals(data, read(wire, Blob.class).getFirst().data());
    }

    @Test
    void mapsKeepOrdinaryJsonObjectForm() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("a", 1);
        counts.put("b", 2);
        var rows = List.of(new Attributes(counts, Map.of("home", new Address("Oslo", "NO"))));

        Wire wire = write(Attributes.class, rows);

        assertEquals("[{\"name\":\"counts\",\"type\":\"map\"},{\"name\":\"addresses\",\"type\":\"map\"}]", wire.columns());
        assertEquals(List.of("[{\"a\":1,\"b\":2},{\"home\":{\"city\":\"Oslo\",\"country\":\"NO\"}}]"), wire.rows());
        assertEquals(rows, read(wire, Attributes.class));
    }

    @Test
    void collectionsAndArraysOfObjectsArePositional() {
        var rows = List.of(new Containers(
                List.of(new Address("Oslo", "NO")), new Address[] {new Address("Bergen", "NO")}, new int[] {1, 2}));

        Wire wire = write(Containers.class, rows);

        String address = "{\"type\":\"object\",\"columns\":["
                + "{\"name\":\"city\",\"type\":\"string\"},{\"name\":\"country\",\"type\":\"string\"}]}";
        assertEquals("[{\"name\":\"list\",\"type\":\"array\",\"items\":" + address + "},"
                + "{\"name\":\"array\",\"type\":\"array\",\"items\":" + address + "},"
                + "{\"name\":\"numbers\",\"type\":\"array\",\"items\":{\"type\":\"integer\"}}]", wire.columns());
        assertEquals(List.of("[[[\"Oslo\",\"NO\"]],[[\"Bergen\",\"NO\"]],[1,2]]"), wire.rows());

        Containers decoded = read(wire, Containers.class).getFirst();
        assertEquals(rows.getFirst().list(), decoded.list());
        assertArrayEquals(rows.getFirst().array(), decoded.array());
        assertArrayEquals(rows.getFirst().numbers(), decoded.numbers());
    }

    @Test
    void nullNestedValuesAreWrittenAsNull() {
        var rows = Arrays.asList(new Nested(null, null), new Nested(new Address("Oslo", null), List.of()));

        Wire wire = write(Nested.class, rows);

        assertEquals(List.of("[null,null]", "[[\"Oslo\",null],[]]"), wire.rows());
        assertEquals(rows, read(wire, Nested.class));
    }

    @Test
    void jacksonPropertyNamesOrderAndIgnoresAreFollowed() {
        var rows = List.of(new Renamed("a", "b", "secret"));

        Wire wire = write(Renamed.class, rows);

        assertEquals("[{\"name\":\"second\",\"type\":\"string\"},{\"name\":\"first\",\"type\":\"string\"}]", wire.columns());
        assertEquals(List.of("[\"b\",\"a\"]"), wire.rows());
        assertEquals(List.of(new Renamed("a", "b", null)), read(wire, Renamed.class));
    }

    @Test
    void mapperNamingStrategyIsFollowed() {
        JsonMapper snakeMapper = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        JsvroCodec snakeCodec = new JsvroCodec(snakeMapper);

        assertEquals(List.of(JsvroColumn.scalar("first_name", JsvroType.STRING)),
                snakeCodec.schema(SnakeCase.class).columns());
    }

    private <T> List<T> plainJsonRoundTrip(List<T> rows, Class<T> type) {
        return mapper.readValue(mapper.writeValueAsString(rows),
                mapper.getTypeFactory().constructCollectionType(List.class, type));
    }

    private <T> Wire write(Class<T> type, List<T> rows) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.write(output, type, rows);
        return new Wire(output.toByteArray());
    }

    private <T> List<T> read(Wire wire, Class<T> type) {
        return codec.readList(new ByteArrayInputStream(wire.bytes()), type);
    }

    private record Wire(byte[] bytes) {
        private List<String> lines() {
            return List.of(new String(bytes, StandardCharsets.UTF_8).split("\n"));
        }

        String columns() {
            String header = lines().getFirst();
            return header.substring("{\"jsvro\":\"1\",\"columns\":".length(), header.length() - 1);
        }

        List<String> rows() {
            return lines().subList(1, lines().size());
        }
    }
}
