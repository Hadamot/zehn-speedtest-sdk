import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = "tj.zehn.rmbt"
version = providers.gradleProperty("sdkVersion").getOrElse("0.1.0")

android {
    namespace = "tj.zehn.rmbt.ui"
    compileSdk = 37
    // Copied resources are prefixed with rmbt_ so they never replace the host app's resources.
    resourcePrefix = "rmbt_"

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":rmbt-sdk"))
    implementation(libs.coroutines.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    // Test location and its point on the result screen; the host app supplies the Maps API key.
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.org.json)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "rmbt-sdk-ui"
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("Zehn RMBT SDK UI")
                description.set("Ready-made Android screens (start, test, result, history) for the Zehn RMBT SDK")
                url.set("https://github.com/Hadamot/zehn-speedtest-sdk")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
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
