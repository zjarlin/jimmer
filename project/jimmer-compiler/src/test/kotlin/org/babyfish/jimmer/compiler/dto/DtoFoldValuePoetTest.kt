package org.babyfish.jimmer.compiler.dto

import com.squareup.javapoet.CodeBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import org.babyfish.jimmer.compiler.render.apt.AptDtoFoldValueRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoFoldValueRenderer
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoFoldProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetTypeName

class DtoFoldValuePoetTest {

    @Test
    fun `renders direct fold construction for both target languages`() {
        val fixture = fixture(guarded = false)

        assertEquals(
            "new demo.dto.BookView.TargetOf_summary(base)",
            fixture.renderJava(),
        )
        assertEquals(
            "demo.dto.BookView.TargetOf_summary(base)",
            fixture.renderKotlin(),
        )
    }

    @Test
    fun `renders null guarded fold construction for both target languages`() {
        val fixture = fixture(guarded = true)

        assertEquals(
            "SUMMARY_NULL_GUARD_ACCESSOR.get(base) != null ? " +
                "new demo.dto.BookView.TargetOf_summary(base) : null",
            fixture.renderJava(),
        )
        assertEquals(
            "SUMMARY_NULL_GUARD_ACCESSOR.get<kotlin.Any?>(base)?.let { " +
                "demo.dto.BookView.TargetOf_summary(base) }",
            fixture.renderKotlin(),
        )
    }

    private data class Fixture(
        val prop: DtoFoldProp,
        val graph: DtoGraph,
    ) {
        fun renderJava(): String {
            return renderJavaCodeBlock().toString()
        }

        fun renderKotlin(): String {
            return KspDtoFoldValueRenderer.render(
                prop = prop,
                graph = graph,
                workspace = LsiWorkspace.EMPTY,
                baseParameterName = "base",
                nullGuardAccessorName = "SUMMARY_NULL_GUARD_ACCESSOR",
                generatedTargetType = { LsiDeclaredType(TARGET_TYPE_NAME.typeId) },
                generatedTypeNames = listOf(TARGET_TYPE_NAME),
            ).toString()
        }

        private fun renderJavaCodeBlock(): CodeBlock {
            return AptDtoFoldValueRenderer.render(
                prop = prop,
                graph = graph,
                workspace = LsiWorkspace.EMPTY,
                baseParameterName = "base",
                nullGuardAccessorName = "SUMMARY_NULL_GUARD_ACCESSOR",
                generatedTargetType = { LsiDeclaredType(TARGET_TYPE_NAME.typeId) },
                generatedTypeNames = listOf(TARGET_TYPE_NAME),
            )
        }
    }

    private companion object {
        val SOURCE = LsiSource.of("src/main/dto/demo/BookView.dto", LsiLanguage.UNKNOWN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val ROOT_TYPE_ID = DtoTypeId("demo.dto.BookView#root")
        val TARGET_TYPE_ID = DtoTypeId("demo.dto.BookView#target:summary")
        val FOLD_PROP_ID = DtoPropId("demo.dto.BookView#prop:summary")
        val GUARD_PROP_ID = DtoPropId("demo.dto.BookView#prop:summaryGuard")
        val IMMUTABLE_PROP_ID = LsiSymbolId.property(LsiSymbolId.type("demo.Book"), "summary")
        val TARGET_TYPE_NAME = LsiPoetTypeName(
            typeId = LsiSymbolId.type("demo.dto.BookView.TargetOf_summary"),
            packageName = "demo.dto",
            simpleNames = listOf("BookView", "TargetOf_summary"),
        )

        fun fixture(guarded: Boolean): Fixture {
            val foldProp = DtoFoldProp(
                id = FOLD_PROP_ID,
                ownerTypeId = ROOT_TYPE_ID,
                name = "summary",
                alias = "summary",
                nullable = true,
                annotations = emptyList(),
                documentation = null,
                aliasLocation = LOCATION,
                nullGuardPropId = GUARD_PROP_ID.takeIf { guarded },
                targetTypeId = TARGET_TYPE_ID,
            )
            val guardProp = DtoBaseProp(
                id = GUARD_PROP_ID,
                ownerTypeId = ROOT_TYPE_ID,
                name = "summaryGuard",
                alias = null,
                nullable = false,
                annotations = emptyList(),
                documentation = null,
                aliasLocation = LOCATION,
                baseLocation = LOCATION,
                baseProps = listOf(DtoBasePropBinding("summary", IMMUTABLE_PROP_ID)),
                basePath = "summary",
                nextPropId = null,
                tailPropId = GUARD_PROP_ID,
                baseNullable = false,
                inputModifier = DtoModifier.STATIC,
                functionName = null,
                targetTypeId = null,
                enumType = null,
                config = null,
                recursive = false,
                likeOptions = emptySet(),
            )
            val rootType = DtoType(
                id = ROOT_TYPE_ID,
                baseTypeId = LsiSymbolId.type("demo.Book"),
                packageName = "demo.dto",
                name = "BookView",
                modifiers = emptySet(),
                annotations = emptyList(),
                superInterfaces = emptyList(),
                documentation = null,
                location = LOCATION,
                focusedRecursion = false,
                propIds = if (guarded) listOf(FOLD_PROP_ID, GUARD_PROP_ID) else listOf(FOLD_PROP_ID),
                hiddenFlatPropIds = emptyList(),
                polymorphism = null,
            )
            val targetType = DtoType(
                id = TARGET_TYPE_ID,
                baseTypeId = LsiSymbolId.type("demo.Summary"),
                packageName = "demo.dto",
                name = "TargetOf_summary",
                modifiers = emptySet(),
                annotations = emptyList(),
                superInterfaces = emptyList(),
                documentation = null,
                location = LOCATION,
                focusedRecursion = false,
                propIds = emptyList(),
                hiddenFlatPropIds = emptyList(),
                polymorphism = null,
            )
            return Fixture(
                prop = foldProp,
                graph = DtoGraph(
                    source = SOURCE,
                    rootTypeIds = listOf(ROOT_TYPE_ID),
                    types = listOf(rootType, targetType).sortedBy(DtoType::id),
                    props = if (guarded) listOf(foldProp, guardProp) else listOf(foldProp),
                ),
            )
        }
    }
}
