package io.micronaut.build.internal

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

data class MigrationImpactEntry(
    val releaseLine: String,
    val surface: String,
    val id: String,
    val acceptedRegressionKind: String,
    val changeType: String,
    val sourceOrReason: String,
    val guidance: String,
    val document: Boolean,
    val documentationSuppressionReason: String
)

data class AcceptedRegression(
    val kind: String,
    val id: String
)

object PlatformMigrationImpact {
    private val surfaces = setOf("version-alias", "library-alias", "bom-alias", "parent-property", "other")
    private val regressionKinds = setOf("version", "library", "none")
    private val changeTypes = setOf(
        "removed",
        "temporarily-absent",
        "moved-or-renamed",
        "version-regression",
        "library-regression",
        "compatibility-regression",
        "deprecated-removal"
    )
    private val surfaceLabels = mapOf(
        "version-alias" to "Version alias",
        "library-alias" to "Library alias",
        "bom-alias" to "BOM alias",
        "parent-property" to "Parent property",
        "other" to "Other"
    )
    private val changeTypeLabels = mapOf(
        "removed" to "Removed",
        "temporarily-absent" to "Temporarily absent",
        "moved-or-renamed" to "Moved or renamed",
        "version-regression" to "Version regression",
        "library-regression" to "Library regression",
        "compatibility-regression" to "Compatibility regression",
        "deprecated-removal" to "Deprecated removal"
    )
    private val header = listOf(
        "releaseLine",
        "surface",
        "id",
        "acceptedRegressionKind",
        "changeType",
        "sourceOrReason",
        "guidance",
        "document",
        "documentationSuppressionReason"
    )
    private val addRegex = Regex("""accepted(Version|Library)Regressions\.add\("([^"]+)"\)""")
    private val addAllRegex = Regex(
        """accepted(Version|Library)Regressions\.addAll\((.*?)\)""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )
    private val acceptedCallRegex = Regex("""accepted(?:Version|Library)Regressions\.add(?:All)?\(""")
    private val stringArgumentRegex = Regex(""""([^"]+)"""")

    fun parseMetadata(metadataFile: File): List<MigrationImpactEntry> {
        val lines = metadataFile.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        if (lines.isEmpty()) {
            throw GradleException("Migration impact metadata file is empty: ${metadataFile.path}")
        }
        val actualHeader = lines.first().split('\t')
        if (actualHeader != header) {
            throw GradleException(
                "Unexpected migration impact metadata header in ${metadataFile.path}. " +
                    "Expected ${header.joinToString("\\t")}"
            )
        }
        return lines.drop(1).mapIndexed { index, line ->
            val splitFields = line.split('\t')
            val fields = when (splitFields.size) {
                header.size -> splitFields
                header.size - 1 -> splitFields + ""
                else -> splitFields
            }
            val lineNumber = index + 2
            if (fields.size != header.size) {
                throw GradleException("Expected ${header.size} tab-separated fields at ${metadataFile.path}:$lineNumber")
            }
            MigrationImpactEntry(
                releaseLine = fields[0],
                surface = fields[1],
                id = fields[2],
                acceptedRegressionKind = fields[3],
                changeType = fields[4],
                sourceOrReason = fields[5],
                guidance = fields[6],
                document = when (fields[7]) {
                    "true" -> true
                    "false" -> false
                    else -> throw GradleException("document must be true or false at ${metadataFile.path}:$lineNumber")
                },
                documentationSuppressionReason = fields[8]
            )
        }
    }

    fun scanAcceptedRegressions(buildFile: File): Set<AcceptedRegression> {
        val text = buildFile.readText()
        val simpleCalls = addRegex.findAll(text).map { match ->
            AcceptedRegression(match.groupValues[1].lowercase(), match.groupValues[2])
        }
        val addAllCalls = addAllRegex.findAll(text).flatMap { match ->
            val kind = match.groupValues[1].lowercase()
            stringArgumentRegex.findAll(match.groupValues[2]).map { AcceptedRegression(kind, it.groupValues[1]) }
        }
        val recognizedCallCount = addRegex.findAll(text).count() + addAllRegex.findAll(text).count()
        val acceptedCallCount = acceptedCallRegex.findAll(text).count()
        if (recognizedCallCount != acceptedCallCount) {
            throw GradleException(
                "Unsupported accepted regression declaration in ${buildFile.path}. " +
                    "Use acceptedVersionRegressions.add(\"alias\"), acceptedLibraryRegressions.add(\"alias\"), or addAll(\"alias\", ...)."
            )
        }
        return (simpleCalls + addAllCalls).toSet()
    }

    fun validate(metadataFile: File, buildFile: File, appendixFile: File) {
        val entries = parseMetadata(metadataFile)
        validateEntries(entries, metadataFile)
        validateCoverage(entries, scanAcceptedRegressions(buildFile), metadataFile, buildFile)
        val expected = normalizeLineEndings(generateAsciiDoc(entries))
        if (!appendixFile.exists()) {
            throw GradleException("Generated appendix is missing: ${appendixFile.path}")
        }
        val actual = normalizeLineEndings(appendixFile.readText())
        if (actual != expected) {
            throw GradleException(
                "Generated appendix is stale: ${appendixFile.path}. " +
                    "Run :micronaut-platform:updatePlatformMigrationImpactAppendix."
            )
        }
    }

    fun generateAsciiDoc(entries: List<MigrationImpactEntry>): String {
        val documented = entries.filter { it.document }
            .sortedWith(compareBy<MigrationImpactEntry> { it.releaseLine }.thenBy { it.surface }.thenBy { it.id })
        return buildString {
            appendLine("This appendix is generated from `platform/src/main/migration-impact/platform-5.tsv`.")
            appendLine("It lists accepted Platform 5 BOM and version-catalog regressions that may affect application upgrades.")
            appendLine("The managed dependency matrix remains the reference for dependencies currently managed by the platform.")
            appendLine("Maintainers refresh this page with `./gradlew :micronaut-platform:updatePlatformMigrationImpactAppendix` and verify it with `./gradlew :micronaut-platform:verifyPlatformMigrationImpactAppendix`.")
            appendLine()
            appendLine("[[platform-5-migration-impact]]")
            appendLine("== Platform 5 Migration Impact")
            appendLine()
            appendLine("[cols=\"1,1,1,1,2,2\",options=\"header\"]")
            appendLine("|===")
            appendLine("|Artifact or alias |Surface |Change type |Release line |Source or reason |Guidance")
            documented.forEach { entry ->
                appendLine(
                    "|`${escapeCell(entry.id)}` |${escapeCell(displaySurface(entry.surface))} " +
                        "|${escapeCell(displayChangeType(entry.changeType))} |${escapeCell(entry.releaseLine)} " +
                        "|${escapeCell(entry.sourceOrReason)} |${escapeCell(entry.guidance)}"
                )
            }
            appendLine("|===")
        }
    }

    fun validateEntries(entries: List<MigrationImpactEntry>, metadataFile: File) {
        val duplicates = entries.groupBy { it.acceptedRegressionKind to it.id }
            .filterKeys { it.first != "none" }
            .filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            throw GradleException("Duplicate migration impact metadata entries in ${metadataFile.path}: ${duplicates.keys}")
        }
        entries.forEach { entry ->
            if (entry.releaseLine.isBlank() || entry.id.isBlank() || entry.sourceOrReason.isBlank() || entry.guidance.isBlank()) {
                throw GradleException("Migration impact metadata fields must not be blank for ${entry.id}")
            }
            if (entry.surface !in surfaces) {
                throw GradleException("Unsupported migration impact surface '${entry.surface}' for ${entry.id}")
            }
            if (entry.acceptedRegressionKind !in regressionKinds) {
                throw GradleException("Unsupported accepted regression kind '${entry.acceptedRegressionKind}' for ${entry.id}")
            }
            if (entry.changeType !in changeTypes) {
                throw GradleException("Unsupported migration impact change type '${entry.changeType}' for ${entry.id}")
            }
            if (!entry.document && entry.documentationSuppressionReason.isBlank()) {
                throw GradleException("document=false requires documentationSuppressionReason for ${entry.id}")
            }
            if (entry.document && entry.documentationSuppressionReason.isNotBlank()) {
                throw GradleException("document=true must not set documentationSuppressionReason for ${entry.id}")
            }
        }
    }

    fun validateCoverage(
        entries: List<MigrationImpactEntry>,
        acceptedRegressions: Set<AcceptedRegression>,
        metadataFile: File,
        buildFile: File
    ) {
        val documentedRegressions = entries
            .filter { it.acceptedRegressionKind != "none" }
            .map { AcceptedRegression(it.acceptedRegressionKind, it.id) }
            .toSet()
        val missing = acceptedRegressions - documentedRegressions
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Accepted regressions in ${buildFile.path} are missing migration-impact metadata in ${metadataFile.path}: " +
                    missing.sortedWith(compareBy<AcceptedRegression> { it.kind }.thenBy { it.id })
            )
        }
        val stale = documentedRegressions - acceptedRegressions
        if (stale.isNotEmpty()) {
            throw GradleException(
                "Migration-impact metadata entries no longer match accepted regressions in ${buildFile.path}: " +
                    stale.sortedWith(compareBy<AcceptedRegression> { it.kind }.thenBy { it.id })
            )
        }
    }

    private fun displaySurface(surface: String): String = surfaceLabels.getValue(surface)

    private fun displayChangeType(changeType: String): String = changeTypeLabels.getValue(changeType)

    private fun escapeCell(value: String): String = value
        .replace("\\", "\\\\")
        .replace("|", "\\|")

    fun normalizeLineEndings(value: String): String = value
        .replace("\r\n", "\n")
        .replace("\r", "\n")
}

abstract class UpdatePlatformMigrationImpactAppendix : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val metadataFile: RegularFileProperty

    @get:OutputFile
    abstract val appendixFile: RegularFileProperty

    @TaskAction
    fun update() {
        val entries = PlatformMigrationImpact.parseMetadata(metadataFile.get().asFile)
        PlatformMigrationImpact.validateEntries(entries, metadataFile.get().asFile)
        appendixFile.get().asFile.writeText(
            PlatformMigrationImpact.normalizeLineEndings(PlatformMigrationImpact.generateAsciiDoc(entries))
        )
    }
}

abstract class VerifyPlatformMigrationImpactAppendix : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val metadataFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appendixFile: RegularFileProperty

    @TaskAction
    fun verify() {
        PlatformMigrationImpact.validate(
            metadataFile.get().asFile,
            buildFile.get().asFile,
            appendixFile.get().asFile
        )
    }
}
