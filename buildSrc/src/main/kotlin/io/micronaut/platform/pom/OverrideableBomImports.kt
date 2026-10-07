package io.micronaut.platform.pom

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.gradle.api.Project
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Allowlisted external BOM import that keeps Maven child property overrides
 * coherent for dependency families owned by imported Micronaut BOMs.
 */
data class OverrideableBomImport(
    val propertyName: String,
    val importedBomGroupId: String,
    val importedBomArtifactId: String,
    val owningBomGroupId: String,
    val owningBomArtifactId: String,
    val owningBomVersionProperty: String
)

fun Project.configureOverrideableBomImports(
    pomFile: File,
    imports: List<OverrideableBomImport>
) {
    val document = parseXml(pomFile)
    val project = document.documentElement
    val properties = project.childElement("properties")
        ?: error("Expected generated Maven POM to contain project properties")
    val dependencyManagement = project.childElement("dependencyManagement")
        ?: error("Expected generated Maven POM to contain dependencyManagement")
    val dependencies = dependencyManagement.childElement("dependencies")
        ?: error("Expected generated Maven POM to contain dependencyManagement dependencies")

    imports.forEach { import ->
        val owningBomVersion = properties.childText(import.owningBomVersionProperty)
            ?: error("Expected generated Maven POM to expose ${import.owningBomVersionProperty}")
        val propertyValue = resolvePomProperty(
            import.owningBomGroupId,
            import.owningBomArtifactId,
            owningBomVersion,
            import.propertyName
        )

        properties.ensureChildText(import.propertyName, propertyValue)

        val owningBom = dependencies.dependency(
            import.owningBomGroupId,
            import.owningBomArtifactId,
            importBom = true
        ) ?: error("Expected generated Maven POM to import ${import.owningBomGroupId}:${import.owningBomArtifactId}")

        dependencies.childElements("dependency")
            .filter { it.isDependency(import.importedBomGroupId, import.importedBomArtifactId, importBom = true) }
            .forEach { dependencies.removeChild(it) }

        dependencies.insertBefore(
            document.createDependency(
                import.importedBomGroupId,
                import.importedBomArtifactId,
                "\${${import.propertyName}}",
                importBom = true
            ),
            owningBom
        )
    }

    writeXml(document, pomFile)
}

fun Project.checkOverrideableBomImports(
    pomFile: File,
    imports: List<OverrideableBomImport>
) {
    val document = parseXml(pomFile)
    val properties = document.getElementsByTagName("properties").item(0) as Element
    val dependencies = document.getElementsByTagName("dependency")

    imports.forEach { import ->
        val propertyValue = properties.getElementsByTagName(import.propertyName).item(0)?.textContent
        check(!propertyValue.isNullOrBlank()) {
            "Expected generated Maven POM to expose ${import.propertyName}"
        }

        var importedBomIndex = -1
        var owningBomIndex = -1
        for (i in 0 until dependencies.length) {
            val dependency = dependencies.item(i) as Element
            if (dependency.isDependency(import.importedBomGroupId, import.importedBomArtifactId)) {
                val isImport = dependency.childText("type") == "pom" && dependency.childText("scope") == "import"
                if (isImport) {
                    check(dependency.childText("version") == "\${${import.propertyName}}") {
                        "Expected imported ${import.importedBomGroupId}:${import.importedBomArtifactId} to use \${${import.propertyName}}"
                    }
                    importedBomIndex = i
                }
            }
            if (dependency.isDependency(import.owningBomGroupId, import.owningBomArtifactId, importBom = true)) {
                owningBomIndex = i
            }
        }

        check(importedBomIndex >= 0) {
            "Expected generated Maven POM to import ${import.importedBomGroupId}:${import.importedBomArtifactId}"
        }
        check(owningBomIndex < 0 || importedBomIndex < owningBomIndex) {
            "Expected ${import.importedBomGroupId}:${import.importedBomArtifactId} import to appear before ${import.owningBomGroupId}:${import.owningBomArtifactId}"
        }
    }
}

private fun Project.resolvePomProperty(
    groupId: String,
    artifactId: String,
    version: String,
    propertyName: String
): String {
    val pomFile = configurations.detachedConfiguration(
        dependencies.create("$groupId:$artifactId:$version@pom")
    ).singleFile
    val properties = parseXml(pomFile).documentElement.childElement("properties")
        ?: error("Expected $groupId:$artifactId:$version to contain properties")
    return properties.childText(propertyName)
        ?: error("Expected $groupId:$artifactId:$version to expose $propertyName")
}

private fun Element.childText(tagName: String): String? =
    getElementsByTagName(tagName).item(0)?.textContent

private fun parseXml(file: File) = DocumentBuilderFactory.newInstance()
    .also { it.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
    .newDocumentBuilder()
    .parse(file)

private fun writeXml(document: org.w3c.dom.Document, file: File) {
    document.removeBlankTextNodes()
    val transformer = TransformerFactory.newInstance().newTransformer()
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
    transformer.setOutputProperty(OutputKeys.METHOD, "xml")
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
    transformer.transform(DOMSource(document), StreamResult(file))
}

private fun Node.removeBlankTextNodes() {
    for (i in childNodes.length - 1 downTo 0) {
        val child = childNodes.item(i)
        if (child.nodeType == Node.TEXT_NODE && child.textContent.isBlank()) {
            removeChild(child)
        } else {
            child.removeBlankTextNodes()
        }
    }
}

private fun Element.childElement(tagName: String): Element? =
    childElements(tagName).firstOrNull()

private fun Element.childElements(tagName: String): List<Element> =
    (0 until childNodes.length)
        .map { childNodes.item(it) }
        .filterIsInstance<Element>()
        .filter { it.tagName == tagName }

private fun Element.ensureChildText(tagName: String, text: String) {
    val element = childElement(tagName) ?: ownerDocument.createElement(tagName).also { appendChild(it) }
    element.textContent = text
}

private fun Element.dependency(groupId: String, artifactId: String, importBom: Boolean = false): Element? =
    childElements("dependency").firstOrNull { it.isDependency(groupId, artifactId, importBom) }

private fun Element.isDependency(groupId: String, artifactId: String, importBom: Boolean = false): Boolean =
    childText("groupId") == groupId &&
        childText("artifactId") == artifactId &&
        (!importBom || childText("type") == "pom" && childText("scope") == "import")

private fun org.w3c.dom.Document.createDependency(
    groupId: String,
    artifactId: String,
    version: String,
    importBom: Boolean = false
): Node {
    val dependency = createElement("dependency")
    dependency.appendTextElement("groupId", groupId)
    dependency.appendTextElement("artifactId", artifactId)
    dependency.appendTextElement("version", version)
    if (importBom) {
        dependency.appendTextElement("type", "pom")
        dependency.appendTextElement("scope", "import")
    }
    return dependency
}

private fun Element.appendTextElement(tagName: String, text: String) {
    appendChild(ownerDocument.createElement(tagName).also { it.textContent = text })
}
