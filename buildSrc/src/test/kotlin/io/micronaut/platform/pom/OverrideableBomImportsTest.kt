package io.micronaut.platform.pom

import java.nio.file.Files
import kotlin.test.Test
import org.gradle.testfixtures.ProjectBuilder

class OverrideableBomImportsTest {

    @Test
    fun `checker accepts multiple allowlisted external bom imports`() {
        val pomFile = Files.createTempFile("overrideable-bom-imports", ".xml").toFile()
        pomFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <properties>
                <netty.version>4.2.12.Final</netty.version>
                <example.version>1.2.3</example.version>
              </properties>
              <dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>io.netty</groupId>
                    <artifactId>netty-bom</artifactId>
                    <version>${'$'}{netty.version}</version>
                    <type>pom</type>
                    <scope>import</scope>
                  </dependency>
                  <dependency>
                    <groupId>com.example</groupId>
                    <artifactId>example-bom</artifactId>
                    <version>${'$'}{example.version}</version>
                    <type>pom</type>
                    <scope>import</scope>
                  </dependency>
                  <dependency>
                    <groupId>io.micronaut</groupId>
                    <artifactId>micronaut-core-bom</artifactId>
                    <version>${'$'}{micronaut.core.version}</version>
                    <type>pom</type>
                    <scope>import</scope>
                  </dependency>
                </dependencies>
              </dependencyManagement>
            </project>
            """.trimIndent()
        )

        val project = ProjectBuilder.builder().build()
        project.checkOverrideableBomImports(
            pomFile,
            listOf(
                OverrideableBomImport(
                    propertyName = "netty.version",
                    importedBomGroupId = "io.netty",
                    importedBomArtifactId = "netty-bom",
                    owningBomGroupId = "io.micronaut",
                    owningBomArtifactId = "micronaut-core-bom",
                    owningBomVersionProperty = "micronaut.core.version"
                ),
                OverrideableBomImport(
                    propertyName = "example.version",
                    importedBomGroupId = "com.example",
                    importedBomArtifactId = "example-bom",
                    owningBomGroupId = "io.micronaut",
                    owningBomArtifactId = "micronaut-core-bom",
                    owningBomVersionProperty = "micronaut.core.version"
                )
            )
        )
    }
}
