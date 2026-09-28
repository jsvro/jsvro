package io.github.jsvro.core;

import java.util.List;
import java.util.Objects;

/** The schema record written once at the start of a JSVRO stream. */
public record JsvroSchema(String jsvro, List<JsvroColumn> columns) {
    public static final String CURRENT_VERSION = "1";

    public JsvroSchema {
        Objects.requireNonNull(jsvro, "jsvro");
        columns = List.copyOf(columns);
    }

    public JsvroSchema(List<JsvroColumn> columns) {
        this(CURRENT_VERSION, columns);
    }
}
