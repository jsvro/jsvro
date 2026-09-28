package io.github.jsvro.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("benchmark")
class JsvroBenchmarkTest {
    private static final BenchmarkPlan PLAN = BenchmarkPlan.fromSystemProperties();

    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final com.sun.management.OperatingSystemMXBean OS =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    private final List<Result> results = new ArrayList<>();
    private long sink;

    @Test
    void compareJsvroWithJsonAndAvroAcrossScales() throws IOException {
        assertTrue(THREADS.isThreadAllocatedMemorySupported() && THREADS.isThreadAllocatedMemoryEnabled(),
                "per-thread allocation measurement is required");

        System.out.println("Plan (rows:iterations): " + PLAN);
        List<BenchmarkDataset<?>> datasets = BenchmarkDataset.all();
        for (BenchmarkDataset<?> dataset : datasets) {
            measure(dataset);
        }
        System.out.println("(% saved = 1 - jsvro / baseline; positive favours jsvro; sink=" + sink + ")");
        System.out.println("Report: " + writeReport(datasets).toUri());
    }

    private <T> void measure(BenchmarkDataset<T> dataset) {
        System.out.printf("%n%s: %% saved by JSVRO%n", dataset.name());
        System.out.printf("%9s %6s | %-27s | %-27s%n", "", "", "vs JSON", "vs Avro");
        System.out.printf("%9s %6s | %6s %6s %6s %6s | %6s %6s %6s %6s%n",
                "rows", "iters", "size", "gzip", "enc", "dec", "size", "gzip", "enc", "dec");

        for (int size : PLAN.sizes()) {
            List<T> rows = dataset.generate(size);
            int iterations = PLAN.iterations(size);

            Map<String, Measurement> formats = new LinkedHashMap<>();
            for (BenchmarkFormat<T> format : dataset.formats()) {
                byte[] encoded = format.encode(rows);
                formats.put(format.key(), new Measurement(encoded.length, gzipLength(encoded),
                        cost(iterations, () -> count(format, rows)),
                        cost(iterations, () -> format.read(encoded))));
            }
            results.add(new Result(dataset.name(), size, iterations, formats));

            Measurement jsvro = formats.get("jsvro");
            System.out.printf("%9d %6d | %s | %s%n", size, iterations,
                    savedColumns(formats.get("json"), jsvro), savedColumns(formats.get("avro"), jsvro));
        }
    }

    private static String savedColumns(Measurement baseline, Measurement jsvro) {
        return String.format("%5.0f%% %5.0f%% %5.0f%% %5.0f%%",
                saved(baseline.bytes(), jsvro.bytes()), saved(baseline.gzipBytes(), jsvro.gzipBytes()),
                saved(baseline.encode().wallNanos(), jsvro.encode().wallNanos()),
                saved(baseline.decode().wallNanos(), jsvro.decode().wallNanos()));
    }

    private static double saved(long baseline, long jsvro) {
        return 100.0 * (1 - (double) jsvro / baseline);
    }

    private Cost cost(int iterations, Supplier<?> operation) {
        for (int i = 0; i < iterations; i++) {
            consume(operation.get());
        }
        long[] wall = new long[iterations];
        long[] allocated = new long[iterations];
        long cpuBefore = OS.getProcessCpuTime();
        for (int i = 0; i < iterations; i++) {
            long allocatedBefore = THREADS.getCurrentThreadAllocatedBytes();
            long start = System.nanoTime();
            Object result = operation.get();
            wall[i] = System.nanoTime() - start;
            allocated[i] = THREADS.getCurrentThreadAllocatedBytes() - allocatedBefore;
            consume(result);
        }
        long cpuPerOperation = (OS.getProcessCpuTime() - cpuBefore) / iterations;
        return new Cost(median(wall), cpuPerOperation, median(allocated));
    }

    private static <T> long count(BenchmarkFormat<T> format, List<T> rows) {
        CountingOutputStream output = new CountingOutputStream();
        format.write(rows, output);
        return output.count;
    }

    private static long median(long[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    private void consume(Object result) {
        sink += result instanceof Long count ? count : ((List<?>) result).size();
    }

    private Path writeReport(List<BenchmarkDataset<?>> datasets) throws IOException {
        String template;
        try (InputStream resource = getClass().getResourceAsStream("/benchmark-report.html")) {
            template = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        String generatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        String javaVersion = Runtime.version().toString();
        int cpuCount = Runtime.getRuntime().availableProcessors();
        Map<String, Object> data = Map.of(
                "generatedAt", generatedAt,
                "javaVersion", javaVersion,
                "cpuCount", cpuCount,
                "mode", PLAN.mode().name().toLowerCase(),
                "datasets", datasets.stream()
                        .map(dataset -> Map.of("name", dataset.name(), "description", dataset.description()))
                        .toList(),
                "results", results);
        String json = JsonMapper.builder().build().writeValueAsString(data).replace("</", "<\\/");

        Path report = Path.of(System.getProperty("jsvro.benchmark.report", "build/reports/jsvro-benchmark/index.html"));
        Files.createDirectories(report.getParent());
        Files.writeString(report, template.replace("/*BENCHMARK_DATA*/null", json));
        Files.writeString(report.resolveSibling("run-mode.txt"), PLAN.mode().name().toLowerCase());
        Files.writeString(report.resolveSibling("summary.md"),
                BenchmarkSummary.markdown(results, generatedAt, javaVersion, cpuCount));
        return report.toAbsolutePath();
    }

    private static long gzipLength(byte[] input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(input);
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return output.size();
    }

    private static final class CountingOutputStream extends OutputStream {
        private long count;

        @Override
        public void write(int b) {
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            count += length;
        }
    }

    public record Cost(long wallNanos, long cpuNanos, long allocatedBytes) {}

    public record Measurement(long bytes, long gzipBytes, Cost encode, Cost decode) {}

    public record Result(String dataset, int rows, int iterations, Map<String, Measurement> formats) {}
}
