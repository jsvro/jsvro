package io.github.jsvro.core.internal;

import io.github.jsvro.core.JsvroColumn;
import io.github.jsvro.core.JsvroException;
import io.github.jsvro.core.JsvroType;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SchemaResolution {
    private SchemaResolution() {
    }

    public static void checkCompatible(List<JsvroColumn> expected, List<JsvroColumn> incoming) {
        checkColumns(expected, incoming, "columns");
    }

    private static void checkColumns(List<JsvroColumn> expected, List<JsvroColumn> incoming, String path) {
        Map<String, JsvroColumn> byName = expected.stream()
                .collect(Collectors.toMap(JsvroColumn::name, Function.identity()));
        for (int i = 0; i < incoming.size(); i++) {
            JsvroColumn column = incoming.get(i);
            JsvroColumn match = byName.get(column.name());
            if (match != null) {
                checkColumn(match, column, path + "[" + i + "]");
            }
        }
    }

    private static void checkColumn(JsvroColumn expected, JsvroColumn incoming, String path) {
        String expectedShape = shape(expected.type());
        String incomingShape = shape(incoming.type());
        if (!expectedShape.equals(incomingShape)) {
            throw new JsvroException("Schema conflict at " + path + " (\"" + incoming.name() + "\"): expected "
                    + expectedShape + " but got " + incomingShape);
        }
        if (expected.type() == JsvroType.OBJECT) {
            checkColumns(expected.columns(), incoming.columns(), path + ".columns");
        }
        else if (expected.type() == JsvroType.ARRAY) {
            checkColumn(expected.items(), incoming.items(), path + ".items");
        }
    }

    private static String shape(JsvroType type) {
        return switch (type) {
            case OBJECT -> "an object";
            case ARRAY -> "an array";
            case MAP -> "a map";
            default -> "a scalar";
        };
    }
}
