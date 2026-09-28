plugins {
    base
    alias(libs.plugins.maven.publish) apply false
}

allprojects {
    group = "io.github.jsvro"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release = 21
            options.encoding = "UTF-8"
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

        dependencies {
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }
    }

    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral()
            signAllPublications()
            pom {
                name = project.name
                description = provider { project.description }
                url = "https://github.com/jsvro/jsvro"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "steinard"
                        name = "Steinar Dragsnes"
                        email = "steinar.dragsnes@gmail.com"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/jsvro/jsvro.git"
                    developerConnection = "scm:git:ssh://git@github.com/jsvro/jsvro.git"
                    url = "https://github.com/jsvro/jsvro"
                }
            }
        }
    }
}
