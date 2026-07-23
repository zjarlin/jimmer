package site.addzero.lsi.jimmer.transactional

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeHierarchyEntry
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace

class TransactionalWorkspaceExtensionsTest {

    @Test
    fun `resolves constructors sql client and effective methods`() {
        val schema = javaWorkspace().toTransactionalSchema()

        val type = schema.types.single()
        assertEquals("demo", type.packageName)
        assertEquals("BookServiceTx", type.generatedSimpleName)
        assertEquals("sqlClient", type.sqlClient.name)
        assertEquals(TransactionalPlatform.JAVA, type.sqlClient.platform)
        assertEquals(1, type.constructors.size)
        assertEquals(listOf("find", "save"), type.methods.map(TransactionalMethod::name))
        assertEquals("REQUIRED", type.methods.single { method -> method.name == "find" }.propagation)
        val save = type.methods.single { method -> method.name == "save" }
        assertEquals("REQUIRES_NEW", save.propagation)
        assertTrue(!save.classLevel)
        assertEquals(64, schema.fingerprint().length)
    }

    @Test
    fun `java field and kotlin property inputs have equal semantic snapshots`() {
        val java = javaWorkspace().toTransactionalSchema()
        val kotlin = kotlinWorkspace().toTransactionalSchema()

        assertEquals(java.normalizedSnapshot(), kotlin.normalizedSnapshot())
        assertNotEquals(java.fingerprint(), kotlin.fingerprint())
    }

    @Test
    fun `renderer fingerprint covers documentation and constructor kind`() {
        val schema = javaWorkspace().toTransactionalSchema()
        val type = schema.types.single()
        val changedDocumentation = schema.copy(
            types = listOf(
                type.copy(
                    methods = type.methods.map { method -> method.copy(documentation = "changed") }
                )
            )
        )
        val changedConstructorKind = schema.copy(
            types = listOf(
                type.copy(
                    constructors = type.constructors.map { constructor -> constructor.copy(primary = true) }
                )
            )
        )

        assertNotEquals(schema.fingerprint(), changedDocumentation.fingerprint())
        assertNotEquals(schema.fingerprint(), changedConstructorKind.fingerprint())
    }

    @Test
    fun `snapshot distinguishes primitive and boxed method types`() {
        val schema = javaWorkspace().toTransactionalSchema()
        val type = schema.types.single()
        val rawSchema = schema.copy(
            types = listOf(
                type.copy(
                    methods = type.methods.map { method ->
                        method.copy(returnType = LsiPrimitiveType(LsiPrimitiveKind.INT))
                    }
                )
            ),
        )
        val boxedSchema = rawSchema.copy(
            types = rawSchema.types.map { transactionalType ->
                transactionalType.copy(
                    methods = transactionalType.methods.map { method ->
                        method.copy(returnType = LsiPrimitiveType(LsiPrimitiveKind.INT, boxed = true))
                    }
                )
            },
        )

        assertNotEquals(rawSchema.normalizedSnapshot(), boxedSchema.normalizedSnapshot())
        assertNotEquals(rawSchema.fingerprint(), boxedSchema.fingerprint())
    }

    @Test
    fun `snapshot distinguishes generic argument variance`() {
        val schema = javaWorkspace().toTransactionalSchema()
        val type = schema.types.single()
        val method = type.methods.first()
        val stringType = LsiDeclaredType(STRING_TYPE)
        val invariantType = LsiDeclaredType(
            declarationId = LsiSymbolId.type("java.util.List"),
            arguments = listOf(LsiTypeArgument.invariant(stringType)),
        )
        val outputType = invariantType.copy(
            arguments = listOf(LsiTypeArgument.output(stringType)),
        )
        fun withReturnType(returnType: LsiDeclaredType): TransactionalSchema {
            return schema.copy(
                types = listOf(
                    type.copy(
                        methods = listOf(method.copy(returnType = returnType)) + type.methods.drop(1),
                    )
                )
            )
        }

        assertNotEquals(
            withReturnType(invariantType).normalizedSnapshot(),
            withReturnType(outputType).normalizedSnapshot(),
        )
        assertNotEquals(
            withReturnType(invariantType).fingerprint(),
            withReturnType(outputType).fingerprint(),
        )
    }

