package io.github.jsvro.core.internal;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanAsArrayDeserializer;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.deser.bean.PropertyBasedCreator;
import tools.jackson.databind.deser.bean.PropertyValueBuffer;
import tools.jackson.databind.introspect.AnnotatedWithParams;

import java.util.BitSet;
import java.util.List;

final class PositionalBeanDeserializer extends BeanAsArrayDeserializer {
    private final DecodingReport report;
    private final boolean setterPath;
    private final ScalarSlot[] scalarSlots;
    private final FastCreator fastCreator;
    private final SettableBeanProperty[] creatorProperties;
    private final int[] argumentIndexes;
    private volatile boolean fastEnabled;
    private final String[] unknownColumns;

    PositionalBeanDeserializer(BeanDeserializerBase named, List<String> columns, List<String> knownColumns,
            DecodingReport report) {
        super(named, columns.stream()
                .map(column -> named.findProperty(PropertyName.construct(column)))
                .toArray(SettableBeanProperty[]::new));
        this.report = report;
        this.unknownColumns = unknownColumns(columns, knownColumns);
        this.setterPath = _propertyBasedCreator == null && !_nonStandardCreation && _injectables == null
                && _objectIdReader == null;
        this.scalarSlots = scalarSlots(_orderedProperties);
        this.creatorProperties = new SettableBeanProperty[_orderedProperties.length];
        this.argumentIndexes = new int[_orderedProperties.length];
        this.fastCreator = fastCreator();
        this.fastEnabled = fastCreator != null;
        report.construction(_beanType.getRawClass(), fastEnabled ? Construction.FAST : Construction.JACKSON);
    }

    private String[] unknownColumns(List<String> columns, List<String> knownColumns) {
        String[] unknown = new String[columns.size()];
        if (knownColumns != null) {
            for (int i = 0; i < unknown.length; i++) {
                if (_orderedProperties[i] == null && !knownColumns.contains(columns.get(i))) {
                    unknown[i] = columns.get(i);
                }
            }
        }
        return unknown;
    }

