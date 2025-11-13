plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "tesserakt-bench"

// `path/to/symlink` is not valid, causes `Missing ExternalProject for :`
// converting it into a real path (following the symlink) fixes this
//includeBuild(File(rootProject.projectDir, "../tesserakt").toPath().toRealPath().absolutePathString()) {
//    dependencySubstitution {
//        substitute(module("io.github.tomwindels:tesserakt-rdf"))
//            .using(project(":rdf"))
//        substitute(module("io.github.tomwindels:tesserakt-testing-tooling-replay_benchmark"))
//            .using(project(":testing:tooling:replay-benchmark"))
//        substitute(module("io.github.tomwindels:tesserakt-serialization-trig"))
//            .using(project(":serialization:trig"))
//    }
//}