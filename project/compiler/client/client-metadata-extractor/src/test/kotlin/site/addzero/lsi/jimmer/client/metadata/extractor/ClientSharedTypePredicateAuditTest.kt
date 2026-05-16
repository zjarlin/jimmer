package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Test

class ClientSharedTypePredicateAuditTest {

    @Test
    fun `client shared extractor reuses shared no value and boolean predicates`() {
        val materializationSource = CompilerAuditTestSupport.sourceOf(
            "project/compiler/client/client-metadata-extractor/src/main/kotlin/site/addzero/lsi/jimmer/client/LsiClientSchemaMaterialization.kt"
        )
        val traversalSource = CompilerAuditTestSupport.sourceOf(
            "project/compiler/client/client-metadata-extractor/src/main/kotlin/site/addzero/lsi/jimmer/client/LsiClientSchemaTraversal.kt"
        )
        val typeSupportSource = CompilerAuditTestSupport.sourceOf(
            "project/compiler/client/client-metadata-extractor/src/main/kotlin/site/addzero/lsi/jimmer/client/LsiClientTypeSupport.kt"
        )
        val exceptionTypeSupportSource = CompilerAuditTestSupport.sourceOf(
            "project/compiler/client/client-metadata-extractor/src/main/kotlin/site/addzero/lsi/jimmer/client/LsiClientExceptionTypeSupport.kt"
        )

        CompilerAuditTestSupport.assertContainsAll(
            materializationSource,
            listOf(
                "isLsiNoValueLikeQualifiedName",
                "isLsiBooleanLikeQualifiedName",
            ),
            "LsiClientSchemaMaterialization.kt",
        )
        CompilerAuditTestSupport.assertContainsAll(
            traversalSource,
            listOf("isLsiNoValueLikeQualifiedName"),
            "LsiClientSchemaTraversal.kt",
        )
        CompilerAuditTestSupport.assertContainsAll(
            typeSupportSource,
            listOf("isLsiCollectionLikeQualifiedName"),
            "LsiClientTypeSupport.kt",
        )
        CompilerAuditTestSupport.assertContainsNone(
            materializationSource,
            listOf(
                "== \"kotlin.Unit\"",
                "== \"kotlin.Nothing\"",
                "!= \"kotlin.Unit\"",
                "!= \"kotlin.Nothing\"",
                "== \"kotlin.Boolean\"",
            ),
            "LsiClientSchemaMaterialization.kt",
        )
        CompilerAuditTestSupport.assertContainsNone(
            traversalSource,
            listOf(
                "== \"kotlin.Unit\"",
                "== \"kotlin.Nothing\"",
                "!= \"kotlin.Unit\"",
                "!= \"kotlin.Nothing\"",
            ),
            "LsiClientSchemaTraversal.kt",
        )
        CompilerAuditTestSupport.assertContainsNone(
            typeSupportSource,
            listOf(
                "startsWith(\"kotlin.collections.\")",
                "startsWith(\"java.util.\")",
                "\"java.lang.Object\"",
            ),
            "LsiClientTypeSupport.kt",
        )
        CompilerAuditTestSupport.assertContainsNone(
            exceptionTypeSupportSource,
            listOf(
                "kotlin.Throws",
                "kotlin.jvm.Throws",
                "exceptionClasses",
            ),
            "LsiClientExceptionTypeSupport.kt",
        )
    }
}
