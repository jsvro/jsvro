package io.github.jsvro.core;

import io.github.jsvro.core.JsvroBenchmarkTest.Measurement;
import io.github.jsvro.core.JsvroBenchmarkTest.Result;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToLongFunction;

final class BenchmarkSummary {
    private static final Set<Integer> SUMMARY_ROWS = Set.of(10, 100, 1_000, 10_000, 100_000);

    private static final List<Column> COLUMNS = List.of(
            new Column("Raw size", Measurement::bytes),
            new Column("Gzip size", Measurement::gzipBytes),
            new Column("Encode time", m -> m.encode().wallNanos()),
            new Column("Encode memory", m -> m.encode().allocatedBytes()),
            new Column("Decode time", m -> m.decode().wallNanos()),
            new Column("Decode memory", m -> m.decode().allocatedBytes()));

    private BenchmarkSummary() {
    }

    static String markdown(List<Result> results, String generatedAt, String javaVersion, int cpuCount) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("Measured ").append(generatedAt).append(" on Java ").append(javaVersion)
                .append(" with ").append(cpuCount).append(" CPUs. ")
                .append("Each value is the geometric mean over the Person, Area and FxTransaction aggregate roots of ")
                .append("baseline ÷ JSVRO: above 1.00× favours JSVRO, below 1.00× favours the baseline.\n\n");
        table(markdown, "JSVRO vs plain Jackson JSON", "json", results);
        markdown.append('\n');
        table(markdown, "JSVRO vs Avro (container file, generated classes)", "avro", results);
        return markdown.toString();
    }

    static double advantage(List<Result> resultsAtOneSize, String baseline, ToLongFunction<Measurement> value) {
        double logSum = 0;
        for (Result result : resultsAtOneSize) {
            double baselineValue = value.applyAsLong(result.formats().get(baseline));
            double jsvroValue = value.applyAsLong(result.formats().get("jsvro"));
            logSum += Math.log(baselineValue / jsvroValue);
        }
        return Math.exp(logSum / resultsAtOneSize.size());
    }

    private static void table(StringBuilder markdown, String title, String baseline, List<Result> results) {
        markdown.append("**").append(title).append("**\n\n| Rows |");
        COLUMNS.forEach(column -> markdown.append(' ').append(column.title()).append(" |"));
        markdown.append("\n|---:|");
        COLUMNS.forEach(column -> markdown.append("---:|"));
        markdown.append('\n');

        results.stream().map(Result::rows).distinct().sorted().filter(SUMMARY_ROWS::contains).forEach(rows -> {
            List<Result> atSize = results.stream().filter(result -> result.rows() == rows).toList();
            markdown.append("| ").append(String.format(Locale.ROOT, "%,d", rows)).append(" |");
            for (Column column : COLUMNS) {
                markdown.append(String.format(Locale.ROOT, " %.2f× |", advantage(atSize, baseline, column.value())));
            }
            markdown.append('\n');
        });
    }

    private record Column(String title, ToLongFunction<Measurement> value) {}
}
