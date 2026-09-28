# JSVRO

Fast, compact, JSON-like serialization format.

> **Status:** experimental `0.1.0-SNAPSHOT`. The v1 wire contract is documented in [SPEC.md](SPEC.md).

**JSON Schema + Values, Row-Oriented**: a compact, streaming, human-readable wire format for JVM/Spring applications.

The JSVRO header is its own small wire schema; it is **not** the JSON Schema specification.

JSVRO writes the object shape once and then sends positional JSON rows. It keeps the useful properties of JSON — readable values, native JSON scalars, easy `curl` debugging — while avoiding repeated field names.

```java
public record Lecturer(String name, String email) {}
public record Course(String code, String title, int credits, Lecturer lecturer) {}
```

becomes:

```json
{"jsvro":"1","columns":[{"name":"code","type":"string"},{"name":"title","type":"string"},{"name":"credits","type":"integer"},{"name":"lecturer","type":"object","columns":[{"name":"name","type":"string"},{"name":"email","type":"string"}]}]}
["CS2010","Algorithms and Data Structures",10,["Ingrid Lund","ingrid.lund@example.edu"]]
["CS2300","Database Systems",10,["Magnus Berg","magnus.berg@example.edu"]]
```

## Goals

- compact without becoming binary
- fast streaming writes with no per-row tree model
- one schema record followed by positional rows
- recursive objects and arrays
- normal JSON values, including `null`
- Jackson's property model is the source of truth
- no annotations required for ordinary Java records/beans
- drop-in Spring MVC content negotiation
- Java 21 bytecode, usable from Java 21 through Java 25+ JVMs

JSVRO deliberately does **not** use dictionary IDs, null bitmaps, delta encoding, or binary packing. Those techniques save more bytes but make terminal inspection substantially worse; if that level of optimization is needed, use a binary schema format such as Avro instead.

## Performance at a glance

