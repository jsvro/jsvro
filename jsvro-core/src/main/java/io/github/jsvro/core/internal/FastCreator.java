package io.github.jsvro.core.internal;

import tools.jackson.databind.introspect.AnnotatedConstructor;
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.databind.introspect.AnnotatedWithParams;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;

final class FastCreator {
    private static final MethodType SPREAD = MethodType.methodType(Object.class, Object[].class);

    private final MethodHandle creator;
    private final int parameterCount;

    private FastCreator(MethodHandle creator, int parameterCount) {
        this.creator = creator;
        this.parameterCount = parameterCount;
    }

    static FastCreator of(AnnotatedWithParams annotated) {
        try {
            MethodHandle handle;
            if (annotated instanceof AnnotatedConstructor constructor) {
                handle = MethodHandles.lookup().unreflectConstructor(constructor.getAnnotated());
            }
            else if (annotated instanceof AnnotatedMethod method && Modifier.isStatic(method.getModifiers())) {
                handle = MethodHandles.lookup().unreflect(method.getAnnotated());
            }
            else {
                return null;
            }
            if (handle.isVarargsCollector()) {
                handle = handle.asFixedArity();
            }
            int count = handle.type().parameterCount();
            MethodHandle spread = handle.asType(handle.type().generic())
                    .asSpreader(Object[].class, count)
                    .asType(SPREAD);
            return new FastCreator(spread, count);
        }
        catch (IllegalAccessException | RuntimeException ex) {
            return null;
        }
    }

    int parameterCount() {
        return parameterCount;
    }

    Object create(Object[] arguments) throws Throwable {
        return (Object) creator.invokeExact(arguments);
    }
}
