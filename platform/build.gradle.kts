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
        // Can be removed after 4.1.3 (see https://github.com/micronaut-projects/micronaut-platform/pull/941)
        this.acceptedLibraryRegressions.addAll(
            "micronaut-oraclecloud-bmc-computecloudatcustomer",
            "micronaut-oraclecloud-bmc-fleetsoftwareupdate",
            "micronaut-oraclecloud-bmc-osmanagementhub",
            "micronaut-oraclecloud-bmc-ocicontrolcenter"
        )

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
        dependencies.add("io.zipkin.brave:brave-bom:5.17.0")
        dependencies.add("io.zipkin.reporter2:zipkin-reporter-bom:2.16.3")

        dependencies.add("io.opentelemetry:opentelemetry-bom:1.31.0")
        dependencies.add("io.opentelemetry:opentelemetry-bom-alpha:1.31.0-alpha")

        dependencies.add("io.opentelemetry:opentelemetry-bom:1.31.0")
        dependencies.add("io.opentelemetry:opentelemetry-bom-alpha:1.31.0-alpha")
        dependencies.add("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:2.0.0")
        dependencies.add("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:1.31.0-alpha")

        // This is in the micrometer BOM for version 1.11.5, but the module is never published
        // See https://github.com/micrometer-metrics/micrometer/issues/4350
        dependencies.add("io.micrometer:micrometer-osgi-test:1.11.5")
    }
}

micronautBuild {
    binaryCompatibility.enabled.set(version != "4.0.0-SNAPSHOT")
}
