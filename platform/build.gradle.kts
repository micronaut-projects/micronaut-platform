plugins {
    id("io.micronaut.build.internal.bom")
}

repositories {
    mavenCentral()
}

micronautBom {
    propertyName.set("platform")
    extraExcludedProjects.add("parent")
    suppressions {
        // ReleaseCandidate ChatBots module removed from 4.1.7 (first official release will be 4.3.0)
        // this can be removed on the 4.1.x branch after 4.1.7 is released
        acceptedVersionRegressions.add("micronaut-chatbots")
        acceptedLibraryRegressions.addAll(
            "micronaut-chatbots-basecamp-api",
            "micronaut-chatbots-basecamp-lambda",
            "micronaut-chatbots-telegram-gcp-function",
            "micronaut-chatbots-telegram-lambda",
            "micronaut-chatbots-telegram-http",
            "micronaut-chatbots-basecamp-azure-function",
            "micronaut-chatbots-basecamp-http",
            "micronaut-chatbots-http",
            "micronaut-chatbots-basecamp-core",
            "micronaut-chatbots-core",
            "micronaut-chatbots-telegram-azure-function",
            "micronaut-chatbots-bom",
            "micronaut-chatbots-google-api",
            "micronaut-chatbots-telegram-api",
            "micronaut-chatbots-lambda",
            "micronaut-chatbots-telegram-core",
            "micronaut-chatbots-basecamp-gcp-function"
        )

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
        dependencies.add("io.zipkin.brave:brave-bom:5.16.0")
        dependencies.add("io.zipkin.reporter2:zipkin-reporter-bom:2.16.3")
        dependencies.add("io.opentelemetry:opentelemetry-bom:1.26.0")
        dependencies.add("io.opentelemetry:opentelemetry-bom-alpha:1.26.0-alpha")
        dependencies.add("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:1.26.0")
        dependencies.add("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:1.26.0-alpha")

    }
}

micronautBuild {
    binaryCompatibility.enabled.set(version != "4.0.0-SNAPSHOT")
}
