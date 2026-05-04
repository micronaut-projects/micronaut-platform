import org.gradle.api.publish.maven.tasks.GenerateMavenPom

plugins {
    id("io.micronaut.build.internal.bom")
}

fun normalizeDuplicatePomProperties(pomFile: File) {
    val duplicatePomProperties = mapOf(
        "micronaut.mongodb.version" to "micronaut.mongo.version",
        "micronaut.oracle.cloud.version" to "micronaut.oraclecloud.version",
        "micronaut.problem.json.version" to "micronaut.problem.version"
    )
    var pom = pomFile.readText()
    duplicatePomProperties.forEach { (duplicate, replacement) ->
        val duplicatePattern = Regex("""<${Regex.escape(duplicate)}>([^<]+)</${Regex.escape(duplicate)}>""")
        val replacementPattern = Regex("""<${Regex.escape(replacement)}>([^<]+)</${Regex.escape(replacement)}>""")
        val duplicateVersion = duplicatePattern.find(pom)?.groupValues?.get(1)
        val replacementVersion = replacementPattern.find(pom)?.groupValues?.get(1)
        val hasDuplicateReference = pom.contains("$" + "{$duplicate}")
        if ((duplicateVersion != null || hasDuplicateReference) && replacementVersion == null) {
            throw GradleException(
                "Cannot normalize duplicate POM property '$duplicate' to '$replacement' " +
                    "because the replacement property is missing"
            )
        }
        if (duplicateVersion != null && replacementVersion != null && duplicateVersion != replacementVersion) {
            throw GradleException(
                "Cannot normalize duplicate POM property '$duplicate' to '$replacement' " +
                    "because they use different versions: $duplicateVersion and $replacementVersion"
            )
        }
        pom = pom.replace(Regex("""(?m)^\s*<${Regex.escape(duplicate)}>[^<]+</${Regex.escape(duplicate)}>\R?"""), "")
        pom = pom.replace("$" + "{$duplicate}", "$" + "{$replacement}")
    }
    pomFile.writeText(pom)
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
        "duplicate Micronaut module aliases are normalized in Micronaut 5.0.0".apply {
            acceptedVersionRegressions.addAll(
                "micronaut-mongodb",
                "micronaut-oracle-cloud",
                "micronaut-problem-json"
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
    withType<GenerateMavenPom>().configureEach {
        doLast {
            normalizeDuplicatePomProperties(destination)
        }
    }

    generateCatalogAsToml {
        doLast {
            val duplicateVersionAliases = mapOf(
                "micronaut-mongodb" to "micronaut-mongo",
                "micronaut-oracle-cloud" to "micronaut-oraclecloud",
                "micronaut-problem-json" to "micronaut-problem"
            )
            val catalogFile = outputFile.get().asFile
            val catalogLines = catalogFile.readLines()
            val versions = catalogLines.mapNotNull {
                Regex("""^([A-Za-z0-9_.-]+) = "([^"]+)"""").find(it)?.destructured?.let { (alias, version) ->
                    alias to version
                }
            }.toMap()
            duplicateVersionAliases.forEach { (duplicate, replacement) ->
                val duplicateVersion = versions[duplicate]
                val replacementVersion = versions[replacement]
                val hasDuplicateReference = catalogLines.any { it.contains("""version.ref = "$duplicate"""") }
                if ((duplicateVersion != null || hasDuplicateReference) && replacementVersion == null) {
                    throw GradleException(
                        "Cannot normalize duplicate version alias '$duplicate' to '$replacement' " +
                            "because the replacement alias is missing"
                    )
                }
                if (duplicateVersion != null && replacementVersion != null && duplicateVersion != replacementVersion) {
                    throw GradleException(
                        "Cannot normalize duplicate version alias '$duplicate' to '$replacement' " +
                            "because they use different versions: $duplicateVersion and $replacementVersion"
                    )
                }
            }
            val normalizedCatalog = catalogLines.mapNotNull { line ->
                val duplicateAlias = duplicateVersionAliases.keys.firstOrNull { line.startsWith("$it = \"") }
                if (duplicateAlias != null) {
                    null
                } else {
                    duplicateVersionAliases.entries.fold(line) { current, (duplicate, replacement) ->
                        current.replace("""version.ref = "$duplicate"""", """version.ref = "$replacement"""")
                    }
                }
            }
            catalogFile.writeText(normalizedCatalog.joinToString("\n"))
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
}
