package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import org.babyfish.jimmer.compiler.render.apt.AptDtoFoldDraftApplyRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoFoldDraftApplyRenderer
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.jimmer.dto.DtoFoldProp
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoTypeId

class DtoFoldDraftApplyPoetTest {

    @Test
    fun `renders direct fold application for both target languages`() {
        val prop = prop(nullable = false)

        assertEquals(
            "this.summary.__applyTo(__draft);\n",
            AptDtoFoldDraftApplyRenderer.render(prop, "__draft").toString(),
        )
        assertEquals(
            "this.summary.__applyTo(_draft)\n",
            KspDtoFoldDraftApplyRenderer.render(prop, "_draft").toString(),
        )
    }

    @Test
    fun `renders nullable fold application with language-specific guards`() {
        val prop = prop(nullable = true)

        assertEquals(
            """
                if (this.summary != null) {
                  this.summary.__applyTo(__draft);
                }
            """.trimIndent() + "\n",
            AptDtoFoldDraftApplyRenderer.render(prop, "__draft").toString(),
        )
        assertEquals(
            "this.summary?.__applyTo(_draft)\n",
            KspDtoFoldDraftApplyRenderer.render(prop, "_draft").toString(),
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("demo/Book.dto")
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val OWNER_ID = DtoTypeId("demo#BookView")
        val PROP_ID = DtoPropId("demo#BookView/prop:summary")
        val TARGET_ID = DtoTypeId("demo#Summary")

        fun prop(nullable: Boolean): DtoFoldProp {
            return DtoFoldProp(
                id = PROP_ID,
                ownerTypeId = OWNER_ID,
                name = "summary",
                alias = "summary",
                nullable = nullable,
                annotations = emptyList(),
                documentation = null,
                aliasLocation = LOCATION,
                nullGuardPropId = null,
                targetTypeId = TARGET_ID,
            )
        }
    }
}
