package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
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
    fun `resolves generic super type from parameterized subtype`() {
        val baseId = LsiSymbolId.type("sample.Base")
        val childId = LsiSymbolId.type("sample.Child")
        val baseParameterId = LsiSymbolId.typeParameter(baseId, "T")
        val childParameterId = LsiSymbolId.typeParameter(childId, "T")
        val base = type(
            id = baseId,
            parameters = listOf(LsiTypeParameter(baseParameterId, "T")),
        )
        val child = type(
            id = childId,
            parameters = listOf(LsiTypeParameter(childParameterId, "T")),
            superTypes = listOf(
                LsiDeclaredType(
                    declarationId = baseId,
                    arguments = listOf(
                        LsiTypeArgument.invariant(LsiTypeParameterRef(childParameterId)),
                    ),
                )
            ),
        )
        val stringType = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val childOfString = LsiDeclaredType(
            declarationId = childId,
            arguments = listOf(LsiTypeArgument.invariant(stringType)),
        )
        val typeSystem = LsiTypeSystem(LsiWorkspace(declarations = listOf(base, child)))

        assertEquals(
            LsiDeclaredType(
                declarationId = baseId,
                arguments = listOf(LsiTypeArgument.invariant(stringType)),
            ),
            typeSystem.resolveSuperType(childOfString, baseId),
        )
    }

    @Test
    fun `preserves projected arguments while resolving generic super types`() {
        val baseId = LsiSymbolId.type("sample.Base")
        val childId = LsiSymbolId.type("sample.Child")
        val baseParameterId = LsiSymbolId.typeParameter(baseId, "T")
        val childParameterId = LsiSymbolId.typeParameter(childId, "T")
        val stringType = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val base = type(
            id = baseId,
            parameters = listOf(LsiTypeParameter(baseParameterId, "T")),
        )
        val child = type(
            id = childId,
            parameters = listOf(LsiTypeParameter(childParameterId, "T")),
            superTypes = listOf(
                LsiDeclaredType(
                    declarationId = baseId,
                    arguments = listOf(
                        LsiTypeArgument.invariant(LsiTypeParameterRef(childParameterId)),
                    ),
                )
            ),
        )
        val typeSystem = LsiTypeSystem(LsiWorkspace(declarations = listOf(base, child)))

        listOf(
            LsiTypeArgument.STAR,
            LsiTypeArgument.input(stringType),
            LsiTypeArgument.output(stringType),
        ).forEach { argument ->
            assertEquals(
                LsiDeclaredType(baseId, listOf(argument)),
                typeSystem.resolveSuperType(LsiDeclaredType(childId, listOf(argument)), baseId),
            )
        }
    }

    @Test
    fun `collapses conflicting projected arguments to star`() {
        val baseId = LsiSymbolId.type("sample.Base")
        val childId = LsiSymbolId.type("sample.Child")
        val baseParameterId = LsiSymbolId.typeParameter(baseId, "T")
        val childParameterId = LsiSymbolId.typeParameter(childId, "T")
        val stringType = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    id = baseId,
                    parameters = listOf(LsiTypeParameter(baseParameterId, "T")),
                ),
                type(
                    id = childId,
                    parameters = listOf(LsiTypeParameter(childParameterId, "T")),
                    superTypes = listOf(
                        LsiDeclaredType(
                            declarationId = baseId,
                            arguments = listOf(
                                LsiTypeArgument.output(LsiTypeParameterRef(childParameterId)),
                            ),
                        )
                    ),
                ),
            ),
        )

        assertEquals(
            LsiDeclaredType(baseId, listOf(LsiTypeArgument.STAR)),
            LsiTypeSystem(workspace).resolveSuperType(
                LsiDeclaredType(childId, listOf(LsiTypeArgument.input(stringType))),
                baseId,
            ),
        )
    }

    @Test
    fun `normalizes raw generic types and rejects invalid argument counts`() {
        val baseId = LsiSymbolId.type("sample.Base")
        val childId = LsiSymbolId.type("sample.Child")
        val baseParameterId = LsiSymbolId.typeParameter(baseId, "T")
        val firstParameterId = LsiSymbolId.typeParameter(childId, "A")
        val secondParameterId = LsiSymbolId.typeParameter(childId, "B")
        val stringType = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    id = baseId,
                    parameters = listOf(LsiTypeParameter(baseParameterId, "T")),
                ),
                type(
                    id = childId,
                    parameters = listOf(
                        LsiTypeParameter(firstParameterId, "A"),
                        LsiTypeParameter(secondParameterId, "B"),
                    ),
                    superTypes = listOf(
                        LsiDeclaredType(
                            declarationId = baseId,
                            arguments = listOf(
                                LsiTypeArgument.invariant(LsiTypeParameterRef(firstParameterId)),
                            ),
                        )
                    ),
                ),
            ),
        )
        val typeSystem = LsiTypeSystem(workspace)

        assertEquals(
            LsiDeclaredType(baseId, listOf(LsiTypeArgument.STAR)),
            typeSystem.resolveSuperType(LsiDeclaredType(childId), baseId),
        )
        assertFailsWith<IllegalArgumentException> {
            typeSystem.resolveSuperType(
                LsiDeclaredType(childId, listOf(LsiTypeArgument.invariant(stringType))),
                baseId,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            typeSystem.resolveSuperType(
                LsiDeclaredType(
                    childId,
                    List(3) { LsiTypeArgument.invariant(stringType) },
                ),
                baseId,
            )
        }
    }

    @Test
    fun `checks nominal generic assignability with use site variance`() {
        val baseId = LsiSymbolId.type("sample.Base")
        val childId = LsiSymbolId.type("sample.Child")
        val charSequenceId = LsiSymbolId.type("java.lang.CharSequence")
        val stringId = LsiSymbolId.type("java.lang.String")
        val baseParameterId = LsiSymbolId.typeParameter(baseId, "T")
        val childParameterId = LsiSymbolId.typeParameter(childId, "T")
        val charSequenceType = LsiDeclaredType(charSequenceId)
        val stringType = LsiDeclaredType(stringId)
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(charSequenceId),
                type(stringId, superTypes = listOf(charSequenceType)),
                type(
                    id = baseId,
                    parameters = listOf(LsiTypeParameter(baseParameterId, "T")),
                ),
                type(
                    id = childId,
                    parameters = listOf(LsiTypeParameter(childParameterId, "T")),
                    superTypes = listOf(
                        LsiDeclaredType(
                            baseId,
                            listOf(LsiTypeArgument.invariant(LsiTypeParameterRef(childParameterId))),
                        )
                    ),
                ),
            ),
        )
        val typeSystem = LsiTypeSystem(workspace)
        val childOfString = LsiDeclaredType(
            childId,
            listOf(LsiTypeArgument.invariant(stringType)),
        )

        assertTrue(
            typeSystem.isAssignable(
                childOfString,
                LsiDeclaredType(baseId, listOf(LsiTypeArgument.output(charSequenceType))),
            )
        )
        assertFalse(
            typeSystem.isAssignable(
                childOfString,
                LsiDeclaredType(baseId, listOf(LsiTypeArgument.invariant(charSequenceType))),
            )
        )
        assertTrue(
            typeSystem.isAssignable(
                LsiDeclaredType(baseId, listOf(LsiTypeArgument.invariant(charSequenceType))),
                LsiDeclaredType(baseId, listOf(LsiTypeArgument.input(stringType))),
            )
        )
        assertTrue(
            typeSystem.isAssignable(
                childOfString,
                LsiDeclaredType(baseId, listOf(LsiTypeArgument.STAR)),
            )
        )
        assertFalse(
            typeSystem.isAssignable(
                LsiDeclaredType(baseId),
                LsiDeclaredType(baseId, listOf(LsiTypeArgument.invariant(stringType))),
            )
        )
        assertTrue(typeSystem.isAssignable(childOfString, LsiDeclaredType(baseId)))
    }

    @Test
    fun `honors declaration site variance and resolved nullability`() {
        val covariantId = LsiSymbolId.type("sample.Source")
        val contravariantId = LsiSymbolId.type("sample.Sink")
        val charSequenceId = LsiSymbolId.type("java.lang.CharSequence")
        val stringId = LsiSymbolId.type("java.lang.String")
        val sourceParameterId = LsiSymbolId.typeParameter(covariantId, "T")
        val sinkParameterId = LsiSymbolId.typeParameter(contravariantId, "T")
        val charSequenceType = LsiDeclaredType(charSequenceId)
        val stringType = LsiDeclaredType(stringId)
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(charSequenceId),
                type(stringId, superTypes = listOf(charSequenceType)),
                type(
                    covariantId,
                    parameters = listOf(
                        LsiTypeParameter(sourceParameterId, "T", variance = LsiVariance.OUT),
                    ),
                ),
                type(
                    contravariantId,
                    parameters = listOf(
                        LsiTypeParameter(sinkParameterId, "T", variance = LsiVariance.IN),
                    ),
                ),
            ),
        )
        val typeSystem = LsiTypeSystem(workspace)

        assertTrue(
            typeSystem.isAssignable(
                LsiDeclaredType(covariantId, listOf(LsiTypeArgument.invariant(stringType))),
                LsiDeclaredType(covariantId, listOf(LsiTypeArgument.invariant(charSequenceType))),
            )
        )
        assertTrue(
            typeSystem.isAssignable(
                LsiDeclaredType(contravariantId, listOf(LsiTypeArgument.invariant(charSequenceType))),
                LsiDeclaredType(contravariantId, listOf(LsiTypeArgument.invariant(stringType))),
            )
        )

        val nullableString = stringType.copy(nullability = LsiNullability.NULLABLE)
        assertTrue(typeSystem.isAssignable(nullableString, charSequenceType.copy(nullability = LsiNullability.NULLABLE)))
        assertFalse(typeSystem.isAssignable(nullableString, charSequenceType))
        assertEquals(
            LsiNullability.NULLABLE,
            typeSystem.resolveSuperType(nullableString, charSequenceId)?.nullability,
        )
    }

    @Test
    fun `checks primitive array function and type parameter assignability`() {
        val charSequenceId = LsiSymbolId.type("java.lang.CharSequence")
        val stringId = LsiSymbolId.type("java.lang.String")
        val ownerId = LsiSymbolId.type("sample.Owner")
        val parameterId = LsiSymbolId.typeParameter(ownerId, "T")
        val charSequenceType = LsiDeclaredType(charSequenceId)
        val stringType = LsiDeclaredType(stringId)
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(charSequenceId),
                type(stringId, superTypes = listOf(charSequenceType)),
                type(
                    ownerId,
                    parameters = listOf(
                        LsiTypeParameter(
                            id = parameterId,
                            name = "T",
                            upperBounds = listOf(charSequenceType),
                        )
                    ),
                ),
            ),
        )
        val typeSystem = LsiTypeSystem(workspace)
        val intType = LsiPrimitiveType(LsiPrimitiveKind.INT)

        assertTrue(typeSystem.isAssignable(intType, intType))
        assertFalse(typeSystem.isAssignable(intType, intType.copy(boxed = true)))
        assertFalse(
            typeSystem.isAssignable(
                intType,
                LsiPrimitiveType(LsiPrimitiveKind.LONG),
            )
        )
        assertTrue(
            typeSystem.isAssignable(
                LsiArrayType(stringType),
                LsiArrayType(stringType),
            )
        )
        assertFalse(
            typeSystem.isAssignable(
                LsiArrayType(stringType),
                LsiArrayType(charSequenceType),
            )
        )
        assertTrue(
            typeSystem.isAssignable(
                LsiFunctionType(
                    returnType = stringType,
                    parameterTypes = listOf(charSequenceType),
                ),
                LsiFunctionType(
                    returnType = charSequenceType,
                    parameterTypes = listOf(stringType),
                ),
            )
        )
        assertFalse(
            typeSystem.isAssignable(
                LsiFunctionType(returnType = stringType),
                LsiFunctionType(returnType = stringType, suspending = true),
            )
        )
        assertTrue(
            typeSystem.isAssignable(
                LsiTypeParameterRef(parameterId),
                charSequenceType,
            )
        )
        assertTrue(
            typeSystem.isAssignable(
                LsiTypeParameterRef(parameterId),
                LsiTypeParameterRef(parameterId),
            )
        )
        assertFalse(typeSystem.isAssignable(stringType, LsiTypeParameterRef(parameterId)))
        assertFalse(
            typeSystem.isAssignable(
                LsiUnresolvedType("Missing"),
                LsiUnresolvedType("Missing"),
            )
        )
    }

    @Test
    fun `uses fallback hierarchy only when workspace has no entry`() {
        val childId = LsiSymbolId.type("sample.Child")
        val baseId = LsiSymbolId.type("sample.Base")
        val fallback = LsiTypeHierarchyEntry(
            id = childId,
            qualifiedName = "sample.Child",
            kind = LsiTypeDeclarationKind.CLASS,
            directSuperTypes = listOf(LsiDeclaredType(baseId)),
        )

        assertTrue(
            LsiTypeSystem(LsiWorkspace(), listOf(fallback)).isAssignable(
                LsiDeclaredType(childId),
                LsiDeclaredType(baseId),
            )
        )
        assertFalse(
            LsiTypeSystem(
                workspace = LsiWorkspace(declarations = listOf(type(childId))),
                fallbackTypeHierarchy = listOf(fallback),
            ).isAssignable(
                LsiDeclaredType(childId),
                LsiDeclaredType(baseId),
            )
        )
    }

    @Test
    fun `preserves type parameter use annotations during substitution`() {
        val baseId = LsiSymbolId.type("sample.Base")
        val entityId = LsiSymbolId.type("sample.Entity")
        val parameterId = LsiSymbolId.typeParameter(baseId, "T")
        val valueId = LsiSymbolId.property(baseId, "value")
        val useSiteMarker = annotation(LsiSymbolId.type("sample.UseSiteMarker"), "use")
        val replacementMarker = annotation(LsiSymbolId.type("sample.ReplacementMarker"), "replacement")
        val base = type(
            id = baseId,
            parameters = listOf(LsiTypeParameter(parameterId, "T")),
            memberIds = listOf(valueId),
        )
        val value = property(
            id = valueId,
            ownerId = baseId,
            type = LsiTypeParameterRef(
                parameterId = parameterId,
                annotations = listOf(useSiteMarker),
            ),
        )
        val entity = type(
            id = entityId,
            superTypes = listOf(
                LsiDeclaredType(
                    declarationId = baseId,
                    arguments = listOf(
                        LsiTypeArgument.invariant(
                            LsiDeclaredType(
                                declarationId = LsiSymbolId.type("java.lang.String"),
                                annotations = listOf(replacementMarker),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val typeSystem = LsiTypeSystem(LsiWorkspace(declarations = listOf(base, value, entity)))

        val resolvedType = typeSystem.effectiveProperties(entityId).single().type
        assertEquals(
            listOf(useSiteMarker, replacementMarker),
            resolvedType.annotations,
        )
    }

    @Test
    fun `substitutes every position of a function type`() {
        val ownerId = LsiSymbolId.type("sample.Owner")
        val parameterId = LsiSymbolId.typeParameter(ownerId, "T")
        val marker = annotation(LsiSymbolId.type("sample.FunctionMarker"), "function")
        val replacement = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val functionType = LsiFunctionType(
            returnType = LsiTypeParameterRef(parameterId),
            receiverType = LsiTypeParameterRef(
                parameterId = parameterId,
                nullability = LsiNullability.NULLABLE,
            ),
            parameterTypes = listOf(
                LsiArrayType(LsiTypeParameterRef(parameterId)),
            ),
            suspending = true,
            annotations = listOf(marker),
        )
        val typeSystem = LsiTypeSystem(LsiWorkspace())

        val substituted = assertIs<LsiFunctionType>(
            typeSystem.substitute(
                type = functionType,
                substitutions = mapOf(parameterId to LsiTypeArgument.invariant(replacement)),
            ),
        )

        assertEquals(replacement, substituted.returnType)
        assertEquals(
            replacement.copy(nullability = LsiNullability.NULLABLE),
            substituted.receiverType,
        )
        assertEquals(
            replacement,
            assertIs<LsiArrayType>(substituted.parameterTypes.single()).elementType,
        )
        assertTrue(substituted.suspending)
        assertEquals(listOf(marker), substituted.annotations)
    }

    @Test
    fun `applies type parameter use metadata to a function replacement`() {
        val ownerId = LsiSymbolId.type("sample.Owner")
        val parameterId = LsiSymbolId.typeParameter(ownerId, "T")
        val useSiteMarker = annotation(LsiSymbolId.type("sample.UseSiteMarker"), "use")
        val replacementMarker = annotation(LsiSymbolId.type("sample.ReplacementMarker"), "replacement")
        val replacement = LsiFunctionType(
            returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
            annotations = listOf(replacementMarker),
        )
        val use = LsiTypeParameterRef(
            parameterId = parameterId,
            nullability = LsiNullability.NULLABLE,
            annotations = listOf(useSiteMarker),
        )

        val substituted = assertIs<LsiFunctionType>(
            LsiTypeSystem(LsiWorkspace()).substitute(
                type = use,
                substitutions = mapOf(parameterId to LsiTypeArgument.invariant(replacement)),
            ),
        )

        assertEquals(LsiNullability.NULLABLE, substituted.nullability)
        assertEquals(listOf(useSiteMarker, replacementMarker), substituted.annotations)
    }

    @Test
    fun `boxes primitive replacements for nullable type parameter uses`() {
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
            type = LsiTypeParameterRef(
                parameterId = parameterId,
                nullability = LsiNullability.NULLABLE,
            ),
        )
        val entity = type(
            id = entityId,
            superTypes = listOf(
                LsiDeclaredType(
                    declarationId = baseId,
                    arguments = listOf(
                        LsiTypeArgument.invariant(LsiPrimitiveType(LsiPrimitiveKind.INT)),
                    ),
                ),
            ),
        )
        val typeSystem = LsiTypeSystem(LsiWorkspace(declarations = listOf(base, value, entity)))

        val resolvedType = assertIs<LsiPrimitiveType>(
            typeSystem.effectiveProperties(entityId).single().type,
        )
        assertEquals(LsiNullability.NULLABLE, resolvedType.nullability)
        assertTrue(resolvedType.boxed)
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
