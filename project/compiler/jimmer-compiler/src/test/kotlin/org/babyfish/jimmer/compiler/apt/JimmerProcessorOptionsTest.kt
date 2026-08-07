package org.babyfish.jimmer.compiler.apt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JimmerProcessorOptionsTest {

    @Test
    fun `exposes compiler and ddl options`() {
        val options = JimmerProcessor().supportedOptions

        assertTrue("jimmer.dto.fieldVisibility" in options)
        assertTrue("jimmer.client.checkedException" in options)
        assertTrue("jimmer.entry.tables" in options)
        assertTrue("jimmerDdl.enabled" in options)
    }

    @Test
    fun `exposes annotation types declared by features`() {
        assertEquals(
            sortedSetOf(
                "org.babyfish.jimmer.Immutable",
                "org.babyfish.jimmer.client.EnableImplicitApi",
                "org.babyfish.jimmer.client.ExportDoc",
                "org.babyfish.jimmer.client.meta.Api",
                "org.babyfish.jimmer.error.ErrorFamily",
                "org.babyfish.jimmer.internal.GeneratedBy",
                "org.babyfish.jimmer.sql.Embeddable",
                "org.babyfish.jimmer.sql.EnableDtoGeneration",
                "org.babyfish.jimmer.sql.Entity",
                "org.babyfish.jimmer.sql.MappedSuperclass",
                "org.babyfish.jimmer.sql.TypedTuple",
                "org.babyfish.jimmer.sql.transaction.Tx",
                "org.springframework.web.bind.annotation.RestController",
            ),
            JimmerProcessor().supportedAnnotationTypes,
        )
    }
}
