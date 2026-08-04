package site.addzero.lsi.jimmer.tuple

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
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.stableSignature

class TypedTupleWorkspaceExtensionsTest {
    @Test
    fun `java tuple consumes fields in declaration order with setter construction`() {
        val origin = sourceOrigin(LsiLanguage.JAVA, "demo/BookStats.java")
        val tupleId = LsiSymbolId.type("demo.BookStats")
        val markerId = LsiSymbolId.type("demo.Marker")
        val count = field(tupleId, "bookCount", LsiPrimitiveType(LsiPrimitiveKind.LONG), origin = origin)
        val ignored = field(tupleId, "ignored", STRING_TYPE, static = true, origin = origin)
        val title = field(tupleId, "title", STRING_TYPE, origin = origin)
        val marker = type("demo.Marker", kind = LsiTypeDeclarationKind.INTERFACE)
        val tuple = type(
            qualifiedName = "demo.BookStats",
            annotations = listOf(annotation(TYPED_TUPLE)),
            memberIds = listOf(count.id, ignored.id, title.id),
            superTypes = listOf(LsiDeclaredType(markerId), LsiDeclaredType(OBJECT_TYPE)),
            origin = origin,
        )
        val workspace = LsiWorkspace(declarations = listOf(marker, count, ignored, title, tuple))

        val model = workspace.toTypedTupleSchema().tuples.single()

        assertEquals(setOf(tupleId), workspace.typedTupleTypeIds())
        assertEquals(LsiLanguage.JAVA, model.sourceLanguage)
        assertEquals("demo", model.packageName)
        assertEquals("BookStats", model.simpleName)
        assertEquals(listOf("bookCount", "title"), model.properties.map(TypedTupleProperty::name))
        assertEquals(listOf(count.id, title.id), model.properties.map(TypedTupleProperty::sourceMemberId))
        val construction = assertIs<TypedTupleJavaSetterConstruction>(model.construction)
        assertEquals(null, construction.constructorId)
        assertEquals(listOf("setBookCount", "setTitle"), construction.assignments.map { it.setterName })
        assertEquals(listOf(0, 1), construction.propertyIndexes)
    }

    @Test
    fun `base table projection freezes entity scalar and dto roles`() {
        val origin = sourceOrigin(LsiLanguage.JAVA, "demo/ProjectionTuple.java")
        val tupleId = LsiSymbolId.type("demo.ProjectionTuple")
        val entityId = LsiSymbolId.type("demo.Book")
        val dtoId = LsiSymbolId.type("demo.BookView")
        val viewId = LsiSymbolId.type("org.babyfish.jimmer.View")
        val book = field(tupleId, "book", LsiDeclaredType(entityId), origin = origin)
        val count = field(tupleId, "count", LONG_TYPE, origin = origin)
        val tuple = type(
            qualifiedName = "demo.ProjectionTuple",
            annotations = listOf(annotation(TYPED_TUPLE)),
            memberIds = listOf(book.id, count.id),
            origin = origin,
        )
        val entity = type(
            qualifiedName = "demo.Book",
            kind = LsiTypeDeclarationKind.INTERFACE,
            annotations = listOf(annotation(ENTITY)),
        )
        val dto = type(
            qualifiedName = "demo.BookView",
            superTypes = listOf(LsiDeclaredType(viewId)),
        )
        val projectionModel = LsiWorkspace(declarations = listOf(entity, book, count, tuple))
            .toTypedTupleSchema()
            .tuples
            .single()
        val selections = requireNotNull(projectionModel.baseTableProjection).selections

        assertEquals(TypedTupleBaseTableSelectionKind.NON_NULL_TABLE, selections[0].kind)
        assertEquals(LsiSymbolId.type("demo.BookTable"), selections[0].entityTableTypeId)
        assertEquals(TypedTupleBaseTableSelectionKind.NON_NULL_EXPRESSION, selections[1].kind)
        assertEquals(TypedTupleScalarCategory.NUMERIC, selections[1].scalarCategory)

        val dtoField = field(tupleId, "book", LsiDeclaredType(dtoId), origin = origin)
        val dtoTuple = tuple.copy(memberIds = listOf(dtoField.id, count.id))
        val dtoModel = LsiWorkspace(declarations = listOf(dto, dtoField, count, dtoTuple))
            .toTypedTupleSchema()
            .tuples
            .single()
        assertEquals(null, dtoModel.baseTableProjection)
    }

