package io.github.jsvro.core;

import java.util.List;
import java.util.Objects;

/** A single column in a JSVRO schema. Object columns contain nested columns; array columns contain an item schema. */
public record JsvroColumn(
        String name,
        JsvroType type,
        List<JsvroColumn> columns,
        JsvroColumn items) {

    public JsvroColumn {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    public static JsvroColumn scalar(String name, JsvroType type) {
        return new JsvroColumn(name, type, List.of(), null);
    }

    public static JsvroColumn object(String name, List<JsvroColumn> columns) {
        return new JsvroColumn(name, JsvroType.OBJECT, columns, null);
    }

    public static JsvroColumn array(String name, JsvroColumn items) {
        return new JsvroColumn(name, JsvroType.ARRAY, List.of(), Objects.requireNonNull(items, "items"));
    }
}
