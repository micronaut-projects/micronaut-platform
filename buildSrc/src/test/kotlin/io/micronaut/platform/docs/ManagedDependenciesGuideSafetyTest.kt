package io.micronaut.platform.docs

import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.StringReader
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManagedDependenciesGuideSafetyTest {

    @Test
    fun secureXmlParserRejectsDoctypeBeforeExpandingExternalEntities() {
        val sentinelFile = Files.createTempFile("managed-dependencies-xxe", ".txt")
        sentinelFile.writeText("XXE_SENTINEL_DEV318")
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE project [
              <!ENTITY xxe SYSTEM "${sentinelFile.toUri()}">
            ]>
            <project>
              <properties>
                <demo.version>&xxe;</demo.version>
              </properties>
            </project>
        """.trimIndent()

        val exception = assertFailsWith<SAXException> {
            ManagedDependenciesGuideSafety.secureDocumentBuilderFactory()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(xml)))
        }

        assertFalse(exception.message.orEmpty().contains("XXE_SENTINEL_DEV318"))
    }

    @Test
    fun generatedAsciiDocCellsNeutralizeMacrosAndRawHtml() {
        val escaped = ManagedDependenciesGuideSafety.escapeCell(
            "include::/etc/passwd[] pass:[<script>alert(1)</script>] image:http://example.test/a.png[] a|b {docdir}"
        )

        assertFalse(escaped.contains("include::"))
        assertFalse(escaped.contains("pass:"))
        assertFalse(escaped.contains("image:"))
        assertFalse(escaped.contains("{docdir}"))
        assertFalse(escaped.contains("<script", ignoreCase = true))
        assertTrue(escaped.contains("\\|"))
        assertTrue(escaped.contains("include&#58;&#58;"))
        assertTrue(escaped.contains("pass&#58;"))
        assertTrue(escaped.contains("&#123;docdir&#125;"))
        assertTrue(ManagedDependenciesGuideSafety.activeAsciiDocTokens(escaped).isEmpty())
    }

    @Test
    fun activeAsciiDocTokenDetectionIncludesAttributesAndConditionals() {
        val activeTokens = ManagedDependenciesGuideSafety.activeAsciiDocTokens(
            "{includedir}/demo ifdef::unsafe[] ifndef::unsafe[] ifeval::[1 == 1] endif::[]"
        )

        assertTrue(activeTokens.contains("{includedir}"))
        assertTrue(activeTokens.contains("ifdef::"))
        assertTrue(activeTokens.contains("ifndef::"))
        assertTrue(activeTokens.contains("ifeval::"))
        assertTrue(activeTokens.contains("endif::"))
    }
}