    @Test
    fun `java full constructor binds arguments by parameter name and type`() {
        val origin = sourceOrigin(LsiLanguage.JAVA, "demo/ReorderedTuple.java")
        val tupleId = LsiSymbolId.type("demo.ReorderedTuple")
        val title = field(tupleId, "title", STRING_TYPE, mutable = false, origin = origin)
        val count = field(tupleId, "count", LONG_TYPE, mutable = false, origin = origin)
        val constructor = constructor(
            ownerId = tupleId,
            parameters = listOf("count" to LONG_TYPE, "title" to STRING_TYPE),
            origin = origin,
        )
        val tuple = type(
            qualifiedName = "demo.ReorderedTuple",
            annotations = listOf(annotation(TYPED_TUPLE)),
            memberIds = listOf(constructor.id, title.id, count.id),
            origin = origin,
        )

        val model = LsiWorkspace(declarations = listOf(tuple, constructor, title, count))
            .toTypedTupleSchema()
            .tuples
            .single()

        val construction = assertIs<TypedTupleJavaConstructorConstruction>(model.construction)
        assertEquals(constructor.id, construction.constructorId)
        assertEquals(listOf(1, 0), construction.arguments.map { it.propertyIndex })
        assertEquals(constructor.parameters.map(LsiParameter::id), construction.arguments.map { it.parameterId })
        assertEquals(listOf("count", "title"), construction.arguments.map { it.parameterName })
        assertEquals(listOf(constructor.id, title.id, count.id).sorted(), model.dependencies.memberIds)
    }

    @Test
    fun `lombok data chooses construction from field finality`() {
        val finalModel = javaLombokDataTuple(mutableStates = listOf(false, false))
        assertIs<TypedTupleJavaConstructorConstruction>(finalModel.construction)

        val mutableModel = javaLombokDataTuple(mutableStates = listOf(true, true))
        assertIs<TypedTupleJavaSetterConstruction>(mutableModel.construction)

        val exception = assertFailsWith<TypedTupleValidationException> {
            javaLombokDataTuple(mutableStates = listOf(false, true))
        }
        assertTrue(exception.message.orEmpty().contains("mix final and non-final"))
        assertFalse(exception.recoverable)
    }

    @Test
    fun `kotlin tuple consumes primary properties in constructor order`() {
        val origin = sourceOrigin(LsiLanguage.KOTLIN, "demo/BookStats.kt")
        val tupleId = LsiSymbolId.type("demo.BookStats")
        val count = property(tupleId, "count", LONG_TYPE, origin = origin)
        val bodyValue = property(tupleId, "bodyValue", STRING_TYPE, origin = origin)
        val title = property(tupleId, "title", STRING_TYPE, origin = origin)
        val primary = constructor(
            ownerId = tupleId,
            parameters = listOf("title" to STRING_TYPE, "count" to LONG_TYPE),
            primary = true,
            origin = origin,
        )
        val tuple = type(
            qualifiedName = "demo.BookStats",
            dataClass = true,
            annotations = listOf(annotation(TYPED_TUPLE)),
            memberIds = listOf(count.id, bodyValue.id, title.id, primary.id),
            origin = origin,
        )

        val model = LsiWorkspace(declarations = listOf(tuple, count, bodyValue, title, primary))
            .toTypedTupleSchema()
            .tuples
            .single()

        assertEquals(LsiLanguage.KOTLIN, model.sourceLanguage)
        assertEquals(listOf("title", "count"), model.properties.map(TypedTupleProperty::name))
        assertFalse(bodyValue.id in model.dependencies.memberIds)
        val construction = assertIs<TypedTupleKotlinConstructorConstruction>(model.construction)
        assertEquals(primary.id, construction.constructorId)
        assertEquals(listOf("title", "count"), construction.arguments.map { it.parameterName })
        assertEquals(listOf(title.id, count.id), construction.arguments.map { it.sourceMemberId })
    }

