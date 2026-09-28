package io.github.jsvro.core;

/** Types exposed by the JSVRO wire schema. */
public enum JsvroType {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    DECIMAL("decimal"),
    BOOLEAN("boolean"),
    DATE("date"),
    DATETIME("datetime"),
    UUID("uuid"),
    BINARY("binary"),
    OBJECT("object"),
    ARRAY("array"),
    MAP("map");

    private final String wireName;

    JsvroType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
