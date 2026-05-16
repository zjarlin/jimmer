package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Test

class ExportDocAptProcessorShellContractTest {

    @Test
    fun `apt export doc processor stays on shared lsi helper and resource filer path`() {
        val source = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ExportDocProcessor.java"
        )

        CompilerAuditTestSupport.assertContainsAll(
            source,
            listOf(
                "LsiResolver resolver = Context.INSTANCE.getLsiResolver();",
                "ClientProcessorSupport.collectExportDocTypeNames(resolver)",
                "GeneratedResourceArtifact artifact = ClientProcessorSupport.generateExportDocArtifact(resolver, typeNames);",
                "Context.INSTANCE.getLsiFiler().mergePropertiesResourceFile(",
                "ClientProcessorSupport.EXPORT_DOC_RESOURCE_COMMENT",
            ),
            "APT export-doc processor",
        )
    }

    @Test
    fun `apt export doc processor does not perform local poet rendering or bypass shared helper path`() {
        val source = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ExportDocProcessor.java"
        )

        CompilerAuditTestSupport.assertContainsNone(
            source,
            listOf(
                "renderJavaSource(",
                "renderKotlinSource(",
                "createSourceFile(",
                "overwriteGeneratedResourceFile(",
                "com.squareup.javapoet",
                "com.squareup.kotlinpoet",
                "JavaFile",
                "FileSpec",
                "KSType",
                "TypeElement",
                "RoundEnvironment",
                "LsiExportDocSupport.writeExportDoc(",
                "import site.addzero.lsi.jimmer.client.metadata.generator.ExportDocResourceGenerator;",
            ),
            "APT export-doc processor",
        )
    }
}
