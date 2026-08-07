package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentOrigin
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerResolutionStatus
import org.babyfish.jimmer.compiler.CompilerSourceSet
import org.babyfish.jimmer.compiler.JacksonFamily
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoAnnotation
import site.addzero.lsi.jimmer.dto.DtoAnnotationArgument
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoAnnotationValue
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoInterfaceContract
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeAnnotationPlan
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.model.LsiVisibility

class JimmerDtoKspMutabilityPlanTest {
    @Test
    fun `non ksp platforms freeze immutable roots without consuming overrides`() {
        val graphs = listOf(graph())
        val options = rendererOptions(defaultMutable = true)
        val aptPlan = options.effectiveKspMutableByRootTypeId(CompilerPlatform.APT, graphs)
        val unknownPlan = options.effectiveKspMutableByRootTypeId(CompilerPlatform.UNKNOWN, graphs)

        assertEquals(ROOT_TYPE_IDS.sorted(), aptPlan.keys.toList())
        assertTrue(aptPlan.values.none { mutable -> mutable })
        assertEquals(aptPlan, unknownPlan)
    }

    @Test
    fun `dto state freezes stable root plan into fingerprint`() {
        val graphs = listOf(graph())
        val falsePlan = ROOT_TYPE_IDS.sorted().associateWith { rootTypeId ->
            rootTypeId == MUTABLE_ROOT_TYPE_ID
        }
        val truePlan = ROOT_TYPE_IDS.sorted().associateWith { rootTypeId ->
            rootTypeId != IMMUTABLE_ROOT_TYPE_ID
        }
        val falseState = state(graphs, falsePlan)
        val trueState = state(graphs, truePlan)

        assertEquals(falsePlan, falseState.effectiveKspMutableByRootTypeId)
        assertNotEquals(falseState.fingerprint, trueState.fingerprint)
        assertFailsWith<IllegalArgumentException> {
            state(
                graphs,
                linkedMapOf(
                    MUTABLE_ROOT_TYPE_ID to true,
                    AUTO_ROOT_TYPE_ID to false,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            state(graphs, sortedMapOf(NESTED_TYPE_ID to true))
        }
    }

    private fun graph(): DtoGraph {
        val types = listOf(
            dtoType(AUTO_ROOT_TYPE_ID, kotlinDtoAnnotation("AUTO")),
            dtoType(DEFAULT_ROOT_TYPE_ID),
            dtoType(IMMUTABLE_ROOT_TYPE_ID, kotlinDtoAnnotation("IMMUTABLE")),
            dtoType(MUTABLE_ROOT_TYPE_ID, kotlinDtoAnnotation("MUTABLE")),
            dtoType(NESTED_TYPE_ID, kotlinDtoAnnotation("MUTABLE"), baseTypeId = null),
        ).sortedBy(DtoType::id)
        return DtoGraph(
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
        id: DtoTypeId,
        annotation: DtoAnnotation? = null,
        baseTypeId: LsiSymbolId? = BASE_TYPE_ID,
    ): DtoType {
        return DtoType(
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

    private fun kotlinDtoAnnotation(immutability: String): DtoAnnotation {
        return DtoAnnotation(
            typeId = KOTLIN_DTO_ANNOTATION_TYPE_ID,
            arguments = listOf(
                DtoAnnotationArgument(
                    name = "immutability",
                    value = DtoAnnotationValue.EnumValue(
                        enumTypeId = KOTLIN_DTO_IMMUTABILITY_TYPE_ID,
                        constant = immutability,
                    ),
                )
            ),
        )
    }

    private fun rendererOptions(defaultMutable: Boolean): JimmerDtoRendererOptions {
        return JimmerDtoRendererOptions(
            jacksonVersion = JacksonFamily.JACKSON_2,
            hibernateValidatorEnhancement = false,
            aptFieldVisibility = LsiVisibility.PRIVATE,
            kspMutable = defaultMutable,
        )
    }

    private fun state(
        graphs: List<DtoGraph>,
        effectiveKspMutableByRootTypeId: Map<DtoTypeId, Boolean>,
    ): JimmerDtoCompilerFeatureState {
        val graph = graphs.single()
        return JimmerDtoCompilerFeatureState(
            status = JimmerDtoCompilerFeatureStatus.RESOLVED,
            dependencyStatus = CompilerResolutionStatus.RESOLVED,
            graphs = graphs,
            annotationContractsBySource = sortedMapOf(
                graph.source to DtoAnnotationContract(
                    declarations = emptyList(),
                    typePlans = graph.types.map { type ->
                        DtoTypeAnnotationPlan(type.id, emptyList())
                    },
                    propPlans = emptyList(),
                    diagnostics = emptyList(),
                )
            ),
            interfaceContractsBySource = sortedMapOf(
                graph.source to DtoInterfaceContractResolution(
                    contracts = graph.types.map { type ->
                        DtoInterfaceContract(type.id, emptyList(), emptyList())
                    },
                    diagnostics = emptyList(),
                )
            ),
            configContractsBySource = sortedMapOf(
                graph.source to DtoConfigContractResolution(
                    contracts = emptyList(),
                    diagnostics = emptyList(),
                )
            ),
            resolvedInputFingerprint = "resolved-input-fingerprint",
            unresolvedDocuments = emptyList(),
            failures = emptyList(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            rendererOptions = rendererOptions(defaultMutable = false),
            effectiveKspMutableByRootTypeId = effectiveKspMutableByRootTypeId,
            inputDocumentDiscoveryComplete = true,
            immutableDependencyFingerprint = "immutable-fingerprint",
        )
    }

    private companion object {
        val DOCUMENT = CompilerInputDocument(
            kind = CompilerInputDocumentKind.DTO,
            sourceSet = CompilerSourceSet.MAIN,
            origin = CompilerInputDocumentOrigin.Project("demo-project", "src/main/dto"),
            relativePath = "demo/Book.dto",
            content = "frozen ksp mutability fixture",
        )
        val BASE_TYPE_ID = LsiSymbolId.type("demo.Book")
        val AUTO_ROOT_TYPE_ID = DtoTypeId("demo/Book.dto#root-auto")
        val DEFAULT_ROOT_TYPE_ID = DtoTypeId("demo/Book.dto#root-default")
        val IMMUTABLE_ROOT_TYPE_ID = DtoTypeId("demo/Book.dto#root-immutable")
        val MUTABLE_ROOT_TYPE_ID = DtoTypeId("demo/Book.dto#root-mutable")
        val NESTED_TYPE_ID = DtoTypeId("demo/Book.dto#type-nested")
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
