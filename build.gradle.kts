import org.jetbrains.intellij.tasks.PrepareSandboxTask

plugins {
    id("java")
    kotlin("jvm") version "1.9.23"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "dev.schemalock"
version = file(".app-version").readText().trim().removePrefix("v") + "-idea.1"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

intellij {
    version.set("2024.1")
    type.set("IC")
    pluginName.set("schemalock-intellij")
    downloadSources.set(false)
}

tasks {
    test {
        useJUnitPlatform()
    }

    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("")
    }

    // Bundle per-platform binaries inside the plugin ZIP under bin/
    named<PrepareSandboxTask>("prepareSandbox") {
        from("bin") {
            into("${intellij.pluginName.get()}/bin")
        }
    }

    named<PrepareSandboxTask>("prepareTestingSandbox") {
        from("bin") {
            into("${intellij.pluginName.get()}/bin")
        }
    }
}
