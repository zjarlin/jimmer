package org.babyfish.jimmer.compiler.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.dto.dtoGenerationReady
import org.babyfish.jimmer.compiler.dto.dtoStateOrNull
import org.babyfish.jimmer.compiler.input.toDtoFile
import org.babyfish.jimmer.compiler.immutable.immutableStateOrNull
import org.babyfish.jimmer.compiler.lsi.ksp.KspLsiCompilerDriver
import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.ksp.Context
import org.babyfish.jimmer.ksp.MetaException
import org.babyfish.jimmer.ksp.dto.DtoProcessor

class JimmerProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val lsiDriver = KspLsiCompilerDriver(environment)
        return object : SymbolProcessor {

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
                        val dtoState = requireNotNull(lsiRoundResult.dtoStateOrNull()) {
                            "DTO generation requires the frozen DTO compiler state"
                        }
                        val immutableState = requireNotNull(lsiRoundResult.immutableStateOrNull()) {
                            "DTO generation requires the frozen immutable compiler state"
                        }
                        val generatedDto = DtoProcessor(
                            ctx = context,
                            dtoFiles = lsiRoundResult.round.inputDocumentSnapshots
                                .asSequence()
                                .map { snapshot -> snapshot.document }
                                .filter { document -> document.kind == CompilerInputDocumentKind.DTO }
                                .map { document -> document.toDtoFile() }
                                .toList(),
                            defaultNullableInputModifier = dtoState.defaultNullableInputModifier,
                            graphs = dtoState.graphs,
                            immutableSchema = immutableState.schema,
                            jacksonVersion = dtoState.rendererOptions.jacksonVersion,
                            effectiveMutableByRootTypeId = dtoState.effectiveKspMutableByRootTypeId,
                            workspace = lsiRoundResult.round.workspace,
                            annotationContractsBySource = dtoState.annotationContractsBySource,
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
