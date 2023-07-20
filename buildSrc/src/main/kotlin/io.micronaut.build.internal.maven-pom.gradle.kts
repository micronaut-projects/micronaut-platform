import io.micronaut.build.internal.PomExtension

plugins {
    id("java-platform")
    id("io.micronaut.build.internal.publishing")
}

val pom = extensions.create<PomExtension>("pom")


publishing {
    publications {
        create<MavenPublication>("parent") {
            from(components["javaPlatform"])
        }
    }
}

tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

val generatePomFile = tasks.named("generatePomFileForParentPublication", GenerateMavenPom::class)

val rewritePomFile = tasks.register<io.micronaut.build.internal.RewritePom>("rewritePomFile") {
    template.convention(layout.projectDirectory.file("src/pom-template.xml"))
    outputFile.fileProvider(generatePomFile.map(GenerateMavenPom::getDestination))
    tokenReplacements.convention(pom.tokenReplacements)
    pomProperties.convention(pom.pomProperties)
    includeBomPropertiesStringListProperty.convention(pom.includeBomPropertiesStringListProperty)
}

generatePomFile.configure {
    finalizedBy(rewritePomFile)
}

afterEvaluate {
    val publishingTasks = setOf(
        "publishParentPublicationToMavenLocal",
        "publishParentPublicationToBuildRepository",
        "publishParentPublicationToSonatypeRepository",
        "publishParentPublicationToExternalRepository",
        "signParentPublication")
    tasks.configureEach {
        if (name in publishingTasks)
            dependsOn(rewritePomFile)
    }

    val libsCatalog = extensions.getByType(VersionCatalogsExtension::class).named("libs")
    val prefix = pom.catalogPropertyPrefix.get() + "."
    libsCatalog.versionAliases
        .filter { it.startsWith(prefix) }
        .forEach { alias ->
            val requiredVersion = libsCatalog.findVersion(alias).get().requiredVersion
            pom.pomProperties.put(alias.substring(prefix.length).replace(".", "-"), requiredVersion)
        }
    pom.tokenReplacements.put("version", version.toString())
}

tasks.withType<PublishToMavenRepository>().configureEach {
    enabled = !name.startsWith("publishMaven")
}

plugins.withId("signing") {
    extensions.getByType(SigningExtension::class.java).sign(publishing.publications["parent"])
}
