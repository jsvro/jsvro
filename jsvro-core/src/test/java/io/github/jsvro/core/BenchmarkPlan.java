package io.github.jsvro.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

final class BenchmarkPlan {
    private static final int MAX_ITERATIONS = 10_000;
    private static final int MAX_ROWS = 100_000;
    private static final String FULL_PLAN = "benchmark-full.properties";

    enum Mode { FULL, CUSTOM }

    private final Mode mode;
    private final Map<Integer, Integer> iterationsByRows;

    private BenchmarkPlan(Mode mode, Map<Integer, Integer> iterationsByRows) {
        if (iterationsByRows.isEmpty()) {
            throw new IllegalArgumentException("A benchmark plan needs at least one rows:iterations entry");
        }
        this.mode = mode;
        this.iterationsByRows = Map.copyOf(iterationsByRows);
    }

    static BenchmarkPlan full() {
        try (InputStream resource = BenchmarkPlan.class.getResourceAsStream("/" + FULL_PLAN)) {
            if (resource == null) {
                throw new IllegalStateException(FULL_PLAN + " is missing from the test resources");
            }
            Properties properties = new Properties();
            properties.load(resource);
            return fromProperties(properties, FULL_PLAN);
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static BenchmarkPlan fromProperties(String properties) {
        try (Reader reader = new StringReader(properties)) {
            Properties loaded = new Properties();
            loaded.load(reader);
            return fromProperties(loaded, "the full plan");
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static BenchmarkPlan fromProperties(Properties properties, String source) {
        Map<Integer, Integer> iterationsByRows = new TreeMap<>();
        for (String rows : properties.stringPropertyNames()) {
            String iterations = properties.getProperty(rows);
            add(iterationsByRows, rows, iterations, rows + "=" + iterations, source);
        }
        return new BenchmarkPlan(Mode.FULL, iterationsByRows);
    }

    static BenchmarkPlan explicit(String plan) {
        Map<Integer, Integer> iterationsByRows = new TreeMap<>();
        for (String entry : plan.split(",")) {
            String[] parts = entry.trim().split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Expected rows:iterations but got '" + entry.trim() + "'");
            }
            add(iterationsByRows, parts[0], parts[1], entry.trim(), "'" + plan + "'");
        }
        return new BenchmarkPlan(Mode.CUSTOM, iterationsByRows);
    }

    static BenchmarkPlan fromSystemProperties() {
        String plan = System.getProperty("jsvro.benchmark.plan");
        boolean full = Boolean.getBoolean("jsvro.benchmark.full");
        if (full && plan != null && !plan.isBlank()) {
            throw new IllegalArgumentException("Choose either a full run or rows:iterations pairs, not both");
        }
        if (full) {
            return full();
        }
        if (plan != null && !plan.isBlank()) {
            return explicit(plan);
        }
        throw new IllegalArgumentException(
                "Choose a run: ./benchmark -full, or ./benchmark -ri 10:1000 20:500 for rows:iterations pairs");
    }

    Mode mode() {
        return mode;
    }

    List<Integer> sizes() {
        return iterationsByRows.keySet().stream().sorted().toList();
    }

    int iterations(int rows) {
        return iterationsByRows.get(rows);
    }

    private static void add(Map<Integer, Integer> iterationsByRows, String rowsText, String iterationsText,
            String entry, String source) {
        int rows = positive(rowsText, "rows", entry);
        if (rows > MAX_ROWS) {
            throw new IllegalArgumentException(
                    "At most " + MAX_ROWS + " rows are allowed, but '" + entry + "' asks for " + rows);
        }
        int iterations = positive(iterationsText, "iterations", entry);
        if (iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException(
                    "At most " + MAX_ITERATIONS + " iterations are allowed, but '" + entry + "' asks for " + iterations);
        }
        if (iterationsByRows.put(rows, iterations) != null) {
            throw new IllegalArgumentException("Rows " + rows + " appear more than once in " + source);
        }
    }

    private static int positive(String value, String name, String entry) {
        try {
            int number = Integer.parseInt(value.trim());
            if (number > 0) {
                return number;
            }
        }
        catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("Expected a positive number of " + name + " in '" + entry + "'");
    }

    @Override
    public String toString() {
        return mode + " " + Arrays.toString(sizes().stream().map(rows -> rows + ":" + iterations(rows)).toArray());
    }
}
