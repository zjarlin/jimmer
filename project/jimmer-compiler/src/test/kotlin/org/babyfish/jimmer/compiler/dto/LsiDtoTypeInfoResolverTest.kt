package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableType
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.completeEntityProps
import org.babyfish.jimmer.dto.compiler.DtoTypeKind
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiWorkspace

class LsiDtoTypeInfoResolverTest {

    @Test
    fun `resolves inherited input before its view supertype with generic substitution`() {
        val bridgeTypeId = typeId("contract.BaseInput")
        val bridgeParameterId = LsiSymbolId.typeParameter(bridgeTypeId, "T")
        val reusableTypeId = typeId("contract.BookInput")
        val resolver = resolver(
            platform = CompilerPlatform.APT,
            declarations = listOf(
                type(
                    id = bridgeTypeId,
                    typeParameters = listOf(LsiTypeParameter(bridgeParameterId, "T")),
                    superTypes = listOf(
                        declared(INPUT_TYPE_ID, LsiTypeParameterRef(bridgeParameterId)),
                    ),
                ),
                type(
                    id = reusableTypeId,
                    superTypes = listOf(declared(bridgeTypeId, LsiDeclaredType(BOOK_TYPE_ID))),
                ),
            ),
        )

        val typeInfo = resolver.resolve(reusableTypeId.requireTypeQualifiedName())

        assertEquals(DtoTypeKind.INPUT, typeInfo?.kind)
        assertEquals(BOOK_TYPE_ID, typeInfo?.baseType?.id)
    }

    @Test
    fun `resolves view entity type`() {
        val reusableTypeId = typeId("contract.BookView")
        val resolver = resolver(
            platform = CompilerPlatform.KSP,
            declarations = listOf(
                type(
                    id = reusableTypeId,
                    superTypes = listOf(declared(VIEW_TYPE_ID, LsiDeclaredType(BOOK_TYPE_ID))),
                ),
            ),
        )

        val typeInfo = resolver.resolve(reusableTypeId.requireTypeQualifiedName())

        assertEquals(DtoTypeKind.VIEW, typeInfo?.kind)
        assertEquals(BOOK_TYPE_ID, typeInfo?.baseType?.id)
    }

    @Test
    fun `uses platform specification marker`() {
        val javaSpecificationId = typeId("contract.BookJavaSpecification")
        val kotlinSpecificationId = typeId("contract.BookKotlinSpecification")
        val declarations = listOf(
            type(
                id = javaSpecificationId,
                superTypes = listOf(
                    declared(
                        J_SPECIFICATION_TYPE_ID,
                        LsiDeclaredType(BOOK_TYPE_ID),
                        LsiDeclaredType(typeId("contract.BookTable")),
                    ),
                ),
            ),
            type(
                id = kotlinSpecificationId,
                superTypes = listOf(
                    declared(K_SPECIFICATION_TYPE_ID, LsiDeclaredType(BOOK_TYPE_ID)),
                ),
            ),
        )
        val aptResolver = resolver(CompilerPlatform.APT, declarations)
        val kspResolver = resolver(CompilerPlatform.KSP, declarations)

        assertEquals(
            DtoTypeKind.SPECIFICATION,
            aptResolver.resolve(javaSpecificationId.requireTypeQualifiedName())?.kind,
        )
        assertNull(aptResolver.resolve(kotlinSpecificationId.requireTypeQualifiedName()))
        assertEquals(
            DtoTypeKind.SPECIFICATION,
            kspResolver.resolve(kotlinSpecificationId.requireTypeQualifiedName())?.kind,
        )
        assertNull(kspResolver.resolve(javaSpecificationId.requireTypeQualifiedName()))
    }

    @Test
    fun `returns null for a non dto type`() {
        val otherTypeId = typeId("contract.Other")
        val resolver = resolver(
            platform = CompilerPlatform.APT,
            declarations = listOf(type(otherTypeId)),
        )

        assertNull(resolver.resolve(otherTypeId.requireTypeQualifiedName()))
        assertNull(resolver.resolve("contract.Missing"))
    }

    @Test
    fun `rejects dto whose entity argument is not immutable`() {
        val reusableTypeId = typeId("contract.InvalidView")
        val resolver = resolver(
            platform = CompilerPlatform.APT,
            declarations = listOf(
                type(
                    id = reusableTypeId,
                    superTypes = listOf(
                        declared(VIEW_TYPE_ID, LsiDeclaredType(typeId("contract.NotImmutable"))),
                    ),
                ),
            ),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            resolver.resolve(reusableTypeId.requireTypeQualifiedName())
        }

        assertEquals(
            "The entity type argument of reusable DTO type \"contract.InvalidView\" is not an immutable type",
            exception.message,
        )
    }

    @Test
    fun `rejects unknown compiler platform`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            resolver(CompilerPlatform.UNKNOWN, emptyList())
        }

        assertEquals("Reusable DTO type resolution requires APT or KSP platform", exception.message)
    }

    private fun resolver(
        platform: CompilerPlatform,
        declarations: List<LsiTypeDeclaration>,
    ): LsiDtoTypeInfoResolver {
        val workspace = LsiWorkspace(declarations = declarations)
        val registry = LsiDtoTypeRegistry(
            immutableSchema = JimmerImmutableSchema(listOf(immutableType(BOOK_TYPE_ID))),
            workspace = workspace,
        )
        return LsiDtoTypeInfoResolver(registry, platform)
    }

    private fun type(
        id: LsiSymbolId,
        typeParameters: List<LsiTypeParameter> = emptyList(),
        superTypes: List<LsiDeclaredType> = emptyList(),
    ): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = id,
            name = id.requireTypeQualifiedName().substringAfterLast('.'),
            qualifiedName = id.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.CLASS,
            modality = LsiModality.OPEN,
            typeParameters = typeParameters,
            superTypes = superTypes,
            origin = BINARY_ORIGIN,
        )
    }

    private fun declared(
        typeId: LsiSymbolId,
        vararg arguments: site.addzero.lsi.model.LsiTypeRef,
    ): LsiDeclaredType {
        return LsiDeclaredType(
            declarationId = typeId,
            arguments = arguments.map(LsiTypeArgument::invariant),
        )
    }

    private fun immutableType(id: LsiSymbolId): JimmerImmutableType {
        val props = completeEntityProps(id)
        return JimmerImmutableType(
            id = id,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = JimmerImmutableTypeKind.ENTITY,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = emptyList(),
            props = props,
            primarySuperTypeId = null,
            inheritanceRootTypeId = null,
            inheritanceStrategy = null,
            joinedTableDissociateAction = null,
            instantiable = true,
            discriminatorValue = null,
            discriminatorPropId = null,
            idPropId = props.single().id,
            versionPropId = null,
            logicalDeletedPropId = null,
            acrossMicroServices = false,
            microServiceName = "",
        )
    }

    private fun typeId(qualifiedName: String): LsiSymbolId = LsiSymbolId.type(qualifiedName)

    private companion object {
        val BOOK_TYPE_ID = LsiSymbolId.type("demo.Book")
        val INPUT_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.Input")
        val VIEW_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.View")
        val J_SPECIFICATION_TYPE_ID =
            LsiSymbolId.type("org.babyfish.jimmer.sql.ast.query.specification.JSpecification")
        val K_SPECIFICATION_TYPE_ID =
            LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.query.specification.KSpecification")
        val BINARY_ORIGIN = LsiOrigin(
            kind = LsiOriginKind.BINARY,
            language = LsiLanguage.UNKNOWN,
        )
    }
}
