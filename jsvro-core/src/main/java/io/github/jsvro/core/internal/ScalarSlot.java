package io.github.jsvro.core.internal;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.impl.MethodProperty;
import tools.jackson.databind.deser.jdk.NumberDeserializers;
import tools.jackson.databind.deser.jdk.StringDeserializer;
import tools.jackson.databind.introspect.AnnotatedField;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.AnnotatedMethod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

abstract class ScalarSlot {
    final MethodHandle setter;

    private ScalarSlot(MethodHandle setter, Class<?> type) {
        this.setter = setter.asType(MethodType.methodType(void.class, Object.class, type));
    }

    abstract boolean readInto(JsonParser parser, Object bean) throws Throwable;

    static ScalarSlot of(SettableBeanProperty property) {
        if (!(property instanceof MethodProperty) || property.getValueTypeDeserializer() != null) {
            return null;
        }
        Class<?> type = property.getType().getRawClass();
        ValueDeserializer<Object> deserializer = property.getValueDeserializer();
        Class<?> expected = builtInDeserializer(type);
        if (expected == null || deserializer == null || deserializer.getClass() != expected) {
            return null;
        }
        MethodHandle setter = setter(property.getMember());
        if (setter == null) {
            return null;
        }
        if (type == String.class) {
            return new StringSlot(setter);
        }
        if (type == int.class) {
            return new IntSlot(setter);
        }
        if (type == long.class) {
            return new LongSlot(setter);
        }
        if (type == double.class) {
            return new DoubleSlot(setter);
        }
        return new BooleanSlot(setter);
    }

    private static Class<?> builtInDeserializer(Class<?> type) {
        if (type == String.class) {
            return StringDeserializer.class;
        }
        if (type == int.class) {
            return NumberDeserializers.IntegerDeserializer.class;
        }
        if (type == long.class) {
            return NumberDeserializers.LongDeserializer.class;
        }
        if (type == double.class) {
            return NumberDeserializers.DoubleDeserializer.class;
        }
        if (type == boolean.class) {
            return NumberDeserializers.BooleanDeserializer.class;
        }
        return null;
    }

    private static MethodHandle setter(AnnotatedMember member) {
        try {
            if (member instanceof AnnotatedMethod method && method.getParameterCount() == 1) {
                return MethodHandles.lookup().unreflect(method.getAnnotated());
            }
            if (member instanceof AnnotatedField annotatedField) {
                Field field = annotatedField.getAnnotated();
                if (!Modifier.isFinal(field.getModifiers())) {
                    return MethodHandles.lookup().unreflectSetter(field);
                }
            }
        }
        catch (IllegalAccessException ex) {
            return null;
        }
        return null;
    }

    private static final class StringSlot extends ScalarSlot {
        StringSlot(MethodHandle setter) {
            super(setter, String.class);
        }

        @Override
        boolean readInto(JsonParser parser, Object bean) throws Throwable {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return false;
            }
            setter.invokeExact(bean, parser.getString());
            return true;
        }
    }

    private static final class IntSlot extends ScalarSlot {
        IntSlot(MethodHandle setter) {
            super(setter, int.class);
        }

        @Override
        boolean readInto(JsonParser parser, Object bean) throws Throwable {
            if (!parser.isExpectedNumberIntToken()) {
                return false;
            }
            setter.invokeExact(bean, parser.getIntValue());
            return true;
        }
    }

    private static final class LongSlot extends ScalarSlot {
        LongSlot(MethodHandle setter) {
            super(setter, long.class);
        }

        @Override
        boolean readInto(JsonParser parser, Object bean) throws Throwable {
            if (!parser.isExpectedNumberIntToken()) {
                return false;
            }
            setter.invokeExact(bean, parser.getLongValue());
            return true;
        }
    }

    private static final class DoubleSlot extends ScalarSlot {
        DoubleSlot(MethodHandle setter) {
            super(setter, double.class);
        }

        @Override
        boolean readInto(JsonParser parser, Object bean) throws Throwable {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
                return false;
            }
            setter.invokeExact(bean, parser.getDoubleValue());
            return true;
        }
    }

    private static final class BooleanSlot extends ScalarSlot {
        BooleanSlot(MethodHandle setter) {
            super(setter, boolean.class);
        }

        @Override
        boolean readInto(JsonParser parser, Object bean) throws Throwable {
            JsonToken token = parser.currentToken();
            if (token != JsonToken.VALUE_TRUE && token != JsonToken.VALUE_FALSE) {
                return false;
            }
            setter.invokeExact(bean, token == JsonToken.VALUE_TRUE);
            return true;
        }
    }
}
