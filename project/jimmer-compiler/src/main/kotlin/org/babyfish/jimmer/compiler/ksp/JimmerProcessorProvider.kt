package org.babyfish.jimmer.compiler.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.babyfish.jimmer.client.EnableImplicitApi
import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.dto.compiler.DtoUtils
import org.babyfish.jimmer.ksp.Context
import org.babyfish.jimmer.ksp.GeneratorException
import org.babyfish.jimmer.ksp.MetaException
import org.babyfish.jimmer.ksp.annotation
import org.babyfish.jimmer.ksp.client.ClientProcessor
import org.babyfish.jimmer.ksp.client.ExportDocProcessor
import org.babyfish.jimmer.ksp.dto.DtoProcessor
import org.babyfish.jimmer.ksp.error.ErrorProcessor
import org.babyfish.jimmer.ksp.immutable.ImmutableProcessor
import org.babyfish.jimmer.ksp.transactional.TxProcessor
import org.babyfish.jimmer.ksp.tuple.TypedTupleProcessor
import org.babyfish.jimmer.ksp.fullName
import java.util.regex.Pattern

class JimmerProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        object : SymbolProcessor {

            private val isModuleRequired =
                environment.options["jimmer.immutable.isModuleRequired"]?.trim() == "true"

            private val dtoDirs =
                dtoDir("jimmer.dto.dirs", "src/main/") ?: listOf("src/main/dto")

            private val dtoTestDirs =
                dtoDir("jimmer.dto.testDirs", "src/test/") ?: listOf("src/test/dto")

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

            private var serverGenerated = false

            private var explicitClientApi: Boolean? = null

            private var tupleGenerated = false

            private var delayedTupleTypeNames: Collection<String>? = null

            private var clientGenerated = false

            private var delayedClientTypeNames: Collection<String>? = null

            override fun process(resolver: Resolver): List<KSAnnotated> {
                return try {
                    val context = Context(resolver, environment)
                    if (explicitClientApi == null) {
                        explicitClientApi = resolver.getAllFiles().any { file ->
                            file.declarations.any {
                                it is KSClassDeclaration &&
                                    context.include(it) &&
                                    it.annotation(EnableImplicitApi::class) != null
                            }
                        }
                    }
                    val processedDeclarations = mutableListOf<KSClassDeclaration>()
                    if (!serverGenerated) {
                        processedDeclarations += ImmutableProcessor(
                            context,
                            isModuleRequired,
                            excludedUserAnnotationPrefixes,
                        ).process()
                        val errorGenerated = ErrorProcessor(context, checkedException).process()
                        val dtoGenerated = DtoProcessor(
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
                            defaultNullableInputModifier,
                        ).process()
                        TxProcessor(context).process()
                        ExportDocProcessor(context).process()
                        serverGenerated = true
                        if (processedDeclarations.isNotEmpty() || errorGenerated || dtoGenerated) {
                            delayedClientTypeNames = resolver.getAllFiles().flatMap { file ->
                                file.declarations.filterIsInstance<KSClassDeclaration>().map { it.fullName }
                            }.toList()
                            return processedDeclarations
                        }
                    }
                    if (!tupleGenerated) {
                        tupleGenerated = true
                        val processedTupleDeclarations =
                            TypedTupleProcessor(context, delayedTupleTypeNames).process()
                        if (processedTupleDeclarations.isNotEmpty()) {
                            return processedTupleDeclarations
                        }
                    }
                    if (tupleGenerated && !clientGenerated && !context.isBuddyIgnoreResourceGeneration) {
                        clientGenerated = true
                        ClientProcessor(
                            context,
                            explicitClientApi ?: error("Internal bug: explicitClientApi not resolved"),
                            delayedClientTypeNames,
                        ).process()
                        delayedClientTypeNames = null
                    }
                    processedDeclarations
                } catch (ex: MetaException) {
                    environment.logger.error(ex.message ?: ex.javaClass.name, ex.declaration)
                    emptyList()
                } catch (ex: DtoAstException) {
                    environment.logger.error(ex.message ?: ex.javaClass.name)
                    emptyList()
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
