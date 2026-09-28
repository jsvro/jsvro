package dev.jsvro.core.internal;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.introspect.AnnotatedField;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.jdk.BooleanSerializer;
import tools.jackson.databind.ser.jdk.NumberSerializers;
import tools.jackson.databind.ser.jdk.StringSerializer;
import tools.jackson.databind.ser.std.NullSerializer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

abstract class ScalarWriter {
    private static final Field SUPPRESSABLE_VALUE = writerField("_suppressableValue");
    private static final Field NULL_SERIALIZER = writerField("_nullSerializer");

    final MethodHandle getter;

    private ScalarWriter(MethodHandle getter, Class<?> type) {
        this.getter = getter.asType(MethodType.methodType(type, Object.class));
    }

    abstract void write(Object bean, JsonGenerator generator) throws Throwable;

    static ScalarWriter of(BeanPropertyWriter writer) {
        Class<?> type = writer.getType().getRawClass();
        if (writer.getTypeSerializer() != null || writer.getSerializationType() != null || suppresses(writer)
                || !type.isPrimitive() && !writesPlainNull(writer)) {
            return null;
        }
        ValueSerializer<Object> serializer = writer.getSerializer();
        Class<?> expected = standardSerializer(type);
        if (expected == null || serializer == null || serializer.getClass() != expected) {
            return null;
        }
        MethodHandle getter = getter(writer.getMember());
        if (getter == null) {
            return null;
        }
        if (type == String.class) {
            return new StringWriter(getter);
        }
        if (type == int.class) {
            return new IntWriter(getter);
        }
        if (type == long.class) {
            return new LongWriter(getter);
        }
        if (type == double.class) {
            return new DoubleWriter(getter);
        }
        return new BooleanWriter(getter);
    }

    private static Class<?> standardSerializer(Class<?> type) {
        if (type == String.class) {
            return StringSerializer.class;
        }
        if (type == int.class) {
            return NumberSerializers.IntegerSerializer.class;
        }
        if (type == long.class) {
            return NumberSerializers.LongSerializer.class;
        }
        if (type == double.class) {
            return NumberSerializers.DoubleSerializer.class;
        }
        if (type == boolean.class) {
            return BooleanSerializer.class;
        }
        return null;
    }

    private static boolean suppresses(BeanPropertyWriter writer) {
        if (SUPPRESSABLE_VALUE == null) {
            return true;
        }
        try {
            return SUPPRESSABLE_VALUE.get(writer) != null;
        }
        catch (IllegalAccessException ex) {
            return true;
        }
    }

    private static boolean writesPlainNull(BeanPropertyWriter writer) {
        if (!writer.hasNullSerializer()) {
            return true;
        }
        if (NULL_SERIALIZER == null) {
            return false;
        }
        try {
            Object nullSerializer = NULL_SERIALIZER.get(writer);
            return nullSerializer == null || nullSerializer.getClass() == NullSerializer.class;
        }
        catch (IllegalAccessException ex) {
            return false;
        }
    }

    private static Field writerField(String name) {
        try {
            Field field = BeanPropertyWriter.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
        catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static MethodHandle getter(AnnotatedMember member) {
        try {
            if (member instanceof AnnotatedMethod method && method.getParameterCount() == 0) {
                return MethodHandles.lookup().unreflect(method.getAnnotated());
            }
            if (member instanceof AnnotatedField field) {
                return MethodHandles.lookup().unreflectGetter(field.getAnnotated());
            }
        }
        catch (IllegalAccessException ex) {
            return null;
        }
        return null;
    }

    private static final class StringWriter extends ScalarWriter {
        StringWriter(MethodHandle getter) {
            super(getter, String.class);
        }

        @Override
        void write(Object bean, JsonGenerator generator) throws Throwable {
            String value = (String) getter.invokeExact(bean);
            if (value == null) {
                generator.writeNull();
            }
            else {
                generator.writeString(value);
            }
        }
    }

    private static final class IntWriter extends ScalarWriter {
        IntWriter(MethodHandle getter) {
            super(getter, int.class);
        }

        @Override
        void write(Object bean, JsonGenerator generator) throws Throwable {
            generator.writeNumber((int) getter.invokeExact(bean));
        }
    }

    private static final class LongWriter extends ScalarWriter {
        LongWriter(MethodHandle getter) {
            super(getter, long.class);
        }

        @Override
        void write(Object bean, JsonGenerator generator) throws Throwable {
            generator.writeNumber((long) getter.invokeExact(bean));
        }
    }

    private static final class DoubleWriter extends ScalarWriter {
        DoubleWriter(MethodHandle getter) {
            super(getter, double.class);
        }

        @Override
        void write(Object bean, JsonGenerator generator) throws Throwable {
            generator.writeNumber((double) getter.invokeExact(bean));
        }
    }

    private static final class BooleanWriter extends ScalarWriter {
        BooleanWriter(MethodHandle getter) {
            super(getter, boolean.class);
        }

        @Override
        void write(Object bean, JsonGenerator generator) throws Throwable {
            generator.writeBoolean((boolean) getter.invokeExact(bean));
        }
    }
}
