package org.babyfish.jimmer.compiler.ddl

import org.babyfish.jimmer.compiler.JimmerCompilerCollectContext
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureCollection
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureRenderResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import org.babyfish.jimmer.ddl.compiler.JimmerDdlCompiler
import org.babyfish.jimmer.ddl.compiler.JimmerDdlCompilerFiles
import org.babyfish.jimmer.ddl.compiler.JimmerDdlCompilerResult
import org.babyfish.jimmer.ddl.compiler.JimmerDdlCompilerSettings
import org.babyfish.jimmer.ddl.compiler.JimmerDdlEntityTableSnapshot
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiTypeDeclaration

/**
 * 在共享编译会话中收集实体，并在最终轮消费冻结后的 LSI 工作区。
 */
class JimmerDdlCompilerFeatureProvider : JimmerCompilerFeatureProvider {

    override val descriptor = JimmerCompilerFeatureDescriptor(DDL_FEATURE_ID)

    override fun collect(
        context: JimmerCompilerCollectContext,
    ): JimmerCompilerFeatureCollection {
        val currentSourcePaths = context.round.currentRootTypeIds.mapNotNullTo(sortedSetOf()) { typeId ->
            (context.round.currentWorkspace[typeId] as? LsiTypeDeclaration)?.origin?.source?.path
        }
        val entityTypeIds = context.round.currentWorkspace
            .declarationsOfType<LsiTypeDeclaration>()
            .filter { declaration ->
                declaration.isEntity() && (
                    declaration.id in context.round.currentRootTypeIds ||
                        declaration.origin.source?.path?.let(currentSourcePaths::contains) == true
                    )
            }
            .mapTo(sortedSetOf(), LsiTypeDeclaration::id)
        return JimmerCompilerFeatureCollection(
            state = JimmerDdlCompilerCollectionState(entityTypeIds),
        )
    }

    override fun precompile(
        context: JimmerCompilerPrecompileContext,
    ): JimmerCompilerFeaturePrecompileResult {
        val collection = context.collection.state as JimmerDdlCompilerCollectionState
        val previousState = context.previousState as? JimmerDdlCompilerFeatureState
        val entityTypeIds = buildSet {
            previousState?.entityTypeIds?.let(::addAll)
            addAll(collection.entityTypeIds)
        }.toSortedSet()
        val optionsFingerprint = context.round.options.ddlFingerprint()
        if (!context.round.isFinal) {
            return JimmerCompilerFeaturePrecompileResult(
                state = JimmerDdlCompilerFeatureState.collecting(
                    entityTypeIds = entityTypeIds,
                    optionsFingerprint = optionsFingerprint,
                ),
                processedSymbols = collection.entityTypeIds,
            )
        }
        if (
            previousState?.compiled == true &&
            previousState.entityTypeIds == entityTypeIds &&
            previousState.optionsFingerprint == optionsFingerprint
        ) {
            return JimmerCompilerFeaturePrecompileResult(
                state = previousState,
                diagnostics = previousState.results.flatMap(JimmerDdlCompilerResult::warningDiagnostics),
            )
        }

        val settingsList = JimmerDdlCompilerSettings.allFromOptions(context.round.options)
        val results = if (
            entityTypeIds.isEmpty() ||
            settingsList.none(JimmerDdlCompilerSettings::enabled)
        ) {
            emptyList()
        } else {
            settingsList.map { settings ->
                JimmerDdlCompiler.compile(
                    workspace = context.round.workspace,
                    entityTypeIds = entityTypeIds,
                    settings = settings,
                )
            }
        }
        return JimmerCompilerFeaturePrecompileResult(
            state = JimmerDdlCompilerFeatureState.compiled(
                entityTypeIds = entityTypeIds,
                optionsFingerprint = optionsFingerprint,
                results = results,
            ),
            diagnostics = results.flatMap(JimmerDdlCompilerResult::warningDiagnostics),
        )
    }

    override fun render(
        context: JimmerCompilerRenderContext,
    ): JimmerCompilerFeatureRenderResult {
        if (!context.round.isFinal) {
            return JimmerCompilerFeatureRenderResult()
        }
        val state = context.state as JimmerDdlCompilerFeatureState
        if (!state.compiled || state.results.isEmpty()) {
            return JimmerCompilerFeatureRenderResult()
        }
        return JimmerCompilerFeatureRenderResult(
            diagnostics = JimmerDdlFileRenderer.render(state.results),
        )
    }

