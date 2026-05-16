package site.addzero.lsi.poet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LsiTypeNameCarrierNormalizationTest {

    @Test
    fun `simple collection names normalize to kotlin collection carriers`() {
        assertEquals("kotlin.collections.Iterable", "Iterable".normalizedLsiCollectionCarrierQualifiedName())
        assertEquals("kotlin.collections.Iterable", "MutableIterable".normalizedLsiCollectionCarrierQualifiedName())
        assertEquals("kotlin.collections.Collection", "Collection".normalizedLsiCollectionCarrierQualifiedName())
        assertEquals("kotlin.collections.List", "MutableList".normalizedLsiCollectionCarrierQualifiedName())
        assertEquals("kotlin.collections.List", "ArrayList".normalizedLsiCollectionCarrierQualifiedName())
        assertEquals("kotlin.collections.Set", "Set".normalizedLsiCollectionCarrierQualifiedName())
        assertEquals("kotlin.collections.Set", "HashSet".normalizedLsiCollectionCarrierQualifiedName())
        assertEquals("kotlin.collections.Map", "MutableMap".normalizedLsiCollectionCarrierQualifiedName())
        assertEquals("kotlin.collections.Map", "HashMap".normalizedLsiCollectionCarrierQualifiedName())
    }

    @Test
    fun `built in carrier helper recognizes primitives and collections only`() {
        assertEquals(LsiClassName.bestGuess("kotlin.Int"), "java.lang.Integer".toBuiltInLsiClassNameOrNull())
        assertEquals(LsiClassName.bestGuess("kotlin.String"), "String".toBuiltInLsiClassNameOrNull())
        assertEquals(LsiClassName.bestGuess("kotlin.collections.List"), "MutableList".toBuiltInLsiClassNameOrNull())
        assertEquals(LsiClassName.bestGuess("kotlin.collections.List"), "ArrayList".toBuiltInLsiClassNameOrNull())
        assertEquals(LsiClassName.bestGuess("kotlin.collections.Map"), "java.util.Map".toBuiltInLsiClassNameOrNull())
        assertEquals(LsiClassName.bestGuess("kotlin.collections.Map"), "HashMap".toBuiltInLsiClassNameOrNull())
        assertNull("demo.Customer".toBuiltInLsiClassNameOrNull())
        assertNull("T".toBuiltInLsiClassNameOrNull())
    }

    @Test
    fun `boxed primitive helper reuses shared primitive metadata`() {
        assertEquals(LsiClassName.bestGuess("java.lang.Integer"), "Int".toBoxedPrimitiveLsiClassNameOrNull())
        assertEquals(LsiClassName.bestGuess("java.lang.Character"), "kotlin.Char".toBoxedPrimitiveLsiClassNameOrNull())
        assertEquals(LsiClassName.bestGuess("java.lang.Boolean"), "boolean".toBoxedPrimitiveLsiClassNameOrNull())
        assertNull("String".toBoxedPrimitiveLsiClassNameOrNull())
    }

    @Test
    fun `carrier predicates normalize nullability and java kotlin aliases`() {
        assertEquals("kotlin.Any", "kotlin.Any?".normalizedLsiCarrierLookupName())
        assertEquals("kotlin.collections.List", "java.util.List<demo.Book>!".normalizedLsiCarrierLookupName())
        assertEquals("kotlin.Unit", "void".normalizedLsiCarrierLookupName())
        assertEquals("kotlin.Nothing", "Nothing?".normalizedLsiCarrierLookupName())

        assertTrue("java.lang.Boolean".isLsiBooleanLikeQualifiedName())
        assertTrue("java.lang.Integer".isLsiPrimitiveLikeQualifiedName())
        assertTrue("kotlin.Long?".isLsiPrimitiveLikeQualifiedName())
        assertTrue("char".isLsiPrimitiveLikeQualifiedName())
        assertTrue("kotlin.Unit?".isLsiVoidLikeQualifiedName())
        assertTrue("Object".isLsiObjectLikeQualifiedName())
        assertTrue("java.util.LinkedList<demo.Book>".isLsiCollectionLikeQualifiedName())
        assertTrue("kotlin.collections.MutableMap?".isLsiCollectionLikeQualifiedName())
        assertTrue("HashSet".isLsiCollectionLikeQualifiedName())
        assertTrue("kotlin.Nothing".isLsiNoValueLikeQualifiedName())
        assertTrue("void".isLsiNoValueLikeQualifiedName())
        assertFalse("kotlin.Boolean".isLsiNoValueLikeQualifiedName())
        assertFalse("java.lang.String".isLsiPrimitiveLikeQualifiedName())
        assertFalse("demo.Book".isLsiCollectionLikeQualifiedName())
        assertFalse("demo.Book".isLsiObjectLikeQualifiedName())
    }
}
