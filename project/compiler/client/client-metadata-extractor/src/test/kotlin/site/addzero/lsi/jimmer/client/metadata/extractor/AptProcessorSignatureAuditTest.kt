package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AptProcessorSignatureAuditTest {

    @Test
    fun `apt processors only keep round environment where the round data is actually needed`() {
        val clientProcessor = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ClientProcessor.java"
        )
        assertFalse(clientProcessor.contains("RoundEnvironment"), clientProcessor)
        assertTrue(clientProcessor.contains("public void process()"), clientProcessor)

        val exportDocProcessor = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ExportDocProcessor.java"
        )
        assertFalse(exportDocProcessor.contains("RoundEnvironment"), exportDocProcessor)
        assertTrue(exportDocProcessor.contains("public void process()"), exportDocProcessor)
        assertTrue(exportDocProcessor.contains("public ExportDocProcessor()"), exportDocProcessor)

        val txProcessor = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/transactional/TxProcessor.java"
        )
        assertFalse(txProcessor.contains("RoundEnvironment"), txProcessor)
        assertTrue(txProcessor.contains("public void process()"), txProcessor)

        val errorProcessor = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/error/ErrorProcessor.java"
        )
        assertFalse(errorProcessor.contains("RoundEnvironment"), errorProcessor)
        assertTrue(errorProcessor.contains("public boolean process()"), errorProcessor)

        val tupleProcessor = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/tuple/TypedTupleProcessor.java"
        )
        assertFalse(tupleProcessor.contains("RoundEnvironment"), tupleProcessor)
        assertTrue(tupleProcessor.contains("public void process()"), tupleProcessor)

        val jimmerProcessor = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/JimmerProcessor.java"
        )
        assertTrue(
            jimmerProcessor.contains("new ImmutableProcessor(buddyIgnoreResourceGeneration).process()"),
            jimmerProcessor
        )
        assertTrue(jimmerProcessor.contains("new ErrorProcessor(checkedException).process()"), jimmerProcessor)
        assertTrue(jimmerProcessor.contains("new TxProcessor(buddyIgnoreResourceGeneration).process()"), jimmerProcessor)
        assertTrue(jimmerProcessor.contains("new ExportDocProcessor().process()"), jimmerProcessor)
        assertTrue(jimmerProcessor.contains("new ClientProcessor(clientExplicitApi).process()"), jimmerProcessor)
        assertTrue(jimmerProcessor.contains("new TypedTupleProcessor().process()"), jimmerProcessor)

        val toolPhase = jimmerProcessor.substring(
            jimmerProcessor.indexOf("private void runToolPhase() {"),
            jimmerProcessor.indexOf("private void prepareToolPhase(RoundEnvironment roundEnv) {")
        )
        assertTrue(toolPhase.contains("new TypedTupleProcessor().process();"), toolPhase)
        assertTrue(toolPhase.contains("new ExportDocProcessor().process();"), toolPhase)
        assertTrue(toolPhase.contains("new ClientProcessor(clientExplicitApi).process();"), toolPhase)
    }
}
