package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.dto.compiler.DtoFile

class JimmerDtoFailureTranslationTest {

    @Test
    fun `dto ast failure targets enable dto generation`() {
        val feature = DtoFeature()
        val failure = DtoAstException(
            DtoFile("/source/book.dto", "book.dto", "BookView {}"),
            1,
            0,
            "Invalid DTO",
        )

        val translation = assertNotNull(feature.translateFailure(failure))

        assertEquals(failure.message, translation.message)
        assertEquals("org.babyfish.jimmer.sql.EnableDtoGeneration", translation.annotationTypeName)
        assertTrue(translation.rethrowWhenTargetMissing)
        assertNull(feature.translateFailure(IllegalStateException("unrelated")))
    }
}
