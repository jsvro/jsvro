package io.github.jsvro.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkDatasetTest {

    static List<BenchmarkDataset<?>> datasets() {
        return BenchmarkDataset.all();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("datasets")
    void everyFormatRoundTripsTheSameRows(BenchmarkDataset<?> dataset) {
        assertRoundTrips(dataset);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("datasets")
    void jsvroIsSmallerThanJsonAndDecodesPositionally(BenchmarkDataset<?> dataset) {
        assertSmallerThanJsonAndPositional(dataset);
    }

    private static <T> void assertRoundTrips(BenchmarkDataset<T> dataset) {
        List<T> rows = dataset.generate(500);

        for (BenchmarkFormat<T> format : dataset.formats()) {
            assertEquals(rows, format.read(format.encode(rows)), format.key());
        }
    }

    private static <T> void assertSmallerThanJsonAndPositional(BenchmarkDataset<T> dataset) {
        List<T> rows = dataset.generate(500);

        byte[] json = dataset.json().encode(rows);
        byte[] jsvro = dataset.jsvro().encode(rows);

        assertTrue(jsvro.length < json.length, "jsvro=" + jsvro.length + " json=" + json.length);
        assertEquals(JsvroDecoding.POSITIONAL, dataset.jsvroDecoding());
    }
}