    @Test
    fun `collects recursive type source and annotation dependencies`() {
        val tupleSource = LsiSource.of("src/main/kotlin/demo/BookTuple.kt", LsiLanguage.KOTLIN)
        val bookSource = LsiSource.of("src/main/kotlin/demo/Book.kt", LsiLanguage.KOTLIN)
        val tupleOrigin = LsiOrigin(LsiOriginKind.SOURCE, tupleSource)
        val bookOrigin = LsiOrigin(LsiOriginKind.SOURCE, bookSource)
        val tupleId = LsiSymbolId.type("demo.BookTuple")
        val bookId = LsiSymbolId.type("demo.Book")
        val listId = LsiSymbolId.type("java.util.List")
        val annotationId = LsiSymbolId.type("demo.TypeMarker")
        val payloadId = LsiSymbolId.type("demo.AnnotationPayload")
        val booksType = LsiDeclaredType(
            declarationId = listId,
            arguments = listOf(
                LsiTypeArgument.invariant(
                    LsiDeclaredType(
                        declarationId = bookId,
                        annotations = listOf(
                            LsiAnnotation(
                                type = annotationId,
                                arguments = mapOf(
                                    "payload" to LsiAnnotationArgument(
                                        value = LsiAnnotationValue.ClassValue(LsiDeclaredType(payloadId)),
                                        origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                                    )
                                ),
                            )
                        ),
                    )
                )
            ),
        )
        val books = property(tupleId, "books", booksType, origin = tupleOrigin)
        val count = property(tupleId, "count", LONG_TYPE, origin = tupleOrigin)
        val primary = constructor(
            tupleId,
            listOf("books" to booksType, "count" to LONG_TYPE),
            primary = true,
            origin = tupleOrigin,
        )
        val tuple = type(
            qualifiedName = "demo.BookTuple",
            dataClass = true,
            annotations = listOf(annotation(TYPED_TUPLE)),
            memberIds = listOf(books.id, count.id, primary.id),
            origin = tupleOrigin,
        )
        val book = type("demo.Book", origin = bookOrigin)
        val workspace = LsiWorkspace(
            sources = listOf(tupleSource, bookSource),
            declarations = listOf(book, books, count, primary, tuple),
        )

        val model = workspace.toTypedTupleSchema().tuples.single()

        assertEquals(
            listOf(annotationId, payloadId, bookId, listId).sorted(),
            model.properties.first().typeDependencyIds,
        )
        assertEquals(
            listOf(annotationId, payloadId, bookId, tupleId, listId).sorted(),
            model.dependencies.typeIds,
        )
        assertEquals(listOf(books.id, count.id, primary.id).sorted(), model.dependencies.memberIds)
        assertEquals(
            setOf(tupleSource, bookSource),
            workspace.originatingSources(model.dependencies.symbolIds),
        )
    }

    @Test
    fun `apt and ksp semantic snapshots match while full fingerprints differ`() {
        val apt = languageWorkspace(LsiLanguage.JAVA).toTypedTupleSchema()
        val ksp = languageWorkspace(LsiLanguage.KOTLIN).toTypedTupleSchema()

        assertEquals(apt.normalizedSnapshot(), ksp.normalizedSnapshot())
        assertNotEquals(apt.fingerprint(), ksp.fingerprint())
        assertEquals(64, apt.fingerprint().length)
        assertEquals(64, ksp.fingerprint().length)
    }

