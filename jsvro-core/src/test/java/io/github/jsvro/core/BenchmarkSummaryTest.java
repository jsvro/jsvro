package io.github.jsvro.core;

import io.github.jsvro.core.JsvroBenchmarkTest.Cost;
import io.github.jsvro.core.JsvroBenchmarkTest.Measurement;
import io.github.jsvro.core.JsvroBenchmarkTest.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkSummaryTest {

    @Test
    void advantageIsTheGeometricMeanOfBaselineOverJsvro() {
        List<Result> results = List.of(result("Person", 100, 200, 100), result("Area", 100, 100, 200));

        assertEquals(1.0, BenchmarkSummary.advantage(results, "json", Measurement::bytes), 1e-9);
    }

    @Test
    void advantageFavoursJsvroAboveOne() {
        List<Result> results = List.of(result("Person", 100, 400, 100), result("Area", 100, 100, 100));

        assertEquals(2.0, BenchmarkSummary.advantage(results, "json", Measurement::bytes), 1e-9);
    }

    @Test
    void markdownHasOneTableRowPerSummarySizeForEachBaseline() {
        List<Result> results = List.of(
                result("Person", 10, 200, 100), result("Person", 50, 200, 100), result("Person", 100, 200, 100));

        String markdown = BenchmarkSummary.markdown(results, "2026-09-25T10:00:00Z", "21", 8);

        assertTrue(markdown.contains("**JSVRO vs plain Jackson JSON**"), markdown);
        assertTrue(markdown.contains("**JSVRO vs Avro (container file, generated classes)**"), markdown);
        assertEquals(2, markdown.lines().filter(line -> line.startsWith("| 10 |")).count(), markdown);
        assertEquals(2, markdown.lines().filter(line -> line.startsWith("| 100 |")).count(), markdown);
        assertEquals(0, markdown.lines().filter(line -> line.startsWith("| 50 |")).count(), markdown);
        assertTrue(markdown.contains("| 10 | 2.00× |"), markdown);
    }

    private static Result result(String dataset, int rows, long baselineBytes, long jsvroBytes) {
        Cost cost = new Cost(1, 1, 1);
        return new Result(dataset, rows, 1, Map.of(
                "json", new Measurement(baselineBytes, 1, cost, cost),
                "avro", new Measurement(baselineBytes, 1, cost, cost),
                "jsvro", new Measurement(jsvroBytes, 1, cost, cost)));
    }
}
