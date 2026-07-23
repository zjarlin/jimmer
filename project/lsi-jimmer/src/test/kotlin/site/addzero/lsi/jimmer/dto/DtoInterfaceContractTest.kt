package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.stableSignature
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeArgument
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoTypeRef
import site.addzero.lsi.jimmer.dto.DtoVariance

class DtoInterfaceContractTest {

    @Test
    fun `accepts binary java setter projection without a source and rejects kotlin projection`() {
        val contractTypeId = typeId("contract.BinaryContract")
        val stringTypeId = typeId("java.lang.String")
        val setterId = LsiSymbolId.function(
            contractTypeId,
            "setName",
            listOf("type:${stringTypeId.requireTypeQualifiedName()}!non-null"),
        )
        val parameterId = LsiSymbolId.parameter(setterId, 0, "name")

        fun workspace(language: LsiLanguage): LsiWorkspace {
            val binaryOrigin = LsiOrigin(
                kind = LsiOriginKind.BINARY,
                language = language,
            )
            return LsiWorkspace(
                declarations = listOf(
                    LsiTypeDeclaration(
                        id = contractTypeId,
                        name = "BinaryContract",
                        qualifiedName = "contract.BinaryContract",
                        kind = LsiTypeDeclarationKind.INTERFACE,
                        modality = LsiModality.ABSTRACT,
                        memberIds = listOf(setterId),
                        origin = binaryOrigin,
                    ),
                    LsiFunction(
                        id = setterId,
                        name = "setName",
                        ownerId = contractTypeId,
                        returnType = LsiPrimitiveType(LsiPrimitiveKind.VOID),
                        parameters = listOf(
                            LsiParameter(
                                id = parameterId,
                                name = "name",
                                callableId = setterId,
                                index = 0,
                                type = LsiDeclaredType(stringTypeId),
                                origin = binaryOrigin,
                            )
                        ),
                        modality = LsiModality.ABSTRACT,
                        origin = binaryOrigin,
                    ),
                    LsiTypeDeclaration(
                        id = stringTypeId,
                        name = "String",
                        qualifiedName = "java.lang.String",
                        kind = LsiTypeDeclarationKind.CLASS,
                        origin = binaryOrigin,
                    ),
                ),
            )
        }

        val javaResolution = workspace(LsiLanguage.JAVA).resolveDtoInterfaceContracts(
            graph(listOf(typeRef("contract.BinaryContract"))),
        )
        val kotlinResolution = workspace(LsiLanguage.KOTLIN).resolveDtoInterfaceContracts(
            graph(listOf(typeRef("contract.BinaryContract"))),
        )

        assertTrue(javaResolution.successful)
        assertEquals("setName", javaResolution.contracts.single().props.single().setter?.name)
        assertEquals(
            listOf("jimmer.dto.interface.illegal-abstract-function"),
            kotlinResolution.diagnostics.map { diagnostic -> diagnostic.code },
        )
    }

