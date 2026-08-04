package site.addzero.lsi.frontend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiTypeSeedMode
import site.addzero.lsi.model.LsiWorkspace

class LsiTypeSeedFixedPointTest {

    @Test
    fun `累计完整声明请求直到可达类型闭包稳定`() {
        val payloadId = LsiSymbolId.type("example.Payload")
        val nestedId = LsiSymbolId.type("example.Nested")
        val frozenSeedSnapshots = mutableListOf<List<LsiTypeSeed>>()

        val result = resolveLsiTypeSeedFixedPoint(
            initialWorkspace = LsiWorkspace.EMPTY,
            requestSeeds = { workspace ->
                buildList {
                    add(LsiTypeSeed(payloadId, LsiTypeSeedMode.FULL_DECLARATION))
                    if (workspace.contains(payloadId)) {
                        add(LsiTypeSeed(nestedId, LsiTypeSeedMode.FULL_DECLARATION))
                    }
                }
            },
            freezeWorkspace = { seeds ->
                frozenSeedSnapshots += seeds
                LsiWorkspace(
                    declarations = seeds.map { seed -> type(seed.typeId) },
                )
            },
        )

        assertEquals(
            listOf(
                listOf(LsiTypeSeed(payloadId, LsiTypeSeedMode.FULL_DECLARATION)),
                listOf(
                    LsiTypeSeed(nestedId, LsiTypeSeedMode.FULL_DECLARATION),
                    LsiTypeSeed(payloadId, LsiTypeSeedMode.FULL_DECLARATION),
                ),
            ),
            frozenSeedSnapshots,
        )
        assertEquals(frozenSeedSnapshots.last(), result.seeds)
        assertEquals(3, result.iterations)
        assertEquals(setOf(payloadId, nestedId), result.workspace.declarations.mapTo(sortedSetOf()) { it.id })
    }

    @Test
    fun `完整声明请求不会被后续 header 请求降级`() {
        val payloadId = LsiSymbolId.type("example.Payload")
        var requests = 0

        val result = resolveLsiTypeSeedFixedPoint(
            initialWorkspace = LsiWorkspace.EMPTY,
            requestSeeds = {
                requests++
                listOf(
                    LsiTypeSeed(
                        payloadId,
                        if (requests == 1) {
                            LsiTypeSeedMode.FULL_DECLARATION
                        } else {
                            LsiTypeSeedMode.HEADER
                        },
                    )
                )
            },
            freezeWorkspace = { LsiWorkspace.EMPTY },
        )

        assertEquals(listOf(LsiTypeSeed(payloadId, LsiTypeSeedMode.FULL_DECLARATION)), result.seeds)
        assertEquals(2, result.iterations)
    }

    @Test
    fun `类型声明请求不收敛时透明失败`() {
        var index = 0

        val exception = assertFailsWith<LsiTypeSeedFixedPointException> {
            resolveLsiTypeSeedFixedPoint(
                initialWorkspace = LsiWorkspace.EMPTY,
                maximumIterations = 2,
                requestSeeds = {
                    index++
                    listOf(
                        LsiTypeSeed(
                            LsiSymbolId.type("example.Payload$index"),
                            LsiTypeSeedMode.FULL_DECLARATION,
                        )
                    )
                },
                freezeWorkspace = { LsiWorkspace.EMPTY },
            )
        }

        assertEquals(2, exception.maximumIterations)
        assertEquals(2, exception.seeds.size)
    }

    private fun type(id: LsiSymbolId): LsiTypeDeclaration {
        val qualifiedName = id.requireTypeQualifiedName()
        return LsiTypeDeclaration(
            id = id,
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.CLASS,
            origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
        )
    }
}
