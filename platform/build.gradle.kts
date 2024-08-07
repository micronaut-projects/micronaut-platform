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

        dependencies.add("io.zipkin.reporter2:zipkin-reporter-bom:3.4.0")
        dependencies.add("io.zipkin.brave:brave-instrumentation-benchmarks:6.0.3")

        dependencies.add("io.opentelemetry:opentelemetry-bom:1.40.0")
        dependencies.add("io.opentelemetry:opentelemetry-bom-alpha:1.40.0-alpha")
        dependencies.add("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:1.33.5")

        // This is in the micrometer BOM for version 1.12.2, but the module is never published
        // See https://github.com/micrometer-metrics/micrometer/issues/4606
        dependencies.add("io.micrometer:docs:1.12.2")

        acceptedLibraryRegressions.add("micronaut-oraclecloud-bmc-applicationmigration")
    }
}

micronautBuild {
    binaryCompatibility.enabled.set(version != "4.0.0-SNAPSHOT")
}

tasks {
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
