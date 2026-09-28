package io.github.jsvro.spring;

import io.github.jsvro.core.JsvroCodec;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsvroHttpMessageConverterTest {
    record Person(String name, int age) {}

    static class ControllerShape {
        List<Person> persons() { return List.of(); }

        List<String> names() { return List.of(); }

        Person[] personArray() { return new Person[0]; }
    }

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsvroHttpMessageConverter converter = new JsvroHttpMessageConverter(mapper, new JsvroCodec(mapper));

    @Test
    void onlyClaimsTheJsvroMediaType() throws Exception {
        Type returnType = returnType("persons");

        assertTrue(converter.canWrite(returnType, List.class, JsvroMediaType.APPLICATION_JSVRO));
        assertFalse(converter.canWrite(returnType, List.class, MediaType.APPLICATION_JSON));
        assertFalse(converter.canWrite(returnType, List.class, MediaType.ALL));
        assertFalse(converter.canWrite(returnType, List.class, MediaType.parseMediaType("application/*")));
    }

    @Test
    void reportsItselfProducibleWhenSpringProbesWithoutAMediaType() throws Exception {
        assertTrue(converter.canWrite(returnType("persons"), List.class, null));
        assertTrue(converter.canWrite(returnType("personArray"), Person[].class, null));
    }

    @Test
    void doesNotClaimElementTypesItCannotEncode() throws Exception {
        Type names = returnType("names");

        assertFalse(converter.canWrite(names, List.class, null));
        assertFalse(converter.canWrite(names, List.class, JsvroMediaType.APPLICATION_JSVRO));
        assertFalse(converter.canRead(names, null, JsvroMediaType.APPLICATION_JSVRO));
    }

    @Test
    void readsOnlyListTargets() throws Exception {
        assertTrue(converter.canRead(returnType("persons"), null, JsvroMediaType.APPLICATION_JSVRO));
        assertFalse(converter.canRead(returnType("personArray"), null, JsvroMediaType.APPLICATION_JSVRO));
        assertFalse(converter.canRead(returnType("persons"), null, MediaType.APPLICATION_JSON));
    }

    private static Type returnType(String methodName) throws NoSuchMethodException {
        Method method = ControllerShape.class.getDeclaredMethod(methodName);
        return method.getGenericReturnType();
    }
}
