package site.addzero.lsi.poet

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LsiTypeNameSemanticTypeTest {

    @Test
    fun `list aliases are semantically equal`() {
        val javaList = LsiParameterizedTypeName(
            rawType = LsiClassName.bestGuess("java.util.List"),
            typeArguments = listOf(LsiClassName.bestGuess("java.lang.String"))
        )
        val kotlinList = LsiParameterizedTypeName(
            rawType = LsiClassName.bestGuess("kotlin.collections.List"),
            typeArguments = listOf(LsiClassName.bestGuess("java.lang.String"))
        )

        assertTrue(javaList.isSemanticallySameType(kotlinList))
    }

    @Test
    fun `map aliases stay semantically equal through nested generics`() {
        val javaMap = LsiParameterizedTypeName(
            rawType = LsiClassName.bestGuess("java.util.Map"),
            typeArguments = listOf(
                LsiClassName.bestGuess("java.lang.String"),
                LsiParameterizedTypeName(
                    rawType = LsiClassName.bestGuess("java.util.List"),
                    typeArguments = listOf(LsiClassName.bestGuess("kotlin.Int"))
                )
            )
        )
        val kotlinMap = LsiParameterizedTypeName(
            rawType = LsiClassName.bestGuess("kotlin.collections.MutableMap"),
            typeArguments = listOf(
                LsiClassName.bestGuess("java.lang.String"),
                LsiParameterizedTypeName(
                    rawType = LsiClassName.bestGuess("kotlin.collections.MutableList"),
                    typeArguments = listOf(LsiClassName.bestGuess("kotlin.Int"))
                )
            )
        )

        assertTrue(javaMap.isSemanticallySameType(kotlinMap))
    }

    @Test
    fun `nullable difference is not ignored`() {
        val nullableList = LsiParameterizedTypeName(
            rawType = LsiClassName.bestGuess("java.util.List"),
            typeArguments = listOf(LsiClassName.bestGuess("java.lang.String")),
            nullable = true
        )
        val nonNullList = LsiParameterizedTypeName(
            rawType = LsiClassName.bestGuess("kotlin.collections.List"),
            typeArguments = listOf(LsiClassName.bestGuess("java.lang.String"))
        )

        assertFalse(nullableList.isSemanticallySameType(nonNullList))
    }

    @Test
    fun `forbidden collection aliases resolve to preferred kotlin carriers`() {
        assertEquals(
            "kotlin.collections.List",
            "java.util.LinkedList".preferredLsiCollectionQualifiedName(),
        )
        assertEquals(
            "kotlin.collections.Map",
            "java.util.TreeMap".preferredLsiCollectionQualifiedName(),
        )
        assertEquals(
            "kotlin.collections.Set",
            "kotlin.collections.HashSet".preferredLsiCollectionQualifiedName(),
        )
    }

    @Test
    fun `preferred and mutable collection names are not rewritten`() {
        assertNull("kotlin.collections.List".preferredLsiCollectionQualifiedName())
        assertNull("kotlin.collections.MutableList".preferredLsiCollectionQualifiedName())
        assertNull("kotlin.String".preferredLsiCollectionQualifiedName())
    }

    @Test
    fun `strict immutable list and map helpers preserve immutable prop validation semantics`() {
        assertTrue("java.util.List".isLsiImmutableListQualifiedName())
        assertTrue("kotlin.collections.List".isLsiImmutableListQualifiedName())
        assertFalse("kotlin.collections.MutableList".isLsiImmutableListQualifiedName())
        assertTrue("java.util.SortedMap".isLsiMapQualifiedName())
        assertTrue("kotlin.collections.MutableMap".isLsiMapQualifiedName())
        assertFalse("java.util.List".isLsiMapQualifiedName())
    }

    @Test
    fun `collection carrier normalization keeps shared generic parsing stable`() {
        assertEquals(
            "kotlin.collections.Iterable",
            "kotlin.collections.MutableIterable".normalizedLsiCollectionCarrierQualifiedName(),
        )
        assertEquals(
            "kotlin.collections.Collection",
            "kotlin.collections.MutableCollection".normalizedLsiCollectionCarrierQualifiedName(),
        )
        assertEquals(
            "kotlin.collections.Map",
            "java.util.LinkedHashMap".normalizedLsiCollectionCarrierQualifiedName(),
        )
        assertEquals(
            "kotlin.String",
            "kotlin.String".normalizedLsiCollectionCarrierQualifiedName(),
        )
    }

    @Test
    fun `general carrier normalization unifies primitive boxed and common runtime names`() {
        assertEquals(
            "kotlin.Int",
            "java.lang.Integer".normalizedLsiCarrierQualifiedName(),
        )
        assertEquals(
            "kotlin.Boolean",
            "boolean".normalizedLsiCarrierQualifiedName(),
        )
        assertEquals(
            "kotlin.String",
            "String".normalizedLsiCarrierQualifiedName(),
        )
        assertEquals(
            "kotlin.Any",
            "java.lang.Object".normalizedLsiCarrierQualifiedName(),
        )
        assertEquals(
            "kotlin.Unit",
            "void".normalizedLsiCarrierQualifiedName(),
        )
    }
}
