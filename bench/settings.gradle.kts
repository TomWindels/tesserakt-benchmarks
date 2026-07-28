import kotlin.io.path.absolutePathString

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "tesserakt-bench"

// `path/to/symlink` is not valid, causes `Missing ExternalProject for :`
// converting it into a real path (following the symlink) fixes this
val tesserakt = File(rootProject.projectDir, "../../tesserakt")
if (!tesserakt.exists()) {
    println("Using tesserakt from Maven Central!")
} else {
    val tesseraktPath = tesserakt.toPath().toRealPath().absolutePathString()
    println("Using local version of tesserakt, located at ${tesseraktPath}")
    includeBuild(tesseraktPath) {
        dependencySubstitution {
            substitute(module("io.github.tomwindels:tesserakt-rdf"))
                .using(project(":rdf"))
            substitute(module("io.github.tomwindels:tesserakt-rdf-snapshot_store"))
                .using(project(":rdf:snapshot-store"))
            substitute(module("io.github.tomwindels:tesserakt-testing-tooling-replay_benchmark"))
                .using(project(":testing:tooling:replay-benchmark"))
            substitute(module("io.github.tomwindels:tesserakt-serialization-trig"))
                .using(project(":serialization:trig"))
            substitute(module("io.github.tomwindels:tesserakt-serialization-common"))
                .using(project(":serialization:common"))
            substitute(module("io.github.tomwindels:tesserakt-serialization-core"))
                .using(project(":serialization:core"))
        }
    }
}
