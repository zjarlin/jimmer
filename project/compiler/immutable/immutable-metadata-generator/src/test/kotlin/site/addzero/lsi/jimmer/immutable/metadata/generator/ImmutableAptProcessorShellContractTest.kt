package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import java.nio.file.Files

class ImmutableAptProcessorShellContractTest {

    @Test
    fun `apt immutable processor stays on shared lsi extraction and artifact path`() {
        val source = immutableProcessorSource()

        val requiredSnippets = listOf(
            "import site.addzero.lsi.jimmer.immutable.metadata.generator.ImmutableGeneratedOutput;",
            "import site.addzero.lsi.jimmer.immutable.metadata.generator.ImmutableProcessorSupport;",
            "ImmutableCollectedSourceAccumulator accumulator = new ImmutableCollectedSourceAccumulator();",
            "ImmutableProcessorSupport.collectRoundSources(",
            "ImmutableProcessorSupport.resolveCollectedSources(",
            "ImmutableProcessorSupport.generateAptOutput(",
            "ImmutableProcessorSupport.logResolvedImmutableTypes(",
            "ImmutableProcessorSupport.notifyEntityMetaConsumers(",
            "for (LsiFileSpec fileSpec : generatedOutput.getSourceFileSpecs()) {",
            "GeneratedResourceArtifact",
            "Context.INSTANCE.guessGeneratedJimmerResourceFile(\"entities\")",
            "writeFileSpec(fileSpec);",
            "for (GeneratedResourceArtifact artifact : generatedOutput.getResourceArtifacts()) {",
            "Context.INSTANCE.getLsiFiler().createResourceFile(resourceArtifact.getPath(), resourceArtifact.getContent());",
        )
        for (snippet in requiredSnippets) {
            assertTrue(source.contains(snippet), "ImmutableProcessor must contain `$snippet`\n$source")
        }
    }

    @Test
    fun `apt immutable processor does not perform local rendering or call removed business generators`() {
        val source = immutableProcessorSource()

        val forbiddenSnippets = listOf(
            "renderJavaSource(",
            "renderKotlinSource(",
            "com.squareup.javapoet",
            "com.squareup.kotlinpoet",
            "GeneratedSourceFileArtifact",
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
            "new TableGenerator(",
            "new EmbeddedPropExpressionGenerator(",
            "private void validateTopLevel(",
            "ImmutableProcessorSupport.validateImmutableTopLevelAnnotatedTypes(",
            "ImmutableProcessorSupport.validateTopLevelAnnotatedTypes(",
            "messager.printMessage(Diagnostic.Kind.NOTE",
            "ImmutableResolvedSource",
            "import site.addzero.lsi.jimmer.processor.spi.EntityMetaConsumerSpi;",
            "private void notifyEntityMetaConsumers(",
            "private void logResolvedImmutableTypes(",
            "notifyEntityMetaConsumers(resolvedSources.getLsiClasses());",
            "logResolvedImmutableTypes(resolvedSources.getLsiClasses());",
            "ServiceLoader.load(EntityMetaConsumerSpi.class, ImmutableProcessor.class.getClassLoader())",
        )
        for (snippet in forbiddenSnippets) {
            assertFalse(source.contains(snippet), "ImmutableProcessor must not contain `$snippet`\n$source")
        }
    }

    @Test
    fun `apt immutable shared assembly keeps kotlin dsl sidecars disabled`() {
        val source = immutableProcessorSource()

        assertTrue(
            source.contains(
                "Settings.INSTANCE.getJimmerImmutableIsModuleRequired(),\n                org.babyfish.jimmer.JimmerVersionsKt.currentVersion()"
            ),
            "APT immutable processor must route through single APT output assembly\n$source",
        )
    }

    private fun immutableProcessorSource(): String =
        Files.readString(
            ImmutableTestSupport.repoRoot.resolve(
                "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/ImmutableProcessor.java"
            )
        )
}
