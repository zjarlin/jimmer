package org.babyfish.jimmer.compiler.immutable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
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
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutableDraftCodegenModelTest {

    @Test
    fun `freezes accessor storage associated id and validation plans`() {
        val workspace = workspace(activeGetterName = "isActive")
        val schema = JimmerImmutablePrecompiler().compile(workspace)
        val draftSchema = JimmerImmutableDraftCodegenPrecompiler().compile(schema, workspace)
        val book = draftSchema.typesById.getValue(BOOK)

        val id = book.propsById.getValue(LsiSymbolId.property(BOOK, "id"))
        assertEquals(0, id.slotIndex)
        assertEquals(JimmerImmutableDraftAccessorStyle.JAVA_BEAN_GET, id.accessorStyle)
        assertEquals("setId", id.javaSetterName)
        assertEquals("getId", id.javaBeanGetterName)
        assertEquals("SLOT_ID", id.slotName)
        assertEquals("applyId", id.javaApplierName)
        assertEquals("addIntoId", id.javaAdderByName)
        assertEquals("__idValue", id.valueFieldName)
        assertEquals("__idLoaded", id.loadedStateFieldName)
        assertEquals(JimmerImmutableDraftValueState.VALUE_AND_LOADED, id.valueState)

        val active = book.propsById.getValue(LsiSymbolId.property(BOOK, "active"))
        assertEquals(JimmerImmutableDraftAccessorStyle.JAVA_BEAN_IS, active.accessorStyle)
        assertEquals("setActive", active.javaSetterName)
        assertEquals("isActive", active.javaBeanGetterName)
        assertEquals(JimmerImmutableDraftValueState.VALUE_AND_LOADED, active.valueState)

        val title = book.propsById.getValue(LsiSymbolId.property(BOOK, "title"))
        assertEquals(JimmerImmutableDraftAccessorStyle.JAVA_BARE, title.accessorStyle)
        assertEquals("setTitle", title.javaSetterName)
        assertEquals("getTitle", title.javaBeanGetterName)
        assertEquals(JimmerImmutableDraftValueState.VALUE_AND_LOADED, title.valueState)
        assertEquals(listOf(VALID_BOOK), title.validationAnnotations.map(LsiAnnotation::type))
        assertEquals(listOf(VALIDATOR), title.customValidations.single().validatorTypeIds)
        assertEquals("invalid title", title.customValidations.single().message)

        val boxedEnabled = book.propsById.getValue(LsiSymbolId.property(BOOK, "enabled"))
        assertEquals("enabled", boxedEnabled.name)
        assertEquals("isEnabled", boxedEnabled.codegenName)
        assertEquals(JimmerImmutableDraftAccessorStyle.JAVA_BARE, boxedEnabled.accessorStyle)
        assertEquals("setIsEnabled", boxedEnabled.javaSetterName)
        assertEquals("getIsEnabled", boxedEnabled.javaBeanGetterName)
        assertEquals("SLOT_IS_ENABLED", boxedEnabled.slotName)

        val url = book.propsById.getValue(LsiSymbolId.property(BOOK, "URL"))
        assertEquals("URL", url.name)
        assertEquals("uRL", url.codegenName)
        assertEquals("setURL", url.javaSetterName)
        assertEquals("getURL", url.javaBeanGetterName)
        assertEquals("SLOT_U_RL", url.slotName)

        val snakeName = book.propsById.getValue(LsiSymbolId.property(BOOK, "first_name"))
        assertEquals("SLOT_FIRST_NAME", snakeName.slotName)

        val author = book.propsById.getValue(LsiSymbolId.property(BOOK, "author"))
        val associatedId = assertNotNull(author.associatedId)
        assertEquals("authorId", associatedId.name)
        assertEquals(LsiSymbolId.property(AUTHOR, "id"), associatedId.targetIdPropId)
        assertTrue(author.autoCreateSupported)
        assertTrue(author.referenceMutationSupported)

        assertEquals(listOf(VALID_BOOK), book.validationAnnotations.map(LsiAnnotation::type))
        assertEquals(listOf(VALIDATOR), book.customValidations.single().validatorTypeIds)
        assertEquals("invalid book", book.customValidations.single().message)
        assertEquals(setOf(BOOK), book.artifactOriginatingSymbols)
        assertEquals(listOf(SOURCE), book.artifactOriginatingSources)
        assertTrue(AUTHOR in book.dependencySymbols)
        assertTrue(LsiSymbolId.property(AUTHOR, "id") in book.dependencySymbols)
        assertTrue(LsiSymbolId.property(BASE_ID, "id") in book.dependencySymbols)
        assertTrue(VALID_BOOK in book.dependencySymbols)
        assertTrue(VALIDATOR in book.dependencySymbols)
        assertTrue(BASE_SOURCE in book.dependencySources)
        assertEquals(
            draftSchema.fingerprint(),
            JimmerImmutableDraftCodegenPrecompiler()
                .compile(
                    schema,
                    LsiWorkspace(
                        sources = workspace.sources,
                        declarations = workspace.declarations.reversed(),
                        typeHierarchy = workspace.typeHierarchy,
                        annotationScopes = workspace.annotationScopes,
                    ),
                )
                .fingerprint(),
        )

        val alternateWorkspace = workspace(activeGetterName = "active")
        val alternateSchema = JimmerImmutablePrecompiler().compile(alternateWorkspace)
        val alternateDraftSchema = JimmerImmutableDraftCodegenPrecompiler().compile(
            alternateSchema,
            alternateWorkspace,
        )
        assertEquals(draftSchema.normalizedSnapshot(), alternateDraftSchema.normalizedSnapshot())
        assertNotEquals(draftSchema.fingerprint(), alternateDraftSchema.fingerprint())

        val getterValidationWorkspace = workspace(
            activeGetterName = "isActive",
            validationUseSiteTarget = LsiAnnotationUseSiteTarget.GETTER,
        )
        val getterValidationSchema = JimmerImmutablePrecompiler().compile(getterValidationWorkspace)
        val getterValidationDraftSchema = JimmerImmutableDraftCodegenPrecompiler().compile(
            getterValidationSchema,
            getterValidationWorkspace,
        )
        assertEquals(draftSchema.normalizedSnapshot(), getterValidationDraftSchema.normalizedSnapshot())
        assertNotEquals(draftSchema.fingerprint(), getterValidationDraftSchema.fingerprint())
    }

    private fun workspace(
        activeGetterName: String,
        validationUseSiteTarget: LsiAnnotationUseSiteTarget? = null,
    ): LsiWorkspace {
        val baseId = LsiSymbolId.property(BASE_ID, "id")
        val bookId = LsiSymbolId.property(BOOK, "id")
        val active = LsiSymbolId.property(BOOK, "active")
        val title = LsiSymbolId.property(BOOK, "title")
        val boxedEnabled = LsiSymbolId.property(BOOK, "enabled")
        val url = LsiSymbolId.property(BOOK, "URL")
        val snakeName = LsiSymbolId.property(BOOK, "first_name")
        val author = LsiSymbolId.property(BOOK, "author")
        val declarations = listOf(
            annotationType(),
            immutableType(
                id = BASE_ID,
                memberIds = listOf(baseId),
                marker = MAPPED_SUPERCLASS,
                origin = BASE_ORIGIN,
            ),
            property(
                id = baseId,
                ownerId = BASE_ID,
                type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
                getterName = "getId",
                annotations = listOf(LsiAnnotation(ID)),
                origin = BASE_ORIGIN,
            ),
            immutableType(
                id = AUTHOR,
                memberIds = emptyList(),
                superTypes = listOf(LsiDeclaredType(BASE_ID)),
            ),
            immutableType(
                id = BOOK,
                memberIds = listOf(title, active, boxedEnabled, url, snakeName, author, bookId),
                annotations = listOf(validation("invalid book")),
            ),
            property(
                id = title,
                ownerId = BOOK,
                type = LsiDeclaredType(STRING, nullability = LsiNullability.NULLABLE),
                getterName = "title",
                annotations = listOf(validation("invalid title", validationUseSiteTarget)),
            ),
            property(
                id = active,
                ownerId = BOOK,
                type = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
                getterName = activeGetterName,
            ),
            property(
                id = boxedEnabled,
                ownerId = BOOK,
                type = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN, boxed = true),
                getterName = "isEnabled",
            ),
            property(
                id = url,
                ownerId = BOOK,
                type = LsiDeclaredType(STRING),
                getterName = "getURL",
            ),
            property(
                id = snakeName,
                ownerId = BOOK,
                type = LsiDeclaredType(STRING),
                getterName = "first_name",
            ),
            property(
                id = author,
                ownerId = BOOK,
                type = LsiDeclaredType(AUTHOR),
                getterName = "author",
                annotations = listOf(LsiAnnotation(MANY_TO_ONE)),
            ),
            property(
                id = bookId,
                ownerId = BOOK,
                type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
                getterName = "getId",
                annotations = listOf(LsiAnnotation(ID)),
            ),
        )
        return LsiWorkspace(
            sources = listOf(SOURCE, BASE_SOURCE),
            declarations = declarations,
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        memberIds: List<LsiSymbolId>,
        annotations: List<LsiAnnotation> = emptyList(),
        marker: LsiSymbolId = ENTITY,
        origin: LsiOrigin = ORIGIN,
        superTypes: List<LsiDeclaredType> = emptyList(),
    ): LsiTypeDeclaration {
        val qualifiedName = id.requireTypeQualifiedName()
        return LsiTypeDeclaration(
            id = id,
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.INTERFACE,
            superTypes = superTypes,
            memberIds = memberIds,
            annotations = listOf(LsiAnnotation(marker)) + annotations,
            origin = origin,
        )
    }

    private fun property(
        id: LsiSymbolId,
        ownerId: LsiSymbolId,
        type: site.addzero.lsi.model.LsiTypeRef,
        getterName: String,
        annotations: List<LsiAnnotation> = emptyList(),
        origin: LsiOrigin = ORIGIN,
    ): LsiProperty {
        return LsiProperty(
            id = id,
            name = id.value.substringAfterLast(':'),
            ownerId = ownerId,
            type = type,
            getterName = getterName,
            annotations = annotations,
            origin = origin,
        )
    }

    private fun annotationType(): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = VALID_BOOK,
            name = "ValidBook",
            qualifiedName = "demo.ValidBook",
            kind = LsiTypeDeclarationKind.ANNOTATION,
            annotations = listOf(
                LsiAnnotation(
                    type = JAKARTA_CONSTRAINT,
                    arguments = mapOf(
                        "validatedBy" to LsiAnnotationArgument(
                            value = LsiAnnotationValue.ArrayValue(
                                listOf(
                                    LsiAnnotationValue.ClassValue(LsiDeclaredType(VALIDATOR))
                                )
                            ),
                            origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                        )
                    ),
                )
            ),
            origin = ORIGIN,
        )
    }

    private fun validation(
        message: String,
        useSiteTarget: LsiAnnotationUseSiteTarget? = null,
    ): LsiAnnotation {
        return LsiAnnotation(
            type = VALID_BOOK,
            arguments = mapOf(
                "message" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.StringValue(message),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                )
            ),
            useSiteTarget = useSiteTarget,
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("src/main/java/demo/Models.java", LsiLanguage.JAVA)
        val BASE_SOURCE = LsiSource.of("src/main/java/demo/BaseId.java", LsiLanguage.JAVA)
        val ORIGIN = LsiOrigin(LsiOriginKind.SOURCE, SOURCE)
        val BASE_ORIGIN = LsiOrigin(LsiOriginKind.SOURCE, BASE_SOURCE)
        val BASE_ID = LsiSymbolId.type("demo.BaseId")
        val AUTHOR = LsiSymbolId.type("demo.Author")
        val BOOK = LsiSymbolId.type("demo.Book")
        val VALID_BOOK = LsiSymbolId.type("demo.ValidBook")
        val VALIDATOR = LsiSymbolId.type("demo.ValidBookValidator")
        val STRING = LsiSymbolId.type("java.lang.String")
        val ENTITY = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
        val MAPPED_SUPERCLASS = LsiSymbolId.type("org.babyfish.jimmer.sql.MappedSuperclass")
        val ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Id")
        val MANY_TO_ONE = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToOne")
        val JAKARTA_CONSTRAINT = LsiSymbolId.type("jakarta.validation.Constraint")
    }
}
