package site.addzero.lsi.jimmer.dto

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DtoAptProcessorShellContractTest {

    @Test
    fun `apt dto processor stays on shared lsi extraction and artifact path`() {
        val source = dtoProcessorSource()

        val requiredSnippets = listOf(
            "DtoProcessorSupport.collectDtoFiles(",
            "DtoProcessorSupport.generateFileSpecs(",
            "Context.INSTANCE.getSourceAnchorFilePath()",
            "Context.INSTANCE::findDraftImplDocMap",
            "Context.INSTANCE.getLsiFiler().createSourceFile(fileSpec);",
            "Context.INSTANCE.getLsiResolver()",
            "LsiSourceFilterKt::matchesConfiguredSourceFilters",
        )
        for (snippet in requiredSnippets) {
            assertTrue(source.contains(snippet), "DtoProcessor must contain `$snippet`\n$source")
        }
    }

    @Test
    fun `apt dto processor does not perform local rendering or call removed apt dto generator chain`() {
        val source = dtoProcessorSource()

        val forbiddenSnippets = listOf(
            "renderJavaSource(",
            "renderKotlinSource(",
            "com.squareup.javapoet",
            "com.squareup.kotlinpoet",
            "org.babyfish.jimmer.dto.compiler.DtoFile",
            "org.babyfish.jimmer.dto.compiler.DtoModifier",
            "AptLsiResourceFiles",
            "AptLsiContext.INSTANCE",
            "JavaFile",
            "org.babyfish.jimmer.apt.dto.DtoGenerator",
            "new org.babyfish.jimmer.apt.dto.DtoGenerator(",
            "new LsiDtoCompiler(",
            "new InputBuilderGenerator(",
            "new SerializerGenerator(",
            "DtoSourceTypeSupportExtKt.resolveDtoSourceTypeOrNull(",
        )
        for (snippet in forbiddenSnippets) {
            assertFalse(source.contains(snippet), "DtoProcessor must not contain `$snippet`\n$source")
        }
    }

    private fun dtoProcessorSource(): String =
        DtoTestSupport.readSource("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/dto/DtoProcessor.java")
}
