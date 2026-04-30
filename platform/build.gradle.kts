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

plugins {
    id("io.micronaut.build.internal.bom")
}

repositories {
    mavenCentral()
    gradlePluginPortal() // needed for checkBom task to resolve plugin dependencies defined in kotest-pom
}

micronautBom {
    propertyName.set("platform")
    extraExcludedProjects.add("parent")

    suppressions {
        // https://github.com/micronaut-projects/micronaut-core/pull/7631#issuecomment-1174702395
        bomAuthorizedGroupIds.put(
                "io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom",
                setOf(
                    "io.opentelemetry",
                    "io.opentelemetry.instrumentation",
                    "io.opentelemetry.javaagent",
                    "io.opentelemetry.javaagent.instrumentation"
                )
        )
        bomAuthorizedGroupIds.put(
                "io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha",
                setOf(
                    "io.opentelemetry",
                    "io.opentelemetry.semconv",
                    "io.opentelemetry.instrumentation",
                    "io.opentelemetry.javaagent",
                    "io.opentelemetry.javaagent.instrumentation"
                )
        )
        bomAuthorizedGroupIds.put(
                "io.zipkin.brave:brave-bom",
                setOf(
                    "io.zipkin.brave",
                    "io.zipkin.zipkin2",
                    "io.zipkin.reporter2",
                    "io.zipkin.proto3"
                )
        )
        bomAuthorizedGroupIds.put(
                "io.zipkin.reporter2:zipkin-reporter-bom",
                setOf(
                    "io.zipkin.reporter2",
                    "io.zipkin.zipkin2",
                    "io.zipkin.proto3"
                )
        )
        bomAuthorizedGroupIds.put(
            "io.projectreactor:reactor-bom",
            setOf("io.projectreactor", "org.reactivestreams")
        )
        // micronaut-oraclecloud (since v3.6.0) pulls in the ojdbc-bom which imports dependencies from many groups
        bomAuthorizedGroupIds.put(
            "com.oracle.database.jdbc:ojdbc-bom",
            setOf(
                "com.oracle.database.ha",
                "com.oracle.database.nl",
                "com.oracle.database.nls",
                "com.oracle.database.observability",
                "com.oracle.database.security",
                "com.oracle.database.xml",
            )
        )

        dependencies.add("io.zipkin.reporter2:zipkin-reporter-bom:3.5.1")
        dependencies.add("io.zipkin.brave:brave-instrumentation-benchmarks:6.0.3")

        dependencies.add("io.opentelemetry:opentelemetry-bom:1.53.0")
        dependencies.add("io.opentelemetry:opentelemetry-bom-alpha:1.40.0-alpha")
        dependencies.add("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:2.21.0")

        // modules no longer published by the OCI SDK
        acceptedLibraryRegressions.add("micronaut-oraclecloud-bmc-applicationmigration")
        acceptedLibraryRegressions.add("micronaut-oraclecloud-bmc-aianomalydetection")
        acceptedLibraryRegressions.add("micronaut-oraclecloud-bmc-osmanagement")
        acceptedLibraryRegressions.add("micronaut-oraclecloud-bmc-dts")
        acceptedLibraryRegressions.add("micronaut-oraclecloud-bmc-servicemesh")


        acceptedLibraryRegressions.add("kafka")
        acceptedVersionRegressions.add("kafka-compat")

        // Removed reactor-netty-http removed in https://github.com/micronaut-projects/micronaut-r2dbc/commit/ae5f341d26b89800b45caffd1494771948584588
        acceptedVersionRegressions.add("reactor-netty")
        acceptedLibraryRegressions.add("reactor-netty-http")

        // Removed in https://github.com/micronaut-projects/micronaut-r2dbc/releases/tag/v6.0.0
        acceptedVersionRegressions.add("r2dbc-io-asyncer-mysql")
        acceptedLibraryRegressions.add("r2dbc-io-asyncer-mysql")

        // Micronaut Coherence 5 regressions
        acceptedLibraryRegressions.add("coherence-java-client")
        acceptedLibraryRegressions.add("micronaut-coherence-grpc-test")
        acceptedLibraryRegressions.add("micronaut-coherence-grpc-client")
        acceptedLibraryRegressions.add("coherence-grpc-proxy")

        // Netty 4.2 moved these from incubator to the Netty monorepo
        acceptedVersionRegressions.add("netty-iouring")
        acceptedVersionRegressions.add("netty-http3")
        acceptedLibraryRegressions.add("netty-incubator-codec-http3")

        // Langchain4j BOM no longer imported
        acceptedVersionRegressions.add("langchain4j")
        acceptedLibraryRegressions.add("boms-langchain4j")

        dependencies.add("io.opentelemetry:opentelemetry-bom:1.53.0")
        dependencies.add("io.opentelemetry.semconv:opentelemetry-semconv:1.37.0")
        dependencies.add("io.opentelemetry:opentelemetry-bom-alpha:1.50.0-alpha")
        dependencies.add("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:2.21.0")

        acceptedVersionRegressions.add("jackson-databind")

        acceptedLibraryRegressions.add("jackson-datatype-jsr310")
        acceptedLibraryRegressions.add("netty-contrib-multipart-vintage")
        acceptedLibraryRegressions.add("micronaut-test-resources-elasticsearch")
        "junit-platform has changed in JUnit 6".apply {
            acceptedVersionRegressions.addAll(
                "junit-platform"
            )
            acceptedLibraryRegressions.addAll(
                "junit-platform-runner",
                "junit-platform-jfr"
            )
        }
        "junit-platform has changed in JUnit 6".apply {
            acceptedLibraryRegressions.addAll(
                "jackson-datatype-jdk8",
                "jackson-datatype-jsr310",
                "jackson-module-parameterNames"
            )
        }
        "oracle cloud sdk changes".apply {
            acceptedLibraryRegressions.addAll(
                "micronaut-oraclecloud-bmc-globallydistributeddatabase",
            )
        }
        "neo4j changes".apply {
            acceptedVersionRegressions.addAll(
                "neo4j"
            )
            acceptedLibraryRegressions.addAll(
                "neo4j-harness",
            )
        }


        "microstream was removed in Micronaut 5.0.0".apply {
            acceptedVersionRegressions.addAll(
                "microstream",
                "micronaut-microstream"
            )
            acceptedLibraryRegressions.addAll(
                "microstream",
                "microstream-storage-restservice",
                "microstream-storage-embedded-configuration",
                "microstream-sql",
                "microstream-aws-s3",
                "microstream-cache",
                "microstream-aws-dynamodb",
                "micronaut-microstream-bom",
                "micronaut-microstream",
                "micronaut-microstream-annotations",
                "micronaut-microstream-cache",
                "micronaut-microstream-rest"
            )
        }

        "rxJava2 was removed in Micronaut 5.0.0".apply {
            acceptedVersionRegressions.addAll(
                "rxjava2",
                "micronaut-rxjava2"
            )
            acceptedLibraryRegressions.addAll(
                "rxjava2",
                "micronaut-rxjava2-bom",
                "micronaut-rxjava2",
                "micronaut-rxjava2-http-client",
                "micronaut-rxjava2-http-server-netty",
            )
        }

        "openapi-generator temporarily removed in Micronaut 5.0.0".apply {
            acceptedLibraryRegressions.addAll(
                "micronaut-openapi-generator"
            )
        }

        "removed graal deprecated versions".apply {
            acceptedVersionRegressions.addAll(
                "graal",
                "graal-svm",
                "graal-sdk"
            )
            acceptedLibraryRegressions.addAll(
                "graal",
                "graal-sdk"
            )
        }
    }
}