    @Test
    fun `marks missing and unresolved symbols recoverable at tuple type`() {
        val missingTuple = type(
            qualifiedName = "demo.MissingTuple",
            annotations = listOf(annotation(TYPED_TUPLE)),
            memberIds = listOf(LsiSymbolId.field(LsiSymbolId.type("demo.MissingTuple"), "value")),
        )
        val missingException = assertFailsWith<TypedTupleValidationException> {
            LsiWorkspace(declarations = listOf(missingTuple)).toTypedTupleSchema()
        }
        assertEquals(missingTuple.id, missingException.declarationId)
        assertTrue(missingException.recoverable)

        val unresolvedTupleId = LsiSymbolId.type("demo.UnresolvedTuple")
        val unresolvedField = field(unresolvedTupleId, "value", LsiUnresolvedType("missing.Value"))
        val unresolvedTuple = type(
            qualifiedName = "demo.UnresolvedTuple",
            annotations = listOf(annotation(TYPED_TUPLE)),
            memberIds = listOf(unresolvedField.id),
        )
        val unresolvedException = assertFailsWith<TypedTupleValidationException> {
            LsiWorkspace(declarations = listOf(unresolvedField, unresolvedTuple)).toTypedTupleSchema()
        }
        assertEquals(unresolvedTuple.id, unresolvedException.declarationId)
        assertTrue(unresolvedException.recoverable)
    }

    @Test
    fun `rejects function typed tuple properties explicitly`() {
        val tupleId = LsiSymbolId.type("demo.CallbackTuple")
        val callback = field(
            ownerId = tupleId,
            name = "callback",
            type = LsiFunctionType(returnType = STRING_TYPE),
        )
        val tuple = type(
            qualifiedName = "demo.CallbackTuple",
            annotations = listOf(annotation(TYPED_TUPLE)),
            memberIds = listOf(callback.id),
        )

        val exception = assertFailsWith<TypedTupleValidationException> {
            LsiWorkspace(declarations = listOf(callback, tuple)).toTypedTupleSchema()
        }

        assertEquals(callback.id, exception.declarationId)
        assertTrue(exception.message.orEmpty().contains("cannot use a function type"))
        assertFalse(exception.recoverable)
    }

    @Test
    fun `rejects invalid declaration shapes and construction`() {
        val nonClass = assertRejected(
            type(
                qualifiedName = "demo.InvalidTuple",
                kind = LsiTypeDeclarationKind.INTERFACE,
                annotations = listOf(annotation(TYPED_TUPLE)),
            ),
            "must be a class",
        )
        assertFalse(nonClass.recoverable)

        val outer = type("demo.Outer")
        val nested = type(
            qualifiedName = "demo.Outer.NestedTuple",
            enclosingTypeId = outer.id,
            annotations = listOf(annotation(TYPED_TUPLE)),
        )
        val nestedException = assertFailsWith<TypedTupleValidationException> {
            LsiWorkspace(declarations = listOf(outer, nested)).toTypedTupleSchema()
        }
        assertTrue(nestedException.message.orEmpty().contains("top-level"))

        val genericId = LsiSymbolId.type("demo.GenericTuple")
        val parameterId = LsiSymbolId.typeParameter(genericId, "T")
        val generic = type(
            qualifiedName = "demo.GenericTuple",
            annotations = listOf(annotation(TYPED_TUPLE)),
            typeParameters = listOf(LsiTypeParameter(parameterId, "T")),
        )
        assertRejected(generic, "cannot be generic")

        val base = type("demo.BaseTuple")
        val tupleId = LsiSymbolId.type("demo.ConstructionTuple")
        val value = field(tupleId, "value", STRING_TYPE)
        val inherited = type(
            qualifiedName = "demo.ConstructionTuple",
            annotations = listOf(annotation(TYPED_TUPLE)),
            memberIds = listOf(value.id),
            superTypes = listOf(LsiDeclaredType(base.id)),
        )
        val inheritanceException = assertFailsWith<TypedTupleValidationException> {
            LsiWorkspace(declarations = listOf(base, value, inherited)).toTypedTupleSchema()
        }
        assertTrue(inheritanceException.message.orEmpty().contains("cannot inherit class"))

        val constructor = constructor(tupleId, listOf("other" to STRING_TYPE))
        val invalidConstruction = inherited.copy(
            memberIds = listOf(value.id, constructor.id),
            superTypes = emptyList(),
        )
        val constructionException = assertFailsWith<TypedTupleValidationException> {
            LsiWorkspace(declarations = listOf(value, constructor, invalidConstruction)).toTypedTupleSchema()
        }
        assertTrue(constructionException.message.orEmpty().contains("parameters match all fields"))
    }