    @Test
    fun `kotlin parameter annotation target projection is frozen and fingerprinted`() {
        val parameterSchema = kotlinWorkspaceWithParameterAnnotationTarget("VALUE_PARAMETER")
            .toTransactionalSchema()
        val getterSchema = kotlinWorkspaceWithParameterAnnotationTarget("PROPERTY_GETTER")
            .toTransactionalSchema()
        val parameterAnnotations = parameterSchema.types.single()
            .constructors.single()
            .parameters.single()
            .annotations
        val getterAnnotations = getterSchema.types.single()
            .constructors.single()
            .parameters.single()
            .annotations

        assertEquals(listOf(LsiAnnotation(PARAMETER_MARKER)), parameterAnnotations)
        assertTrue(getterAnnotations.isEmpty())
        assertEquals(
            setOf(PARAMETER_MARKER),
            parameterSchema.types.single().constructors.single().parameters.single().annotationProjectionTypeIds,
        )
        assertEquals(
            setOf(PARAMETER_MARKER),
            getterSchema.types.single().constructors.single().parameters.single().annotationProjectionTypeIds,
        )
        assertEquals(parameterSchema.normalizedSnapshot(), getterSchema.normalizedSnapshot())
        assertNotEquals(parameterSchema.fingerprint(), getterSchema.fingerprint())
    }

    @Test
    fun `rejects invalid type sql client and methods`() {
        assertRejected(
            workspace(modality = LsiModality.FINAL),
            "must be open",
        )
        assertRejected(
            workspace(typeParameters = listOf(LsiTypeParameter(LsiSymbolId.typeParameter(TYPE_ID, "T"), "T"))),
            "type parameters",
        )
        assertRejected(
            workspace(includeSqlClient = false),
            "exactly one non-static",
        )
        assertRejected(
            workspace(sqlClientVisibility = LsiVisibility.PRIVATE),
            "cannot be private",
        )
        assertRejected(
            workspace(methodModality = LsiModality.FINAL),
            "must be open",
        )
        assertRejected(
            workspace(thrownType = LsiDeclaredType(LsiSymbolId.type("java.io.IOException"))),
            "only throw RuntimeException",
        )
        assertRejected(
            workspace(receiverType = LsiDeclaredType(STRING_TYPE)),
            "extension function",
        )
        assertRejected(
            workspace(suspending = true),
            "cannot be suspend",
        )
    }

    @Test
    fun `accepts runtime exception subtype declared in workspace`() {
        val businessExceptionId = LsiSymbolId.type("demo.BusinessException")
        val businessException = type(
            id = businessExceptionId,
            qualifiedName = "demo.BusinessException",
            superTypes = listOf(LsiDeclaredType(RUNTIME_EXCEPTION)),
        )
        val schema = workspace(
            thrownType = LsiDeclaredType(businessExceptionId),
            extraDeclarations = listOf(businessException),
        ).toTransactionalSchema()

        val find = schema.types.single().methods.single { method -> method.name == "find" }
        assertEquals(businessExceptionId, (find.thrownTypes.single() as LsiDeclaredType).declarationId)
    }

    @Test
    fun `accepts external runtime exception subtype from hierarchy`() {
        val completionExceptionId = LsiSymbolId.type("java.util.concurrent.CompletionException")
        val workspace = workspace(thrownType = LsiDeclaredType(completionExceptionId))
        val hierarchyEntry = LsiTypeHierarchyEntry(
            id = completionExceptionId,
            qualifiedName = "java.util.concurrent.CompletionException",
            kind = LsiTypeDeclarationKind.CLASS,
            directSuperTypes = listOf(LsiDeclaredType(RUNTIME_EXCEPTION)),
        )

        val schema = LsiWorkspace(
            sources = workspace.sources,
            declarations = workspace.declarations,
            typeHierarchy = workspace.typeHierarchy + hierarchyEntry,
        ).toTransactionalSchema()

        val find = schema.types.single().methods.single { method -> method.name == "find" }
        assertEquals(completionExceptionId, (find.thrownTypes.single() as LsiDeclaredType).declarationId)
    }

