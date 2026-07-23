package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType

class DtoInterfaceContractSnapshotTest {

    @Test
    fun `snapshot covers property accessor origin type mutability and diagnostic semantics`() {
        val baseline = resolution()
        val baselineProp = baseline.contracts.single().props.single()

        assertSemanticChange(
            baseline,
            resolution(baselineProp.copy(getter = baselineProp.getter?.copy(name = "readValue"))),
        )
        assertSemanticChange(
            baseline,
            resolution(baselineProp.copy(mutable = false, setter = null)),
        )
        assertSemanticChange(
            baseline,
            resolution(
                baselineProp.copy(
                    origin = baselineProp.origin.copy(
                        kind = LsiOriginKind.GENERATED,
                        source = GENERATED_SOURCE,
                        language = LsiLanguage.JAVA,
                    )
                )
            ),
        )
        assertSemanticChange(
            baseline,
            resolution(
                baselineProp.copy(
                    type = LsiPrimitiveType(
                        kind = LsiPrimitiveKind.INT,
                        nullability = LsiNullability.NULLABLE,
                        boxed = true,
                    )
                )
            ),
        )

        val diagnostic = diagnostic(message = "Interface contract is invalid")
        assertSemanticChange(
            resolution(diagnostics = listOf(diagnostic)),
            resolution(
                diagnostics = listOf(
                    diagnostic.copy(
                        message = "Interface contract has an invalid property",
                        details = linkedMapOf("property" to "value", "reason" to "type-mismatch"),
                    )
                )
            ),
        )
    }

    @Test
    fun `fingerprint is stable across unordered origin symbols diagnostics and details`() {
        val firstProp = prop(
            origin = sourceOrigin(
                linkedSetOf(
                    LsiSymbolId.type("demo.SecondOrigin"),
                    LsiSymbolId.type("demo.FirstOrigin"),
                )
            )
        )
        val secondProp = prop(
            origin = sourceOrigin(
                linkedSetOf(
                    LsiSymbolId.type("demo.FirstOrigin"),
                    LsiSymbolId.type("demo.SecondOrigin"),
                )
            )
        )
        val firstDiagnostics = listOf(
            diagnostic(
                code = "jimmer.dto.interface.z",
                message = "Second diagnostic",
                details = linkedMapOf("z" to "last", "a" to "first"),
            ),
            diagnostic(
                code = "jimmer.dto.interface.a",
                message = "First diagnostic",
                details = linkedMapOf("property" to "value", "reason" to "conflict"),
            ),
        )
        val secondDiagnostics = listOf(
            firstDiagnostics[1].copy(details = linkedMapOf("reason" to "conflict", "property" to "value")),
            firstDiagnostics[0].copy(details = linkedMapOf("a" to "first", "z" to "last")),
        )
        val first = resolution(firstProp, firstDiagnostics)
        val second = resolution(secondProp, secondDiagnostics)

        assertEquals(first.normalizedSnapshot(), second.normalizedSnapshot())
        assertEquals(first.fingerprint(), second.fingerprint())
        assertEquals(first.fingerprint(), first.fingerprint())
        assertTrue(first.fingerprint().matches(Regex("[0-9a-f]{64}")))
    }

    private fun assertSemanticChange(
        first: DtoInterfaceContractResolution,
        second: DtoInterfaceContractResolution,
    ) {
        assertNotEquals(first.normalizedSnapshot(), second.normalizedSnapshot())
        assertNotEquals(first.fingerprint(), second.fingerprint())
    }

    private fun resolution(
        prop: DtoInterfacePropContract = prop(),
        diagnostics: List<LsiDiagnostic> = emptyList(),
    ): DtoInterfaceContractResolution {
        return DtoInterfaceContractResolution(
            contracts = listOf(
                DtoInterfaceContract(
                    typeId = DtoTypeId("demo/Contract.dto#ContractView"),
                    superInterfaceTypeIds = listOf(
                        LsiSymbolId.type("demo.ParentContract"),
                        LsiSymbolId.type("demo.AuditContract"),
                    ),
                    props = listOf(prop),
                )
            ),
            diagnostics = diagnostics,
        )
    }

    private fun prop(
        origin: LsiOrigin = sourceOrigin(
            setOf(LsiSymbolId.type("demo.OriginContract"))
        ),
    ): DtoInterfacePropContract {
        return DtoInterfacePropContract(
            declaringTypeId = CONTRACT_TYPE_ID,
            name = "value",
            type = LsiPrimitiveType(LsiPrimitiveKind.INT),
            mutable = true,
            getter = accessor("getValue"),
            setter = accessor("setValue"),
            origin = origin,
        )
    }

    private fun accessor(name: String): DtoInterfaceAccessorContract {
        return DtoInterfaceAccessorContract(
            declarationId = LsiSymbolId.function(CONTRACT_TYPE_ID, name, emptyList()),
            name = name,
            origin = sourceOrigin(setOf(LsiSymbolId.type("demo.AccessorOrigin"))),
        )
    }

    private fun diagnostic(
        code: String = "jimmer.dto.interface.invalid-contract",
        message: String,
        details: Map<String, String> = mapOf("property" to "value"),
    ): LsiDiagnostic {
        return LsiDiagnostic(
            code = code,
            severity = LsiDiagnosticSeverity.ERROR,
            message = message,
            symbolId = CONTRACT_TYPE_ID,
            location = LsiLocation(
                source = SOURCE,
                start = LsiPosition(3, 5),
                end = LsiPosition(3, 10),
            ),
            details = details,
        )
    }

    private fun sourceOrigin(originatingSymbols: Set<LsiSymbolId>): LsiOrigin {
        return LsiOrigin(
            kind = LsiOriginKind.SOURCE,
            source = SOURCE,
            originatingSymbols = originatingSymbols,
        )
    }

    private companion object {
        val CONTRACT_TYPE_ID = LsiSymbolId.type("demo.Contract")
        val SOURCE = LsiSource.of("demo/Contract.kt", LsiLanguage.KOTLIN)
        val GENERATED_SOURCE = LsiSource.of(
            "build/generated/demo/Contract.java",
            language = LsiLanguage.JAVA,
            kind = LsiSourceKind.GENERATED,
        )
    }
}
