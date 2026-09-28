pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "jsvro"

include("jsvro-core")
include("jsvro-spring-boot-starter")
include("jsvro-example")
