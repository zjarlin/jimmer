package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

class DtoKotlinMutabilityExtensionsTest {
    @Test
    fun `resolves effective mutability for roots in stable order`() {
        val graph = graph()

        val immutableDefaultPlan = graph.effectiveKotlinMutabilityByRootTypeId(
            DtoKotlinMutability.IMMUTABLE,
        )
        val mutableDefaultPlan = graph.effectiveKotlinMutabilityByRootTypeId(
            DtoKotlinMutability.MUTABLE,
        )

        assertEquals(ROOT_TYPE_IDS.sorted(), immutableDefaultPlan.keys.toList())
        assertEquals(DtoKotlinMutability.IMMUTABLE, immutableDefaultPlan.getValue(AUTO_ROOT_TYPE_ID))
        assertEquals(DtoKotlinMutability.IMMUTABLE, immutableDefaultPlan.getValue(DEFAULT_ROOT_TYPE_ID))
        assertEquals(DtoKotlinMutability.IMMUTABLE, immutableDefaultPlan.getValue(IMMUTABLE_ROOT_TYPE_ID))
        assertEquals(DtoKotlinMutability.MUTABLE, immutableDefaultPlan.getValue(MUTABLE_ROOT_TYPE_ID))
        assertEquals(DtoKotlinMutability.MUTABLE, mutableDefaultPlan.getValue(AUTO_ROOT_TYPE_ID))
        assertEquals(DtoKotlinMutability.MUTABLE, mutableDefaultPlan.getValue(DEFAULT_ROOT_TYPE_ID))
        assertEquals(DtoKotlinMutability.IMMUTABLE, mutableDefaultPlan.getValue(IMMUTABLE_ROOT_TYPE_ID))
        assertEquals(DtoKotlinMutability.MUTABLE, mutableDefaultPlan.getValue(MUTABLE_ROOT_TYPE_ID))
        assertFalse(NESTED_TYPE_ID in immutableDefaultPlan)
        assertFalse(NESTED_TYPE_ID in mutableDefaultPlan)
    }

    @Test
    fun `uses default when kotlin dto annotation is absent or auto`() {
        val defaultType = dtoType(DEFAULT_ROOT_TYPE_ID)
        val autoType = dtoType(AUTO_ROOT_TYPE_ID, kotlinDtoAnnotation("AUTO"))

        assertEquals(
            DtoKotlinMutability.IMMUTABLE,
            defaultType.effectiveKotlinMutability(DtoKotlinMutability.IMMUTABLE),
        )
        assertEquals(
            DtoKotlinMutability.MUTABLE,
            autoType.effectiveKotlinMutability(DtoKotlinMutability.MUTABLE),
        )
    }

    @Test
    fun `rejects malformed kotlin dto mutability`() {
        val duplicateAnnotations = dtoType(
            DEFAULT_ROOT_TYPE_ID,
            annotations = listOf(kotlinDtoAnnotation("AUTO"), kotlinDtoAnnotation("MUTABLE")),
        )
        val missingArgument = dtoType(
            DEFAULT_ROOT_TYPE_ID,
            annotations = listOf(DtoAnnotation(KOTLIN_DTO_ANNOTATION_TYPE_ID, emptyList())),
        )
        val literalArgument = dtoType(
            DEFAULT_ROOT_TYPE_ID,
            annotations = listOf(
                kotlinDtoAnnotation(DtoAnnotationValue.LiteralValue("MUTABLE")),
            ),
        )
        val wrongEnumType = dtoType(
            DEFAULT_ROOT_TYPE_ID,
            annotations = listOf(
                kotlinDtoAnnotation(
                    DtoAnnotationValue.EnumValue(
                        enumTypeId = LsiSymbolId.type("demo.OtherMutability"),
                        constant = "MUTABLE",
                    ),
                ),
            ),
        )
        val unsupportedConstant = dtoType(
            DEFAULT_ROOT_TYPE_ID,
            annotations = listOf(kotlinDtoAnnotation("UNKNOWN")),
        )

        assertFailsWith<IllegalArgumentException> {
            duplicateAnnotations.effectiveKotlinMutability(DtoKotlinMutability.IMMUTABLE)
        }
        assertFailsWith<IllegalStateException> {
            missingArgument.effectiveKotlinMutability(DtoKotlinMutability.IMMUTABLE)
        }
        assertFailsWith<IllegalStateException> {
            literalArgument.effectiveKotlinMutability(DtoKotlinMutability.IMMUTABLE)
        }
        assertFailsWith<IllegalArgumentException> {
            wrongEnumType.effectiveKotlinMutability(DtoKotlinMutability.IMMUTABLE)
        }
        assertFailsWith<IllegalStateException> {
            unsupportedConstant.effectiveKotlinMutability(DtoKotlinMutability.IMMUTABLE)
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
            source = SOURCE,
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
        annotations: List<DtoAnnotation> = listOfNotNull(annotation),
        baseTypeId: LsiSymbolId? = BASE_TYPE_ID,
    ): DtoType {
        return DtoType(
            id = id,
            baseTypeId = baseTypeId,
            packageName = "demo.dto",
            name = id.value.substringAfterLast('-').replaceFirstChar(Char::uppercase),
            modifiers = emptySet(),
            annotations = annotations,
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
    }

    private fun kotlinDtoAnnotation(immutability: String): DtoAnnotation {
        return kotlinDtoAnnotation(
            DtoAnnotationValue.EnumValue(
                enumTypeId = KOTLIN_DTO_IMMUTABILITY_TYPE_ID,
                constant = immutability,
            ),
        )
    }

    private fun kotlinDtoAnnotation(immutability: DtoAnnotationValue): DtoAnnotation {
        return DtoAnnotation(
            typeId = KOTLIN_DTO_ANNOTATION_TYPE_ID,
            arguments = listOf(
                DtoAnnotationArgument(
                    name = "immutability",
                    value = immutability,
                ),
            ),
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("demo/src/main/dto/Book.dto")
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
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
