package io.github.jsvro.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class JsvroMvcIntegrationTest {
    private static final String PEOPLE_JSVRO = """
            {"jsvro":"1","columns":[{"name":"name","type":"string"},{"name":"age","type":"integer"}]}
            ["Alice",30]
            ["Bob",40]
            """;
    private static final String PEOPLE_JSON = "[{\"name\":\"Alice\",\"age\":30},{\"name\":\"Bob\",\"age\":40}]";

    public record Person(String name, int age) {}

    @SpringBootApplication
    @Import(PeopleController.class)
    static class TestApplication {
    }

    @RestController
    static class PeopleController {
        @GetMapping("/people")
        List<Person> people() {
            return List.of(new Person("Alice", 30), new Person("Bob", 40));
        }

        @GetMapping("/people/stream")
        Stream<Person> peopleStream() {
            return people().stream();
        }

        @GetMapping("/names")
        List<String> names() {
            return List.of("Alice", "Bob");
        }

        @PostMapping("/people/names")
        List<String> names(@RequestBody List<Person> people) {
            return people.stream().map(Person::name).toList();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ClientHttpMessageConvertersCustomizer jsvroClientHttpMessageConvertersCustomizer;

    @LocalServerPort
    private int port;

    @Test
    void servesJsvroWhenExplicitlyRequested() throws Exception {
        mvc.perform(get("/people").accept(JsvroMediaType.APPLICATION_JSVRO))
                .andExpect(status().isOk())
                .andExpect(content().contentType(JsvroMediaType.APPLICATION_JSVRO))
                .andExpect(content().string(PEOPLE_JSVRO));
    }

    @Test
    void servesJsvroForStreams() throws Exception {
        mvc.perform(get("/people/stream").accept(JsvroMediaType.APPLICATION_JSVRO))
                .andExpect(status().isOk())
                .andExpect(content().string(PEOPLE_JSVRO));
    }

    @Test
    void prefersJsvroWhenItHasTheHighestQuality() throws Exception {
        mvc.perform(get("/people").header(HttpHeaders.ACCEPT, "application/json;q=0.5, application/vnd.jsvro"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(JsvroMediaType.APPLICATION_JSVRO));
    }

    @Test
    void keepsJsonAsTheDefaultWithoutAnAcceptHeader() throws Exception {
        mvc.perform(get("/people"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(PEOPLE_JSON));
    }

    @Test
    void keepsJsonAsTheDefaultForWildcardAccept() throws Exception {
        mvc.perform(get("/people").accept(MediaType.ALL))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void servesJsonWhenJsonIsRequested() throws Exception {
        mvc.perform(get("/people").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void answersNotAcceptableForElementTypesJsvroCannotEncode() throws Exception {
        mvc.perform(get("/names").accept(JsvroMediaType.APPLICATION_JSVRO))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void readsJsvroRequestBodies() throws Exception {
        mvc.perform(post("/people/names")
                        .contentType(JsvroMediaType.APPLICATION_JSVRO)
                        .content(PEOPLE_JSVRO)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("[\"Alice\",\"Bob\"]"));
    }

    @Test
    void restClientReadsAndWritesJsvro() {
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .configureMessageConverters(converters -> {
                    converters.registerDefaults();
                    jsvroClientHttpMessageConvertersCustomizer.customize(converters);
                })
                .build();

        List<Person> people = client.get().uri("/people")
                .accept(JsvroMediaType.APPLICATION_JSVRO)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertEquals(List.of(new Person("Alice", 30), new Person("Bob", 40)), people);

        List<String> names = client.post().uri("/people/names")
                .contentType(JsvroMediaType.APPLICATION_JSVRO)
                .accept(MediaType.APPLICATION_JSON)
                .body(people, new ParameterizedTypeReference<List<Person>>() {})
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertEquals(List.of("Alice", "Bob"), names);
    }
}
