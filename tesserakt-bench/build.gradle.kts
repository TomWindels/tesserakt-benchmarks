plugins {
    kotlin("multiplatform") version "2.2.21"
}

group = "dev.tesserakt.bench"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("io.github.tomwindels:tesserakt-rdf")
                implementation("io.github.tomwindels:tesserakt-serialization-trig")
                implementation("io.github.tomwindels:tesserakt-testing-tooling-replay_benchmark")
            }
        }
        val jvmMain by getting {
            dependencies {
                // jna
                implementation("net.java.dev.jna:jna-platform:5.18.1")
            }
        }
    }
}
