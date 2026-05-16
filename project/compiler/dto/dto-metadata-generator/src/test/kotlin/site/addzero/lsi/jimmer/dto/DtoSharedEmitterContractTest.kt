package site.addzero.lsi.jimmer.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DtoSharedEmitterContractTest {

    @Test
    fun `dto processor support only assembles file specs through DtoGenerator`() {
        val source = DtoTestSupport.readSource(
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/DtoProcessorSupport.kt"
        )

        assertTrue(source.contains("val generator = DtoGenerator("), source)
        assertTrue(source.contains("generator.generate()?.let(fileSpecs::add)"), source)
        assertFalse(source.contains("InputBuilderGenerator("), source)
        assertFalse(source.contains("SerializerGenerator("), source)
        assertFalse(source.contains("LsiFileSpec("), source)
    }

    @Test
    fun `DtoGenerator stays the only dto shared file emitter`() {
        val dtoGenerator = DtoTestSupport.readSource(
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/DtoGenerator.kt"
        )
        val inputBuilderGenerator = DtoTestSupport.readSource(
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/InputBuilderGenerator.kt"
        )
        val serializerGenerator = DtoTestSupport.readSource(
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/SerializerGenerator.kt"
        )

        assertTrue(dtoGenerator.contains("fun generate(): LsiFileSpec? {"), dtoGenerator)
        assertTrue(dtoGenerator.contains("LsiFileSpec("), dtoGenerator)
        assertTrue(dtoGenerator.contains("addNestedType(SerializerGenerator(this).generate())"), dtoGenerator)
        assertTrue(dtoGenerator.contains("addNestedType(InputBuilderGenerator(this).generate())"), dtoGenerator)

        assertTrue(inputBuilderGenerator.contains("fun generate(): LsiTypeSpec ="), inputBuilderGenerator)
        assertFalse(inputBuilderGenerator.contains("LsiFileSpec("), inputBuilderGenerator)
        assertFalse(inputBuilderGenerator.contains("createSourceFile("), inputBuilderGenerator)

        assertTrue(serializerGenerator.contains("fun generate(): LsiTypeSpec ="), serializerGenerator)
        assertFalse(serializerGenerator.contains("LsiFileSpec("), serializerGenerator)
        assertFalse(serializerGenerator.contains("createSourceFile("), serializerGenerator)
    }

    @Test
    fun `dto shared source tree keeps file emitters constrained to DtoGenerator`() {
        val fileEmitterFiles = DtoTestSupport.dtoSharedSources()
            .filter { source ->
                val text = java.nio.file.Files.readString(source)
                text.contains("LsiFileSpec(")
            }
            .map { it.fileName.toString() }
            .sorted()

        assertEquals(listOf("DtoGenerator.kt"), fileEmitterFiles)
    }
}
