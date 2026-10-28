plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Publish Java 17-compatible bytecode even when ETA's build JDK is 25.
// This lets KELIVO and other Android/desktop hosts reuse this artifact.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}
