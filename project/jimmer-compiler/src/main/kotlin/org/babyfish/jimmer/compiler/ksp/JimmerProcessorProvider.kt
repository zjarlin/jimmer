package org.babyfish.jimmer.compiler.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.babyfish.jimmer.client.EnableImplicitApi
import org.babyfish.jimmer.compiler.dto.dtoGenerationReady
import org.babyfish.jimmer.compiler.dto.dtoGenerationTerminal
import org.babyfish.jimmer.compiler.lsi.ksp.KspLsiCompilerDriver
import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.dto.compiler.DtoBundleLoader
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.dto.compiler.DtoUtils
import org.babyfish.jimmer.ksp.Context
import org.babyfish.jimmer.ksp.GeneratorException
import org.babyfish.jimmer.ksp.MetaException
import org.babyfish.jimmer.ksp.annotation
import org.babyfish.jimmer.ksp.client.ClientProcessor
import org.babyfish.jimmer.ksp.dto.DtoProcessor
import org.babyfish.jimmer.ksp.immutable.ImmutableProcessor
import java.util.regex.Pattern

class JimmerProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val lsiDriver = KspLsiCompilerDriver(environment)
        return object : SymbolProcessor {

            private val dtoDirs =
                dtoDir("jimmer.dto.dirs", "src/main/") ?: listOf("src/main/dto")

            private val dtoTestDirs =
                dtoDir("jimmer.dto.testDirs", "src/test/") ?: listOf("src/test/dto")

            private val dtoBundleEnabled =
                DtoBundleLoader.isEnabled(environment.options)

            private val defaultNullableInputModifier =
                environment.options["jimmer.dto.defaultNullableInputModifier"]
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

            private val checkedException =
                environment.options["jimmer.client.checkedException"]?.trim() == "true"

            private val dtoMutable =
                environment.options["jimmer.dto.mutable"]?.trim() == "true"

            private val excludedUserAnnotationPrefixes =
                environment.options["jimmer.excludedUserAnnotationPrefixes"]
                    ?.trim()
                    ?.let { SEPARATOR.split(it).toList() }
                    ?: emptyList()

            private var dtoGenerated = false

            private var explicitClientApi = false

            private var clientContent: String? = null

            private var clientReadyInLatestRound = false

            override fun process(resolver: Resolver): List<KSAnnotated> {
                val deferred = linkedSetOf<KSAnnotated>()
                val lsiDeferred = lsiDriver.process(resolver)
                deferred += lsiDeferred
                val lsiRoundResult = requireNotNull(lsiDriver.lastRoundResult) {
                    "LSI driver must expose the current KSP round result"
                }
                processJimmer(
                    resolver = resolver,
                    lsiRoundResult = lsiRoundResult,
                    hasInvalidDeferred = lsiDeferred.isNotEmpty(),
                )
                return deferred.toList()
            }

            override fun finish() {
                lsiDriver.finish()
                if (clientReadyInLatestRound) {
                    clientContent?.let { content ->
                        environment.codeGenerator.createNewFile(
                            dependencies = Dependencies.ALL_FILES,
                            packageName = "META-INF.jimmer",
                            fileName = "client",
                            extensionName = "",
                        ).bufferedWriter().use { writer ->
                            writer.write(content)
                        }
                    }
                }
            }

            private fun processJimmer(
                resolver: Resolver,
                lsiRoundResult: org.babyfish.jimmer.compiler.CompilerRoundResult,
                hasInvalidDeferred: Boolean,
            ) {
                clientReadyInLatestRound = false
                clientContent = null
                try {
                    val context = Context(resolver, environment)
                    explicitClientApi = explicitClientApi || resolver.getAllFiles().any { file ->
                        file.declarations.any {
                            it is KSClassDeclaration &&
                                context.include(it) &&
                                it.annotation(EnableImplicitApi::class) != null
                        }
                    }
                    val processedDeclarations = ImmutableProcessor(
                        context,
                        excludedUserAnnotationPrefixes,
                    ).process()
                    var generated = lsiRoundResult.generatedSources || processedDeclarations.isNotEmpty()
                    if (generated) {
                        return
                    }
                    if (!dtoGenerated && lsiRoundResult.dtoGenerationReady()) {
                        dtoGenerated = true
                        val generatedDto = DtoProcessor(
                            context,
                            dtoMutable,
                            if (
                                resolver.getAllFiles().toList().isNotEmpty() &&
                                isTest(context.resolver.getAllFiles().first().filePath)
                            ) {
                                dtoTestDirs
                            } else {
                                dtoDirs
                            },
                            dtoBundleEnabled,
                            defaultNullableInputModifier,
                        ).process()
                        generated = generated || generatedDto
                    }
                    if (generated) {
                        return
                    }
                    if (
                        hasInvalidDeferred ||
                        !lsiRoundResult.dtoGenerationTerminal() ||
                        lsiRoundResult.unresolvedSymbols.isNotEmpty()
                    ) {
                        return
                    }
                    if (!context.isBuddyIgnoreResourceGeneration) {
                        clientContent = ClientProcessor(
                            context,
                            explicitClientApi,
                        ).render()
                    }
                    clientReadyInLatestRound = !context.isBuddyIgnoreResourceGeneration
                } catch (ex: MetaException) {
                    environment.logger.error(ex.message ?: ex.javaClass.name, ex.declaration)
                } catch (ex: DtoAstException) {
                    environment.logger.error(ex.message ?: ex.javaClass.name)
                }
            }

            private fun dtoDir(configurationName: String, prefix: String): Collection<String>? =
                environment.options[configurationName]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { text ->
                        text.split("\\s*[,:;]\\s*")
                            .map {
                                when {
                                    it == "" || it == "/" -> null
                                    it.startsWith("/") -> it.substring(1)
                                    it.endsWith("/") -> it.substring(0, it.length - 1)
                                    else -> it.takeIf { value -> value.isNotEmpty() }
                                }?.also { dir ->
                                    if (!dir.startsWith(prefix)) {
                                        throw GeneratorException(
                                            "Illegal KSP configuration \"$configurationName\", it contains an " +
                                                "illegal path \"$dir\" which does not start with \"$prefix\"",
                                        )
                                    }
                                }
                            }
                            .filterNotNull()
                            .toSet()
                    }
                    ?.let { DtoUtils.standardDtoDirs(it) }
        }
    }

    companion object {

        private val SEPARATOR = Pattern.compile("\\s+|\\s*[,;]\\s*")

        private fun isTest(path: String): Boolean {
            val testIndex = path.indexOf("/src/test/")
            if (testIndex == -1) {
                return false
            }
            val mainIndex = path.indexOf("/src/main/")
            return mainIndex == -1 || testIndex < mainIndex
        }
    }
}
