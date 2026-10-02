package io.micronaut.build.internal

import io.micronaut.platform.docs.ManagedDependenciesGuideSafety
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import org.w3c.dom.NodeList

/**
 * Verifies the `exec-maven-plugin` arguments of a generated POM, that is, the command line
 * that `mvn exec:exec` launches the application with. An `<argument>` is compared by its text,
 * any other element (such as `<classpath/>`) as `<name/>`.
 */
abstract class VerifyExecArguments : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val pomFile: RegularFileProperty

    @get:Input
    abstract val expectedArguments: ListProperty<String>

    @TaskAction
    fun verify() {
        val file = pomFile.get().asFile
        val document = ManagedDependenciesGuideSafety.secureDocumentBuilderFactory()
            .newDocumentBuilder()
            .parse(file)
        val plugin = document.getElementsByTagNameNS("*", "plugin")
            .elements()
            .singleOrNull { it.child("artifactId")?.textContent?.trim() == "exec-maven-plugin" }
            ?: throw GradleException("$file does not configure exec-maven-plugin exactly once")
        val arguments = plugin.child("configuration")
            ?.child("arguments")
            ?.childNodes
            ?.elements()
            ?.map { if (it.localName == "argument") it.textContent.trim() else "<${it.localName}/>" }
            ?: throw GradleException("$file does not configure exec-maven-plugin arguments")
        val expected = expectedArguments.get()
        if (arguments != expected) {
            throw GradleException("$file launches exec:exec with $arguments, expected $expected")
        }
    }

    private fun Element.child(name: String): Element? =
        childNodes.elements().firstOrNull { it.localName == name }

    private fun NodeList.elements(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }
}
