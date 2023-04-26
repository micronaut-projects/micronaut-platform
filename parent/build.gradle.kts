plugins {
    id("io.micronaut.build.internal.maven-pom")
}

pom {
    catalogPropertyPrefix.set("parent")
    includeBomPropertiesStringListProperty.set(listOf(
        libs.boms.micronaut.grpc.get().toString(),
        libs.boms.micronaut.picocli.get().toString()
    ))
}
