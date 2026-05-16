package site.addzero.lsi.jimmer.transactional.metadata.extractor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.method.LsiParameter
import site.addzero.lsi.type.LsiType

class TxMetadataExtractorTest {

    @Test
    fun extracts_tx_metadata_from_service_class() {
        val extractor = TxMetadataExtractor()
        val kSqlClientType = FakeType(
            qualifiedName = "org.babyfish.jimmer.sql.kt.KSqlClient",
            lsiClassValue = fakePlainClass("org.babyfish.jimmer.sql.kt.KSqlClient"),
        )
        val declaration = fakeTxClass(
            qualifiedName = "test.BookService",
            annotations = listOf(
                FakeAnnotation(
                    qualifiedName = "org.babyfish.jimmer.sql.transaction.Tx",
                    attributes = mapOf("value" to "org.babyfish.jimmer.sql.transaction.Propagation.REQUIRED"),
                ),
                FakeAnnotation(
                    qualifiedName = "org.babyfish.jimmer.sql.transaction.TargetAnnotation",
                    attributes = mapOf("value" to fakePlainClass("test.MyTx")),
                ),
                FakeAnnotation(
                    qualifiedName = "kotlin.Deprecated",
                    attributes = mapOf("message" to "type"),
                ),
            ),
            fields = listOf(
                FakeField(
                    name = "sqlClient",
                    type = kSqlClientType,
                    isPrivate = false,
                ),
            ),
            primaryConstructor = FakeMethod(
                name = "<init>",
                returnType = null,
                annotations = emptyList(),
                isConstructor = true,
                parameters = listOf(
                    FakeParameter(
                        name = "sqlClient",
                        type = kSqlClientType,
                    )
                ),
            ),
            methods = listOf(
                FakeMethod(
                    name = "findBook",
                    returnType = FakeType(qualifiedName = "kotlin.String"),
                    annotations = listOf(
                        FakeAnnotation(
                            qualifiedName = "kotlin.jvm.JvmName",
                            attributes = mapOf("name" to "findBookTx"),
                        )
                    ),
                    isPublic = true,
                    isOpen = true,
                    parameters = listOf(
                        FakeParameter(
                            name = "id",
                            type = FakeType(qualifiedName = "kotlin.Long"),
                        )
                    ),
                ),
                FakeMethod(
                    name = "saveBook",
                    returnType = FakeType(qualifiedName = "kotlin.Unit"),
                    annotations = listOf(
                        FakeAnnotation(
                            qualifiedName = "org.babyfish.jimmer.sql.transaction.Tx",
                            attributes = mapOf("value" to "org.babyfish.jimmer.sql.transaction.Propagation.REQUIRES_NEW"),
                        ),
                        FakeAnnotation(
                            qualifiedName = "kotlin.Suppress",
                            attributes = mapOf("names" to listOf("UNCHECKED_CAST")),
                        ),
                    ),
                    isPublic = true,
                    isOpen = true,
                    parameters = emptyList(),
                ),
                FakeMethod(
                    name = "helper",
                    returnType = FakeType(qualifiedName = "kotlin.Unit"),
                    annotations = emptyList(),
                    isPublic = false,
                    isOpen = true,
                    parameters = emptyList(),
                ),
            ),
        )

        val result = extractor.extract(sequenceOf(declaration))

        assertEquals(1, result.types.size)
        val metadata = result.types.single()
        assertEquals("test.BookService", metadata.id)
        assertEquals("BookServiceTx", metadata.generatedSimpleName)
        assertEquals("test.BookServiceTx", metadata.generatedQualifiedName)
        assertEquals("sqlClient", metadata.sqlClientPropertyName)
        assertEquals("test.MyTx", metadata.targetAnnotationTypeQualifiedName)
        assertEquals(1, metadata.copiedAnnotations.size)
        assertEquals("kotlin.Deprecated", metadata.copiedAnnotations.single().qualifiedName)
        assertEquals(2, metadata.methods.size)
        assertEquals("findBook", metadata.methods[0].name)
        assertEquals("REQUIRED", metadata.methods[0].propagation)
        assertEquals("kotlin.jvm.JvmName", metadata.methods[0].annotations.single().qualifiedName)
        assertEquals("saveBook", metadata.methods[1].name)
        assertEquals("REQUIRES_NEW", metadata.methods[1].propagation)
        assertEquals("kotlin.Suppress", metadata.methods[1].annotations.single().qualifiedName)
        assertEquals(
            "test.BookService#findBook(kotlin.Long)",
            metadata.methods[0].id,
        )
        assertEquals(
            "findBook",
            result.sourceIndex.anchorOf(metadata.methods[0].id)?.symbolName,
        )
    }

    @Test
    fun rejects_private_sql_client_field() {
        val extractor = TxMetadataExtractor()
        val declaration = fakeTxClass(
            qualifiedName = "test.BookService",
            annotations = listOf(
                FakeAnnotation(
                    qualifiedName = "org.babyfish.jimmer.sql.transaction.Tx",
                    attributes = mapOf("value" to "org.babyfish.jimmer.sql.transaction.Propagation.REQUIRED"),
                ),
            ),
            fields = listOf(
                FakeField(
                    name = "sqlClient",
                    type = FakeType(
                        qualifiedName = "org.babyfish.jimmer.sql.kt.KSqlClient",
                        lsiClassValue = fakePlainClass("org.babyfish.jimmer.sql.kt.KSqlClient"),
                    ),
                    isPrivate = true,
                ),
            ),
            primaryConstructor = null,
            methods = emptyList(),
        )

        assertThrows(MetaException::class.java) {
            extractor.extract(sequenceOf(declaration))
        }
    }

