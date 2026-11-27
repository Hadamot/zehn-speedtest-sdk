rootProject.name = "zehn-speedtest-sdk"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":rmbt-sdk")
project(":rmbt-sdk").projectDir = file("core")

include(":rmbt-sdk-ui")
project(":rmbt-sdk-ui").projectDir = file("ui")
