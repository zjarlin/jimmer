package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Test

class CompilerPairedProcessorShellAuditTest {

    @Test
    fun `ksp paired shell processors stay on shared lsi orchestration`() {
        val cases = listOf(
            ProcessorShellAuditCase(
                relativePath = "project/compiler/dto/jimmer-ksp-dto/src/main/kotlin/org/babyfish/jimmer/ksp/dto/DtoProcessor.kt",
                owner = "KSP dto processor",
                requiredSnippets = listOf(
                    "import site.addzero.lsi.jimmer.dto.DtoProcessorSupport",
                    "DtoProcessorSupport.generateFileSpecs(",
                    "Context.lsiResolver",
                    "Context.lsiFiler.createSourceFile(fileSpec)",
                ),
                forbiddenSnippets = listOf(
                    "DtoGenerator(",
                    "InputBuilderGenerator(",
                    "SerializerGenerator(",
                ),
            ),
            ProcessorShellAuditCase(
                relativePath = "project/compiler/client/jimmer-ksp-client/src/main/kotlin/org/babyfish/jimmer/ksp/client/ClientProcessor.kt",
                owner = "KSP client processor",
                requiredSnippets = listOf(
                    "import site.addzero.lsi.jimmer.client.metadata.generator.ClientProcessorSupport",
                    "ClientProcessorSupport.collectClientSchemaServiceTypeNames(",
                    "ClientProcessorSupport.generateClientSchemaArtifact(",
                    "ctx.lsiFiler.createResourceFile(artifact.path, artifact.content)",
                ),
                forbiddenSnippets = listOf(
                    "ClientSchemaMetadataExtractor(",
                    "ClientSchemaMetadataGenerator(",
                ),
            ),
            ProcessorShellAuditCase(
                relativePath = "project/compiler/client/jimmer-ksp-client/src/main/kotlin/org/babyfish/jimmer/ksp/client/ExportDocProcessor.kt",
                owner = "KSP export-doc processor",
                requiredSnippets = listOf(
                    "import site.addzero.lsi.jimmer.client.metadata.generator.ClientProcessorSupport",
                    "ClientProcessorSupport.collectExportDocTypeNames(ctx.lsiResolver)",
                    "ClientProcessorSupport.generateExportDocArtifact(",
                    "ctx.lsiFiler.createResourceFile(artifact.path, artifact.content)",
                ),
                forbiddenSnippets = listOf(
                    "ExportDocResourceGenerator(",
                    "LsiExportDocSupport.resolveExportDocDeclarations(",
                ),
            ),
            ProcessorShellAuditCase(
                relativePath = "project/compiler/error/jimmer-ksp-error/src/main/kotlin/org/babyfish/jimmer/lsi/error/ErrorProcessor.kt",
                owner = "KSP error processor",
                requiredSnippets = listOf(
                    "import site.addzero.lsi.jimmer.error.metadata.generator.ErrorProcessorSupport",
                    "ErrorProcessorSupport.collectNewTypes(",
                    "ErrorProcessorSupport.generateFileSpecs(",
                    "ctx.lsiResolver",
                    "ctx.lsiFiler.createSourceFile(fileSpec)",
                ),
                forbiddenSnippets = listOf(
                    "ErrorMetadataExtractor()",
                    "ErrorMetadataGenerator()",
                ),
            ),
            ProcessorShellAuditCase(
                relativePath = "project/compiler/transactional/jimmer-ksp-transactional/src/main/kotlin/org/babyfish/jimmer/ksp/transactional/TxProcessor.kt",
                owner = "KSP transactional processor",
                requiredSnippets = listOf(
                    "import site.addzero.lsi.jimmer.transactional.metadata.generator.TxProcessorSupport",
                    "TxProcessorSupport.collectNewTypes(ctx.lsiResolver)",
                    "TxProcessorSupport.generateFileSpecs(collectedTypes.values)",
                    "ctx.lsiFiler.createSourceFile(fileSpec)",
                ),
                forbiddenSnippets = listOf(
                    "TxMetadataExtractor()",
                    "TxMetadataGenerator()",
                ),
            ),
            ProcessorShellAuditCase(
                relativePath = "project/compiler/tuple/jimmer-ksp-tuple/src/main/kotlin/org/babyfish/jimmer/ksp/tuple/TypedTupleProcessor.kt",
                owner = "KSP tuple processor",
                requiredSnippets = listOf(
                    "import site.addzero.lsi.jimmer.tuple.metadata.generator.TypedTupleProcessorSupport",
                    "TypedTupleProcessorSupport.collectRoundTypes(",
                    "TypedTupleProcessorSupport.generateFileSpecs(collectedTypes.values)",
                    "ctx.lsiFiler.createSourceFile(fileSpec)",
                ),
                forbiddenSnippets = listOf(
                    "TypedTupleMetadataExtractor()",
                    "TypedTupleMetadataGenerator()",
                ),
            ),
            ProcessorShellAuditCase(
                relativePath = "project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable/ImmutableProcessor.kt",
                owner = "KSP immutable processor",
                requiredSnippets = listOf(
                    "ImmutableProcessorSupport.collectRoundSources(",
                    "ImmutableProcessorSupport.resolveCollectedSources(",
                    "ImmutableProcessorSupport.generateKspOutput(",
                    "ImmutableProcessorSupport.notifyEntityMetaConsumers(",
                    "generatedOutput.sourceFileSpecs.forEach(::writeFileSpec)",
                    "generatedOutput.resourceArtifacts.forEach(::writeResourceArtifact)",
                ),
                forbiddenSnippets = listOf(
                    "private fun notifyEntityMetaConsumers(",
                    "private fun logResolvedImmutableTypes(",
                    "ServiceLoader.load(",
                ),
            ),
        )

        for (case in cases) {
            val source = CompilerAuditTestSupport.sourceOf(case.relativePath)
            CompilerAuditTestSupport.assertContainsAll(source, case.requiredSnippets, case.owner)
            CompilerAuditTestSupport.assertContainsNone(
                source,
                commonKspForbiddenSnippets + case.forbiddenSnippets,
                case.owner,
            )
        }
    }