micronautBuild {
    binaryCompatibility.enabled.set(version != "4.0.0-SNAPSHOT")
}

tasks {
    val generatedMavenPom = layout.buildDirectory.file("publications/maven/pom-default.xml")

    named("generatePomFileForMavenPublication") {
        doLast {
            configureNettyBomImport(generatedMavenPom.get().asFile)
        }
    }

    // This is a workaround for the `jackson-databind` version being removed from the catalog
    // because it's not referenced anywhere anymore. However we must keep it for backwards
    // compatibility. This canbe removed after the next major release.
    if (version.toString().startsWith("4.")) {
        generateCatalogAsToml {
            doLast {
                val catalogFile = outputFile.get().asFile.readLines()
                // append one line after the first line which starts with `jackson = `
                val newCatalogFile = catalogFile.joinToString("\n") {
                    if (it.startsWith("jackson = ")) {
                        var versionPart = it.substringAfterLast(" = ")
                        "$it\n#@NextMajorVersion @Deprecated Delete in Micronaut Framework 5.\njackson-databind = $versionPart"
                    } else {
                        it
                    }
                }
                outputFile.get().asFile.writeText(newCatalogFile)
            }
        }
    }
    checkVersionCatalogCompatibility {
        doFirst {
            println(baseline.get().asFile)
            println(current.get().asFile)
        }
    }

    val checkNettyBomImport by registering {
        description = "Verifies the published Maven platform POM exposes an overrideable Netty BOM import."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn("generatePomFileForMavenPublication")
        inputs.file(generatedMavenPom)

        doLast {
            val factory = DocumentBuilderFactory.newInstance()
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            val document = factory.newDocumentBuilder().parse(generatedMavenPom.get().asFile)
            val properties = document.getElementsByTagName("properties").item(0) as Element
            val nettyVersion = properties.getElementsByTagName("netty.version").item(0)?.textContent
            check(!nettyVersion.isNullOrBlank()) {
                "Expected generated Maven POM to expose a netty.version property"
            }

            var importedNettyBomIndex = -1
            var importedMicronautCoreBomIndex = -1
            val dependencies = document.getElementsByTagName("dependency")
            for (i in 0 until dependencies.length) {
                val dependency = dependencies.item(i) as Element
                val groupId = dependency.childText("groupId")
                val artifactId = dependency.childText("artifactId")
                if (groupId == "io.netty" && artifactId == "netty-bom") {
                    val isImport = dependency.childText("type") == "pom" && dependency.childText("scope") == "import"
                    if (isImport) {
                        check(dependency.childText("version") == "\${netty.version}") {
                            "Expected imported io.netty:netty-bom to use \${netty.version}"
                        }
                        importedNettyBomIndex = i
                    }
                }
                if (groupId == "io.micronaut" &&
                    artifactId == "micronaut-core-bom" &&
                    dependency.childText("type") == "pom" &&
                    dependency.childText("scope") == "import") {
                    importedMicronautCoreBomIndex = i
                }
            }

            check(importedNettyBomIndex >= 0) {
                "Expected generated Maven POM to import io.netty:netty-bom"
            }
            check(importedMicronautCoreBomIndex < 0 || importedNettyBomIndex < importedMicronautCoreBomIndex) {
                "Expected io.netty:netty-bom import to appear before micronaut-core-bom"
            }
        }
    }

    check {
        dependsOn(checkNettyBomImport)
    }
}

