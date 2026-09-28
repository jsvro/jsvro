package io.github.jsvro.core;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.std.StdSerializer;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsvroSchemaRejectionTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsvroCodec codec = new JsvroCodec(mapper);

    public abstract static class Animal {
        public String name = "rex";
    }

    public static class Dog extends Animal {
        public int legs = 4;
    }

    public interface Shape {
        double area();
    }

    record AbstractProperty(Animal animal) {}

    record InterfaceProperty(Shape shape) {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes(@JsonSubTypes.Type(value = Circle.class, name = "circle"))
    public sealed interface Polymorphic permits Circle {}

    public record Circle(double radius) implements Polymorphic {}

    record PolymorphicProperty(Polymorphic value) {}

    record Recursive(String name, List<Recursive> children) {}

    record Inner(String city) {}

    record Unwrapped(@JsonUnwrapped Inner inner) {}

    public static class AnyGetter {
        public String name = "x";

        @JsonAnyGetter
        public Map<String, Object> extra() {
            return Map.of();
        }
    }

    record ObjectProperty(Object value) {}

    record AsArrayProperty(@JsonFormat(shape = JsonFormat.Shape.ARRAY) Inner inner) {}

    public static class Opaque {
    }

    static final class OpaqueSerializer extends StdSerializer<Opaque> {
        OpaqueSerializer() {
            super(Opaque.class);
        }

        @Override
        public void serialize(Opaque value, JsonGenerator generator, SerializationContext context) {
            generator.writeString("opaque");
        }
    }

    record CustomSerialized(@JsonSerialize(using = OpaqueSerializer.class) Opaque value) {}

    public static class Base {
        public String name = "base";
    }

    public static class Derived extends Base {
        public String extra = "lost";
    }

    record Person(String name) {}

    @Test
    void rejectsAbstractClassProperties() {
        assertRejected(AbstractProperty.class, "abstract");
    }

    @Test
    void rejectsInterfaceProperties() {
        assertRejected(InterfaceProperty.class, "Shape");
    }

    @Test
    void rejectsPolymorphicProperties() {
        assertRejected(PolymorphicProperty.class, "polymorphic");
    }

    @Test
    void rejectsRecursiveTypes() {
        assertRejected(Recursive.class, "recursive");
    }

    @Test
    void rejectsUnwrappedProperties() {
        assertRejected(Unwrapped.class, "@JsonUnwrapped");
    }

    @Test
    void rejectsAnyGetters() {
        assertRejected(AnyGetter.class, "@JsonAnyGetter");
    }

    @Test
    void rejectsUntypedObjectProperties() {
        assertRejected(ObjectProperty.class, "java.lang.Object");
    }

    @Test
    void rejectsNonObjectBeanShapes() {
        assertRejected(AsArrayProperty.class, "shape");
    }

    @Test
    void rejectsSerializersThatDoNotDescribeTheirShape() {
        assertRejected(CustomSerialized.class, "acceptJsonFormatVisitor");
    }

    @Test
    void rejectsNonObjectRootRows() {
        assertRejected(String.class, "root row type must be an object");
    }

    @Test
    void supportsReportsWhetherASchemaCanBeDerived() {
        assertTrue(codec.supports(mapper.constructType(Person.class)));
        assertFalse(codec.supports(mapper.constructType(String.class)));
        assertFalse(codec.supports(mapper.constructType(Recursive.class)));
    }

    @Test
    void rejectsRuntimeSubtypesWhoseExtraPropertiesWouldBeDropped() {
        var failure = assertThrows(JsvroException.class,
                () -> codec.write(new ByteArrayOutputStream(), Base.class, List.of(new Derived())));

        assertTrue(failure.getMessage().contains(Derived.class.getName()), failure.getMessage());
    }

    @Test
    void rejectsNullRows() {
        var failure = assertThrows(JsvroException.class,
                () -> codec.write(new ByteArrayOutputStream(), Person.class, Arrays.asList(new Person("a"), null)));

        assertEquals("Row 1 is null; JSVRO rows must be objects", failure.getMessage());
    }

    private void assertRejected(Class<?> type, String expectedMessagePart) {
        var failure = assertThrows(JsvroException.class, () -> codec.schema(type));
        assertTrue(failure.getMessage().contains(expectedMessagePart), failure.getMessage());
    }
}
