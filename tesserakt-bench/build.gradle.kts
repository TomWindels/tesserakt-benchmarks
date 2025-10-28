plugins {
    kotlin("jvm") version "2.2.21"
    application
}

group = "dev.tesserakt.bench"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.tomwindels:tesserakt-serialization-trig")
    implementation("io.github.tomwindels:tesserakt-testing-tooling-replay_benchmark")
    // jna
    implementation("net.java.dev.jna:jna-platform:5.18.1")
    implementation("com.github.ajalt.clikt:clikt:5.0.3")
}

application {
    mainClass.set("CLIKt")
}
