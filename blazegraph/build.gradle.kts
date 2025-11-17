plugins {
    kotlin("jvm") version "2.2.20"
//    id("com.gradleup.shadow") version "9.2.2"
}

group = "dev.tesserakt.bench"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.blazegraph:bigdata-core:2.1.4")
}

val jar by tasks.getting(Jar::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(
        configurations
            .runtimeClasspath
            .get()
            .files
            .map { if (it.isDirectory) it else zipTree(it) }
    )
}
