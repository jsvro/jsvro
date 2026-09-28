package io.github.jsvro.core.internal;

import io.github.jsvro.core.JsvroColumn;
import io.github.jsvro.core.JsvroSchema;
import tools.jackson.core.JsonGenerator;

public final class SchemaWriter {
    private SchemaWriter() {
    }

    public static void write(JsonGenerator generator, JsvroSchema schema) {
        generator.writeStartObject();
        generator.writeName("jsvro");
        generator.writeString(schema.jsvro());
        generator.writeName("columns");
        generator.writeStartArray();
        for (JsvroColumn column : schema.columns()) {
            writeColumn(generator, column, false);
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private static void writeColumn(JsonGenerator generator, JsvroColumn column, boolean item) {
        generator.writeStartObject();
        if (!item) {
            generator.writeName("name");
            generator.writeString(column.name());
        }
        generator.writeName("type");
        generator.writeString(column.type().wireName());

        if (!column.columns().isEmpty()) {
            generator.writeName("columns");
            generator.writeStartArray();
            for (JsvroColumn nested : column.columns()) {
                writeColumn(generator, nested, false);
            }
            generator.writeEndArray();
        }

        if (column.items() != null) {
            generator.writeName("items");
            writeColumn(generator, column.items(), true);
        }

        generator.writeEndObject();
    }
}