private fun Project.configureNettyBomImport(pomFile: File) {
    val document = parseXml(pomFile)
    val project = document.documentElement
    val properties = project.childElement("properties")
        ?: error("Expected generated Maven POM to contain project properties")
    val micronautCoreVersion = properties.childText("micronaut.core.version")
        ?: error("Expected generated Maven POM to expose micronaut.core.version")
    val nettyVersion = resolveMicronautCoreNettyVersion(micronautCoreVersion)

    properties.ensureChildText("netty.version", nettyVersion)

    val dependencyManagement = project.childElement("dependencyManagement")
        ?: error("Expected generated Maven POM to contain dependencyManagement")
    val dependencies = dependencyManagement.childElement("dependencies")
        ?: error("Expected generated Maven POM to contain dependencyManagement dependencies")
    val micronautCoreBom = dependencies.dependency("io.micronaut", "micronaut-core-bom", importBom = true)
        ?: error("Expected generated Maven POM to import micronaut-core-bom")

    dependencies.childElements("dependency")
        .filter { it.isDependency("io.netty", "netty-bom", importBom = true) }
        .forEach { dependencies.removeChild(it) }

    dependencies.insertBefore(
        document.createDependency("io.netty", "netty-bom", "\${netty.version}", importBom = true),
        micronautCoreBom
    )
    writeXml(document, pomFile)
}

private fun Project.resolveMicronautCoreNettyVersion(micronautCoreVersion: String): String {
    val coreBomPom = configurations.detachedConfiguration(
        dependencies.create("io.micronaut:micronaut-core-bom:$micronautCoreVersion@pom")
    ).singleFile
    val properties = parseXml(coreBomPom).documentElement.childElement("properties")
        ?: error("Expected micronaut-core-bom $micronautCoreVersion to contain properties")
    return properties.childText("netty.version")
        ?: error("Expected micronaut-core-bom $micronautCoreVersion to expose netty.version")
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
