package org.babyfish.jimmer.compiler.ddl

import site.addzero.lsi.compiler.CompilerCollectContext
import site.addzero.lsi.compiler.CompilerFeature
import site.addzero.lsi.compiler.CompilerFeatureCollection
import site.addzero.lsi.compiler.CompilerFeatureMetadata
import site.addzero.lsi.compiler.CompilerFeaturePrecompileResult
import site.addzero.lsi.compiler.CompilerFeatureRenderResult
import site.addzero.lsi.compiler.CompilerFeatureState
import site.addzero.lsi.compiler.CompilerPrecompileContext
import site.addzero.lsi.compiler.CompilerRenderContext
import site.addzero.lsi.compiler.compilerFeatureKey
import org.babyfish.jimmer.ddl.compiler.JimmerDdlCompiler
import org.babyfish.jimmer.ddl.compiler.JimmerDdlCompilerFiles
import org.babyfish.jimmer.ddl.compiler.JimmerDdlCompilerResult
import org.babyfish.jimmer.ddl.compiler.JimmerDdlCompilerSettings
import org.babyfish.jimmer.ddl.compiler.JimmerDdlEntityTableSnapshot
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.clazz.LsiClass

/**
 * 在共享编译会话中收集实体，并在最终轮消费冻结后的 LSI 工作区。
 */
class DdlFeature : CompilerFeature<DdlCollectionState, DdlFeatureState> {

    override val key = Key

    override val metadata = CompilerFeatureMetadata(
        supportedOptions = setOf(
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
        ),
    )

    override fun collect(
        context: CompilerCollectContext,
    ): CompilerFeatureCollection<DdlCollectionState> {
        val currentSourcePaths = context.round.currentRootTypeIds.mapNotNullTo(sortedSetOf()) { typeId ->
            (context.round.currentWorkspace[typeId] as? LsiClass)?.origin?.source?.path
        }
        val entityTypeIds = context.round.currentWorkspace
            .declarationsOfType<LsiClass>()
            .filter { declaration ->
                declaration.isEntity() && (
                    declaration.id in context.round.currentRootTypeIds ||
                        declaration.origin.source?.path?.let(currentSourcePaths::contains) == true
                    )
            }
            .mapTo(sortedSetOf(), LsiClass::id)
        return CompilerFeatureCollection(
            state = DdlCollectionState(entityTypeIds),
        )
    }

    override fun precompile(
        context: CompilerPrecompileContext<DdlCollectionState, DdlFeatureState>,
    ): CompilerFeaturePrecompileResult<DdlFeatureState> {
        val collection = context.collection.state
        val previousState = context.previousState
        val entityTypeIds = buildSet {
            previousState?.entityTypeIds?.let(::addAll)
            addAll(collection.entityTypeIds)
        }.toSortedSet()
        val optionsFingerprint = context.round.options.ddlFingerprint()
        if (!context.round.isFinal) {
            return CompilerFeaturePrecompileResult(
                state = DdlFeatureState.collecting(
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
            return CompilerFeaturePrecompileResult(
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
        return CompilerFeaturePrecompileResult(
            state = DdlFeatureState.compiled(
                entityTypeIds = entityTypeIds,
                optionsFingerprint = optionsFingerprint,
                results = results,
            ),
            diagnostics = results.flatMap(JimmerDdlCompilerResult::warningDiagnostics),
        )
    }

    override fun render(
        context: CompilerRenderContext<DdlCollectionState, DdlFeatureState>,
    ): CompilerFeatureRenderResult {
        if (!context.round.isFinal) {
            return CompilerFeatureRenderResult()
        }
        val state = context.state
        if (!state.compiled || state.results.isEmpty()) {
            return CompilerFeatureRenderResult()
        }
        return CompilerFeatureRenderResult(
            diagnostics = JimmerDdlFileRenderer.render(state.results),
        )
    }

    companion object {
        val Key = compilerFeatureKey<DdlFeature, DdlCollectionState, DdlFeatureState>(
            DdlCollectionState.EMPTY
        )
    }
}

data class DdlCollectionState(
    val entityTypeIds: Set<LsiSymbolId>,
    override val fingerprint: String = entityTypeIds.joinToString(",") { typeId -> typeId.value },
) : CompilerFeatureState {

    companion object {
        val EMPTY = DdlCollectionState(emptySet())
    }
}

data class DdlFeatureState(
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
) : CompilerFeatureState {

    companion object {
        fun collecting(
            entityTypeIds: Set<LsiSymbolId>,
            optionsFingerprint: String,
        ): DdlFeatureState {
            return DdlFeatureState(
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
        ): DdlFeatureState {
            return DdlFeatureState(
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

private fun LsiClass.isEntity(): Boolean {
    return annotations.any { annotation -> annotation.type == ENTITY_ANNOTATION_ID }
}

private fun Map<String, String>.ddlFingerprint(): String {
    return entries.asSequence()
        .filter { (name, _) -> name.startsWith("jimmerDdl.") }
        .sortedBy(Map.Entry<String, String>::key)
        .joinToString("|") { (name, value) -> "$name=${value.length}:$value" }
}

private val ENTITY_ANNOTATION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
