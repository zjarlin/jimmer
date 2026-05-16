package site.addzero.lsi.jimmer.tuple.metadata.extractor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.jimmer.TYPED_TUPLE
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleConstructorConstructionMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleSetterConstructionMetadata
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.method.LsiParameter
import site.addzero.lsi.resolver.LsiResolver
import site.addzero.lsi.type.LsiType

class TypedTupleMetadataExtractorTest {

    @Test
    fun extracts_constructor_construction_from_data_class() {
        val extractor = TypedTupleMetadataExtractor()
        val declaration = fakeTypedTupleClass(
            qualifiedName = "test.BookTuple",
            isData = true,
            fields = listOf(
                FakeField(name = "id", type = FakeType(qualifiedName = "kotlin.Long")),
                FakeField(name = "name", type = FakeType(qualifiedName = "kotlin.String")),
            ),
            constructors = listOf(
                FakeMethod.constructor(
                    FakeParameter("id", FakeType(qualifiedName = "kotlin.Long")),
                    FakeParameter("name", FakeType(qualifiedName = "kotlin.String")),
                )
            ),
        )

        val result = extractor.extract(sequenceOf(declaration))

        val metadata = result.types.single()
        assertEquals(
            TypedTupleConstructorConstructionMetadata(argumentPropertyIndices = listOf(0, 1)),
            metadata.construction,
        )
        assertEquals("id", metadata.properties[0].name)
        assertEquals("name", metadata.properties[1].name)
    }

    @Test
    fun extracts_setter_construction_from_java_bean_tuple() {
        val extractor = TypedTupleMetadataExtractor()
        val declaration = fakeTypedTupleClass(
            qualifiedName = "test.BookTuple",
            isData = false,
            fields = listOf(
                FakeField(name = "book", type = FakeType(qualifiedName = "test.Book"), isVar = true),
                FakeField(name = "authorCount", type = FakeType(qualifiedName = "kotlin.Long"), isVar = true),
            ),
            constructors = emptyList(),
        )

        val metadata = extractor.extract(sequenceOf(declaration)).types.single()

        assertEquals(
            TypedTupleSetterConstructionMetadata(
                setterNames = listOf("setBook", "setAuthorCount"),
            ),
            metadata.construction,
        )
    }

    @Test
    fun extracts_constructor_construction_from_lombok_data_final_fields() {
        val extractor = TypedTupleMetadataExtractor()
        val declaration = fakeTypedTupleClass(
            qualifiedName = "test.BookTuple",
            isData = false,
            annotations = listOf(
                FakeAnnotation(qualifiedName = TYPED_TUPLE),
                FakeAnnotation(qualifiedName = "lombok.Data"),
            ),
            fields = listOf(
                FakeField(name = "book", type = FakeType(qualifiedName = "test.Book"), isVar = false),
                FakeField(name = "authorCount", type = FakeType(qualifiedName = "kotlin.Long"), isVar = false),
            ),
            constructors = emptyList(),
        )

        val metadata = extractor.extract(sequenceOf(declaration)).types.single()

        assertEquals(
            TypedTupleConstructorConstructionMetadata(argumentPropertyIndices = listOf(0, 1)),
            metadata.construction,
        )
    }

    @Test
    fun rejects_lombok_data_with_mixed_finality() {
        val extractor = TypedTupleMetadataExtractor()
        val declaration = fakeTypedTupleClass(
            qualifiedName = "test.BookTuple",
            isData = false,
            annotations = listOf(
                FakeAnnotation(qualifiedName = TYPED_TUPLE),
                FakeAnnotation(qualifiedName = "lombok.Data"),
            ),
            fields = listOf(
                FakeField(name = "book", type = FakeType(qualifiedName = "test.Book"), isVar = false),
                FakeField(name = "authorCount", type = FakeType(qualifiedName = "kotlin.Long"), isVar = true),
            ),
        )

        assertThrows(MetaException::class.java) {
            extractor.extract(sequenceOf(declaration))
        }
    }

    @Test
    fun collects_current_round_and_delayed_tuple_types_once() {
        val extractor = TypedTupleMetadataExtractor()
        val currentTuple = fakeTypedTupleClass(
            qualifiedName = "test.CurrentTuple",
            isData = true,
            fields = listOf(FakeField(name = "id", type = FakeType(qualifiedName = "kotlin.Long"))),
            constructors = listOf(
                FakeMethod.constructor(
                    FakeParameter("id", FakeType(qualifiedName = "kotlin.Long")),
                )
            ),
        )
        val delayedTuple = fakeTypedTupleClass(
            qualifiedName = "test.DelayedTuple",
            isData = false,
            fields = listOf(FakeField(name = "name", type = FakeType(qualifiedName = "kotlin.String"), isVar = true)),
        )
        val resolver = FakeResolver(
            annotatedClasses = listOf(currentTuple, delayedTuple),
            classesByQualifiedName = mapOf(
                currentTuple.qualifiedName!! to currentTuple,
                delayedTuple.qualifiedName!! to delayedTuple,
            ),
        )

        val extraction = extractor.collectRoundTypes(
            resolver = resolver,
            delayedTypeNames = listOf("test.DelayedTuple"),
        )

        assertEquals(
            listOf("test.CurrentTuple", "test.DelayedTuple"),
            extraction.types.map { it.sourceQualifiedName },
        )
    }

