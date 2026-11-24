import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "tj.zehn.rmbt"
version = "0.1.0"

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    js {
        browser()
        nodejs {
            testTask { useMocha { timeout = "30s" } }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.network)
            implementation(libs.ktor.network.tls)
            implementation(libs.ktor.client.cio)
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}
