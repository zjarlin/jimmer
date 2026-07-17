package org.babyfish.jimmer.compiler.immutable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiOverride
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutablePrecompilerTest {

    @Test
    fun `entity overrides mapped superclass default annotation after generic substitution`() {
        val schema = JimmerImmutablePrecompiler().compile(overrideWorkspace(LsiLanguage.KOTLIN))

        val entity = schema.types.single { type -> type.kind == JimmerImmutableTypeKind.ENTITY }
        val status = entity.props.single()
        val statusType = assertIs<LsiDeclaredType>(status.type)
        assertEquals(STRING_TYPE, statusType.declarationId)
        assertTrue(status.overridden)
        assertFalse(status.inherited)
        assertEquals(JimmerImmutablePrimaryMapping.SCALAR, status.primaryMapping)
        assertEquals(
            listOf(
                LsiSymbolId.property(ENTITY_TYPE, "status"),
                LsiSymbolId.property(BASE_TYPE, "status"),
            ),
            status.overrideChain,
        )
        assertEquals("1", status.annotationString(DEFAULT, "value"))
        assertEquals("STATUS", status.annotationString(COLUMN, "name"))
    }

    @Test
    fun `apt and ksp equivalent workspaces have identical immutable snapshots`() {
        val aptSchema = JimmerImmutablePrecompiler().compile(overrideWorkspace(LsiLanguage.JAVA))
        val kspSchema = JimmerImmutablePrecompiler().compile(overrideWorkspace(LsiLanguage.KOTLIN))

        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())
        assertEquals(64, aptSchema.fingerprint().length)
    }

    @Test
    fun `preserves direct super and inherited declaration order across apt and ksp`() {
        val aptSchema = JimmerImmutablePrecompiler().compile(orderedHierarchyWorkspace(LsiLanguage.JAVA))
        val kspSchema = JimmerImmutablePrecompiler().compile(orderedHierarchyWorkspace(LsiLanguage.KOTLIN))
        val entityId = LsiSymbolId.type("demo.OrderedEntity")
        val primarySuperTypeId = LsiSymbolId.type("demo.APrimaryEntity")
        val aptEntity = aptSchema.types.single { type -> type.id == entityId }
        val expectedPropNames = listOf(
            "auditZ",
            "auditA",
            "rootZ",
            "rootA",
            "tenantZ",
            "tenantA",
            "childZ",
            "childA",
        )

        assertEquals(
            listOf(
                LsiSymbolId.type("demo.ZAuditBase"),
                primarySuperTypeId,
                LsiSymbolId.type("demo.MTenantBase"),
            ),
            aptEntity.superTypeIds,
        )
        assertEquals(primarySuperTypeId, aptEntity.primarySuperTypeId)
        assertEquals(expectedPropNames, aptEntity.props.map(JimmerImmutableProp::name))
        assertEquals(
            LsiSymbolId.property(entityId, "auditZ"),
            aptEntity.props.first().declarationId,
        )
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())
        val snapshotLines = aptSchema.normalizedSnapshot().lineSequence().toList()
        val entitySnapshot = snapshotLines.single { line ->
            line.startsWith("type|${entityId.value}|")
        }
        assertEquals(primarySuperTypeId.value, entitySnapshot.split('|')[6])
        val entitySnapshotPropNames = snapshotLines
            .asSequence()
            .filter { line -> line.startsWith("prop|${entityId.value}|") }
            .map { line -> line.split('|')[5] }
            .toList()
        assertEquals(expectedPropNames, entitySnapshotPropNames)
    }

    @Test
    fun `classifies immutable property mapping categories`() {
        val authorId = LsiSymbolId.type("demo.Author")
        val addressId = LsiSymbolId.type("demo.Address")
        val payloadId = LsiSymbolId.type("demo.Payload")
        val bookId = LsiSymbolId.type("demo.Book")
        val properties = listOf(
            property(bookId, "id", LsiPrimitiveType(LsiPrimitiveKind.LONG), listOf(annotation(ID))),
            property(bookId, "version", LsiPrimitiveType(LsiPrimitiveKind.INT), listOf(annotation(VERSION))),
            property(
                bookId,
                "deleted",
                LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
                listOf(annotation(LOGICAL_DELETED)),
            ),
            property(bookId, "author", LsiDeclaredType(authorId), listOf(annotation(MANY_TO_ONE))),
            property(
                bookId,
                "displayName",
                LsiDeclaredType(STRING_TYPE),
                listOf(formula(sql = "NAME")),
                modality = LsiModality.ABSTRACT,
            ),
            property(bookId, "temporary", LsiDeclaredType(STRING_TYPE), listOf(annotation(TRANSIENT))),
            property(bookId, "authorId", LsiPrimitiveType(LsiPrimitiveKind.LONG), listOf(annotation(ID_VIEW))),
            property(
                bookId,
                "authors",
                listType(authorId),
            ),
            property(
                bookId,
                "authorView",
                listType(authorId),
                listOf(annotation(MANY_TO_MANY_VIEW)),
            ),
            property(
                bookId,
                "description",
                LsiDeclaredType(STRING_TYPE, nullability = LsiNullability.NULLABLE),
            ),
            property(bookId, "address", LsiDeclaredType(addressId)),
            property(bookId, "payload", LsiDeclaredType(payloadId)),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type("demo.Author", ENTITY, emptyList()),
                type("demo.Address", EMBEDDABLE, emptyList()),
                type("demo.Payload", IMMUTABLE, emptyList()),
                type("demo.Book", ENTITY, properties.map(LsiProperty::id)),
            ) + properties,
        )

        val book = JimmerImmutablePrecompiler().compile(workspace)
            .types.single { type -> type.id == bookId }
        val props = book.props.associateBy(JimmerImmutableProp::name)

        assertEquals(JimmerImmutablePrimaryMapping.ID, props.getValue("id").primaryMapping)
        assertEquals(JimmerImmutablePrimaryMapping.VERSION, props.getValue("version").primaryMapping)
        assertEquals(
            JimmerImmutablePrimaryMapping.LOGICAL_DELETED,
            props.getValue("deleted").primaryMapping,
        )
        assertEquals(JimmerImmutablePrimaryMapping.ASSOCIATION, props.getValue("author").primaryMapping)
        assertEquals(JimmerAssociationKind.MANY_TO_ONE, props.getValue("author").associationKind)
        assertEquals(JimmerImmutablePrimaryMapping.FORMULA, props.getValue("displayName").primaryMapping)
        assertEquals(JimmerFormulaKind.SQL, props.getValue("displayName").formulaKind)
        assertEquals(JimmerImmutablePrimaryMapping.TRANSIENT, props.getValue("temporary").primaryMapping)
        assertEquals(JimmerImmutablePrimaryMapping.VIEW, props.getValue("authorId").primaryMapping)
        assertEquals(JimmerViewKind.ID, props.getValue("authorId").viewKind)
        assertEquals(JimmerAssociationKind.IMPLICIT, props.getValue("authors").associationKind)
        assertTrue(props.getValue("authors").list)
        assertTrue(props.getValue("authors").association)
        assertEquals(JimmerImmutablePrimaryMapping.VIEW, props.getValue("authorView").primaryMapping)
        assertEquals(JimmerViewKind.MANY_TO_MANY, props.getValue("authorView").viewKind)
        assertTrue(props.getValue("description").nullable)
        assertEquals(JimmerImmutablePrimaryMapping.SCALAR, props.getValue("description").primaryMapping)
        assertFalse(props.getValue("address").association)
        assertTrue(props.getValue("address").embedded)
        assertFalse(props.getValue("payload").association)
        assertFalse(props.getValue("payload").embedded)
    }

    @Test
    fun `extracts validation and converter typed metadata`() {
        val entityId = LsiSymbolId.type("demo.CodeEntity")
        val property = property(
            ownerId = entityId,
            name = "code",
            type = LsiDeclaredType(STRING_TYPE, nullability = LsiNullability.NULLABLE),
            annotations = listOf(
                annotation(
                    VALID_CODE,
                    mapOf("message" to LsiAnnotationValue.StringValue("invalid code")),
                ),
                annotation(CODE_FORMAT),
            ),
        )
        val validCodeType = declaration(
            qualifiedName = "demo.ValidCode",
            kind = LsiTypeDeclarationKind.ANNOTATION,
            annotations = listOf(
                annotation(
                    JAKARTA_CONSTRAINT,
                    mapOf(
                        "validatedBy" to LsiAnnotationValue.ArrayValue(
                            listOf(
                                LsiAnnotationValue.ClassValue(LsiDeclaredType(CODE_VALIDATOR))
                            )
                        )
                    ),
                )
            ),
        )
        val codeFormatType = declaration(
            qualifiedName = "demo.CodeFormat",
            kind = LsiTypeDeclarationKind.ANNOTATION,
            annotations = listOf(
                annotation(
                    JSON_CONVERTER,
                    mapOf(
                        "value" to LsiAnnotationValue.ClassValue(LsiDeclaredType(CODE_CONVERTER))
                    ),
                )
            ),
        )
        val converterType = declaration(
            qualifiedName = "demo.CodeConverter",
            kind = LsiTypeDeclarationKind.CLASS,
            superTypes = listOf(
                LsiDeclaredType(
                    declarationId = CONVERTER,
                    arguments = listOf(
                        LsiTypeArgument.invariant(LsiDeclaredType(STRING_TYPE)),
                        LsiTypeArgument.invariant(
                            LsiPrimitiveType(
                                LsiPrimitiveKind.LONG,
                                nullability = LsiNullability.NULLABLE,
                            )
                        ),
                    ),
                )
            ),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type("demo.CodeEntity", ENTITY, listOf(property.id)),
                property,
                validCodeType,
                codeFormatType,
                converterType,
                declaration("demo.CodeValidator", LsiTypeDeclarationKind.CLASS),
            ),
        )

        val schema = JimmerImmutablePrecompiler().compile(workspace)
        val prop = schema.types.single().props.single()

        val validation = prop.validations.single()
        assertEquals(VALID_CODE, validation.annotationTypeId)
        assertEquals(listOf(CODE_VALIDATOR), validation.validatorTypeIds)
        assertEquals("invalid code", validation.message)
        val converter = requireNotNull(prop.converter)
        assertEquals(CODE_CONVERTER, converter.converterTypeId)
        assertEquals(STRING_TYPE, assertIs<LsiDeclaredType>(converter.sourceType).declarationId)
        assertEquals(LsiPrimitiveKind.LONG, assertIs<LsiPrimitiveType>(converter.targetType).kind)
        assertFalse(converter.sourceNullable)
        assertTrue(converter.targetNullable)
        assertTrue(converter.propertyNullable)
        assertTrue(schema.normalizedSnapshot().contains("validation"))
        assertTrue(schema.normalizedSnapshot().contains("converter"))
    }

    @Test
    fun `only entity can override direct mapped superclass property`() {
        val mappedChild = overrideCategoryWorkspace(
            baseMarker = MAPPED_SUPERCLASS,
            childMarker = MAPPED_SUPERCLASS,
            baseAnnotations = listOf(default("0", LsiLanguage.KOTLIN)),
            childAnnotations = listOf(default("1", LsiLanguage.KOTLIN)),
        )
        val mappedException = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(mappedChild)
        }
        assertTrue(mappedException.message.orEmpty().contains("mapped superclass of an entity"))

        val entityParent = overrideCategoryWorkspace(
            baseMarker = ENTITY,
            childMarker = ENTITY,
            baseAnnotations = listOf(default("0", LsiLanguage.KOTLIN)),
            childAnnotations = listOf(default("1", LsiLanguage.KOTLIN)),
        )
        val entityException = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(entityParent)
        }
        assertTrue(entityException.message.orEmpty().contains("mapped superclass of an entity"))
    }

    @Test
    fun `ordinary property override does not require mapped superclass annotation override rules`() {
        val workspace = overrideCategoryWorkspace(
            baseMarker = ENTITY,
            childMarker = ENTITY,
            baseAnnotations = listOf(annotation(JAVA_OVERRIDE)),
            childAnnotations = listOf(annotation(JAVA_OVERRIDE)),
        )

        val schema = JimmerImmutablePrecompiler().compile(workspace)

        assertEquals(2, schema.types.size)
        assertTrue(
            schema.types.single { type -> type.id == LsiSymbolId.type("demo.Child") }
                .props
                .single()
                .overridden
        )
    }

    @Test
    fun `rejects overridden property type nullability and list changes`() {
        assertOverrideRejected(
            baseType = LsiDeclaredType(STRING_TYPE),
            childType = LsiPrimitiveType(LsiPrimitiveKind.INT),
            expected = "resolved type",
        )
        assertOverrideRejected(
            baseType = LsiDeclaredType(STRING_TYPE),
            childType = LsiDeclaredType(STRING_TYPE, nullability = LsiNullability.NULLABLE),
            expected = "nullability",
        )
        assertOverrideRejected(
            baseType = LsiDeclaredType(STRING_TYPE),
            childType = listType(STRING_TYPE),
            expected = "list category",
        )
    }

    @Test
    fun `rejects overridden primary association and formula category changes`() {
        val targetId = LsiSymbolId.type("demo.Target")
        assertOverrideRejected(
            baseType = LsiDeclaredType(targetId),
            childType = LsiDeclaredType(targetId),
            baseAnnotations = listOf(annotation(MANY_TO_ONE)),
            childAnnotations = listOf(annotation(ONE_TO_ONE)),
            expected = "primary mapping annotation",
            extraTypes = listOf(type("demo.Target", ENTITY, emptyList())),
        )
        assertOverrideRejected(
            baseType = LsiDeclaredType(STRING_TYPE),
            childType = LsiDeclaredType(STRING_TYPE),
            baseAnnotations = listOf(formula(sql = "NAME")),
            childAnnotations = listOf(formula(dependencies = listOf("name"))),
            baseModality = LsiModality.ABSTRACT,
            childModality = LsiModality.FINAL,
            expected = "formula kind",
        )
    }

    private fun assertOverrideRejected(
        baseType: LsiTypeRef,
        childType: LsiTypeRef,
        baseAnnotations: List<LsiAnnotation> = emptyList(),
        childAnnotations: List<LsiAnnotation> = emptyList(),
        baseModality: LsiModality = LsiModality.ABSTRACT,
        childModality: LsiModality = LsiModality.ABSTRACT,
        expected: String,
        extraTypes: List<LsiTypeDeclaration> = emptyList(),
    ) {
        val workspace = overrideCategoryWorkspace(
            baseMarker = MAPPED_SUPERCLASS,
            childMarker = ENTITY,
            baseType = baseType,
            childType = childType,
            baseAnnotations = baseAnnotations,
            childAnnotations = childAnnotations,
            baseModality = baseModality,
            childModality = childModality,
            extraTypes = extraTypes,
        )
        val exception = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(workspace)
        }
        assertTrue(exception.message.orEmpty().contains(expected))
    }

    private fun overrideWorkspace(language: LsiLanguage): LsiWorkspace {
        val origin = sourceOrigin(language)
        val parameterId = LsiSymbolId.typeParameter(BASE_TYPE, "T")
        val basePropertyId = LsiSymbolId.property(BASE_TYPE, "status")
        val entityPropertyId = LsiSymbolId.property(ENTITY_TYPE, "status")
        val nullability = if (language == LsiLanguage.JAVA) {
            LsiNullability.PLATFORM
        } else {
            LsiNullability.NON_NULL
        }
        val base = type(
            qualifiedName = "demo.BaseOnlyOneSwitch",
            marker = MAPPED_SUPERCLASS,
            memberIds = listOf(basePropertyId),
            typeParameters = listOf(LsiTypeParameter(parameterId, "T")),
            origin = origin,
        )
        val baseProperty = property(
            ownerId = BASE_TYPE,
            name = "status",
            type = LsiTypeParameterRef(parameterId, nullability),
            annotations = listOf(
                default("0", language),
                annotation(
                    COLUMN,
                    mapOf("name" to LsiAnnotationValue.StringValue("STATUS")),
                ),
            ),
            origin = origin,
        )
        val entity = type(
            qualifiedName = "demo.OnlyOneSwitch",
            marker = ENTITY,
            memberIds = listOf(entityPropertyId),
            superTypes = listOf(
                LsiDeclaredType(
                    declarationId = BASE_TYPE,
                    arguments = listOf(
                        LsiTypeArgument.invariant(
                            LsiDeclaredType(STRING_TYPE, nullability = nullability)
                        )
                    ),
                )
            ),
            origin = origin,
        )
        val entityProperty = property(
            ownerId = ENTITY_TYPE,
            name = "status",
            type = LsiDeclaredType(STRING_TYPE, nullability = nullability),
            annotations = listOf(default("1", language)),
            overrides = listOf(LsiOverride(basePropertyId)),
            origin = origin,
        )
        return LsiWorkspace(
            sources = listOf(requireNotNull(origin.source)),
            declarations = listOf(base, baseProperty, entity, entityProperty),
        )
    }

    private fun orderedHierarchyWorkspace(language: LsiLanguage): LsiWorkspace {
        val origin = sourceOrigin(language)
        val auditTypeId = LsiSymbolId.type("demo.ZAuditBase")
        val primaryTypeId = LsiSymbolId.type("demo.APrimaryEntity")
        val tenantTypeId = LsiSymbolId.type("demo.MTenantBase")
        val entityTypeId = LsiSymbolId.type("demo.OrderedEntity")
        val auditProps = listOf(
            property(auditTypeId, "auditZ", LsiDeclaredType(STRING_TYPE), origin = origin),
            property(auditTypeId, "auditA", LsiDeclaredType(STRING_TYPE), origin = origin),
        )
        val primaryProps = listOf(
            property(primaryTypeId, "rootZ", LsiDeclaredType(STRING_TYPE), origin = origin),
            property(primaryTypeId, "rootA", LsiDeclaredType(STRING_TYPE), origin = origin),
        )
        val tenantProps = listOf(
            property(tenantTypeId, "tenantZ", LsiDeclaredType(STRING_TYPE), origin = origin),
            property(tenantTypeId, "tenantA", LsiDeclaredType(STRING_TYPE), origin = origin),
        )
        val entityProps = listOf(
            property(entityTypeId, "childZ", LsiDeclaredType(STRING_TYPE), origin = origin),
            property(
                ownerId = entityTypeId,
                name = "auditZ",
                type = LsiDeclaredType(STRING_TYPE),
                overrides = listOf(LsiOverride(auditProps.first().id)),
                origin = origin,
            ),
            property(entityTypeId, "childA", LsiDeclaredType(STRING_TYPE), origin = origin),
        )
        val declarations = listOf(
            type(
                qualifiedName = "demo.ZAuditBase",
                marker = MAPPED_SUPERCLASS,
                memberIds = auditProps.map(LsiProperty::id),
                origin = origin,
            ),
            type(
                qualifiedName = "demo.APrimaryEntity",
                marker = ENTITY,
                memberIds = primaryProps.map(LsiProperty::id),
                origin = origin,
            ),
            type(
                qualifiedName = "demo.MTenantBase",
                marker = MAPPED_SUPERCLASS,
                memberIds = tenantProps.map(LsiProperty::id),
                origin = origin,
            ),
            type(
                qualifiedName = "demo.OrderedEntity",
                marker = ENTITY,
                memberIds = entityProps.map(LsiProperty::id),
                superTypes = listOf(
                    LsiDeclaredType(auditTypeId),
                    LsiDeclaredType(primaryTypeId),
                    LsiDeclaredType(tenantTypeId),
                ),
                origin = origin,
            ),
        ) + auditProps + primaryProps + tenantProps + entityProps
        return LsiWorkspace(
            sources = listOf(requireNotNull(origin.source)),
            declarations = if (language == LsiLanguage.JAVA) declarations else declarations.reversed(),
        )
    }

    private fun overrideCategoryWorkspace(
        baseMarker: LsiSymbolId,
        childMarker: LsiSymbolId,
        baseType: LsiTypeRef = LsiDeclaredType(STRING_TYPE),
        childType: LsiTypeRef = baseType,
        baseAnnotations: List<LsiAnnotation> = emptyList(),
        childAnnotations: List<LsiAnnotation> = emptyList(),
        baseModality: LsiModality = LsiModality.ABSTRACT,
        childModality: LsiModality = LsiModality.ABSTRACT,
        extraTypes: List<LsiTypeDeclaration> = emptyList(),
    ): LsiWorkspace {
        val baseId = LsiSymbolId.type("demo.Base")
        val childId = LsiSymbolId.type("demo.Child")
        val basePropertyId = LsiSymbolId.property(baseId, "value")
        val childPropertyId = LsiSymbolId.property(childId, "value")
        return LsiWorkspace(
            declarations = listOf(
                type("demo.Base", baseMarker, listOf(basePropertyId)),
                property(
                    ownerId = baseId,
                    name = "value",
                    type = baseType,
                    annotations = baseAnnotations,
                    modality = baseModality,
                ),
                type(
                    qualifiedName = "demo.Child",
                    marker = childMarker,
                    memberIds = listOf(childPropertyId),
                    superTypes = listOf(LsiDeclaredType(baseId)),
                ),
                property(
                    ownerId = childId,
                    name = "value",
                    type = childType,
                    annotations = childAnnotations,
                    overrides = listOf(LsiOverride(basePropertyId)),
                    modality = childModality,
                ),
            ) + extraTypes,
        )
    }

    private fun type(
        qualifiedName: String,
        marker: LsiSymbolId,
        memberIds: List<LsiSymbolId>,
        typeParameters: List<LsiTypeParameter> = emptyList(),
        superTypes: List<LsiTypeRef> = emptyList(),
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiTypeDeclaration {
        return declaration(
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.INTERFACE,
            annotations = listOf(annotation(marker)),
            memberIds = memberIds,
            typeParameters = typeParameters,
            superTypes = superTypes,
            origin = origin,
        )
    }

    private fun declaration(
        qualifiedName: String,
        kind: LsiTypeDeclarationKind,
        annotations: List<LsiAnnotation> = emptyList(),
        memberIds: List<LsiSymbolId> = emptyList(),
        typeParameters: List<LsiTypeParameter> = emptyList(),
        superTypes: List<LsiTypeRef> = emptyList(),
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = LsiSymbolId.type(qualifiedName),
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = kind,
            typeParameters = typeParameters,
            superTypes = superTypes,
            memberIds = memberIds,
            annotations = annotations,
            origin = origin,
        )
    }

    private fun property(
        ownerId: LsiSymbolId,
        name: String,
        type: LsiTypeRef,
        annotations: List<LsiAnnotation> = emptyList(),
        overrides: List<LsiOverride> = emptyList(),
        modality: LsiModality = LsiModality.ABSTRACT,
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiProperty {
        return LsiProperty(
            id = LsiSymbolId.property(ownerId, name),
            name = name,
            ownerId = ownerId,
            type = type,
            modality = modality,
            overrides = overrides,
            annotations = annotations,
            origin = origin,
        )
    }

    private fun listType(elementTypeId: LsiSymbolId): LsiDeclaredType {
        return LsiDeclaredType(
            declarationId = LsiSymbolId.type("java.util.List"),
            arguments = listOf(LsiTypeArgument.invariant(LsiDeclaredType(elementTypeId))),
        )
    }

    private fun default(
        value: String,
        language: LsiLanguage,
    ): LsiAnnotation {
        return annotation(
            type = DEFAULT,
            arguments = mapOf("value" to LsiAnnotationValue.StringValue(value)),
            useSiteTarget = if (language == LsiLanguage.JAVA) {
                LsiAnnotationUseSiteTarget.METHOD
            } else {
                LsiAnnotationUseSiteTarget.GETTER
            },
        )
    }

    private fun formula(
        sql: String = "",
        dependencies: List<String> = emptyList(),
    ): LsiAnnotation {
        return annotation(
            type = FORMULA,
            arguments = mapOf(
                "sql" to LsiAnnotationValue.StringValue(sql),
                "dependencies" to LsiAnnotationValue.ArrayValue(
                    dependencies.map(LsiAnnotationValue::StringValue)
                ),
            ),
        )
    }

    private fun annotation(
        type: LsiSymbolId,
        arguments: Map<String, LsiAnnotationValue> = emptyMap(),
        useSiteTarget: LsiAnnotationUseSiteTarget? = null,
    ): LsiAnnotation {
        return LsiAnnotation(
            type = type,
            arguments = arguments.mapValues { (_, value) ->
                LsiAnnotationArgument(value, LsiAnnotationArgumentOrigin.EXPLICIT)
            },
            useSiteTarget = useSiteTarget,
        )
    }

    private fun JimmerImmutableProp.annotationString(
        annotationType: LsiSymbolId,
        argumentName: String,
    ): String? {
        val annotation = annotations.firstOrNull { item -> item.type == annotationType } ?: return null
        return (annotation.arguments[argumentName]?.value as? LsiAnnotationValue.StringValue)?.value
    }

    private fun sourceOrigin(language: LsiLanguage): LsiOrigin {
        val sourceRoot = if (language == LsiLanguage.JAVA) "java" else "kotlin"
        val extension = if (language == LsiLanguage.JAVA) "java" else "kt"
        return LsiOrigin(
            kind = LsiOriginKind.SOURCE,
            source = LsiSource.of(
                "src/main/$sourceRoot/demo/OnlyOneSwitch.$extension",
                language,
            ),
        )
    }

    companion object {
        private val BASE_TYPE = LsiSymbolId.type("demo.BaseOnlyOneSwitch")
        private val ENTITY_TYPE = LsiSymbolId.type("demo.OnlyOneSwitch")
        private val STRING_TYPE = LsiSymbolId.type("java.lang.String")

        private val ENTITY = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
        private val IMMUTABLE = LsiSymbolId.type("org.babyfish.jimmer.Immutable")
        private val MAPPED_SUPERCLASS = LsiSymbolId.type("org.babyfish.jimmer.sql.MappedSuperclass")
        private val EMBEDDABLE = LsiSymbolId.type("org.babyfish.jimmer.sql.Embeddable")
        private val ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Id")
        private val VERSION = LsiSymbolId.type("org.babyfish.jimmer.sql.Version")
        private val LOGICAL_DELETED = LsiSymbolId.type("org.babyfish.jimmer.sql.LogicalDeleted")
        private val ONE_TO_ONE = LsiSymbolId.type("org.babyfish.jimmer.sql.OneToOne")
        private val MANY_TO_ONE = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToOne")
        private val FORMULA = LsiSymbolId.type("org.babyfish.jimmer.Formula")
        private val TRANSIENT = LsiSymbolId.type("org.babyfish.jimmer.sql.Transient")
        private val ID_VIEW = LsiSymbolId.type("org.babyfish.jimmer.sql.IdView")
        private val MANY_TO_MANY_VIEW = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToManyView")
        private val DEFAULT = LsiSymbolId.type("org.babyfish.jimmer.sql.Default")
        private val COLUMN = LsiSymbolId.type("org.babyfish.jimmer.sql.Column")
        private val JAVA_OVERRIDE = LsiSymbolId.type("java.lang.Override")

        private val VALID_CODE = LsiSymbolId.type("demo.ValidCode")
        private val CODE_FORMAT = LsiSymbolId.type("demo.CodeFormat")
        private val CODE_VALIDATOR = LsiSymbolId.type("demo.CodeValidator")
        private val CODE_CONVERTER = LsiSymbolId.type("demo.CodeConverter")
        private val JAKARTA_CONSTRAINT = LsiSymbolId.type("jakarta.validation.Constraint")
        private val JSON_CONVERTER = LsiSymbolId.type("org.babyfish.jimmer.jackson.JsonConverter")
        private val CONVERTER = LsiSymbolId.type("org.babyfish.jimmer.jackson.Converter")

        private val SYNTHETIC_ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
    }
}
