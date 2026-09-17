package io.micronaut.platform.docs

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyManagedDependenciesGuide : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val guideFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val text = guideFile.get().asFile.readText()
        val requiredSections = listOf(
            "Platform BOMs",
            "Managed standalone libraries",
            "Generated catalog coordinates",
            "Parent POM plugin and property versions"
        )
        requiredSections.forEach { section ->
            if (!text.contains("== $section")) {
                throw GradleException("Managed dependencies guide is missing section '$section'")
            }
        }

        val anchors = Regex("\\[\\[([^]]+)]]").findAll(text).map { it.groupValues[1] }.toList()
        if (anchors.isEmpty()) {
            throw GradleException("Managed dependencies guide does not contain stable anchors")
        }
        val duplicateAnchors = anchors.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicateAnchors.isNotEmpty()) {
            throw GradleException("Managed dependencies guide has duplicate anchors: ${duplicateAnchors.sorted().joinToString()}")
        }

        val sectionAnchorPrefixes = mapOf(
            "Platform BOMs" to "managed-dependencies-platform-boms-",
            "Managed standalone libraries" to "managed-dependencies-managed-standalone-libraries-",
            "Generated catalog coordinates" to "managed-dependencies-generated-catalog-coordinates-",
            "Parent POM plugin and property versions" to "managed-dependencies-parent-pom-plugin-and-property-versions-"
        )
        sectionAnchorPrefixes.forEach { (section, prefix) ->
            if (anchors.none { it.startsWith(prefix) }) {
                throw GradleException("Managed dependencies guide section '$section' does not contain anchored rows")
            }
        }

        val unresolvedGeneratedTokens = listOf("%%", "\${")
            .filter { text.contains(it) }
        if (unresolvedGeneratedTokens.isNotEmpty()) {
            throw GradleException("Managed dependencies guide contains unresolved generated tokens: $unresolvedGeneratedTokens")
        }

        val activeAsciiDocTokens = ManagedDependenciesGuideSafety.activeAsciiDocTokens(text)
        if (activeAsciiDocTokens.isNotEmpty()) {
            throw GradleException("Managed dependencies guide contains active AsciiDoc or HTML tokens: $activeAsciiDocTokens")
        }
    }
}
