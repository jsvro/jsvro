package io.github.jsvro.core.internal;

import io.github.jsvro.core.JsvroType;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonMapFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonNumberFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonValueFormat;

import java.time.temporal.Temporal;
import java.util.Calendar;
import java.util.Date;

final class FormatProbe extends JsonFormatVisitorWrapper.Base {
    private final Class<?> rawType;
    private JsvroType type;
    private JsvroType itemType;

    private FormatProbe(SerializationContext context, Class<?> rawType) {
        super(context);
        this.rawType = rawType;
    }

    static FormatProbe probe(JsonFormatVisitable serializer, JavaType javaType, SerializationContext context) {
        FormatProbe probe = new FormatProbe(context, javaType.getRawClass());
        serializer.acceptJsonFormatVisitor(probe, javaType);
        return probe;
    }

    JsvroType type() {
        return type;
    }

    JsvroType itemType() {
        return itemType;
    }

    @Override
    public JsonStringFormatVisitor expectStringFormat(JavaType javaType) {
        type = JsvroType.STRING;
        return new JsonStringFormatVisitor.Base() {
            @Override
            public void format(JsonValueFormat format) {
                type = switch (format) {
                    case DATE -> JsvroType.DATE;
                    case DATE_TIME -> isPointInTime() ? JsvroType.DATETIME : JsvroType.STRING;
                    case UUID -> JsvroType.UUID;
                    default -> JsvroType.STRING;
                };
            }
        };
    }

    private boolean isPointInTime() {
        return Temporal.class.isAssignableFrom(rawType)
                || Date.class.isAssignableFrom(rawType)
                || Calendar.class.isAssignableFrom(rawType);
    }

    @Override
    public JsonIntegerFormatVisitor expectIntegerFormat(JavaType javaType) {
        type = JsvroType.INTEGER;
        return null;
    }

    @Override
    public JsonNumberFormatVisitor expectNumberFormat(JavaType javaType) {
        type = JsvroType.NUMBER;
        return new JsonNumberFormatVisitor.Base() {
            @Override
            public void numberType(JsonParser.NumberType numberType) {
                if (numberType == JsonParser.NumberType.BIG_DECIMAL) {
                    type = JsvroType.DECIMAL;
                }
            }
        };
    }

    @Override
    public JsonBooleanFormatVisitor expectBooleanFormat(JavaType javaType) {
        type = JsvroType.BOOLEAN;
        return null;
    }

    @Override
    public JsonMapFormatVisitor expectMapFormat(JavaType javaType) {
        type = JsvroType.MAP;
        return null;
    }

    @Override
    public JsonArrayFormatVisitor expectArrayFormat(JavaType javaType) {
        type = JsvroType.ARRAY;
        return new JsonArrayFormatVisitor.Base(getContext()) {
            @Override
            public void itemsFormat(JsonFormatVisitable handler, JavaType elementType) {
                FormatProbe item = probe(handler, elementType, getContext());
                if (item.type() != JsvroType.ARRAY) {
                    itemType = item.type();
                }
            }

            @Override
            public void itemsFormat(JsonFormatTypes format) {
                itemType = switch (format) {
                    case STRING -> JsvroType.STRING;
                    case INTEGER -> JsvroType.INTEGER;
                    case NUMBER -> JsvroType.NUMBER;
                    case BOOLEAN -> JsvroType.BOOLEAN;
                    case OBJECT -> JsvroType.MAP;
                    default -> null;
                };
            }
        };
    }
}
