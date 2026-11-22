pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"
    id("dev.kikugie.loom-back-compat") version "0.4"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        fun target(project: String, version: String = project) {
            version("$project-fabric", version).buildscript("build.fabric.gradle.kts")
            version("$project-neoforge", version).buildscript("build.neoforge.gradle.kts")
        }

        // The 1.21 line gets its own targets later; everything below is
        // Minecraft 26, which ships unobfuscated and runs on Java 25.
        target("26.1.x", "26.1.2")
        target("26.2.x", "26.2")
        target("26.3.x", "26.3")

        vcsVersion = "26.3.x-fabric"
    }
}

rootProject.name = "Voice Translate"
