package site.addzero.lsi.jimmer.client.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.type.LsiType
import java.io.StringReader
import java.util.Properties

class ExportDocResourceGeneratorTest {

    @Test
    fun generates_export_doc_resource_from_lsi_declarations() {
        val generator = ExportDocResourceGenerator()

        val artifact = generator.generate(
            listOf(
                FakeClass(
                    qualifiedName = "test.Book",
                    comment = "Book doc",
                    fields = listOf(
                        FakeField(name = "name", comment = "Name doc"),
                        FakeField(name = "IGNORED_STATIC", comment = "ignored", isStatic = true),
                        FakeField(name = "IGNORED_CONST", comment = "ignored", isConstant = true),
                    ),
                )
            )
        )

        assertEquals(ExportDocResourceGenerator.RESOURCE_PATH, artifact.path)
        assertTrue(artifact.content.contains(ExportDocResourceGenerator.RESOURCE_COMMENT))

        val properties = Properties()
        StringReader(artifact.content).use(properties::load)
        assertEquals("Book doc", properties["test.Book"])
        assertEquals("Name doc", properties["test.Book.name"])
        assertTrue("test.Book.IGNORED_STATIC" !in properties)
        assertTrue("test.Book.IGNORED_CONST" !in properties)
    }

    private data class FakeClass(
        override val qualifiedName: String,
        override val comment: String?,
        override val fields: List<LsiField>,
    ) : LsiClass {
        override val simpleName: String = qualifiedName.substringAfterLast('.')
        override val annotations: List<LsiAnnotation> = emptyList()
        override val isInterface: Boolean = false
        override val isClass: Boolean = true
        override val isEnum: Boolean = false
        override val isCollectionType: Boolean = false
        override val isPojo: Boolean = false
        override val packageAnnotations: List<LsiAnnotation> = emptyList()
        override val superClasses: List<LsiClass> = emptyList()
        override val interfaces: List<LsiClass> = emptyList()
        override val methods: List<LsiMethod> = emptyList()
    }

    private data class FakeField(
        override val name: String,
        override val comment: String?,
        override val isStatic: Boolean = false,
        override val isConstant: Boolean = false,
    ) : LsiField {
        override val type: LsiType? = null
        override val typeName: String? = null
        override val annotations: List<LsiAnnotation> = emptyList()
        override val isEnum: Boolean = false
        override val isVar: Boolean = false
        override val isLateInit: Boolean = false
        override val isCollectionType: Boolean = false
        override val defaultValue: String? = null
        override val columnName: String? = null
        override val declaringClass: LsiClass? = null
        override val fieldTypeClass: LsiClass? = null
        override val isNestedObject: Boolean = false
        override val children: List<LsiField> = emptyList()
    }
}
