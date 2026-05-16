package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import java.nio.file.Path
import kotlin.io.path.readText

class ImmutableKotlinSidecarSourceMappingAuditTest {

    @Test
    fun `kotlin sidecar artifact emitters stay pinned to the approved generators`() {
        val sources = generatorSources()

        assertEquals(
            setOf("DraftGenerator.kt", "PropsGenerator.kt", "FetcherGenerator.kt"),
            filesMatching(
                sources = sources,
                regex = Regex("""name\s*=\s*"\$\{[^}]+}Dsl""""),
            ),
        )
        assertEquals(
            setOf("PropsGenerator.kt"),
            filesContaining(sources, "topLevelProperties ="),
        )
        assertEquals(
            setOf("DraftGenerator.kt", "PropsGenerator.kt", "FetcherGenerator.kt"),
            filesContaining(sources, "topLevelCallables ="),
        )
    }

    @Test
    fun `kotlin sidecar lambda sources stay mapped to the approved generators`() {
        val sources = generatorSources()

        assertEquals(
            setOf(
                "DraftGenerator.kt",
                "PropsGenerator.kt",
                "FetcherGenerator.kt",
                "FetcherDslGenerator.kt",
                "ImmutableCallbackMetadataExt.kt",
            ),
            filesContaining(sources, "toLsiLambdaTypeName("),
        )

        val fetcherDsl = sources.getValue("FetcherDslGenerator.kt")
        assertTrue(
            "topLevelCallables =" !in fetcherDsl,
            "FetcherDslGenerator 只能贡献嵌套 DSL lambda，不得自己发射顶层 sidecar callable",
        )
        assertTrue(
            "LsiFileSpec(" !in fetcherDsl,
            "FetcherDslGenerator 不得直接发射独立 sidecar file",
        )
    }

    @Test
    fun `kotlin sidecar extension emitters remain coupled to the dsl emitters`() {
        val sources = generatorSources()

        val draftGenerator = sources.getValue("DraftGenerator.kt")
        val propsGenerator = sources.getValue("PropsGenerator.kt")
        val fetcherGenerator = sources.getValue("FetcherGenerator.kt")

        assertTrue(
            "receiverType = type.draftClassName" in draftGenerator,
            "BookDraftDsl 的扩展 callable 必须继续由 DraftGenerator 显式生成",
        )
        assertTrue(
            "receiverType = type.receiverTypeName" in draftGenerator,
            "DraftGenerator 的 addBy/newBy/copy 扩展 receiver 不得丢失",
        )
        assertTrue(
            "receiverType = receiverType" in propsGenerator,
            "BookPropsDsl 的顶层扩展 property 必须继续由 PropsGenerator 显式生成",
        )
        assertTrue(
            "receiverType = K_PROPS_LSI_CLASS_NAME.parameterizedBy(type.className)" in propsGenerator,
            "BookPropsDsl 的 list predicate 扩展 callable 必须继续由 PropsGenerator 显式生成",
        )
        assertTrue(
            "receiverType = fetcherCreatorType(type.className)" in fetcherGenerator,
            "BookFetcherDsl 的顶层扩展 callable 必须继续由 FetcherGenerator 显式生成",
        )
    }

    private fun generatorSources(): Map<String, String> =
        ImmutableTestSupport.generatorFiles()
            .associateBy(Path::getFileName)
            .mapKeys { (fileName, _) -> fileName.toString() }
            .mapValues { (_, path) -> path.readText() }

    private fun filesContaining(
        sources: Map<String, String>,
        needle: String,
    ): Set<String> =
        sources
            .filterValues { it.contains(needle) }
            .keys

    private fun filesMatching(
        sources: Map<String, String>,
        regex: Regex,
    ): Set<String> =
        sources
            .filterValues { regex.containsMatchIn(it) }
            .keys
}
