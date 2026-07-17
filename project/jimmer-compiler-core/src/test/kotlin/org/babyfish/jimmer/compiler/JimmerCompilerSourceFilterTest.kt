package org.babyfish.jimmer.compiler

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JimmerCompilerSourceFilterTest {

    @Test
    fun `include prefixes constrain sources before excludes`() {
        val filter = JimmerCompilerSourceFilter.from(
            mapOf(
                "jimmer.source.includes" to " demo.api, demo.model ;demo.api ",
                "jimmer.source.excludes" to "demo.model.internal",
            )
        )

        assertTrue(filter.accepts("demo.api.BookService"))
        assertTrue(filter.accepts("demo.model.Book"))
        assertFalse(filter.accepts("demo.model.internal.Secret"))
        assertFalse(filter.accepts("other.External"))
    }
}
