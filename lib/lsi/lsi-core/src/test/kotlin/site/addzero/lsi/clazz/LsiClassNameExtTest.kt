package site.addzero.lsi.clazz

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.poet.LsiClassName

class LsiClassNameExtTest {

    @Test
    fun `toLsiClassName keeps nested simple names and only transforms tail`() {
        val lsiClass = fakeLsiClass(
            qualifiedName = "com.acme.Book.Store",
            simpleName = "Store",
            simpleNames = listOf("Book", "Store"),
        )

        val className = lsiClass.toLsiClassName(nameTransformer = { "${it}Draft" })

        assertEquals(
            LsiClassName(
                packageName = "com.acme",
                simpleNames = listOf("Book", "StoreDraft"),
                nullable = false,
            ),
            className,
        )
    }

    @Test
    fun `toLsiNestedClassName appends nested names after existing class chain`() {
        val lsiClass = fakeLsiClass(
            qualifiedName = "com.acme.Book.Store",
            simpleName = "Store",
            simpleNames = listOf("Book", "Store"),
        )

        val className = lsiClass.toLsiNestedClassName(namesTransformer = { names ->
            names + listOf("Producer", "Impl")
        })

        assertEquals(
            LsiClassName(
                packageName = "com.acme",
                simpleNames = listOf("Book", "Store", "Producer", "Impl"),
                nullable = false,
            ),
            className,
        )
    }

    private fun fakeLsiClass(
        qualifiedName: String,
        simpleName: String,
        simpleNames: List<String>,
    ): LsiClass =
        object : LsiClass {
            override val simpleName: String = simpleName
            override val qualifiedName: String = qualifiedName
            override val packageName: String = "com.acme"
            override val simpleNames: List<String> = simpleNames
            override val comment: String? = null
            override val fields: List<LsiField> = emptyList()
            override val annotations: List<LsiAnnotation> = emptyList()
            override val isInterface: Boolean = false
            override val isClass: Boolean = true
            override val isEnum: Boolean = false
            override val isCollectionType: Boolean = false
            override val isPojo: Boolean = false
            override val methods: List<LsiMethod> = emptyList()
            override val constructors: List<LsiMethod> = emptyList()
            override val superClasses: List<LsiClass> = emptyList()
            override val interfaces: List<LsiClass> = emptyList()
        }
}
