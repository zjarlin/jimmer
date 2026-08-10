package org.babyfish.jimmer.compiler.immutable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import site.addzero.lsi.jimmer.ApplicationDefaultStrategy
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.FormulaDependency
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.ImmutableDefault
import site.addzero.lsi.jimmer.ImmutablePrecompileException
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.InheritanceStrategy
import site.addzero.lsi.jimmer.JoinedTableDissociateAction
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.TransientResolver
import site.addzero.lsi.jimmer.fingerprint
import site.addzero.lsi.jimmer.jimmerTypeSignature
import site.addzero.lsi.jimmer.normalizedSnapshot
import site.addzero.lsi.jimmer.toImmutableSchema
import site.addzero.lsi.codegen.ArtifactAggregationMode
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
import site.addzero.lsi.type.LsiArrayType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.type.LsiNullability
import site.addzero.lsi.model.LsiOverride
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.type.LsiTypeArgument
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.copy
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.type.LsiTypeParameter
import site.addzero.lsi.type.LsiTypeParameterRef
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiWorkspace

class ImmutableWorkspaceExtensionsTest {

    @Test
    fun `allows concrete helper functions without jimmer annotations`() {
        val typeId = LsiSymbolId.type("demo.HelperBase")
        val functionId = LsiSymbolId.function(typeId, "normalize")
        val typedFunctionId = LsiSymbolId.function(typeId, "typed")
        val schema = LsiWorkspace(
            declarations = listOf(
                type("demo.HelperBase", MAPPED_SUPERCLASS, listOf(functionId, typedFunctionId)),
                LsiFunction(
                    id = functionId,
                    name = "normalize",
                    ownerId = typeId,
                    returnType = LsiPrimitiveType(LsiPrimitiveKind.INT),
                    modality = LsiModality.OPEN,
                    origin = SYNTHETIC_ORIGIN,
                ),
                LsiFunction(
                    id = typedFunctionId,
                    name = "typed",
                    ownerId = typeId,
                    returnType = LsiDeclaredType(STRING_TYPE),
                    modality = LsiModality.FINAL,
                    annotations = listOf(
                        annotation(T_NULLABLE).copy(
                            useSiteTarget = LsiAnnotationUseSiteTarget.RETURN_TYPE,
                        )
                    ),
                    origin = SYNTHETIC_ORIGIN,
                ),
            )
        ).toImmutableSchema()

        assertTrue(schema.types.single().props.isEmpty())
    }

    @Test
    fun `rejects abstract and annotated helper functions`() {
        fun failure(
            typeName: String,
            modality: LsiModality,
            annotations: List<LsiAnnotation> = emptyList(),
        ): ImmutablePrecompileException {
            val typeId = LsiSymbolId.type("demo.$typeName")
            val functionId = LsiSymbolId.function(typeId, "normalize")
            return assertFailsWith {
                LsiWorkspace(
                    declarations = listOf(
                        type("demo.$typeName", MAPPED_SUPERCLASS, listOf(functionId)),
                        LsiFunction(
                            id = functionId,
                            name = "normalize",
                            ownerId = typeId,
                            returnType = LsiPrimitiveType(LsiPrimitiveKind.INT),
                            modality = modality,
                            annotations = annotations,
                            origin = SYNTHETIC_ORIGIN,
                        ),
                    )
                ).toImmutableSchema()
            }
        }

        val abstractFailure = failure("AbstractHelperBase", LsiModality.ABSTRACT)
        assertTrue(abstractFailure.message.orEmpty().contains("cannot declare abstract function 'normalize'"))

        val annotatedFailure = failure(
            "AnnotatedHelperBase",
            LsiModality.OPEN,
            listOf(
                annotation(API_IGNORE).copy(
                    useSiteTarget = LsiAnnotationUseSiteTarget.METHOD,
                )
            ),
        )
        assertTrue(annotatedFailure.message.orEmpty().contains("Jimmer annotation @org.babyfish.jimmer.client.ApiIgnore"))
    }

    @Test
    fun `entity overrides mapped superclass default annotation after generic substitution`() {
        val schema = compileFixture(overrideWorkspace(LsiLanguage.KOTLIN))

        val entity = schema.types.single { type -> type.kind == ImmutableTypeKind.ENTITY }
        val status = entity.props.single { prop -> prop.name == "status" }
        val statusType = assertIs<LsiDeclaredType>(status.type)
        assertEquals(STRING_TYPE, statusType.declarationId)
        assertTrue(status.overridden)
        assertFalse(status.inherited)
        assertEquals(PrimaryMapping.SCALAR, status.primaryMapping)
        assertEquals(
            listOf(
                LsiSymbolId.property(ENTITY_TYPE, "status"),
                LsiSymbolId.property(BASE_TYPE, "status"),
            ),
            status.overrideChain,
        )
        assertEquals("1", status.annotationString(DEFAULT, "value"))
        assertEquals("STATUS", status.annotationString(COLUMN, "name"))
        assertEquals(
            ImmutableDefault.Application(
                annotationValue = "1",
                strategy = ApplicationDefaultStrategy.DECLARED_VALUE,
            ),
            status.defaultContract,
        )
        assertEquals("Base status documentation", status.documentation)
        assertNotEquals(
            schema.fingerprint(),
            schema.copy(
                types = schema.types.map { type ->
                    if (type.id != entity.id) {
                        type
                    } else {
                        type.copy(
                            props = type.props.map { prop ->
                                if (prop.id == status.id) prop.copy(documentation = "Changed") else prop
                            }
                        )
                    }
                }
            ).fingerprint(),
        )
    }

    @Test
    fun `precompiles application database and implicit version defaults`() {
        val ownerId = LsiSymbolId.type("demo.DefaultBase")
        val applicationProp = property(
            ownerId,
            "applicationValue",
            LsiDeclaredType(STRING_TYPE),
            listOf(
                annotation(
                    DEFAULT,
                    mapOf("value" to LsiAnnotationValue.StringValue("client-value")),
                )
            ),
        )
        val databaseProp = property(
            ownerId,
            "databaseValue",
            LsiDeclaredType(STRING_TYPE),
            listOf(
                annotation(
                    DATABASE_DEFAULT,
                    mapOf("value" to LsiAnnotationValue.StringValue("CURRENT_TIMESTAMP")),
                )
            ),
        )
        val emptyDatabaseProp = property(
            ownerId,
            "emptyDatabaseValue",
            LsiDeclaredType(STRING_TYPE),
            listOf(annotation(DATABASE_DEFAULT)),
        )
        val versionProp = property(
            ownerId,
            "version",
            LsiPrimitiveType(LsiPrimitiveKind.INT),
            listOf(annotation(VERSION)),
        )
        val keyProp = property(
            ownerId,
            "businessKey",
            LsiDeclaredType(STRING_TYPE),
            listOf(
                annotation(KEY),
                annotation(
                    DEFAULT,
                    mapOf("value" to LsiAnnotationValue.StringValue("key-value")),
                ),
            ),
        )
        val props = listOf(applicationProp, databaseProp, emptyDatabaseProp, versionProp, keyProp)
        val schema = LsiWorkspace(
            declarations = listOf(
                type("demo.DefaultBase", MAPPED_SUPERCLASS, props.map(LsiProperty::id)),
            ) + props,
        ).toImmutableSchema()

        val defaults = schema.types.single().props.associate { prop -> prop.name to prop.defaultContract }
        assertEquals(
            ImmutableDefault.Application(
                annotationValue = "client-value",
                strategy = ApplicationDefaultStrategy.DECLARED_VALUE,
            ),
            defaults.getValue("applicationValue"),
        )
        assertEquals(
            ImmutableDefault.Database("CURRENT_TIMESTAMP"),
            defaults.getValue("databaseValue"),
        )
        assertEquals(
            ImmutableDefault.Database(null),
            defaults.getValue("emptyDatabaseValue"),
        )
        assertEquals(
            ImmutableDefault.Application(
                annotationValue = null,
                strategy = ApplicationDefaultStrategy.VERSION_ZERO,
            ),
            defaults.getValue("version"),
        )
        assertEquals(
            ImmutableDefault.Application(
                annotationValue = "key-value",
                strategy = ApplicationDefaultStrategy.DECLARED_VALUE,
            ),
            defaults.getValue("businessKey"),
        )

        val explicitOwnerId = LsiSymbolId.type("demo.ExplicitVersionBase")
        val explicitVersionProp = property(
            explicitOwnerId,
            "version",
            LsiPrimitiveType(LsiPrimitiveKind.INT),
            listOf(
                annotation(VERSION),
                annotation(DEFAULT, mapOf("value" to LsiAnnotationValue.StringValue(""))),
            ),
        )
        val explicitVersion = LsiWorkspace(
            declarations = listOf(
                type(
                    "demo.ExplicitVersionBase",
                    MAPPED_SUPERCLASS,
                    listOf(explicitVersionProp.id),
                ),
                explicitVersionProp,
            )
        ).toImmutableSchema().types.single().props.single().defaultContract
        assertEquals(
            ImmutableDefault.Application(
                annotationValue = "",
                strategy = ApplicationDefaultStrategy.DECLARED_VALUE,
            ),
            explicitVersion,
        )

        val logicalOwnerId = LsiSymbolId.type("demo.ExplicitLogicalDefaultBase")
        val logicalProp = property(
            logicalOwnerId,
            "deleted",
            LsiPrimitiveType(LsiPrimitiveKind.INT),
            listOf(
                annotation(LOGICAL_DELETED),
                annotation(DEFAULT, mapOf("value" to LsiAnnotationValue.StringValue(""))),
            ),
        )
        val explicitLogicalDefault = LsiWorkspace(
            declarations = listOf(
                type(
                    "demo.ExplicitLogicalDefaultBase",
                    MAPPED_SUPERCLASS,
                    listOf(logicalProp.id),
                ),
                logicalProp,
            )
        ).toImmutableSchema().types.single().props.single().defaultContract
        assertEquals(
            ImmutableDefault.Application(
                annotationValue = "",
                strategy = ApplicationDefaultStrategy.LOGICAL_DELETED,
            ),
            explicitLogicalDefault,
        )
    }

    @Test
    fun `validates effective immutable default contracts`() {
        fun failure(
            typeName: String,
            marker: LsiSymbolId = MAPPED_SUPERCLASS,
            annotations: List<LsiAnnotation>,
            propType: LsiType = LsiDeclaredType(STRING_TYPE),
        ): ImmutablePrecompileException {
            val ownerId = LsiSymbolId.type("demo.$typeName")
            val prop = property(ownerId, "value", propType, annotations)
            return assertFailsWith {
                LsiWorkspace(
                    declarations = listOf(
                        type("demo.$typeName", marker, listOf(prop.id)),
                        prop,
                    )
                ).toImmutableSchema()
            }
        }

        val conflict = failure(
            "ConflictingDefaults",
            annotations = listOf(
                annotation(DEFAULT, mapOf("value" to LsiAnnotationValue.StringValue("1"))),
                annotation(DATABASE_DEFAULT),
            ),
        )
        assertTrue(conflict.message.orEmpty().contains("cannot be decorated by both"))

        val databaseKey = failure(
            "DatabaseKeyDefault",
            annotations = listOf(annotation(KEYS), annotation(DATABASE_DEFAULT)),
        )
        assertTrue(databaseKey.message.orEmpty().contains("cannot be id, key, version"))

        val malformedDatabase = failure(
            "MalformedDatabaseDefault",
            annotations = listOf(
                annotation(
                    DATABASE_DEFAULT,
                    mapOf("value" to LsiAnnotationValue.IntValue(1)),
                )
            ),
        )
        assertTrue(malformedDatabase.message.orEmpty().contains("must declare a typed string value"))

        val logicalBoolean = failure(
            "BooleanLogicalDefault",
            annotations = listOf(
                annotation(LOGICAL_DELETED),
                annotation(DEFAULT, mapOf("value" to LsiAnnotationValue.StringValue("false"))),
            ),
            propType = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
        )
        assertTrue(logicalBoolean.message.orEmpty().contains("unless its type is Int or enum"))

        val immutableDefault = failure(
            "PlainImmutableDefault",
            marker = IMMUTABLE,
            annotations = listOf(
                annotation(DEFAULT, mapOf("value" to LsiAnnotationValue.StringValue("value"))),
            ),
        )
        assertTrue(immutableDefault.message.orEmpty().contains("entity or mapped superclass"))
    }

    @Test
    fun `rejects default and database-default inherited across annotation override`() {
        val baseId = LsiSymbolId.type("demo.DatabaseDefaultBase")
        val entityId = LsiSymbolId.type("demo.ApplicationDefaultEntity")
        val baseProp = property(
            baseId,
            "status",
            LsiDeclaredType(STRING_TYPE),
            listOf(annotation(DATABASE_DEFAULT)),
        )
        val entityIdProp = property(
            entityId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val overridingProp = property(
            entityId,
            "status",
            LsiDeclaredType(STRING_TYPE),
            listOf(
                annotation(DEFAULT, mapOf("value" to LsiAnnotationValue.StringValue("1"))),
            ),
            overrides = listOf(LsiOverride(baseProp.id)),
        )
        val exception = assertFailsWith<ImmutablePrecompileException> {
            LsiWorkspace(
                declarations = listOf(
                    type("demo.DatabaseDefaultBase", MAPPED_SUPERCLASS, listOf(baseProp.id)),
                    baseProp,
                    type(
                        "demo.ApplicationDefaultEntity",
                        ENTITY,
                        listOf(entityIdProp.id, overridingProp.id),
                        superTypes = listOf(LsiDeclaredType(baseId)),
                    ),
                    entityIdProp,
                    overridingProp,
                )
            ).toImmutableSchema()
        }

        assertEquals(overridingProp.id, exception.declarationId)
        assertTrue(exception.message.orEmpty().contains("cannot be decorated by both"))
    }

    @Test
    fun `apt and ksp equivalent workspaces have identical immutable snapshots`() {
        val aptSchema = compileFixture(overrideWorkspace(LsiLanguage.JAVA))
        val kspSchema = compileFixture(overrideWorkspace(LsiLanguage.KOTLIN))

        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())
        assertEquals(64, aptSchema.fingerprint().length)
    }

    @Test
    fun `apt and ksp preserve microservice remote and generic recursive semantics`() {
        val aptSchema = compileFixture(microServiceWorkspace(LsiLanguage.JAVA))
        val kspSchema = compileFixture(microServiceWorkspace(LsiLanguage.KOTLIN))
        val base = aptSchema.types.single { type -> type.id == REMOTE_BASE_TYPE }
        val node = aptSchema.types.single { type -> type.id == REMOTE_NODE_TYPE }
        val product = aptSchema.types.single { type -> type.id == REMOTE_PRODUCT_TYPE }
        val baseParent = base.props.single { prop -> prop.name == "parent" }
        val props = node.props.associateBy(ImmutableProp::name)
        val parent = requireNotNull(props["parent"])
        val remoteProduct = requireNotNull(props["product"])

        assertTrue(base.acrossMicroServices)
        assertEquals("", base.microServiceName)
        assertTrue(baseParent.genericTarget)
        assertEquals(null, baseParent.targetTypeId)
        assertFalse(baseParent.recursive)
        assertFalse(node.acrossMicroServices)
        assertEquals("node-service", node.microServiceName)
        assertEquals("product-service", product.microServiceName)
        assertEquals(REMOTE_NODE_TYPE, parent.targetTypeId)
        assertFalse(parent.genericTarget)
        assertFalse(parent.remote)
        assertTrue(parent.recursive)
        assertEquals(REMOTE_PRODUCT_TYPE, remoteProduct.targetTypeId)
        assertTrue(remoteProduct.remote)
        assertFalse(remoteProduct.recursive)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())
        val nodeSnapshot = aptSchema.normalizedSnapshot().lineSequence().single { line ->
            line.startsWith("type|${REMOTE_NODE_TYPE.value}|")
        }
        assertTrue(nodeSnapshot.endsWith("|false|node-service"))

