package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerSourceSet
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSymbolId

class JimmerDtoKspMutabilityPlanTest {
    @Test
    fun `ksp freezes effective mutability for roots only`() {
        val schema = schema()
        val immutableDefaultPlan = rendererOptions(defaultMutable = false)
            .effectiveKspMutableByRootTypeId(CompilerPlatform.KSP, schema)
        val mutableDefaultPlan = rendererOptions(defaultMutable = true)
            .effectiveKspMutableByRootTypeId(CompilerPlatform.KSP, schema)

        assertEquals(ROOT_TYPE_IDS.sorted(), immutableDefaultPlan.keys.toList())
        assertFalse(immutableDefaultPlan.getValue(AUTO_ROOT_TYPE_ID))
        assertFalse(immutableDefaultPlan.getValue(DEFAULT_ROOT_TYPE_ID))
        assertFalse(immutableDefaultPlan.getValue(IMMUTABLE_ROOT_TYPE_ID))
        assertTrue(immutableDefaultPlan.getValue(MUTABLE_ROOT_TYPE_ID))
        assertTrue(mutableDefaultPlan.getValue(AUTO_ROOT_TYPE_ID))
        assertTrue(mutableDefaultPlan.getValue(DEFAULT_ROOT_TYPE_ID))
        assertFalse(mutableDefaultPlan.getValue(IMMUTABLE_ROOT_TYPE_ID))
        assertTrue(mutableDefaultPlan.getValue(MUTABLE_ROOT_TYPE_ID))
        assertFalse(NESTED_TYPE_ID in immutableDefaultPlan)
        assertFalse(NESTED_TYPE_ID in mutableDefaultPlan)
    }

    @Test
    fun `non ksp platforms freeze immutable roots without consuming overrides`() {
        val schema = schema()
        val options = rendererOptions(defaultMutable = true)
        val aptPlan = options.effectiveKspMutableByRootTypeId(CompilerPlatform.APT, schema)
        val unknownPlan = options.effectiveKspMutableByRootTypeId(CompilerPlatform.UNKNOWN, schema)

        assertEquals(ROOT_TYPE_IDS.sorted(), aptPlan.keys.toList())
        assertTrue(aptPlan.values.none { mutable -> mutable })
        assertEquals(aptPlan, unknownPlan)
    }

