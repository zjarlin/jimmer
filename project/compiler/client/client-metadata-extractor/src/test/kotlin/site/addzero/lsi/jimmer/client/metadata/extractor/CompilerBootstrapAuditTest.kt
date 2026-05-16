package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompilerBootstrapAuditTest {

    @Test
    fun `ksp bootstrap stays focused on context wiring and processor orchestration`() {
        val source = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-ksp/src/main/kotlin/org/babyfish/jimmer/ksp/JimmerProcessor.kt"
        )

        CompilerAuditTestSupport.assertContainsAll(
            source,
            listOf(
                "KspLsiContext.init(environment)",
                "KspLsiContext.resetRound(resolver)",
                "val lsiResolver = resolver.toLsiResolver()",
                "lsiFiler = environment.codeGenerator.toLsiFiler()",
                "KspLsiFile(resolver, firstFile)",
                "sourceAnchorFilePathProvider = sourceAnchorFilePathProvider@{",
                "ServiceLoader.load(ProcessorSpi::class.java",
            ),
            "KSP JimmerProcessor",
        )

        val forbidden = listOf(
            "DtoGenerator(",
            "ClientSchemaMetadataGenerator(",
            "ErrorMetadataGenerator(",
            "TxMetadataGenerator(",
            "TypedTupleMetadataGenerator(",
            "toGeneratedArtifacts(",
            "renderKotlinSource(",
            "renderJavaSource(",
        )
        CompilerAuditTestSupport.assertContainsNone(source, forbidden, "KSP JimmerProcessor")
    }

    @Test
    fun `apt bootstrap stays focused on round lifecycle and processor orchestration`() {
        val source = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/JimmerProcessor.java"
        )

        CompilerAuditTestSupport.assertContainsAll(
            source,
            listOf(
                "AptLsiContext.INSTANCE.init(processingEnv, null);",
                "AptLsiContext.INSTANCE.resetRound(roundEnv, null);",
                "if (runModelPhase(roundEnv)) {",
                "runToolPhase();",
                "private boolean runModelPhase(RoundEnvironment roundEnv) {",
                "private void runToolPhase() {",
                "private void prepareToolPhase(RoundEnvironment roundEnv) {",
                "findSourceAnchorFilePath()",
                "new ImmutableProcessor(buddyIgnoreResourceGeneration).process();",
                "new EntryProcessor(",
                "new ErrorProcessor(checkedException).process();",
                "new DtoProcessor(",
                "new TxProcessor(buddyIgnoreResourceGeneration).process();",
                "new ExportDocProcessor().process();",
                "Context.INSTANCE.snapshotAllTypeNames();",
                "Context.INSTANCE.setDelayedTupleTypeNames(",
                "new TypedTupleProcessor().process();",
                "new ClientProcessor(clientExplicitApi).process();",
            ),
            "APT JimmerProcessor",
        )

        val forbidden = listOf(
            "new site.addzero.lsi.jimmer.dto.DtoGenerator(",
            "new ClientSchemaMetadataGenerator(",
            "new ErrorMetadataGenerator(",
            "new TxMetadataGenerator(",
            "new TypedTupleMetadataGenerator(",
            "ImmutableGeneratedArtifactsKt.toGeneratedArtifacts(",
            "ImmutableProcessorSupport.generateSharedArtifacts(",
            "ImmutableProcessorSupport.generateAptOnlyArtifacts(",
            "ImmutableProcessorSupport.generateAptEntityTableOutput(",
            "renderKotlinSource(",
            "renderJavaSource(",
        )
        CompilerAuditTestSupport.assertContainsNone(source, forbidden, "APT JimmerProcessor")

        val modelPhaseStart = source.indexOf("private boolean runModelPhase(RoundEnvironment roundEnv) {")
        val toolPhaseStart = source.indexOf("private void runToolPhase() {")
        val prepareToolPhaseStart = source.indexOf("private void prepareToolPhase(RoundEnvironment roundEnv) {")
        assertTrue(modelPhaseStart >= 0, source)
        assertTrue(toolPhaseStart > modelPhaseStart, source)
        assertTrue(prepareToolPhaseStart > toolPhaseStart, source)

        val modelPhase = source.substring(modelPhaseStart, toolPhaseStart)
        assertFalse(modelPhase.contains("new ExportDocProcessor().process();"), modelPhase)

        val toolPhase = source.substring(toolPhaseStart, prepareToolPhaseStart)
        val tupleIndex = toolPhase.indexOf("new TypedTupleProcessor().process();")
        val exportDocIndex = toolPhase.indexOf("new ExportDocProcessor().process();")
        val clientIndex = toolPhase.indexOf("new ClientProcessor(clientExplicitApi).process();")
        assertTrue(tupleIndex >= 0, toolPhase)
        assertTrue(exportDocIndex > tupleIndex, toolPhase)
        assertTrue(clientIndex > exportDocIndex, toolPhase)
    }
}
