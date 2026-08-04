package site.addzero.lsi.apt

import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.ObjectOutputStream
import java.io.Reader
import java.io.StringReader
import java.net.URI
import javax.annotation.processing.Filer
import javax.lang.model.element.Element
import javax.tools.FileObject
import javax.tools.JavaFileManager
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardLocation
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AptCompilerInputResourceReaderTest {

    @Test
    fun `reads existing resources ignores missing paths and freezes content`() {
        val entities = MutableResourceFileObject(
            "META-INF/lsi-test/entities",
            "demo.Book\n",
        )
        val immutables = MutableResourceFileObject(
            "META-INF/lsi-test/types",
            "demo.Author\n",
        )
        val filer = ResourceFiler(
            mapOf(
                "META-INF/lsi-test/entities" to entities,
                "META-INF/lsi-test/types" to immutables,
            ),
        )
        val reader = AptCompilerInputResourceReader(filer)

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
        assertEquals(1, entities.closedReaderCount)
        assertEquals(1, immutables.closedReaderCount)
        assertTrue(snapshot.isDetachedSerializableSnapshot())

        entities.content = "demo.ChangedBook\n"

        assertEquals("demo.Book\n", snapshot.getValue("META-INF/lsi-test/entities"))
        assertEquals(
            "demo.ChangedBook\n",
            reader.read(setOf("META-INF/lsi-test/entities"))
                .getValue("META-INF/lsi-test/entities"),
        )
        assertEquals(2, entities.closedReaderCount)
    }

    private class ResourceFiler(
        private val resources: Map<String, FileObject>,
    ) : Filer {

        override fun createSourceFile(
            name: CharSequence,
            vararg originatingElements: Element,
        ): JavaFileObject = error("This filer only supports input resources")

        override fun createClassFile(
            name: CharSequence,
            vararg originatingElements: Element,
        ): JavaFileObject = error("This filer only supports input resources")

        override fun createResource(
            location: JavaFileManager.Location,
            moduleAndPkg: CharSequence,
            relativeName: CharSequence,
            vararg originatingElements: Element,
        ): FileObject = error("This filer only supports input resources")

        override fun getResource(
            location: JavaFileManager.Location,
            moduleAndPkg: CharSequence,
            relativeName: CharSequence,
        ): FileObject {
            assertEquals(StandardLocation.CLASS_OUTPUT, location)
            assertEquals("", moduleAndPkg.toString())
            return resources[relativeName.toString()]
                ?: throw FileNotFoundException(relativeName.toString())
        }
    }

    private class MutableResourceFileObject(
        path: String,
        var content: String,
    ) : SimpleJavaFileObject(URI.create("mem:///$path"), JavaFileObject.Kind.OTHER) {

        var closedReaderCount: Int = 0
            private set

        override fun openReader(ignoreEncodingErrors: Boolean): Reader {
            val delegate = StringReader(content)
            return object : Reader() {
                override fun read(
                    buffer: CharArray,
                    offset: Int,
                    length: Int,
                ): Int = delegate.read(buffer, offset, length)

                override fun close() {
                    delegate.close()
                    closedReaderCount++
                }
            }
        }
    }

    private fun Map<String, String>.isDetachedSerializableSnapshot(): Boolean {
        val output = ByteArrayOutputStream()
        ObjectOutputStream(output).use { stream ->
            stream.writeObject(this)
        }
        return output.size() > 0
    }
}
