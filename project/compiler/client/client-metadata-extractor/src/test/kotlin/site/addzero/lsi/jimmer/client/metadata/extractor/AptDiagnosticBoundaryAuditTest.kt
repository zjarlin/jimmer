package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class AptDiagnosticBoundaryAuditTest {

    @Test
    fun `apt local diagnostic duplicates stay deleted`() {
        assertFalse(
            Files.exists(
                CompilerAuditTestSupport.repoRoot.resolve(
                    "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/MetaException.java"
                )
            )
        )
        assertFalse(
            Files.exists(
                CompilerAuditTestSupport.repoRoot.resolve(
                    "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/GeneratorException.java"
                )
            )
        )
    }

    @Test
    fun `apt boundary routes diagnostics through lsi adapters`() {
        val helper = CompilerAuditTestSupport.sourceOf(
            "lib/lsi/lsi-apt/src/main/kotlin/site/addzero/lsi/apt/diagnostic/AptLsiDiagnostics.kt"
        )
        assertTrue(helper.contains("fun metaException(element: Element, reason: String)"), helper)
        assertTrue(helper.contains("fun generatorException(message: String, cause: Throwable)"), helper)

        val jimmerProcessor = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/JimmerProcessor.java"
        )
        assertTrue(jimmerProcessor.contains("import site.addzero.lsi.diagnostic.MetaException;"), jimmerProcessor)
        assertFalse(jimmerProcessor.contains("ex.getElement()"), jimmerProcessor)

        val aptSemanticsExt = CompilerAuditTestSupport.sourceOf(
            "lib/lsi/lsi-apt/src/main/kotlin/site/addzero/lsi/apt/clazz/AptLsiClassSemanticsExt.kt"
        )
        assertTrue(aptSemanticsExt.contains("AptLsiDiagnostics.metaException"), aptSemanticsExt)

        val clientProcessor = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ClientProcessor.java"
        )
        assertTrue(clientProcessor.contains("AptLsiDiagnostics.generatorException"), clientProcessor)
    }
}
