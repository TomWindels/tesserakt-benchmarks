import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("multiplatform") version "2.2.21"
    id("com.gradleup.shadow") version "9.2.2"
}

group = "dev.tesserakt.bench"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    js {
        nodejs {
            passCliArgumentsToMainFunction()
            binaries.executable()
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                // not sure why, but using dependency substitution made it necessary for every otherwise
                //  transitive `api()` module to be defined explicitly
                implementation("io.github.tomwindels:tesserakt-rdf:0.3.1")
                implementation("io.github.tomwindels:tesserakt-rdf-snapshot_store:0.3.1")
                implementation("io.github.tomwindels:tesserakt-serialization-common:0.3.1")
                implementation("io.github.tomwindels:tesserakt-serialization-core:0.3.1")
                implementation("io.github.tomwindels:tesserakt-serialization-trig:0.3.1")
                implementation("io.github.tomwindels:tesserakt-testing-tooling-replay_benchmark:0.3.1")
                implementation("com.github.ajalt.clikt:clikt:5.0.3")
            }
        }
        val jvmMain by getting {
            dependencies {
                // native implementations use the jvm implementation, utilising jna
                implementation("net.java.dev.jna:jna-platform:5.18.1")
            }
        }
    }
}

// see https://boyl.es/post/two-versions-same-library/ and https://gradleup.com/shadow/configuration/relocation/
tasks.named<ShadowJar>("shadowJar") {
    relocate("dev.tesserakt", "benchmark.tesserakt")
    manifest {
        attributes["Main-Class"] = "CLIKt"
    }
}