    @Test
    fun `dto state freezes stable root plan into fingerprint`() {
        val schema = schema()
        val falsePlan = ROOT_TYPE_IDS.sorted().associateWith { rootTypeId ->
            rootTypeId == MUTABLE_ROOT_TYPE_ID
        }
        val truePlan = ROOT_TYPE_IDS.sorted().associateWith { rootTypeId ->
            rootTypeId != IMMUTABLE_ROOT_TYPE_ID
        }
        val falseState = state(schema, falsePlan)
        val trueState = state(schema, truePlan)

        assertEquals(falsePlan, falseState.effectiveKspMutableByRootTypeId)
        assertNotEquals(falseState.fingerprint, trueState.fingerprint)
        assertFailsWith<IllegalArgumentException> {
            state(
                schema,
                linkedMapOf(
                    MUTABLE_ROOT_TYPE_ID to true,
                    AUTO_ROOT_TYPE_ID to false,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            state(schema, sortedMapOf(NESTED_TYPE_ID to true))
        }
    }

    private fun schema(): JimmerDtoPrecompiledSchema {
        val graph = graph()
        return JimmerDtoPrecompiledSchema(
            listOf(
                JimmerDtoPrecompiledDocument(
                    inputSnapshot = CompilerInputDocumentSnapshot(DOCUMENT, emptyList()),
                    targetTypeIds = listOf(BASE_TYPE_ID),
                    renderGraph = graph,
                    annotationContract = JimmerDtoAnnotationContract(
                        declarations = emptyList(),
                        typePlans = graph.types.map { type ->
                            JimmerDtoTypeAnnotationPlan(type.id, emptyList())
                        },
                        propPlans = emptyList(),
                        diagnostics = emptyList(),
                    ),
                    interfaceContractResolution = DtoInterfaceContractResolution(
                        contracts = graph.types.map { type ->
                            DtoInterfaceContract(type.id, emptyList(), emptyList())
                        },
                        diagnostics = emptyList(),
                    ),
                    configContractResolution = DtoConfigContractResolution(
                        contracts = emptyList(),
                        diagnostics = emptyList(),
                    ),
                )
            ),
        )
    }

    private fun graph(): JimmerDtoRenderGraph {
        val types = listOf(
            dtoType(AUTO_ROOT_TYPE_ID, kotlinDtoAnnotation("AUTO")),
            dtoType(DEFAULT_ROOT_TYPE_ID),
            dtoType(IMMUTABLE_ROOT_TYPE_ID, kotlinDtoAnnotation("IMMUTABLE")),
            dtoType(MUTABLE_ROOT_TYPE_ID, kotlinDtoAnnotation("MUTABLE")),
            dtoType(NESTED_TYPE_ID, kotlinDtoAnnotation("MUTABLE"), baseTypeId = null),
        ).sortedBy(JimmerDtoType::id)
        return JimmerDtoRenderGraph(
            source = DOCUMENT.source,
            rootTypeIds = listOf(
                MUTABLE_ROOT_TYPE_ID,
                DEFAULT_ROOT_TYPE_ID,
                IMMUTABLE_ROOT_TYPE_ID,
                AUTO_ROOT_TYPE_ID,
            ),
            types = types,
            props = emptyList(),
        )
    }

    private fun dtoType(
        id: JimmerDtoTypeId,
        annotation: JimmerDtoAnnotation? = null,
        baseTypeId: LsiSymbolId? = BASE_TYPE_ID,
    ): JimmerDtoType {
        return JimmerDtoType(
            id = id,
            baseTypeId = baseTypeId,
            packageName = "demo.dto",
            name = id.value.substringAfterLast('-').replaceFirstChar(Char::uppercase),
            modifiers = emptySet(),
            annotations = listOfNotNull(annotation),
            superInterfaces = emptyList(),
            documentation = null,
            location = LsiLocation(DOCUMENT.source, LsiPosition(1, 1)),
            focusedRecursion = false,
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
    }

    private fun kotlinDtoAnnotation(immutability: String): JimmerDtoAnnotation {
        return JimmerDtoAnnotation(
            typeId = KOTLIN_DTO_ANNOTATION_TYPE_ID,
            arguments = listOf(
                JimmerDtoAnnotationArgument(
                    name = "immutability",
                    value = JimmerDtoAnnotationValue.EnumValue(
                        enumTypeId = KOTLIN_DTO_IMMUTABILITY_TYPE_ID,
                        constant = immutability,
                    ),
                )
            ),
        )
    }

    private fun rendererOptions(defaultMutable: Boolean): JimmerDtoRendererOptions {
        return JimmerDtoRendererOptions(
            jacksonVersion = JimmerDtoJacksonVersion.JACKSON_2,
            hibernateValidatorEnhancement = false,
            aptFieldVisibility = JimmerDtoFieldVisibility.PRIVATE,
            kspMutable = defaultMutable,
        )
    }

    private fun state(
        schema: JimmerDtoPrecompiledSchema,
        effectiveKspMutableByRootTypeId: Map<JimmerDtoTypeId, Boolean>,
    ): JimmerDtoCompilerFeatureState {
        return JimmerDtoCompilerFeatureState(
            status = JimmerDtoCompilerFeatureStatus.RESOLVED,
            dependencyStatus = JimmerDtoCompilerDependencyStatus.RESOLVED,
            schema = schema,
            unresolvedDocuments = emptyList(),
            failures = emptyList(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            rendererOptions = rendererOptions(defaultMutable = false),
            effectiveKspMutableByRootTypeId = effectiveKspMutableByRootTypeId,
            immutableDependencyFingerprint = "immutable-fingerprint",
        )
    }

    private companion object {
        val DOCUMENT = CompilerInputDocument(
            kind = CompilerInputDocumentKind.DTO,
            sourceSet = CompilerSourceSet.MAIN,
            projectName = "demo-project",
            sourceRoot = "src/main/dto",
            relativePath = "demo/Book.dto",
            content = "frozen ksp mutability fixture",
        )
        val BASE_TYPE_ID = LsiSymbolId.type("demo.Book")
        val AUTO_ROOT_TYPE_ID = JimmerDtoTypeId("demo/Book.dto#root-auto")
        val DEFAULT_ROOT_TYPE_ID = JimmerDtoTypeId("demo/Book.dto#root-default")
        val IMMUTABLE_ROOT_TYPE_ID = JimmerDtoTypeId("demo/Book.dto#root-immutable")
        val MUTABLE_ROOT_TYPE_ID = JimmerDtoTypeId("demo/Book.dto#root-mutable")
        val NESTED_TYPE_ID = JimmerDtoTypeId("demo/Book.dto#type-nested")
        val ROOT_TYPE_IDS = listOf(
            AUTO_ROOT_TYPE_ID,
            DEFAULT_ROOT_TYPE_ID,
            IMMUTABLE_ROOT_TYPE_ID,
            MUTABLE_ROOT_TYPE_ID,
        )
        val KOTLIN_DTO_ANNOTATION_TYPE_ID =
            LsiSymbolId.type("org.babyfish.jimmer.kt.dto.KotlinDto")
        val KOTLIN_DTO_IMMUTABILITY_TYPE_ID =
            LsiSymbolId.type("org.babyfish.jimmer.kt.dto.KotlinDtoImmutability")
    }
}
