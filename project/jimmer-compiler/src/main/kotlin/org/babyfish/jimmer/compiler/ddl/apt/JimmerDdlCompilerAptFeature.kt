package org.babyfish.jimmer.compiler.ddl.apt

import org.babyfish.jimmer.compiler.lsi.apt.toLsiWorkspace
import org.babyfish.jimmer.compiler.lsi.LsiFrontendOptions
import org.babyfish.jimmer.ddl.compiler.JimmerDdlCompiler
import org.babyfish.jimmer.ddl.compiler.JimmerDdlCompilerFiles
import org.babyfish.jimmer.ddl.compiler.JimmerDdlCompilerSettings
import org.babyfish.jimmer.ddl.compiler.JimmerDdlEntityTableSnapshot
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiWorkspace
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.tools.Diagnostic

/**
 * 在主 APT 生命周期的有效轮冻结 LSI，并在最终轮仅消费不可变快照。
 */
class JimmerDdlCompilerAptFeature(
    private val processingEnvironment: ProcessingEnvironment,
) {
    private val frontendOptions = LsiFrontendOptions.from(processingEnvironment.options)

    private val settingsList = JimmerDdlCompilerSettings.allFromOptions(processingEnvironment.options)

    private val sources = linkedSetOf<LsiSource>()

    private val declarations = linkedMapOf<LsiSymbolId, LsiDeclaration>()

    private val entityTypeIds = linkedSetOf<LsiSymbolId>()

    private var finished = false

    fun onRound(roundEnvironment: RoundEnvironment) {
        if (finished || settingsList.none { settings -> settings.enabled }) {
            return
        }
        if (roundEnvironment.processingOver()) {
            finish()
            return
        }

        merge(roundEnvironment.toLsiWorkspace(processingEnvironment, frontendOptions))
    }

    fun finish() {
        if (finished) {
            return
        }
        finished = true
        if (settingsList.none { settings -> settings.enabled } || entityTypeIds.isEmpty()) {
            return
        }

        val workspace = LsiWorkspace(sources, declarations.values)
        settingsList.forEach { settings ->
            val result = JimmerDdlCompiler.compile(
                workspace = workspace,
                entityTypeIds = entityTypeIds,
                settings = settings,
            )
            result.warnings.forEach { warning ->
                processingEnvironment.messager.printMessage(Diagnostic.Kind.WARNING, warning)
            }
            JimmerDdlEntityTableSnapshot.writeGeneratedSnapshot(
                entities = result.entities,
                schema = result.snapshotSchema,
                settings = settings,
            )
            if (!result.isEmpty) {
                val outputFile = JimmerDdlCompilerFiles.writeOutputFile(settings, result.sql)
                processingEnvironment.messager.printMessage(
                    Diagnostic.Kind.NOTE,
                    "Jimmer DDL generated: ${outputFile.absolutePath}",
                )
            }
        }
    }

    private fun merge(workspace: LsiWorkspace) {
        sources += workspace.sources
        for (declaration in workspace.declarations) {
            declarations[declaration.id] = declaration
            if (declaration is LsiTypeDeclaration && declaration.isEntity()) {
                entityTypeIds += declaration.id
            }
        }
    }

    private fun LsiTypeDeclaration.isEntity(): Boolean {
        return annotations.any { annotation -> annotation.type == ENTITY_ANNOTATION_ID }
    }

    companion object {
        const val ENTITY_ANNOTATION = "org.babyfish.jimmer.sql.Entity"

        private val ENTITY_ANNOTATION_ID = LsiSymbolId.type(ENTITY_ANNOTATION)

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
            "jimmerDdl.nullabilityRepairOnly",
            "jimmerDdl.sourceFingerprint",
        )
    }
}
