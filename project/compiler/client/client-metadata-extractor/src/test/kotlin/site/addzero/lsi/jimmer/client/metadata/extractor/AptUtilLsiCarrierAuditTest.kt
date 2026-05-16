package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AptUtilLsiCarrierAuditTest {

    @Test
    fun `apt util helpers stay lsi first and poet free`() {
        for (file in aptUtilSources()) {
            val source = Files.readString(file)
            assertFalse(source.contains("import com.squareup.javapoet"), "${file.fileName} must not import JavaPoet")
            assertFalse(source.contains("import com.squareup.kotlinpoet"), "${file.fileName} must not import KotlinPoet")
            assertFalse(source.contains("toJavaPoet("), "${file.fileName} must not call toJavaPoet")
            assertFalse(source.contains("toKotlinPoet("), "${file.fileName} must not call toKotlinPoet")
            assertFalse(source.contains("renderJavaSource("), "${file.fileName} must not render Java source directly")
            assertFalse(source.contains("renderKotlinSource("), "${file.fileName} must not render Kotlin source directly")
        }
    }

    @Test
    fun `apt util lsi carrier entrypoints stay explicit`() {
        val sharedAnnotations = CompilerAuditTestSupport.sourceOf(
            "project/compiler/jimmer-ksp-ext/src/main/kotlin/site/addzero/lsi/codegen/JimmerCodegenAnnotationExt.kt"
        )
        assertTrue(sharedAnnotations.contains("fun generatedAnnotation()"), sharedAnnotations)
        assertTrue(sharedAnnotations.contains("fun suppressWarningsAllAnnotation()"), sharedAnnotations)
        assertFalse(
            Files.exists(
                CompilerAuditTestSupport.repoRoot.resolve(
                    "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/util/GeneratedAnnotation.java"
                )
            )
        )
        assertFalse(
            Files.exists(
                CompilerAuditTestSupport.repoRoot.resolve(
                    "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/util/SuppressAnnotation.java"
                )
            )
        )

        val aptLsiClassNames = CompilerAuditTestSupport.sourceOf(
            "lib/lsi/lsi-apt/src/main/kotlin/site/addzero/lsi/apt/clazz/AptLsiClassNames.kt"
        )
        assertTrue(aptLsiClassNames.contains("LsiClassName"), aptLsiClassNames)
        assertTrue(aptLsiClassNames.contains("object AptLsiClassNames"), aptLsiClassNames)
        assertTrue(aptLsiClassNames.contains("@JvmStatic"), aptLsiClassNames)
        assertTrue(aptLsiClassNames.contains("fun of("), aptLsiClassNames)

        assertFalse(
            Files.exists(
                CompilerAuditTestSupport.repoRoot.resolve(
                    "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/util/ConverterMetadata.java"
                )
            )
        )
        val sharedConverterMetadata = CompilerAuditTestSupport.sourceOf(
            "project/compiler/jimmer-ksp-ext/src/main/kotlin/site/addzero/lsi/codegen/ConverterMetadata.kt"
        )
        assertTrue(sharedConverterMetadata.contains("fun converterMetadataOf(declaration: LsiClass)"), sharedConverterMetadata)
        assertTrue(sharedConverterMetadata.contains("open fun toListMetadata()"), sharedConverterMetadata)

        val strings = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/util/Strings.java"
        )
        assertTrue(strings.contains("public class Strings"), strings)
        assertTrue(strings.contains("public static String upper(String text)"), strings)

        assertFalse(
            Files.exists(
                CompilerAuditTestSupport.repoRoot.resolve(
                    "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/util/GenericParser.java"
                )
            )
        )
        assertFalse(
            Files.exists(
                CompilerAuditTestSupport.repoRoot.resolve(
                    "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/util/RecursiveAnnotations.java"
                )
            )
        )
        assertFalse(
            Files.exists(
                CompilerAuditTestSupport.repoRoot.resolve(
                    "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/meta/ValidationMessages.java"
                )
            )
        )
        assertFalse(
            Files.exists(
                CompilerAuditTestSupport.repoRoot.resolve(
                    "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/DraftDocMetadataSupport.java"
                )
            )
        )
        val lsiAnnotationExt = CompilerAuditTestSupport.sourceOf(
            "lib/lsi/lsi-core/src/main/kotlin/site/addzero/lsi/anno/LsiAnnotationExt.kt"
        )
        assertTrue(lsiAnnotationExt.contains("fun List<LsiAnnotation>.recursiveAnnotation("), lsiAnnotationExt)
        val aptClassDocExt = CompilerAuditTestSupport.sourceOf(
            "lib/lsi/lsi-apt/src/main/kotlin/site/addzero/lsi/apt/clazz/AptLsiClassDocExt.kt"
        )
        assertTrue(aptClassDocExt.contains("fun LsiClass.findAptDraftImplDocMap("), aptClassDocExt)
        assertFalse(
            Files.exists(
                CompilerAuditTestSupport.repoRoot.resolve(
                    "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/meta/ImmutableProp.java"
                )
            )
        )
        assertFalse(
            Files.exists(
                CompilerAuditTestSupport.repoRoot.resolve(
                    "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/meta/ImmutableType.java"
                )
            )
        )

        assertFalse(
            Files.exists(
                CompilerAuditTestSupport.repoRoot.resolve(
                    "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/Context.java"
                )
            )
        )

        val clientProcessor = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client/ClientProcessor.java"
        )
        assertTrue(clientProcessor.contains("Context.INSTANCE::findDraftImplDocMap"), clientProcessor)
        assertTrue(clientProcessor.contains("Context.INSTANCE::convertedLsiTypeNameOf"), clientProcessor)
        assertTrue(clientProcessor.contains("ClientProcessorSupport.collectClientSchemaServiceTypeNames"), clientProcessor)
        assertFalse(clientProcessor.contains(".typeOf(owner)"), clientProcessor)

        val aptClassSemanticsExt = CompilerAuditTestSupport.sourceOf(
            "lib/lsi/lsi-apt/src/main/kotlin/site/addzero/lsi/apt/clazz/AptLsiClassSemanticsExt.kt"
        )
        assertTrue(aptClassSemanticsExt.contains("fun TypeElement.isImmutableType()"), aptClassSemanticsExt)
        assertTrue(aptClassSemanticsExt.contains("fun TypeElement.matchesConfiguredSourceFilters()"), aptClassSemanticsExt)

        val dtoProcessor = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/dto/DtoProcessor.java"
        )
        assertTrue(dtoProcessor.contains("Context.INSTANCE::findDraftImplDocMap"), dtoProcessor)
        assertTrue(dtoProcessor.contains("Context.INSTANCE.getSourceAnchorFilePath()"), dtoProcessor)
        assertTrue(dtoProcessor.contains("Context.INSTANCE.option(\"jimmer.dto.mutable\")"), dtoProcessor)
        assertTrue(dtoProcessor.contains("LsiSourceFilterKt::matchesConfiguredSourceFilters"), dtoProcessor)
        assertFalse(dtoProcessor.contains("Context context;"), dtoProcessor)
        assertFalse(dtoProcessor.contains("AptLsiContext.INSTANCE"), dtoProcessor)
        assertFalse(dtoProcessor.contains("AptLsiResourceFiles"), dtoProcessor)

        val errorProcessor = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/error/ErrorProcessor.java"
        )
        assertTrue(errorProcessor.contains("LsiSourceFilterKt::matchesConfiguredSourceFilters"), errorProcessor)

        val jimmerProcessor = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/JimmerProcessor.java"
        )
        assertFalse(jimmerProcessor.contains("new Context("), jimmerProcessor)
        assertTrue(jimmerProcessor.contains("Context.INSTANCE.snapshotAllTypeNames()"), jimmerProcessor)
        assertTrue(jimmerProcessor.contains("Context.INSTANCE.setDelayedTupleTypeNames("), jimmerProcessor)

        val typedTupleProcessor = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/tuple/TypedTupleProcessor.java"
        )
        assertFalse(typedTupleProcessor.contains("org.babyfish.jimmer.apt.Context"), typedTupleProcessor)
        assertTrue(typedTupleProcessor.contains("Context.INSTANCE.getDelayedTupleTypeNames()"), typedTupleProcessor)

        assertTrue(clientProcessor.contains("Context.INSTANCE.getDelayedClientTypeNames()"), clientProcessor)
    }

    private fun aptUtilSources(): List<Path> {
        val utilRoot = CompilerAuditTestSupport.repoRoot
            .resolve("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/util")
        return CompilerAuditTestSupport.collectSourceFiles(listOf(utilRoot))
    }
}
