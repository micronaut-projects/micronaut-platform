plugins {
    id("io.micronaut.build.internal.bom")
}

repositories {
    mavenCentral()
    maven { setUrl("https://s01.oss.sonatype.org/content/repositories/snapshots")}
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
        dependencies.add("io.zipkin.brave:brave-bom:5.15.0")
        dependencies.add("io.zipkin.reporter2:zipkin-reporter-bom:2.16.3")
        dependencies.add("io.opentelemetry:opentelemetry-bom:1.22.0")
        dependencies.add("io.opentelemetry:opentelemetry-bom-alpha:1.22.0-alpha")
        dependencies.add("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:1.22.0")
        dependencies.add("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:1.22.0-alpha")

    }
}

micronautBuild {
    binaryCompatibility.enabled.set(version != "4.0.0-SNAPSHOT")
}
