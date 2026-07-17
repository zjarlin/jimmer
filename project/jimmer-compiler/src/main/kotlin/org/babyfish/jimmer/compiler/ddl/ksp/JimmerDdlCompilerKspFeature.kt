package org.babyfish.jimmer.compiler.ddl.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import org.babyfish.jimmer.compiler.lsi.ksp.toLsiWorkspace
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
import java.io.File

/**
 * 在主 KSP 生命周期的有效轮冻结 LSI，并在 finish 阶段仅消费不可变快照。
 */
class JimmerDdlCompilerKspFeature(
    private val environment: SymbolProcessorEnvironment,
) {
    private val frontendOptions = LsiFrontendOptions.from(environment.options)

    private val settingsList = JimmerDdlCompilerSettings.allFromOptions(environment.options)

    private val projectDirs = settingsList.mapNotNull { settings ->
        settings.outputDir.toProjectDirFromOutputDir()
    }.toSet()

    private val sources = linkedSetOf<LsiSource>()

    private val declarations = linkedMapOf<LsiSymbolId, LsiDeclaration>()

    private val entityTypeIds = linkedSetOf<LsiSymbolId>()

    private var hasErrors = false

    private var finished = false

    fun process(resolver: Resolver): List<KSAnnotated> {
        if (finished || settingsList.none { settings -> settings.enabled }) {
            return emptyList()
        }

        val deferred = mutableListOf<KSAnnotated>()
        resolver.getSymbolsWithAnnotation(ENTITY_ANNOTATION).forEach { symbol ->
            if (!symbol.validate()) {
                deferred += symbol
            } else if (symbol !is KSClassDeclaration) {
                environment.logger.error("Jimmer DDL can only process class declarations: $symbol", symbol)
                hasErrors = true
            }
        }
        val validRoots = resolver.getAllFiles()
            .flatMap { file -> file.declarations.filterIsInstance<KSClassDeclaration>() }
            .filter(KSClassDeclaration::validate)
            .toList()
        if (validRoots.isNotEmpty()) {
            merge(validRoots.toLsiWorkspace(resolver, frontendOptions))
        }
        return deferred
    }

    fun finish() {
        if (finished) {
            return
        }
        finished = true
        if (hasErrors || entityTypeIds.isEmpty()) {
            return
        }

        val workspace = LsiWorkspace(sources, declarations.values)
        settingsList.forEach { settings ->
            val result = JimmerDdlCompiler.compile(
                workspace = workspace,
                entityTypeIds = entityTypeIds,
                settings = settings,
            )
            result.warnings.forEach(environment.logger::warn)
            JimmerDdlEntityTableSnapshot.writeGeneratedSnapshot(
                entities = result.entities,
                schema = result.snapshotSchema,
                settings = settings,
            )
            if (!result.isEmpty) {
                val outputFile = JimmerDdlCompilerFiles.writeOutputFile(settings, result.sql)
                environment.logger.warn("Jimmer DDL generated: ${outputFile.absolutePath}")
            }
        }
    }

    private fun merge(workspace: LsiWorkspace) {
        sources += workspace.sources
        for (declaration in workspace.declarations) {
            declarations[declaration.id] = declaration
            if (declaration is LsiTypeDeclaration && declaration.isCurrentProjectEntity()) {
                entityTypeIds += declaration.id
            }
        }
    }

    private fun LsiTypeDeclaration.isCurrentProjectEntity(): Boolean {
        if (annotations.none { annotation -> annotation.type == ENTITY_ANNOTATION_ID }) {
            return false
        }
        val path = origin.source?.path ?: return false
        if (projectDirs.isEmpty()) {
            return true
        }
        return projectDirs.any { projectDir ->
            path.startsWith("$projectDir/src/") || path.startsWith("$projectDir/build/generated/")
        }
    }

    companion object {
        const val ENTITY_ANNOTATION = "org.babyfish.jimmer.sql.Entity"

        private val ENTITY_ANNOTATION_ID = LsiSymbolId.type(ENTITY_ANNOTATION)
    }
}

private fun String.toProjectDirFromOutputDir(): String? {
    val outputPath = File(this).absolutePath.normalizedLsiPath()
    val index = outputPath.indexOf("/build/")
    if (index < 0) {
        return null
    }
    return outputPath.substring(0, index).trimEnd('/').takeIf(String::isNotBlank)
}

private fun String.normalizedLsiPath(): String {
    return replace('\\', '/').trimStart('/')
}
