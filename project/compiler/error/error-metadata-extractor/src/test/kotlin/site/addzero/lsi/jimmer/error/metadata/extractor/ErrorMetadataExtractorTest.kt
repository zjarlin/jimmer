package site.addzero.lsi.jimmer.error.metadata.extractor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.LsiEnumConstant
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.jimmer.ERROR_FAMILY
import site.addzero.lsi.jimmer.ERROR_FIELD
import site.addzero.lsi.method.LsiMethod

class ErrorMetadataExtractorTest {

    @Test
    fun collects_error_metadata_from_enum_and_entries() {
        val extractor = ErrorMetadataExtractor()
        val declaration = fakeErrorEnum(
            simpleName = "UserError",
            qualifiedName = "test.UserError",
            annotations = listOf(
                FakeAnnotation(
                    qualifiedName = ERROR_FAMILY,
                    attributes = mapOf("value" to "")
                ),
                FakeAnnotation(
                    qualifiedName = ERROR_FIELD,
                    attributes = mapOf(
                        "name" to "tenantId",
                        "type" to FakeClass.simple("kotlin.Long"),
                        "list" to false,
                        "nullable" to false,
                        "doc" to "tenant"
                    )
                )
            ),
            constants = listOf(
                FakeEnumConstant(
                    name = "INVALID_EMAIL",
                    comment = "invalid email",
                    annotations = listOf(
                        FakeAnnotation(
                            qualifiedName = ERROR_FIELD,
                            attributes = mapOf(
                                "name" to "email",
                                "type" to FakeClass.simple("kotlin.String"),
                                "list" to false,
                                "nullable" to false,
                                "doc" to "email field"
                            )
                        )
                    )
                )
            )
        )

        val result = extractor.extract(sequenceOf(declaration))

        assertEquals(1, result.types.size)
        val metadata = result.types.single()
        assertEquals("test.UserError", metadata.id)
        assertEquals("USER", metadata.family)
        assertEquals("UserException", metadata.exceptionSimpleName)
        assertEquals("test.UserException", metadata.exceptionQualifiedName)
        assertEquals(1, metadata.declaredFields.size)
        assertEquals("tenantId", metadata.declaredFields.single().name)
        assertEquals("kotlin.Long", metadata.declaredFields.single().typeName)
        assertEquals("tenant", metadata.declaredFields.single().doc)
        assertEquals(1, metadata.items.size)
        assertEquals("INVALID_EMAIL", metadata.items.single().enumConstantName)
        assertEquals("InvalidEmail", metadata.items.single().exceptionSimpleName)
        assertEquals("INVALID_EMAIL", metadata.items.single().code)
        assertEquals("email", metadata.items.single().declaredFields.single().name)
        assertEquals("email field", metadata.items.single().declaredFields.single().doc)
        assertEquals(
            "test.UserError#INVALID_EMAIL",
            metadata.items.single().id
        )
        assertEquals(
            "test.UserError#INVALID_EMAIL::email",
            metadata.items.single().declaredFields.single().id
        )
        assertEquals(
            "INVALID_EMAIL",
            result.sourceIndex.anchorOf(metadata.items.single().id)?.symbolName
        )
    }

    @Test
    fun rejects_item_field_that_duplicates_shared_field() {
        val extractor = ErrorMetadataExtractor()
        val declaration = fakeErrorEnum(
            simpleName = "OrderError",
            qualifiedName = "test.OrderError",
            annotations = listOf(
                FakeAnnotation(
                    qualifiedName = ERROR_FAMILY,
                    attributes = mapOf("value" to "")
                ),
                FakeAnnotation(
                    qualifiedName = ERROR_FIELD,
                    attributes = mapOf(
                        "name" to "id",
                        "type" to FakeClass.simple("kotlin.Long"),
                        "list" to false,
                        "nullable" to false,
                        "doc" to ""
                    )
                )
            ),
            constants = listOf(
                FakeEnumConstant(
                    name = "NOT_FOUND",
                    comment = null,
                    annotations = listOf(
                        FakeAnnotation(
                            qualifiedName = ERROR_FIELD,
                            attributes = mapOf(
                                "name" to "id",
                                "type" to FakeClass.simple("kotlin.Long"),
                                "list" to false,
                                "nullable" to false,
                                "doc" to ""
                            )
                        )
                    )
                )
            )
        )

        assertThrows(MetaException::class.java) {
            extractor.extract(sequenceOf(declaration))
        }
    }

