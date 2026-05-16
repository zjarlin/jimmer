package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import java.nio.file.Files

class PropsMetadataSharedCarrierAuditTest {

    @Test
    fun `props metadata reuses shared built in carrier helper`() {
        val source = Files.readString(
            ImmutableTestSupport.repoRoot.resolve(
                "project/compiler/immutable/immutable-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/immutable/generator/PropsMetadata.kt"
            )
        )

        assertTrue(
            source.contains("toBuiltInLsiClassNameOrNull"),
            "PropsMetadata 必须复用共享 built-in carrier helper\n$source",
        )
        assertFalse(
            source.contains("private fun primitiveLsiTypeName"),
            "PropsMetadata 不得保留本地 primitive truth table\n$source",
        )
    }
}
