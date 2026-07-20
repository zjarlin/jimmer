package org.babyfish.jimmer.compiler.render.apt

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType

class LsiJavaPoetTest {

    @Test
    fun `renders primitive representation without losing boxing`() {
        val rawType = LsiPrimitiveType(LsiPrimitiveKind.INT)
        val boxedType = rawType.copy(boxed = true)

        assertEquals("int", rawType.toJavaTypeName().toString())
        assertEquals("java.lang.Integer", boxedType.toJavaTypeName().toString())
    }
}