    @Test
    fun `resolves inherited generic property and abstract setter`() {
        val rootTypeId = typeId("contract.Root")
        val middleTypeId = typeId("contract.Middle")
        val rootParameterId = LsiSymbolId.typeParameter(rootTypeId, "E")
        val middleParameterId = LsiSymbolId.typeParameter(middleTypeId, "T")
        val valuePropertyId = LsiSymbolId.property(rootTypeId, "value")
        val setterId = LsiSymbolId.function(
            middleTypeId,
            "setValue",
            listOf("parameter:${middleParameterId.value}!unknown"),
        )
        val setterParameterId = LsiSymbolId.parameter(setterId, 0, "value")
        val listTypeId = typeId("kotlin.collections.List")
        val workspace = LsiWorkspace(
            declarations = listOf(
                interfaceType(
                    id = rootTypeId,
                    typeParameters = listOf(LsiTypeParameter(rootParameterId, "E")),
                    memberIds = listOf(valuePropertyId),
                ),
                LsiProperty(
                    id = valuePropertyId,
                    name = "value",
                    ownerId = rootTypeId,
                    type = LsiTypeParameterRef(rootParameterId),
                    getterName = "getValue",
                    modality = LsiModality.ABSTRACT,
                    origin = origin("contract/Root.kt"),
                ),
                interfaceType(
                    id = middleTypeId,
                    typeParameters = listOf(LsiTypeParameter(middleParameterId, "T")),
                    superTypes = listOf(
                        LsiDeclaredType(
                            declarationId = rootTypeId,
                            arguments = listOf(
                                LsiTypeArgument.invariant(
                                    LsiDeclaredType(
                                        declarationId = listTypeId,
                                        arguments = listOf(
                                            LsiTypeArgument.invariant(LsiTypeParameterRef(middleParameterId)),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    memberIds = listOf(setterId),
                ),
                LsiFunction(
                    id = setterId,
                    name = "setValue",
                    ownerId = middleTypeId,
                    returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
                    parameters = listOf(
                        LsiParameter(
                            id = setterParameterId,
                            name = "value",
                            callableId = setterId,
                            index = 0,
                            type = LsiDeclaredType(
                                declarationId = listTypeId,
                                arguments = listOf(
                                    LsiTypeArgument.invariant(LsiTypeParameterRef(middleParameterId)),
                                ),
                            ),
                            origin = origin("contract/Middle.java"),
                        ),
                    ),
                    modality = LsiModality.ABSTRACT,
                    origin = origin("contract/Middle.java"),
                ),
                interfaceType(listTypeId),
            ),
        )
        val graph = graph(
            superInterfaces = listOf(
                typeRef(
                    "contract.Middle",
                    arguments = listOf(typeArgument(typeRef("String"))),
                ),
            ),
        )

        val resolution = workspace.resolveDtoInterfaceContracts(graph)

        assertTrue(resolution.successful)
        val prop = resolution.contracts.single().props.single()
        assertEquals(middleTypeId, prop.declaringTypeId)
        assertEquals("value", prop.name)
        assertEquals(
            "type:kotlin.collections.List<type:kotlin.String!non-null>!non-null",
            prop.type.stableSignature(),
        )
        assertTrue(prop.mutable)
        assertEquals("getValue", prop.getter?.name)
        assertEquals("setValue", prop.setter?.name)
        assertEquals(setterId, prop.setter?.declarationId)
        assertEquals(origin("contract/Middle.java"), prop.origin)
    }

    @Test
    fun `captures immutable and mutable interface properties`() {
        val contractTypeId = typeId("contract.Properties")
        val titleId = LsiSymbolId.property(contractTypeId, "title")
        val enabledId = LsiSymbolId.property(contractTypeId, "enabled")
        val workspace = LsiWorkspace(
            declarations = listOf(
                interfaceType(contractTypeId, memberIds = listOf(enabledId, titleId)),
                LsiProperty(
                    id = titleId,
                    name = "title",
                    ownerId = contractTypeId,
                    type = LsiDeclaredType(typeId("kotlin.String")),
                    getterName = "title",
                    modality = LsiModality.ABSTRACT,
                    origin = origin("contract/Properties.kt"),
                ),
                LsiProperty(
                    id = enabledId,
                    name = "enabled",
                    ownerId = contractTypeId,
                    type = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
                    getterName = "enabled",
                    mutable = true,
                    modality = LsiModality.ABSTRACT,
                    origin = origin("contract/Properties.kt"),
                ),
                interfaceType(typeId("kotlin.String")),
            ),
        )

        val resolution = workspace.resolveDtoInterfaceContracts(
            graph(listOf(typeRef("contract.Properties"))),
        )

        assertTrue(resolution.successful)
        val props = resolution.contracts.single().propsByName
        assertFalse(props.getValue("title").mutable)
        assertEquals("title", props.getValue("title").getter?.name)
        assertNull(props.getValue("title").setter)
        assertTrue(props.getValue("enabled").mutable)
        assertEquals("enabled", props.getValue("enabled").getter?.name)
        assertEquals("enabled", props.getValue("enabled").setter?.name)
    }

    @Test
    fun `merges compatible property getters reached through a generic diamond`() {
        val baseId = typeId("contract.DiamondBase")
        val leftId = typeId("contract.DiamondLeft")
        val rightId = typeId("contract.DiamondRight")
        val baseParameterId = LsiSymbolId.typeParameter(baseId, "E")
        val leftParameterId = LsiSymbolId.typeParameter(leftId, "L")
        val rightParameterId = LsiSymbolId.typeParameter(rightId, "R")
        val valueId = LsiSymbolId.property(baseId, "value")
        val workspace = LsiWorkspace(
            declarations = listOf(
                interfaceType(
                    id = baseId,
                    typeParameters = listOf(LsiTypeParameter(baseParameterId, "E")),
                    memberIds = listOf(valueId),
                ),
                LsiProperty(
                    id = valueId,
                    name = "value",
                    ownerId = baseId,
                    type = LsiTypeParameterRef(baseParameterId),
                    getterName = "getValue",
                    modality = LsiModality.ABSTRACT,
                    origin = origin("contract/DiamondBase.java"),
                ),
                interfaceType(
                    id = leftId,
                    typeParameters = listOf(LsiTypeParameter(leftParameterId, "L")),
                    superTypes = listOf(
                        LsiDeclaredType(
                            baseId,
                            listOf(LsiTypeArgument.invariant(LsiTypeParameterRef(leftParameterId))),
                        ),
                    ),
                ),
                interfaceType(
                    id = rightId,
                    typeParameters = listOf(LsiTypeParameter(rightParameterId, "R")),
                    superTypes = listOf(
                        LsiDeclaredType(
                            baseId,
                            listOf(LsiTypeArgument.invariant(LsiTypeParameterRef(rightParameterId))),
                        ),
                    ),
                ),
            ),
        )
        val stringArgument = listOf(typeArgument(typeRef("String")))

        val resolution = workspace.resolveDtoInterfaceContracts(
            graph(
                listOf(
                    typeRef("contract.DiamondLeft", stringArgument),
                    typeRef("contract.DiamondRight", stringArgument),
                ),
            ),
        )

        assertTrue(resolution.successful)
        val prop = resolution.contracts.single().props.single()
        assertEquals(baseId, prop.declaringTypeId)
        assertEquals("getValue", prop.getter?.name)
        assertEquals("type:kotlin.String!non-null", prop.type.stableSignature())
    }

    @Test
    fun `rejects missing and non interface roots with shared diagnostics`() {
        val classTypeId = typeId("contract.NotAnInterface")
        val workspace = LsiWorkspace(
            declarations = listOf(
                LsiTypeDeclaration(
                    id = classTypeId,
                    name = "NotAnInterface",
                    qualifiedName = "contract.NotAnInterface",
                    kind = LsiTypeDeclarationKind.CLASS,
                    origin = origin("contract/NotAnInterface.kt"),
                ),
            ),
        )
        val resolution = workspace.resolveDtoInterfaceContracts(
            graph(
                listOf(
                    typeRef("contract.Missing"),
                    typeRef("contract.NotAnInterface"),
                ),
            ),
        )

        assertFalse(resolution.successful)
        assertTrue(resolution.contracts.isEmpty())
        assertEquals(
            listOf(
                "jimmer.dto.interface.not-interface",
                "jimmer.dto.interface.unresolved-type-reference",
            ),
            resolution.diagnostics.map { diagnostic -> diagnostic.code },
        )
        assertTrue(resolution.diagnostics.all { diagnostic -> diagnostic.severity.name == "ERROR" })
        assertTrue(resolution.diagnostics.all { diagnostic -> diagnostic.location?.source == DTO_SOURCE })
    }

    @Test
    fun `rejects malformed nested generic arguments before rendering`() {
        val contractTypeId = typeId("contract.Generic")
        val parameterId = LsiSymbolId.typeParameter(contractTypeId, "T")
        val workspace = LsiWorkspace(
            declarations = listOf(
                interfaceType(
                    contractTypeId,
                    typeParameters = listOf(LsiTypeParameter(parameterId, "T")),
                ),
            ),
        )
        val resolution = workspace.resolveDtoInterfaceContracts(
            graph(
                listOf(
                    typeRef(
                        "contract.Generic",
                        listOf(typeArgument(typeRef("List"))),
                    ),
                ),
            ),
        )

        assertFalse(resolution.successful)
        assertEquals(
            listOf("jimmer.dto.interface.invalid-type-reference"),
            resolution.diagnostics.map { diagnostic -> diagnostic.code },
        )
        assertTrue(resolution.contracts.isEmpty())
    }

    @Test
    fun `rejects generic non accessor and unresolved abstract functions`() {
        val contractTypeId = typeId("contract.Illegal")
        val genericId = LsiSymbolId.function(contractTypeId, "getValue")
        val actionId = LsiSymbolId.function(contractTypeId, "execute")
        val unresolvedId = LsiSymbolId.function(contractTypeId, "setMissing")
        val unresolvedParameterId = LsiSymbolId.parameter(unresolvedId, 0, "missing")
        val functionParameterId = LsiSymbolId.typeParameter(genericId, "T")
        val workspace = LsiWorkspace(
            declarations = listOf(
                interfaceType(
                    contractTypeId,
                    memberIds = listOf(unresolvedId, actionId, genericId),
                ),
                LsiFunction(
                    id = genericId,
                    name = "getValue",
                    ownerId = contractTypeId,
                    returnType = LsiTypeParameterRef(functionParameterId),
                    typeParameters = listOf(LsiTypeParameter(functionParameterId, "T")),
                    modality = LsiModality.ABSTRACT,
                    origin = origin("contract/Illegal.java"),
                ),
                LsiFunction(
                    id = actionId,
                    name = "execute",
                    ownerId = contractTypeId,
                    returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
                    modality = LsiModality.ABSTRACT,
                    origin = origin("contract/Illegal.java"),
                ),
                LsiFunction(
                    id = unresolvedId,
                    name = "setMissing",
                    ownerId = contractTypeId,
                    returnType = LsiPrimitiveType(LsiPrimitiveKind.VOID),
                    parameters = listOf(
                        LsiParameter(
                            id = unresolvedParameterId,
                            name = "missing",
                            callableId = unresolvedId,
                            index = 0,
                            type = LsiUnresolvedType("Missing"),
                            origin = origin("contract/Illegal.java"),
                        ),
                    ),
                    modality = LsiModality.ABSTRACT,
                    origin = origin("contract/Illegal.java"),
                ),
            ),
        )

        val resolution = workspace.resolveDtoInterfaceContracts(
            graph(listOf(typeRef("contract.Illegal"))),
        )

        assertFalse(resolution.successful)
        assertEquals(
            listOf(
                "jimmer.dto.interface.illegal-abstract-function",
                "jimmer.dto.interface.illegal-abstract-function",
                "jimmer.dto.interface.unresolved-member-type",
            ),
            resolution.diagnostics.map { diagnostic -> diagnostic.code },
        )
        assertTrue(resolution.diagnostics.all { diagnostic -> diagnostic.symbolId != null })
    }

    @Test
    fun `rejects Kotlin accessor shaped functions and unnormalized Java getters`() {
        val contractTypeId = typeId("contract.Functions")
        val kotlinGetterId = LsiSymbolId.function(contractTypeId, "getName")
        val kotlinSetterId = LsiSymbolId.function(
            contractTypeId,
            "setName",
            listOf("type:kotlin.String!non-null"),
        )
        val javaGetterId = LsiSymbolId.function(contractTypeId, "getCode")
        val kotlinSetterParameterId = LsiSymbolId.parameter(kotlinSetterId, 0, "name")
        val workspace = LsiWorkspace(
            declarations = listOf(
                interfaceType(
                    contractTypeId,
                    memberIds = listOf(kotlinGetterId, kotlinSetterId, javaGetterId),
                ),
                LsiFunction(
                    id = kotlinGetterId,
                    name = "getName",
                    ownerId = contractTypeId,
                    returnType = LsiDeclaredType(typeId("kotlin.String")),
                    modality = LsiModality.ABSTRACT,
                    origin = origin("contract/Functions.kt"),
                ),
                LsiFunction(
                    id = kotlinSetterId,
                    name = "setName",
                    ownerId = contractTypeId,
                    returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
                    parameters = listOf(
                        LsiParameter(
                            id = kotlinSetterParameterId,
                            name = "name",
                            callableId = kotlinSetterId,
                            index = 0,
                            type = LsiDeclaredType(typeId("kotlin.String")),
                            origin = origin("contract/Functions.kt"),
                        ),
                    ),
                    modality = LsiModality.ABSTRACT,
                    origin = origin("contract/Functions.kt"),
                ),
                LsiFunction(
                    id = javaGetterId,
                    name = "getCode",
                    ownerId = contractTypeId,
                    returnType = LsiDeclaredType(typeId("java.lang.String")),
                    modality = LsiModality.ABSTRACT,
                    origin = origin("contract/Functions.java"),
                ),
                interfaceType(typeId("kotlin.String")),
                interfaceType(typeId("java.lang.String")),
            ),
        )

        val resolution = workspace.resolveDtoInterfaceContracts(
            graph(listOf(typeRef("contract.Functions"))),
        )

        assertFalse(resolution.successful)
        assertEquals(3, resolution.diagnostics.size)
        assertTrue(
            resolution.diagnostics.all { diagnostic ->
                diagnostic.code == "jimmer.dto.interface.illegal-abstract-function"
            },
        )
    }

    @Test
    fun `rejects conflicting generic property types deterministically`() {
        val leftId = typeId("contract.Left")
        val rightId = typeId("contract.Right")
        val leftValueId = LsiSymbolId.property(leftId, "value")
        val rightValueId = LsiSymbolId.property(rightId, "value")
        val declarations = listOf(
            interfaceType(leftId, memberIds = listOf(leftValueId)),
            interfaceType(rightId, memberIds = listOf(rightValueId)),
            LsiProperty(
                id = leftValueId,
                name = "value",
                ownerId = leftId,
                type = LsiPrimitiveType(LsiPrimitiveKind.INT),
                getterName = "getValue",
                modality = LsiModality.ABSTRACT,
                origin = origin("contract/Left.kt"),
            ),
            LsiProperty(
                id = rightValueId,
                name = "value",
                ownerId = rightId,
                type = LsiDeclaredType(typeId("kotlin.String")),
                getterName = "getValue",
                modality = LsiModality.ABSTRACT,
                origin = origin("contract/Right.kt"),
            ),
            interfaceType(typeId("kotlin.String")),
        )
        val graph = graph(listOf(typeRef("contract.Left"), typeRef("contract.Right")))

        val first = LsiWorkspace(declarations = declarations).resolveDtoInterfaceContracts(graph)
        val second = LsiWorkspace(declarations = declarations.reversed()).resolveDtoInterfaceContracts(graph)

        assertEquals(first.diagnostics, second.diagnostics)
        assertEquals(listOf("jimmer.dto.interface.conflicting-property-type"), first.diagnostics.map { it.code })
        assertTrue(first.contracts.isEmpty())
    }

    @Test
    fun `contract classes do not expose compiler frontend or poet types`() {
        val reachableNames = reachableTypeNames(DtoInterfaceContractResolution::class.java)

        assertTrue(
            reachableNames.none { name ->
                name.startsWith("javax.lang.model.") ||
                    name.startsWith("com.google.devtools.ksp.") ||
                    name.startsWith("com.squareup.javapoet.") ||
                    name.startsWith("com.squareup.kotlinpoet.")
            },
            "DTO interface contract exposes platform renderer state: $reachableNames",
        )
    }

    private fun graph(superInterfaces: List<DtoTypeRef>): DtoGraph {
        val type = DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = null,
            packageName = "contract.dto",
            name = "ContractDto",
            modifiers = emptySet(),
            annotations = emptyList(),
            superInterfaces = superInterfaces,
            documentation = null,
            location = DTO_LOCATION,
            focusedRecursion = false,
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        return DtoGraph(
            source = DTO_SOURCE,
            rootTypeIds = listOf(DTO_TYPE_ID),
            types = listOf(type),
            props = emptyList(),
        )
    }

    private fun typeRef(
        typeName: String,
        arguments: List<DtoTypeArgument> = emptyList(),
        nullable: Boolean = false,
    ): DtoTypeRef {
        return DtoTypeRef(typeName, arguments, nullable, DTO_LOCATION)
    }

    private fun typeArgument(type: DtoTypeRef): DtoTypeArgument {
        return DtoTypeArgument(DtoVariance.INVARIANT, type)
    }

    private fun interfaceType(
        id: LsiSymbolId,
        typeParameters: List<LsiTypeParameter> = emptyList(),
        superTypes: List<LsiDeclaredType> = emptyList(),
        memberIds: List<LsiSymbolId> = emptyList(),
    ): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = id,
            name = id.requireTypeQualifiedName().substringAfterLast('.'),
            qualifiedName = id.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.INTERFACE,
            modality = LsiModality.ABSTRACT,
            typeParameters = typeParameters,
            superTypes = superTypes,
            memberIds = memberIds,
            origin = origin(id.requireTypeQualifiedName().replace('.', '/') + ".kt"),
        )
    }

    private fun typeId(qualifiedName: String): LsiSymbolId = LsiSymbolId.type(qualifiedName)

    private fun origin(path: String): LsiOrigin {
        return LsiOrigin(
            kind = LsiOriginKind.SOURCE,
            source = LsiSource.of(
                path,
                if (path.endsWith(".java")) LsiLanguage.JAVA else LsiLanguage.KOTLIN,
            ),
        )
    }

    private fun reachableTypeNames(root: Class<*>): Set<String> {
        val result = linkedSetOf<String>()
        val pending = ArrayDeque<Class<*>>()
        pending += root
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!result.add(current.name)) {
                continue
            }
            current.declaredFields.forEach { field ->
                field.type.componentType?.let(pending::addLast)
                if (!field.type.isPrimitive && !field.type.name.startsWith("java.")) {
                    pending += field.type
                }
            }
        }
        return result
    }

    companion object {
        private val DTO_SOURCE = LsiSource.of("contract/Contract.dto")
        private val DTO_LOCATION = LsiLocation(DTO_SOURCE, LsiPosition(1, 1))
        private val DTO_TYPE_ID = DtoTypeId("contract/Contract.dto#root:0000:ContractDto")
    }
}