        val changedMicroService = aptSchema.copy(
            types = aptSchema.types.map { type ->
                if (type.id == REMOTE_NODE_TYPE) type.copy(microServiceName = "changed-service") else type
            }
        )
        val changedRemote = aptSchema.copy(
            types = aptSchema.types.map { type ->
                if (type.id != REMOTE_NODE_TYPE) {
                    type
                } else {
                    type.copy(
                        props = type.props.map { prop ->
                            if (prop.name == "product") prop.copy(remote = false) else prop
                        }
                    )
                }
            }
        )
        assertNotEquals(aptSchema.fingerprint(), changedMicroService.fingerprint())
        assertNotEquals(aptSchema.fingerprint(), changedRemote.fingerprint())
    }

    @Test
    fun `includes binary managed property targets in semantic schema`() {
        val ownerId = LsiSymbolId.type("demo.LocalBook")
        val targetId = LsiSymbolId.type("dependency.BinaryAuthor")
        val authorProp = property(
            ownerId = ownerId,
            name = "author",
            type = LsiDeclaredType(targetId),
            annotations = listOf(annotation(MANY_TO_ONE)),
            origin = sourceOrigin(LsiLanguage.KOTLIN),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = ownerId.requireTypeQualifiedName(),
                    marker = ENTITY,
                    memberIds = listOf(authorProp.id),
                    origin = sourceOrigin(LsiLanguage.KOTLIN),
                ),
                authorProp,
                type(
                    qualifiedName = targetId.requireTypeQualifiedName(),
                    marker = ENTITY,
                    memberIds = emptyList(),
                    origin = LsiOrigin(LsiOriginKind.BINARY),
                ),
            ),
        )

        val schema = compileFixture(workspace, setOf(ownerId))

        assertEquals(listOf(ownerId, targetId), schema.types.map(ImmutableType::id))
        assertEquals(
            targetId,
            schema.types.first().props.single { prop -> prop.name == "author" }.targetTypeId,
        )
    }

    @Test
    fun `generic mapped superclass association closes over binary target after substitution`() {
        val baseId = LsiSymbolId.type("demo.GenericAssociationBase")
        val childId = LsiSymbolId.type("demo.GenericAssociationChild")
        val targetId = LsiSymbolId.type("dependency.BinaryTarget")
        val parameterId = LsiSymbolId.typeParameter(baseId, "T")
        val targetProp = property(
            ownerId = baseId,
            name = "target",
            type = LsiTypeParameterRef(parameterId, LsiNullability.NON_NULL),
            annotations = listOf(annotation(MANY_TO_ONE)),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = baseId.requireTypeQualifiedName(),
                    marker = MAPPED_SUPERCLASS,
                    memberIds = listOf(targetProp.id),
                    typeParameters = listOf(LsiTypeParameter(parameterId, "T")),
                ),
                targetProp,
                type(
                    qualifiedName = childId.requireTypeQualifiedName(),
                    marker = ENTITY,
                    memberIds = emptyList(),
                    superTypes = listOf(
                        LsiDeclaredType(
                            declarationId = baseId,
                            arguments = listOf(
                                LsiTypeArgument.invariant(LsiDeclaredType(targetId))
                            ),
                        )
                    ),
                ),
                type(
                    qualifiedName = targetId.requireTypeQualifiedName(),
                    marker = ENTITY,
                    memberIds = emptyList(),
                    origin = LsiOrigin(LsiOriginKind.BINARY),
                ),
            ),
        )

        val schema = compileFixture(workspace, setOf(childId))
        val child = schema.types.single { type -> type.id == childId }
        val effectiveTarget = child.props.single { prop -> prop.name == "target" }

        assertEquals(
            setOf(baseId, childId, targetId),
            schema.types.mapTo(sortedSetOf(), ImmutableType::id),
        )
        assertEquals(targetId, effectiveTarget.targetTypeId)
        assertFalse(effectiveTarget.genericTarget)
    }

    @Test
    fun `resolves association ownership and storage indexes`() {
        val ownerId = LsiSymbolId.type("demo.StorageOwner")
        val targetId = LsiSymbolId.type("demo.StorageTarget")
        val leftId = LsiSymbolId.type("demo.StorageLeft")
        val rightId = LsiSymbolId.type("demo.StorageRight")
        val sqlLeftId = LsiSymbolId.type("demo.SqlLeft")
        val sqlRightId = LsiSymbolId.type("demo.SqlRight")

        fun id(ownerId: LsiSymbolId): LsiProperty {
            return property(ownerId, "id", LsiPrimitiveType(LsiPrimitiveKind.LONG), listOf(annotation(ID)))
        }

        val ownerTarget = property(
            ownerId,
            "target",
            LsiDeclaredType(targetId),
            listOf(annotation(MANY_TO_ONE)),
        )
        val ownerTargets = property(
            ownerId,
            "targets",
            listType(targetId),
            listOf(
                annotation(
                    ONE_TO_MANY,
                    mapOf("mappedBy" to LsiAnnotationValue.StringValue("owner")),
                )
            ),
        )
        val targetOwner = property(
            targetId,
            "owner",
            LsiDeclaredType(ownerId),
            listOf(annotation(MANY_TO_ONE)),
        )
        val leftRights = property(
            leftId,
            "rights",
            listType(rightId),
            listOf(annotation(MANY_TO_MANY)),
        )
        val rightLefts = property(
            rightId,
            "lefts",
            listType(leftId),
            listOf(
                annotation(
                    MANY_TO_MANY,
                    mapOf("mappedBy" to LsiAnnotationValue.StringValue("rights")),
                )
            ),
        )
        val sqlRights = property(
            sqlLeftId,
            "rights",
            listType(sqlRightId),
            listOf(
                annotation(MANY_TO_MANY),
                annotation(JOIN_SQL, mapOf("value" to LsiAnnotationValue.StringValue(
                    "%alias.ID = %target_alias.ID"
                ))),
            ),
        )
        val sqlLefts = property(
            sqlRightId,
            "lefts",
            listType(sqlLeftId),
            listOf(
                annotation(
                    MANY_TO_MANY,
                    mapOf("mappedBy" to LsiAnnotationValue.StringValue("rights")),
                )
            ),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(ownerId.requireTypeQualifiedName(), ENTITY, listOf(id(ownerId).id, ownerTarget.id, ownerTargets.id)),
                type(targetId.requireTypeQualifiedName(), ENTITY, listOf(id(targetId).id, targetOwner.id)),
                type(leftId.requireTypeQualifiedName(), ENTITY, listOf(id(leftId).id, leftRights.id)),
                type(rightId.requireTypeQualifiedName(), ENTITY, listOf(id(rightId).id, rightLefts.id)),
                type(sqlLeftId.requireTypeQualifiedName(), ENTITY, listOf(id(sqlLeftId).id, sqlRights.id)),
                type(sqlRightId.requireTypeQualifiedName(), ENTITY, listOf(id(sqlRightId).id, sqlLefts.id)),
                id(ownerId), ownerTarget, ownerTargets,
                id(targetId), targetOwner,
                id(leftId), leftRights,
                id(rightId), rightLefts,
                id(sqlLeftId), sqlRights,
                id(sqlRightId), sqlLefts,
            ),
        )

        val schema = compileFixture(workspace)
        val owner = schema.typesById.getValue(ownerId).props.associateBy(ImmutableProp::name)
        val target = schema.typesById.getValue(targetId).props.associateBy(ImmutableProp::name)
        val left = schema.typesById.getValue(leftId).props.associateBy(ImmutableProp::name)
        val right = schema.typesById.getValue(rightId).props.associateBy(ImmutableProp::name)
        val sqlLeft = schema.typesById.getValue(sqlLeftId).props.associateBy(ImmutableProp::name)
        val sqlRight = schema.typesById.getValue(sqlRightId).props.associateBy(ImmutableProp::name)

        assertEquals(AssociationStorageKind.COLUMN, owner.getValue("target").associationStorage)
        assertEquals(AssociationStorageKind.COLUMN, target.getValue("owner").associationStorage)
        assertEquals(AssociationStorageKind.NONE, owner.getValue("targets").associationStorage)
        assertEquals(target.getValue("owner").id, owner.getValue("targets").mappedBy?.ownerPropId)
        assertTrue(owner.getValue("targets").reverse)
        assertEquals(AssociationStorageKind.MIDDLE_TABLE, left.getValue("rights").associationStorage)
        assertEquals(AssociationStorageKind.NONE, right.getValue("lefts").associationStorage)
        assertEquals(left.getValue("rights").id, right.getValue("lefts").mappedBy?.ownerPropId)
        assertEquals(AssociationStorageKind.NONE, sqlLeft.getValue("rights").associationStorage)
        assertFalse(sqlLeft.getValue("rights").reverse)
        assertEquals(sqlLeft.getValue("rights").id, sqlRight.getValue("lefts").mappedBy?.ownerPropId)
        assertEquals(
            listOf(owner.getValue("targets").id),
            schema.inversePropIdsByOwnerPropId.getValue(target.getValue("owner").id),
        )
        assertEquals(
            listOf(right.getValue("lefts").id),
            schema.inversePropIdsByOwnerPropId.getValue(left.getValue("rights").id),
        )
    }

    @Test
    fun `rejects invalid association ownership and storage contracts`() {
        fun id(ownerId: LsiSymbolId): LsiProperty {
            return property(ownerId, "id", LsiPrimitiveType(LsiPrimitiveKind.LONG), listOf(annotation(ID)))
        }

        fun failure(
            ownerId: LsiSymbolId,
            targetId: LsiSymbolId,
            ownerProp: LsiProperty,
            targetProp: LsiProperty,
        ): String {
            val ownerIdProp = id(ownerId)
            val targetIdProp = id(targetId)
            val workspace = LsiWorkspace(
                declarations = listOf(
                    type(ownerId.requireTypeQualifiedName(), ENTITY, listOf(ownerIdProp.id, ownerProp.id)),
                    type(targetId.requireTypeQualifiedName(), ENTITY, listOf(targetIdProp.id, targetProp.id)),
                    ownerIdProp,
                    ownerProp,
                    targetIdProp,
                    targetProp,
                ),
            )
            return assertFailsWith<ImmutablePrecompileException> {
                compileFixture(workspace)
            }.message.orEmpty()
        }

        val ownerId = LsiSymbolId.type("demo.InvalidOwner")
        val targetId = LsiSymbolId.type("demo.InvalidTarget")
        assertTrue(
            "cannot find mappedBy property" in failure(
                ownerId,
                targetId,
                property(
                    ownerId,
                    "targets",
                    listType(targetId),
                    listOf(
                        annotation(
                            ONE_TO_MANY,
                            mapOf("mappedBy" to LsiAnnotationValue.StringValue("missing")),
                        )
                    ),
                ),
                property(
                    targetId,
                    "owner",
                    LsiDeclaredType(ownerId),
                    listOf(annotation(MANY_TO_ONE)),
                ),
            )
        )
        assertTrue(
            "must declare a non-empty mappedBy" in failure(
                ownerId,
                targetId,
                property(
                    ownerId,
                    "targets",
                    listType(targetId),
                    listOf(
                        annotation(
                            ONE_TO_MANY,
                            mapOf("mappedBy" to LsiAnnotationValue.StringValue("")),
                        )
                    ),
                ),
                property(
                    targetId,
                    "owner",
                    LsiDeclaredType(ownerId),
                    listOf(annotation(MANY_TO_ONE)),
                ),
            )
        )
        assertTrue(
            "does not match mappedBy owner kind" in failure(
                ownerId,
                targetId,
                property(
                    ownerId,
                    "target",
                    listType(targetId),
                    listOf(
                        annotation(
                            ONE_TO_MANY,
                            mapOf("mappedBy" to LsiAnnotationValue.StringValue("owner")),
                        )
                    ),
                ),
                property(
                    targetId,
                    "owner",
                    listType(ownerId),
                    listOf(annotation(MANY_TO_MANY)),
                ),
            )
        )
        assertTrue(
            "is itself an inverse association" in failure(
                ownerId,
                targetId,
                property(
                    ownerId,
                    "target",
                    listType(targetId),
                    listOf(
                        annotation(
                            MANY_TO_MANY,
                            mapOf("mappedBy" to LsiAnnotationValue.StringValue("owner")),
                        )
                    ),
                ),
                property(
                    targetId,
                    "owner",
                    listType(ownerId),
                    listOf(
                        annotation(
                            MANY_TO_MANY,
                            mapOf("mappedBy" to LsiAnnotationValue.StringValue("target")),
                        )
                    ),
                ),
            )
        )

        val conflictingOwner = property(
            ownerId,
            "target",
            LsiDeclaredType(targetId),
            listOf(annotation(MANY_TO_ONE), annotation(JOIN_COLUMN), annotation(JOIN_TABLE)),
        )
        val targetOwner = property(
            targetId,
            "owner",
            LsiDeclaredType(ownerId),
            listOf(annotation(ONE_TO_MANY, mapOf("mappedBy" to LsiAnnotationValue.StringValue("target")))),
        )
        assertTrue(
            "conflicting association storage annotations" in failure(
                ownerId,
                targetId,
                conflictingOwner,
                targetOwner,
            )
        )

        val nonNullableInverse = property(
            ownerId,
            "target",
            LsiDeclaredType(targetId),
            listOf(
                annotation(
                    ONE_TO_ONE,
                    mapOf("mappedBy" to LsiAnnotationValue.StringValue("owner")),
                )
            ),
        )
        assertTrue(
            "must be nullable" in failure(
                ownerId,
                targetId,
                nonNullableInverse,
                property(targetId, "owner", LsiDeclaredType(ownerId), listOf(annotation(ONE_TO_ONE))),
            )
        )
    }

    @Test
    fun `generic mapped superclass formula dependencies use owner specific inherited property ids`() {
        val baseId = LsiSymbolId.type("demo.GenericFormulaBase")
        val childId = LsiSymbolId.type("demo.GenericFormulaChild")
        val parameterId = LsiSymbolId.typeParameter(baseId, "T")
        val valueProp = property(
            ownerId = baseId,
            name = "value",
            type = LsiTypeParameterRef(parameterId, LsiNullability.NON_NULL),
        )
        val displayProp = property(
            ownerId = baseId,
            name = "display",
            type = LsiDeclaredType(STRING_TYPE),
            annotations = listOf(formula(dependencies = listOf("value"))),
            modality = LsiModality.FINAL,
        )
        val schema = compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    type(
                        qualifiedName = baseId.requireTypeQualifiedName(),
                        marker = MAPPED_SUPERCLASS,
                        memberIds = listOf(valueProp.id, displayProp.id),
                        typeParameters = listOf(LsiTypeParameter(parameterId, "T")),
                    ),
                    valueProp,
                    displayProp,
                    type(
                        qualifiedName = childId.requireTypeQualifiedName(),
                        marker = ENTITY,
                        memberIds = emptyList(),
                        superTypes = listOf(
                            LsiDeclaredType(
                                declarationId = baseId,
                                arguments = listOf(
                                    LsiTypeArgument.invariant(LsiDeclaredType(STRING_TYPE))
                                ),
                            )
                        ),
                    ),
                )
            ),
            setOf(childId),
        )

        val baseFormula = schema.typesById.getValue(baseId).props.single { prop -> prop.name == "display" }
        val childFormula = schema.typesById.getValue(childId).props.single { prop -> prop.name == "display" }
        assertEquals(
            listOf(FormulaDependency(listOf(valueProp.id))),
            baseFormula.formulaDependencies,
        )
        assertEquals(
            listOf(
                FormulaDependency(
                    listOf(LsiSymbolId.property(childId, "value"))
                )
            ),
            childFormula.formulaDependencies,
        )
    }

    @Test
    fun `rejects named across-microservice mapped superclass`() {
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.InvalidBase",
                    marker = MAPPED_SUPERCLASS,
                    memberIds = emptyList(),
                    markerArguments = mapOf(
                        "acrossMicroServices" to LsiAnnotationValue.BooleanValue(true),
                        "microServiceName" to LsiAnnotationValue.StringValue("base-service"),
                    ),
                )
            )
        )

        val exception = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(workspace)
        }

        assertTrue(exception.message.orEmpty().contains("cannot specify microServiceName"))
    }

    @Test
    fun `rejects incompatible immutable super type categories`() {
        val cases = listOf(
            Triple(MAPPED_SUPERCLASS, ENTITY, "only inherit a mapped superclass"),
            Triple(ENTITY, IMMUTABLE, "only inherit an entity or mapped superclass"),
            Triple(EMBEDDABLE, MAPPED_SUPERCLASS, "does not support inheritance"),
            Triple(IMMUTABLE, ENTITY, "only inherit a simple immutable type"),
        )

        cases.forEachIndexed { index, (childMarker, superMarker, expectedMessage) ->
            val superId = LsiSymbolId.type("demo.InvalidSuper$index")
            val childId = LsiSymbolId.type("demo.InvalidChild$index")
            val workspace = LsiWorkspace(
                declarations = listOf(
                    type(
                        qualifiedName = superId.requireTypeQualifiedName(),
                        marker = superMarker,
                        memberIds = emptyList(),
                    ),
                    type(
                        qualifiedName = childId.requireTypeQualifiedName(),
                        marker = childMarker,
                        memberIds = emptyList(),
                        superTypes = listOf(LsiDeclaredType(superId)),
                    ),
                ),
            )

            val exception = assertFailsWith<ImmutablePrecompileException> {
                compileFixture(workspace, setOf(childId))
            }

            assertEquals(childId, exception.declarationId)
            assertTrue(exception.message.orEmpty().contains(expectedMessage), exception.message)
        }
    }

    @Test
    fun `allows multiple mapped superclasses but rejects multiple simple immutable parents`() {
        val firstBaseId = LsiSymbolId.type("demo.FirstBase")
        val secondBaseId = LsiSymbolId.type("demo.SecondBase")
        val mappedId = LsiSymbolId.type("demo.CompositeBase")
        val entityId = LsiSymbolId.type("demo.CompositeEntity")
        val validWorkspace = LsiWorkspace(
            declarations = listOf(
                type(firstBaseId.requireTypeQualifiedName(), MAPPED_SUPERCLASS, emptyList()),
                type(secondBaseId.requireTypeQualifiedName(), MAPPED_SUPERCLASS, emptyList()),
                type(
                    qualifiedName = mappedId.requireTypeQualifiedName(),
                    marker = MAPPED_SUPERCLASS,
                    memberIds = emptyList(),
                    superTypes = listOf(LsiDeclaredType(firstBaseId), LsiDeclaredType(secondBaseId)),
                ),
                type(
                    qualifiedName = entityId.requireTypeQualifiedName(),
                    marker = ENTITY,
                    memberIds = emptyList(),
                    superTypes = listOf(LsiDeclaredType(mappedId)),
                ),
            ),
        )

        val entity = compileFixture(validWorkspace, setOf(entityId))
            .typesById
            .getValue(entityId)
        assertEquals(listOf(mappedId), entity.superTypeIds)

        val firstSimpleId = LsiSymbolId.type("demo.FirstSimple")
        val secondSimpleId = LsiSymbolId.type("demo.SecondSimple")
        val invalidSimpleId = LsiSymbolId.type("demo.InvalidSimple")
        val invalidWorkspace = LsiWorkspace(
            declarations = listOf(
                type(firstSimpleId.requireTypeQualifiedName(), IMMUTABLE, emptyList()),
                type(secondSimpleId.requireTypeQualifiedName(), IMMUTABLE, emptyList()),
                type(
                    qualifiedName = invalidSimpleId.requireTypeQualifiedName(),
                    marker = IMMUTABLE,
                    memberIds = emptyList(),
                    superTypes = listOf(
                        LsiDeclaredType(firstSimpleId),
                        LsiDeclaredType(secondSimpleId),
                    ),
                ),
            ),
        )

        val exception = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(invalidWorkspace, setOf(invalidSimpleId))
        }
        assertEquals(invalidSimpleId, exception.declarationId)
        assertTrue(exception.message.orEmpty().contains("does not support multiple inheritance"))
    }

    @Test
    fun `rejects microservice mismatch with non-across superclass`() {
        val baseId = LsiSymbolId.type("demo.ServiceBase")
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.ServiceBase",
                    marker = MAPPED_SUPERCLASS,
                    memberIds = emptyList(),
                    markerArguments = mapOf(
                        "microServiceName" to LsiAnnotationValue.StringValue("base-service")
                    ),
                ),
                type(
                    qualifiedName = "demo.ServiceEntity",
                    marker = ENTITY,
                    memberIds = emptyList(),
                    markerArguments = mapOf(
                        "microServiceName" to LsiAnnotationValue.StringValue("entity-service")
                    ),
                    superTypes = listOf(LsiDeclaredType(baseId)),
                ),
            )
        )

        val exception = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(workspace)
        }

        assertTrue(exception.message.orEmpty().contains("has micro service name 'entity-service'"))
        assertTrue(exception.message.orEmpty().contains("has micro service name 'base-service'"))
    }

    @Test
    fun `rejects concrete association declared by across-microservice mapped superclass`() {
        val baseId = LsiSymbolId.type("demo.CrossServiceBase")
        val targetId = LsiSymbolId.type("demo.CrossServiceTarget")
        val association = property(
            ownerId = baseId,
            name = "target",
            type = LsiDeclaredType(targetId),
            annotations = listOf(annotation(MANY_TO_ONE)),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.CrossServiceBase",
                    marker = MAPPED_SUPERCLASS,
                    memberIds = listOf(association.id),
                    markerArguments = mapOf(
                        "acrossMicroServices" to LsiAnnotationValue.BooleanValue(true)
                    ),
                ),
                association,
                type(
                    qualifiedName = "demo.CrossServiceTarget",
                    marker = ENTITY,
                    memberIds = emptyList(),
                    markerArguments = mapOf(
                        "microServiceName" to LsiAnnotationValue.StringValue("target-service")
                    ),
                ),
            )
        )

        val exception = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(workspace)
        }

        assertEquals(association.id, exception.declarationId)
        assertTrue(exception.message.orEmpty().contains("is across microservices"))
    }

    @Test
    fun `rejects remote association with empty service name or join sql`() {
        val ownerId = LsiSymbolId.type("demo.RemoteOwner")
        val targetId = LsiSymbolId.type("demo.RemoteTarget")
        val target = type(
            qualifiedName = "demo.RemoteTarget",
            marker = ENTITY,
            memberIds = emptyList(),
            markerArguments = mapOf(
                "microServiceName" to LsiAnnotationValue.StringValue("target-service")
            ),
        )
        val association = property(
            ownerId = ownerId,
            name = "target",
            type = LsiDeclaredType(targetId),
            annotations = listOf(annotation(MANY_TO_ONE)),
        )
        val unnamedWorkspace = LsiWorkspace(
            declarations = listOf(
                type("demo.RemoteOwner", ENTITY, listOf(association.id)),
                association,
                target,
            )
        )
        val unnamedException = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(unnamedWorkspace)
        }
        assertTrue(unnamedException.message.orEmpty().contains("requires non-empty micro service names"))

        val nonNullWorkspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.RemoteOwner",
                    marker = ENTITY,
                    memberIds = listOf(association.id),
                    markerArguments = mapOf(
                        "microServiceName" to LsiAnnotationValue.StringValue("owner-service")
                    ),
                ),
                association,
                target,
            )
        )
        val nonNullException = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(nonNullWorkspace)
        }
        assertTrue(nonNullException.message.orEmpty().contains("must be nullable"))

        val joinSqlAssociation = association.copy(
            annotations = association.annotations + annotation(
                JOIN_SQL,
                mapOf("value" to LsiAnnotationValue.StringValue("%alias.ID = %target_alias.ID")),
            )
        )
        val joinSqlWorkspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.RemoteOwner",
                    marker = ENTITY,
                    memberIds = listOf(joinSqlAssociation.id),
                    markerArguments = mapOf(
                        "microServiceName" to LsiAnnotationValue.StringValue("owner-service")
                    ),
                ),
                joinSqlAssociation,
                target,
            )
        )
        val joinSqlException = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(joinSqlWorkspace)
        }
        assertTrue(joinSqlException.message.orEmpty().contains("cannot be decorated by @${JOIN_SQL.value}"))
    }

    @Test
    fun `apt and ksp inheritance fixtures preserve equivalent type metadata`() {
        val aptSchema = compileFixture(inheritanceWorkspace(LsiLanguage.JAVA))
        val kspSchema = compileFixture(inheritanceWorkspace(LsiLanguage.KOTLIN))
        val rootId = LsiSymbolId.type("demo.Account")
        val childId = LsiSymbolId.type("demo.AdminAccount")
        val aptRoot = aptSchema.types.single { type -> type.id == rootId }
        val aptChild = aptSchema.types.single { type -> type.id == childId }

        assertEquals("账户继承根。\n用于多态 DTO。", aptRoot.documentation)
        assertEquals(
            listOf(ENTITY, INHERITANCE, DISCRIMINATOR_VALUE, API_MODEL),
            aptRoot.annotations.map(LsiAnnotation::type),
        )
        assertTrue(aptRoot.instantiable)
        assertEquals(rootId, aptRoot.inheritanceRootTypeId)
        assertEquals(InheritanceStrategy.JOINED, aptRoot.inheritanceStrategy)
        assertEquals(JoinedTableDissociateAction.DELETE, aptRoot.joinedTableDissociateAction)
        assertEquals("ACCOUNT", aptRoot.discriminatorValue)
        assertEquals(LsiSymbolId.property(rootId, "kind"), aptRoot.discriminatorPropId)
        assertEquals(
            PrimaryMapping.DISCRIMINATOR,
            aptRoot.props.single { prop -> prop.name == "kind" }.primaryMapping,
        )

        assertEquals(rootId, aptChild.primarySuperTypeId)
        assertEquals(rootId, aptChild.inheritanceRootTypeId)
        assertEquals(null, aptChild.inheritanceStrategy)
        assertEquals(null, aptChild.joinedTableDissociateAction)
        assertTrue(aptChild.instantiable)
        assertEquals("ADMIN", aptChild.discriminatorValue)
        assertEquals(LsiSymbolId.property(childId, "kind"), aptChild.discriminatorPropId)

        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())
        val rootSnapshot = aptSchema.normalizedSnapshot().lineSequence().single { line ->
            line.startsWith("type|${rootId.value}|")
        }
        assertTrue(rootSnapshot.contains("账户继承根。\\n用于多态 DTO。"))
        assertTrue(rootSnapshot.contains("${INHERITANCE.value}("))
        assertTrue(rootSnapshot.contains("|true|${rootId.value}|JOINED|DELETE|ACCOUNT|"))

        val changedDocumentation = aptSchema.copy(
            types = aptSchema.types.map { type ->
                if (type.id == rootId) type.copy(documentation = "已修改") else type
            },
        )
        assertNotEquals(aptSchema.fingerprint(), changedDocumentation.fingerprint())
    }

    @Test
    fun `rejects forType property when inheritance fetcher has strict branches`() {
        val workspace = inheritanceWorkspace(LsiLanguage.KOTLIN)
        val rootId = LsiSymbolId.type("demo.Account")
        val conflictProp = property(
            ownerId = rootId,
            name = "forType",
            type = LsiDeclaredType(STRING_TYPE),
            origin = (workspace[rootId] as LsiClass).origin,
        )
        val declarations = workspace.declarations.map { declaration ->
            val type = declaration as? LsiClass ?: return@map declaration
            if (type.id == rootId) {
                type.copy(memberIds = type.memberIds + conflictProp.id)
            } else {
                type
            }
        } + conflictProp
        val schema = compileFixture(
            LsiWorkspace(
                sources = workspace.sources,
                declarations = declarations,
                annotationScopes = workspace.annotationScopes,
            )
        )
        val exception = assertFailsWith<ImmutablePrecompileException> {
            schema.validateFetcherGenerationContracts(setOf(rootId))
        }

        assertEquals(conflictProp.id, exception.declarationId)
        assertTrue(exception.message.orEmpty().contains("Illegal property name 'forType'"))
    }

    @Test
    fun `inheritance fetcher aggregates strict branch origins`() {
        val schema = compileFixture(inheritanceWorkspace(LsiLanguage.KOTLIN))
        val rootId = LsiSymbolId.type("demo.Account")
        val childId = LsiSymbolId.type("demo.AdminAccount")
        val root = schema.typesById.getValue(rootId)
        val child = schema.typesById.getValue(childId)

        assertEquals(ArtifactAggregationMode.AGGREGATING, schema.inheritanceArtifactAggregationMode(root))
        assertEquals(setOf(rootId, childId), schema.inheritanceArtifactOriginatingSymbols(root))
        assertEquals(ArtifactAggregationMode.AGGREGATING, schema.inheritanceArtifactAggregationMode(child))
        assertEquals(setOf(childId), schema.inheritanceArtifactOriginatingSymbols(child))
    }

    @Test
    fun `inheritance defaults preserve legacy instantiability and discriminator values`() {
        val schema = compileFixture(defaultInheritanceWorkspace())
        val rootId = LsiSymbolId.type("demo.AbstractAccount")
        val childId = LsiSymbolId.type("demo.ConcreteAccount")
        val root = schema.types.single { type -> type.id == rootId }
        val child = schema.types.single { type -> type.id == childId }

        assertFalse(root.instantiable)
        assertEquals(rootId, root.inheritanceRootTypeId)
        assertEquals(InheritanceStrategy.SINGLE_TABLE, root.inheritanceStrategy)
        assertEquals(JoinedTableDissociateAction.DELETE, root.joinedTableDissociateAction)
        assertEquals(null, root.discriminatorValue)
        assertEquals(LsiSymbolId.property(rootId, "kind"), root.discriminatorPropId)

        assertTrue(child.instantiable)
        assertEquals(rootId, child.inheritanceRootTypeId)
        assertEquals(null, child.inheritanceStrategy)
        assertEquals(null, child.joinedTableDissociateAction)
        assertEquals("ConcreteAccount", child.discriminatorValue)
        assertEquals(LsiSymbolId.property(childId, "kind"), child.discriminatorPropId)
    }

    @Test
    fun `joined inheritance preserves lax dissociate action and single table rejects it`() {
        val joinedSchema = compileFixture(
            inheritanceActionWorkspace(
                strategy = "JOINED",
                joinedTableDissociateAction = "LAX",
            )
        )
        val root = joinedSchema.types.single()

        assertEquals(InheritanceStrategy.JOINED, root.inheritanceStrategy)
        assertEquals(JoinedTableDissociateAction.LAX, root.joinedTableDissociateAction)
        assertTrue(joinedSchema.normalizedSnapshot().contains("|JOINED|LAX|"))

        val exception = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(
                inheritanceActionWorkspace(
                    strategy = "SINGLE_TABLE",
                    joinedTableDissociateAction = "LAX",
                )
            )
        }
        assertTrue(exception.message.orEmpty().contains("only be LAX when the inheritance strategy is JOINED"))
    }

    @Test
    fun `derived entity only accepts discriminator inherited from inheritance root`() {
        val rootId = LsiSymbolId.type("demo.Account")
        val mappedId = LsiSymbolId.type("demo.AdminBase")
        val childId = LsiSymbolId.type("demo.AdminAccount")
        val rootDiscriminator = property(
            ownerId = rootId,
            name = "kind",
            type = LsiDeclaredType(STRING_TYPE),
            annotations = listOf(annotation(DISCRIMINATOR)),
        )
        val extraDiscriminator = property(
            ownerId = mappedId,
            name = "adminKind",
            type = LsiDeclaredType(STRING_TYPE),
            annotations = listOf(annotation(DISCRIMINATOR)),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.Account",
                    marker = ENTITY,
                    memberIds = listOf(rootDiscriminator.id),
                    typeAnnotations = listOf(annotation(INHERITANCE)),
                ),
                rootDiscriminator,
                type(
                    qualifiedName = "demo.AdminBase",
                    marker = MAPPED_SUPERCLASS,
                    memberIds = listOf(extraDiscriminator.id),
                ),
                extraDiscriminator,
                type(
                    qualifiedName = "demo.AdminAccount",
                    marker = ENTITY,
                    memberIds = emptyList(),
                    superTypes = listOf(
                        LsiDeclaredType(rootId),
                        LsiDeclaredType(mappedId),
                    ),
                ),
            ),
        )

        val exception = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(workspace)
        }

        assertEquals(extraDiscriminator.id, exception.declarationId)
        assertTrue(exception.message.orEmpty().contains("except from its inheritance root"))

        val declaredDiscriminator = property(
            ownerId = childId,
            name = "adminKind",
            type = LsiDeclaredType(STRING_TYPE),
            annotations = listOf(annotation(DISCRIMINATOR)),
        )
        val declaredWorkspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.Account",
                    marker = ENTITY,
                    memberIds = listOf(rootDiscriminator.id),
                    typeAnnotations = listOf(annotation(INHERITANCE)),
                ),
                rootDiscriminator,
                type(
                    qualifiedName = "demo.AdminAccount",
                    marker = ENTITY,
                    memberIds = listOf(declaredDiscriminator.id),
                    superTypes = listOf(LsiDeclaredType(rootId)),
                ),
                declaredDiscriminator,
            ),
        )
        val declaredException = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(declaredWorkspace)
        }
        assertEquals(declaredDiscriminator.id, declaredException.declarationId)
        assertTrue(declaredException.message.orEmpty().contains("cannot be declared by an inheritance derived type"))
    }

    @Test
    fun `inheritance root requires exactly one discriminator property`() {
        val missingWorkspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.MissingDiscriminatorRoot",
                    marker = ENTITY,
                    memberIds = emptyList(),
                    typeAnnotations = listOf(annotation(INHERITANCE)),
                )
            ),
        )
        val missingException = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(missingWorkspace)
        }
        assertTrue(missingException.message.orEmpty().contains("must declare or inherit one property"))

        val rootId = LsiSymbolId.type("demo.MultipleDiscriminatorRoot")
        val first = property(
            ownerId = rootId,
            name = "kind",
            type = LsiDeclaredType(STRING_TYPE),
            annotations = listOf(annotation(DISCRIMINATOR)),
        )
        val second = property(
            ownerId = rootId,
            name = "category",
            type = LsiDeclaredType(STRING_TYPE),
            annotations = listOf(annotation(DISCRIMINATOR)),
        )
        val multipleWorkspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.MultipleDiscriminatorRoot",
                    marker = ENTITY,
                    memberIds = listOf(first.id, second.id),
                    typeAnnotations = listOf(annotation(INHERITANCE)),
                ),
                first,
                second,
            ),
        )
        val multipleException = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(multipleWorkspace)
        }
        assertTrue(multipleException.message.orEmpty().contains("multiple discriminator properties"))
    }

    @Test
    fun `discriminator must be scalar string or enum property`() {
        val enumId = LsiSymbolId.type("demo.AccountKind")
        val enumSchema = compileFixture(
            discriminatorWorkspace(
                discriminatorType = LsiDeclaredType(enumId),
                extraDeclarations = listOf(declaration("demo.AccountKind", LsiTypeDeclarationKind.ENUM)),
            )
        )
        assertEquals(
            PrimaryMapping.DISCRIMINATOR,
            enumSchema.types.single().props.single { prop -> prop.name == "kind" }.primaryMapping,
        )

        val invalidTypes = listOf<LsiType>(
            LsiPrimitiveType(LsiPrimitiveKind.INT),
            listType(STRING_TYPE),
        )
        invalidTypes.forEach { invalidType ->
            val exception = assertFailsWith<ImmutablePrecompileException> {
                compileFixture(discriminatorWorkspace(invalidType))
            }
            assertTrue(exception.message.orEmpty().contains("must be a scalar string or enum property"))
        }

        val targetId = LsiSymbolId.type("demo.Target")
        val associationException = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(
                discriminatorWorkspace(
                    discriminatorType = LsiDeclaredType(targetId),
                    extraDeclarations = listOf(type("demo.Target", ENTITY, emptyList())),
                )
            )
        }
        assertTrue(associationException.message.orEmpty().contains("must be decorated by"))
        assertTrue(associationException.message.orEmpty().contains("ManyToOne"))

        val formulaException = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(
                discriminatorWorkspace(
                    discriminatorType = LsiDeclaredType(STRING_TYPE),
                    additionalAnnotations = listOf(formula(sql = "TYPE")),
                )
            )
        }
        assertTrue(formulaException.message.orEmpty().contains("must be a scalar string or enum property"))
    }

    @Test
    fun `preserves direct super and inherited declaration order across apt and ksp`() {
        val aptSchema = compileFixture(orderedHierarchyWorkspace(LsiLanguage.JAVA))
        val kspSchema = compileFixture(orderedHierarchyWorkspace(LsiLanguage.KOTLIN))
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
        assertEquals(
            expectedPropNames,
            aptEntity.props.map(ImmutableProp::name).filterNot { name -> name == "__fixtureId" },
        )
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
            .filterNot { name -> name == "__fixtureId" }
            .toList()
        assertEquals(expectedPropNames, entitySnapshotPropNames)
    }

    @Test
    fun `classifies immutable property mapping categories`() {
        val authorId = LsiSymbolId.type("demo.Author")
        val addressId = LsiSymbolId.type("demo.Address")
        val bookAuthorId = LsiSymbolId.type("demo.BookAuthor")
        val bookId = LsiSymbolId.type("demo.Book")
        val authorIdProp = property(
            authorId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val bookAuthorAuthorProp = property(
            bookAuthorId,
            "author",
            LsiDeclaredType(authorId),
            listOf(annotation(MANY_TO_ONE)),
        )
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
                "authorLinks",
                listType(bookAuthorId),
                listOf(annotation(ONE_TO_MANY)),
            ),
            property(
                bookId,
                "authorView",
                listType(authorId),
                listOf(
                    annotation(
                        MANY_TO_MANY_VIEW,
                        mapOf(
                            "prop" to LsiAnnotationValue.StringValue("authorLinks"),
                            "deeperProp" to LsiAnnotationValue.StringValue("author"),
                        ),
                    )
                ),
            ),
            property(
                bookId,
                "description",
                LsiDeclaredType(STRING_TYPE, nullability = LsiNullability.NULLABLE),
            ),
            property(bookId, "address", LsiDeclaredType(addressId)),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type("demo.Author", ENTITY, listOf(authorIdProp.id)),
                type("demo.BookAuthor", ENTITY, listOf(bookAuthorAuthorProp.id)),
                type("demo.Address", EMBEDDABLE, emptyList()),
                type("demo.Book", ENTITY, properties.map(LsiProperty::id)),
            ) + properties + authorIdProp + bookAuthorAuthorProp,
        )

        val book = compileFixture(workspace)
            .types.single { type -> type.id == bookId }
        val props = book.props.associateBy(ImmutableProp::name)

        assertEquals(PrimaryMapping.ID, props.getValue("id").primaryMapping)
        assertEquals(PrimaryMapping.VERSION, props.getValue("version").primaryMapping)
        assertEquals(
            PrimaryMapping.LOGICAL_DELETED,
            props.getValue("deleted").primaryMapping,
        )
        assertEquals(props.getValue("id").id, book.idPropId)
        assertEquals(props.getValue("version").id, book.versionPropId)
        assertEquals(props.getValue("deleted").id, book.logicalDeletedPropId)
        assertEquals(PrimaryMapping.ASSOCIATION, props.getValue("author").primaryMapping)
        assertEquals(AssociationKind.MANY_TO_ONE, props.getValue("author").associationKind)
        assertEquals(PrimaryMapping.FORMULA, props.getValue("displayName").primaryMapping)
        assertEquals(FormulaKind.SQL, props.getValue("displayName").formulaKind)
        assertEquals(PrimaryMapping.TRANSIENT, props.getValue("temporary").primaryMapping)
        assertEquals(PrimaryMapping.VIEW, props.getValue("authorId").primaryMapping)
        assertEquals(
            ImmutableView.Id(
                basePropId = LsiSymbolId.property(bookId, "author"),
                targetIdPropId = LsiSymbolId.property(authorId, "id"),
            ),
            props.getValue("authorId").view,
        )
        assertEquals(AssociationKind.ONE_TO_MANY, props.getValue("authorLinks").associationKind)
        assertTrue(props.getValue("authorLinks").list)
        assertTrue(props.getValue("authorLinks").association)
        assertEquals(PrimaryMapping.VIEW, props.getValue("authorView").primaryMapping)
        assertEquals(
            ImmutableView.ManyToMany(
                basePropId = LsiSymbolId.property(bookId, "authorLinks"),
                deeperPropId = LsiSymbolId.property(bookAuthorId, "author"),
            ),
            props.getValue("authorView").view,
        )
        assertTrue(props.getValue("description").nullable)
        assertEquals(PrimaryMapping.SCALAR, props.getValue("description").primaryMapping)
        assertFalse(props.getValue("address").association)
        assertTrue(props.getValue("address").embedded)
    }

    @Test
    fun `freezes inherited identity slots as effective owner properties`() {
        val baseId = LsiSymbolId.type("demo.IdentityBase")
        val entityId = LsiSymbolId.type("demo.IdentityEntity")
        val baseIdProp = property(
            baseId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val baseVersionProp = property(
            baseId,
            "version",
            LsiPrimitiveType(LsiPrimitiveKind.INT),
            listOf(annotation(VERSION)),
        )
        val baseDeletedProp = property(
            baseId,
            "deleted",
            LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
            listOf(annotation(LOGICAL_DELETED)),
        )
        val overriddenIdProp = property(
            entityId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            annotations = listOf(annotation(COLUMN)),
            overrides = listOf(LsiOverride(baseIdProp.id)),
        )
        val schema = compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    type(
                        "demo.IdentityBase",
                        MAPPED_SUPERCLASS,
                        listOf(baseIdProp.id, baseVersionProp.id, baseDeletedProp.id),
                    ),
                    baseIdProp,
                    baseVersionProp,
                    baseDeletedProp,
                    type(
                        "demo.IdentityEntity",
                        ENTITY,
                        listOf(overriddenIdProp.id),
                        superTypes = listOf(LsiDeclaredType(baseId)),
                    ),
                    overriddenIdProp,
                )
            )
        )

        val base = schema.typesById.getValue(baseId)
        val entity = schema.typesById.getValue(entityId)
        assertEquals(LsiSymbolId.property(baseId, "id"), base.idPropId)
        assertEquals(LsiSymbolId.property(baseId, "version"), base.versionPropId)
        assertEquals(LsiSymbolId.property(baseId, "deleted"), base.logicalDeletedPropId)
        assertEquals(LsiSymbolId.property(entityId, "id"), entity.idPropId)
        assertEquals(LsiSymbolId.property(entityId, "version"), entity.versionPropId)
        assertEquals(LsiSymbolId.property(entityId, "deleted"), entity.logicalDeletedPropId)
        assertTrue(entity.props.single { prop -> prop.name == "id" }.overridden)
    }

    @Test
    fun `requires entity id but allows mapped superclass without identity`() {
        val mappedType = type("demo.IdentityOptionalBase", MAPPED_SUPERCLASS, emptyList())
        val mappedSchema = LsiWorkspace(declarations = listOf(mappedType)).toImmutableSchema()
        assertNull(mappedSchema.types.single().idPropId)

        val entityType = type("demo.IdentityRequiredEntity", ENTITY, emptyList())
        val exception = assertFailsWith<ImmutablePrecompileException> {
            LsiWorkspace(declarations = listOf(entityType)).toImmutableSchema()
        }
        assertEquals(entityType.id, exception.declarationId)
        assertTrue(exception.message.orEmpty().contains("must have exactly one"))
    }

    @Test
    fun `inherits root version and logical-deleted slots into derived entity`() {
        val rootId = LsiSymbolId.type("demo.IdentityRoot")
        val childId = LsiSymbolId.type("demo.IdentityChild")
        val rootIdProp = property(
            rootId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val versionProp = property(
            rootId,
            "version",
            LsiPrimitiveType(LsiPrimitiveKind.INT),
            listOf(annotation(VERSION)),
        )
        val deletedProp = property(
            rootId,
            "deleted",
            LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
            listOf(annotation(LOGICAL_DELETED)),
        )
        val discriminatorProp = property(
            rootId,
            "kind",
            LsiDeclaredType(STRING_TYPE),
            listOf(annotation(DISCRIMINATOR)),
        )
        val schema = LsiWorkspace(
            declarations = listOf(
                type(
                    "demo.IdentityRoot",
                    ENTITY,
                    listOf(rootIdProp.id, versionProp.id, deletedProp.id, discriminatorProp.id),
                    typeAnnotations = listOf(annotation(INHERITANCE)),
                ),
                rootIdProp,
                versionProp,
                deletedProp,
                discriminatorProp,
                type(
                    "demo.IdentityChild",
                    ENTITY,
                    emptyList(),
                    superTypes = listOf(LsiDeclaredType(rootId)),
                ),
            )
        ).toImmutableSchema()

        val child = schema.typesById.getValue(childId)
        assertEquals(LsiSymbolId.property(childId, "id"), child.idPropId)
        assertEquals(LsiSymbolId.property(childId, "version"), child.versionPropId)
        assertEquals(LsiSymbolId.property(childId, "deleted"), child.logicalDeletedPropId)
    }

    @Test
    fun `rejects duplicate and non-persistent identity slots`() {
        val duplicateTypeId = LsiSymbolId.type("demo.DuplicateIdentity")
        val firstId = property(
            duplicateTypeId,
            "firstId",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val secondId = property(
            duplicateTypeId,
            "secondId",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val duplicateException = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(
                LsiWorkspace(
                    declarations = listOf(
                        type(
                            "demo.DuplicateIdentity",
                            ENTITY,
                            listOf(firstId.id, secondId.id),
                        ),
                        firstId,
                        secondId,
                    )
                )
            )
        }
        assertEquals(secondId.id, duplicateException.declarationId)
        assertTrue(duplicateException.message.orEmpty().contains("multiple properties"))

        val immutableTypeId = LsiSymbolId.type("demo.SimpleIdentity")
        val immutableId = property(
            immutableTypeId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val kindException = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(
                LsiWorkspace(
                    declarations = listOf(
                        type("demo.SimpleIdentity", IMMUTABLE, listOf(immutableId.id)),
                        immutableId,
                    )
                )
            )
        }
        assertEquals(immutableId.id, kindException.declarationId)
        assertTrue(kindException.message.orEmpty().contains("neither an entity nor a mapped superclass"))
    }

    @Test
    fun `resolves mapped-superclass identity diamonds and rejects unrelated slots`() {
        fun mappedType(
            typeId: LsiSymbolId,
            memberIds: List<LsiSymbolId>,
            superTypeIds: List<LsiSymbolId> = emptyList(),
        ): LsiClass {
            return type(
                qualifiedName = typeId.requireTypeQualifiedName(),
                marker = MAPPED_SUPERCLASS,
                memberIds = memberIds,
                superTypes = superTypeIds.map(::LsiDeclaredType),
            )
        }

        val firstId = LsiSymbolId.type("demo.FirstIdentityBase")
        val secondId = LsiSymbolId.type("demo.SecondIdentityBase")
        val conflictId = LsiSymbolId.type("demo.ConflictingIdentityEntity")
        val firstIdProp = property(
            firstId,
            "firstId",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val secondIdProp = property(
            secondId,
            "secondId",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val slotConflict = assertFailsWith<ImmutablePrecompileException> {
            LsiWorkspace(
                declarations = listOf(
                    mappedType(firstId, listOf(firstIdProp.id)),
                    firstIdProp,
                    mappedType(secondId, listOf(secondIdProp.id)),
                    secondIdProp,
                    type(
                        conflictId.requireTypeQualifiedName(),
                        ENTITY,
                        emptyList(),
                        superTypes = listOf(LsiDeclaredType(firstId), LsiDeclaredType(secondId)),
                    ),
                )
            ).toImmutableSchema()
        }
        assertTrue(slotConflict.message.orEmpty().contains("multiple properties"))

        val sameNameSecondProp = property(
            secondId,
            "firstId",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val nameConflict = assertFailsWith<ImmutablePrecompileException> {
            LsiWorkspace(
                declarations = listOf(
                    mappedType(firstId, listOf(firstIdProp.id)),
                    firstIdProp,
                    mappedType(secondId, listOf(sameNameSecondProp.id)),
                    sameNameSecondProp,
                    type(
                        conflictId.requireTypeQualifiedName(),
                        ENTITY,
                        emptyList(),
                        superTypes = listOf(LsiDeclaredType(firstId), LsiDeclaredType(secondId)),
                    ),
                )
            ).toImmutableSchema()
        }
        assertEquals(conflictId, nameConflict.declarationId)
        assertTrue(nameConflict.message.orEmpty().contains("inherits conflicting property"))

        val rootId = LsiSymbolId.type("demo.IdentityDiamondRoot")
        val leftId = LsiSymbolId.type("demo.IdentityDiamondLeft")
        val rightId = LsiSymbolId.type("demo.IdentityDiamondRight")
        val entityId = LsiSymbolId.type("demo.IdentityDiamondEntity")
        val rootIdProp = property(
            rootId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val diamond = LsiWorkspace(
            declarations = listOf(
                mappedType(rootId, listOf(rootIdProp.id)),
                rootIdProp,
                mappedType(leftId, emptyList(), listOf(rootId)),
                mappedType(rightId, emptyList(), listOf(rootId)),
                type(
                    entityId.requireTypeQualifiedName(),
                    ENTITY,
                    emptyList(),
                    superTypes = listOf(LsiDeclaredType(leftId), LsiDeclaredType(rightId)),
                ),
            )
        ).toImmutableSchema().typesById.getValue(entityId)
        assertEquals(LsiSymbolId.property(entityId, "id"), diamond.idPropId)
        assertEquals(1, diamond.props.count { prop -> prop.primaryMapping == PrimaryMapping.ID })
    }

    @Test
    fun `validates version and logical-deleted identity types`() {
        fun failure(
            name: String,
            type: LsiType,
            annotationType: LsiSymbolId,
            extraDeclarations: List<LsiClass> = emptyList(),
        ): ImmutablePrecompileException {
            val ownerId = LsiSymbolId.type("demo.$name")
            val prop = property(ownerId, "value", type, listOf(annotation(annotationType)))
            return assertFailsWith {
                compileFixture(
                    LsiWorkspace(
                        declarations = listOf(
                            type("demo.$name", MAPPED_SUPERCLASS, listOf(prop.id)),
                            prop,
                        ) + extraDeclarations,
                    )
                )
            }
        }

        assertTrue(
            failure("StringVersion", LsiDeclaredType(STRING_TYPE), VERSION)
                .message.orEmpty().contains("non-null Int")
        )
        assertTrue(
            failure("StringDeleted", LsiDeclaredType(STRING_TYPE), LOGICAL_DELETED)
                .message.orEmpty().contains("Boolean, Int, enum, Long, UUID")
        )
        assertTrue(
            failure(
                "BoxedBooleanDeleted",
                LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN, boxed = true),
                LOGICAL_DELETED,
            ).message.orEmpty().contains("primitive Boolean or Int")
        )
        assertTrue(
            failure(
                "NonNullTimeDeleted",
                LsiDeclaredType(LsiSymbolId.type("java.time.Instant")),
                LOGICAL_DELETED,
            ).message.orEmpty().contains("must be nullable for a time type")
        )

        val nullableTimeOwnerId = LsiSymbolId.type("demo.NullableTimeDeleted")
        val nullableTimeProp = property(
            nullableTimeOwnerId,
            "deletedAt",
            LsiDeclaredType(
                LsiSymbolId.type("java.time.Instant"),
                nullability = LsiNullability.NULLABLE,
            ),
            listOf(annotation(LOGICAL_DELETED)),
        )
        val nullableTime = compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.NullableTimeDeleted", MAPPED_SUPERCLASS, listOf(nullableTimeProp.id)),
                    nullableTimeProp,
                )
            )
        ).types.single()
        assertEquals(nullableTimeProp.id, nullableTime.logicalDeletedPropId)

        val enumTypeId = LsiSymbolId.type("demo.NullableDeleteState")
        val nullableEnumOwnerId = LsiSymbolId.type("demo.NullableEnumDeleted")
        val nullableEnumProp = property(
            nullableEnumOwnerId,
            "state",
            LsiDeclaredType(enumTypeId, nullability = LsiNullability.NULLABLE),
            listOf(
                annotation(
                    LOGICAL_DELETED,
                    mapOf("value" to LsiAnnotationValue.StringValue("DELETED")),
                )
            ),
        )
        val nullableEnum = compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    declaration(
                        enumTypeId.requireTypeQualifiedName(),
                        kind = LsiTypeDeclarationKind.ENUM,
                    ),
                    type(
                        "demo.NullableEnumDeleted",
                        MAPPED_SUPERCLASS,
                        listOf(nullableEnumProp.id),
                    ),
                    nullableEnumProp,
                )
            )
        ).types.single()
        assertEquals(nullableEnumProp.id, nullableEnum.logicalDeletedPropId)
    }

    @Test
    fun `rejects version declared by inheritance derived entity`() {
        val rootId = LsiSymbolId.type("demo.VersionRoot")
        val childId = LsiSymbolId.type("demo.VersionChild")
        val rootIdProp = property(
            rootId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val discriminatorProp = property(
            rootId,
            "kind",
            LsiDeclaredType(STRING_TYPE),
            listOf(annotation(DISCRIMINATOR)),
        )
        val childVersionProp = property(
            childId,
            "version",
            LsiPrimitiveType(LsiPrimitiveKind.INT),
            listOf(annotation(VERSION)),
        )
        val exception = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(
                LsiWorkspace(
                    declarations = listOf(
                        type(
                            "demo.VersionRoot",
                            ENTITY,
                            listOf(rootIdProp.id, discriminatorProp.id),
                            typeAnnotations = listOf(annotation(INHERITANCE)),
                        ),
                        rootIdProp,
                        discriminatorProp,
                        type(
                            "demo.VersionChild",
                            ENTITY,
                            listOf(childVersionProp.id),
                            superTypes = listOf(LsiDeclaredType(rootId)),
                        ),
                        childVersionProp,
                    )
                )
            )
        }
        assertEquals(childVersionProp.id, exception.declarationId)
        assertTrue(exception.message.orEmpty().contains("inheritance derived type"))
    }

    @Test
    fun `rejects invalid association cardinality and target kinds`() {
        val ownerId = LsiSymbolId.type("demo.CategoryOwner")
        val targetId = LsiSymbolId.type("demo.CategoryTarget")

        fun failure(
            propertyType: LsiType,
            propertyAnnotations: List<LsiAnnotation>,
            targetMarker: LsiSymbolId? = null,
            ownerMarker: LsiSymbolId = ENTITY,
        ): String {
            val valueProp = property(ownerId, "value", propertyType, propertyAnnotations)
            val ownerProps = mutableListOf(valueProp)
            if (ownerMarker in setOf(ENTITY, MAPPED_SUPERCLASS)) {
                ownerProps += property(
                    ownerId,
                    "id",
                    LsiPrimitiveType(LsiPrimitiveKind.LONG),
                    listOf(annotation(ID)),
                )
            }
            val declarations = mutableListOf<site.addzero.lsi.model.LsiDeclaration>()
            declarations += type(
                "demo.CategoryOwner",
                ownerMarker,
                ownerProps.map(LsiProperty::id),
            )
            declarations += ownerProps
            if (targetMarker != null) {
                val targetIdProp = property(
                    targetId,
                    "id",
                    LsiPrimitiveType(LsiPrimitiveKind.LONG),
                    listOf(annotation(ID)),
                )
                val targetMemberIds = if (targetMarker == ENTITY) listOf(targetIdProp.id) else emptyList()
                declarations += type(
                    "demo.CategoryTarget",
                    targetMarker,
                    targetMemberIds,
                )
                if (targetMarker == ENTITY) {
                    declarations += targetIdProp
                }
            }
            return assertFailsWith<ImmutablePrecompileException> {
                compileFixture(LsiWorkspace(declarations = declarations))
            }.message.orEmpty()
        }

        assertTrue(
            "list association" in failure(
                listType(targetId),
                listOf(annotation(MANY_TO_ONE)),
                ENTITY,
            )
        )
        assertTrue(
            "is not a list" in failure(
                LsiDeclaredType(targetId),
                listOf(annotation(ONE_TO_MANY)),
                ENTITY,
            )
        )
        assertTrue(
            "must be an entity" in failure(
                LsiDeclaredType(STRING_TYPE),
                listOf(annotation(MANY_TO_ONE)),
            )
        )
        val implicitAssociationFailure = failure(
            LsiDeclaredType(targetId),
            emptyList(),
            ENTITY,
        )
        assertTrue("ManyToOne" in implicitAssociationFailure)
        assertTrue("OneToOne" in implicitAssociationFailure)
        assertTrue(
            "cannot target mapped superclass" in failure(
                LsiDeclaredType(targetId),
                emptyList(),
                MAPPED_SUPERCLASS,
            )
        )
        val embeddableListFailure = failure(
            listType(targetId),
            emptyList(),
            EMBEDDABLE,
        )
        assertTrue("embeddable" in embeddableListFailure)
        assertTrue("cannot be a list" in embeddableListFailure)
        assertTrue(
            "immutable but not embeddable" in failure(
                LsiDeclaredType(targetId),
                emptyList(),
                IMMUTABLE,
            )
        )
        assertTrue(
            "not an entity or mapped superclass" in failure(
                LsiDeclaredType(targetId),
                listOf(annotation(MANY_TO_ONE)),
                ENTITY,
                ownerMarker = EMBEDDABLE,
            )
        )

        val immutableOwnerId = LsiSymbolId.type("demo.ImmutableOwner")
        val immutableTargetId = LsiSymbolId.type("demo.ImmutableTarget")
        val immutableValueProp = property(
            immutableOwnerId,
            "value",
            LsiDeclaredType(immutableTargetId),
        )
        val immutableValue = compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.ImmutableOwner", IMMUTABLE, listOf(immutableValueProp.id)),
                    type("demo.ImmutableTarget", IMMUTABLE, emptyList()),
                    immutableValueProp,
                )
            )
        ).types.single { type -> type.id == immutableOwnerId }.props.single()
        assertEquals(PrimaryMapping.SCALAR, immutableValue.primaryMapping)
        assertFalse(immutableValue.association)
        assertFalse(immutableValue.embedded)
    }

    @Test
    fun `resolves scalar and list id views with stable dependency indexes`() {
        val storeId = LsiSymbolId.type("demo.Store")
        val authorId = LsiSymbolId.type("demo.Author")
        val bookId = LsiSymbolId.type("demo.Book")
        val storeIdProp = property(
            storeId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val authorIdProp = property(
            authorId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val storeProp = property(
            bookId,
            "store",
            LsiDeclaredType(storeId, nullability = LsiNullability.NULLABLE),
            listOf(annotation(MANY_TO_ONE)),
        )
        val storeIdViewProp = property(
            bookId,
            "storeId",
            LsiDeclaredType(
                LsiSymbolId.type("java.lang.Long"),
                nullability = LsiNullability.PLATFORM,
            ),
            listOf(annotation(ID_VIEW)),
        )
        val authorsProp = property(
            bookId,
            "authors",
            listType(authorId),
            listOf(annotation(MANY_TO_MANY)),
        )
        val authorIdsProp = property(
            bookId,
            "authorIds",
            LsiDeclaredType(
                declarationId = LsiSymbolId.type("java.util.List"),
                arguments = listOf(
                    LsiTypeArgument.invariant(LsiDeclaredType(LsiSymbolId.type("java.lang.Long")))
                ),
            ),
            listOf(
                annotation(
                    ID_VIEW,
                    mapOf("value" to LsiAnnotationValue.StringValue("authors")),
                )
            ),
        )
        val schema = compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.Store", ENTITY, listOf(storeIdProp.id)),
                    type("demo.Author", ENTITY, listOf(authorIdProp.id)),
                    type(
                        "demo.Book",
                        ENTITY,
                        listOf(storeProp.id, storeIdViewProp.id, authorsProp.id, authorIdsProp.id),
                    ),
                    storeIdProp,
                    authorIdProp,
                    storeProp,
                    storeIdViewProp,
                    authorsProp,
                    authorIdsProp,
                ),
            )
        )
        val props = schema.types.single { type -> type.id == bookId }
            .props
            .associateBy(ImmutableProp::name)

        assertTrue(props.getValue("storeId").nullable)
        assertEquals(
            ImmutableView.Id(storeProp.id, storeIdProp.id),
            props.getValue("storeId").view,
        )
        assertEquals(
            ImmutableView.Id(authorsProp.id, authorIdProp.id),
            props.getValue("authorIds").view,
        )
        assertEquals(listOf(storeIdViewProp.id), schema.idViewPropIdsByBasePropId[storeProp.id])
        assertEquals(listOf(authorIdsProp.id), schema.idViewPropIdsByBasePropId[authorsProp.id])
        assertEquals(
            listOf(storeProp.id, storeIdProp.id),
            schema.viewDependencyPathByPropId[storeIdViewProp.id],
        )
        assertEquals(
            listOf(authorsProp.id, authorIdProp.id),
            schema.viewDependencyPathByPropId[authorIdsProp.id],
        )
    }

    @Test
    fun `resolves generic mapped superclass id view and relinks overridden annotation`() {
        val authorId = LsiSymbolId.type("demo.Author")
        val baseId = LsiSymbolId.type("demo.BaseBook")
        val bookId = LsiSymbolId.type("demo.Book")
        val targetParameterId = LsiSymbolId.typeParameter(baseId, "T")
        val authorIdProp = property(
            authorId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val targetProp = property(
            baseId,
            "target",
            LsiTypeParameterRef(targetParameterId),
            listOf(annotation(MANY_TO_ONE)),
        )
        val alternateProp = property(
            baseId,
            "alternate",
            LsiTypeParameterRef(targetParameterId),
            listOf(annotation(MANY_TO_ONE)),
        )
        val selectedIdProp = property(
            baseId,
            "selectedId",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(
                annotation(
                    ID_VIEW,
                    mapOf("value" to LsiAnnotationValue.StringValue("target")),
                )
            ),
        )
        val overriddenSelectedIdProp = property(
            bookId,
            "selectedId",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(
                annotation(
                    ID_VIEW,
                    mapOf("value" to LsiAnnotationValue.StringValue("alternate")),
                )
            ),
            overrides = listOf(LsiOverride(selectedIdProp.id)),
        )
        val schema = compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.Author", ENTITY, listOf(authorIdProp.id)),
                    type(
                        qualifiedName = "demo.BaseBook",
                        marker = MAPPED_SUPERCLASS,
                        memberIds = listOf(targetProp.id, alternateProp.id, selectedIdProp.id),
                        typeParameters = listOf(
                            LsiTypeParameter(targetParameterId, "T")
                        ),
                    ),
                    type(
                        qualifiedName = "demo.Book",
                        marker = ENTITY,
                        memberIds = listOf(overriddenSelectedIdProp.id),
                        superTypes = listOf(
                            LsiDeclaredType(
                                declarationId = baseId,
                                arguments = listOf(LsiTypeArgument.invariant(LsiDeclaredType(authorId))),
                            )
                        ),
                    ),
                    authorIdProp,
                    targetProp,
                    alternateProp,
                    selectedIdProp,
                    overriddenSelectedIdProp,
                ),
            )
        )
        val baseView = schema.types.single { type -> type.id == baseId }
            .props.single { prop -> prop.name == "selectedId" }
        val bookView = schema.types.single { type -> type.id == bookId }
            .props.single { prop -> prop.name == "selectedId" }

        assertEquals(
            ImmutableView.Id(targetProp.id, null),
            baseView.view,
        )
        assertEquals(
            ImmutableView.Id(
                LsiSymbolId.property(bookId, "alternate"),
                authorIdProp.id,
            ),
            bookView.view,
        )
        assertTrue(bookView.overridden)
    }

    @Test
    fun `resolves many to many view with automatic deeper property`() {
        val bookId = LsiSymbolId.type("demo.Book")
        val linkId = LsiSymbolId.type("demo.BookAuthor")
        val authorId = LsiSymbolId.type("demo.Author")
        val authorIdProp = property(
            authorId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val linksProp = property(
            bookId,
            "links",
            listType(linkId),
            listOf(annotation(ONE_TO_MANY)),
        )
        val authorViewProp = property(
            bookId,
            "authors",
            listType(authorId),
            listOf(
                annotation(
                    MANY_TO_MANY_VIEW,
                    mapOf("prop" to LsiAnnotationValue.StringValue("links")),
                )
            ),
        )
        val deeperProp = property(
            linkId,
            "author",
            LsiDeclaredType(authorId),
            listOf(annotation(MANY_TO_ONE)),
        )
        val authorIdsProp = property(
            bookId,
            "authorIds",
            LsiDeclaredType(
                declarationId = LsiSymbolId.type("java.util.List"),
                arguments = listOf(
                    LsiTypeArgument.invariant(LsiPrimitiveType(LsiPrimitiveKind.LONG))
                ),
            ),
            listOf(
                annotation(
                    ID_VIEW,
                    mapOf("value" to LsiAnnotationValue.StringValue("authors")),
                )
            ),
        )
        val schema = compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.Book", ENTITY, listOf(linksProp.id, authorViewProp.id, authorIdsProp.id)),
                    type("demo.BookAuthor", ENTITY, listOf(deeperProp.id)),
                    type("demo.Author", ENTITY, listOf(authorIdProp.id)),
                    linksProp,
                    authorViewProp,
                    deeperProp,
                    authorIdProp,
                    authorIdsProp,
                ),
            )
        )
        val props = schema.types.single { type -> type.id == bookId }
            .props.associateBy(ImmutableProp::name)
        val view = props.getValue("authors")

        assertEquals(
            ImmutableView.ManyToMany(linksProp.id, deeperProp.id),
            view.view,
        )
        assertEquals(
            listOf(linksProp.id, deeperProp.id),
            schema.viewDependencyPathByPropId[view.id],
        )
        assertEquals(
            ImmutableView.Id(view.id, authorIdProp.id),
            props.getValue("authorIds").view,
        )
    }

    @Test
    fun `rejects invalid id view links`() {
        val storeId = LsiSymbolId.type("demo.Store")
        val bookId = LsiSymbolId.type("demo.Book")

        fun failure(
            viewName: String = "storeId",
            annotationValue: String? = "store",
            includeBase: Boolean = true,
            baseType: LsiType = LsiDeclaredType(storeId),
            baseAnnotations: List<LsiAnnotation> = listOf(annotation(MANY_TO_ONE)),
            viewType: LsiType = LsiPrimitiveType(LsiPrimitiveKind.LONG),
        ): String {
            val storeIdProp = property(
                storeId,
                "id",
                LsiPrimitiveType(LsiPrimitiveKind.LONG),
                listOf(annotation(ID)),
            )
            val baseProp = property(bookId, "store", baseType, baseAnnotations)
            val viewProp = property(
                bookId,
                viewName,
                viewType,
                listOf(
                    annotation(
                        ID_VIEW,
                        annotationValue
                            ?.let { value ->
                                mapOf("value" to LsiAnnotationValue.StringValue(value))
                            }
                            .orEmpty(),
                    )
                ),
            )
            val bookMemberIds = buildList {
                if (includeBase) {
                    add(baseProp.id)
                }
                add(viewProp.id)
            }
            val declarations = buildList {
                add(type("demo.Store", ENTITY, listOf(storeIdProp.id)))
                add(type("demo.Book", ENTITY, bookMemberIds))
                add(storeIdProp)
                if (includeBase) {
                    add(baseProp)
                }
                add(viewProp)
            }
            return assertFailsWith<ImmutablePrecompileException> {
                compileFixture(LsiWorkspace(declarations = declarations))
            }.message.orEmpty()
        }

        assertTrue("determine" in failure(viewName = "URLId", annotationValue = null))
        assertTrue(
            "itself" in failure(
                viewName = "store",
                annotationValue = "store",
                includeBase = false,
            )
        )
        assertTrue("cannot find" in failure(annotationValue = "missing"))
        assertTrue(
            "not a persistent entity association" in failure(
                baseType = LsiPrimitiveType(LsiPrimitiveKind.LONG),
                baseAnnotations = emptyList(),
            )
        )
        assertTrue(
            "not a persistent entity association" in failure(
                baseAnnotations = listOf(annotation(TRANSIENT)),
            )
        )
        assertTrue(
            "list category" in failure(
                viewType = LsiDeclaredType(
                    declarationId = LsiSymbolId.type("java.util.List"),
                    arguments = listOf(
                        LsiTypeArgument.invariant(LsiPrimitiveType(LsiPrimitiveKind.LONG))
                    ),
                ),
            )
        )
        assertTrue(
            "nullability" in failure(
                baseType = LsiDeclaredType(storeId, nullability = LsiNullability.NULLABLE),
            )
        )
        assertTrue(
            "type does not match" in failure(
                viewType = LsiDeclaredType(STRING_TYPE),
            )
        )
        assertTrue(
            "type does not match" in failure(
                baseType = listType(storeId),
                baseAnnotations = listOf(annotation(MANY_TO_MANY)),
                viewType = LsiDeclaredType(
                    declarationId = LsiSymbolId.type("java.util.List"),
                    arguments = listOf(
                        LsiTypeArgument.invariant(
                            LsiPrimitiveType(
                                LsiPrimitiveKind.LONG,
                                nullability = LsiNullability.NULLABLE,
                            )
                        )
                    ),
                ),
            )
        )
    }

    @Test
    fun `rejects conflicting primary mapping annotations independent of order`() {
        val storeId = LsiSymbolId.type("demo.Store")
        val bookId = LsiSymbolId.type("demo.Book")
        val storeIdProp = property(
            storeId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val storeProp = property(
            bookId,
            "store",
            LsiDeclaredType(storeId),
            listOf(annotation(MANY_TO_ONE)),
        )

        listOf(
            listOf(annotation(ID_VIEW), annotation(TRANSIENT)),
            listOf(annotation(TRANSIENT), annotation(ID_VIEW)),
            listOf(annotation(FORMULA), annotation(ID_VIEW)),
        ).forEach { annotations ->
            val storeIdViewProp = property(
                bookId,
                "storeId",
                LsiPrimitiveType(LsiPrimitiveKind.LONG),
                annotations,
            )
            val exception = assertFailsWith<ImmutablePrecompileException> {
                compileFixture(
                    LsiWorkspace(
                        declarations = listOf(
                            type("demo.Store", ENTITY, listOf(storeIdProp.id)),
                            type("demo.Book", ENTITY, listOf(storeProp.id, storeIdViewProp.id)),
                            storeIdProp,
                            storeProp,
                            storeIdViewProp,
                        )
                    )
                )
            }
            assertTrue("multiple primary mapping annotations" in exception.message.orEmpty())
        }
    }

    @Test
    fun `rejects implicit id view name conflict except maps id`() {
        val storeId = LsiSymbolId.type("demo.Store")
        val bookId = LsiSymbolId.type("demo.Book")
        val storeIdProp = property(
            storeId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val bookIdProp = property(
            bookId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val storeProp = property(
            bookId,
            "store",
            LsiDeclaredType(storeId),
            listOf(annotation(MANY_TO_ONE)),
        )
        val conflictingStoreIdProp = property(
            bookId,
            "storeId",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
        )
        val conflict = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(
                LsiWorkspace(
                    declarations = listOf(
                        type("demo.Store", ENTITY, listOf(storeIdProp.id)),
                        type(
                            "demo.Book",
                            ENTITY,
                            listOf(bookIdProp.id, storeProp.id, conflictingStoreIdProp.id),
                        ),
                        storeIdProp,
                        bookIdProp,
                        storeProp,
                        conflictingStoreIdProp,
                    )
                )
            )
        }
        assertTrue("looks like an id view" in conflict.message.orEmpty())

        val orderId = LsiSymbolId.type("demo.Order")
        val lineId = LsiSymbolId.type("demo.OrderLine")
        val orderIdProp = property(
            orderId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val lineOrderIdProp = property(
            lineId,
            "orderId",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val lineOrderProp = property(
            lineId,
            "order",
            LsiDeclaredType(orderId),
            listOf(annotation(MANY_TO_ONE), annotation(MAPS_ID)),
        )

        compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.Order", ENTITY, listOf(orderIdProp.id)),
                    type("demo.OrderLine", ENTITY, listOf(lineOrderIdProp.id, lineOrderProp.id)),
                    orderIdProp,
                    lineOrderIdProp,
                    lineOrderProp,
                )
            )
        )
    }

    @Test
    fun `rejects invalid many to many view links`() {
        val bookId = LsiSymbolId.type("demo.Book")
        val linkId = LsiSymbolId.type("demo.BookAuthor")
        val authorId = LsiSymbolId.type("demo.Author")
        val otherId = LsiSymbolId.type("demo.Other")

        fun failure(
            viewType: LsiType = listType(authorId),
            basePropName: String = "links",
            baseAnnotation: LsiSymbolId = ONE_TO_MANY,
            deeperPropName: String = "",
            deeperTargets: List<LsiSymbolId> = listOf(authorId),
        ): String {
            val linksProp = property(
                bookId,
                "links",
                listType(linkId),
                listOf(annotation(baseAnnotation)),
            )
            val viewArguments = linkedMapOf<String, LsiAnnotationValue>(
                "prop" to LsiAnnotationValue.StringValue(basePropName)
            )
            if (deeperPropName.isNotEmpty()) {
                viewArguments["deeperProp"] = LsiAnnotationValue.StringValue(deeperPropName)
            }
            val viewProp = property(
                bookId,
                "authors",
                viewType,
                listOf(annotation(MANY_TO_MANY_VIEW, viewArguments)),
            )
            val deeperProps = deeperTargets.mapIndexed { index, targetId ->
                property(
                    linkId,
                    "author${index + 1}",
                    LsiDeclaredType(targetId),
                    listOf(annotation(MANY_TO_ONE)),
                )
            }
            val declarations = buildList {
                add(type("demo.Book", ENTITY, listOf(linksProp.id, viewProp.id)))
                add(type("demo.BookAuthor", ENTITY, deeperProps.map(LsiProperty::id)))
                add(type("demo.Author", ENTITY, emptyList()))
                add(type("demo.Other", ENTITY, emptyList()))
                add(linksProp)
                add(viewProp)
                addAll(deeperProps)
            }
            return assertFailsWith<ImmutablePrecompileException> {
                compileFixture(LsiWorkspace(declarations = declarations))
            }.message.orEmpty()
        }

        assertTrue("is not a list" in failure(viewType = LsiDeclaredType(authorId)))
        assertTrue("cannot find" in failure(basePropName = "missing"))
        assertTrue("not a one-to-many" in failure(baseAnnotation = MANY_TO_MANY))
        assertTrue("found 0" in failure(deeperTargets = emptyList()))
        assertTrue("found 2" in failure(deeperTargets = listOf(authorId, authorId)))
        assertTrue(
            "cannot find many-to-one deeper" in failure(
                deeperPropName = "author1",
                deeperTargets = listOf(otherId),
            )
        )
        assertTrue(
            "cannot find many-to-one deeper" in failure(
                deeperPropName = "missing",
            )
        )
    }

    @Test
    fun `resolves validation nullity annotation families`() {
        val entityId = LsiSymbolId.type("demo.NullityModel")
        val nullableProp = property(
            entityId,
            "nullableValue",
            LsiDeclaredType(STRING_TYPE, nullability = LsiNullability.PLATFORM),
            listOf(annotation(LsiSymbolId.type("javax.validation.constraints.Null"))),
        )
        val nonNullProp = property(
            entityId,
            "nonNullValue",
            LsiDeclaredType(STRING_TYPE, nullability = LsiNullability.PLATFORM),
            listOf(annotation(LsiSymbolId.type("org.jetbrains.annotations.NotNull"))),
        )
        val schema = compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.NullityModel", IMMUTABLE, listOf(nullableProp.id, nonNullProp.id)),
                    nullableProp,
                    nonNullProp,
                )
            )
        )
        val props = schema.types.single().props.associateBy(ImmutableProp::name)
        assertTrue(props.getValue("nullableValue").nullable)
        assertFalse(props.getValue("nonNullValue").nullable)

        val conflictingProp = property(
            entityId,
            "conflictingValue",
            LsiDeclaredType(STRING_TYPE, nullability = LsiNullability.PLATFORM),
            listOf(
                annotation(LsiSymbolId.type("javax.validation.constraints.Null")),
                annotation(LsiSymbolId.type("org.jetbrains.annotations.NotNull")),
            ),
        )
        val failure = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(
                LsiWorkspace(
                    declarations = listOf(
                        type("demo.NullityModel", IMMUTABLE, listOf(conflictingProp.id)),
                        conflictingProp,
                    )
                )
            )
        }
        assertTrue("cannot be decorated by both" in failure.message.orEmpty())
    }

    @Test
    fun `canonicalizes property type nullability and ignores nullity annotation spelling in snapshot`() {
        val entityId = LsiSymbolId.type("demo.NullabilityModel")
        val propertyId = LsiSymbolId.property(entityId, "rating")
        val aptSchema = compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.NullabilityModel", IMMUTABLE, listOf(propertyId)),
                    property(
                        ownerId = entityId,
                        name = "rating",
                        type = LsiPrimitiveType(
                            LsiPrimitiveKind.INT,
                            nullability = LsiNullability.PLATFORM,
                            boxed = true,
                        ),
                        annotations = listOf(
                            annotation(LsiSymbolId.type("org.jetbrains.annotations.Nullable")),
                        ),
                    ),
                ),
            ),
        )
        val kspSchema = compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.NullabilityModel", IMMUTABLE, listOf(propertyId)),
                    property(
                        ownerId = entityId,
                        name = "rating",
                        type = LsiPrimitiveType(
                            LsiPrimitiveKind.INT,
                            nullability = LsiNullability.NULLABLE,
                            boxed = true,
                        ),
                    ),
                ),
            ),
        )

        val aptProp = aptSchema.types.single().props.single()
        val kspProp = kspSchema.types.single().props.single()
        assertTrue(aptProp.nullable)
        assertEquals(LsiNullability.NULLABLE, aptProp.type.nullability)
        assertEquals("primitive:int:boxed?", aptProp.type.jimmerTypeSignature())
        assertEquals(kspProp.type, aptProp.type)
        assertTrue(aptProp.annotations.any { annotation ->
            annotation.type == LsiSymbolId.type("org.jetbrains.annotations.Nullable")
        })
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())
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

        val schema = compileFixture(workspace)
        val prop = schema.types.single().props.single { candidate -> candidate.name == "code" }

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
    fun `converter source boxing is equivalent only at the root`() {
        val modelId = LsiSymbolId.type("demo.BoxingModel")
        val propertyId = LsiSymbolId.property(modelId, "value")

        fun compile(
            propertyType: LsiType,
            converterSourceType: LsiType,
        ): ImmutableSchema {
            val valueProp = property(
                ownerId = modelId,
                name = "value",
                type = propertyType,
                annotations = listOf(annotation(CODE_FORMAT)),
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
                            LsiTypeArgument.invariant(converterSourceType),
                            LsiTypeArgument.invariant(LsiDeclaredType(STRING_TYPE)),
                        ),
                    )
                ),
            )
            return compileFixture(
                LsiWorkspace(
                    declarations = listOf(
                        type("demo.BoxingModel", IMMUTABLE, listOf(propertyId)),
                        valueProp,
                        codeFormatType,
                        converterType,
                    ),
                )
            )
        }

        val rawInt = LsiPrimitiveType(LsiPrimitiveKind.INT)
        val boxedInt = LsiPrimitiveType(LsiPrimitiveKind.INT, boxed = true)
        val scalarSchema = compile(rawInt, boxedInt)
        assertEquals(rawInt, scalarSchema.types.single().props.single().type)

        val javaList = LsiDeclaredType(
            declarationId = LsiSymbolId.type("java.util.List"),
            arguments = listOf(LsiTypeArgument.invariant(boxedInt)),
        )
        val kotlinMutableList = javaList.copy(
            declarationId = LsiSymbolId.type("kotlin.collections.MutableList"),
        )
        val listSchema = compile(javaList, kotlinMutableList)
        assertEquals(javaList, listSchema.types.single().props.single().type)

        val failure = assertFailsWith<ImmutablePrecompileException> {
            compile(
                propertyType = LsiArrayType(rawInt),
                converterSourceType = LsiArrayType(boxedInt),
            )
        }
        assertTrue(failure.message.orEmpty().contains("source type"), failure.message)
    }

    @Test
    fun `inherits and lifts declaring type id converter for list id view`() {
        val storeId = LsiSymbolId.type("demo.Store")
        val bookId = LsiSymbolId.type("demo.Book")
        val storeIdProp = property(
            storeId,
            "id",
            LsiDeclaredType(STRING_TYPE),
            listOf(annotation(ID)),
        )
        val bookIdProp = property(
            bookId,
            "id",
            LsiDeclaredType(STRING_TYPE),
            listOf(annotation(ID), annotation(CODE_FORMAT)),
        )
        val storesProp = property(
            bookId,
            "stores",
            listType(storeId),
            listOf(annotation(MANY_TO_MANY)),
        )
        val storeIdsProp = property(
            bookId,
            "storeIds",
            LsiDeclaredType(
                declarationId = LsiSymbolId.type("java.util.List"),
                arguments = listOf(
                    LsiTypeArgument.invariant(LsiDeclaredType(STRING_TYPE))
                ),
            ),
            listOf(
                annotation(
                    ID_VIEW,
                    mapOf("value" to LsiAnnotationValue.StringValue("stores")),
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
        val schema = compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.Store", ENTITY, listOf(storeIdProp.id)),
                    type("demo.Book", ENTITY, listOf(bookIdProp.id, storesProp.id, storeIdsProp.id)),
                    storeIdProp,
                    bookIdProp,
                    storesProp,
                    storeIdsProp,
                    codeFormatType,
                    converterType,
                ),
            )
        )
        val viewProp = schema.types.single { type -> type.id == bookId }
            .props.single { prop -> prop.name == "storeIds" }
        val converter = requireNotNull(viewProp.converter)
        val sourceList = assertIs<LsiDeclaredType>(converter.sourceType)
        val targetList = assertIs<LsiDeclaredType>(converter.targetType)

        assertEquals(LsiSymbolId.type("java.util.List"), sourceList.declarationId)
        assertEquals(STRING_TYPE, assertIs<LsiDeclaredType>(sourceList.arguments.single().type).declarationId)
        assertEquals(LsiSymbolId.type("java.util.List"), targetList.declarationId)
        assertEquals(
            LsiPrimitiveKind.LONG,
            assertIs<LsiPrimitiveType>(targetList.arguments.single().type).kind,
        )
        assertFalse(converter.sourceNullable)
        assertFalse(converter.targetNullable)
        assertFalse(converter.propertyNullable)
    }

    @Test
    fun `prefers explicit id view converter and validates source type`() {
        val storeId = LsiSymbolId.type("demo.Store")
        val bookId = LsiSymbolId.type("demo.Book")
        val idProp = property(
            storeId,
            "id",
            LsiDeclaredType(STRING_TYPE),
            listOf(annotation(ID), annotation(CODE_FORMAT)),
        )
        val storeProp = property(
            bookId,
            "store",
            LsiDeclaredType(storeId),
            listOf(annotation(MANY_TO_ONE)),
        )
        fun idViewProp(converterTypeId: LsiSymbolId): LsiProperty {
            return property(
                bookId,
                "storeId",
                LsiDeclaredType(STRING_TYPE),
                listOf(
                    annotation(ID_VIEW),
                    annotation(
                        JSON_CONVERTER,
                        mapOf(
                            "value" to LsiAnnotationValue.ClassValue(LsiDeclaredType(converterTypeId))
                        ),
                    ),
                ),
            )
        }
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
        val targetConverterType = declaration(
            qualifiedName = "demo.CodeConverter",
            kind = LsiTypeDeclarationKind.CLASS,
            superTypes = listOf(
                LsiDeclaredType(
                    CONVERTER,
                    arguments = listOf(
                        LsiTypeArgument.invariant(LsiDeclaredType(STRING_TYPE)),
                        LsiTypeArgument.invariant(LsiPrimitiveType(LsiPrimitiveKind.LONG)),
                    ),
                )
            ),
        )
        val explicitConverterType = declaration(
            qualifiedName = "demo.ExplicitCodeConverter",
            kind = LsiTypeDeclarationKind.CLASS,
            superTypes = listOf(
                LsiDeclaredType(
                    CONVERTER,
                    arguments = listOf(
                        LsiTypeArgument.invariant(LsiDeclaredType(STRING_TYPE)),
                        LsiTypeArgument.invariant(LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN)),
                    ),
                )
            ),
        )
        val invalidConverterType = declaration(
            qualifiedName = "demo.InvalidCodeConverter",
            kind = LsiTypeDeclarationKind.CLASS,
            superTypes = listOf(
                LsiDeclaredType(
                    CONVERTER,
                    arguments = listOf(
                        LsiTypeArgument.invariant(LsiPrimitiveType(LsiPrimitiveKind.INT)),
                        LsiTypeArgument.invariant(LsiDeclaredType(STRING_TYPE)),
                    ),
                )
            ),
        )
        fun workspace(viewProp: LsiProperty): LsiWorkspace {
            return LsiWorkspace(
                declarations = listOf(
                    type("demo.Store", ENTITY, listOf(idProp.id)),
                    type("demo.Book", ENTITY, listOf(storeProp.id, viewProp.id)),
                    idProp,
                    storeProp,
                    viewProp,
                    codeFormatType,
                    targetConverterType,
                    explicitConverterType,
                    invalidConverterType,
                )
            )
        }

        val schema = compileFixture(workspace(idViewProp(EXPLICIT_CODE_CONVERTER)))
        val converter = requireNotNull(
            schema.types.single { type -> type.id == bookId }
                .props.single { prop -> prop.name == "storeId" }
                .converter
        )
        assertEquals(EXPLICIT_CODE_CONVERTER, converter.converterTypeId)
        assertEquals(LsiPrimitiveKind.BOOLEAN, assertIs<LsiPrimitiveType>(converter.targetType).kind)

        val failure = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(workspace(idViewProp(INVALID_CODE_CONVERTER)))
        }
        assertTrue("source type" in failure.message.orEmpty())

        val convertedAssociation = storeProp.copy(
            annotations = storeProp.annotations + annotation(
                JSON_CONVERTER,
                mapOf(
                    "value" to LsiAnnotationValue.ClassValue(LsiDeclaredType(EXPLICIT_CODE_CONVERTER))
                ),
            )
        )
        val associationFailure = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(
                LsiWorkspace(
                    declarations = listOf(
                        type("demo.Store", ENTITY, listOf(idProp.id)),
                        type("demo.Book", ENTITY, listOf(convertedAssociation.id)),
                        idProp,
                        convertedAssociation,
                        codeFormatType,
                        targetConverterType,
                        explicitConverterType,
                    )
                )
            )
        }
        assertTrue("association property" in associationFailure.message.orEmpty())

        val formattedViewProp = idViewProp(EXPLICIT_CODE_CONVERTER).copy(
            annotations = idViewProp(EXPLICIT_CODE_CONVERTER).annotations + annotation(JSON_FORMAT)
        )
        val formatFailure = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(workspace(formattedViewProp))
        }
        assertTrue("cannot declare both" in formatFailure.message.orEmpty())
    }

    @Test
    fun `only entity can override direct mapped superclass property`() {
        val mappedChild = overrideCategoryWorkspace(
            baseMarker = MAPPED_SUPERCLASS,
            childMarker = MAPPED_SUPERCLASS,
            baseAnnotations = listOf(default("0", LsiLanguage.KOTLIN)),
            childAnnotations = listOf(default("1", LsiLanguage.KOTLIN)),
        )
        val mappedException = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(mappedChild)
        }
        assertTrue(
            mappedException.message.orEmpty().contains("mapped superclass of an entity"),
            mappedException.message,
        )

        val entityParent = overrideCategoryWorkspace(
            baseMarker = ENTITY,
            childMarker = ENTITY,
            baseAnnotations = listOf(annotation(DISCRIMINATOR)),
            baseTypeAnnotations = listOf(annotation(INHERITANCE)),
        )
        val entityException = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(entityParent)
        }
        assertTrue(
            entityException.message.orEmpty().contains("mapped superclass of an entity"),
            entityException.message,
        )
    }

    @Test
    fun `mapped superclass cannot redeclare property without shadowing semantic annotation`() {
        val workspace = overrideCategoryWorkspace(
            baseMarker = MAPPED_SUPERCLASS,
            childMarker = MAPPED_SUPERCLASS,
        )

        assertOverrideEligibilityRejected(workspace)
    }

    @Test
    fun `entity cannot redeclare entity property without shadowing semantic annotation`() {
        assertOverrideEligibilityRejected(entityOverrideWorkspace())
    }

    @Test
    fun `entity cannot redeclare property from indirect mapped superclass without shadowing semantic annotation`() {
        assertOverrideEligibilityRejected(indirectMappedSuperclassOverrideWorkspace())
    }

    @Test
    fun `override and suppress annotations are omitted from immutable property annotations`() {
        val workspace = overrideCategoryWorkspace(
            baseMarker = MAPPED_SUPERCLASS,
            childMarker = ENTITY,
            baseAnnotations = listOf(
                default("0", LsiLanguage.KOTLIN),
                annotation(
                    COLUMN,
                    mapOf("name" to LsiAnnotationValue.StringValue("VALUE")),
                ),
            ),
            childAnnotations = listOf(
                default("1", LsiLanguage.KOTLIN),
                annotation(JAVA_OVERRIDE),
                annotation(KOTLIN_SUPPRESS),
            ),
        )

        val schema = compileFixture(workspace)
        val value = schema.types
            .single { type -> type.id == LsiSymbolId.type("demo.Child") }
            .props
            .single { prop -> prop.name == "value" }

        assertEquals(listOf(DEFAULT, COLUMN), value.annotations.map(LsiAnnotation::type))
        assertEquals("1", value.annotationString(DEFAULT, "value"))
        assertEquals("VALUE", value.annotationString(COLUMN, "name"))
    }

    @Test
    fun `mapped superclass property override is allowed without semantic annotation shadowing`() {
        val workspace = overrideCategoryWorkspace(
            baseMarker = MAPPED_SUPERCLASS,
            childMarker = ENTITY,
            baseAnnotations = listOf(annotation(JAVA_OVERRIDE)),
            childAnnotations = listOf(annotation(JAVA_OVERRIDE)),
        )

        val schema = compileFixture(workspace)

        assertEquals(2, schema.types.size)
        assertTrue(
            schema.types.single { type -> type.id == LsiSymbolId.type("demo.Child") }
                .props
                .single { prop -> prop.name == "value" }
                .overridden
        )
    }

    @Test
    fun `validates override against every direct mapped superclass property`() {
        val alignedBaseId = LsiSymbolId.type("demo.AlignedBase")
        val nullableBaseId = LsiSymbolId.type("demo.NullableBase")
        val entityId = LsiSymbolId.type("demo.MultiBaseEntity")
        val nullableParameterId = LsiSymbolId.typeParameter(nullableBaseId, "T")
        val alignedValue = property(
            ownerId = alignedBaseId,
            name = "value",
            type = LsiDeclaredType(STRING_TYPE),
        )
        val nullableValue = property(
            ownerId = nullableBaseId,
            name = "value",
            type = LsiTypeParameterRef(
                parameterId = nullableParameterId,
                nullability = LsiNullability.NULLABLE,
            ),
        )
        val entityValue = property(
            ownerId = entityId,
            name = "value",
            type = LsiDeclaredType(STRING_TYPE),
            overrides = listOf(
                LsiOverride(alignedValue.id),
                LsiOverride(nullableValue.id),
            ),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = alignedBaseId.requireTypeQualifiedName(),
                    marker = MAPPED_SUPERCLASS,
                    memberIds = listOf(alignedValue.id),
                ),
                alignedValue,
                type(
                    qualifiedName = nullableBaseId.requireTypeQualifiedName(),
                    marker = MAPPED_SUPERCLASS,
                    memberIds = listOf(nullableValue.id),
                    typeParameters = listOf(LsiTypeParameter(nullableParameterId, "T")),
                ),
                nullableValue,
                type(
                    qualifiedName = entityId.requireTypeQualifiedName(),
                    marker = ENTITY,
                    memberIds = listOf(entityValue.id),
                    superTypes = listOf(
                        LsiDeclaredType(alignedBaseId),
                        LsiDeclaredType(
                            declarationId = nullableBaseId,
                            arguments = listOf(
                                LsiTypeArgument.invariant(LsiDeclaredType(STRING_TYPE)),
                            ),
                        ),
                    ),
                ),
                entityValue,
            ),
        )

        val alignedWorkspace = LsiWorkspace(
            sources = workspace.sources,
            declarations = workspace.declarations.map { declaration ->
                if (declaration.id == nullableValue.id) {
                    nullableValue.copy(
                        type = LsiTypeParameterRef(
                            parameterId = nullableParameterId,
                            nullability = LsiNullability.NON_NULL,
                        ),
                    )
                } else {
                    declaration
                }
            },
        )
        val alignedEntityValue = compileFixture(alignedWorkspace)
            .types
            .single { type -> type.id == entityId }
            .props
            .single { prop -> prop.name == "value" }
        assertEquals(3, alignedEntityValue.overrideChain.size)

        val exception = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(workspace)
        }
        assertEquals(entityValue.id, exception.declarationId)
        assertTrue(exception.message.orEmpty().contains("nullability"))
    }

    @Test
    fun `rejects an indirect override hidden behind an aligned direct override`() {
        val alignedBaseId = LsiSymbolId.type("demo.AlignedBase")
        val rootBaseId = LsiSymbolId.type("demo.RootBase")
        val middleBaseId = LsiSymbolId.type("demo.MiddleBase")
        val entityId = LsiSymbolId.type("demo.MultiPathEntity")
        val alignedValue = property(
            ownerId = alignedBaseId,
            name = "value",
            type = LsiDeclaredType(STRING_TYPE),
        )
        val rootValue = property(
            ownerId = rootBaseId,
            name = "value",
            type = LsiDeclaredType(STRING_TYPE),
        )
        val entityValue = property(
            ownerId = entityId,
            name = "value",
            type = LsiDeclaredType(STRING_TYPE),
            overrides = listOf(
                LsiOverride(alignedValue.id, distance = 1),
                LsiOverride(rootValue.id, distance = 2),
            ),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = alignedBaseId.requireTypeQualifiedName(),
                    marker = MAPPED_SUPERCLASS,
                    memberIds = listOf(alignedValue.id),
                ),
                alignedValue,
                type(
                    qualifiedName = rootBaseId.requireTypeQualifiedName(),
                    marker = MAPPED_SUPERCLASS,
                    memberIds = listOf(rootValue.id),
                ),
                rootValue,
                type(
                    qualifiedName = middleBaseId.requireTypeQualifiedName(),
                    marker = MAPPED_SUPERCLASS,
                    memberIds = emptyList(),
                    superTypes = listOf(LsiDeclaredType(rootBaseId)),
                ),
                type(
                    qualifiedName = entityId.requireTypeQualifiedName(),
                    marker = ENTITY,
                    memberIds = listOf(entityValue.id),
                    superTypes = listOf(
                        LsiDeclaredType(alignedBaseId),
                        LsiDeclaredType(middleBaseId),
                    ),
                ),
                entityValue,
            ),
        )

        assertOverrideEligibilityRejected(workspace)
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
            baseType = LsiPrimitiveType(LsiPrimitiveKind.INT),
            childType = LsiPrimitiveType(LsiPrimitiveKind.INT, boxed = true),
            expected = "resolved type",
        )
        assertOverrideRejected(
            baseType = LsiDeclaredType(STRING_TYPE),
            childType = listType(STRING_TYPE),
            expected = "list category",
        )
        assertOverrideRejected(
            baseType = listType(STRING_TYPE),
            childType = listType(STRING_TYPE),
            childAnnotations = listOf(annotation(SCALAR)),
            expected = "list category",
        )
        val jsonScalar = LsiSymbolId.type("demo.JsonScalar")
        assertOverrideRejected(
            baseType = listType(STRING_TYPE),
            childType = listType(STRING_TYPE),
            childAnnotations = listOf(annotation(jsonScalar)),
            expected = "list category",
            extraTypes = listOf(
                declaration(
                    qualifiedName = jsonScalar.requireTypeQualifiedName(),
                    kind = LsiTypeDeclarationKind.ANNOTATION,
                    annotations = listOf(annotation(SCALAR)),
                )
            ),
        )
    }

    @Test
    fun `non-list collection types require scalar semantics`() {
        val collectionTypes = listOf(
            COLLECTION_TYPE,
            SET_TYPE,
            KOTLIN_MUTABLE_LIST_TYPE,
            CUSTOM_COLLECTION_TYPE,
        )
        val hierarchyTypes = listOf(
            declaration(
                qualifiedName = COLLECTION_TYPE.requireTypeQualifiedName(),
                kind = LsiTypeDeclarationKind.INTERFACE,
            ),
            declaration(
                qualifiedName = SET_TYPE.requireTypeQualifiedName(),
                kind = LsiTypeDeclarationKind.INTERFACE,
                superTypes = listOf(LsiDeclaredType(COLLECTION_TYPE)),
            ),
            declaration(
                qualifiedName = KOTLIN_MUTABLE_LIST_TYPE.requireTypeQualifiedName(),
                kind = LsiTypeDeclarationKind.INTERFACE,
                superTypes = listOf(LsiDeclaredType(COLLECTION_TYPE)),
            ),
            declaration(
                qualifiedName = CUSTOM_COLLECTION_TYPE.requireTypeQualifiedName(),
                kind = LsiTypeDeclarationKind.INTERFACE,
                superTypes = listOf(LsiDeclaredType(COLLECTION_TYPE)),
            ),
        )

        for (collectionType in collectionTypes) {
            val failure = assertFailsWith<ImmutablePrecompileException> {
                compileFixture(
                    collectionPropertyWorkspace(
                        collectionType = collectionType,
                        annotations = emptyList(),
                        hierarchyTypes = hierarchyTypes,
                    )
                )
            }
            assertTrue(failure.message.orEmpty().contains("must use java.util.List"))

            val scalarSchema = compileFixture(
                collectionPropertyWorkspace(
                    collectionType = collectionType,
                    annotations = listOf(annotation(SCALAR)),
                    hierarchyTypes = hierarchyTypes,
                )
            )
            assertFalse(scalarSchema.types.single().props.single { prop -> prop.name == "values" }.list)

            val formulaSchema = compileFixture(
                collectionPropertyWorkspace(
                    collectionType = collectionType,
                    annotations = listOf(formula(dependencies = listOf("source"))),
                    modality = LsiModality.FINAL,
                    hierarchyTypes = hierarchyTypes,
                )
            )
            assertFalse(formulaSchema.types.single().props.single { prop -> prop.name == "values" }.list)
        }
    }

    @Test
    fun `validates immutable list argument and element shapes`() {
        val entityId = LsiSymbolId.type("demo.ListShapeEntity")
        val listTypeId = LsiSymbolId.type("java.util.List")

        fun compile(
            propertyType: LsiType,
            annotations: List<LsiAnnotation> = emptyList(),
        ): ImmutableSchema {
            val valuesProp = property(entityId, "values", propertyType, annotations)
            return compileFixture(
                LsiWorkspace(
                    declarations = listOf(
                        type("demo.ListShapeEntity", ENTITY, listOf(valuesProp.id)),
                        valuesProp,
                    )
                )
            )
        }

        val invalidArgumentShapes = listOf(
            LsiDeclaredType(listTypeId),
            LsiDeclaredType(listTypeId, arguments = listOf(LsiTypeArgument.STAR)),
            LsiDeclaredType(
                listTypeId,
                arguments = listOf(LsiTypeArgument.output(LsiDeclaredType(STRING_TYPE))),
            ),
        )
        invalidArgumentShapes.forEach { propertyType ->
            val exception = assertFailsWith<ImmutablePrecompileException> {
                compile(propertyType)
            }
            assertTrue(
                exception.message.orEmpty().contains("exactly one invariant, non-star element type"),
                exception.message,
            )
        }

        val nestedListType = LsiDeclaredType(
            listTypeId,
            arguments = listOf(LsiTypeArgument.invariant(listType(STRING_TYPE))),
        )
        val arrayElementType = LsiDeclaredType(
            listTypeId,
            arguments = listOf(
                LsiTypeArgument.invariant(LsiArrayType(LsiDeclaredType(STRING_TYPE))),
            ),
        )
        listOf(nestedListType, arrayElementType).forEach { propertyType ->
            val exception = assertFailsWith<ImmutablePrecompileException> {
                compile(propertyType)
            }
            assertTrue(
                exception.message.orEmpty().contains("non-parameterized declared type"),
                exception.message,
            )
        }

        val scalarNested = compile(nestedListType, listOf(annotation(SCALAR)))
            .types
            .single { type -> type.id == entityId }
            .props
            .single { prop -> prop.name == "values" }
        assertFalse(scalarNested.list)
        assertEquals(PrimaryMapping.SCALAR, scalarNested.primaryMapping)
    }

    @Test
    fun `resolves stable formula dependency paths across associations and embedded properties`() {
        val addressTypeId = LsiSymbolId.type("demo.Address")
        val departmentTypeId = LsiSymbolId.type("demo.Department")
        val employeeTypeId = LsiSymbolId.type("demo.Employee")
        val cityProp = property(addressTypeId, "city", LsiDeclaredType(STRING_TYPE))
        val departmentNameProp = property(departmentTypeId, "name", LsiDeclaredType(STRING_TYPE))
        val addressProp = property(departmentTypeId, "address", LsiDeclaredType(addressTypeId))
        val departmentProp = property(
            employeeTypeId,
            "department",
            LsiDeclaredType(departmentTypeId),
            listOf(annotation(MANY_TO_ONE)),
        )
        val displayNameProp = property(
            employeeTypeId,
            "displayName",
            LsiDeclaredType(STRING_TYPE),
            listOf(
                formula(
                    dependencies = listOf(
                        "department.name",
                        "department.address.city",
                        "department.name",
                    )
                )
            ),
            modality = LsiModality.FINAL,
        )
        val schema = compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.Address", EMBEDDABLE, listOf(cityProp.id)),
                    cityProp,
                    type(
                        "demo.Department",
                        ENTITY,
                        listOf(departmentNameProp.id, addressProp.id),
                    ),
                    departmentNameProp,
                    addressProp,
                    type(
                        "demo.Employee",
                        ENTITY,
                        listOf(departmentProp.id, displayNameProp.id),
                    ),
                    departmentProp,
                    displayNameProp,
                )
            )
        )

        val formulaProp = schema.typesById.getValue(employeeTypeId)
            .props
            .single { prop -> prop.name == "displayName" }
        assertEquals(
            listOf(
                FormulaDependency(
                    listOf(
                        departmentProp.id,
                        departmentNameProp.id,
                    )
                ),
                FormulaDependency(
                    listOf(
                        departmentProp.id,
                        addressProp.id,
                        cityProp.id,
                    )
                ),
            ),
            formulaProp.formulaDependencies,
        )
        assertEquals(
            formulaProp.formulaDependencies.map(FormulaDependency::propIds),
            schema.formulaDependencyPathsByPropId.getValue(formulaProp.id),
        )
        assertEquals(
            listOf(formulaProp.id),
            schema.dependentFormulaPropIdsByPropId.getValue(departmentProp.id),
        )
        assertEquals(
            listOf(formulaProp.id),
            schema.dependentFormulaPropIdsByPropId.getValue(cityProp.id),
        )
        assertTrue(schema.normalizedSnapshot().contains("formula-dependency"))
    }

    @Test
    fun `rejects unresolved and scalar intermediate formula dependency segments`() {
        val entityTypeId = LsiSymbolId.type("demo.FormulaEntity")
        val nameProp = property(entityTypeId, "name", LsiDeclaredType(STRING_TYPE))

        fun compile(dependency: String) {
            val formulaProp = property(
                entityTypeId,
                "displayName",
                LsiDeclaredType(STRING_TYPE),
                listOf(formula(dependencies = listOf(dependency))),
                modality = LsiModality.FINAL,
            )
            compileFixture(
                LsiWorkspace(
                    declarations = listOf(
                        type("demo.FormulaEntity", ENTITY, listOf(nameProp.id, formulaProp.id)),
                        nameProp,
                        formulaProp,
                    )
                )
            )
        }

        val missing = assertFailsWith<ImmutablePrecompileException> {
            compile("missing")
        }
        assertTrue(missing.message.orEmpty().contains("there is no property 'missing'"))

        val scalarIntermediate = assertFailsWith<ImmutablePrecompileException> {
            compile("name.length")
        }
        assertTrue(scalarIntermediate.message.orEmpty().contains("neither an association nor an embedded property"))
    }

    @Test
    fun `formula dependency paths participate in immutable fingerprints`() {
        fun schema(dependency: String): ImmutableSchema {
            val entityTypeId = LsiSymbolId.type("demo.FingerprintFormulaEntity")
            val firstProp = property(entityTypeId, "first", LsiDeclaredType(STRING_TYPE))
            val secondProp = property(entityTypeId, "second", LsiDeclaredType(STRING_TYPE))
            val formulaProp = property(
                entityTypeId,
                "display",
                LsiDeclaredType(STRING_TYPE),
                listOf(formula(dependencies = listOf(dependency))),
                modality = LsiModality.FINAL,
            )
            return compileFixture(
                LsiWorkspace(
                    declarations = listOf(
                        type(
                            "demo.FingerprintFormulaEntity",
                            ENTITY,
                            listOf(firstProp.id, secondProp.id, formulaProp.id),
                        ),
                        firstProp,
                        secondProp,
                        formulaProp,
                    )
                )
            )
        }

        assertNotEquals(schema("first").fingerprint(), schema("second").fingerprint())
    }

    @Test
    fun `precompiles transient resolvers and fetchability`() {
        val typeId = LsiSymbolId.type("demo.TransientEntity")
        val resolverTypeId = LsiSymbolId.type("demo.TransientResolver")

        fun transient(
            value: LsiType? = null,
            ref: String? = null,
        ): LsiAnnotation {
            return annotation(
                type = TRANSIENT,
                arguments = buildMap {
                    value?.let { type ->
                        put("value", LsiAnnotationValue.ClassValue(type))
                    }
                    ref?.let { name ->
                        put("ref", LsiAnnotationValue.StringValue(name))
                    }
                },
            )
        }

        val idProp = property(
            typeId,
            "id",
            LsiPrimitiveType(LsiPrimitiveKind.LONG),
            listOf(annotation(ID)),
        )
        val plainProp = property(typeId, "name", LsiDeclaredType(STRING_TYPE))
        val voidProp = property(
            typeId,
            "voidValue",
            LsiDeclaredType(STRING_TYPE),
            listOf(transient(value = LsiPrimitiveType(LsiPrimitiveKind.VOID))),
        )
        val unitProp = property(
            typeId,
            "unitValue",
            LsiDeclaredType(STRING_TYPE),
            listOf(transient(value = LsiDeclaredType(LsiSymbolId.type("kotlin.Unit")))),
        )
        val typeResolverProp = property(
            typeId,
            "typeValue",
            LsiDeclaredType(STRING_TYPE),
            listOf(transient(value = LsiDeclaredType(resolverTypeId))),
        )
        val referenceResolverProp = property(
            typeId,
            "referenceValue",
            LsiDeclaredType(STRING_TYPE),
            listOf(transient(ref = "transientResolver")),
        )
        val props = listOf(
            idProp,
            plainProp,
            voidProp,
            unitProp,
            typeResolverProp,
            referenceResolverProp,
        )
        val schema = compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.TransientEntity", ENTITY, props.map(LsiProperty::id)),
                ) + props,
            )
        )
        val propsByName = schema.typesById.getValue(typeId).props.associateBy(ImmutableProp::name)

        assertFalse(propsByName.getValue("id").fetchable)
        assertTrue(propsByName.getValue("name").fetchable)
        assertFalse(propsByName.getValue("voidValue").fetchable)
        assertNull(propsByName.getValue("voidValue").transientResolver)
        assertFalse(propsByName.getValue("unitValue").fetchable)
        assertNull(propsByName.getValue("unitValue").transientResolver)
        assertTrue(propsByName.getValue("typeValue").fetchable)
        assertEquals(
            TransientResolver.Type(resolverTypeId),
            propsByName.getValue("typeValue").transientResolver,
        )
        assertTrue(propsByName.getValue("referenceValue").fetchable)
        assertEquals(
            TransientResolver.Reference("transientResolver"),
            propsByName.getValue("referenceValue").transientResolver,
        )
        val snapshot = schema.normalizedSnapshot()
        assertTrue("transient-resolver|${typeResolverProp.id.value}|TYPE|${resolverTypeId.value}" in snapshot)
        assertTrue(
            "transient-resolver|${referenceResolverProp.id.value}|REFERENCE|transientResolver" in snapshot
        )

        val invalidProp = property(
            typeId,
            "invalidValue",
            LsiDeclaredType(STRING_TYPE),
            listOf(
                transient(
                    value = LsiDeclaredType(resolverTypeId),
                    ref = "transientResolver",
                )
            ),
        )
        val exception = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(
                LsiWorkspace(
                    declarations = listOf(
                        type("demo.TransientEntity", ENTITY, listOf(invalidProp.id)),
                        invalidProp,
                    )
                )
            )
        }
        assertTrue(exception.message.orEmpty().contains("cannot specify both resolver type and resolver reference"))
    }

    @Test
    fun `mapped superclass transient resolver is replaced by entity override annotation`() {
        val baseId = LsiSymbolId.type("demo.TransientBase")
        val refChildId = LsiSymbolId.type("demo.RefTransientChild")
        val plainChildId = LsiSymbolId.type("demo.PlainTransientChild")
        val resolverTypeId = LsiSymbolId.type("demo.BaseResolver")
        val baseProp = property(
            baseId,
            "value",
            LsiDeclaredType(STRING_TYPE),
            listOf(
                annotation(
                    TRANSIENT,
                    mapOf(
                        "value" to LsiAnnotationValue.ClassValue(LsiDeclaredType(resolverTypeId))
                    ),
                )
            ),
        )
        val refChildProp = property(
            refChildId,
            "value",
            LsiDeclaredType(STRING_TYPE),
            listOf(
                annotation(
                    TRANSIENT,
                    mapOf("ref" to LsiAnnotationValue.StringValue("childResolver")),
                )
            ),
            overrides = listOf(LsiOverride(baseProp.id)),
        )
        val plainChildProp = property(
            plainChildId,
            "value",
            LsiDeclaredType(STRING_TYPE),
            listOf(annotation(TRANSIENT)),
            overrides = listOf(LsiOverride(baseProp.id)),
        )
        val schema = compileFixture(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.TransientBase", MAPPED_SUPERCLASS, listOf(baseProp.id)),
                    baseProp,
                    type(
                        "demo.RefTransientChild",
                        ENTITY,
                        listOf(refChildProp.id),
                        superTypes = listOf(LsiDeclaredType(baseId)),
                    ),
                    refChildProp,
                    type(
                        "demo.PlainTransientChild",
                        ENTITY,
                        listOf(plainChildProp.id),
                        superTypes = listOf(LsiDeclaredType(baseId)),
                    ),
                    plainChildProp,
                )
            )
        )

        val base = schema.typesById.getValue(baseId).props.single()
        val refChild = schema.typesById.getValue(refChildId).props.single { prop -> prop.name == "value" }
        val plainChild = schema.typesById.getValue(plainChildId).props.single { prop -> prop.name == "value" }
        assertEquals(TransientResolver.Type(resolverTypeId), base.transientResolver)
        assertEquals(TransientResolver.Reference("childResolver"), refChild.transientResolver)
        assertTrue(refChild.fetchable)
        assertNull(plainChild.transientResolver)
        assertFalse(plainChild.fetchable)
    }

    @Test
    fun `validates language sql and embeddable formula contracts`() {
        fun compile(
            marker: LsiSymbolId = ENTITY,
            modality: LsiModality,
            sql: String = "",
            dependencies: List<String> = emptyList(),
        ) {
            val typeId = LsiSymbolId.type("demo.FormulaContract")
            val sourceProp = property(typeId, "source", LsiDeclaredType(STRING_TYPE))
            val formulaProp = property(
                typeId,
                "result",
                LsiDeclaredType(STRING_TYPE),
                listOf(formula(sql = sql, dependencies = dependencies)),
                modality = modality,
            )
            compileFixture(
                LsiWorkspace(
                    declarations = listOf(
                        type("demo.FormulaContract", marker, listOf(sourceProp.id, formulaProp.id)),
                        sourceProp,
                        formulaProp,
                    )
                )
            )
        }

        assertTrue(
            assertFailsWith<ImmutablePrecompileException> {
                compile(modality = LsiModality.ABSTRACT)
            }.message.orEmpty().contains("must specify sql")
        )
        assertTrue(
            assertFailsWith<ImmutablePrecompileException> {
                compile(
                    modality = LsiModality.ABSTRACT,
                    sql = "SOURCE",
                    dependencies = listOf("source"),
                )
            }.message.orEmpty().contains("cannot specify dependencies")
        )
        assertTrue(
            assertFailsWith<ImmutablePrecompileException> {
                compile(modality = LsiModality.FINAL)
            }.message.orEmpty().contains("must specify dependencies")
        )
        assertTrue(
            assertFailsWith<ImmutablePrecompileException> {
                compile(
                    modality = LsiModality.FINAL,
                    sql = "SOURCE",
                    dependencies = listOf("source"),
                )
            }.message.orEmpty().contains("cannot specify sql")
        )
        assertTrue(
            assertFailsWith<ImmutablePrecompileException> {
                compile(
                    marker = EMBEDDABLE,
                    modality = LsiModality.ABSTRACT,
                    sql = "SOURCE",
                )
            }.message.orEmpty().contains("cannot be declared in embeddable type")
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
        assertOverrideRejected(
            baseType = listType(STRING_TYPE),
            childType = listType(STRING_TYPE),
            childAnnotations = listOf(formula(dependencies = listOf("name"))),
            childModality = LsiModality.FINAL,
            expected = "list category",
        )
    }

    private fun assertOverrideRejected(
        baseType: LsiType,
        childType: LsiType,
        baseAnnotations: List<LsiAnnotation> = emptyList(),
        childAnnotations: List<LsiAnnotation> = emptyList(),
        baseModality: LsiModality = LsiModality.ABSTRACT,
        childModality: LsiModality = LsiModality.ABSTRACT,
        expected: String,
        extraTypes: List<LsiClass> = emptyList(),
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
        val exception = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(workspace)
        }
        assertTrue(exception.message.orEmpty().contains(expected))
    }

    private fun collectionPropertyWorkspace(
        collectionType: LsiSymbolId,
        annotations: List<LsiAnnotation>,
        modality: LsiModality = LsiModality.ABSTRACT,
        hierarchyTypes: List<LsiClass>,
    ): LsiWorkspace {
        val entityId = LsiSymbolId.type("demo.CollectionEntity")
        val sourceProp = property(
            ownerId = entityId,
            name = "source",
            type = LsiDeclaredType(STRING_TYPE),
        )
        val collectionProp = property(
            ownerId = entityId,
            name = "values",
            type = LsiDeclaredType(
                declarationId = collectionType,
                arguments = listOf(LsiTypeArgument.invariant(LsiDeclaredType(STRING_TYPE))),
            ),
            annotations = annotations,
            modality = modality,
        )
        return LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = entityId.requireTypeQualifiedName(),
                    marker = ENTITY,
                    memberIds = listOf(sourceProp.id, collectionProp.id),
                ),
                sourceProp,
                collectionProp,
            ) + hierarchyTypes,
        )
    }

    private fun assertOverrideEligibilityRejected(workspace: LsiWorkspace) {
        val exception = assertFailsWith<ImmutablePrecompileException> {
            compileFixture(workspace)
        }

        assertTrue(exception.message.orEmpty().contains("mapped superclass of an entity"))
    }

    private fun compileFixture(workspace: LsiWorkspace): ImmutableSchema {
        return workspace.completeEntityIdentities().toImmutableSchema()
    }

    private fun compileFixture(
        workspace: LsiWorkspace,
        targetTypeIds: Set<LsiSymbolId>,
    ): ImmutableSchema {
        return workspace.completeEntityIdentities().toImmutableSchema(targetTypeIds)
    }

    private fun entityOverrideWorkspace(): LsiWorkspace {
        val baseId = LsiSymbolId.type("demo.EntityBase")
        val childId = LsiSymbolId.type("demo.EntityChild")
        val discriminatorProp = property(
            ownerId = baseId,
            name = "kind",
            type = LsiDeclaredType(STRING_TYPE),
            annotations = listOf(annotation(DISCRIMINATOR)),
        )
        val baseValueProp = property(
            ownerId = baseId,
            name = "value",
            type = LsiDeclaredType(STRING_TYPE),
        )
        val childValueProp = property(
            ownerId = childId,
            name = "value",
            type = LsiDeclaredType(STRING_TYPE),
            overrides = listOf(LsiOverride(baseValueProp.id)),
        )
        return LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = baseId.requireTypeQualifiedName(),
                    marker = ENTITY,
                    memberIds = listOf(discriminatorProp.id, baseValueProp.id),
                    typeAnnotations = listOf(annotation(INHERITANCE)),
                ),
                discriminatorProp,
                baseValueProp,
                type(
                    qualifiedName = childId.requireTypeQualifiedName(),
                    marker = ENTITY,
                    memberIds = listOf(childValueProp.id),
                    superTypes = listOf(LsiDeclaredType(baseId)),
                ),
                childValueProp,
            ),
        )
    }

    private fun indirectMappedSuperclassOverrideWorkspace(): LsiWorkspace {
        val baseId = LsiSymbolId.type("demo.IndirectBase")
        val middleId = LsiSymbolId.type("demo.IndirectMiddle")
        val entityId = LsiSymbolId.type("demo.IndirectEntity")
        val baseValueProp = property(
            ownerId = baseId,
            name = "value",
            type = LsiDeclaredType(STRING_TYPE),
        )
        val entityValueProp = property(
            ownerId = entityId,
            name = "value",
            type = LsiDeclaredType(STRING_TYPE),
            overrides = listOf(LsiOverride(baseValueProp.id)),
        )
        return LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = baseId.requireTypeQualifiedName(),
                    marker = MAPPED_SUPERCLASS,
                    memberIds = listOf(baseValueProp.id),
                ),
                baseValueProp,
                type(
                    qualifiedName = middleId.requireTypeQualifiedName(),
                    marker = MAPPED_SUPERCLASS,
                    memberIds = emptyList(),
                    superTypes = listOf(LsiDeclaredType(baseId)),
                ),
                type(
                    qualifiedName = entityId.requireTypeQualifiedName(),
                    marker = ENTITY,
                    memberIds = listOf(entityValueProp.id),
                    superTypes = listOf(LsiDeclaredType(middleId)),
                ),
                entityValueProp,
            ),
        )
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
            documentation = "Base status documentation",
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

    private fun microServiceWorkspace(language: LsiLanguage): LsiWorkspace {
        val origin = sourceOrigin(language)
        val nullability = if (language == LsiLanguage.JAVA) {
            LsiNullability.PLATFORM
        } else {
            LsiNullability.NON_NULL
        }
        val remoteNullability = LsiNullability.NULLABLE
        val parameterId = LsiSymbolId.typeParameter(REMOTE_BASE_TYPE, "T")
        val baseParentId = LsiSymbolId.property(REMOTE_BASE_TYPE, "parent")
        val productId = LsiSymbolId.property(REMOTE_NODE_TYPE, "product")
        val base = type(
            qualifiedName = REMOTE_BASE_TYPE.requireTypeQualifiedName(),
            marker = MAPPED_SUPERCLASS,
            memberIds = listOf(baseParentId),
            typeParameters = listOf(LsiTypeParameter(parameterId, "T")),
            markerArguments = mapOf(
                "acrossMicroServices" to LsiAnnotationValue.BooleanValue(true),
            ),
            origin = origin,
        )
        val baseParent = property(
            ownerId = REMOTE_BASE_TYPE,
            name = "parent",
            type = LsiTypeParameterRef(parameterId, nullability),
            annotations = listOf(annotation(MANY_TO_ONE)),
            origin = origin,
        )
        val node = type(
            qualifiedName = REMOTE_NODE_TYPE.requireTypeQualifiedName(),
            marker = ENTITY,
            memberIds = listOf(productId),
            superTypes = listOf(
                LsiDeclaredType(
                    declarationId = REMOTE_BASE_TYPE,
                    arguments = listOf(
                        LsiTypeArgument.invariant(
                            LsiDeclaredType(REMOTE_NODE_TYPE, nullability = nullability),
                        )
                    ),
                )
            ),
            markerArguments = mapOf(
                "microServiceName" to LsiAnnotationValue.StringValue("node-service"),
            ),
            origin = origin,
        )
        val productAssociation = property(
            ownerId = REMOTE_NODE_TYPE,
            name = "product",
            type = LsiDeclaredType(REMOTE_PRODUCT_TYPE, nullability = remoteNullability),
            annotations = listOf(annotation(MANY_TO_ONE)),
            origin = origin,
        )
        val product = type(
            qualifiedName = REMOTE_PRODUCT_TYPE.requireTypeQualifiedName(),
            marker = ENTITY,
            memberIds = emptyList(),
            markerArguments = mapOf(
                "microServiceName" to LsiAnnotationValue.StringValue("product-service"),
            ),
            origin = origin,
        )
        return LsiWorkspace(
            sources = listOf(requireNotNull(origin.source)),
            declarations = listOf(base, baseParent, node, productAssociation, product),
        )
    }

    private fun inheritanceWorkspace(language: LsiLanguage): LsiWorkspace {
        val origin = sourceOrigin(language)
        val baseId = LsiSymbolId.type("demo.AccountBase")
        val rootId = LsiSymbolId.type("demo.Account")
        val childId = LsiSymbolId.type("demo.AdminAccount")
        val discriminatorProp = property(
            ownerId = baseId,
            name = "kind",
            type = LsiDeclaredType(STRING_TYPE),
            annotations = listOf(annotation(DISCRIMINATOR)),
            origin = origin,
        )
        val base = type(
            qualifiedName = "demo.AccountBase",
            marker = MAPPED_SUPERCLASS,
            memberIds = listOf(discriminatorProp.id),
            documentation = "账户公共字段。",
            origin = origin,
        )
        val root = declaration(
            qualifiedName = "demo.Account",
            kind = LsiTypeDeclarationKind.INTERFACE,
            documentation = "账户继承根。\n用于多态 DTO。",
            annotations = listOf(
                annotation(
                    ENTITY,
                    mapOf(
                        "instantiability" to LsiAnnotationValue.EnumValue(
                            ENTITY_INSTANTIABILITY,
                            "INSTANTIABLE",
                        )
                    ),
                ),
                annotation(
                    INHERITANCE,
                    mapOf(
                        "strategy" to LsiAnnotationValue.EnumValue(
                            INHERITANCE_TYPE,
                            "JOINED",
                        )
                    ),
                ),
                annotation(
                    DISCRIMINATOR_VALUE,
                    mapOf("value" to LsiAnnotationValue.StringValue("ACCOUNT")),
                ),
                annotation(
                    API_MODEL,
                    mapOf("name" to LsiAnnotationValue.StringValue("AccountModel")),
                ),
            ),
            superTypes = listOf(LsiDeclaredType(baseId)),
            origin = origin,
        )
        val child = declaration(
            qualifiedName = "demo.AdminAccount",
            kind = LsiTypeDeclarationKind.INTERFACE,
            documentation = "管理员账户。",
            annotations = listOf(
                annotation(
                    ENTITY,
                    mapOf(
                        "instantiability" to LsiAnnotationValue.EnumValue(
                            ENTITY_INSTANTIABILITY,
                            "AUTO",
                        )
                    ),
                ),
                annotation(
                    DISCRIMINATOR_VALUE,
                    mapOf("value" to LsiAnnotationValue.StringValue("ADMIN")),
                ),
            ),
            superTypes = listOf(LsiDeclaredType(rootId)),
            origin = origin,
        )
        val declarations = listOf(base, discriminatorProp, root, child)
        return LsiWorkspace(
            sources = listOf(requireNotNull(origin.source)),
            declarations = if (language == LsiLanguage.JAVA) declarations else declarations.reversed(),
        )
    }

    private fun defaultInheritanceWorkspace(): LsiWorkspace {
        val rootId = LsiSymbolId.type("demo.AbstractAccount")
        val childId = LsiSymbolId.type("demo.ConcreteAccount")
        val discriminatorProp = property(
            ownerId = rootId,
            name = "kind",
            type = LsiDeclaredType(STRING_TYPE),
            annotations = listOf(annotation(DISCRIMINATOR)),
        )
        return LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.AbstractAccount",
                    marker = ENTITY,
                    memberIds = listOf(discriminatorProp.id),
                    typeAnnotations = listOf(annotation(INHERITANCE)),
                ),
                discriminatorProp,
                type(
                    qualifiedName = "demo.ConcreteAccount",
                    marker = ENTITY,
                    memberIds = emptyList(),
                    superTypes = listOf(LsiDeclaredType(rootId)),
                ),
            ),
        )
    }

    private fun inheritanceActionWorkspace(
        strategy: String,
        joinedTableDissociateAction: String,
    ): LsiWorkspace {
        val rootId = LsiSymbolId.type("demo.ActionRoot")
        val discriminatorProp = property(
            ownerId = rootId,
            name = "kind",
            type = LsiDeclaredType(STRING_TYPE),
            annotations = listOf(annotation(DISCRIMINATOR)),
        )
        return LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.ActionRoot",
                    marker = ENTITY,
                    memberIds = listOf(discriminatorProp.id),
                    typeAnnotations = listOf(
                        annotation(
                            INHERITANCE,
                            mapOf(
                                "strategy" to LsiAnnotationValue.EnumValue(INHERITANCE_TYPE, strategy),
                                "joinedTableDissociateAction" to LsiAnnotationValue.EnumValue(
                                    JOINED_TABLE_DISSOCIATE_ACTION,
                                    joinedTableDissociateAction,
                                ),
                            ),
                        )
                    ),
                ),
                discriminatorProp,
            ),
        )
    }

    private fun discriminatorWorkspace(
        discriminatorType: LsiType,
        extraDeclarations: List<LsiClass> = emptyList(),
        additionalAnnotations: List<LsiAnnotation> = emptyList(),
    ): LsiWorkspace {
        val rootId = LsiSymbolId.type("demo.DiscriminatorRoot")
        val discriminatorProp = property(
            ownerId = rootId,
            name = "kind",
            type = discriminatorType,
            annotations = listOf(annotation(DISCRIMINATOR)) + additionalAnnotations,
        )
        return LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.DiscriminatorRoot",
                    marker = ENTITY,
                    memberIds = listOf(discriminatorProp.id),
                    typeAnnotations = listOf(annotation(INHERITANCE)),
                ),
                discriminatorProp,
            ) + extraDeclarations,
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
            property(
                primaryTypeId,
                "rootZ",
                LsiDeclaredType(STRING_TYPE),
                annotations = listOf(annotation(DISCRIMINATOR)),
                origin = origin,
            ),
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
                typeAnnotations = listOf(annotation(INHERITANCE)),
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
        baseType: LsiType = LsiDeclaredType(STRING_TYPE),
        childType: LsiType = baseType,
        baseAnnotations: List<LsiAnnotation> = emptyList(),
        childAnnotations: List<LsiAnnotation> = emptyList(),
        baseModality: LsiModality = LsiModality.ABSTRACT,
        childModality: LsiModality = LsiModality.ABSTRACT,
        baseTypeAnnotations: List<LsiAnnotation> = emptyList(),
        extraTypes: List<LsiClass> = emptyList(),
    ): LsiWorkspace {
        val baseId = LsiSymbolId.type("demo.Base")
        val childId = LsiSymbolId.type("demo.Child")
        val basePropertyId = LsiSymbolId.property(baseId, "value")
        val childPropertyId = LsiSymbolId.property(childId, "value")
        return LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.Base",
                    marker = baseMarker,
                    memberIds = listOf(basePropertyId),
                    typeAnnotations = baseTypeAnnotations,
                ),
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
        superTypes: List<LsiType> = emptyList(),
        documentation: String? = null,
        markerArguments: Map<String, LsiAnnotationValue> = emptyMap(),
        typeAnnotations: List<LsiAnnotation> = emptyList(),
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiClass {
        return declaration(
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.INTERFACE,
            documentation = documentation,
            annotations = listOf(annotation(marker, markerArguments)) + typeAnnotations,
            memberIds = memberIds,
            typeParameters = typeParameters,
            superTypes = superTypes,
            origin = origin,
        )
    }

    private fun declaration(
        qualifiedName: String,
        kind: LsiTypeDeclarationKind,
        documentation: String? = null,
        annotations: List<LsiAnnotation> = emptyList(),
        memberIds: List<LsiSymbolId> = emptyList(),
        typeParameters: List<LsiTypeParameter> = emptyList(),
        superTypes: List<LsiType> = emptyList(),
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiClass {
        return LsiClass(
            id = LsiSymbolId.type(qualifiedName),
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = kind,
            typeParameters = typeParameters,
            superTypes = superTypes,
            memberIds = memberIds,
            documentation = documentation,
            annotations = annotations,
            origin = origin,
        )
    }

    private fun property(
        ownerId: LsiSymbolId,
        name: String,
        type: LsiType,
        annotations: List<LsiAnnotation> = emptyList(),
        overrides: List<LsiOverride> = emptyList(),
        modality: LsiModality = LsiModality.ABSTRACT,
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
        documentation: String? = null,
    ): LsiProperty {
        return LsiProperty(
            id = LsiSymbolId.property(ownerId, name),
            name = name,
            ownerId = ownerId,
            type = type,
            documentation = documentation,
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

    private fun ImmutableProp.annotationString(
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
        private val INHERITANCE = LsiSymbolId.type("org.babyfish.jimmer.sql.Inheritance")
        private val DISCRIMINATOR_VALUE = LsiSymbolId.type("org.babyfish.jimmer.sql.DiscriminatorValue")
        private val DISCRIMINATOR = LsiSymbolId.type("org.babyfish.jimmer.sql.Discriminator")
        private val ENTITY_INSTANTIABILITY =
            LsiSymbolId.type("org.babyfish.jimmer.sql.EntityInstantiability")
        private val INHERITANCE_TYPE = LsiSymbolId.type("org.babyfish.jimmer.sql.InheritanceType")
        private val JOINED_TABLE_DISSOCIATE_ACTION =
            LsiSymbolId.type("org.babyfish.jimmer.sql.JoinedTableDissociateAction")
        private val API_MODEL = LsiSymbolId.type("demo.ApiModel")
        private val ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Id")
        private val VERSION = LsiSymbolId.type("org.babyfish.jimmer.sql.Version")
        private val LOGICAL_DELETED = LsiSymbolId.type("org.babyfish.jimmer.sql.LogicalDeleted")
        private val API_IGNORE = LsiSymbolId.type("org.babyfish.jimmer.client.ApiIgnore")
        private val T_NULLABLE = LsiSymbolId.type("org.babyfish.jimmer.client.TNullable")
        private val ONE_TO_ONE = LsiSymbolId.type("org.babyfish.jimmer.sql.OneToOne")
        private val MANY_TO_ONE = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToOne")
        private val ONE_TO_MANY = LsiSymbolId.type("org.babyfish.jimmer.sql.OneToMany")
        private val MANY_TO_MANY = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToMany")
        private val JOIN_COLUMN = LsiSymbolId.type("org.babyfish.jimmer.sql.JoinColumn")
        private val JOIN_TABLE = LsiSymbolId.type("org.babyfish.jimmer.sql.JoinTable")
        private val JOIN_SQL = LsiSymbolId.type("org.babyfish.jimmer.sql.JoinSql")
        private val FORMULA = LsiSymbolId.type("org.babyfish.jimmer.Formula")
        private val TRANSIENT = LsiSymbolId.type("org.babyfish.jimmer.sql.Transient")
        private val ID_VIEW = LsiSymbolId.type("org.babyfish.jimmer.sql.IdView")
        private val MANY_TO_MANY_VIEW = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToManyView")
        private val MAPS_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.MapsId")
        private val DEFAULT = LsiSymbolId.type("org.babyfish.jimmer.sql.Default")
        private val DATABASE_DEFAULT = LsiSymbolId.type("org.babyfish.jimmer.sql.DatabaseDefault")
        private val KEY = LsiSymbolId.type("org.babyfish.jimmer.sql.Key")
        private val KEYS = LsiSymbolId.type("org.babyfish.jimmer.sql.Keys")
        private val COLUMN = LsiSymbolId.type("org.babyfish.jimmer.sql.Column")
        private val JAVA_OVERRIDE = LsiSymbolId.type("java.lang.Override")
        private val KOTLIN_SUPPRESS = LsiSymbolId.type("kotlin.Suppress")
        private val SCALAR = LsiSymbolId.type("org.babyfish.jimmer.Scalar")

        private val COLLECTION_TYPE = LsiSymbolId.type("java.util.Collection")
        private val SET_TYPE = LsiSymbolId.type("java.util.Set")
        private val KOTLIN_MUTABLE_LIST_TYPE = LsiSymbolId.type("kotlin.collections.MutableList")
        private val CUSTOM_COLLECTION_TYPE = LsiSymbolId.type("demo.CustomCollection")

        private val REMOTE_BASE_TYPE = LsiSymbolId.type("demo.RemoteBase")
        private val REMOTE_NODE_TYPE = LsiSymbolId.type("demo.RemoteNode")
        private val REMOTE_PRODUCT_TYPE = LsiSymbolId.type("demo.RemoteProduct")

        private val VALID_CODE = LsiSymbolId.type("demo.ValidCode")
        private val CODE_FORMAT = LsiSymbolId.type("demo.CodeFormat")
        private val CODE_VALIDATOR = LsiSymbolId.type("demo.CodeValidator")
        private val CODE_CONVERTER = LsiSymbolId.type("demo.CodeConverter")
        private val EXPLICIT_CODE_CONVERTER = LsiSymbolId.type("demo.ExplicitCodeConverter")
        private val INVALID_CODE_CONVERTER = LsiSymbolId.type("demo.InvalidCodeConverter")
        private val JAKARTA_CONSTRAINT = LsiSymbolId.type("jakarta.validation.Constraint")
        private val JSON_CONVERTER = LsiSymbolId.type("org.babyfish.jimmer.jackson.JsonConverter")
        private val JSON_FORMAT = LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonFormat")
        private val CONVERTER = LsiSymbolId.type("org.babyfish.jimmer.jackson.Converter")

        private val SYNTHETIC_ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
    }
}
