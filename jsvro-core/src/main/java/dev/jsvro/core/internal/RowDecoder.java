package dev.jsvro.core.internal;

import dev.jsvro.core.JsvroColumn;
import dev.jsvro.core.JsvroException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.util.TokenBuffer;

import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;

public final class RowDecoder {
    private final ObjectReader reader;
    private final boolean positional;
    private final List<JsvroColumn> columns;
    private final Map<Class<?>, Construction> constructions;

    RowDecoder(ObjectReader reader, boolean positional, List<JsvroColumn> columns,
            Map<Class<?>, Construction> constructions) {
        this.reader = reader;
        this.positional = positional;
        this.columns = columns;
        this.constructions = constructions;
    }

    public boolean isPositional() {
        return positional;
    }

    public Construction construction(Class<?> type) {
        return constructions.get(type);
    }

    public JsonParser createParser(InputStream input) {
        return reader.createParser(input);
    }

    public Iterator<Object> rows(JsonParser parser) {
        parser.clearCurrentToken();
        return positional ? new PositionalRows(parser, reader.readValues(parser)) : new BufferedRows(parser);
    }

    public Object read(JsonParser parser, long index) {
        String path = "row " + index;
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw notARow(index, parser.currentToken());
        }
        if (positional) {
            try {
                return reader.readValue(parser);
            }
            catch (PositionalCountException ex) {
                throw new JsvroException(ex.messageAt(path + describe(ex.getPath())), ex);
            }
        }
        TokenBuffer named = TokenBuffer.forBuffering(parser, parser.objectReadContext());
        object(parser, columns, named, path);
        try (JsonParser namedParser = named.asParser(parser.objectReadContext())) {
            return reader.readValue(namedParser);
        }
    }

    private static JsvroException notARow(long index, JsonToken token) {
        return new JsvroException("Expected row " + index + " to be a JSON array but got " + token);
    }

    private final class PositionalRows implements Iterator<Object> {
        private final JsonParser parser;
        private final MappingIterator<Object> values;
        private long index;
        private boolean ready;

        private PositionalRows(JsonParser parser, MappingIterator<Object> values) {
            this.parser = parser;
            this.values = values;
        }

        @Override
        public boolean hasNext() {
            if (ready) {
                return true;
            }
            if (!values.hasNextValue()) {
                return false;
            }
            if (parser.currentToken() != JsonToken.START_ARRAY) {
                throw notARow(index, parser.currentToken());
            }
            ready = true;
            return true;
        }

        @Override
        public Object next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            ready = false;
            long row = index++;
            try {
                return values.nextValue();
            }
            catch (PositionalCountException ex) {
                throw new JsvroException(ex.messageAt("row " + row + describe(ex.getPath())), ex);
            }
        }
    }

    private final class BufferedRows implements Iterator<Object> {
        private final JsonParser parser;
        private long index;
        private boolean ready;

        private BufferedRows(JsonParser parser) {
            this.parser = parser;
        }

        @Override
        public boolean hasNext() {
            if (!ready) {
                ready = parser.nextToken() != null;
            }
            return ready;
        }

        @Override
        public Object next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            ready = false;
            return read(parser, index++);
        }
    }

    private static String describe(List<JacksonException.Reference> references) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : references) {
            if (reference.getPropertyName() != null) {
                path.append('.').append(reference.getPropertyName());
            }
            else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static void value(JsonParser parser, JsvroColumn column, TokenBuffer out, String path) {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            out.writeNull();
            return;
        }
        switch (column.type()) {
            case OBJECT -> object(parser, column.columns(), out, path);
            case ARRAY -> array(parser, column.items(), out, path);
            default -> out.copyCurrentStructure(parser);
        }
    }

    private static void object(JsonParser parser, List<JsvroColumn> columns, TokenBuffer out, String path) {
        expectArray(parser, path);
        out.writeStartObject();
        for (int i = 0; i < columns.size(); i++) {
            JsonToken token = parser.nextToken();
            if (token == JsonToken.END_ARRAY || token == null) {
                throw new JsvroException("Expected " + columns.size() + " positional values at " + path + " but got " + i);
            }
            JsvroColumn column = columns.get(i);
            out.writeName(column.name());
            value(parser, column, out, path + "." + column.name());
        }
        if (parser.nextToken() != JsonToken.END_ARRAY) {
            throw new JsvroException("Expected " + columns.size() + " positional values at " + path + " but got more");
        }
        out.writeEndObject();
    }

    private static void array(JsonParser parser, JsvroColumn items, TokenBuffer out, String path) {
        expectArray(parser, path);
        out.writeStartArray();
        int index = 0;
        for (JsonToken token = parser.nextToken(); token != JsonToken.END_ARRAY; token = parser.nextToken()) {
            if (token == null) {
                throw new JsvroException("Unterminated array at " + path);
            }
            value(parser, items, out, path + "[" + index++ + "]");
        }
        out.writeEndArray();
    }

    private static void expectArray(JsonParser parser, String path) {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new JsvroException("Expected a positional array at " + path + " but got " + parser.currentToken());
        }
    }
}
