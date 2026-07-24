package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation

class DtoKotlinPropertyExtensionsTest {
    @Test
    fun `selects concrete property placement by fixed priority`() {
        val cases = listOf(
            placements(
                DtoAnnotationPlacement.FIELD,
                DtoAnnotationPlacement.GETTER,
                DtoAnnotationPlacement.SETTER,
                DtoAnnotationPlacement.PROPERTY,
            ) to DtoAnnotationPlacement.FIELD,
            placements(
                DtoAnnotationPlacement.GETTER,
                DtoAnnotationPlacement.SETTER,
                DtoAnnotationPlacement.PROPERTY,
            ) to DtoAnnotationPlacement.GETTER,
            placements(
                DtoAnnotationPlacement.SETTER,
                DtoAnnotationPlacement.PROPERTY,
            ) to DtoAnnotationPlacement.SETTER,
            placements(DtoAnnotationPlacement.PROPERTY) to DtoAnnotationPlacement.PROPERTY,
        )

        cases.forEach { (placements, expected) ->
            val application = application(
                typeName = "demo.Annotation",
                origin = DtoAnnotationOrigin.DTO,
                placements = placements,
            )

            assertEquals(
                expected,
                application.kotlinPropertyPlacement(DtoKotlinPropertyShape.CONCRETE),
            )
        }
    }

    @Test
    fun `selects abstract accessor placement only from getter and property`() {
        val getterApplication = application(
            typeName = "demo.GetterAnnotation",
            placements = placements(
                DtoAnnotationPlacement.FIELD,
                DtoAnnotationPlacement.GETTER,
                DtoAnnotationPlacement.SETTER,
                DtoAnnotationPlacement.PROPERTY,
            ),
        )
        val propertyApplication = application(
            typeName = "demo.PropertyAnnotation",
            placements = placements(
                DtoAnnotationPlacement.FIELD,
                DtoAnnotationPlacement.SETTER,
                DtoAnnotationPlacement.PROPERTY,
            ),
        )
        val unavailableApplication = application(
            typeName = "demo.FieldAnnotation",
            placements = placements(
                DtoAnnotationPlacement.FIELD,
                DtoAnnotationPlacement.SETTER,
            ),
        )

        assertEquals(
            DtoAnnotationPlacement.GETTER,
            getterApplication.kotlinPropertyPlacement(DtoKotlinPropertyShape.ABSTRACT_ACCESSOR),
        )
        assertEquals(
            DtoAnnotationPlacement.PROPERTY,
            propertyApplication.kotlinPropertyPlacement(DtoKotlinPropertyShape.ABSTRACT_ACCESSOR),
        )
        assertNull(
            unavailableApplication.kotlinPropertyPlacement(DtoKotlinPropertyShape.ABSTRACT_ACCESSOR),
        )
    }

    @Test
    fun `returns null when concrete property has no supported placement`() {
        val application = application(
            typeName = "demo.TypeAnnotation",
            placements = placements(DtoAnnotationPlacement.TYPE),
        )

        assertNull(application.kotlinPropertyPlacement(DtoKotlinPropertyShape.CONCRETE))
    }

    @Test
    fun `forces getter only for dto annotations in exact jackson package`() {
        val dtoJackson = application(
            typeName = "com.fasterxml.jackson.annotation.JsonProperty",
            origin = DtoAnnotationOrigin.DTO,
            placements = placements(
                DtoAnnotationPlacement.FIELD,
                DtoAnnotationPlacement.GETTER,
                DtoAnnotationPlacement.PROPERTY,
            ),
        )
        val immutableJackson = application(
            typeName = "com.fasterxml.jackson.annotation.JsonProperty",
            origin = DtoAnnotationOrigin.IMMUTABLE,
            placements = dtoJackson.placements,
        )
        val toolsJackson = application(
            typeName = "tools.jackson.annotation.JsonProperty",
            origin = DtoAnnotationOrigin.DTO,
            placements = dtoJackson.placements,
        )
        val adjacentPackage = application(
            typeName = "com.fasterxml.jacksonish.annotation.JsonProperty",
            origin = DtoAnnotationOrigin.DTO,
            placements = dtoJackson.placements,
        )

        DtoKotlinPropertyShape.entries.forEach { shape ->
            assertEquals(DtoAnnotationPlacement.GETTER, dtoJackson.kotlinPropertyPlacement(shape))
        }
        assertEquals(
            DtoAnnotationPlacement.FIELD,
            immutableJackson.kotlinPropertyPlacement(DtoKotlinPropertyShape.CONCRETE),
        )
        assertEquals(
            DtoAnnotationPlacement.FIELD,
            toolsJackson.kotlinPropertyPlacement(DtoKotlinPropertyShape.CONCRETE),
        )
        assertEquals(
            DtoAnnotationPlacement.FIELD,
            adjacentPackage.kotlinPropertyPlacement(DtoKotlinPropertyShape.CONCRETE),
        )
    }

    @Test
    fun `rejects dto jackson annotation without getter placement`() {
        val application = application(
            typeName = "com.fasterxml.jackson.annotation.JsonProperty",
            origin = DtoAnnotationOrigin.DTO,
            placements = placements(
                DtoAnnotationPlacement.FIELD,
                DtoAnnotationPlacement.PROPERTY,
            ),
        )

        DtoKotlinPropertyShape.entries.forEach { shape ->
            val error = assertFailsWith<IllegalArgumentException> {
                application.kotlinPropertyPlacement(shape)
            }

            assertEquals(
                "DTO Jackson annotation does not support GETTER placement: " +
                    "com.fasterxml.jackson.annotation.JsonProperty",
                error.message,
            )
        }
    }

    private fun application(
        typeName: String,
        origin: DtoAnnotationOrigin = DtoAnnotationOrigin.DTO,
        placements: List<DtoAnnotationPlacement>,
    ): DtoAnnotationApplication {
        return DtoAnnotationApplication(
            annotation = LsiAnnotation(LsiSymbolId.type(typeName), emptyMap()),
            origin = origin,
            sourceSymbolId = null,
            placements = placements,
        )
    }

    private fun placements(
        vararg placements: DtoAnnotationPlacement,
    ): List<DtoAnnotationPlacement> {
        return placements.sorted()
    }
}
