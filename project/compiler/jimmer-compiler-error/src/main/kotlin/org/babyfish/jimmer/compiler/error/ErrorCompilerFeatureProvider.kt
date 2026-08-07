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
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.jimmer.error.ErrorSchema
import site.addzero.lsi.jimmer.error.ErrorSchemaOptions
import site.addzero.lsi.jimmer.error.ErrorValidationException
import site.addzero.lsi.jimmer.error.fingerprint
import site.addzero.lsi.jimmer.error.toErrorSchema
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.poet.LsiPoetRenderer
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class ErrorCompilerFeatureProvider : JimmerCompilerFeatureProvider {
    override val descriptor = JimmerCompilerFeatureDescriptor(
        id = "error",
        aptAnnotationTypes = setOf("org.babyfish.jimmer.error.ErrorFamily"),
        supportedOptions = setOf("jimmer.client.checkedException"),
    )

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
            val schema = context.round.workspace.toErrorSchema(
                options = ErrorSchemaOptions(
                    checkedException = context.round.options["jimmer.client.checkedException"] == "true"
                ),
                targetTypeIds = targetTypeIds,
            )
            val state = ErrorCompilerFeatureState(
                status = ErrorCompilerFeatureStatus.RESOLVED,
                schema = schema,
            )
            JimmerCompilerFeaturePrecompileResult(
                state = state,
                processedSymbols = schema.families.mapTo(sortedSetOf()) { family -> family.id },
            )
        } catch (exception: ErrorValidationException) {
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
        val renderer: LsiPoetRenderer = when (context.round.platform) {
            CompilerPlatform.APT -> LsiJavaPoetRenderer()
            CompilerPlatform.KSP -> LsiKotlinPoetRenderer()
            CompilerPlatform.UNKNOWN -> return JimmerCompilerFeatureRenderResult()
        }
        val artifacts = state.schema
            .toLsiPoetArtifacts(context.round.workspace)
            .map(renderer::render)
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

enum class ErrorCompilerFeatureStatus {
    RESOLVED,
    INVALID,
}

data class ErrorCompilerFeatureState(
    val status: ErrorCompilerFeatureStatus,
    val schema: ErrorSchema,
    override val fingerprint: String = "${status.name}:${schema.fingerprint()}",
) : JimmerCompilerFeatureState {
    companion object {
        fun invalid(exception: ErrorValidationException): ErrorCompilerFeatureState {
            return ErrorCompilerFeatureState(
                status = ErrorCompilerFeatureStatus.INVALID,
                schema = ErrorSchema(emptyList()),
                fingerprint = "INVALID:${exception.declarationId.value}:${exception.message.orEmpty()}",
            )
        }
    }
}