    @Test
    fun rejects_primitive_list_field() {
        val extractor = ErrorMetadataExtractor()
        val declaration = fakeErrorEnum(
            simpleName = "OrderError",
            qualifiedName = "test.OrderError",
            annotations = listOf(
                FakeAnnotation(
                    qualifiedName = ERROR_FAMILY,
                    attributes = mapOf("value" to "")
                ),
                FakeAnnotation(
                    qualifiedName = ERROR_FIELD,
                    attributes = mapOf(
                        "name" to "ids",
                        "type" to FakeClass.simple("int"),
                        "list" to true,
                        "nullable" to false,
                        "doc" to ""
                    )
                )
            ),
            constants = emptyList()
        )

        assertThrows(MetaException::class.java) {
            extractor.extract(sequenceOf(declaration))
        }
    }

    private fun fakeErrorEnum(
        simpleName: String,
        qualifiedName: String,
        annotations: List<LsiAnnotation>,
        constants: List<FakeEnumConstant>
    ): LsiClass {
        val owner = object : LsiClass {
            override val simpleName: String = simpleName
            override val qualifiedName: String = qualifiedName
            override val comment: String? = "enum doc"
            override val fields: List<LsiField> = emptyList()
            override val annotations: List<LsiAnnotation> = annotations
            override val isInterface: Boolean = false
            override val isClass: Boolean = false
            override val isEnum: Boolean = true
            override val isCollectionType: Boolean = false
            override val isPojo: Boolean = false
            override val superClasses: List<LsiClass> = emptyList()
            override val interfaces: List<LsiClass> = emptyList()
            override val methods: List<LsiMethod> = emptyList()
            override val enumConstants: List<LsiEnumConstant> = emptyList()
        }
        constants.forEach { it.declaringClassValue = owner }
        return object : LsiClass by owner {
            override val enumConstants: List<LsiEnumConstant> = constants
        }
    }

    private data class FakeAnnotation(
        override val qualifiedName: String?,
        override val attributes: Map<String, Any?>,
        override val annotations: List<LsiAnnotation> = emptyList()
    ) : LsiAnnotation {
        override val simpleName: String?
            get() = qualifiedName?.substringAfterLast('.')

        override fun getAttribute(name: String): Any? =
            attributes[name]

        override fun hasAttribute(name: String): Boolean =
            attributes.containsKey(name)
    }

    private data class FakeEnumConstant(
        override val name: String?,
        override val comment: String?,
        override val annotations: List<LsiAnnotation>,
        var declaringClassValue: LsiClass? = null
    ) : LsiEnumConstant {
        override val declaringClass: LsiClass?
            get() = declaringClassValue
    }

    private data class FakeClass(
        override val simpleName: String?,
        override val qualifiedName: String?
    ) : LsiClass {
        override val comment: String? = null
        override val fields: List<LsiField> = emptyList()
        override val annotations: List<LsiAnnotation> = emptyList()
        override val isInterface: Boolean = false
        override val isClass: Boolean = true
        override val isEnum: Boolean = false
        override val isCollectionType: Boolean = false
        override val isPojo: Boolean = false
        override val superClasses: List<LsiClass> = emptyList()
        override val interfaces: List<LsiClass> = emptyList()
        override val methods: List<LsiMethod> = emptyList()

        companion object {
            fun simple(qualifiedName: String): FakeClass =
                FakeClass(
                    simpleName = qualifiedName.substringAfterLast('.'),
                    qualifiedName = qualifiedName
                )
        }
    }
}
