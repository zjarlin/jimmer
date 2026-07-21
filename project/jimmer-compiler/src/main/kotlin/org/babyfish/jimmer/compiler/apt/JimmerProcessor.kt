package org.babyfish.jimmer.compiler.apt

import org.babyfish.jimmer.apt.Context
import org.babyfish.jimmer.apt.GeneratorException
import org.babyfish.jimmer.apt.MetaException
import org.babyfish.jimmer.apt.dto.DtoProcessor
import org.babyfish.jimmer.apt.immutable.ImmutableProcessor
import org.babyfish.jimmer.compiler.ddl.JimmerDdlCompilerFeatureProvider
import org.babyfish.jimmer.compiler.dto.dtoGenerationReady
import org.babyfish.jimmer.compiler.immutable.immutableGenerationReady
import org.babyfish.jimmer.compiler.lsi.apt.AptLsiCompilerDriver
import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.dto.compiler.DtoBundleLoader
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.dto.compiler.DtoUtils
import org.babyfish.jimmer.dto.compiler.SourceTypeFilter
import org.babyfish.jimmer.sql.EnableDtoGeneration
import java.io.IOException
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.lang.model.SourceVersion
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.tools.Diagnostic
import javax.tools.StandardLocation

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
)
class JimmerProcessor : AbstractProcessor() {

    private lateinit var lsiDriver: AptLsiCompilerDriver

    private lateinit var context: Context

    private lateinit var elements: Elements

    private lateinit var messager: javax.annotation.processing.Messager

    private lateinit var dtoDirs: Collection<String>

    private lateinit var dtoTestDirs: Collection<String>

    private var dtoBundleEnabled = true

    private var defaultNullableInputModifier = DtoModifier.STATIC

    private var dtoGenerated = false

    private lateinit var dtoFieldModifier: Modifier

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
        val includes = processingEnv.options["jimmer.source.includes"]
        val excludes = processingEnv.options["jimmer.source.excludes"]
        dtoDirs = dtoDirs(
            processingEnv,
            "jimmer.dto.dirs",
            "src/main/",
            listOf("src/main/dto"),
        )
        dtoTestDirs = dtoDirs(
            processingEnv,
            "jimmer.dto.testDirs",
            "src/test/",
            listOf("src/test/dto"),
        )
        dtoBundleEnabled = DtoBundleLoader.isEnabled(processingEnv.options)
        defaultNullableInputModifier = processingEnv.options["jimmer.dto.defaultNullableInputModifier"]
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                when (it) {
                    "fixed" -> DtoModifier.FIXED
                    "static" -> DtoModifier.STATIC
                    "dynamic" -> DtoModifier.DYNAMIC
                    "fuzzy" -> DtoModifier.FUZZY
                    else -> throw IllegalArgumentException(
                        "The apt options `jimmer.dto.defaultNullableInputModifier` can only be " +
                            "\"fixed\", \"static\", \"dynamic\" or \"fuzzy\"",
                    )
                }
            } ?: DtoModifier.STATIC
        dtoFieldModifier = when (val visibility = processingEnv.options["jimmer.dto.fieldVisibility"]) {
            null, "private" -> Modifier.PRIVATE
            "protected" -> Modifier.PROTECTED
            "public" -> Modifier.PUBLIC
            else -> throw IllegalArgumentException(
                "The apt options `jimmer.dto.fieldVisibility` can only be " +
                    "\"private\", \"protected\" or \"public\"",
            )
        }
        context = Context(
            processingEnv.elementUtils,
            processingEnv.typeUtils,
            processingEnv.filer,
            processingEnv.options["jimmer.keepIsPrefix"] == "true",
            SourceTypeFilter(includes, excludes),
            detectIsJackson3(processingEnv),
            processingEnv.options["jimmer.entry.immutables"],
            processingEnv.options["jimmer.entry.tables"],
            processingEnv.options["jimmer.entry.tableExes"],
            processingEnv.options["jimmer.entry.fetchers"],
            processingEnv.options["jimmer.dto.hibernateValidatorEnhancement"] == "true",
            processingEnv.options["jimmer.buddy.ignoreResourceGeneration"] == "true",
            dtoFieldModifier,
        )
        elements = processingEnv.elementUtils
    }

    override fun process(
        annotations: MutableSet<out TypeElement>,
        roundEnv: RoundEnvironment,
    ): Boolean {
        try {
            val lsiRoundResult = lsiDriver.process(roundEnv)
            var generated = lsiRoundResult.generatedSources
            if (!roundEnv.processingOver() && lsiRoundResult.immutableGenerationReady()) {
                val immutableTypeElements = ImmutableProcessor(context, messager).process(roundEnv).keys
                generated = generated || immutableTypeElements.isNotEmpty()
            }
            if (
                !roundEnv.processingOver() &&
                !generated &&
                !dtoGenerated &&
                lsiRoundResult.dtoGenerationReady()
            ) {
                dtoGenerated = true
                generated = DtoProcessor(
                    context,
                    elements,
                    if (isTest()) dtoTestDirs else dtoDirs,
                    dtoBundleEnabled,
                    defaultNullableInputModifier,
                ).process()
            }
            if (generated) {
                return true
            }
        } catch (ex: MetaException) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                ex.message ?: ex.javaClass.name,
                ex.element,
            )
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

    private fun isTest(): Boolean {
        try {
            val path = context.filer.getResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                "dummy.txt",
            ).toUri().path
            return path.endsWith("/test/dummy.txt")
        } catch (ex: IOException) {
            throw GeneratorException("Cannot get the class output dir", ex)
        }
    }

    companion object {

        private val COMPILER_OPTIONS = setOf(
            "jimmer.buddy.ignoreResourceGeneration",
            "jimmer.client.checkedException",
            "jimmer.dto.defaultNullableInputModifier",
            DtoBundleLoader.ENABLED_OPTION,
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

        private fun detectIsJackson3(processingEnv: ProcessingEnvironment): Boolean {
            val jackson3 = processingEnv.options["jimmer.jackson3"]
            return if (jackson3.isNullOrEmpty()) {
                processingEnv.elementUtils.getTypeElement("tools.jackson.databind.ObjectMapper") != null
            } else {
                jackson3 == "true"
            }
        }

        private fun dtoDirs(
            environment: ProcessingEnvironment,
            configurationName: String,
            prefix: String,
            defaultDirs: Collection<String>,
        ): Collection<String> {
            val configuredDirs = environment.options[configurationName]
            if (configuredDirs.isNullOrEmpty()) {
                return defaultDirs
            }
            val dirs = linkedSetOf<String>()
            for (configuredPath in configuredDirs.trim().split(Regex("\\s*[,:;]\\s*"))) {
                var path = configuredPath
                if (path.isEmpty() || path == "/") {
                    continue
                }
                if (path.startsWith('/')) {
                    path = path.substring(1)
                }
                if (path.endsWith('/')) {
                    path = path.substring(0, path.length - 1)
                }
                if (path.isNotEmpty()) {
                    dirs += path
                }
            }
            for (dir in dirs) {
                if (!dir.startsWith(prefix)) {
                    throw GeneratorException(
                        "Illegal annotation processor configuration \"$configurationName\", it contains an " +
                            "illegal path \"$dir\" which does not start with \"$prefix\"",
                        null,
                    )
                }
            }
            return DtoUtils.standardDtoDirs(dirs)
        }
    }
}
