package io.github.jsvro.core;

import io.github.jsvro.core.Areas.Area;
import io.github.jsvro.core.Areas.AreaType;
import io.github.jsvro.core.People.Address;
import io.github.jsvro.core.People.Person;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AddressPostalAreaTest {
    private final JsvroCodec codec = new JsvroCodec(JsonMapper.builder().build());

    @Test
    void addressRequiresAPostArea() {
        Area municipality = new Area();
        municipality.setAreaType(AreaType.MUNICIPALITY);

        var failure = assertThrows(IllegalArgumentException.class,
                () -> new Address("Storgata 1", "Oslo", "NO", municipality));
        assertEquals("postal must be a POST area, not MUNICIPALITY", failure.getMessage());
        assertThrows(NullPointerException.class, () -> new Address("Storgata 1", "Oslo", "NO", null));
    }

    @Test
    void decodingRejectsANonPostalArea() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.write(output, Person.class, People.generate(1));
        String wire = output.toString(StandardCharsets.UTF_8).replaceFirst("\"POST\"", "\"MUNICIPALITY\"");

        var failure = assertThrows(ValueInstantiationException.class,
                () -> codec.readList(new ByteArrayInputStream(wire.getBytes(StandardCharsets.UTF_8)), Person.class));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }

    @Test
    void personRowsReachDownToThePostalBoundary() {
        JsvroColumn address = column(codec.schema(Person.class).columns(), "address");
        JsvroColumn postal = column(address.columns(), "postal");
        JsvroColumn boundary = column(postal.columns(), "boundary");

        assertEquals(JsvroType.ARRAY, boundary.type());
        assertEquals(List.of("lat", "lon"), boundary.items().columns().stream().map(JsvroColumn::name).toList());
    }

    @Test
    void fxTransactionsNestFiveLevelsDeep() {
        JsvroColumn owner = column(codec.schema(FxTransactions.FxTransactionResponse.class).columns(), "owner");
        JsvroColumn postal = column(column(owner.columns(), "address").columns(), "postal");

        assertEquals(JsvroType.ARRAY, column(postal.columns(), "boundary").type());
        assertEquals(JsvroDecoding.POSITIONAL, codec.readerFor(FxTransactions.FxTransactionResponse.class).decoding());
    }

    private static JsvroColumn column(List<JsvroColumn> columns, String name) {
        return columns.stream().filter(column -> column.name().equals(name)).findFirst().orElseThrow();
    }
}
