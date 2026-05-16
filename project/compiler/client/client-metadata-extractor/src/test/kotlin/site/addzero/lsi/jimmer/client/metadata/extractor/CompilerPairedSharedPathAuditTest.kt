package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Test

class CompilerPairedSharedPathAuditTest {

    @Test
    fun `dto paired processors stay on shared lsi generator path`() {
        val ksp = CompilerAuditTestSupport.sourceOf(
            "project/compiler/dto/jimmer-ksp-dto/src/main/kotlin/org/babyfish/jimmer/ksp/dto/DtoProcessor.kt"
        )
        CompilerAuditTestSupport.assertContainsAll(
            ksp,
            listOf(
                "import site.addzero.lsi.jimmer.dto.DtoProcessorSupport",
                "DtoProcessorSupport.generateFileSpecs(",
                "Context.lsiFiler.createSourceFile(fileSpec)",
            ),
            "KSP DTO processor",
        )

        val apt = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/dto/DtoProcessor.java"
        )
        CompilerAuditTestSupport.assertContainsAll(
            apt,
            listOf(
                "DtoProcessorSupport.collectDtoFiles(",
                "DtoProcessorSupport.generateFileSpecs(",
                "Context.INSTANCE.getLsiFiler().createSourceFile(fileSpec);",
            ),
            "APT DTO processor",
        )
    }

    @Test
    fun `client paired processors stay on shared lsi extractor and generator path`() {
        val ksp = CompilerAuditTestSupport.sourceOf(
            "project/compiler/client/jimmer-ksp-client/src/main/kotlin/org/babyfish/jimmer/ksp/client/ClientProcessor.kt"
        )
        CompilerAuditTestSupport.assertContainsAll(
            ksp,
            listOf(
                "import site.addzero.lsi.jimmer.client.metadata.generator.ClientProcessorSupport",
                "ClientProcessorSupport.collectClientSchemaServiceTypeNames(",
                "ClientProcessorSupport.generateClientSchemaArtifact(",
                "convertedLsiTypeNameOf = Context::convertedLsiTypeNameOf",
                "ctx.lsiFiler.createResourceFile(artifact.path, artifact.content)",
            ),
            "KSP client processor",
        )
        CompilerAuditTestSupport.assertContainsNone(
            ksp,
            listOf("Context.typeOf(owner)"),
            "KSP client processor",
        )

        val apt = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ClientProcessor.java"
        )
        CompilerAuditTestSupport.assertContainsAll(
            apt,
            listOf(
                "ClientProcessorSupport.collectClientSchemaServiceTypeNames(",
                "ClientProcessorSupport.generateClientSchemaArtifact(",
                "GeneratedResourceArtifact artifact = ClientProcessorSupport.generateClientSchemaArtifact(",
                "Context.INSTANCE::convertedLsiTypeNameOf",
                "Context.INSTANCE.getLsiFiler().overwriteResourceFile(artifact.getPath(), artifact.getContent());",
            ),
            "APT client processor",
        )
        CompilerAuditTestSupport.assertContainsNone(
            apt,
            listOf(".typeOf(owner)"),
            "APT client processor",
        )
    }

    @Test
    fun `export doc paired processors stay on shared lsi support path`() {
        val ksp = CompilerAuditTestSupport.sourceOf(
            "project/compiler/client/jimmer-ksp-client/src/main/kotlin/org/babyfish/jimmer/ksp/client/ExportDocProcessor.kt"
        )
        CompilerAuditTestSupport.assertContainsAll(
            ksp,
            listOf(
                "import site.addzero.lsi.jimmer.client.metadata.generator.ClientProcessorSupport",
                "ClientProcessorSupport.collectExportDocTypeNames(ctx.lsiResolver)",
                "ClientProcessorSupport.generateExportDocArtifact(",
                "ctx.lsiFiler.createResourceFile(artifact.path, artifact.content)",
            ),
            "KSP export-doc processor",
        )

        val apt = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ExportDocProcessor.java"
        )
        CompilerAuditTestSupport.assertContainsAll(
            apt,
            listOf(
                "import site.addzero.lsi.jimmer.client.metadata.generator.ClientProcessorSupport;",
                "ClientProcessorSupport.collectExportDocTypeNames(resolver)",
                "GeneratedResourceArtifact artifact = ClientProcessorSupport.generateExportDocArtifact(resolver, typeNames);",
                "Context.INSTANCE.getLsiFiler().mergePropertiesResourceFile(",
                "ClientProcessorSupport.EXPORT_DOC_RESOURCE_COMMENT",
            ),
            "APT export-doc processor",
        )
        CompilerAuditTestSupport.assertContainsNone(
            apt,
            listOf("ExportDocResourceGenerator"),
            "APT export-doc processor",
        )
    }

    @Test
    fun `error paired processors stay on shared metadata path`() {
        val ksp = CompilerAuditTestSupport.sourceOf(
            "project/compiler/error/jimmer-ksp-error/src/main/kotlin/org/babyfish/jimmer/lsi/error/ErrorProcessor.kt"
        )
        CompilerAuditTestSupport.assertContainsAll(
            ksp,
            listOf(
                "import site.addzero.lsi.jimmer.error.metadata.generator.ErrorProcessorSupport",
                "ErrorProcessorSupport.collectNewTypes(",
                "ErrorProcessorSupport.generateFileSpecs(",
                "ctx.lsiFiler.createSourceFile(fileSpec)",
            ),
            "KSP error processor",
        )
        CompilerAuditTestSupport.assertContainsNone(
            ksp,
            listOf(
                "ErrorMetadataExtractor()",
                "ErrorMetadataGenerator()",
            ),
            "KSP error processor",
        )

        val apt = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/error/ErrorProcessor.java"
        )
        CompilerAuditTestSupport.assertContainsAll(
            apt,
            listOf(
                "import site.addzero.lsi.jimmer.error.metadata.generator.ErrorProcessorSupport;",
                "ErrorProcessorSupport.collectNewTypes(",
                "ErrorProcessorSupport.generateFileSpecs(",
                ".createSourceFile(fileSpec);",
            ),
            "APT error processor",
        )
        CompilerAuditTestSupport.assertContainsNone(
            apt,
            listOf(
                "new ErrorMetadataExtractor()",
                "new ErrorMetadataGenerator()",
            ),
            "APT error processor",
        )
    }

    @Test
    fun `tx paired processors stay on shared metadata path`() {
        val ksp = CompilerAuditTestSupport.sourceOf(
            "project/compiler/transactional/jimmer-ksp-transactional/src/main/kotlin/org/babyfish/jimmer/ksp/transactional/TxProcessor.kt"
        )
        CompilerAuditTestSupport.assertContainsAll(
            ksp,
            listOf(
                "import site.addzero.lsi.jimmer.transactional.metadata.generator.TxProcessorSupport",
                "TxProcessorSupport.collectNewTypes(ctx.lsiResolver)",
                "TxProcessorSupport.generateFileSpecs(",
                "ctx.lsiFiler.createSourceFile(fileSpec)",
            ),
            "KSP tx processor",
        )
        CompilerAuditTestSupport.assertContainsNone(
            ksp,
            listOf(
                "TxMetadataExtractor()",
                "TxMetadataGenerator()",
            ),
            "KSP tx processor",
        )

        val apt = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/transactional/TxProcessor.java"
        )
        CompilerAuditTestSupport.assertContainsAll(
            apt,
            listOf(
                "import site.addzero.lsi.jimmer.transactional.metadata.generator.TxProcessorSupport;",
                "TxProcessorSupport.collectNewTypes(Context.INSTANCE.getLsiResolver())",
                "TxProcessorSupport.generateFileSpecs(",
                ".createSourceFile(fileSpec);",
            ),
            "APT tx processor",
        )
        CompilerAuditTestSupport.assertContainsNone(
            apt,
            listOf(
                "new TxMetadataExtractor()",
                "new TxMetadataGenerator()",
            ),
            "APT tx processor",
        )
    }

    @Test
    fun `tuple paired processors stay on shared metadata path`() {
        val ksp = CompilerAuditTestSupport.sourceOf(
            "project/compiler/tuple/jimmer-ksp-tuple/src/main/kotlin/org/babyfish/jimmer/ksp/tuple/TypedTupleProcessor.kt"
        )
        CompilerAuditTestSupport.assertContainsAll(
            ksp,
            listOf(
                "import site.addzero.lsi.jimmer.tuple.metadata.generator.TypedTupleProcessorSupport",
                "TypedTupleProcessorSupport.collectRoundTypes(",
                "TypedTupleProcessorSupport.generateFileSpecs(",
                "ctx.lsiFiler.createSourceFile(fileSpec)",
            ),
            "KSP tuple processor",
        )
        CompilerAuditTestSupport.assertContainsNone(
            ksp,
            listOf(
                "TypedTupleMetadataExtractor()",
                "TypedTupleMetadataGenerator()",
            ),
            "KSP tuple processor",
        )

        val apt = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/tuple/TypedTupleProcessor.java"
        )
        CompilerAuditTestSupport.assertContainsAll(
            apt,
            listOf(
                "import site.addzero.lsi.jimmer.tuple.metadata.generator.TypedTupleProcessorSupport;",
                "TypedTupleProcessorSupport.collectRoundTypes(",
                "TypedTupleProcessorSupport.generateFileSpecs(",
                "Context.INSTANCE.getLsiFiler().createSourceFile(fileSpec);",
            ),
            "APT tuple processor",
        )
        CompilerAuditTestSupport.assertContainsNone(
            apt,
            listOf(
                "new TypedTupleMetadataExtractor()",
                "new TypedTupleMetadataGenerator()",
            ),
            "APT tuple processor",
        )
    }

    @Test
    fun `immutable paired processors stay on shared artifact assembly path`() {
        val ksp = CompilerAuditTestSupport.sourceOf(
            "project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/ImmutableProcessor.kt"
        )
        CompilerAuditTestSupport.assertContainsAll(
            ksp,
            listOf(
                "import site.addzero.lsi.jimmer.immutable.metadata.extractor.ImmutableCollectedSourceAccumulator",
                "import site.addzero.lsi.jimmer.immutable.metadata.generator.ImmutableProcessorSupport",
                "ImmutableProcessorSupport.generateKspOutput(",
                "generatedOutput.sourceFileSpecs.forEach(::writeFileSpec)",
            ),
            "KSP immutable processor",
        )

        val apt = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/ImmutableProcessor.java"
        )
        CompilerAuditTestSupport.assertContainsAll(
            apt,
            listOf(
                "ImmutableProcessorSupport.generateAptOutput(",
                "for (LsiFileSpec fileSpec : generatedOutput.getSourceFileSpecs()) {",
                "Context.INSTANCE.getLsiFiler().createSourceFile(fileSpec);",
            ),
            "APT immutable processor",
        )
    }

}
