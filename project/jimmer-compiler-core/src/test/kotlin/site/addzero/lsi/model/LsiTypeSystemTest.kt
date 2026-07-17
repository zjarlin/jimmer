package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId

class LsiTypeSystemTest {

    @Test
    fun `resolves generic inherited property type`() {
        val baseId = LsiSymbolId.type("sample.Base")
        val entityId = LsiSymbolId.type("sample.Entity")
        val parameterId = LsiSymbolId.typeParameter(baseId, "T")
        val valueId = LsiSymbolId.property(baseId, "value")
        val base = type(
            id = baseId,
            parameters = listOf(LsiTypeParameter(parameterId, "T")),
            memberIds = listOf(valueId),
        )
        val value = property(
            id = valueId,
            ownerId = baseId,
            type = LsiTypeParameterRef(parameterId),
        )
        val entity = type(
            id = entityId,
            superTypes = listOf(
                LsiDeclaredType(
                    declarationId = baseId,
                    arguments = listOf(
                        LsiTypeArgument.invariant(
                            LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
                        ),
                    ),
                ),
            ),
        )
        val typeSystem = LsiTypeSystem(LsiWorkspace(declarations = listOf(base, value, entity)))

        assertEquals(
            "type:java.lang.String!non-null",
            typeSystem.effectiveProperties(entityId).single().type.stableSignature(),
        )
        assertEquals(
            "type:sample.Base<type:java.lang.String!non-null>!non-null",
            typeSystem.resolveSuperType(entityId, baseId)?.stableSignature(),
        )
    }

    @Test
    fun `merges overridden annotations by qualified type id`() {
        val baseId = LsiSymbolId.type("sample.Base")
        val entityId = LsiSymbolId.type("sample.Entity")
        val basePropertyId = LsiSymbolId.property(baseId, "status")
        val entityPropertyId = LsiSymbolId.property(entityId, "status")
        val defaultType = LsiSymbolId.type("org.babyfish.jimmer.sql.Default")
        val columnType = LsiSymbolId.type("org.babyfish.jimmer.sql.Column")
        val base = type(baseId, memberIds = listOf(basePropertyId))
        val baseProperty = property(
            id = basePropertyId,
            ownerId = baseId,
            annotations = listOf(annotation(defaultType, "0"), annotation(columnType, "STATUS")),
        )
        val entity = type(
            id = entityId,
            superTypes = listOf(LsiDeclaredType(baseId)),
            memberIds = listOf(entityPropertyId),
        )
        val entityProperty = property(
            id = entityPropertyId,
            ownerId = entityId,
            annotations = listOf(annotation(defaultType, "1")),
            overrides = listOf(LsiOverride(basePropertyId)),
        )
        val typeSystem = LsiTypeSystem(
            LsiWorkspace(declarations = listOf(base, baseProperty, entity, entityProperty)),
        )

        val resolved = typeSystem.effectiveProperties(entityId).single()
        assertEquals(listOf(entityPropertyId, basePropertyId), resolved.overrideChain.map(LsiProperty::id))
        assertEquals(listOf(defaultType, columnType), resolved.annotations.map(LsiAnnotation::type))
        assertEquals(
            "1",
            (resolved.annotations.first().arguments.getValue("value").value as LsiAnnotationValue.StringValue).value,
        )
    }

