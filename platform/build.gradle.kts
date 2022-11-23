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
                "io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha",
                setOf(
                    "io.opentelemetry.javaagent",
                    "io.opentelemetry",
                    "io.opentelemetry.instrumentation",
                    "io.opentelemetry.javaagent.instrumentation"
                )
        )
        dependencies.add("io.opentelemetry:opentelemetry-bom:1.15.0")
        dependencies.add("io.opentelemetry:opentelemetry-bom-alpha:1.15.0-alpha")

    }
}

micronautBuild {
    binaryCompatibility.enabled.set(version != "4.0.0-SNAPSHOT")
}
