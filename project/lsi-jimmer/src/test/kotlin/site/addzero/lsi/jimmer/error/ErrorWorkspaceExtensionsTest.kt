package site.addzero.lsi.jimmer.error

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
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiEnumEntry
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace

class ErrorWorkspaceExtensionsTest {

    @Test
    fun `precompiles error family fields codes and names`() {
        val schema = errorWorkspace(LsiLanguage.JAVA, repeatableContainer = true)
            .toErrorSchema(ErrorSchemaOptions(checkedException = true))

        val family = schema.families.single()
        assertEquals("demo", family.packageName)
        assertEquals("BOOK", family.family)
        assertEquals(LsiSymbolId.type("demo.BookException"), family.exceptionTypeId)
        assertEquals("BookException", family.exceptionSimpleName)
        assertTrue(family.checkedException)
        assertEquals("demo/BookErrorCode.java", family.originatingSources.single().path)
        assertEquals(listOf("timestamp", "tags"), family.declaredFields.map(ErrorField::name))
        val code = family.codes.single()
        assertEquals("OUT_OF_RANGE", code.code)
        assertEquals(LsiSymbolId.type("demo.BookException.OutOfRange"), code.exceptionTypeId)
        assertEquals("outOfRange", code.creatorName)
        assertEquals("OutOfRange", code.exceptionSimpleName)
        assertEquals(
            listOf("timestamp", "tags", "min", "max"),
            (family.declaredFields + code.declaredFields).map(ErrorField::name),
        )
        assertEquals(64, schema.fingerprint().length)
    }

    @Test
    fun `apt and ksp repeatable annotations have identical error snapshots`() {
        val apt = errorWorkspace(LsiLanguage.JAVA, repeatableContainer = true).toErrorSchema()
        val ksp = errorWorkspace(LsiLanguage.KOTLIN, repeatableContainer = false).toErrorSchema()

        assertEquals(apt.normalizedSnapshot(), ksp.normalizedSnapshot())
        assertEquals(apt.fingerprint(), ksp.fingerprint())
    }

    @Test
    fun `snapshot distinguishes primitive and boxed error fields`() {
        val schema = errorWorkspace(LsiLanguage.JAVA, repeatableContainer = true).toErrorSchema()
        val family = schema.families.single()
        val boxedFamily = family.copy(
            codes = family.codes.map { code ->
                code.copy(
                    declaredFields = code.declaredFields.map { field ->
                        if (field.name == "min") {
                            field.copy(type = LsiPrimitiveType(LsiPrimitiveKind.INT, boxed = true))
                        } else {
                            field
                        }
                    },
                )
            },
        )
        val boxedSchema = ErrorSchema(listOf(boxedFamily))

        assertNotEquals(schema.normalizedSnapshot(), boxedSchema.normalizedSnapshot())
        assertNotEquals(schema.fingerprint(), boxedSchema.fingerprint())
    }

    @Test
    fun `target selection excludes classpath error families from generation`() {
        val sourceWorkspace = errorWorkspace(LsiLanguage.JAVA, repeatableContainer = false)
        val sourceFamily = sourceWorkspace.declarationsOfType<LsiTypeDeclaration>().single()
        val binaryFamilyId = LsiSymbolId.type("external.ExternalErrorCode")
        val binaryEntry = LsiEnumEntry(
            id = LsiSymbolId("${binaryFamilyId.value}#FAILED"),
            name = "FAILED",
            ownerId = binaryFamilyId,
            origin = BINARY_ORIGIN,
        )
        val binaryFamily = type(
            qualifiedName = binaryFamilyId.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.ENUM,
            annotations = listOf(errorFamily("EXTERNAL")),
            enumEntries = listOf(binaryEntry),
            origin = BINARY_ORIGIN,
        )
        val workspace = LsiWorkspace(
            sources = sourceWorkspace.sources,
            declarations = sourceWorkspace.declarations + binaryFamily,
        )

        val schema = workspace.toErrorSchema(targetTypeIds = setOf(sourceFamily.id))

        assertEquals(listOf(sourceFamily.id), schema.families.map(ErrorFamily::id))
    }

