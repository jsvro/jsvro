package io.github.jsvro.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.github.jsvro.core.Areas.Area;
import io.github.jsvro.core.Areas.AreaType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;

final class People {
    private static final List<String> FIRST_NAMES = List.of("Alice", "Bob", "Carol", "Dag", "Eva", "Frode", "Guro", "Hans");
    private static final List<String> LAST_NAMES = List.of("Hansen", "Johansen", "Olsen", "Larsen", "Andersen", "Berg");
    private static final List<String> STREETS = List.of("Main Street", "Parkveien", "Storgata", "Kongens gate", "Bryggen");
    private static final List<String> CITIES = List.of("Oslo", "Bergen", "Trondheim", "Stavanger", "Tromsø");
    private static final List<String> ROLES = List.of("ADMIN", "USER", "AUDITOR", "TRADER", "SUPPORT");

    record Address(String street, String city, String country, Area postal) {
        Address {
            Objects.requireNonNull(postal, "postal");
            if (postal.getAreaType() != AreaType.POST) {
                throw new IllegalArgumentException("postal must be a POST area, not " + postal.getAreaType());
            }
        }
    }

    record Person(String name, int age, LocalDate born, Address address, List<String> roles) {}

    @JsonPropertyOrder({"name", "age", "born", "address", "roles"})
    record Person2(
            @JsonProperty("roles") List<String> permissions,
            @JsonProperty("address") Address homeAddress,
            @JsonProperty("born") LocalDate birthDate,
            @JsonProperty("age") int ageInYears,
            @JsonProperty("name") String fullName,
            @JsonIgnore String nickname) {

        static Person2 from(Person person, String nickname) {
            return new Person2(person.roles(), person.address(), person.born(), person.age(), person.name(), nickname);
        }

        Person toPerson() {
            return new Person(fullName, ageInYears, birthDate, homeAddress, permissions);
        }
    }

    private People() {
    }

    static List<Person> generate(int count) {
        Random random = new Random(42);
        return IntStream.range(0, count).mapToObj(i -> person(random)).toList();
    }

    static Person person(Random random) {
        return new Person(
                pick(random, FIRST_NAMES) + " " + pick(random, LAST_NAMES),
                18 + random.nextInt(62),
                LocalDate.of(1950, 1, 1).plusDays(random.nextInt(20_000)),
                address(random),
                roles(random));
    }

    private static Address address(Random random) {
        String city = pick(random, CITIES);
        return new Address(pick(random, STREETS) + " " + (1 + random.nextInt(120)), city, "NO", Areas.postal(random, city));
    }

    private static List<String> roles(Random random) {
        List<String> roles = new ArrayList<>();
        for (String role : ROLES) {
            if (random.nextInt(3) == 0) {
                roles.add(role);
            }
        }
        return List.copyOf(roles);
    }

    private static String pick(Random random, List<String> values) {
        return values.get(random.nextInt(values.size()));
    }
}
