package io.micronaut.build.internal

import org.gradle.api.GradleException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlatformMigrationImpactTest {
    @Test
    fun `scans accepted regressions from add and addAll declarations`() {
        val buildFile = tempFile(
            """
            suppressions {
                acceptedVersionRegressions.add("reactor-netty")
                acceptedLibraryRegressions.add("reactor-netty-http")
                acceptedVersionRegressions.addAll(
                    "microstream",
                    "micronaut-microstream"
                )
                acceptedLibraryRegressions.addAll(
                    "micronaut-rxjava2",
                    "micronaut-rxjava2-bom",
                )
            }
            """.trimIndent()
        )

        assertEquals(
            setOf(
                AcceptedRegression("version", "reactor-netty"),
                AcceptedRegression("library", "reactor-netty-http"),
                AcceptedRegression("version", "microstream"),
                AcceptedRegression("version", "micronaut-microstream"),
                AcceptedRegression("library", "micronaut-rxjava2"),
                AcceptedRegression("library", "micronaut-rxjava2-bom")
            ),
            PlatformMigrationImpact.scanAcceptedRegressions(buildFile)
        )
    }

    @Test
    fun `coverage fails when accepted regression has no metadata`() {
        val error = assertFailsWith<GradleException> {
            PlatformMigrationImpact.validateCoverage(
                entries = listOf(
                    MigrationImpactEntry(
                        releaseLine = "5.0.x",
                        surface = "version-alias",
                        id = "reactor-netty",
                        acceptedRegressionKind = "version",
                        changeType = "removed",
                        sourceOrReason = "Removed upstream",
                        guidance = "No direct replacement recorded",
                        document = true,
                        documentationSuppressionReason = ""
                    )
                ),
                acceptedRegressions = setOf(
                    AcceptedRegression("version", "reactor-netty"),
                    AcceptedRegression("library", "reactor-netty-http")
                ),
                metadataFile = File("metadata.tsv"),
                buildFile = File("platform/build.gradle.kts")
            )
        }

        assertTrue(error.message.orEmpty().contains("reactor-netty-http"))
    }

    @Test
    fun `parses documented metadata without trailing suppression column`() {
        val metadata = tempFile(
            """
            releaseLine	surface	id	acceptedRegressionKind	changeType	sourceOrReason	guidance	document	documentationSuppressionReason
            5.0.x	library-alias	reactor-netty-http	library	removed	Removed by upstream module	No direct replacement recorded	true
            """.trimIndent()
        )

        val entries = PlatformMigrationImpact.parseMetadata(metadata)

        assertEquals("", entries.single().documentationSuppressionReason)
    }

    @Test
    fun `generates asciidoc appendix from documented metadata`() {
        val asciidoc = PlatformMigrationImpact.generateAsciiDoc(
            listOf(
                MigrationImpactEntry(
                    releaseLine = "5.0.x",
                    surface = "library-alias",
                    id = "reactor-netty-http",
                    acceptedRegressionKind = "library",
                    changeType = "removed",
                    sourceOrReason = "Removed by upstream module",
                    guidance = "No direct replacement recorded",
                    document = true,
                    documentationSuppressionReason = ""
                )
            )
        )

        assertTrue(asciidoc.contains("== Platform 5 Migration Impact"))
        assertTrue(asciidoc.contains("`reactor-netty-http`"))
        assertTrue(asciidoc.contains("Removed by upstream module"))
    }

    private fun tempFile(text: String): File = kotlin.io.path.createTempFile().toFile().also { it.writeText(text) }
}