    @Test
    fun `apt paired shell processors stay on shared lsi orchestration`() {
        val cases = listOf(
            ProcessorShellAuditCase(
                relativePath = "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/dto/DtoProcessor.java",
                owner = "APT dto processor",
                requiredSnippets = listOf(
                    "import site.addzero.lsi.jimmer.dto.DtoProcessorSupport;",
                    "DtoProcessorSupport.collectDtoFiles(",
                    "DtoProcessorSupport.generateFileSpecs(",
                    "Context.INSTANCE.getLsiFiler().createSourceFile(fileSpec);",
                ),
                forbiddenSnippets = listOf(
                    "new DtoGenerator(",
                    "new InputBuilderGenerator(",
                    "new SerializerGenerator(",
                ),
            ),
            ProcessorShellAuditCase(
                relativePath = "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ClientProcessor.java",
                owner = "APT client processor",
                requiredSnippets = listOf(
                    "import site.addzero.lsi.jimmer.client.metadata.generator.ClientProcessorSupport;",
                    "ClientProcessorSupport.collectClientSchemaServiceTypeNames(",
                    "ClientProcessorSupport.generateClientSchemaArtifact(",
                    "Context.INSTANCE.getLsiFiler().overwriteResourceFile(artifact.getPath(), artifact.getContent());",
                ),
                forbiddenSnippets = listOf(
                    "ClientSchemaMetadataExtractor(",
                    "ClientSchemaMetadataGenerator(",
                    "import com.squareup.kotlinpoet",
                    "import com.squareup.javapoet",
                    "toKotlinPoet(",
                    "toJavaPoet(",
                    "renderKotlinSource(",
                    "renderJavaSource(",
                ),
                useCommonAptForbiddenSnippets = false,
            ),
            ProcessorShellAuditCase(
                relativePath = "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ExportDocProcessor.java",
                owner = "APT export-doc processor",
                requiredSnippets = listOf(
                    "import site.addzero.lsi.jimmer.client.metadata.generator.ClientProcessorSupport;",
                    "ClientProcessorSupport.collectExportDocTypeNames(resolver)",
                    "ClientProcessorSupport.generateExportDocArtifact(resolver, typeNames);",
                    "Context.INSTANCE.getLsiFiler().mergePropertiesResourceFile(",
                ),
                forbiddenSnippets = listOf(
                    "ExportDocResourceGenerator(",
                    "LsiExportDocSupport.resolveExportDocDeclarations(",
                    "import com.squareup.kotlinpoet",
                    "import com.squareup.javapoet",
                    "toKotlinPoet(",
                    "toJavaPoet(",
                    "renderKotlinSource(",
                    "renderJavaSource(",
                ),
                useCommonAptForbiddenSnippets = false,
            ),
            ProcessorShellAuditCase(
                relativePath = "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/error/ErrorProcessor.java",
                owner = "APT error processor",
                requiredSnippets = listOf(
                    "import site.addzero.lsi.jimmer.error.metadata.generator.ErrorProcessorSupport;",
                    "ErrorProcessorSupport.collectNewTypes(",
                    "ErrorProcessorSupport.generateFileSpecs(",
                    "Context.INSTANCE.getLsiResolver()",
                    "Context.INSTANCE.getLsiFiler().createSourceFile(fileSpec);",
                ),
                forbiddenSnippets = listOf(
                    "new ErrorMetadataExtractor()",
                    "new ErrorMetadataGenerator()",
                ),
            ),
            ProcessorShellAuditCase(
                relativePath = "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/transactional/TxProcessor.java",
                owner = "APT transactional processor",
                requiredSnippets = listOf(
                    "import site.addzero.lsi.jimmer.transactional.metadata.generator.TxProcessorSupport;",
                    "TxProcessorSupport.collectNewTypes(Context.INSTANCE.getLsiResolver())",
                    "TxProcessorSupport.generateFileSpecs(extraction.getTypes())",
                    "Context.INSTANCE.getLsiFiler().createSourceFile(fileSpec);",
                ),
                forbiddenSnippets = listOf(
                    "new TxMetadataExtractor()",
                    "new TxMetadataGenerator()",
                ),
            ),
            ProcessorShellAuditCase(
                relativePath = "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/tuple/TypedTupleProcessor.java",
                owner = "APT tuple processor",
                requiredSnippets = listOf(
                    "import site.addzero.lsi.jimmer.tuple.metadata.generator.TypedTupleProcessorSupport;",
                    "TypedTupleProcessorSupport.collectRoundTypes(",
                    "TypedTupleProcessorSupport.generateFileSpecs(extraction.getTypes())",
                    "Context.INSTANCE.getLsiFiler().createSourceFile(fileSpec);",
                ),
                forbiddenSnippets = listOf(
                    "new TypedTupleMetadataExtractor()",
                    "new TypedTupleMetadataGenerator()",
                ),
            ),
            ProcessorShellAuditCase(
                relativePath = "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/ImmutableProcessor.java",
                owner = "APT immutable processor",
                requiredSnippets = listOf(
                    "import site.addzero.lsi.jimmer.immutable.metadata.generator.ImmutableProcessorSupport;",
                    "ImmutableProcessorSupport.collectRoundSources(",
                    "ImmutableProcessorSupport.resolveCollectedSources(",
                    "ImmutableProcessorSupport.generateAptOutput(",
                    "ImmutableProcessorSupport.logResolvedImmutableTypes(",
                    "ImmutableProcessorSupport.notifyEntityMetaConsumers(",
                    "for (LsiFileSpec fileSpec : generatedOutput.getSourceFileSpecs()) {",
                    "writeFileSpec(fileSpec);",
                    "for (GeneratedResourceArtifact artifact : generatedOutput.getResourceArtifacts()) {",
                    "Context.INSTANCE.getLsiFiler().createResourceFile(resourceArtifact.getPath(), resourceArtifact.getContent());",
                ),
                forbiddenSnippets = listOf(
                    "new DraftGenerator(",
                    "new ProducerGenerator(",
                    "new BuilderGenerator(",
                    "new ImplementorGenerator(",
                    "new ImplGenerator(",
                    "new DraftImplGenerator(",
                    "new PropsGenerator(",
                    "new FetcherGenerator(",
                    "new ValidationGenerator(",
                    "new CaseAppender(",
                    "messager.printMessage(Diagnostic.Kind.NOTE",
                    "import site.addzero.lsi.jimmer.processor.spi.EntityMetaConsumerSpi;",
                    "private void notifyEntityMetaConsumers(",
                    "private void logResolvedImmutableTypes(",
                    "notifyEntityMetaConsumers(resolvedSources.getLsiClasses());",
                    "logResolvedImmutableTypes(resolvedSources.getLsiClasses());",
                    "ServiceLoader.load(EntityMetaConsumerSpi.class",
                    "renderJavaSource(",
                    "renderKotlinSource(",
                ),
            ),
        )

        for (case in cases) {
            val source = CompilerAuditTestSupport.sourceOf(case.relativePath)
            CompilerAuditTestSupport.assertContainsAll(source, case.requiredSnippets, case.owner)
            CompilerAuditTestSupport.assertContainsNone(
                source,
                (if (case.useCommonAptForbiddenSnippets) commonAptForbiddenSnippets else emptyList()) + case.forbiddenSnippets,
                case.owner,
            )
        }
    }

    private data class ProcessorShellAuditCase(
        val relativePath: String,
        val owner: String,
        val requiredSnippets: List<String>,
        val forbiddenSnippets: List<String> = emptyList(),
        val useCommonAptForbiddenSnippets: Boolean = true,
    )

    private companion object {
        val commonKspForbiddenSnippets = listOf(
            "import com.squareup.kotlinpoet",
            "import com.squareup.javapoet",
            "import com.google.devtools.ksp.",
            "import site.addzero.lsi.ksp.",
            "toKotlinPoet(",
            "toJavaPoet(",
            "renderKotlinSource(",
            "renderJavaSource(",
            "createNewFile(",
        )

        val commonAptForbiddenSnippets = listOf(
            "import com.squareup.kotlinpoet",
            "import com.squareup.javapoet",
            "import site.addzero.lsi.apt.",
            "toKotlinPoet(",
            "toJavaPoet(",
            "renderKotlinSource(",
            "renderJavaSource(",
        )
    }
}
