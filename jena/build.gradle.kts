import java.util.Properties

plugins {
    kotlin("jvm") version "2.2.20"
}

group = "dev.tesserakt.bench"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.jena:jena-arq:5.6.0")
}

val jar by tasks.getting(Jar::class) {
    // not required here
//    manifest {
//        attributes["Main-Class"] = "Engine"
//    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(
        configurations
            .runtimeClasspath
            .get()
            .files
            .map { if (it.isDirectory) it else zipTree(it) }
    )
}
