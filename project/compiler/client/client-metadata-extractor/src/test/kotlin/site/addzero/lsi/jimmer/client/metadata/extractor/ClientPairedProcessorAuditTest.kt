package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClientPairedProcessorAuditTest {

    @Test
    fun `ksp client processor stays on shared lsi extractor and generator path`() {
        val source = CompilerAuditTestSupport.sourceOf(
            "project/compiler/client/jimmer-ksp-client/src/main/kotlin/org/babyfish/jimmer/ksp/client/ClientProcessor.kt"
        )

        assertTrue(
            source.contains("import site.addzero.lsi.jimmer.client.metadata.generator.ClientProcessorSupport"),
            "KSP client processor must use the shared client processor support",
        )
        assertTrue(
            source.contains("ClientProcessorSupport.collectClientSchemaServiceTypeNames("),
            "KSP client processor must delegate client API discovery to the shared support",
        )
        assertTrue(
            source.contains("ClientProcessorSupport.generateClientSchemaArtifact("),
            "KSP client processor must delegate schema artifact generation to the shared support",
        )
        assertTrue(
            source.contains("convertedLsiTypeNameOf = Context::convertedLsiTypeNameOf"),
            "KSP client processor must query converter target types through Context's minimal LSI helper",
        )
        assertTrue(
            source.contains("ctx.lsiFiler.createResourceFile(artifact.path, artifact.content)"),
            "KSP client processor must write resource artifacts through LsiFiler",
        )
        assertFalse(
            source.contains("createNewFile("),
            "KSP client processor must not write raw KSP files directly",
        )
        assertFalse(
            source.contains("Context.typeOf(owner)"),
            "KSP client processor must not depend on shared immutable metadata concrete types directly",
        )
    }

    @Test
    fun `apt client processor stays on shared lsi extractor and generator path`() {
        val source = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ClientProcessor.java"
        )

        assertTrue(
            source.contains("import site.addzero.lsi.jimmer.client.metadata.generator.ClientProcessorSupport;"),
            "APT client processor must use the shared client processor support",
        )
        assertTrue(
            source.contains("ClientProcessorSupport.collectClientSchemaServiceTypeNames("),
            "APT client processor must delegate client API discovery to the shared support",
        )
        assertTrue(
            source.contains("ClientProcessorSupport.generateClientSchemaArtifact("),
            "APT client processor must delegate schema artifact generation to the shared support",
        )
        assertTrue(
            source.contains("GeneratedResourceArtifact artifact = ClientProcessorSupport.generateClientSchemaArtifact("),
            "APT client processor must consume shared resource artifacts",
        )
        assertTrue(
            source.contains("Context.INSTANCE::convertedLsiTypeNameOf"),
            "APT client processor must query converter target types through Context's minimal LSI helper",
        )
        assertTrue(
            source.contains("Context.INSTANCE.getLsiFiler().overwriteResourceFile(artifact.getPath(), artifact.getContent());"),
            "APT client processor must write resource artifacts through LsiFiler boundary methods",
        )
        assertFalse(
            source.contains("javax.annotation.processing.Filer"),
            "APT client processor must not depend on raw annotation-processing filer APIs",
        )
        assertFalse(
            source.contains(".typeOf(owner)"),
            "APT client processor must not depend on shared immutable metadata concrete types directly",
        )
    }
}