    @Test
    fun `derives nested error exception name and package`() {
        val outer = type("demo.Outer", LsiTypeDeclarationKind.CLASS)
        val nestedFamilyId = LsiSymbolId.type("demo.Outer.SecurityErrorCode")
        val entry = LsiEnumEntry(
            id = LsiSymbolId("${nestedFamilyId.value}#DENIED"),
            name = "DENIED",
            ownerId = nestedFamilyId,
            origin = SYNTHETIC_ORIGIN,
        )
        val nested = type(
            qualifiedName = "demo.Outer.SecurityErrorCode",
            kind = LsiTypeDeclarationKind.ENUM,
            annotations = listOf(errorFamily("")),
            enumEntries = listOf(entry),
        )

        val family = LsiWorkspace(declarations = listOf(outer, nested)).toErrorSchema()
            .families.single()

        assertEquals("demo", family.packageName)
        assertEquals("Outer_SecurityException", family.exceptionSimpleName)
        assertEquals("OUTER_SECURITY", family.family)
    }

    @Test
    fun `preserves leading acronym when deriving default family`() {
        val familyId = LsiSymbolId.type("demo.KBusinessError")
        val entry = LsiEnumEntry(
            id = LsiSymbolId("${familyId.value}#FAILED"),
            name = "FAILED",
            ownerId = familyId,
            origin = SYNTHETIC_ORIGIN,
        )
        val family = type(
            qualifiedName = familyId.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.ENUM,
            annotations = listOf(errorFamily("")),
            enumEntries = listOf(entry),
        )

        val schema = LsiWorkspace(declarations = listOf(family)).toErrorSchema()

        assertEquals("KBUSINESS", schema.families.single().family)
    }

    @Test
    fun `rejects invalid family and fields`() {
        val nonEnum = type(
            qualifiedName = "demo.InvalidError",
            kind = LsiTypeDeclarationKind.INTERFACE,
            annotations = listOf(errorFamily("INVALID")),
        )
        val kindException = assertFailsWith<ErrorValidationException> {
            LsiWorkspace(declarations = listOf(nonEnum)).toErrorSchema()
        }
        assertTrue(kindException.message.orEmpty().contains("Only enum"))

        val emptyFamily = type(
            qualifiedName = "demo.EmptyError",
            kind = LsiTypeDeclarationKind.ENUM,
            annotations = listOf(errorFamily("EMPTY")),
        )
        val emptyFamilyException = assertFailsWith<ErrorValidationException> {
            LsiWorkspace(declarations = listOf(emptyFamily)).toErrorSchema()
        }
        assertTrue(emptyFamilyException.message.orEmpty().contains("at least one error code"))

        val duplicate = errorWorkspace(
            language = LsiLanguage.JAVA,
            repeatableContainer = false,
            codeFields = listOf(errorField("timestamp", STRING_TYPE)),
        )
        val duplicateException = assertFailsWith<ErrorValidationException> {
            duplicate.toErrorSchema()
        }
        assertTrue(duplicateException.message.orEmpty().contains("already been declared"))

        val primitiveList = errorWorkspace(
            language = LsiLanguage.JAVA,
            repeatableContainer = false,
            codeFields = listOf(errorField("values", LsiPrimitiveType(LsiPrimitiveKind.INT), list = true)),
        )
        val primitiveException = assertFailsWith<ErrorValidationException> {
            primitiveList.toErrorSchema()
        }
        assertTrue(primitiveException.message.orEmpty().contains("list of primitive"))
    }

