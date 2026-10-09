pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "Foldable Project View"
//includeBuild("/Users/hsz/Projects/JetBrains/gradle-intellij-plugin")
