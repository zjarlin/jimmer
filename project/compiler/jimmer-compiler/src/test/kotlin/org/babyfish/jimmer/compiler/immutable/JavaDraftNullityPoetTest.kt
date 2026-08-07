package org.babyfish.jimmer.compiler.immutable

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.poet.LsiPoetArtifact
import site.addzero.lsi.poet.LsiPoetFile
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetParameter
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.poet.LsiPoetTypeKind
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

class JavaDraftNullityPoetTest {

    @Test
    fun `空值类型注解位于限定类型的合法位置`() {
        val statusTypeId = LsiSymbolId.type("demo.model.B.Status")
        val utilDateTypeId = LsiSymbolId.type("java.util.Date")
        val sqlDateTypeId = LsiSymbolId.type("java.sql.Date")
        val nonNullTypeId = LsiSymbolId.type("org.jspecify.annotations.NonNull")
        val type = LsiPoetType(
            name = "DraftBuilder",
            kind = LsiPoetTypeKind.CLASS,
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            members = listOf(
                LsiPoetFunction(
                    name = "status",
                    modifiers = setOf(LsiPoetModifier.PUBLIC),
                    parameters = listOf(
                        LsiPoetParameter(
                            name = "status",
                            type = LsiDeclaredType(statusTypeId).withJavaDraftNullity(nullable = false),
                        )
                    ),
                ),
                LsiPoetFunction(
                    name = "dates",
                    modifiers = setOf(LsiPoetModifier.PUBLIC),
                    parameters = listOf(
                        LsiPoetParameter(
                            name = "utilDate",
                            type = LsiDeclaredType(utilDateTypeId).withJavaDraftNullity(nullable = false),
                        ),
                        LsiPoetParameter(
                            name = "sqlDate",
                            type = LsiDeclaredType(sqlDateTypeId).withJavaDraftNullity(nullable = false),
                        ),
                    ),
                ),
            ),
        )
        val artifact = LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.JAVA,
                packageName = "demo.generated",
                fileName = "DraftBuilder",
                members = listOf(type),
            ),
            typeNames = listOf(
                LsiPoetTypeName(statusTypeId, "demo.model", listOf("B", "Status")),
                LsiPoetTypeName(utilDateTypeId, "java.util", listOf("Date")),
                LsiPoetTypeName(sqlDateTypeId, "java.sql", listOf("Date")),
                LsiPoetTypeName(nonNullTypeId, "org.jspecify.annotations", listOf("NonNull")),
            ),
            aggregationMode = ArtifactAggregationMode.AGGREGATING,
        )

        val content = LsiJavaPoetRenderer().render(artifact).content

        assertContains(content, "B. @NonNull Status status")
        assertContains(content, "java.sql. @NonNull Date sqlDate")
        assertFalse("@NonNull B.Status" in content)
        assertFalse("@NonNull java.sql.Date" in content)
    }
}
