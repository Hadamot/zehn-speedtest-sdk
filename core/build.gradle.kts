import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
    `maven-publish`
}

group = "tj.zehn.rmbt"
version = providers.gradleProperty("sdkVersion").getOrElse("0.1.0")

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    android {
        namespace = "tj.zehn.rmbt"
        compileSdk = 37
        minSdk = 24
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    js {
        outputModuleName.set("speedtest-sdk")
        compilations["main"].packageJson {
            name = "@hadamot/speedtest-sdk"
            customField("publishConfig", mapOf("registry" to "https://npm.pkg.github.com"))
            customField("repository", "https://github.com/Hadamot/zehn-speedtest-sdk")
        }
        binaries.library()
        generateTypeScriptDefinitions()
        browser()
        nodejs {
            testTask { useMocha { timeout = "30s" } }
        }
    }
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach {
        it.binaries.framework {
            baseName = "RmbtSdk"
        }
    }

    applyDefaultHierarchyTemplate {
        common {
            group("socket") {
                group("jvmAndroid") {
                    withJvm()
                    withCompilations { it.target.platformType == KotlinPlatformType.androidJvm }
                }
                group("ios") {
                    withIos()
                }
            }
        }
    }

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
        getByName("socketMain").dependencies {
            implementation(libs.ktor.network)
            implementation(libs.ktor.network.tls)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Zehn RMBT SDK")
            description.set("Kotlin Multiplatform SDK for RMBT speed tests against Open-RMBT")
            url.set("https://github.com/Hadamot/zehn-speedtest-sdk")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri(providers.gradleProperty("publishUrl").getOrElse("https://maven.pkg.github.com/Hadamot/zehn-speedtest-sdk"))
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}
