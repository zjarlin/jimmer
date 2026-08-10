package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.babyfish.jimmer.compiler.render.apt.AptDtoTypeRefRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoTypeRefRenderer
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import org.babyfish.jimmer.dto.compiler.DtoTypeKind
import site.addzero.lsi.jimmer.dto.DtoReusableTypeReference
import site.addzero.lsi.jimmer.dto.DtoTypeArgument
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoTypeRef
import site.addzero.lsi.type.LsiVariance
import site.addzero.lsi.jimmer.dto.toLsiType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class DtoTypeRefPoetTest {

    @Test
    fun `renders one frozen generic type through both poet adapters`() {
        val typeRef = type(
            name = "List",
            arguments = listOf(
                DtoTypeArgument(
                    variance = LsiVariance.INVARIANT,
                    type = type("String"),
                ),
            ),
        )

        assertEquals(
            "java.util.List<? extends java.lang.String>",
            LsiJavaPoetRenderer().renderTypeName(
                typeRef.toLsiType(LsiLanguage.JAVA),
                DTO_COMMON_POET_TYPE_NAMES,
            ).toString(),
        )
        assertEquals(
            "kotlin.collections.List<kotlin.String>",
            LsiKotlinPoetRenderer().renderTypeName(
                typeRef.toLsiType(LsiLanguage.KOTLIN),
                DTO_COMMON_POET_TYPE_NAMES,
            ).toString(),
        )
    }

    @Test
    fun `renders primitive arrays without platform type heuristics`() {
        val typeRef = type(
            name = "Array",
            arguments = listOf(
                DtoTypeArgument(
                    variance = LsiVariance.INVARIANT,
                    type = type("Int"),
                ),
            ),
        )

        assertEquals(
            "int[]",
            LsiJavaPoetRenderer().renderTypeName(
                typeRef.toLsiType(LsiLanguage.JAVA),
                DTO_COMMON_POET_TYPE_NAMES,
            ).toString(),
        )
        assertEquals(
            "kotlin.IntArray",
            LsiKotlinPoetRenderer().renderTypeName(
                typeRef.toLsiType(LsiLanguage.KOTLIN),
                DTO_COMMON_POET_TYPE_NAMES,
            ).toString(),
        )
    }

    @Test
    fun `resolves custom nested types through the production workspace path`() {
        val outerId = LsiSymbolId.type("demo.types.Outer")
        val nestedId = LsiSymbolId.type("demo.types.Outer.Inner")
        val workspace = LsiWorkspace(
            declarations = listOf(
                typeDeclaration(outerId, "Outer"),
                typeDeclaration(nestedId, "Inner", outerId),
            ),
        )
        val typeRef = type(nestedId.requireTypeQualifiedName())

        assertEquals(
            "demo.types.Outer.Inner",
            AptDtoTypeRefRenderer.render(typeRef, workspace).toString(),
        )
        assertEquals(
            "demo.types.Outer.Inner",
            KspDtoTypeRefRenderer.render(typeRef, workspace).toString(),
        )
    }

    @Test
    fun `resolves reusable generated types through an explicit generated name`() {
        val generatedTypeName = JimmerDtoPoetTypeNames.create(
            packageName = "demo.generated",
            simpleNames = listOf("Outer", "Inner"),
        )
        val reference = DtoReusableTypeReference(
            qualifiedName = generatedTypeName.canonicalName,
            targetBaseTypeId = LsiSymbolId.type("demo.Base"),
            kind = DtoTypeKind.VIEW,
            location = LOCATION,
        )

        assertEquals(
            "demo.generated.Outer.Inner",
            AptDtoTypeRefRenderer.render(reference, LsiWorkspace.EMPTY, generatedTypeName).toString(),
        )
        assertEquals(
            "demo.generated.Outer.Inner",
            KspDtoTypeRefRenderer.render(reference, LsiWorkspace.EMPTY, generatedTypeName).toString(),
        )
    }

    @Test
    fun `rejects ambiguous package and nested boundaries for reusable targets`() {
        val canonicalName = "demo.BookView.Target"
        val typeId = LsiSymbolId.type(canonicalName)
        val reference = DtoReusableTypeReference(
            qualifiedName = canonicalName,
            targetBaseTypeId = LsiSymbolId.type("demo.Base"),
            kind = DtoTypeKind.VIEW,
            location = LOCATION,
        )

        assertFailsWith<IllegalArgumentException> {
            JimmerDtoPoetTypeNames.reusableTarget(
                reference,
                mapOf(
                    DtoTypeId("nested") to LsiTypeName(
                        typeId = typeId,
                        packageName = "demo",
                        simpleNames = listOf("BookView", "Target"),
                    ),
                    DtoTypeId("top-level") to LsiTypeName(
                        typeId = typeId,
                        packageName = "demo.BookView",
                        simpleNames = listOf("Target"),
                    ),
                ),
            )
        }
    }

    private fun type(
        name: String,
        arguments: List<DtoTypeArgument> = emptyList(),
    ): DtoTypeRef = DtoTypeRef(
        typeName = name,
        arguments = arguments,
        nullable = false,
        location = LOCATION,
    )

    private fun typeDeclaration(
        id: LsiSymbolId,
        name: String,
        enclosingTypeId: LsiSymbolId? = null,
    ): LsiTypeDeclaration = LsiTypeDeclaration(
        id = id,
        name = name,
        qualifiedName = id.requireTypeQualifiedName(),
        kind = LsiTypeDeclarationKind.CLASS,
        enclosingTypeId = enclosingTypeId,
        origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
    )

    private companion object {
        val LOCATION = LsiLocation(
            source = LsiSource.of("demo/src/main/dto/Types.dto"),
            start = LsiPosition(1, 1),
        )
    }
}
