package dev.jsvro.core.internal;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.deser.bean.BeanDeserializer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class PositionalDeserializerModifier extends ValueDeserializerModifier {
    private final Map<Class<?>, List<String>> columnsByType;
    private final Map<Class<?>, List<String>> knownColumnsByType;
    private final Set<Class<?>> wrapped = ConcurrentHashMap.newKeySet();
    private final Set<Class<?>> unsupported = ConcurrentHashMap.newKeySet();
    private final DecodingReport report = new DecodingReport();

    PositionalDeserializerModifier(Map<Class<?>, List<String>> columnsByType) {
        this(columnsByType, Map.of());
    }

    PositionalDeserializerModifier(Map<Class<?>, List<String>> columnsByType,
            Map<Class<?>, List<String>> knownColumnsByType) {
        this.columnsByType = Map.copyOf(columnsByType);
        this.knownColumnsByType = Map.copyOf(knownColumnsByType);
    }

    Set<Class<?>> positionalTypes() {
        return columnsByType.keySet();
    }

    DecodingReport report() {
        return report;
    }

    boolean allTypesSupported() {
        return unsupported.isEmpty() && wrapped.containsAll(columnsByType.keySet());
    }

    @Override
    public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config,
            BeanDescription.Supplier beanDescription, ValueDeserializer<?> deserializer) {
        List<String> columns = columnsByType.get(beanDescription.getBeanClass());
        if (columns == null) {
            return deserializer;
        }
        List<String> knownColumns = knownColumnsByType.get(beanDescription.getBeanClass());
        boolean unknownColumns = knownColumns != null && !knownColumns.containsAll(columns);
        if (deserializer instanceof BeanDeserializer
                && !(unknownColumns && beanDescription.get().findAnySetterAccessor() != null)) {
            wrapped.add(beanDescription.getBeanClass());
            return new PositionalOrNamedDeserializer(deserializer, columns, knownColumns, report);
        }
        unsupported.add(beanDescription.getBeanClass());
        return deserializer;
    }
}
