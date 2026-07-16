package org.babyfish.jimmer.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JimmerCompilerFeatureGraphTest {

    @Test
    fun `依赖图按确定顺序排列`() {
        val client = feature("client", "dto", "error")
        val error = feature("error")
        val immutable = feature("immutable")
        val dto = feature("dto", "immutable")

        val sorted = JimmerCompilerFeatureGraph.sort(listOf(client, error, immutable, dto))

        assertEquals(
            listOf("error", "immutable", "dto", "client"),
            sorted.map { provider -> provider.descriptor.id }
        )
    }

    @Test
    fun `重复功能标识直接失败`() {
        val exception = assertFailsWith<DuplicateCompilerFeatureException> {
            JimmerCompilerFeatureGraph.sort(listOf(feature("immutable"), feature("immutable")))
        }

        assertEquals("immutable", exception.featureId)
    }

    @Test
    fun `缺失依赖直接失败`() {
        val exception = assertFailsWith<MissingCompilerFeatureDependencyException> {
            JimmerCompilerFeatureGraph.sort(listOf(feature("client", "dto")))
        }

        assertEquals("client", exception.featureId)
        assertEquals("dto", exception.dependencyId)
    }

    @Test
    fun `依赖环直接失败并给出闭环路径`() {
        val exception = assertFailsWith<CyclicCompilerFeatureDependencyException> {
            JimmerCompilerFeatureGraph.sort(
                listOf(
                    feature("client", "dto"),
                    feature("dto", "immutable"),
                    feature("immutable", "client")
                )
            )
        }

        assertEquals(listOf("client", "dto", "immutable", "client"), exception.cycle)
    }

    private fun feature(id: String, vararg dependencies: String): JimmerCompilerFeatureProvider =
        object : JimmerCompilerFeatureProvider {
            override val descriptor = JimmerCompilerFeatureDescriptor(id, dependencies.toSet())

            override fun compile(context: JimmerCompilerFeatureContext): JimmerCompilerFeatureResult =
                JimmerCompilerFeatureResult()
        }
}