    companion object {
        @JvmField
        val SUPPORTED_OPTIONS: Set<String> = linkedSetOf(
            "jimmerDdl.enabled",
            "jimmerDdl.profiles",
            "jimmerDdl.databaseType",
            "jimmerDdl.jdbcUrl",
            "jimmerDdl.jdbcUsername",
            "jimmerDdl.jdbcPassword",
            "jimmerDdl.jdbcSchema",
            "jimmerDdl.jdbcDriver",
            "jimmerDdl.springResourcePath",
            "jimmerDdl.springProfile",
            "jimmerDdl.outputFormat",
            "jimmerDdl.outputDir",
            "jimmerDdl.version",
            "jimmerDdl.description",
            "jimmerDdl.includePackages",
            "jimmerDdl.excludePackages",
            "jimmerDdl.includeForeignKeys",
            "jimmerDdl.includeIndexes",
            "jimmerDdl.includeComments",
            "jimmerDdl.includeSequences",
            "jimmerDdl.includeManyToManyTables",
            "jimmerDdl.compareDatabase",
            "jimmerDdl.allowDestructiveChanges",
            "jimmerDdl.nullabilityRepairOnly",
            "jimmerDdl.sourceFingerprint",
        )
    }
}

private data class JimmerDdlCompilerCollectionState(
    val entityTypeIds: Set<LsiSymbolId>,
    override val fingerprint: String = entityTypeIds.joinToString(",") { typeId -> typeId.value },
) : JimmerCompilerFeatureState

private data class JimmerDdlCompilerFeatureState(
    val entityTypeIds: Set<LsiSymbolId>,
    val optionsFingerprint: String,
    val compiled: Boolean,
    val results: List<JimmerDdlCompilerResult>,
    override val fingerprint: String = buildString {
        append(if (compiled) "compiled" else "collecting")
        append(':')
        append(optionsFingerprint)
        entityTypeIds.forEach { typeId ->
            append(':')
            append(typeId.value)
        }
        results.forEach { result ->
            append(':')
            append(result.settings.outputDir)
            append('/')
            append(result.settings.outputFileName)
            append(':')
            append(result.sql.length)
            append(':')
            append(result.sql)
            result.warnings.forEach { warning ->
                append(':')
                append(warning.length)
                append(':')
                append(warning)
            }
        }
    },
) : JimmerCompilerFeatureState {

    companion object {
        fun collecting(
            entityTypeIds: Set<LsiSymbolId>,
            optionsFingerprint: String,
        ): JimmerDdlCompilerFeatureState {
            return JimmerDdlCompilerFeatureState(
                entityTypeIds = entityTypeIds,
                optionsFingerprint = optionsFingerprint,
                compiled = false,
                results = emptyList(),
            )
        }

        fun compiled(
            entityTypeIds: Set<LsiSymbolId>,
            optionsFingerprint: String,
            results: List<JimmerDdlCompilerResult>,
        ): JimmerDdlCompilerFeatureState {
            return JimmerDdlCompilerFeatureState(
                entityTypeIds = entityTypeIds,
                optionsFingerprint = optionsFingerprint,
                compiled = true,
                results = results,
            )
        }
    }
}

private object JimmerDdlFileRenderer {

    fun render(results: List<JimmerDdlCompilerResult>): List<LsiDiagnostic> {
        return buildList {
            results.forEach { result ->
                JimmerDdlEntityTableSnapshot.writeGeneratedSnapshot(
                    entities = result.entities,
                    schema = result.snapshotSchema,
                    settings = result.settings,
                )
                if (!result.isEmpty) {
                    val outputFile = JimmerDdlCompilerFiles.writeOutputFile(result.settings, result.sql)
                    add(
                        LsiDiagnostic(
                            code = "jimmer.ddl.generated",
                            severity = LsiDiagnosticSeverity.INFO,
                            message = "Jimmer DDL generated: ${outputFile.absolutePath}",
                        )
                    )
                }
            }
        }
    }
}

private fun JimmerDdlCompilerResult.warningDiagnostics(): List<LsiDiagnostic> {
    return warnings.map { warning ->
        LsiDiagnostic(
            code = "jimmer.ddl.warning",
            severity = LsiDiagnosticSeverity.WARNING,
            message = warning,
        )
    }
}

private fun LsiTypeDeclaration.isEntity(): Boolean {
    return annotations.any { annotation -> annotation.type == ENTITY_ANNOTATION_ID }
}

private fun Map<String, String>.ddlFingerprint(): String {
    return entries.asSequence()
        .filter { (name, _) -> name.startsWith("jimmerDdl.") }
        .sortedBy(Map.Entry<String, String>::key)
        .joinToString("|") { (name, value) -> "$name=${value.length}:$value" }
}

const val DDL_FEATURE_ID = "ddl"

private val ENTITY_ANNOTATION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
