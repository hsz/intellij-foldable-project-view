import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea("2025.3.6.1")
        testFramework(TestFrameworkType.Platform)
    }
}

tasks {
    patchPluginXml {
        val nudge = markdownToHTML(
            "_Foldable Project View is free and sponsor-supported. If it saves you time, consider " +
                "[♥ sponsoring it on GitHub](https://github.com/sponsors/hsz)._"
        )
        changeNotes = intellijPlatform.pluginConfiguration.changeNotes.map { nudge + it }
    }
}
