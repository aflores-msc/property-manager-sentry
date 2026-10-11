plugins {
    // Lets Gradle download a JDK 25 automatically if none is installed (see java.toolchain in build.gradle.kts).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "property-manager-sentry"