Compared with plain Jackson JSON, from the [published benchmark run](#benchmark) of 28 September 2026 on the author's development machine:

| JSVRO vs JSON | 10–50 rows | 100+ rows |
|---|---|---|
| Payload size | 38% smaller | 43% smaller |
| Encode time | 21% less (1.26× faster) | 21% less (1.26× faster) |
| Encode CPU | 19% less | 21% less |
| Encode memory | 17% less | 17% less |
| Decode time | 32% less (1.48× faster) | 33% less (1.50× faster) |
| Decode CPU | 33% less | 33% less |
| Decode memory | 31% less | 32% less |

For the paged lists of 10–50 rows most APIs return, JSVRO sends 38% less data, encodes 1.26× and decodes 1.48× as fast, and uses about a fifth less CPU and memory to encode and a third less to decode. Each column averages the row counts in its range, combined over the `Person`, `Area` and `FxTransaction` aggregate roots. Gains depend on the shape of your objects and your machine; see [Benchmark](#benchmark) for the disclaimer, the comparison with Avro and the full report.

## Modules

- `jsvro-core` — schema derivation, cached codecs, streaming encoder and decoder
- `jsvro-spring-boot-starter` — Spring Boot 4 auto-configuration and an `HttpMessageConverter` for Spring MVC and `RestClient`
- `jsvro-example` — a small runnable Spring Boot app: an `EnrolledCourseController` serving courses, lecturers and enrolled students, with no JSVRO-specific controller code

## Spring Boot usage

Add the starter:

```kotlin
dependencies {
    implementation("dev.jsvro:jsvro-spring-boot-starter:0.1.0")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
}
```

Controller code stays normal. From the example app:

```java
@GetMapping("/enrolled-courses")
List<EnrolledCourse> enrolledCourses() {
    return ...;
}
```

Normal JSON remains the default:

```bash
curl http://localhost:8080/enrolled-courses
```

Request JSVRO explicitly:

```bash
curl -H 'Accept: application/vnd.jsvro' http://localhost:8080/enrolled-courses
```

No `@JsonProperty`, JSVRO wrapper, schema declaration, or controller-specific serialization code is required. Controllers may return a `List`, any other `Iterable`, a `Stream` or an array; request bodies are read as `List<T>`.

To try it, run the example with `./gradlew :jsvro-example:bootRun` and use the `curl` commands above.

The starter also provides a `ClientHttpMessageConvertersCustomizer`, so a `RestClient` configured with it can send and receive JSVRO.

Disable auto-configuration if needed:

```properties
jsvro.enabled=false
```

## Using the codec directly

`JsvroCodec` wraps your Jackson `ObjectMapper`. `writerFor` and `readerFor` derive and cache the schema once; reuse the returned writer and reader for every stream:

```java
JsvroCodec codec = new JsvroCodec(jsonMapper);

JsvroWriter<Course> writer = codec.writerFor(Course.class);
writer.write(outputStream, courses);

JsvroReader<Course> reader = codec.readerFor(Course.class);
List<Course> all = reader.readList(inputStream);

try (Stream<Course> rows = reader.readStream(inputStream)) {
    rows.forEach(this::process);
}
```

`readStream` decodes one row at a time as the stream is consumed. Input and output streams stay owned by the caller and are never closed by the codec.

## Wire format v1

A stream consists of independent JSON values separated by newlines.

The first value is the schema:

```json
{"jsvro":"1","columns":[...]}
```

Every following value is one row. Object fields are represented positionally in schema order:

```json
["Nordic University of Technology","NO"]
```

Nested objects are recursively positional:

```json
["CS2010","Algorithms and Data Structures",10,["Ingrid Lund","ingrid.lund@example.edu"]]
```

Arrays remain arrays. Arrays of objects use the nested object item's positional schema. Maps retain ordinary JSON object form because map keys are data rather than statically-known field names.

### Types

JSVRO v1 uses a small language-neutral schema vocabulary:

`string`, `integer`, `number`, `decimal`, `boolean`, `date`, `datetime`, `uuid`, `binary`, `object`, `array`, `map`.

Java implementation class names are never written to the wire.

### Schema evolution

Readers match columns to their own schema by name, so writer and reader can evolve separately: a stream decodes to the same result as the equivalent JSON objects would. Columns the reader does not know are handled like unknown JSON properties (ignored under Jackson 3's defaults), and missing columns like absent ones. Only a structural conflict, such as an object where the reader expects a list, rejects the stream. The rules are in [SPEC.md](SPEC.md#schema-resolution).

## Jackson behavior

JSVRO derives properties through Jackson serialization introspection, rather than `Class.getRecordComponents()`. This means it follows the configured Jackson property names, ordering and ignored properties. Plain records require no annotations:

```java
public record Lecturer(String name, String email) {}
```

Schema/codec derivation is cached by Jackson `JavaType`; it is not repeated per row. The hot encoding path writes directly to Jackson's `JsonGenerator` and does not construct `JsonNode` trees.

The decoder validates the incoming schema header, then reads every row through a single Jackson reader, binding positional values straight to the target type without building a tree. Records and `@JsonCreator` types are constructed through a cached constructor handle, falling back to Jackson's own construction if that fails. Types whose nested columns differ between positions are decoded by buffering each row as named tokens instead; `JsvroReader.decoding()` reports which path a type uses.

## Current constraints

- root rows must be object/record-like types, and a row must not be `null`
- a row whose runtime class is a subclass of the declared element type is rejected, because its extra properties have no column
- recursive/cyclic object schemas are rejected in v1
- polymorphic, abstract, `@JsonUnwrapped`, `@JsonAnyGetter` and untyped (`Object`) properties are rejected when the schema is derived
- Spring request-body decoding currently targets `List<T>`
- Spring response encoding supports `Iterable<T>`, `Stream<T>`, and arrays
- custom Jackson serializers must describe their JSON shape through `acceptJsonFormatVisitor`, otherwise the schema cannot be derived

## Build

The project targets Java 21 bytecode. It can be consumed by Java 21, 22, 23, 24, 25 and newer compatible JVMs.

```bash
./gradlew build
```

The Gradle wrapper pins Gradle 9.3.0. A GitHub Actions workflow runs the build on pushes to `main` and on pull requests.

## Benchmark

JSVRO is compared with plain Jackson JSON, the readable format it replaces, and with Apache Avro, the binary format to reach for when readability does not matter. Three aggregate roots are measured at row counts from 10 to 100,000: `Person` (records with a nested postal area), `Area` (plain classes with boundary polygons) and `FxTransaction` (a 36-field record nesting a person, address and postal area).

**[Open the full interactive report](https://steinard.github.io/jsvro/benchmark/)** for wire size, time, CPU and memory per aggregate root and row count, compared with JSON and with Avro on separate pages.

> **Disclaimer:** these numbers were collected by running the benchmark on the author's development machine. Results vary with hardware, JVM, load and data shape, so treat them as an indication of gains and losses, not as guarantees. Run the benchmark on your own machine and data before deciding.

<!-- benchmark-summary:start -->
Measured 2026-09-28T10:12:55Z on Java 21.0.6+7-LTS with 11 CPUs. Each value is the geometric mean over the Person, Area and FxTransaction aggregate roots of baseline ÷ JSVRO: above 1.00× favours JSVRO, below 1.00× favours the baseline.

**JSVRO vs plain Jackson JSON**

| Rows | Raw size | Gzip size | Encode time | Encode memory | Decode time | Decode memory |
|---:|---:|---:|---:|---:|---:|---:|
| 10 | 1.49× | 1.02× | 1.24× | 1.20× | 1.47× | 1.41× |
| 100 | 1.73× | 1.08× | 1.28× | 1.21× | 1.49× | 1.48× |
| 1,000 | 1.76× | 1.10× | 1.28× | 1.21× | 1.50× | 1.48× |
| 10,000 | 1.76× | 1.10× | 1.24× | 1.21× | 1.50× | 1.48× |
| 100,000 | 1.76× | 1.10× | 1.30× | 1.21× | 1.52× | 1.48× |

**JSVRO vs Avro (container file, generated classes)**

| Rows | Raw size | Gzip size | Encode time | Encode memory | Decode time | Decode memory |
|---:|---:|---:|---:|---:|---:|---:|
| 10 | 0.91× | 1.40× | 0.84× | 4.45× | 1.80× | 4.49× |
| 100 | 0.72× | 1.32× | 0.55× | 0.94× | 0.56× | 1.88× |
| 1,000 | 0.70× | 1.30× | 0.54× | 0.56× | 0.47× | 1.52× |
| 10,000 | 0.70× | 1.30× | 0.56× | 0.53× | 0.46× | 1.43× |
| 100,000 | 0.70× | 1.30× | 0.59× | 0.52× | 0.46× | 1.42× |
<!-- benchmark-summary:end -->

How to read it: against JSON, JSVRO is smaller, faster and uses less memory at every size, most of all when decoding. Against Avro, JSVRO is about 40% bigger on the wire and takes roughly 1.8× as long to encode and just over 2× as long to decode once a stream passes about 100 rows; it is smaller after gzip, allocates less memory when decoding, and for 10–50 rows decodes as fast as Avro, where Avro's container header dominates. If you want payloads a person can read with `curl` and debug in a terminal, JSVRO keeps that at a lower cost than JSON. If readability does not matter and throughput does, choose Avro.

Avro is measured as an object container file without compression, using classes generated from Avro schemas, with the mapping between the application objects and the generated classes included in encode and decode.

### Running the benchmark

```bash
./benchmark -full                  # full run, as set in benchmark-full.properties
./benchmark -ri 10:1000 20:500     # custom run: rows:iterations pairs
./benchmark -publish               # publish the last full run
```

A custom run takes any `rows:iterations` pairs, up to 100,000 rows and 10,000 iterations each. The full run's row counts and iterations are set in `jsvro-core/src/test/resources/benchmark-full.properties`, one `rows=iterations` line each, within the same limits. Each measurement runs the same number of warm-up iterations first. Results print to the terminal and are written to `jsvro-core/build/reports/jsvro-benchmark/index.html`. The normal `./gradlew build` does not run the benchmark.

Only a full run can be published. `./benchmark -publish` copies the report to `docs/benchmark/index.html`, which GitHub Pages serves, and refreshes the summary tables above; after a custom run it refuses and asks for a full run.

## Why not just gzip JSON?

You often should enable HTTP compression as well. Repeated JSON field names compress efficiently, but JSVRO still avoids generating, parsing and matching those names in the first place. Treat gzip/zstd as a transport optimization and JSVRO as a representation optimization. Benchmark both wire size and CPU for your workload; the benchmark above is a starting point.

## License

Apache License 2.0.
