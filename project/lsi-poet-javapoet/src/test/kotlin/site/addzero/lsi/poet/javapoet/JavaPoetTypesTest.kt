package site.addzero.lsi.poet.javapoet

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType

class JavaPoetTypesTest {

    @Test
    fun `rejects function types without guessing a JVM representation`() {
        val functionType = LsiFunctionType(
            returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
            parameterTypes = listOf(LsiPrimitiveType(LsiPrimitiveKind.INT)),
            suspending = true,
        )

        val exception = assertFailsWith<IllegalStateException> {
            functionType.toJavaTypeName()
        }

        assertTrue(requireNotNull(exception.message).contains("without an explicit JVM ABI"))
    }
}