    private fun fakeTypedTupleClass(
        qualifiedName: String,
        isData: Boolean,
        fields: List<FakeField>,
        constructors: List<FakeMethod> = emptyList(),
        annotations: List<LsiAnnotation> = listOf(FakeAnnotation(qualifiedName = TYPED_TUPLE)),
    ): LsiClass {
        val simpleName = qualifiedName.substringAfterLast('.')
        lateinit var owner: LsiClass
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
            override val isTopLevel: Boolean = true
            override val isData: Boolean = isData
            override val typeParameterCount: Int = 0
            override val superClasses: List<LsiClass> = listOf(fakePlainClass("kotlin.Any"))
            override val interfaces: List<LsiClass> = emptyList()
            override val methods: List<LsiMethod> = constructors
            override val constructors: List<LsiMethod> = constructors
            override val primaryConstructor: LsiMethod? = constructors.firstOrNull()
        }
        fields.forEach { it.declaringClassValue = owner }
        constructors.forEach { it.declaringClassValue = owner }
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
        override val attributes: Map<String, Any?> = emptyMap(),
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
        override val isVar: Boolean = false,
        var declaringClassValue: LsiClass? = null,
    ) : LsiField {
        override val typeName: String?
            get() = type?.qualifiedName
        override val comment: String? = null
        override val annotations: List<LsiAnnotation> = emptyList()
        override val isStatic: Boolean = false
        override val isConstant: Boolean = false
        override val isEnum: Boolean = false
        override val isLateInit: Boolean = false
        override val isCollectionType: Boolean = false
        override val defaultValue: String? = null
        override val columnName: String? = null
        override val declaringClass: LsiClass?
            get() = declaringClassValue
        override val fieldTypeClass: LsiClass? = type?.lsiClass
        override val isNestedObject: Boolean = false
        override val children: List<LsiField> = emptyList()
    }

    private data class FakeType(
        override val qualifiedName: String?,
        override val simpleName: String? = qualifiedName?.substringAfterLast('.'),
        override val presentableText: String? = qualifiedName,
        override val isNullable: Boolean = false,
        override val isPrimitive: Boolean = false,
        override val isArray: Boolean = false,
        override val typeParameters: List<LsiType> = emptyList(),
        override val componentType: LsiType? = null,
        override val lsiClass: LsiClass? = null,
    ) : LsiType {
        override val annotations: List<LsiAnnotation> = emptyList()
        override val isCollectionType: Boolean = false
    }

    private data class FakeMethod(
        override val name: String?,
        override val isConstructor: Boolean,
        override val isPrivate: Boolean,
        override val parameters: List<LsiParameter>,
        var declaringClassValue: LsiClass? = null,
    ) : LsiMethod {
        override val returnType: LsiType? = null
        override val returnTypeName: String? = null
        override val comment: String? = null
        override val annotations: List<LsiAnnotation> = emptyList()
        override val isStatic: Boolean = false
        override val isAbstract: Boolean = false
        override val isPublic: Boolean = !isPrivate
        override val isProtected: Boolean = false
        override val isInternal: Boolean = false
        override val isOpen: Boolean = false
        override val declaringClass: LsiClass?
            get() = declaringClassValue

        companion object {
            fun constructor(vararg parameters: FakeParameter): FakeMethod =
                FakeMethod(
                    name = "<init>",
                    isConstructor = true,
                    isPrivate = false,
                    parameters = parameters.toList(),
                )
        }
    }

    private data class FakeParameter(
        override val name: String?,
        override val type: LsiType?,
    ) : LsiParameter {
        override val typeName: String?
            get() = type?.qualifiedName
        override val annotations: List<LsiAnnotation> = emptyList()
        override val hasDefault: Boolean = false
    }

    private data class FakeResolver(
        val annotatedClasses: List<LsiClass>,
        val classesByQualifiedName: Map<String, LsiClass>,
    ) : LsiResolver {
        override fun allClasses(): Sequence<LsiClass> =
            classesByQualifiedName.values.asSequence()

        override fun newClasses(): Sequence<LsiClass> =
            annotatedClasses.asSequence()

        override fun findClassesAnnotatedWith(annotationQualifiedName: String): Sequence<LsiClass> =
            if (annotationQualifiedName == TYPED_TUPLE) {
                annotatedClasses.asSequence()
            } else {
                emptySequence()
            }

        override fun findClassByQualifiedName(qualifiedName: String): LsiClass? =
            classesByQualifiedName[qualifiedName]
    }
}
