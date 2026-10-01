pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
    plugins {
        id("net.fabricmc.fabric-loom") version "1.18-SNAPSHOT"
        id("net.fabricmc.fabric-loom-remap") version "1.18-SNAPSHOT"
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
    create(rootProject) {
        // Add a line here (plus versions/<name>/gradle.properties) to support another Minecraft version.
        versions("1.21.8", "26.2")
        vcsVersion = "26.2"
    }
}

rootProject.name = "aller-client"
