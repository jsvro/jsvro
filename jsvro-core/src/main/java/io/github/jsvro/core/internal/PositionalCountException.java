package io.github.jsvro.core.internal;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;

final class PositionalCountException extends DatabindException {
    private final int expected;
    private final String actual;

    PositionalCountException(JsonParser parser, int expected, String actual) {
        super(parser, "Expected " + expected + " positional values but got " + actual);
        this.expected = expected;
        this.actual = actual;
    }

    String messageAt(String path) {
        return "Expected " + expected + " positional values at " + path + " but got " + actual;
    }
}