    private fun errorWorkspace(
        language: LsiLanguage,
        repeatableContainer: Boolean,
        codeFields: List<LsiAnnotation> = listOf(
            errorField("min", LsiPrimitiveType(LsiPrimitiveKind.INT)),
            errorField("max", LsiPrimitiveType(LsiPrimitiveKind.INT)),
        ),
    ): LsiWorkspace {
        val familyId = LsiSymbolId.type("demo.BookErrorCode")
        val entryId = LsiSymbolId("${familyId.value}#OUT_OF_RANGE")
        val familyFields = listOf(
            errorField("timestamp", DATE_TIME_TYPE, documentation = "Created time"),
            errorField("tags", STRING_TYPE, list = true, nullable = true),
        )
        val familyAnnotations = if (repeatableContainer) {
            listOf(errorFamily("BOOK"), errorFields(familyFields))
        } else {
            listOf(errorFamily("BOOK")) + familyFields
        }
        val codeAnnotations = if (repeatableContainer) {
            listOf(errorFields(codeFields))
        } else {
            codeFields
        }
        val origin = sourceOrigin(language)
        val entry = LsiEnumEntry(
            id = entryId,
            name = "OUT_OF_RANGE",
            ownerId = familyId,
            documentation = "Out of range.\r\n",
            annotations = codeAnnotations,
            origin = origin,
        )
        val family = type(
            qualifiedName = "demo.BookErrorCode",
            kind = LsiTypeDeclarationKind.ENUM,
            annotations = familyAnnotations,
            enumEntries = listOf(entry),
            documentation = "Book errors.",
            origin = origin,
        )
        return LsiWorkspace(
            sources = listOf(requireNotNull(origin.source)),
            declarations = listOf(family),
        )
    }

    private fun type(
        qualifiedName: String,
        kind: LsiTypeDeclarationKind,
        annotations: List<LsiAnnotation> = emptyList(),
        enumEntries: List<LsiEnumEntry> = emptyList(),
        documentation: String? = null,
        origin: LsiOrigin = SYNTHETIC_ORIGIN,
    ): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = LsiSymbolId.type(qualifiedName),
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = kind,
            annotations = annotations,
            enumEntries = enumEntries,
            documentation = documentation,
            origin = origin,
        )
    }

    private fun errorFamily(value: String): LsiAnnotation {
        return annotation(
            ERROR_FAMILY,
            mapOf("value" to LsiAnnotationValue.StringValue(value)),
        )
    }

    private fun errorFields(fields: List<LsiAnnotation>): LsiAnnotation {
        return annotation(
            ERROR_FIELDS,
            mapOf(
                "value" to LsiAnnotationValue.ArrayValue(
                    fields.map(LsiAnnotationValue::NestedAnnotationValue)
                )
            ),
        )
    }

    private fun errorField(
        name: String,
        type: LsiTypeRef,
        list: Boolean = false,
        nullable: Boolean = false,
        documentation: String = "",
    ): LsiAnnotation {
        return annotation(
            ERROR_FIELD,
            mapOf(
                "name" to LsiAnnotationValue.StringValue(name),
                "type" to LsiAnnotationValue.ClassValue(type),
                "list" to LsiAnnotationValue.BooleanValue(list),
                "nullable" to LsiAnnotationValue.BooleanValue(nullable),
                "doc" to LsiAnnotationValue.StringValue(documentation),
            ),
        )
    }

    private fun annotation(
        type: LsiSymbolId,
        arguments: Map<String, LsiAnnotationValue>,
    ): LsiAnnotation {
        return LsiAnnotation(
            type = type,
            arguments = arguments.mapValues { (_, value) ->
                LsiAnnotationArgument(value, LsiAnnotationArgumentOrigin.EXPLICIT)
            },
        )
    }

    private fun sourceOrigin(language: LsiLanguage): LsiOrigin {
        return LsiOrigin(
            kind = LsiOriginKind.SOURCE,
            source = LsiSource.of("demo/BookErrorCode.${if (language == LsiLanguage.JAVA) "java" else "kt"}", language),
        )
    }

    private companion object {
        val ERROR_FAMILY = LsiSymbolId.type("org.babyfish.jimmer.error.ErrorFamily")
        val ERROR_FIELD = LsiSymbolId.type("org.babyfish.jimmer.error.ErrorField")
        val ERROR_FIELDS = LsiSymbolId.type("org.babyfish.jimmer.error.ErrorFields")
        val STRING_TYPE = site.addzero.lsi.model.LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val DATE_TIME_TYPE = site.addzero.lsi.model.LsiDeclaredType(LsiSymbolId.type("java.time.LocalDateTime"))
        val SYNTHETIC_ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
        val BINARY_ORIGIN = LsiOrigin(LsiOriginKind.BINARY)
    }
}
