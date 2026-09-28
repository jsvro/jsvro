package dev.jsvro.core.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class DecodingReport {
    private final Map<Class<?>, Construction> constructions = new ConcurrentHashMap<>();
    private final Map<Class<?>, Integer> scalarSlots = new ConcurrentHashMap<>();

    void construction(Class<?> type, Construction construction) {
        constructions.put(type, construction);
    }

    Construction construction(Class<?> type) {
        return constructions.get(type);
    }

    void scalarSlots(Class<?> type, int count) {
        scalarSlots.put(type, count);
    }

    int scalarSlots(Class<?> type) {
        return scalarSlots.getOrDefault(type, 0);
    }
}
