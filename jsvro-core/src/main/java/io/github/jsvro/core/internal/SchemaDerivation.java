package io.github.jsvro.core.internal;

import io.github.jsvro.core.JsvroColumn;
import io.github.jsvro.core.JsvroException;
import io.github.jsvro.core.JsvroType;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.BeanSerializer;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.ser.UnrolledBeanSerializer;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.databind.ser.jdk.ByteArraySerializer;
import tools.jackson.databind.ser.std.StdContainerSerializer;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

final class SchemaDerivation {
    private final SerializationContext context;
    private final Set<JavaType> path = new HashSet<>();

    SchemaDerivation(SerializationContext context) {
        this.context = context;
    }

    ObjectCodec root(JavaType type) {
        ValueCodec codec = build(type, context.findRootValueSerializer(type), null);
        if (codec instanceof ObjectCodec object) {
            return object;
        }
        throw new JsvroException("JSVRO root row type must be an object, but " + type
                + " is written as " + codec.column("root").type().wireName());
    }

    private ValueCodec build(JavaType type, ValueSerializer<?> knownSerializer, BeanPropertyWriter property) {
        if (context.findTypeSerializer(type) != null) {
            throw unsupported(type, "it is polymorphic (@JsonTypeInfo or default typing)");
        }
        ValueSerializer<Object> serializer = serializer(type, knownSerializer, property);

        if (type.isReferenceType()) {
            return reference(type, serializer, property);
        }
        if ((ValueSerializer<?>) serializer instanceof ByteArraySerializer) {
            return new LeafCodec(JsvroType.BINARY, null, serializer);
        }
        if (type.isMapLikeType()) {
            return new LeafCodec(JsvroType.MAP, null, serializer);
        }
        if (type.isContainerType() && serializer instanceof StdContainerSerializer<?> container) {
            return container(type, container, serializer);
        }
        if (serializer instanceof BeanSerializer || serializer instanceof UnrolledBeanSerializer) {
            return object(type, (BeanSerializerBase) serializer);
        }
        if (serializer instanceof BeanSerializerBase) {
            throw unsupported(type, "its Jackson shape is not a plain object (e.g. @JsonFormat(shape = ARRAY) or @JsonUnwrapped)");
        }
        return leaf(type, serializer);
    }

    @SuppressWarnings("unchecked")
    private ValueSerializer<Object> serializer(JavaType type, ValueSerializer<?> known, BeanPropertyWriter property) {
        if (known != null) {
            return (ValueSerializer<Object>) known;
        }
        return property != null
                ? context.findPrimaryPropertySerializer(type, property)
                : context.findValueSerializer(type);
    }

    private ValueCodec reference(JavaType type, ValueSerializer<Object> serializer, BeanPropertyWriter property) {
        ValueCodec content = build(type.getReferencedType(), null, property);
        if (content instanceof LeafCodec leaf) {
            return leaf.withSerializer(serializer);
        }
        ValueCodec reference = ReferenceCodec.forType(type.getRawClass(), content);
        if (reference == null) {
            throw unsupported(type, "only Optional and AtomicReference may wrap nested objects");
        }
        return reference;
    }

    private ValueCodec container(JavaType type, StdContainerSerializer<?> container, ValueSerializer<Object> serializer) {
        ValueCodec item = build(type.getContentType(), container.getContentSerializer(), null);
        if (item instanceof LeafCodec) {
            return new LeafCodec(JsvroType.ARRAY, item.column("item"), serializer);
        }
        return new ArrayCodec(item);
    }

    private ValueCodec object(JavaType type, BeanSerializerBase serializer) {
        Class<?> raw = type.getRawClass();
        if (raw.isInterface() || Modifier.isAbstract(raw.getModifiers())) {
            throw unsupported(type, "it is abstract; its runtime properties are unknown");
        }
        if (serializer.usesObjectId()) {
            throw unsupported(type, "it uses @JsonIdentityInfo");
        }
        if (serializer.getFilterId() != null) {
            throw unsupported(type, "it uses @JsonFilter");
        }
        BeanDescription description = context.introspectBeanDescription(
                type, context.getConfig().classIntrospectorInstance().introspectClassAnnotations(type));
        if (description.findAnyGetter() != null) {
            throw unsupported(type, "it uses @JsonAnyGetter");
        }
        if (!path.add(type)) {
            throw unsupported(type, "it is recursive");
        }

        try {
            List<ObjectCodec.Property> properties = new ArrayList<>();
            for (Iterator<PropertyWriter> it = serializer.properties(); it.hasNext(); ) {
                properties.add(property(type, it.next()));
            }
            if (properties.isEmpty()) {
                throw unsupported(type, "it has no serializable properties");
            }
            return new ObjectCodec(raw, properties);
        }
        finally {
            path.remove(type);
        }
    }

    private ObjectCodec.Property property(JavaType owner, PropertyWriter propertyWriter) {
        if (!(propertyWriter instanceof BeanPropertyWriter writer)) {
            throw unsupported(owner, "property '" + propertyWriter.getName() + "' is not a regular bean property");
        }
        if (writer.isUnwrapping()) {
            throw unsupported(owner, "property '" + writer.getName() + "' uses @JsonUnwrapped");
        }
        if (writer.getTypeSerializer() != null) {
            throw unsupported(owner, "property '" + writer.getName() + "' is polymorphic");
        }
        JavaType type = writer.getSerializationType() != null ? writer.getSerializationType() : writer.getType();
        return new ObjectCodec.Property(writer, build(type, writer.getSerializer(), writer));
    }

    private LeafCodec leaf(JavaType type, ValueSerializer<Object> serializer) {
        FormatProbe probe = FormatProbe.probe(serializer, type, context);
        JsvroType jsvroType = probe.type();
        if (jsvroType == null && isOpenType(type.getRawClass())) {
            throw unsupported(type, "it is abstract or untyped; the runtime shape of its values is unknown");
        }
        if (jsvroType == null) {
            throw unsupported(type, "its serializer " + serializer.getClass().getName()
                    + " does not describe its JSON shape (implement acceptJsonFormatVisitor)");
        }
        if (jsvroType != JsvroType.ARRAY) {
            return new LeafCodec(jsvroType, null, serializer);
        }
        if (probe.itemType() == null) {
            throw unsupported(type, "its serializer writes an array with undescribed items");
        }
        return new LeafCodec(JsvroType.ARRAY, JsvroColumn.scalar("item", probe.itemType()), serializer);
    }

    private static boolean isOpenType(Class<?> raw) {
        return raw == Object.class || raw.isInterface() || Modifier.isAbstract(raw.getModifiers());
    }

    private static JsvroException unsupported(JavaType type, String reason) {
        return new JsvroException("Cannot derive a JSVRO schema for " + type.toCanonical() + ": " + reason);
    }
}
