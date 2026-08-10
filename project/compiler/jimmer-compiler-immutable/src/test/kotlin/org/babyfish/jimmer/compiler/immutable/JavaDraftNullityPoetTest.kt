package org.babyfish.jimmer.compiler.immutable

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.model.LsiFile
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

class JavaDraftNullityPoetTest {

    @Test
    fun `空值类型注解位于限定类型的合法位置`() {
        val statusTypeId = LsiSymbolId.type("demo.model.B.Status")
        val utilDateTypeId = LsiSymbolId.type("java.util.Date")
        val sqlDateTypeId = LsiSymbolId.type("java.sql.Date")
        val nonNullTypeId = LsiSymbolId.type("org.jspecify.annotations.NonNull")
        val type = LsiTypeDeclaration(
            name = "DraftBuilder",
            kind = LsiTypeDeclarationKind.CLASS,
            modifiers = setOf(LsiModifier.PUBLIC),
            members = listOf(
                LsiFunction(
                    name = "status",
                    modifiers = setOf(LsiModifier.PUBLIC),
                    parameters = listOf(
                        LsiParameter(
                            name = "status",
                            type = LsiDeclaredType(statusTypeId).withJavaDraftNullity(nullable = false),
                        )
                    ),
                ),
                LsiFunction(
                    name = "dates",
                    modifiers = setOf(LsiModifier.PUBLIC),
                    parameters = listOf(
                        LsiParameter(
                            name = "utilDate",
                            type = LsiDeclaredType(utilDateTypeId).withJavaDraftNullity(nullable = false),
                        ),
                        LsiParameter(
                            name = "sqlDate",
                            type = LsiDeclaredType(sqlDateTypeId).withJavaDraftNullity(nullable = false),
                        ),
                    ),
                ),
            ),
        )
        val artifact = LsiSourceArtifact(
            file = LsiFile(
                language = LsiLanguage.JAVA,
                packageName = "demo.generated",
                fileName = "DraftBuilder",
                members = listOf(type),
            ),
            typeNames = listOf(
                LsiTypeName(statusTypeId, "demo.model", listOf("B", "Status")),
                LsiTypeName(utilDateTypeId, "java.util", listOf("Date")),
                LsiTypeName(sqlDateTypeId, "java.sql", listOf("Date")),
                LsiTypeName(nonNullTypeId, "org.jspecify.annotations", listOf("NonNull")),
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