    @Test
    fun `rejects tx on kotlin property`() {
        val workspace = kotlinWorkspace()
        val service = workspace[TYPE_ID] as LsiTypeDeclaration
        val origin = service.origin
        val property = LsiProperty(
            id = LsiSymbolId.property(TYPE_ID, "version"),
            name = "version",
            ownerId = TYPE_ID,
            type = LsiPrimitiveType(LsiPrimitiveKind.INT),
            modality = LsiModality.OPEN,
            annotations = listOf(tx("REQUIRED")),
            origin = origin,
        )
        val declarations = workspace.declarations
            .filterNot { declaration -> declaration.id == TYPE_ID } +
            service.copy(memberIds = service.memberIds + property.id) +
            property

        assertRejected(
            LsiWorkspace(workspace.sources, declarations),
            "Only methods",
        )
    }

    private fun assertRejected(workspace: LsiWorkspace, message: String) {
        val exception = assertFailsWith<TransactionalValidationException> {
            workspace.toTransactionalSchema()
        }
        assertTrue(exception.message.orEmpty().contains(message), exception.message)
    }

    private fun javaWorkspace(): LsiWorkspace = workspace(language = LsiLanguage.JAVA)

    private fun kotlinWorkspace(): LsiWorkspace = workspace(language = LsiLanguage.KOTLIN)

    private fun kotlinWorkspaceWithParameterAnnotationTarget(target: String): LsiWorkspace {
        val workspace = kotlinWorkspace()
        val constructor = workspace.declarations.filterIsInstance<LsiConstructor>().single()
        val parameter = constructor.parameters.single()
        val marker = annotationDeclaration(target)
        return LsiWorkspace(
            sources = workspace.sources + listOfNotNull(marker.origin.source),
            declarations = workspace.declarations.map { declaration ->
                if (declaration.id == constructor.id) {
                    constructor.copy(
                        parameters = listOf(
                            parameter.copy(
                                annotations = listOf(
                                    LsiAnnotation(
                                        type = marker.id,
                                        useSiteTarget = LsiAnnotationUseSiteTarget.ALL,
                                    )
                                )
                            )
                        )
                    )
                } else {
                    declaration
                }
            } + marker,
            typeHierarchy = workspace.typeHierarchy,
            annotationScopes = workspace.annotationScopes,
        )
    }

    private fun workspace(
        language: LsiLanguage = LsiLanguage.JAVA,
        modality: LsiModality = LsiModality.OPEN,
        typeParameters: List<LsiTypeParameter> = emptyList(),
        includeSqlClient: Boolean = true,
        sqlClientVisibility: LsiVisibility = LsiVisibility.PROTECTED,
        methodModality: LsiModality = LsiModality.OPEN,
        thrownType: LsiDeclaredType? = null,
        receiverType: LsiDeclaredType? = null,
        suspending: Boolean = false,
        extraDeclarations: List<LsiTypeDeclaration> = emptyList(),
    ): LsiWorkspace {
        val source = LsiSource.of(
            "demo/BookService.${if (language == LsiLanguage.JAVA) "java" else "kt"}",
            language,
        )
        val origin = LsiOrigin(LsiOriginKind.SOURCE, source)
        val sqlClient = if (!includeSqlClient) {
            null
        } else if (language == LsiLanguage.JAVA) {
            LsiField(
                id = LsiSymbolId.field(TYPE_ID, "sqlClient"),
                name = "sqlClient",
                ownerId = TYPE_ID,
                type = LsiDeclaredType(J_SQL_CLIENT),
                mutable = false,
                visibility = sqlClientVisibility,
                origin = origin,
            )
        } else {
            LsiProperty(
                id = LsiSymbolId.property(TYPE_ID, "sqlClient"),
                name = "sqlClient",
                ownerId = TYPE_ID,
                type = LsiDeclaredType(K_SQL_CLIENT),
                modality = LsiModality.OPEN,
                visibility = sqlClientVisibility,
                origin = origin,
            )
        }
        val constructorId = LsiSymbolId.constructor(TYPE_ID, listOf("type:java.lang.String"))
        val constructorParameter = LsiParameter(
            id = LsiSymbolId.parameter(constructorId, 0, "name"),
            name = "name",
            callableId = constructorId,
            index = 0,
            type = LsiDeclaredType(STRING_TYPE),
            origin = origin,
        )
        val constructor = LsiConstructor(
            id = constructorId,
            ownerId = TYPE_ID,
            parameters = listOf(constructorParameter),
            visibility = LsiVisibility.PUBLIC,
            origin = origin,
        )
        val find = function(
            name = "find",
            modality = methodModality,
            annotations = emptyList(),
            thrownType = thrownType,
            receiverType = receiverType,
            suspending = suspending,
            origin = origin,
        )
        val save = function(
            name = "save",
            modality = LsiModality.OPEN,
            annotations = listOf(tx("REQUIRES_NEW")),
            origin = origin,
        )
        val members = listOfNotNull(sqlClient, constructor, find, save)
        val service = type(
            id = TYPE_ID,
            qualifiedName = "demo.BookService",
            modality = modality,
            typeParameters = typeParameters,
            memberIds = members.map { declaration -> declaration.id },
            annotations = listOf(tx("REQUIRED")),
            origin = origin,
        )
        return LsiWorkspace(
            sources = listOf(source),
            declarations = listOf(service) + members + extraDeclarations,
        )
    }

