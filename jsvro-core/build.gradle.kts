import org.apache.avro.Schema
import org.apache.avro.compiler.specific.SpecificCompiler
import org.apache.avro.generic.GenericData

buildscript {
    dependencies {
        classpath(libs.avro.compiler)
    }
}

plugins {
    `java-library`
    alias(libs.plugins.maven.publish)
}

description = "JSVRO: a compact, row-oriented positional serialization format built on Jackson 3."

abstract class GenerateAvro : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemas: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun generate() {
        val destination = output.get().asFile
        destination.deleteRecursively()
        destination.mkdirs()
        schemas.asFileTree.matching { include("**/*.avsc") }.files.sorted().forEach { file ->
            val compiler = SpecificCompiler(Schema.Parser().parse(file))
            compiler.setStringType(GenericData.StringType.String)
            compiler.setEnableDecimalLogicalType(true)
            compiler.compileToDestination(file, destination)
        }
    }
}

val generateTestAvro by tasks.registering(GenerateAvro::class) {
    description = "Generates Java classes from the Avro schemas used by the benchmark."
    schemas = layout.projectDirectory.dir("src/test/avro")
    output = layout.buildDirectory.dir("generated/sources/avro/test/java")
}

sourceSets.test {
    java.srcDir(generateTestAvro)
}

dependencies {
    api(libs.jackson.databind)

    testImplementation(libs.avro)
    testImplementation(libs.junit.jupiter)
    testImplementation("org.junit.jupiter:junit-jupiter-params")
}

tasks.withType<Test>().configureEach {
    systemProperty("org.apache.avro.SERIALIZABLE_PACKAGES", "io.github.jsvro.core.avro")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("benchmark")
    }
}

tasks.register<Test>("benchmark") {
    description = "Compares JSVRO with plain Jackson JSON and Avro for size, time, CPU and memory."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("benchmark")
    }
    maxHeapSize = "6g"
    listOf("plan", "full").forEach { name ->
        providers.gradleProperty("benchmark.$name").orNull?.let { systemProperty("jsvro.benchmark.$name", it) }
    }
    val runChosen = listOf("plan", "full").any { providers.gradleProperty("benchmark.$it").isPresent }
    doFirst {
        if (!runChosen) {
            throw GradleException("Choose a run: ./benchmark -full, or ./benchmark -ri 10:1000 20:500 for rows:iterations pairs")
        }
    }
    outputs.upToDateWhen { false }
    testLogging {
        showStandardStreams = true
    }
}

abstract class PublishBenchmarkReport : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val report: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val summary: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runMode: RegularFileProperty

    @get:OutputFile
    abstract val publishedReport: RegularFileProperty

    @get:Internal
    abstract val readme: RegularFileProperty

    @TaskAction
    fun publish() {
        val mode = runMode.get().asFile.readText().trim()
        if (mode != "full") {
            throw GradleException(
                    "Only a full benchmark run can be published, but the last run was a $mode run. Run ./benchmark -full first.")
        }
        val target = publishedReport.get().asFile
        target.parentFile.mkdirs()
        report.get().asFile.copyTo(target, overwrite = true)

        val start = "<!-- benchmark-summary:start -->"
        val end = "<!-- benchmark-summary:end -->"
        val readmeFile = readme.get().asFile
        val text = readmeFile.readText()
        val from = text.indexOf(start)
        val to = text.indexOf(end)
        require(from >= 0 && to > from) { "README.md must contain $start and $end" }
        val table = summary.get().asFile.readText().trim()
        readmeFile.writeText(text.substring(0, from + start.length) + "\n" + table + "\n" + text.substring(to))
    }
}

tasks.register<PublishBenchmarkReport>("publishBenchmarkReport") {
    description = "Copies the last benchmark report to docs/benchmark and updates the summary table in README.md."
    group = "documentation"
    report = layout.buildDirectory.file("reports/jsvro-benchmark/index.html")
    summary = layout.buildDirectory.file("reports/jsvro-benchmark/summary.md")
    runMode = layout.buildDirectory.file("reports/jsvro-benchmark/run-mode.txt")
    publishedReport = rootProject.layout.projectDirectory.file("docs/benchmark/index.html")
    readme = rootProject.layout.projectDirectory.file("README.md")
    outputs.upToDateWhen { false }
}

val jmh by sourceSets.creating {
    compileClasspath += sourceSets.test.get().output + sourceSets.test.get().compileClasspath
    runtimeClasspath += output + compileClasspath + sourceSets.test.get().runtimeClasspath
}

dependencies {
    "jmhImplementation"(libs.jmh.core)
    "jmhAnnotationProcessor"(libs.jmh.generator)
}

tasks.register<JavaExec>("jmh") {
    description = "Runs the JMH microbenchmarks; pass JMH options with -Pjmh.args=\"...\"."
    group = "verification"
    classpath = jmh.runtimeClasspath
    mainClass = "org.openjdk.jmh.Main"
    val results = layout.buildDirectory.file("reports/jmh/results.json")
    outputs.file(results)
    outputs.upToDateWhen { false }
    val extra = providers.gradleProperty("jmh.args").map { it.split(" ").filter(String::isNotBlank) }.orElse(listOf())
    argumentProviders.add(CommandLineArgumentProvider {
        extra.get() + listOf("-rf", "json", "-rff", results.get().asFile.absolutePath)
    })
    doFirst { results.get().asFile.parentFile.mkdirs() }
}
