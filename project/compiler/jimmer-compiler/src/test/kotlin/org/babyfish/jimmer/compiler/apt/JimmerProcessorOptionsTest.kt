package org.babyfish.jimmer.compiler.apt

import kotlin.test.Test
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
}
