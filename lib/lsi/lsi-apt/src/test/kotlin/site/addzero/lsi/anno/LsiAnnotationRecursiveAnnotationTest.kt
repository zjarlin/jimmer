package site.addzero.lsi.anno

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LsiAnnotationRecursiveAnnotationTest {

    @Test
    fun `find recursive annotation through meta annotations`() {
        val target = fakeAnnotation("test.Target")
        val nested = fakeAnnotation("test.Nested", annotations = listOf(target))
        val found = listOf(nested).recursiveAnnotation("test.Target")
        assertNotNull(found)
        assertEquals("test.Target", found?.qualifiedName)
    }

    @Test
    fun `fail when recursive annotation resolves to multiple paths`() {
        val direct = fakeAnnotation("test.Target")
        val nested = fakeAnnotation("test.Nested", annotations = listOf(fakeAnnotation("test.Target")))
        val ex = assertThrows(IllegalStateException::class.java) {
            listOf(direct, nested).recursiveAnnotation("test.Target")
        }
        assertEquals(
            "Conflict annotation \"@test.Target\" one is declared directly and the other one is declared as nest annotation of [test.Nested]",
            ex.message
        )
    }

    private fun fakeAnnotation(
        qualifiedName: String,
        annotations: List<LsiAnnotation> = emptyList(),
    ): LsiAnnotation =
        object : LsiAnnotation {
            override val qualifiedName: String = qualifiedName

            override val simpleName: String = qualifiedName.substringAfterLast('.')

            override val attributes: Map<String, Any?> = emptyMap()

            override fun getAttribute(name: String): Any? = attributes[name]

            override fun hasAttribute(name: String): Boolean = attributes.containsKey(name)

            override val annotations: List<LsiAnnotation> = annotations
        }
}
