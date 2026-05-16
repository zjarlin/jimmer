package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class AptResourceBoundaryAuditTest {

    @Test
    fun `apt main sources do not use raw resource filer apis`() {
        for (file in aptSources()) {
            val source = Files.readString(file)
            assertFalse(source.contains("getResource("), "${file.name} must not call raw getResource")
            assertFalse(source.contains("createResource("), "${file.name} must not call raw createResource")
            assertFalse(source.contains("FileObject"), "${file.name} must not depend on FileObject")
            assertFalse(source.contains("StandardLocation"), "${file.name} must not depend on StandardLocation")
        }
    }

    @Test
    fun `apt main sources do not carry raw filer state anymore`() {
        val filerImportOwners = aptSources()
            .filter { Files.readString(it).contains("import javax.annotation.processing.Filer") }
            .map { it.fileName.toString() }
            .sorted()
        assertEquals(emptyList<String>(), filerImportOwners)
        assertFalse(
            Files.exists(
                CompilerAuditTestSupport.repoRoot.resolve("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/Context.java")
            ),
            "APT legacy Context.java should be deleted"
        )
    }

    @Test
    fun `apt main sources use boundary helpers instead of direct processing environment and raw apt filer construction`() {
        for (file in aptSources()) {
            val source = Files.readString(file)
            assertFalse(
                source.contains("AptLsiContext.INSTANCE.getProcessingEnvironment("),
                "${file.name} must not pull ProcessingEnvironment directly from AptLsiContext"
            )
            assertFalse(
                source.contains("new AptLsiFiler("),
                "${file.name} must not construct AptLsiFiler directly in APT business shell"
            )
            assertFalse(
                source.contains("AptLsiResourceFiles"),
                "${file.name} must not bypass LsiFiler through AptLsiResourceFiles"
            )
        }
    }

    private fun aptSources(): List<Path> {
        val aptRoot = CompilerAuditTestSupport.repoRoot
            .resolve("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt")
        return CompilerAuditTestSupport.collectSourceFiles(listOf(aptRoot))
    }
}
