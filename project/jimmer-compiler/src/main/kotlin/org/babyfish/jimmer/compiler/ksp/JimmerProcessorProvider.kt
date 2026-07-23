package org.babyfish.jimmer.compiler.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.dto.dtoGenerationReady
import org.babyfish.jimmer.compiler.input.toDtoFile
import org.babyfish.jimmer.compiler.lsi.ksp.KspLsiCompilerDriver
import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.ksp.Context
import org.babyfish.jimmer.ksp.MetaException
import org.babyfish.jimmer.ksp.dto.DtoProcessor

class JimmerProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val lsiDriver = KspLsiCompilerDriver(environment)
        return object : SymbolProcessor {

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

            private val dtoMutable =
                environment.options["jimmer.dto.mutable"]?.trim() == "true"

            private var dtoGenerated = false

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
                )
                return deferred.toList()
            }

            override fun finish() {
                lsiDriver.finish()
            }

            private fun processJimmer(
                resolver: Resolver,
                lsiRoundResult: org.babyfish.jimmer.compiler.CompilerRoundResult,
            ) {
                try {
                    val context = Context(resolver, environment)
                    var generated = lsiRoundResult.generatedSources
                    if (generated) {
                        return
                    }
                    if (!dtoGenerated && lsiRoundResult.dtoGenerationReady()) {
                        dtoGenerated = true
                        val generatedDto = DtoProcessor(
                            context,
                            dtoMutable,
                            lsiRoundResult.round.inputDocumentSnapshots
                                .asSequence()
                                .map { snapshot -> snapshot.document }
                                .filter { document -> document.kind == CompilerInputDocumentKind.DTO }
                                .map { document -> document.toDtoFile() }
                                .toList(),
                            defaultNullableInputModifier,
                        ).process()
                        generated = generated || generatedDto
                    }
                    if (generated) {
                        return
                    }
                } catch (ex: MetaException) {
                    environment.logger.error(ex.message ?: ex.javaClass.name, ex.declaration)
                } catch (ex: DtoAstException) {
                    environment.logger.error(ex.message ?: ex.javaClass.name)
                }
            }
        }
    }
}
