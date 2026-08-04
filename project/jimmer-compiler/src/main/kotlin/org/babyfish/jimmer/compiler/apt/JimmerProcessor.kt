package org.babyfish.jimmer.compiler.apt

import org.babyfish.jimmer.compiler.ddl.JimmerDdlCompilerFeatureProvider
import org.babyfish.jimmer.compiler.input.CompilerInputDocumentBundleReader
import org.babyfish.jimmer.compiler.input.CompilerInputDocumentBundleRenderer
import org.babyfish.jimmer.compiler.lsi.apt.AptLsiCompilerDriver
import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.sql.EnableDtoGeneration
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

@SupportedAnnotationTypes(
    "org.babyfish.jimmer.Immutable",
    "org.babyfish.jimmer.sql.Entity",
    "org.babyfish.jimmer.sql.MappedSuperclass",
    "org.babyfish.jimmer.sql.Embeddable",
    "org.babyfish.jimmer.sql.EnableDtoGeneration",
    "org.babyfish.jimmer.error.ErrorFamily",
    "org.babyfish.jimmer.client.EnableImplicitApi",
    "org.babyfish.jimmer.client.meta.Api",
    "org.babyfish.jimmer.client.ExportDoc",
    "org.springframework.web.bind.annotation.RestController",
    "org.babyfish.jimmer.sql.transaction.Tx",
    "org.babyfish.jimmer.sql.TypedTuple",
    "org.babyfish.jimmer.internal.GeneratedBy",
)
class JimmerProcessor : AbstractProcessor() {

    private lateinit var lsiDriver: AptLsiCompilerDriver

    private lateinit var messager: javax.annotation.processing.Messager

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

    override fun getSupportedOptions(): MutableSet<String> =
        buildSet {
            addAll(JimmerDdlCompilerFeatureProvider.SUPPORTED_OPTIONS)
            addAll(COMPILER_OPTIONS)
        }.toMutableSet()

    @Synchronized
    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        lsiDriver = AptLsiCompilerDriver(processingEnv)
        messager = processingEnv.messager
    }

    override fun process(
        annotations: MutableSet<out TypeElement>,
        roundEnv: RoundEnvironment,
    ): Boolean {
        try {
            lsiDriver.process(roundEnv)
        } catch (ex: DtoAstException) {
            val annotatedElements = roundEnv.getElementsAnnotatedWith(EnableDtoGeneration::class.java)
            if (annotatedElements.isEmpty()) {
                messager.printMessage(Diagnostic.Kind.ERROR, ex.message ?: ex.javaClass.name)
                throw ex
            }
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                ex.message ?: ex.javaClass.name,
                annotatedElements.first(),
            )
        }
        return true
    }

    companion object {

        private val COMPILER_OPTIONS = setOf(
            "jimmer.buddy.ignoreResourceGeneration",
            "jimmer.client.checkedException",
            "jimmer.dto.defaultNullableInputModifier",
            CompilerInputDocumentBundleReader.ENABLED_OPTION,
            CompilerInputDocumentBundleRenderer.BUNDLE_ID_OPTION,
            "jimmer.dto.dirs",
            "jimmer.dto.fieldVisibility",
            "jimmer.dto.hibernateValidatorEnhancement",
            "jimmer.dto.mutable",
            "jimmer.dto.testDirs",
            "jimmer.entry.fetchers",
            "jimmer.entry.immutables",
            "jimmer.entry.tableExes",
            "jimmer.entry.tables",
            "jimmer.excludedUserAnnotationPrefixes",
            "jimmer.immutable.isModuleRequired",
            "jimmer.jackson3",
            "jimmer.keepIsPrefix",
            "jimmer.source.excludes",
            "jimmer.source.includes",
        )

    }
}
