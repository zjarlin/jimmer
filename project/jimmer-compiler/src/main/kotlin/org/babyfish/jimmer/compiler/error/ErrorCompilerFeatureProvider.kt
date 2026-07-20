package org.babyfish.jimmer.compiler.error

import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureRenderResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.error.apt.ErrorJavaRenderer
import org.babyfish.jimmer.compiler.error.ksp.ErrorKotlinRenderer
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity

class ErrorCompilerFeatureProvider : JimmerCompilerFeatureProvider {
    override val descriptor = JimmerCompilerFeatureDescriptor("error")

    override fun precompile(
        context: JimmerCompilerPrecompileContext,
    ): JimmerCompilerFeaturePrecompileResult {
        return try {
            val sourceFilter = JimmerCompilerSourceFilter.from(context.round.options)
            val targetTypeIds = context.round.workspace
                .declarationsOfType<LsiTypeDeclaration>()
                .asSequence()
                .filter { type -> type.origin.kind in COMPILATION_ORIGIN_KINDS }
                .filter { type -> sourceFilter.accepts(type.qualifiedName) }
                .filter { type -> type.isVisibleOn(context.round.platform) }
                .filter { type ->
                    type.annotations.any { annotation -> annotation.type == ERROR_FAMILY_ANNOTATION }
                }
                .mapTo(sortedSetOf(), LsiTypeDeclaration::id)
            val schema = ErrorPrecompiler(
                ErrorPrecompileOptions(
                    checkedException = context.round.options["jimmer.client.checkedException"] == "true"
                )
            ).compile(context.round.workspace, targetTypeIds)
            val state = ErrorCompilerFeatureState(
                status = ErrorCompilerFeatureStatus.RESOLVED,
                schema = schema,
            )
            JimmerCompilerFeaturePrecompileResult(
                state = state,
                processedSymbols = schema.families.mapTo(sortedSetOf()) { family -> family.id },
            )
        } catch (exception: ErrorPrecompileException) {
            JimmerCompilerFeaturePrecompileResult(
                state = ErrorCompilerFeatureState.invalid(exception),
                diagnostics = listOf(
                    LsiDiagnostic(
                        code = "jimmer.error.invalid",
                        severity = LsiDiagnosticSeverity.ERROR,
                        message = exception.message ?: "Invalid error family",
                        symbolId = exception.declarationId,
                    )
                ),
            )
        }
    }

    override fun render(
        context: JimmerCompilerRenderContext,
    ): JimmerCompilerFeatureRenderResult {
        if (context.round.isFinal) {
            return JimmerCompilerFeatureRenderResult()
        }
        val state = context.state as ErrorCompilerFeatureState
        if (state.status != ErrorCompilerFeatureStatus.RESOLVED || state.schema.families.isEmpty()) {
            return JimmerCompilerFeatureRenderResult()
        }
        val artifacts = when (context.round.platform) {
            CompilerPlatform.APT -> ErrorJavaRenderer().render(state.schema, context.round.workspace)
            CompilerPlatform.KSP -> ErrorKotlinRenderer().render(state.schema, context.round.workspace)
            CompilerPlatform.UNKNOWN -> emptyList()
        }
        return JimmerCompilerFeatureRenderResult(artifacts = artifacts)
    }
}

private val COMPILATION_ORIGIN_KINDS = setOf(
    LsiOriginKind.SOURCE,
    LsiOriginKind.GENERATED,
)

private val ERROR_FAMILY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.error.ErrorFamily")

private fun LsiTypeDeclaration.isVisibleOn(platform: CompilerPlatform): Boolean {
    return when (platform) {
        CompilerPlatform.APT -> annotations.none { annotation ->
            annotation.type == LsiSymbolId.type("kotlin.Metadata")
        }
        CompilerPlatform.KSP -> origin.language != LsiLanguage.JAVA
        CompilerPlatform.UNKNOWN -> true
    }
}

internal enum class ErrorCompilerFeatureStatus {
    RESOLVED,
    INVALID,
}

internal data class ErrorCompilerFeatureState(
    val status: ErrorCompilerFeatureStatus,
    val schema: ErrorPrecompiledSchema,
    override val fingerprint: String = "${status.name}:${schema.fingerprint()}",
) : JimmerCompilerFeatureState {
    companion object {
        fun invalid(exception: ErrorPrecompileException): ErrorCompilerFeatureState {
            return ErrorCompilerFeatureState(
                status = ErrorCompilerFeatureStatus.INVALID,
                schema = ErrorPrecompiledSchema(emptyList()),
                fingerprint = "INVALID:${exception.declarationId.value}:${exception.message.orEmpty()}",
            )
        }
    }
}
