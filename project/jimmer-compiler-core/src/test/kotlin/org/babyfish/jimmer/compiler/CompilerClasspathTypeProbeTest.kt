package org.babyfish.jimmer.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiWorkspace

class CompilerClasspathTypeProbeTest {

    @Test
    fun `descriptor accepts only stable type ids`() {
        val typeId = LsiSymbolId.type("tools.jackson.databind.ObjectMapper")
        val descriptor = JimmerCompilerFeatureDescriptor(
            id = "immutable",
            classpathTypeIds = setOf(typeId),
        )

        assertEquals(setOf(typeId), descriptor.classpathTypeIds)
        assertFailsWith<IllegalArgumentException> {
            JimmerCompilerFeatureDescriptor(
                id = "invalid",
                classpathTypeIds = setOf(LsiSymbolId.property(typeId, "factory")),
            )
        }
    }

    @Test
    fun `round exposes only frozen declared availability`() {
        val availableTypeId = LsiSymbolId.type("tools.jackson.databind.ObjectMapper")
        val unavailableTypeId = LsiSymbolId.type("com.fasterxml.jackson.databind.ObjectMapper")
        val rounds = mutableListOf<CompilerRound>()
        val provider = object : JimmerCompilerFeatureProvider {
            override val descriptor = JimmerCompilerFeatureDescriptor(
                id = "immutable",
                classpathTypeIds = setOf(availableTypeId, unavailableTypeId),
            )

            override fun collect(context: JimmerCompilerCollectContext): JimmerCompilerFeatureCollection {
                rounds += context.round
                return JimmerCompilerFeatureCollection()
            }
        }
        val session = CompilerSession("classpath-probe", listOf(provider))

        session.execute(round(0, setOf(availableTypeId)))
        session.execute(round(1, setOf(availableTypeId), isFinal = true))

        assertEquals(
            listOf(setOf(availableTypeId), setOf(availableTypeId)),
            rounds.map(CompilerRound::availableTypeIds),
        )
    }

    @Test
    fun `session rejects availability not declared by any feature`() {
        val provider = object : JimmerCompilerFeatureProvider {
            override val descriptor = JimmerCompilerFeatureDescriptor("immutable")
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            CompilerSession("classpath-probe", listOf(provider)).execute(
                round(0, setOf(LsiSymbolId.type("tools.jackson.databind.ObjectMapper"))),
            )
        }

        assertEquals(
            "Available compiler types must be declared by a compiler feature",
            exception.message,
        )
    }

    private fun round(
        number: Int,
        availableTypeIds: Set<LsiSymbolId>,
        isFinal: Boolean = false,
    ): CompilerRound {
        return CompilerRound(
            number = number,
            workspace = LsiWorkspace.EMPTY,
            currentRootTypeIds = emptySet(),
            isFinal = isFinal,
            availableTypeIds = availableTypeIds,
            inputDocumentSnapshots = emptyList(),
        )
    }
}
