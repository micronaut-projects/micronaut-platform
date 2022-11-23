plugins {
    id("io.micronaut.build.internal.maven-pom")
}

// Intentionally set to an "old" groupId so that
// the parent POM is published at the same GAV coordinates
// as the Micronaut 3 version
group = "io.micronaut"

pom {
    catalogPropertyPrefix.set("parent")
    includeBomPropertiesStringListProperty.set(listOf(
        libs.boms.micronaut.grpc.get().toString(),
        libs.boms.micronaut.picocli.get().toString()
    ))
}
