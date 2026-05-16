package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class AptEntryBoundaryAuditTest {

    @Test
    fun `apt entry sources stay on lsi source generation boundary`() {
        for (file in entrySources()) {
            val source = Files.readString(file)
            assertFalse(source.contains("import javax.lang.model"), "${file.name} must not import lang model types")
            assertFalse(source.contains("TypeElement"), "${file.name} must not depend on TypeElement")
            assertFalse(source.contains("PackageElement"), "${file.name} must not depend on PackageElement")
            assertFalse(source.contains("getTypeElement("), "${file.name} must not resolve APT elements directly")
            assertFalse(source.contains("AptLsiClassNames"), "${file.name} must not depend on apt-only class-name helpers")
            assertFalse(source.contains("import com.squareup.javapoet"), "${file.name} must not import JavaPoet")
            assertFalse(source.contains("import com.squareup.kotlinpoet"), "${file.name} must not import KotlinPoet")
            assertFalse(source.contains("toJavaPoet("), "${file.name} must not call toJavaPoet")
            assertFalse(source.contains("toKotlinPoet("), "${file.name} must not call toKotlinPoet")
            assertFalse(source.contains("renderJavaSource("), "${file.name} must not render Java source directly")
            assertFalse(source.contains("renderKotlinSource("), "${file.name} must not render Kotlin source directly")
        }
    }

    @Test
    fun `apt entry source writing stays centralized in abstract summary generator`() {
        val entrySourcesByName = entrySources().associateBy { it.fileName.toString() }
        val createSourceFileOwners = entrySourcesByName.values
            .filter { Files.readString(it).contains("createSourceFile(") }
            .map { it.fileName.toString() }
            .sorted()

        assertEquals(listOf("AbstractSummaryGenerator.java"), createSourceFileOwners)
        assertTrue(
            Files.readString(entrySourcesByName.getValue("AbstractSummaryGenerator.java")).contains("new LsiFileSpec("),
            "AbstractSummaryGenerator must wrap source writes by LsiFileSpec",
        )
    }

    @Test
    fun `apt entry subsystem stays apt only summary and index layer`() {
        val entryProcessor = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/entry/EntryProcessor.java"
        )

        CompilerAuditTestSupport.assertContainsAll(
            entryProcessor,
            listOf(
                "new IndexFileGenerator(",
                "new ImmutablesGenerator(",
                "new TablesGenerator(",
                "new FetchersGenerator(",
            ),
            "APT entry EntryProcessor",
        )
        CompilerAuditTestSupport.assertContainsNone(
            entryProcessor,
            listOf(
                "ImmutableProcessorSupport",
                "DtoProcessorSupport",
                "ClientProcessorSupport",
                "ErrorProcessorSupport",
                "TxProcessorSupport",
                "TypedTupleProcessorSupport",
                "ProcessorSpi",
                "ServiceLoader",
            ),
            "APT entry EntryProcessor",
        )

        for (file in entrySources()) {
            val source = Files.readString(file)
            assertFalse(
                source.contains("site.addzero.lsi.jimmer.") && source.contains("metadata.generator"),
                "${file.name} must not depend on paired shared metadata generators",
            )
            assertFalse(
                source.contains("site.addzero.lsi.jimmer.") && source.contains("metadata.extractor"),
                "${file.name} must not depend on paired shared metadata extractors",
            )
            assertFalse(source.contains("ProcessorSpi"), "${file.name} must not implement shared processor SPI")
        }
    }

    private fun entrySources(): List<Path> {
        val entryRoot = CompilerAuditTestSupport.repoRoot
            .resolve("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/entry")
        return CompilerAuditTestSupport.collectSourceFiles(listOf(entryRoot))
    }
}
