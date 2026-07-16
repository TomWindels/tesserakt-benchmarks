plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "tesserakt-jar"

includeBuild("./.tesserakt") {
    dependencySubstitution {
        substitute(module("io.github.tomwindels:tesserakt-sparql"))
            .using(project(":sparql"))
    }
}
