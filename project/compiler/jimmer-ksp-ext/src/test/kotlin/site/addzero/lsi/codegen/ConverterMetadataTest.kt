package site.addzero.lsi.codegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.isSemanticallySameType

class ConverterMetadataTest {

    @Test
    fun `to list metadata uses kotlin list carrier`() {
        val metadata = ConverterMetadata(
            sourceTypeName = LsiClassName.bestGuess("kotlin.String"),
            targetTypeName = LsiClassName.bestGuess("kotlin.Int")
        )

        val listMetadata = metadata.toListMetadata()

        assertEquals(
            LsiParameterizedTypeName(
                rawType = LsiClassName.bestGuess("kotlin.collections.List"),
                typeArguments = listOf(LsiClassName.bestGuess("kotlin.String"))
            ),
            listMetadata.sourceTypeName
        )
        assertEquals(
            LsiParameterizedTypeName(
                rawType = LsiClassName.bestGuess("kotlin.collections.List"),
                typeArguments = listOf(LsiClassName.bestGuess("kotlin.Int"))
            ),
            listMetadata.targetTypeName
        )
    }

    @Test
    fun `list metadata cannot be listified twice`() {
        val metadata = ConverterMetadata(
            sourceTypeName = LsiClassName.bestGuess("kotlin.String"),
            targetTypeName = LsiClassName.bestGuess("kotlin.Int")
        ).toListMetadata()

        assertThrows(IllegalStateException::class.java) {
            metadata.toListMetadata()
        }
    }

    @Test
    fun `list metadata keeps java kotlin collection alias compatibility`() {
        val listMetadata = ConverterMetadata(
            sourceTypeName = LsiClassName.bestGuess("kotlin.String"),
            targetTypeName = LsiClassName.bestGuess("kotlin.Int")
        ).toListMetadata()

        val javaList = LsiParameterizedTypeName(
            rawType = LsiClassName.bestGuess("java.util.List"),
            typeArguments = listOf(LsiClassName.bestGuess("kotlin.String"))
        )

        assertTrue(listMetadata.sourceTypeName.isSemanticallySameType(javaList))
    }
}
