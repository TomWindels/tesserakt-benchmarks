import java.util.Properties

plugins {
    kotlin("multiplatform") version "2.2.20"
    id("com.gradleup.shadow") version "9.2.2"
}

group = "dev.tesserakt.bench"
//version = gradle.includedBuild("tesserakt")
version = Properties()
    .runCatching {
        load(File(gradle.includedBuild("tesserakt").projectDir, "gradle.properties").inputStream());
        this
    }
    .getOrNull()
    ?.get("VERSION_NAME")
    ?: "unknown"

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation("io.github.tomwindels:tesserakt-sparql-runtime:0.4.0")
                implementation("io.github.tomwindels:tesserakt-sparql:0.4.0")
            }
        }
    }
}

repositories {
    mavenCentral()
}

