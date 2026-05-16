package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Test

class ClientAptProcessorShellContractTest {

    @Test
    fun `apt client processor stays on shared lsi extractor generator and resource filer path`() {
        val source = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ClientProcessor.java"
        )

        CompilerAuditTestSupport.assertContainsAll(
            source,
            listOf(
                "Context.INSTANCE.guessGeneratedJimmerResourceFile(\"client\")",
                "ClientProcessorSupport.generateClientSchemaArtifact(",
                "ClientProcessorSupport.collectClientSchemaServiceTypeNames(",
                "LsiSourceFilterKt::matchesConfiguredSourceFilters",
                "checkJdkVersion(serviceTypeNames);",
                "GeneratedResourceArtifact artifact = ClientProcessorSupport.generateClientSchemaArtifact(",
                "Context.INSTANCE.getLsiFiler().overwriteResourceFile(artifact.getPath(), artifact.getContent());",
            ),
            "APT client processor",
        )
    }

    @Test
    fun `apt client processor does not perform local poet rendering or depend on legacy immutable metadata`() {
        val source = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ClientProcessor.java"
        )

        CompilerAuditTestSupport.assertContainsNone(
            source,
            listOf(
                "renderJavaSource(",
                "renderKotlinSource(",
                "createSourceFile(",
                "com.squareup.javapoet",
                "com.squareup.kotlinpoet",
                "JavaFile",
                "FileSpec",
                "javax.annotation.processing.Filer",
                "org.babyfish.jimmer.apt.immutable.meta.",
                "toJavaPoet(",
                "toKotlinPoet(",
            ),
            "APT client processor",
        )
    }
}