    @Test
    fun `rejects lombok builder and non data kotlin class`() {
        val lombokTupleId = LsiSymbolId.type("demo.LombokTuple")
        val lombokValue = field(lombokTupleId, "value", STRING_TYPE)
        val lombokTuple = type(
            qualifiedName = "demo.LombokTuple",
            annotations = listOf(annotation(TYPED_TUPLE), annotation(LOMBOK_BUILDER)),
            memberIds = listOf(lombokValue.id),
        )
        val lombokException = assertFailsWith<TypedTupleValidationException> {
            LsiWorkspace(declarations = listOf(lombokValue, lombokTuple)).toTypedTupleSchema()
        }
        assertTrue(lombokException.message.orEmpty().contains("lombok.Builder"))

        val kotlinOrigin = sourceOrigin(LsiLanguage.KOTLIN, "demo/NotData.kt")
        val kotlinTupleId = LsiSymbolId.type("demo.NotData")
        val kotlinProperty = property(kotlinTupleId, "value", STRING_TYPE, origin = kotlinOrigin)
        val kotlinConstructor = constructor(
            kotlinTupleId,
            listOf("value" to STRING_TYPE),
            primary = true,
            origin = kotlinOrigin,
        )
        val kotlinTuple = type(
            qualifiedName = "demo.NotData",
            annotations = listOf(annotation(TYPED_TUPLE)),
            memberIds = listOf(kotlinProperty.id, kotlinConstructor.id),
            origin = kotlinOrigin,
        )
        val kotlinException = assertFailsWith<TypedTupleValidationException> {
            LsiWorkspace(declarations = listOf(kotlinProperty, kotlinConstructor, kotlinTuple))
                .toTypedTupleSchema()
        }
        assertTrue(kotlinException.message.orEmpty().contains("data class"))
    }

    private fun javaLombokDataTuple(mutableStates: List<Boolean>): TypedTupleType {
        val tupleId = LsiSymbolId.type("demo.LombokTuple")
        val fields = mutableStates.mapIndexed { index, mutable ->
            field(tupleId, "value$index", STRING_TYPE, mutable = mutable)
        }
        val tuple = type(
            qualifiedName = "demo.LombokTuple",
            annotations = listOf(annotation(TYPED_TUPLE), annotation(LOMBOK_DATA)),
            memberIds = fields.map(LsiField::id),
        )
        return LsiWorkspace(declarations = fields + tuple)
            .toTypedTupleSchema()
            .tuples
            .single()
    }

    private fun assertRejected(
        type: LsiTypeDeclaration,
        messagePart: String,
    ): TypedTupleValidationException {
        val exception = assertFailsWith<TypedTupleValidationException> {
            LsiWorkspace(declarations = listOf(type)).toTypedTupleSchema()
        }
        assertTrue(exception.message.orEmpty().contains(messagePart))
        return exception
    }

