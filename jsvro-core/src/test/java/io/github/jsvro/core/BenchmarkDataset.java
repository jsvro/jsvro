package io.github.jsvro.core;

import io.github.jsvro.core.Areas.Area;
import io.github.jsvro.core.FxTransactions.FxTransactionResponse;
import io.github.jsvro.core.People.Person;
import io.github.jsvro.core.avro.AvroArea;
import io.github.jsvro.core.avro.AvroFxTransaction;
import io.github.jsvro.core.avro.AvroPerson;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.IntFunction;

final class BenchmarkDataset<T> {
    private final String name;
    private final String description;
    private final IntFunction<List<T>> generator;
    private final BenchmarkFormat<T> json;
    private final JsvroFormat<T> jsvro;
    private final BenchmarkFormat<T> avro;

    private BenchmarkDataset(String name, String description, Class<T> type, IntFunction<List<T>> generator,
            BenchmarkFormat<T> avro) {
        this.name = name;
        this.description = description;
        this.generator = generator;
        this.avro = avro;

        JsonMapper mapper = JsonMapper.builder().build();
        this.json = jsonFormat(mapper, type);
        JsvroCodec codec = new JsvroCodec(mapper);
        this.jsvro = new JsvroFormat<>(codec.writerFor(type), codec.readerFor(type));
    }

    static List<BenchmarkDataset<?>> all() {
        return List.of(
                new BenchmarkDataset<>("Person",
                        "Records: person → address → postal area (class) → boundary points",
                        Person.class, People::generate,
                        new AvroContainerFormat<>(AvroPerson.class, new AvroPerson(),
                                AvroMapping::toAvro, AvroMapping::fromAvro)),
                new BenchmarkDataset<>("Area",
                        "Plain classes with setters: area → boundary polygon of 4–16 points",
                        Area.class, Areas::generate,
                        new AvroContainerFormat<>(AvroArea.class, new AvroArea(),
                                AvroMapping::toAvro, AvroMapping::fromAvro)),
                new BenchmarkDataset<>("FxTransaction",
                        "A 36-field record: transaction → owner person → address → postal area → boundary points",
                        FxTransactionResponse.class, FxTransactions::generate,
                        new AvroContainerFormat<>(AvroFxTransaction.class, new AvroFxTransaction(),
                                AvroMapping::toAvro, AvroMapping::fromAvro)));
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    List<T> generate(int rows) {
        return generator.apply(rows);
    }

    List<BenchmarkFormat<T>> formats() {
        return List.of(json, jsvro, avro);
    }

    BenchmarkFormat<T> json() {
        return json;
    }

    BenchmarkFormat<T> jsvro() {
        return jsvro;
    }

    JsvroDecoding jsvroDecoding() {
        return jsvro.reader().decoding();
    }

    @Override
    public String toString() {
        return name;
    }

    private static <T> BenchmarkFormat<T> jsonFormat(JsonMapper mapper, Class<T> type) {
        JavaType listType = mapper.getTypeFactory().constructCollectionType(List.class, type);
        ObjectWriter writer = mapper.writerFor(listType);
        ObjectReader reader = mapper.readerFor(listType);
        return new BenchmarkFormat<>() {
            @Override
            public String key() {
                return "json";
            }

            @Override
            public void write(List<T> rows, OutputStream output) {
                writer.writeValue(output, rows);
            }

            @Override
            public List<T> read(byte[] encoded) {
                return reader.readValue(new ByteArrayInputStream(encoded));
            }
        };
    }

    private record JsvroFormat<T>(JsvroWriter<T> writer, JsvroReader<T> reader) implements BenchmarkFormat<T> {
        @Override
        public String key() {
            return "jsvro";
        }

        @Override
        public void write(List<T> rows, OutputStream output) {
            writer.write(output, rows);
        }

        @Override
        public List<T> read(byte[] encoded) {
            return reader.readList(new ByteArrayInputStream(encoded));
        }
    }
}
