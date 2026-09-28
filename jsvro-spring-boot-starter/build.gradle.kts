plugins {
    `java-library`
    `maven-publish`
}

description = "Spring Boot auto-configuration for JSVRO HTTP message conversion."

dependencies {
    api(project(":jsvro-core"))
    implementation(libs.spring.boot.autoconfigure)

    compileOnly(platform(libs.spring.boot.dependencies))
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework.boot:spring-boot-http-converter")

    annotationProcessor(platform(libs.spring.boot.dependencies))
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.github.jsvro.spring")
    }
}
