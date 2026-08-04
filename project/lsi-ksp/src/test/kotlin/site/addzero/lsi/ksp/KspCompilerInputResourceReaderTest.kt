package site.addzero.lsi.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.io.OutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KspCompilerInputResourceReaderTest {

    @Test
    fun `reads existing resources ignores missing paths and freezes content`() {
        val projectDirectory = createTempDirectory(prefix = "compiler-ksp-input-resource-test").toFile()
        val generatedRoot = projectDirectory.resolve("build/generated/ksp/test")
        val generatedSource = generatedRoot.resolve("kotlin/demo/Generated.kt")
        generatedSource.parentFile.mkdirs()
        generatedSource.writeText("package demo\n")
        val entities = generatedRoot.resolve("resources/META-INF/lsi-test/entities")
        entities.parentFile.mkdirs()
        entities.writeText("demo.Book\n")
        val immutables = generatedRoot.resolve("resources/META-INF/lsi-test/types")
        immutables.writeText("demo.Author\n")
        val codeGenerator = ResourceCodeGenerator(
            listOf(
                projectDirectory.resolve("unrelated/generated.txt"),
                generatedSource,
            ),
        )
        val reader = KspCompilerInputResourceReader(codeGenerator)

        val snapshot = reader.read(
            linkedSetOf(
                "META-INF/lsi-test/missing",
                "META-INF/lsi-test/types",
                "META-INF/lsi-test/entities",
            ),
        )

        assertContentEquals(
            listOf(
                "META-INF/lsi-test/entities",
                "META-INF/lsi-test/types",
            ),
            snapshot.keys,
        )
        assertEquals("demo.Book\n", snapshot.getValue("META-INF/lsi-test/entities"))
        assertEquals("demo.Author\n", snapshot.getValue("META-INF/lsi-test/types"))
        assertTrue(snapshot.isDetachedSerializableSnapshot())

        entities.writeText("demo.ChangedBook\n")

        assertEquals("demo.Book\n", snapshot.getValue("META-INF/lsi-test/entities"))
        assertEquals(
            "demo.ChangedBook\n",
            reader.read(setOf("META-INF/lsi-test/entities"))
                .getValue("META-INF/lsi-test/entities"),
        )
    }

    private class ResourceCodeGenerator(
        override val generatedFile: Collection<File>,
    ) : CodeGenerator {

        override fun createNewFile(
            dependencies: Dependencies,
            packageName: String,
            fileName: String,
            extensionName: String,
        ): OutputStream = error("This generator only supports input resources")

        override fun createNewFileByPath(
            dependencies: Dependencies,
            path: String,
            extensionName: String,
        ): OutputStream = error("This generator only supports input resources")

        override fun associate(
            sources: List<KSFile>,
            packageName: String,
            fileName: String,
            extensionName: String,
        ) = Unit

        override fun associateByPath(
            sources: List<KSFile>,
            path: String,
            extensionName: String,
        ) = Unit

        override fun associateWithClasses(
            classes: List<KSClassDeclaration>,
            packageName: String,
            fileName: String,
            extensionName: String,
        ) = Unit
    }

    private fun Map<String, String>.isDetachedSerializableSnapshot(): Boolean {
        val output = ByteArrayOutputStream()
        ObjectOutputStream(output).use { stream ->
            stream.writeObject(this)
        }
        return output.size() > 0
    }
}