    private fun fakeTxClass(
        qualifiedName: String,
        annotations: List<LsiAnnotation>,
        fields: List<FakeField>,
        primaryConstructor: FakeMethod?,
        methods: List<FakeMethod>,
    ): LsiClass {
        val simpleName = qualifiedName.substringAfterLast('.')
        lateinit var owner: LsiClass
        val allConstructors = buildList {
            primaryConstructor?.let(::add)
        }
        owner = object : LsiClass {
            override val simpleName: String = simpleName
            override val qualifiedName: String = qualifiedName
            override val comment: String? = null
            override val fields: List<LsiField> = fields
            override val annotations: List<LsiAnnotation> = annotations
            override val isInterface: Boolean = false
            override val isClass: Boolean = true
            override val isEnum: Boolean = false
            override val isCollectionType: Boolean = false
            override val isPojo: Boolean = false
            override val superClasses: List<LsiClass> = listOf(fakePlainClass("kotlin.Any"))
            override val interfaces: List<LsiClass> = emptyList()
            override val methods: List<LsiMethod> = methods
            override val isTopLevel: Boolean = true
            override val isData: Boolean = false
            override val isSealed: Boolean = false
            override val isOpen: Boolean = true
            override val isFinal: Boolean = false
            override val isAbstract: Boolean = false
            override val typeParameterCount: Int = 0
            override val primaryConstructor: LsiMethod? = primaryConstructor
            override val constructors: List<LsiMethod> = allConstructors
        }
        fields.forEach { it.declaringClassValue = owner }
        allConstructors.forEach { it.declaringClassValue = owner }
        methods.forEach { it.declaringClassValue = owner }
        return owner
    }

    private fun fakePlainClass(
        qualifiedName: String,
    ): LsiClass =
        object : LsiClass {
            override val simpleName: String = qualifiedName.substringAfterLast('.')
            override val qualifiedName: String = qualifiedName
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
        }

    private data class FakeAnnotation(
        override val qualifiedName: String?,
        override val attributes: Map<String, Any?>,
        override val annotations: List<LsiAnnotation> = emptyList(),
    ) : LsiAnnotation {
        override val simpleName: String?
            get() = qualifiedName?.substringAfterLast('.')

        override fun getAttribute(name: String): Any? =
            attributes[name]

        override fun hasAttribute(name: String): Boolean =
            attributes.containsKey(name)
    }

    private data class FakeField(
        override val name: String?,
        override val type: LsiType?,
        override val annotations: List<LsiAnnotation> = emptyList(),
        override val isStatic: Boolean = false,
        override val isPrivate: Boolean = false,
        var declaringClassValue: LsiClass? = null,
    ) : LsiField {
        override val typeName: String?
            get() = type?.qualifiedName
        override val comment: String? = null
        override val isPublic: Boolean
            get() = !isPrivate
        override val isConstant: Boolean = false
        override val isEnum: Boolean = false
        override val isVar: Boolean = false
        override val isLateInit: Boolean = false
        override val isCollectionType: Boolean = false
        override val defaultValue: String? = null
        override val columnName: String? = null
        override val declaringClass: LsiClass?
            get() = declaringClassValue
        override val fieldTypeClass: LsiClass?
            get() = type?.lsiClass
        override val isNestedObject: Boolean = false
        override val children: List<LsiField> = emptyList()
    }

    private data class FakeMethod(
        override val name: String?,
        override val returnType: LsiType?,
        override val annotations: List<LsiAnnotation>,
        override val isStatic: Boolean = false,
        override val isAbstract: Boolean = false,
        override val isPublic: Boolean = true,
        override val isProtected: Boolean = false,
        override val isInternal: Boolean = false,
        override val isPrivate: Boolean = false,
        override val isOpen: Boolean = false,
        override val isConstructor: Boolean = false,
        override val parameters: List<LsiParameter>,
        var declaringClassValue: LsiClass? = null,
    ) : LsiMethod {
        override val returnTypeName: String?
            get() = returnType?.qualifiedName
        override val comment: String? = null
        override val typeParameterCount: Int = 0
        override val declaringClass: LsiClass?
            get() = declaringClassValue
    }

    private data class FakeParameter(
        override val name: String?,
        override val type: LsiType?,
        override val annotations: List<LsiAnnotation> = emptyList(),
    ) : LsiParameter {
        override val typeName: String?
            get() = type?.qualifiedName
    }

    private data class FakeType(
        override val qualifiedName: String?,
        val lsiClassValue: LsiClass? = null,
    ) : LsiType {
        override val simpleName: String?
            get() = qualifiedName?.substringAfterLast('.')
        override val presentableText: String?
            get() = qualifiedName
        override val annotations: List<LsiAnnotation> = emptyList()
        override val isCollectionType: Boolean = false
        override val typeParameters: List<LsiType> = emptyList()
        override val isPrimitive: Boolean = false
        override val componentType: LsiType? = null
        override val isArray: Boolean = false
        override val lsiClass: LsiClass?
            get() = lsiClassValue
    }
}
