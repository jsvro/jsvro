package io.github.jsvro.core;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsAppend = "-Dorg.apache.avro.SERIALIZABLE_PACKAGES=io.github.jsvro.core.avro")
public class DecodeBenchmark {
    @Param({"Person", "Area", "FxTransaction"})
    public String dataset;

    @Param({"100", "1000", "3000"})
    public int rows;

    @Param({"same", "evolved"})
    public String schema;

    private BenchmarkFormat<?> jsvro;
    private byte[] encoded;

    @Setup
    public void prepare() {
        BenchmarkDataset<?> selected = BenchmarkDataset.all().stream()
                .filter(candidate -> candidate.name().equals(dataset))
                .findFirst()
                .orElseThrow();
        prepare(selected);
    }

    private <T> void prepare(BenchmarkDataset<T> selected) {
        BenchmarkFormat<T> format = selected.jsvro();
        encoded = format.encode(selected.generate(rows));
        if (schema.equals("evolved")) {
            encoded = withLeadingColumn(encoded);
        }
        jsvro = format;
    }

    private static byte[] withLeadingColumn(byte[] stream) {
        String[] lines = new String(stream, StandardCharsets.UTF_8).split("\n");
        StringBuilder evolved = new StringBuilder(lines[0].replaceFirst("\"columns\":\\[",
                "\"columns\":[{\"name\":\"addedByWriter\",\"type\":\"string\"},")).append('\n');
        for (int i = 1; i < lines.length; i++) {
            evolved.append("[\"added\",").append(lines[i], 1, lines[i].length()).append('\n');
        }
        return evolved.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Benchmark
    public List<?> decodeJsvro() {
        return jsvro.read(encoded);
    }
}
