package site.addzero.lsi.jimmer.dto

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DtoPairedProcessorAuditTest {

    @Test
    fun `apt dto processor stays on shared lsi generator path`() {
        val source = DtoTestSupport.readSource("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/dto/DtoProcessor.java")

        assertTrue(
            source.contains("DtoProcessorSupport.generateFileSpecs("),
            "APT DTO processor must drive DTO generation through the shared processor support",
        )
        assertTrue(
            source.contains("DtoProcessorSupport.collectDtoFiles("),
            "APT DTO processor must collect DTO files through the shared processor support",
        )
        assertTrue(
            source.contains("Context.INSTANCE.getLsiFiler().createSourceFile(fileSpec);"),
            "APT DTO processor must write the shared LsiFileSpec through shared LsiFiler",
        )
        assertTrue(
            source.contains("Context.INSTANCE::findDraftImplDocMap"),
            "APT DTO processor must pass draft impl doc callback through the shared DTO support",
        )
        assertFalse(
            source.contains("JavaFile"),
            "APT DTO processor must not build JavaPoet files directly",
        )
        assertFalse(
            source.contains("org.babyfish.jimmer.dto.compiler.DtoFile"),
            "APT DTO processor shell must not expose raw dto compiler files after LSI DTO surface extraction",
        )
        assertFalse(
            source.contains("org.babyfish.jimmer.dto.compiler.DtoModifier"),
            "APT DTO processor shell must not expose raw dto compiler modifiers after LSI DTO surface extraction",
        )
        assertFalse(
            source.contains("import site.addzero.lsi.jimmer.client.DocMetadata;"),
            "APT DTO processor shell must not import DocMetadata directly after shared support absorbs metadata assembly",
        )
        assertFalse(
            source.contains("com.squareup.javapoet"),
            "APT DTO processor must not import JavaPoet",
        )
        assertFalse(
            source.contains("org.babyfish.jimmer.apt.dto.DtoGenerator"),
            "APT DTO processor must not depend on an APT-only DTO business generator",
        )
        assertFalse(
            source.contains("DtoSourceTypeSupportExtKt.resolveDtoSourceTypeOrNull("),
            "APT DTO processor must not keep DTO source type resolution in the shell after support extraction",
        )
    }

    @Test
    fun `ksp dto processor stays on shared lsi generator path`() {
        val source = DtoTestSupport.readSource("project/compiler/dto/jimmer-ksp-dto/src/main/kotlin/org/babyfish/jimmer/ksp/dto/DtoProcessor.kt")

        assertTrue(
            source.contains("import site.addzero.lsi.jimmer.dto.DtoProcessorSupport"),
            "KSP DTO processor must import the shared DTO processor support",
        )
        assertTrue(
            source.contains("Context.sourceAnchorFilePath"),
            "KSP DTO processor must obtain DTO anchor path through shared Context",
        )
        assertTrue(
            source.contains("draftImplDocMapOf = { type, annotationQualifiedName, valueAttributeName ->") &&
                source.contains("Context.findDraftImplDocMap(type, annotationQualifiedName, valueAttributeName)"),
            "KSP DTO processor must pass draft impl doc callback through shared DTO support",
        )
        assertTrue(
            source.contains("DtoProcessorSupport.generateFileSpecs(") &&
                source.contains("resolver = Context.lsiResolver") &&
                source.contains("includeDtoSourceType = { it.matchesConfiguredSourceFilters() }"),
            "KSP DTO processor must delegate DTO compilation to shared processor support",
        )
        assertTrue(
            source.contains("Context.lsiFiler.createSourceFile(fileSpec)"),
            "KSP DTO processor must write the shared LsiFileSpec through LsiFiler",
        )
        assertFalse(
            source.contains("import org.babyfish.jimmer.dto.compiler.DtoFile"),
            "KSP DTO processor shell must not import raw dto compiler files after LSI DTO surface extraction",
        )
        assertFalse(
            source.contains("import org.babyfish.jimmer.dto.compiler.DtoModifier"),
            "KSP DTO processor shell must not import raw dto compiler modifiers after LSI DTO surface extraction",
        )
        assertFalse(
            source.contains("import site.addzero.lsi.jimmer.client.DocMetadata"),
            "KSP DTO processor shell must not import DocMetadata directly after shared support absorbs metadata assembly",
        )
        assertFalse(
            source.contains("DtoGenerator("),
            "KSP DTO processor must not instantiate DTO generator directly after support extraction",
        )
        assertFalse(
            source.contains("resolveDtoSourceTypeOrNull("),
            "KSP DTO processor must not keep DTO source type resolution in the shell after support extraction",
        )
        assertFalse(
            source.contains("toKotlinPoet("),
            "KSP DTO processor must not render KotlinPoet directly",
        )
        assertFalse(
            source.contains("com.squareup.kotlinpoet"),
            "KSP DTO processor must not import KotlinPoet",
        )
    }

}