    private void skipColumn(JsonParser parser, DeserializationContext context, int index, Object bean) {
        String unknown = unknownColumns[index];
        if (unknown == null) {
            parser.skipChildren();
        }
        else if (bean != null) {
            handleUnknownVanilla(parser, context, bean, unknown);
        }
        else {
            handleUnknownProperty(parser, context, _beanType.getRawClass(), unknown);
        }
    }

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context) {
        if (_needViewProcesing && context.getActiveView() != null) {
            return super.deserialize(parser, context);
        }
        if (fastCreator != null) {
            return deserializeWithFastCreator(parser, context);
        }
        if (_propertyBasedCreator != null) {
            return deserializeWithCreator(parser, context);
        }
        if (setterPath) {
            return deserializeWithSetters(parser, context);
        }
        return super.deserialize(parser, context);
    }

    private ScalarSlot[] scalarSlots(SettableBeanProperty[] properties) {
        ScalarSlot[] slots = new ScalarSlot[properties.length];
        int count = 0;
        if (setterPath) {
            for (int i = 0; i < properties.length; i++) {
                if (properties[i] != null && (slots[i] = ScalarSlot.of(properties[i])) != null) {
                    count++;
                }
            }
        }
        report.scalarSlots(_beanType.getRawClass(), count);
        return slots;
    }

    private FastCreator fastCreator() {
        if (_propertyBasedCreator == null || _injectables != null || _objectIdReader != null) {
            return null;
        }
        AnnotatedWithParams creator = _valueInstantiator.getWithArgsCreator();
        if (creator == null) {
            return null;
        }
        BitSet covered = new BitSet();
        for (int i = 0; i < _orderedProperties.length; i++) {
            SettableBeanProperty property = _orderedProperties[i];
            if (property == null) {
                argumentIndexes[i] = -1;
                continue;
            }
            SettableBeanProperty creatorProperty = _propertyBasedCreator.findCreatorProperty(property.getName());
            if (creatorProperty == null
                    || creatorProperty.isInjectionOnly()
                    || creatorProperty.getInjectableValueId() != null
                    || covered.get(creatorProperty.getCreatorIndex())) {
                return null;
            }
            covered.set(creatorProperty.getCreatorIndex());
            creatorProperties[i] = creatorProperty;
            argumentIndexes[i] = creatorProperty.getCreatorIndex();
        }
        FastCreator fast = FastCreator.of(creator);
        if (fast == null || covered.cardinality() != fast.parameterCount()
                || covered.nextClearBit(0) != fast.parameterCount()) {
            return null;
        }
        return fast;
    }

    private Object deserializeWithFastCreator(JsonParser parser, DeserializationContext context) {
        SettableBeanProperty[] properties = _orderedProperties;
        Object[] arguments = new Object[fastCreator.parameterCount()];
        for (int i = 0; i < properties.length; i++) {
            if (parser.nextToken() == JsonToken.END_ARRAY) {
                throw new PositionalCountException(parser, properties.length, String.valueOf(i));
            }
            SettableBeanProperty property = creatorProperties[i];
            if (property == null) {
                skipColumn(parser, context, i, null);
                continue;
            }
            try {
                arguments[argumentIndexes[i]] = property.deserialize(parser, context);
            }
            catch (Exception ex) {
                throw wrapAndThrow(ex, _beanType.getRawClass(), property.getName(), context);
            }
        }
        expectEnd(parser, properties.length);

        if (fastEnabled && !context.isEnabled(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)) {
            try {
                return fastCreator.create(arguments);
            }
            catch (Throwable fastFailure) {
                Object bean = createWithJackson(arguments, parser, context);
                fastEnabled = false;
                report.construction(_beanType.getRawClass(), Construction.JACKSON);
                return bean;
            }
        }
        return createWithJackson(arguments, parser, context);
    }

    private Object createWithJackson(Object[] arguments, JsonParser parser, DeserializationContext context) {
        PropertyValueBuffer buffer = _propertyBasedCreator.startBuilding(parser, context, _objectIdReader);
        for (int i = 0; i < creatorProperties.length; i++) {
            if (creatorProperties[i] != null) {
                buffer.assignParameter(creatorProperties[i], arguments[argumentIndexes[i]]);
            }
        }
        try {
            return _propertyBasedCreator.build(context, buffer);
        }
        catch (Exception ex) {
            return wrapInstantiationProblem(context, ex);
        }
    }

    private Object deserializeWithSetters(JsonParser parser, DeserializationContext context) {
        Object bean = _valueInstantiator.createUsingDefault(context);
        parser.assignCurrentValue(bean);

        SettableBeanProperty[] properties = _orderedProperties;
        for (int i = 0; i < properties.length; i++) {
            if (parser.nextToken() == JsonToken.END_ARRAY) {
                throw new PositionalCountException(parser, properties.length, String.valueOf(i));
            }
            SettableBeanProperty property = properties[i];
            if (property == null) {
                skipColumn(parser, context, i, bean);
                continue;
            }
            try {
                ScalarSlot slot = scalarSlots[i];
                if (slot == null || !slot.readInto(parser, bean)) {
                    property.deserializeAndSet(parser, context, bean);
                }
            }
            catch (Throwable ex) {
                throw wrapAndThrow(ex, bean, property.getName(), context);
            }
        }
        expectEnd(parser, properties.length);
        return bean;
    }

    private Object deserializeWithCreator(JsonParser parser, DeserializationContext context) {
        PropertyBasedCreator creator = _propertyBasedCreator;
        PropertyValueBuffer buffer = creator.startBuilding(parser, context, _objectIdReader);
        Object bean = null;

        SettableBeanProperty[] properties = _orderedProperties;
        for (int i = 0; i < properties.length; i++) {
            if (parser.nextToken() == JsonToken.END_ARRAY) {
                throw new PositionalCountException(parser, properties.length, String.valueOf(i));
            }
            SettableBeanProperty property = properties[i];
            if (property == null) {
                skipColumn(parser, context, i, bean);
                continue;
            }
            String name = property.getName();
            try {
                if (bean != null) {
                    property.deserializeAndSet(parser, context, bean);
                    continue;
                }
                SettableBeanProperty creatorProperty = creator.findCreatorProperty(name);
                if (creatorProperty == null) {
                    buffer.bufferProperty(property, property.deserialize(parser, context));
                }
                else if (creatorProperty.isInjectionOnly()) {
                    parser.skipChildren();
                }
                else if (buffer.assignParameter(creatorProperty, creatorProperty.deserialize(parser, context))) {
                    bean = creator.build(context, buffer);
                    parser.assignCurrentValue(bean);
                }
            }
            catch (Exception ex) {
                throw wrapAndThrow(ex, bean != null ? bean : _beanType.getRawClass(), name, context);
            }
        }
        expectEnd(parser, properties.length);

        if (bean == null) {
            try {
                bean = creator.build(context, buffer);
            }
            catch (Exception ex) {
                return wrapInstantiationProblem(context, ex);
            }
        }
        return bean;
    }

    private static void expectEnd(JsonParser parser, int expected) {
        if (parser.nextToken() != JsonToken.END_ARRAY) {
            throw new PositionalCountException(parser, expected, "more");
        }
    }
}
