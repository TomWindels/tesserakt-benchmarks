import java.util.Properties

plugins {
    kotlin("jvm") version "2.2.20"
}

group = "dev.tesserakt.bench"
//version = gradle.includedBuild("tesserakt")
version = Properties()
    .apply { load(File(gradle.includedBuild("tesserakt").projectDir, "gradle.properties").inputStream()) }
    .get("VERSION_NAME")
    ?: "unknown"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.tomwindels:tesserakt-sparql:0.3.1")
}

val jar by tasks.getting(Jar::class) {
    // not required here
//    manifest {
//        attributes["Main-Class"] = "Engine"
//    }

    from(
        configurations
            .runtimeClasspath
            .get()
            .files
            .map { if (it.isDirectory) it else zipTree(it) }
    )
}
