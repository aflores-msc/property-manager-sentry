import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
}

group = "com.propertymanager"
version = "0.1.0-SNAPSHOT"
description = "Property Manager Sentry — AI triage for a property manager's Gmail inbox"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// JavaFX ships one jar per OS/CPU. We stay on the classpath (no module-info),
// so we pick the matching classifier ourselves instead of using a plugin.
val javafxPlatform: String =
    run {
        val os = System.getProperty("os.name").lowercase()
        val arm = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }
        when {
            os.contains("win") -> "win"
            os.contains("mac") -> if (arm) "mac-aarch64" else "mac"
            else -> if (arm) "linux-aarch64" else "linux"
        }
    }

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(platform(libs.spring.ai.bom))

    // Spring Boot: DI, configuration, JPA over an embedded H2 file database, Flyway migrations.
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    runtimeOnly("com.h2database:h2")

    // Spring AI (Gemini) + LangGraph4j for the triage graph.
    implementation(libs.spring.ai.starter.google.genai)
    implementation(libs.langgraph4j.core)

    // Gmail API with the OAuth2 desktop ("installed app") flow.
    implementation(libs.google.api.client)
    implementation(libs.google.oauth.client.jetty)
    implementation(libs.google.gmail)
    implementation(libs.jsoup)

    // JavaFX desktop UI with the AtlantaFX theme.
    listOf("base", "graphics", "controls").forEach {
        implementation("org.openjfx:javafx-$it:${libs.versions.javafx.get()}:$javafxPlatform")
    }
    implementation(libs.atlantafx) {
        exclude(group = "org.openjfx")
    }

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial"))
}

tasks.test {
    useJUnitPlatform()
}

springBoot {
    mainClass = "com.propertymanager.sentry.SentryLauncher"
}

// JavaFX loads native libraries; JDK 25 warns unless native access is granted explicitly.
val jvmFlags = listOf("--enable-native-access=ALL-UNNAMED")

tasks.bootRun {
    jvmArgs(jvmFlags)
}

tasks.bootJar {
    manifest {
        attributes("Enable-Native-Access" to "ALL-UNNAMED")
    }
}

// Code formatting. `./gradlew spotlessApply` fixes everything; `spotlessCheck` (part of `check`, so of
// `build` and CI) fails on unformatted files.
spotless {
    java {
        target("src/**/*.java")
        // `// spotless:off` ... `// spotless:on` keeps hand-formatted blocks (e.g. the triage graph) as written.
        toggleOffOn()
        // google-java-format in AOSP style (4-space indent). It keeps up with new Java syntax
        // (e.g. unnamed `_` in record patterns), which Palantir's formatter can't parse yet.
        // No explicit version: each Spotless release pins the google-java-format version it supports
        // (newer ones can break its integration), so upgrading Spotless upgrades the formatter.
        googleJavaFormat().aosp().reflowLongStrings()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
    format("misc") {
        target(
            "*.md",
            "docs/**/*.md",
            "config/**/*.yml",
            ".github/**/*.yml",
            "src/**/*.yml",
            "src/**/*.sql",
            "src/**/*.css",
            "src/**/*.st",
            ".gitignore",
            ".gitattributes",
            "gradle.properties",
            "gradle/*.toml",
        )
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        endWithNewline()
    }
}
