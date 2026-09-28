package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroColumn;
import dev.jsvro.core.JsvroException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.BeanPropertyWriter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class ObjectCodec implements ValueCodec {
    private final Class<?> type;
    private final Property[] properties;

    ObjectCodec(Class<?> type, List<Property> properties) {
        this.type = type;
        this.properties = properties.toArray(Property[]::new);
    }

    @Override
    public JsvroColumn column(String name) {
        return JsvroColumn.object(name, Arrays.stream(properties).map(Property::column).toList());
    }

    @Override
    public boolean collectObjectTypes(Map<Class<?>, List<String>> columnsByType) {
        List<String> columns = Arrays.stream(properties).map(property -> property.writer().getName()).toList();
        List<String> existing = columnsByType.putIfAbsent(type, columns);
        if (existing != null && !existing.equals(columns)) {
            return false;
        }
        for (Property property : properties) {
            if (!property.codec().collectObjectTypes(columnsByType)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean collectIncomingTypes(JsvroColumn incoming, Map<Class<?>, List<String>> columnsByType) {
        List<String> columns = incoming.columns().stream().map(JsvroColumn::name).toList();
        List<String> existing = columnsByType.putIfAbsent(type, columns);
        if (existing != null && !existing.equals(columns)) {
            return false;
        }
        for (JsvroColumn column : incoming.columns()) {
            for (Property property : properties) {
                if (property.writer().getName().equals(column.name())
                        && !property.codec().collectIncomingTypes(column, columnsByType)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void write(Object value, JsonGenerator generator, SerializationContext context) {
        if (value == null) {
            generator.writeNull();
            return;
        }
        if (value.getClass() != type) {
            throw new JsvroException("JSVRO schema was derived from " + type.getName()
                    + " but the value is a " + value.getClass().getName()
                    + "; subtype properties cannot be represented positionally");
        }

        generator.writeStartArray(value, properties.length);
        for (Property property : properties) {
            property.write(value, generator, context);
        }
        generator.writeEndArray();
    }

    int scalarWriters() {
        return (int) Arrays.stream(properties).filter(property -> property.scalar() != null).count();
    }

    record Property(BeanPropertyWriter writer, ValueCodec codec, ScalarWriter scalar) {
        Property(BeanPropertyWriter writer, ValueCodec codec) {
            this(writer, codec, codec instanceof LeafCodec ? ScalarWriter.of(writer) : null);
        }

        JsvroColumn column() {
            return codec.column(writer.getName());
        }

        void write(Object bean, JsonGenerator generator, SerializationContext context) {
            try {
                if (scalar != null) {
                    scalar.write(bean, generator);
                }
                else if (codec instanceof LeafCodec) {
                    writer.serializeAsElement(bean, generator, context);
                }
                else {
                    codec.write(writer.get(bean), generator, context);
                }
            }
            catch (JacksonException | JsvroException | Error ex) {
                throw ex;
            }
            catch (Throwable ex) {
                throw new JsvroException("Could not write property '" + writer.getName() + "'", ex);
            }
        }
    }
}
