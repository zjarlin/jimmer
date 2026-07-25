package site.addzero.lsi.jimmer.client

import kotlin.test.Test
import kotlin.test.assertNotEquals
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiVariance

class ClientSnapshotTest {

    @Test
    fun `fingerprint preserves rendered operation and branch order`() {
        val firstOperation = operation("first")
        val secondOperation = operation("second")
        assertNotEquals(
            schema(operations = listOf(firstOperation, secondOperation)).fingerprint(),
            schema(operations = listOf(secondOperation, firstOperation)).fingerprint(),
        )

        val firstBranch = declaredType("demo.FirstBranch")
        val secondBranch = declaredType("demo.SecondBranch")
        assertNotEquals(
            schema(branches = listOf(firstBranch, secondBranch)).fingerprint(),
            schema(branches = listOf(secondBranch, firstBranch)).fingerprint(),
        )
    }

    @Test
    fun `fingerprint covers complete polymorphic branch type reference`() {
        val branch = declaredType("demo.Branch")
        val changedBranches = listOf(
            branch.copy(
                arguments = listOf(
                    ClientTypeArgument(
                        variance = LsiVariance.INVARIANT,
                        type = ClientPrimitiveTypeRef(LsiPrimitiveKind.INT),
                    )
                ),
            ),
            branch.copy(nullable = true),
            branch.copy(
                fetchBy = ClientFetchBy(
                    value = "DETAIL_FETCHER",
                    ownerTypeId = LsiSymbolId.type("demo.Fetchers"),
                    ownerTypeName = ClientTypeName.parse("demo.Fetchers"),
                    targetEntityTypeId = LsiSymbolId.type("demo.Entity"),
                    documentation = "Detail fetcher.",
                )
            ),
        )
        val fingerprint = schema(branches = listOf(branch)).fingerprint()

        changedBranches.forEach { changedBranch ->
            assertNotEquals(fingerprint, schema(branches = listOf(changedBranch)).fingerprint())
        }
    }

    @Test
    fun `fingerprint preserves rendered definition member order`() {
        val definition = schema().definitions.single()
        val firstProperty = ClientDefinitionProperty(
            id = LsiSymbolId.property(DEFINITION_ID, "first"),
            name = "first",
            type = ClientPrimitiveTypeRef(LsiPrimitiveKind.INT),
            doc = null,
        )
        val secondProperty = ClientDefinitionProperty(
            id = LsiSymbolId.property(DEFINITION_ID, "second"),
            name = "second",
            type = ClientPrimitiveTypeRef(LsiPrimitiveKind.LONG),
            doc = null,
        )
        assertDefinitionOrderChanges(
            definition.copy(properties = listOf(firstProperty, secondProperty)),
            definition.copy(properties = listOf(secondProperty, firstProperty)),
        )

        val firstSuperType = declaredType("demo.FirstSuperType")
        val secondSuperType = declaredType("demo.SecondSuperType")
        assertDefinitionOrderChanges(
            definition.copy(superTypes = listOf(firstSuperType, secondSuperType)),
            definition.copy(superTypes = listOf(secondSuperType, firstSuperType)),
        )

        val firstConstant = ClientEnumConstant(
            id = LsiSymbolId("${DEFINITION_ID.value}#FIRST"),
            name = "FIRST",
            doc = null,
        )
        val secondConstant = ClientEnumConstant(
            id = LsiSymbolId("${DEFINITION_ID.value}#SECOND"),
            name = "SECOND",
            doc = null,
        )
        val enumDefinition = definition.copy(
            kind = ClientDefinitionKind.ENUM,
            polymorphicBranches = emptyList(),
        )
        assertDefinitionOrderChanges(
            enumDefinition.copy(enumConstants = listOf(firstConstant, secondConstant)),
            enumDefinition.copy(enumConstants = listOf(secondConstant, firstConstant)),
        )
    }

    @Test
    fun `fingerprint distinguishes collection element boundaries`() {
        val service = schema().services.single()
        assertNotEquals(
            schema().copy(services = listOf(service.copy(groups = listOf("a,b")))).fingerprint(),
            schema().copy(services = listOf(service.copy(groups = listOf("a", "b")))).fingerprint(),
        )

        val singleGroupOperation = operation("find").copy(groups = listOf("a,b"))
        val multipleGroupOperation = operation("find").copy(groups = listOf("a", "b"))
        assertNotEquals(
            schema(operations = listOf(singleGroupOperation)).fingerprint(),
            schema(operations = listOf(multipleGroupOperation)).fingerprint(),
        )
    }

    @Test
    fun `fingerprint distinguishes nested type name boundaries`() {
        val definition = schema().definitions.single()
        val literalDollarName = definition.copy(
            typeName = ClientTypeName(
                packageName = "demo",
                simpleNames = listOf("Outer\$Inner"),
            )
        )
        val nestedName = definition.copy(
            typeName = ClientTypeName(
                packageName = "demo",
                simpleNames = listOf("Outer", "Inner"),
            )
        )

        assertDefinitionOrderChanges(literalDollarName, nestedName)
    }

    private fun assertDefinitionOrderChanges(
        first: ClientTypeDefinition,
        second: ClientTypeDefinition,
    ) {
        assertNotEquals(
            schema().copy(definitions = listOf(first)).fingerprint(),
            schema().copy(definitions = listOf(second)).fingerprint(),
        )
    }

    private fun schema(
        operations: List<ClientOperation> = emptyList(),
        branches: List<ClientDeclaredTypeRef> = emptyList(),
    ): ClientSchema {
        return ClientSchema(
            services = listOf(
                ClientService(
                    id = SERVICE_ID,
                    qualifiedName = SERVICE_ID.requireTypeQualifiedName(),
                    groups = emptyList(),
                    doc = null,
                    operations = operations,
                )
            ),
            definitions = listOf(
                ClientTypeDefinition(
                    id = DEFINITION_ID,
                    typeName = ClientTypeName.parse(DEFINITION_ID.requireTypeQualifiedName()),
                    kind = ClientDefinitionKind.OBJECT,
                    apiIgnore = false,
                    doc = null,
                    error = null,
                    properties = emptyList(),
                    superTypes = emptyList(),
                    polymorphicBranches = branches,
                    enumConstants = emptyList(),
                )
            ),
        )
    }

    private fun operation(name: String): ClientOperation {
        return ClientOperation(
            id = LsiSymbolId.function(SERVICE_ID, name),
            name = name,
            groups = emptyList(),
            doc = null,
            parameters = emptyList(),
            ignoredParameters = emptyList(),
            returnType = null,
            declaredExceptionTypeIds = emptyList(),
            exceptionTypeIds = emptyList(),
            exceptionMetadata = emptyList(),
        )
    }

    private fun declaredType(qualifiedName: String): ClientDeclaredTypeRef {
        return ClientDeclaredTypeRef(
            typeId = LsiSymbolId.type(qualifiedName),
            typeName = ClientTypeName.parse(qualifiedName),
        )
    }

    companion object {
        private val SERVICE_ID = LsiSymbolId.type("demo.Service")
        private val DEFINITION_ID = LsiSymbolId.type("demo.Result")
    }
}
