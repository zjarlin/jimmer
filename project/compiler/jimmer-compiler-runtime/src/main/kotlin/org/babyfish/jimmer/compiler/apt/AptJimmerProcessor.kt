package org.babyfish.jimmer.compiler.apt

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import org.babyfish.jimmer.compiler.CompilerFailureTranslation
import org.babyfish.jimmer.compiler.CompilerFailureTranslator
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProviders

/**
 * 聚合所有编译功能的通用 APT 生命周期入口。
 */
open class AptJimmerProcessor(
    providers: Iterable<JimmerCompilerFeatureProvider> = JimmerCompilerFeatureProviders.load(),
) : AbstractProcessor() {

    private val providerList = providers.toList()

    private lateinit var lsiDriver: AptLsiCompilerDriver

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

    override fun getSupportedAnnotationTypes(): MutableSet<String> = providerList
        .flatMapTo(sortedSetOf()) { provider -> provider.descriptor.aptAnnotationTypes }

    override fun getSupportedOptions(): MutableSet<String> = providerList
        .flatMapTo(sortedSetOf()) { provider -> provider.descriptor.supportedOptions }

    @Synchronized
    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        lsiDriver = AptLsiCompilerDriver(processingEnv, providerList)
    }

    override fun process(
        annotations: MutableSet<out TypeElement>,
        roundEnv: RoundEnvironment,
    ): Boolean {
        try {
            lsiDriver.process(roundEnv)
        } catch (failure: Throwable) {
            val translation = translateFailure(failure) ?: throw failure
            reportFailure(translation, failure, roundEnv)
        }
        return true
    }

    private fun translateFailure(failure: Throwable): CompilerFailureTranslation? {
        return providerList
            .asSequence()
            .filterIsInstance<CompilerFailureTranslator>()
            .mapNotNull { translator -> translator.translateFailure(failure) }
            .firstOrNull()
    }

    private fun reportFailure(
        translation: CompilerFailureTranslation,
        failure: Throwable,
        roundEnvironment: RoundEnvironment,
    ) {
        val annotationTypeName = translation.annotationTypeName
        if (annotationTypeName == null) {
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, translation.message)
            if (translation.rethrowWhenTargetMissing) {
                throw failure
            }
            return
        }
        val annotationType = processingEnv.elementUtils.getTypeElement(annotationTypeName)
        val annotatedElement = annotationType
            ?.let(roundEnvironment::getElementsAnnotatedWith)
            ?.firstOrNull()
        if (annotatedElement != null) {
            processingEnv.messager.printMessage(
                Diagnostic.Kind.ERROR,
                translation.message,
                annotatedElement,
            )
            return
        }
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, translation.message)
        if (translation.rethrowWhenTargetMissing) {
            throw failure
        }
    }
}
