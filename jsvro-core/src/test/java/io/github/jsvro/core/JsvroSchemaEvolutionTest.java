package io.github.jsvro.core;

import io.github.jsvro.core.People.Address;
import io.github.jsvro.core.People.Person;
import io.github.jsvro.core.People.Person2;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsvroSchemaEvolutionTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsvroCodec codec = new JsvroCodec(mapper);
    private final List<Person> people = People.generate(100);

    @JsonPropertyOrder({"name", "age", "born", "address", "roles", "email"})
    record PersonWithEmail(String name, int age, LocalDate born, Address address, List<String> roles, String email) {}

    @Test
    void annotatedPerson2DerivesTheSameSchemaAsPerson() {
        assertEquals(codec.schema(Person.class), codec.schema(Person2.class));
    }

    @Test
    void person2WritesTheSameBytesAsPerson() {
        List<Person2> people2 = people.stream().map(person -> Person2.from(person, "nick")).toList();

        assertArrayEquals(write(Person.class, people), write(Person2.class, people2));
    }

    @Test
    void aPersonStreamDecodesAsPerson2() {
        List<Person2> decoded = read(write(Person.class, people), Person2.class);

        assertEquals(people, decoded.stream().map(Person2::toPerson).toList());
        assertTrue(decoded.stream().allMatch(person -> person.nickname() == null));
    }

    @Test
    void aPerson2StreamDecodesAsPerson() {
        List<Person2> people2 = people.stream().map(person -> Person2.from(person, "nick")).toList();

        assertEquals(people, read(write(Person2.class, people2), Person.class));
    }

    @Test
    void aReaderIgnoresAColumnItDoesNotKnow() {
        assertEquals(people, read(write(PersonWithEmail.class, withEmail()), Person.class));
    }

    @Test
    void aReaderGivesColumnsTheStreamLacksTheirDefault() {
        List<PersonWithEmail> decoded = read(write(Person.class, people), PersonWithEmail.class);

        assertEquals(people.size(), decoded.size());
        for (int i = 0; i < people.size(); i++) {
            Person person = people.get(i);
            assertEquals(new PersonWithEmail(person.name(), person.age(), person.born(), person.address(), person.roles(), null),
                    decoded.get(i));
        }
    }

    @Test
    void failingOnUnknownPropertiesAppliesToUnknownColumns() {
        JsonMapper strict = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        byte[] wire = write(PersonWithEmail.class, withEmail());

        assertThrows(UnrecognizedPropertyException.class,
                () -> new JsvroCodec(strict).readList(new ByteArrayInputStream(wire), Person.class));
    }

    private List<PersonWithEmail> withEmail() {
        return people.stream()
                .map(p -> new PersonWithEmail(p.name(), p.age(), p.born(), p.address(), p.roles(), "x@example.com"))
                .toList();
    }

    private <T> byte[] write(Class<T> type, List<T> rows) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.write(output, type, rows);
        return output.toByteArray();
    }

    private <T> List<T> read(byte[] wire, Class<T> type) {
        return codec.readList(new ByteArrayInputStream(wire), type);
    }
}