    private fun languageWorkspace(language: LsiLanguage): LsiWorkspace {
        val extension = if (language == LsiLanguage.JAVA) "java" else "kt"
        val origin = sourceOrigin(language, "demo/LanguageTuple.$extension")
        val tupleId = LsiSymbolId.type("demo.LanguageTuple")
        val stringType = LsiDeclaredType(
            declarationId = LsiSymbolId.type("java.lang.String"),
            nullability = if (language == LsiLanguage.JAVA) {
                LsiNullability.PLATFORM
            } else {
                LsiNullability.NON_NULL
            },
        )
        val members = if (language == LsiLanguage.JAVA) {
            listOf(
                field(tupleId, "title", stringType, origin = origin),
                field(tupleId, "count", LONG_TYPE, origin = origin),
            )
        } else {
            val title = property(tupleId, "title", stringType, origin = origin)
            val count = property(tupleId, "count", LONG_TYPE, origin = origin)
            val primary = constructor(
                tupleId,
                listOf("title" to stringType, "count" to LONG_TYPE),
                primary = true,
                origin = origin,
            )
            listOf(title, count, primary)
        }
        val tuple = type(
            qualifiedName = "demo.LanguageTuple",
            dataClass = language == LsiLanguage.KOTLIN,
            annotations = listOf(annotation(TYPED_TUPLE)),
            memberIds = members.map { member -> member.id },
            origin = origin,
        )
        return LsiWorkspace(
            sources = listOf(requireNotNull(origin.source)),
            declarations = members + tuple,
        )
    }

    private fun type(
        qualifiedName: String,
        kind: LsiTypeDeclarationKind = LsiTypeDeclarationKind.CLASS,
        enclosingTypeId: LsiSymbolId? = null,
        dataClass: Boolean = false,
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
            enclosingTypeId = enclosingTypeId,
            dataClass = dataClass,
            typeParameters = typeParameters,
            superTypes = superTypes,
            memberIds = memberIds,
            annotations = annotations,
            origin = origin,
        )
    }

    private fun field(
        ownerId: LsiSymbolId,
        name: String,
        type: LsiTypeRef,
        mutable: Boolean = true,
        static: Boolean = false,
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiField {
        return LsiField(
            id = LsiSymbolId.field(ownerId, name),
            name = name,
            ownerId = ownerId,
            type = type,
            mutable = mutable,
            static = static,
            origin = origin,
        )
    }

    private fun property(
        ownerId: LsiSymbolId,
        name: String,
        type: LsiTypeRef,
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiProperty {
        return LsiProperty(
            id = LsiSymbolId.property(ownerId, name),
            name = name,
            ownerId = ownerId,
            type = type,
            origin = origin,
        )
    }

    private fun constructor(
        ownerId: LsiSymbolId,
        parameters: List<Pair<String, LsiTypeRef>>,
        primary: Boolean = false,
        visibility: LsiVisibility = LsiVisibility.PUBLIC,
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiConstructor {
        val constructorId = LsiSymbolId.constructor(
            ownerId,
            parameters.map { (_, type) -> type.stableSignature() },
        )
        val lsiParameters = parameters.mapIndexed { index, (name, type) ->
            LsiParameter(
                id = LsiSymbolId.parameter(constructorId, index, name),
                name = name,
                callableId = constructorId,
                index = index,
                type = type,
                origin = origin,
            )
        }
        return LsiConstructor(
            id = constructorId,
            ownerId = ownerId,
            primary = primary,
            parameters = lsiParameters,
            visibility = visibility,
            origin = origin,
        )
    }

    private fun sourceOrigin(language: LsiLanguage, relativePath: String): LsiOrigin {
        val source = LsiSource.of("src/main/${language.name.lowercase()}/$relativePath", language)
        return LsiOrigin(LsiOriginKind.SOURCE, source)
    }

    private fun annotation(type: LsiSymbolId): LsiAnnotation {
        return LsiAnnotation(type)
    }

    companion object {
        private val TYPED_TUPLE = LsiSymbolId.type("org.babyfish.jimmer.sql.TypedTuple")
        private val ENTITY = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
        private val LOMBOK_BUILDER = LsiSymbolId.type("lombok.Builder")
        private val LOMBOK_DATA = LsiSymbolId.type("lombok.Data")
        private val OBJECT_TYPE = LsiSymbolId.type("java.lang.Object")
        private val STRING_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        private val LONG_TYPE = LsiPrimitiveType(LsiPrimitiveKind.LONG)
        private val SYNTHETIC_ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
    }
}
