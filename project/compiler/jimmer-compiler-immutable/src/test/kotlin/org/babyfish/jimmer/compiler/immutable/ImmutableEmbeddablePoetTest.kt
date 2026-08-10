package org.babyfish.jimmer.compiler.immutable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiNullability
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class ImmutableEmbeddablePoetTest {

    @Test
    fun `same source artifacts are isolating and keep frontend metadata parity`() {
        val source = source("demo/Location.kt")
        val typeId = LsiSymbolId.type("demo.Location")
        val propId = LsiSymbolId.property(typeId, "name")
        val propType = LsiDeclaredType(STRING_ID)
        val prop = immutableProp(typeId, propId, "name", propType)
        val type = immutableType(typeId, listOf(prop))
        val workspace = LsiWorkspace(
            sources = listOf(source),
            declarations = listOf(
                declaration(typeId, source, memberIds = listOf(propId)),
                property(propId, typeId, "name", propType, source),
            ),
        )
        val schema = ImmutableSchema(listOf(type))

        val javaArtifacts = schema.toEmbeddablePoetArtifacts(
            types = listOf(type),
            language = LsiLanguage.JAVA,
            workspace = workspace,
        )
        val kotlinArtifacts = schema.toEmbeddablePoetArtifacts(
            types = listOf(type),
            language = LsiLanguage.KOTLIN,
            workspace = workspace,
        )

        assertTrue((javaArtifacts + kotlinArtifacts).all {
            it.aggregationMode == ArtifactAggregationMode.ISOLATING
        })
        assertTrue((javaArtifacts + kotlinArtifacts).all { artifact ->
            artifact.originatingSymbols == setOf(typeId) && artifact.originatingSources == setOf(source)
        })
        assertEquals(
            javaArtifacts.semanticDependencySymbols(workspace),
            kotlinArtifacts.semanticDependencySymbols(workspace),
        )
        assertEquals(setOf(typeId, propId), javaArtifacts.semanticDependencySymbols(workspace))
        assertEquals(setOf(source), javaArtifacts.flatMapTo(sortedSetOf()) { it.dependencySources })
        assertEquals(setOf(source), kotlinArtifacts.single().dependencySources)
    }

    @Test
    fun `cross source semantic inputs are aggregating and expression hierarchy stays precise`() {
        val ownerSource = source("demo/Location.kt")
        val customSource = source("shared/Code.kt")
        val baseSource = source("shared/BaseCode.kt")
        val annotationSource = source("shared/TypeMarker.kt")
        val targetSource = source("shared/Coordinates.kt")
        val parentSource = source("shared/Named.kt")
        val ownerId = LsiSymbolId.type("demo.Location")
        val customId = LsiSymbolId.type("shared.Code")
        val baseId = LsiSymbolId.type("shared.BaseCode")
        val markerId = LsiSymbolId.type("shared.TypeMarker")
        val targetId = LsiSymbolId.type("shared.Coordinates")
        val parentId = LsiSymbolId.type("shared.Named")
        val customPropId = LsiSymbolId.property(ownerId, "code")
        val nestedPropId = LsiSymbolId.property(ownerId, "coordinates")
        val effectiveInheritedPropId = LsiSymbolId.property(ownerId, "name")
        val parentPropId = LsiSymbolId.property(parentId, "name")
        val customType = LsiDeclaredType(
            declarationId = customId,
            annotations = listOf(LsiAnnotation(markerId)),
        )
        val customProp = immutableProp(ownerId, customPropId, "code", customType)
        val nestedProp = immutableProp(
            ownerTypeId = ownerId,
            id = nestedPropId,
            name = "coordinates",
            type = LsiDeclaredType(targetId),
            embedded = true,
            targetTypeId = targetId,
        )
        val inheritedProp = immutableProp(
            ownerTypeId = ownerId,
            id = effectiveInheritedPropId,
            name = "name",
            type = LsiDeclaredType(STRING_ID),
            declarationId = parentPropId,
            declaringTypeId = parentId,
            inherited = true,
        )
        val ownerType = immutableType(ownerId, listOf(customProp, nestedProp, inheritedProp))
        val targetType = immutableType(targetId, emptyList())
        val workspace = LsiWorkspace(
            sources = listOf(
                ownerSource,
                customSource,
                baseSource,
                annotationSource,
                targetSource,
                parentSource,
            ),
            declarations = listOf(
                declaration(
                    ownerId,
                    ownerSource,
                    memberIds = listOf(customPropId, nestedPropId),
                ),
                property(customPropId, ownerId, "code", customType, ownerSource),
                property(nestedPropId, ownerId, "coordinates", LsiDeclaredType(targetId), ownerSource),
                declaration(
                    customId,
                    customSource,
                    superTypes = listOf(LsiDeclaredType(baseId)),
                ),
                declaration(
                    baseId,
                    baseSource,
                    superTypes = listOf(LsiDeclaredType(COMPARABLE_ID)),
                ),
                declaration(
                    markerId,
                    annotationSource,
                    kind = LsiTypeDeclarationKind.ANNOTATION,
                ),
                declaration(targetId, targetSource),
                declaration(parentId, parentSource, memberIds = listOf(parentPropId)),
                property(parentPropId, parentId, "name", LsiDeclaredType(STRING_ID), parentSource),
            ),
        )
        val schema = ImmutableSchema(listOf(ownerType, targetType))

        val javaArtifacts = schema.toEmbeddablePoetArtifacts(
            types = listOf(ownerType),
            language = LsiLanguage.JAVA,
            workspace = workspace,
        )
        val kotlinArtifact = schema.toEmbeddablePoetArtifacts(
            types = listOf(ownerType),
            language = LsiLanguage.KOTLIN,
            workspace = workspace,
        ).single()
        val javaProps = javaArtifacts.single { it.file.fileName == "LocationProps" }
        val javaExpression = javaArtifacts.single { it.file.fileName == "LocationPropExpression" }

        assertTrue((javaArtifacts + kotlinArtifact).all {
            it.aggregationMode == ArtifactAggregationMode.AGGREGATING
        })
        val commonDependencyIds = setOf(
            customId,
            markerId,
            targetId,
            parentId,
            parentPropId,
        )
        assertTrue(javaProps.dependencySymbols.containsAll(commonDependencyIds))
        assertTrue(javaExpression.dependencySymbols.containsAll(commonDependencyIds + baseId))
        assertTrue(kotlinArtifact.dependencySymbols.containsAll(commonDependencyIds))
        assertFalse(baseId in javaProps.dependencySymbols)
        assertFalse(baseSource in javaProps.dependencySources)
        assertTrue(baseId in javaExpression.dependencySymbols)
        assertTrue(baseSource in javaExpression.dependencySources)
        assertFalse(baseId in kotlinArtifact.dependencySymbols)
        assertFalse(baseSource in kotlinArtifact.dependencySources)
        assertTrue(annotationSource in javaProps.dependencySources)
        assertTrue(annotationSource in kotlinArtifact.dependencySources)
    }

    @Test
    fun `binary semantic dependencies do not make artifacts aggregating`() {
        val ownerSource = source("demo/Location.kt")
        val binarySource = source(
            path = "binary/shared/Code.class",
            kind = LsiSourceKind.BINARY,
        )
        val ownerId = LsiSymbolId.type("demo.Location")
        val binaryTypeId = LsiSymbolId.type("shared.Code")
        val propId = LsiSymbolId.property(ownerId, "code")
        val propType = LsiDeclaredType(binaryTypeId)
        val prop = immutableProp(ownerId, propId, "code", propType)
        val type = immutableType(ownerId, listOf(prop))
        val workspace = LsiWorkspace(
            sources = listOf(ownerSource, binarySource),
            declarations = listOf(
                declaration(ownerId, ownerSource, memberIds = listOf(propId)),
                property(propId, ownerId, "code", propType, ownerSource),
                declaration(
                    id = binaryTypeId,
                    source = binarySource,
                    originKind = LsiOriginKind.BINARY,
                ),
            ),
        )
        val schema = ImmutableSchema(listOf(type))

        val artifacts = listOf(LsiLanguage.JAVA, LsiLanguage.KOTLIN).flatMap { language ->
            schema.toEmbeddablePoetArtifacts(listOf(type), language, workspace)
        }

        assertTrue(artifacts.all { it.aggregationMode == ArtifactAggregationMode.ISOLATING })
        assertTrue(artifacts.all { binaryTypeId in it.dependencySymbols })
        assertTrue(artifacts.all { it.dependencySources == setOf(ownerSource) })
    }

    @Test
    fun `type use annotation values remain artifact dependencies`() {
        val ownerSource = source("demo/Location.kt")
        val annotationSource = source("shared/TypeMarker.kt")
        val payloadSource = source("shared/Payload.kt")
        val enumSource = source("shared/Mode.kt")
        val nestedSource = source("shared/NestedMarker.kt")
        val ownerId = LsiSymbolId.type("demo.Location")
        val markerId = LsiSymbolId.type("shared.TypeMarker")
        val payloadId = LsiSymbolId.type("shared.Payload")
        val enumId = LsiSymbolId.type("shared.Mode")
        val nestedId = LsiSymbolId.type("shared.NestedMarker")
        val propId = LsiSymbolId.property(ownerId, "name")
        val annotation = LsiAnnotation(
            type = markerId,
            arguments = mapOf(
                "payload" to explicit(
                    LsiAnnotationValue.ClassValue(LsiDeclaredType(payloadId))
                ),
                "mode" to explicit(
                    LsiAnnotationValue.EnumValue(enumId, "PRIMARY")
                ),
                "nested" to explicit(
                    LsiAnnotationValue.ArrayValue(
                        listOf(
                            LsiAnnotationValue.NestedAnnotationValue(
                                LsiAnnotation(nestedId)
                            )
                        )
                    )
                ),
            ),
        )
        val propType = LsiDeclaredType(
            declarationId = STRING_ID,
            annotations = listOf(annotation),
        )
        val prop = immutableProp(ownerId, propId, "name", propType)
        val type = immutableType(ownerId, listOf(prop))
        val workspace = LsiWorkspace(
            sources = listOf(
                ownerSource,
                annotationSource,
                payloadSource,
                enumSource,
                nestedSource,
            ),
            declarations = listOf(
                declaration(ownerId, ownerSource, memberIds = listOf(propId)),
                property(propId, ownerId, "name", propType, ownerSource),
                declaration(
                    markerId,
                    annotationSource,
                    kind = LsiTypeDeclarationKind.ANNOTATION,
                ),
                declaration(payloadId, payloadSource),
                declaration(enumId, enumSource, kind = LsiTypeDeclarationKind.ENUM),
                declaration(
                    nestedId,
                    nestedSource,
                    kind = LsiTypeDeclarationKind.ANNOTATION,
                ),
            ),
        )
        val schema = ImmutableSchema(listOf(type))

        val artifacts = listOf(LsiLanguage.JAVA, LsiLanguage.KOTLIN).flatMap { language ->
            schema.toEmbeddablePoetArtifacts(listOf(type), language, workspace)
        }
        val annotationDependencyIds = setOf(markerId, payloadId, enumId, nestedId)
        val annotationDependencySources = setOf(
            annotationSource,
            payloadSource,
            enumSource,
            nestedSource,
        )

        assertTrue(artifacts.all { it.aggregationMode == ArtifactAggregationMode.AGGREGATING })
        assertTrue(artifacts.all { it.dependencySymbols.containsAll(annotationDependencyIds) })
        assertTrue(artifacts.all { it.dependencySources.containsAll(annotationDependencySources) })
    }

    @Test
    fun `default package generated names do not gain a leading dot`() {
        val source = source("Location.kt")
        val typeId = LsiSymbolId.type("Location")
        val propId = LsiSymbolId.property(typeId, "name")
        val propType = LsiDeclaredType(STRING_ID)
        val prop = immutableProp(typeId, propId, "name", propType)
        val type = immutableType(typeId, listOf(prop))
        val workspace = LsiWorkspace(
            sources = listOf(source),
            declarations = listOf(
                declaration(typeId, source, memberIds = listOf(propId)),
                property(propId, typeId, "name", propType, source),
            ),
        )
        val schema = ImmutableSchema(listOf(type))
        val javaArtifacts = schema.toEmbeddablePoetArtifacts(
            types = listOf(type),
            language = LsiLanguage.JAVA,
            workspace = workspace,
        )
        val kotlinArtifact = schema.toEmbeddablePoetArtifacts(
            types = listOf(type),
            language = LsiLanguage.KOTLIN,
            workspace = workspace,
        ).single()

        assertEquals(
            setOf("LocationProps", "LocationPropExpression"),
            javaArtifacts.mapTo(linkedSetOf(), LsiSourceArtifact::qualifiedFileName),
        )
        assertEquals("LocationProps", kotlinArtifact.qualifiedFileName)
        javaArtifacts.forEach(LsiJavaPoetRenderer()::render)
        LsiKotlinPoetRenderer().render(kotlinArtifact)
    }

    private fun List<LsiSourceArtifact>.semanticDependencySymbols(
        workspace: LsiWorkspace,
    ): Set<LsiSymbolId> {
        return flatMapTo(sortedSetOf()) { artifact ->
            artifact.dependencySymbols.filter(workspace::contains)
        }
    }

    private fun explicit(value: LsiAnnotationValue): LsiAnnotationArgument {
        return LsiAnnotationArgument(
            value = value,
            origin = LsiAnnotationArgumentOrigin.EXPLICIT,
        )
    }

    private fun source(
        path: String,
        kind: LsiSourceKind = LsiSourceKind.SOURCE,
    ): LsiSource {
        return LsiSource.of(path, LsiLanguage.KOTLIN, kind)
    }

    private fun declaration(
        id: LsiSymbolId,
        source: LsiSource,
        memberIds: List<LsiSymbolId> = emptyList(),
        superTypes: List<LsiType> = emptyList(),
        kind: LsiTypeDeclarationKind = LsiTypeDeclarationKind.INTERFACE,
        originKind: LsiOriginKind = LsiOriginKind.SOURCE,
    ): LsiClass {
        val qualifiedName = id.requireTypeQualifiedName()
        return LsiClass(
            id = id,
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = kind,
            superTypes = superTypes,
            memberIds = memberIds,
            origin = LsiOrigin(originKind, source),
        )
    }

    private fun property(
        id: LsiSymbolId,
        ownerId: LsiSymbolId,
        name: String,
        type: LsiType,
        source: LsiSource,
    ): LsiProperty {
        return LsiProperty(
            id = id,
            name = name,
            ownerId = ownerId,
            type = type,
            origin = LsiOrigin(LsiOriginKind.SOURCE, source),
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        props: List<ImmutableProp>,
    ): ImmutableType {
        return ImmutableType(
            id = id,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = ImmutableTypeKind.EMBEDDABLE,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = emptyList(),
            props = props,
            primarySuperTypeId = null,
            inheritanceRootTypeId = null,
            inheritanceStrategy = null,
            joinedTableDissociateAction = null,
            instantiable = false,
            discriminatorValue = null,
            discriminatorPropId = null,
            idPropId = null,
            versionPropId = null,
            logicalDeletedPropId = null,
            acrossMicroServices = false,
            microServiceName = "",
        )
    }

    private fun immutableProp(
        ownerTypeId: LsiSymbolId,
        id: LsiSymbolId,
        name: String,
        type: LsiType,
        declarationId: LsiSymbolId = id,
        declaringTypeId: LsiSymbolId = ownerTypeId,
        inherited: Boolean = false,
        embedded: Boolean = false,
        targetTypeId: LsiSymbolId? = null,
    ): ImmutableProp {
        return ImmutableProp(
            id = id,
            declarationId = declarationId,
            ownerTypeId = ownerTypeId,
            declaringTypeId = declaringTypeId,
            name = name,
            documentation = null,
            type = type,
            annotations = emptyList(),
            overrideChain = listOf(declarationId),
            inherited = inherited,
            overridden = false,
            nullable = type.nullability == LsiNullability.NULLABLE,
            list = false,
            association = false,
            embedded = embedded,
            targetTypeId = targetTypeId,
            primaryMapping = PrimaryMapping.SCALAR,
            primaryAnnotationTypeId = null,
            defaultContract = null,
            associationKind = AssociationKind.NONE,
            formulaKind = FormulaKind.NONE,
            mappedBy = null,
            associationStorage = AssociationStorageKind.NONE,
            transientResolver = null,
            view = null,
            genericTarget = false,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = null,
        )
    }

    private companion object {
        val STRING_ID = LsiSymbolId.type("java.lang.String")
        val COMPARABLE_ID = LsiSymbolId.type("java.lang.Comparable")
    }
}
