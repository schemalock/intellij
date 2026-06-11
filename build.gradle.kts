import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    kotlin("jvm") version "2.4.0"
    id("org.jetbrains.intellij.platform")
}

group = "dev.schemalock"
version = "0.1.0"

// Repositories are managed in settings.gradle.kts (dependencyResolutionManagement),
// as required by the IntelliJ Platform Gradle Plugin 2.x settings plugin.

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        create("IU", "2024.2")
        bundledPlugin("org.jetbrains.plugins.yaml")
    }
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    // Gradle 9 no longer puts the JUnit Platform launcher on the test runtime
    // classpath automatically — declare it explicitly.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        name = "SchemaLock"
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        // Platform plugin 2.16 promotes INTERNAL_API_USAGES / DEPRECATED_API_USAGES
        // to failures by default. We knowingly use PluginManagerCore.getPlugin
        // (internal) in BinaryResolver and the deprecated DaemonCodeAnalyzer.restart;
        // keep those as warnings and only fail on genuine compatibility/structure
        // problems, as under the previous plugin version.
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
        )
        ides {
            recommended()
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    // Bundle per-platform binaries inside the plugin ZIP under bin/
    named<PrepareSandboxTask>("prepareSandbox") {
        from("bin") {
            into("schemalock/bin")
        }
    }

    named<PrepareSandboxTask>("prepareTestSandbox") {
        from("bin") {
            into("schemalock/bin")
        }
    }
}
