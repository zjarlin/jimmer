package org.babyfish.jimmer.compiler.immutable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
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
    fun `apt and ksp equivalent workspaces have identical immutable snapshots`() {
        val aptSchema = JimmerImmutablePrecompiler().compile(overrideWorkspace(LsiLanguage.JAVA))
        val kspSchema = JimmerImmutablePrecompiler().compile(overrideWorkspace(LsiLanguage.KOTLIN))

        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())
        assertEquals(64, aptSchema.fingerprint().length)
    }

    @Test
    fun `apt and ksp preserve microservice remote and generic recursive semantics`() {
        val aptSchema = JimmerImmutablePrecompiler().compile(microServiceWorkspace(LsiLanguage.JAVA))
        val kspSchema = JimmerImmutablePrecompiler().compile(microServiceWorkspace(LsiLanguage.KOTLIN))
        val base = aptSchema.types.single { type -> type.id == REMOTE_BASE_TYPE }
        val node = aptSchema.types.single { type -> type.id == REMOTE_NODE_TYPE }
        val product = aptSchema.types.single { type -> type.id == REMOTE_PRODUCT_TYPE }
        val baseParent = base.props.single { prop -> prop.name == "parent" }
        val props = node.props.associateBy(JimmerImmutableProp::name)
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

        val schema = JimmerImmutablePrecompiler().compile(workspace, setOf(ownerId))

        assertEquals(listOf(ownerId, targetId), schema.types.map(JimmerImmutableType::id))
        assertEquals(targetId, schema.types.first().props.single().targetTypeId)
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

        val schema = JimmerImmutablePrecompiler().compile(workspace, setOf(childId))
        val child = schema.types.single { type -> type.id == childId }
        val effectiveTarget = child.props.single { prop -> prop.name == "target" }

        assertEquals(
            setOf(baseId, childId, targetId),
            schema.types.mapTo(sortedSetOf(), JimmerImmutableType::id),
        )
        assertEquals(targetId, effectiveTarget.targetTypeId)
        assertFalse(effectiveTarget.genericTarget)
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

        val exception = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(workspace)
        }

        assertTrue(exception.message.orEmpty().contains("cannot specify microServiceName"))
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

        val exception = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(workspace)
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

        val exception = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(workspace)
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
        val unnamedException = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(unnamedWorkspace)
        }
        assertTrue(unnamedException.message.orEmpty().contains("requires non-empty micro service names"))

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
        val joinSqlException = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(joinSqlWorkspace)
        }
        assertTrue(joinSqlException.message.orEmpty().contains("cannot be decorated by @${JOIN_SQL.value}"))
    }

    @Test
    fun `apt and ksp inheritance fixtures preserve equivalent type metadata`() {
        val aptSchema = JimmerImmutablePrecompiler().compile(inheritanceWorkspace(LsiLanguage.JAVA))
        val kspSchema = JimmerImmutablePrecompiler().compile(inheritanceWorkspace(LsiLanguage.KOTLIN))
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
        assertEquals(JimmerInheritanceStrategy.JOINED, aptRoot.inheritanceStrategy)
        assertEquals(JimmerJoinedTableDissociateAction.DELETE, aptRoot.joinedTableDissociateAction)
        assertEquals("ACCOUNT", aptRoot.discriminatorValue)
        assertEquals(LsiSymbolId.property(rootId, "kind"), aptRoot.discriminatorPropId)
        assertEquals(JimmerImmutablePrimaryMapping.DISCRIMINATOR, aptRoot.props.single().primaryMapping)

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
    fun `inheritance defaults preserve legacy instantiability and discriminator values`() {
        val schema = JimmerImmutablePrecompiler().compile(defaultInheritanceWorkspace())
        val rootId = LsiSymbolId.type("demo.AbstractAccount")
        val childId = LsiSymbolId.type("demo.ConcreteAccount")
        val root = schema.types.single { type -> type.id == rootId }
        val child = schema.types.single { type -> type.id == childId }

        assertFalse(root.instantiable)
        assertEquals(rootId, root.inheritanceRootTypeId)
        assertEquals(JimmerInheritanceStrategy.SINGLE_TABLE, root.inheritanceStrategy)
        assertEquals(JimmerJoinedTableDissociateAction.DELETE, root.joinedTableDissociateAction)
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
        val joinedSchema = JimmerImmutablePrecompiler().compile(
            inheritanceActionWorkspace(
                strategy = "JOINED",
                joinedTableDissociateAction = "LAX",
            )
        )
        val root = joinedSchema.types.single()

        assertEquals(JimmerInheritanceStrategy.JOINED, root.inheritanceStrategy)
        assertEquals(JimmerJoinedTableDissociateAction.LAX, root.joinedTableDissociateAction)
        assertTrue(joinedSchema.normalizedSnapshot().contains("|JOINED|LAX|"))

        val exception = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(
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

        val exception = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(workspace)
        }

        assertEquals(extraDiscriminator.id, exception.declarationId)
        assertTrue(exception.message.orEmpty().contains("except from its inheritance root"))

        val declaredDiscriminator = property(
            ownerId = childId,
            name = "kind",
            type = LsiDeclaredType(STRING_TYPE),
            overrides = listOf(LsiOverride(rootDiscriminator.id)),
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
        val declaredException = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(declaredWorkspace)
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
        val missingException = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(missingWorkspace)
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
        val multipleException = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(multipleWorkspace)
        }
        assertTrue(multipleException.message.orEmpty().contains("multiple discriminator properties"))
    }

    @Test
    fun `discriminator must be scalar string or enum property`() {
        val enumId = LsiSymbolId.type("demo.AccountKind")
        val enumSchema = JimmerImmutablePrecompiler().compile(
            discriminatorWorkspace(
                discriminatorType = LsiDeclaredType(enumId),
                extraDeclarations = listOf(declaration("demo.AccountKind", LsiTypeDeclarationKind.ENUM)),
            )
        )
        assertEquals(
            JimmerImmutablePrimaryMapping.DISCRIMINATOR,
            enumSchema.types.single().props.single().primaryMapping,
        )

        val invalidTypes = listOf<LsiTypeRef>(
            LsiPrimitiveType(LsiPrimitiveKind.INT),
            listType(STRING_TYPE),
        )
        invalidTypes.forEach { invalidType ->
            val exception = assertFailsWith<JimmerImmutablePrecompileException> {
                JimmerImmutablePrecompiler().compile(discriminatorWorkspace(invalidType))
            }
            assertTrue(exception.message.orEmpty().contains("must be a scalar string or enum property"))
        }

        val targetId = LsiSymbolId.type("demo.Target")
        val associationException = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(
                discriminatorWorkspace(
                    discriminatorType = LsiDeclaredType(targetId),
                    extraDeclarations = listOf(type("demo.Target", ENTITY, emptyList())),
                )
            )
        }
        assertTrue(associationException.message.orEmpty().contains("must be a scalar string or enum property"))

        val formulaException = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(
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
            property(bookId, "payload", LsiDeclaredType(payloadId)),
        )
        val workspace = LsiWorkspace(
            declarations = listOf(
                type("demo.Author", ENTITY, listOf(authorIdProp.id)),
                type("demo.BookAuthor", ENTITY, listOf(bookAuthorAuthorProp.id)),
                type("demo.Address", EMBEDDABLE, emptyList()),
                type("demo.Payload", IMMUTABLE, emptyList()),
                type("demo.Book", ENTITY, properties.map(LsiProperty::id)),
            ) + properties + authorIdProp + bookAuthorAuthorProp,
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
        assertEquals(
            JimmerImmutableView.Id(
                basePropId = LsiSymbolId.property(bookId, "author"),
                targetIdPropId = LsiSymbolId.property(authorId, "id"),
            ),
            props.getValue("authorId").view,
        )
        assertEquals(JimmerAssociationKind.ONE_TO_MANY, props.getValue("authorLinks").associationKind)
        assertTrue(props.getValue("authorLinks").list)
        assertTrue(props.getValue("authorLinks").association)
        assertEquals(JimmerImmutablePrimaryMapping.VIEW, props.getValue("authorView").primaryMapping)
        assertEquals(
            JimmerImmutableView.ManyToMany(
                basePropId = LsiSymbolId.property(bookId, "authorLinks"),
                deeperPropId = LsiSymbolId.property(bookAuthorId, "author"),
            ),
            props.getValue("authorView").view,
        )
        assertTrue(props.getValue("description").nullable)
        assertEquals(JimmerImmutablePrimaryMapping.SCALAR, props.getValue("description").primaryMapping)
        assertFalse(props.getValue("address").association)
        assertTrue(props.getValue("address").embedded)
        assertFalse(props.getValue("payload").association)
        assertFalse(props.getValue("payload").embedded)
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
        val schema = JimmerImmutablePrecompiler().compile(
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
            .associateBy(JimmerImmutableProp::name)

        assertTrue(props.getValue("storeId").nullable)
        assertEquals(
            JimmerImmutableView.Id(storeProp.id, storeIdProp.id),
            props.getValue("storeId").view,
        )
        assertEquals(
            JimmerImmutableView.Id(authorsProp.id, authorIdProp.id),
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
        val schema = JimmerImmutablePrecompiler().compile(
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
            JimmerImmutableView.Id(targetProp.id, null),
            baseView.view,
        )
        assertEquals(
            JimmerImmutableView.Id(
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
        val schema = JimmerImmutablePrecompiler().compile(
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
            .props.associateBy(JimmerImmutableProp::name)
        val view = props.getValue("authors")

        assertEquals(
            JimmerImmutableView.ManyToMany(linksProp.id, deeperProp.id),
            view.view,
        )
        assertEquals(
            listOf(linksProp.id, deeperProp.id),
            schema.viewDependencyPathByPropId[view.id],
        )
        assertEquals(
            JimmerImmutableView.Id(view.id, authorIdProp.id),
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
            baseType: LsiTypeRef = LsiDeclaredType(storeId),
            baseAnnotations: List<LsiAnnotation> = listOf(annotation(MANY_TO_ONE)),
            viewType: LsiTypeRef = LsiPrimitiveType(LsiPrimitiveKind.LONG),
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
            return assertFailsWith<JimmerImmutablePrecompileException> {
                JimmerImmutablePrecompiler().compile(LsiWorkspace(declarations = declarations))
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
            val exception = assertFailsWith<JimmerImmutablePrecompileException> {
                JimmerImmutablePrecompiler().compile(
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
        val conflict = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(
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

        JimmerImmutablePrecompiler().compile(
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
            viewType: LsiTypeRef = listType(authorId),
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
            return assertFailsWith<JimmerImmutablePrecompileException> {
                JimmerImmutablePrecompiler().compile(LsiWorkspace(declarations = declarations))
            }.message.orEmpty()
        }

        assertTrue("list of entities" in failure(viewType = LsiDeclaredType(authorId)))
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
        val schema = JimmerImmutablePrecompiler().compile(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.NullityModel", IMMUTABLE, listOf(nullableProp.id, nonNullProp.id)),
                    nullableProp,
                    nonNullProp,
                )
            )
        )
        val props = schema.types.single().props.associateBy(JimmerImmutableProp::name)
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
        val failure = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(
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
        val aptSchema = JimmerImmutablePrecompiler().compile(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.NullabilityModel", IMMUTABLE, listOf(propertyId)),
                    property(
                        ownerId = entityId,
                        name = "rating",
                        type = LsiPrimitiveType(
                            LsiPrimitiveKind.INT,
                            nullability = LsiNullability.PLATFORM,
                        ),
                        annotations = listOf(
                            annotation(LsiSymbolId.type("org.jetbrains.annotations.Nullable")),
                        ),
                    ),
                ),
            ),
        )
        val kspSchema = JimmerImmutablePrecompiler().compile(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.NullabilityModel", IMMUTABLE, listOf(propertyId)),
                    property(
                        ownerId = entityId,
                        name = "rating",
                        type = LsiPrimitiveType(
                            LsiPrimitiveKind.INT,
                            nullability = LsiNullability.NULLABLE,
                        ),
                    ),
                ),
            ),
        )

        val aptProp = aptSchema.types.single().props.single()
        val kspProp = kspSchema.types.single().props.single()
        assertTrue(aptProp.nullable)
        assertEquals(LsiNullability.NULLABLE, aptProp.type.nullability)
        assertEquals("primitive:int?", aptProp.type.normalizedTypeSignature())
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
    fun `inherits and lifts target id converter for list id view`() {
        val storeId = LsiSymbolId.type("demo.Store")
        val bookId = LsiSymbolId.type("demo.Book")
        val idProp = property(
            storeId,
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
        val schema = JimmerImmutablePrecompiler().compile(
            LsiWorkspace(
                declarations = listOf(
                    type("demo.Store", ENTITY, listOf(idProp.id)),
                    type("demo.Book", ENTITY, listOf(storesProp.id, storeIdsProp.id)),
                    idProp,
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

        val schema = JimmerImmutablePrecompiler().compile(workspace(idViewProp(EXPLICIT_CODE_CONVERTER)))
        val converter = requireNotNull(
            schema.types.single { type -> type.id == bookId }
                .props.single { prop -> prop.name == "storeId" }
                .converter
        )
        assertEquals(EXPLICIT_CODE_CONVERTER, converter.converterTypeId)
        assertEquals(LsiPrimitiveKind.BOOLEAN, assertIs<LsiPrimitiveType>(converter.targetType).kind)

        val failure = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(workspace(idViewProp(INVALID_CODE_CONVERTER)))
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
        val associationFailure = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(
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
        val formatFailure = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(workspace(formattedViewProp))
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
        val mappedException = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(mappedChild)
        }
        assertTrue(mappedException.message.orEmpty().contains("mapped superclass of an entity"))

        val entityParent = overrideCategoryWorkspace(
            baseMarker = ENTITY,
            childMarker = ENTITY,
            baseAnnotations = listOf(
                default("0", LsiLanguage.KOTLIN),
                annotation(DISCRIMINATOR),
            ),
            childAnnotations = listOf(default("1", LsiLanguage.KOTLIN)),
            baseTypeAnnotations = listOf(annotation(INHERITANCE)),
        )
        val entityException = assertFailsWith<JimmerImmutablePrecompileException> {
            JimmerImmutablePrecompiler().compile(entityParent)
        }
        assertTrue(entityException.message.orEmpty().contains("mapped superclass of an entity"))
    }

    @Test
    fun `ordinary property override does not require mapped superclass annotation override rules`() {
        val workspace = overrideCategoryWorkspace(
            baseMarker = MAPPED_SUPERCLASS,
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
            type = LsiDeclaredType(REMOTE_PRODUCT_TYPE, nullability = nullability),
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
        discriminatorType: LsiTypeRef,
        extraDeclarations: List<LsiTypeDeclaration> = emptyList(),
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
        baseType: LsiTypeRef = LsiDeclaredType(STRING_TYPE),
        childType: LsiTypeRef = baseType,
        baseAnnotations: List<LsiAnnotation> = emptyList(),
        childAnnotations: List<LsiAnnotation> = emptyList(),
        baseModality: LsiModality = LsiModality.ABSTRACT,
        childModality: LsiModality = LsiModality.ABSTRACT,
        baseTypeAnnotations: List<LsiAnnotation> = emptyList(),
        extraTypes: List<LsiTypeDeclaration> = emptyList(),
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
        superTypes: List<LsiTypeRef> = emptyList(),
        documentation: String? = null,
        markerArguments: Map<String, LsiAnnotationValue> = emptyMap(),
        typeAnnotations: List<LsiAnnotation> = emptyList(),
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiTypeDeclaration {
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
            documentation = documentation,
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
        private val ONE_TO_ONE = LsiSymbolId.type("org.babyfish.jimmer.sql.OneToOne")
        private val MANY_TO_ONE = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToOne")
        private val ONE_TO_MANY = LsiSymbolId.type("org.babyfish.jimmer.sql.OneToMany")
        private val MANY_TO_MANY = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToMany")
        private val JOIN_SQL = LsiSymbolId.type("org.babyfish.jimmer.sql.JoinSql")
        private val FORMULA = LsiSymbolId.type("org.babyfish.jimmer.Formula")
        private val TRANSIENT = LsiSymbolId.type("org.babyfish.jimmer.sql.Transient")
        private val ID_VIEW = LsiSymbolId.type("org.babyfish.jimmer.sql.IdView")
        private val MANY_TO_MANY_VIEW = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToManyView")
        private val MAPS_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.MapsId")
        private val DEFAULT = LsiSymbolId.type("org.babyfish.jimmer.sql.Default")
        private val COLUMN = LsiSymbolId.type("org.babyfish.jimmer.sql.Column")
        private val JAVA_OVERRIDE = LsiSymbolId.type("java.lang.Override")

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