    @Test
    fun `rejects unrelated inherited properties with same name`() {
        val leftId = LsiSymbolId.type("sample.Left")
        val rightId = LsiSymbolId.type("sample.Right")
        val entityId = LsiSymbolId.type("sample.Entity")
        val leftPropertyId = LsiSymbolId.property(leftId, "value")
        val rightPropertyId = LsiSymbolId.property(rightId, "value")
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(leftId, memberIds = listOf(leftPropertyId)),
                property(leftPropertyId, leftId),
                type(rightId, memberIds = listOf(rightPropertyId)),
                property(rightPropertyId, rightId),
                type(
                    entityId,
                    superTypes = listOf(LsiDeclaredType(leftId), LsiDeclaredType(rightId)),
                ),
            ),
        )

        val exception = assertFailsWith<LsiInheritedPropertyConflictException> {
            LsiTypeSystem(workspace).effectiveProperties(entityId)
        }
        assertEquals(listOf(leftPropertyId, rightPropertyId), exception.conflictingPropertyIds)
    }

    @Test
    fun `nearest inherited property wins and keeps farther annotations`() {
        val rootId = LsiSymbolId.type("sample.Root")
        val middleId = LsiSymbolId.type("sample.Middle")
        val directId = LsiSymbolId.type("sample.Direct")
        val entityId = LsiSymbolId.type("sample.Entity")
        val rootPropertyId = LsiSymbolId.property(rootId, "name")
        val directPropertyId = LsiSymbolId.property(directId, "name")
        val keyType = LsiSymbolId.type("org.babyfish.jimmer.sql.Key")
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(rootId, memberIds = listOf(rootPropertyId)),
                property(rootPropertyId, rootId, annotations = listOf(annotation(keyType, "root"))),
                type(middleId, superTypes = listOf(LsiDeclaredType(rootId))),
                type(directId, memberIds = listOf(directPropertyId)),
                property(directPropertyId, directId),
                type(
                    entityId,
                    superTypes = listOf(LsiDeclaredType(middleId), LsiDeclaredType(directId)),
                ),
            ),
        )

        val resolved = LsiTypeSystem(workspace).effectiveProperties(entityId).single()

        assertEquals(directPropertyId, resolved.declaration.id)
        assertEquals(listOf(directPropertyId, rootPropertyId), resolved.overrideChain.map(LsiProperty::id))
        assertEquals(listOf(keyType), resolved.annotations.map(LsiAnnotation::type))
    }

    @Test
    fun `resolves generic super type through external hierarchy`() {
        val entityId = LsiSymbolId.type("sample.Entity")
        val middleId = LsiSymbolId.type("sample.Middle")
        val baseId = LsiSymbolId.type("sample.Base")
        val listId = LsiSymbolId.type("java.util.List")
        val middleParameterId = LsiSymbolId.typeParameter(middleId, "T")
        val baseParameterId = LsiSymbolId.typeParameter(baseId, "E")
        val stringType = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val entity = type(
            id = entityId,
            superTypes = listOf(
                LsiDeclaredType(
                    declarationId = middleId,
                    arguments = listOf(LsiTypeArgument.invariant(stringType)),
                ),
            ),
        )
        val middle = LsiTypeHierarchyEntry(
            id = middleId,
            qualifiedName = "sample.Middle",
            kind = LsiTypeDeclarationKind.CLASS,
            typeParameters = listOf(LsiTypeParameter(middleParameterId, "T")),
            directSuperTypes = listOf(
                LsiDeclaredType(
                    declarationId = baseId,
                    arguments = listOf(
                        LsiTypeArgument.invariant(
                            LsiDeclaredType(
                                declarationId = listId,
                                arguments = listOf(
                                    LsiTypeArgument.invariant(LsiTypeParameterRef(middleParameterId)),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val base = LsiTypeHierarchyEntry(
            id = baseId,
            qualifiedName = "sample.Base",
            kind = LsiTypeDeclarationKind.INTERFACE,
            typeParameters = listOf(LsiTypeParameter(baseParameterId, "E")),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(entity),
            typeHierarchy = listOf(middle, base),
        )

        val resolved = LsiTypeSystem(workspace).resolveSuperType(entityId, baseId)

        assertEquals(
            "type:sample.Base<type:java.util.List<type:java.lang.String!non-null>!non-null>!non-null",
            resolved?.stableSignature(),
        )
    }

    private fun type(
        id: LsiSymbolId,
        parameters: List<LsiTypeParameter> = emptyList(),
        superTypes: List<LsiTypeRef> = emptyList(),
        memberIds: List<LsiSymbolId> = emptyList(),
    ): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = id,
            name = id.value.substringAfterLast('.'),
            qualifiedName = id.value.removePrefix("type:"),
            kind = LsiTypeDeclarationKind.INTERFACE,
            typeParameters = parameters,
            superTypes = superTypes,
            memberIds = memberIds,
            origin = ORIGIN,
        )
    }

    private fun property(
        id: LsiSymbolId,
        ownerId: LsiSymbolId,
        type: LsiTypeRef = LsiPrimitiveType(LsiPrimitiveKind.INT),
        annotations: List<LsiAnnotation> = emptyList(),
        overrides: List<LsiOverride> = emptyList(),
    ): LsiProperty {
        return LsiProperty(
            id = id,
            name = id.value.substringAfter("/property:"),
            ownerId = ownerId,
            type = type,
            annotations = annotations,
            overrides = overrides,
            origin = ORIGIN,
        )
    }

    private fun annotation(type: LsiSymbolId, value: String): LsiAnnotation {
        return LsiAnnotation(
            type = type,
            arguments = mapOf(
                "value" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.StringValue(value),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                ),
            ),
        )
    }

    companion object {
        private val ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
    }
}