    private fun function(
        name: String,
        modality: LsiModality,
        annotations: List<LsiAnnotation>,
        thrownType: LsiDeclaredType? = null,
        receiverType: LsiDeclaredType? = null,
        suspending: Boolean = false,
        origin: LsiOrigin,
    ): LsiFunction {
        return LsiFunction(
            id = LsiSymbolId.function(TYPE_ID, name),
            name = name,
            ownerId = TYPE_ID,
            returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
            receiverType = receiverType,
            suspending = suspending,
            thrownTypes = listOfNotNull(thrownType),
            modality = modality,
            annotations = annotations,
            origin = origin,
        )
    }

    private fun type(
        id: LsiSymbolId,
        qualifiedName: String,
        modality: LsiModality = LsiModality.OPEN,
        typeParameters: List<LsiTypeParameter> = emptyList(),
        superTypes: List<LsiDeclaredType> = listOf(LsiDeclaredType(OBJECT_TYPE)),
        memberIds: List<LsiSymbolId> = emptyList(),
        annotations: List<LsiAnnotation> = emptyList(),
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = id,
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.CLASS,
            modality = modality,
            typeParameters = typeParameters,
            superTypes = superTypes,
            memberIds = memberIds,
            annotations = annotations,
            origin = origin,
        )
    }

    private fun tx(propagation: String): LsiAnnotation {
        return LsiAnnotation(
            type = TX,
            arguments = mapOf(
                "value" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.EnumValue(PROPAGATION, propagation),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                )
            ),
        )
    }

    private fun annotationDeclaration(target: String): LsiTypeDeclaration {
        val source = LsiSource.of("demo/ParameterMarker.kt", LsiLanguage.KOTLIN)
        return LsiTypeDeclaration(
            id = PARAMETER_MARKER,
            name = "ParameterMarker",
            qualifiedName = "demo.ParameterMarker",
            kind = LsiTypeDeclarationKind.ANNOTATION,
            annotations = listOf(
                LsiAnnotation(
                    type = KOTLIN_TARGET,
                    arguments = mapOf(
                        "allowedTargets" to LsiAnnotationArgument(
                            value = LsiAnnotationValue.ArrayValue(
                                listOf(LsiAnnotationValue.EnumValue(KOTLIN_ANNOTATION_TARGET, target)),
                            ),
                            origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                        ),
                    ),
                )
            ),
            origin = LsiOrigin(LsiOriginKind.SOURCE, source),
        )
    }

    private companion object {
        val TYPE_ID = LsiSymbolId.type("demo.BookService")
        val TX = LsiSymbolId.type("org.babyfish.jimmer.sql.transaction.Tx")
        val PROPAGATION = LsiSymbolId.type("org.babyfish.jimmer.sql.transaction.Propagation")
        val J_SQL_CLIENT = LsiSymbolId.type("org.babyfish.jimmer.sql.JSqlClient")
        val K_SQL_CLIENT = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.KSqlClient")
        val RUNTIME_EXCEPTION = LsiSymbolId.type("java.lang.RuntimeException")
        val OBJECT_TYPE = LsiSymbolId.type("java.lang.Object")
        val STRING_TYPE = LsiSymbolId.type("java.lang.String")
        val PARAMETER_MARKER = LsiSymbolId.type("demo.ParameterMarker")
        val KOTLIN_TARGET = LsiSymbolId.type("kotlin.annotation.Target")
        val KOTLIN_ANNOTATION_TARGET = LsiSymbolId.type("kotlin.annotation.AnnotationTarget")
        val SYNTHETIC_ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
    }
}
