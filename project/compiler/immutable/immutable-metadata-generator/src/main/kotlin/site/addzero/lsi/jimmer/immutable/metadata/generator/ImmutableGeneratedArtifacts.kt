package site.addzero.lsi.jimmer.immutable.metadata.generator

import site.addzero.lsi.codegen.GeneratorException
import site.addzero.lsi.codegen.JacksonTypes
import site.addzero.lsi.jimmer.immutable.metadata.extractor.ImmutableCollectedSourceResolution
import site.addzero.lsi.jimmer.immutable.generator.DraftGenerator
import site.addzero.lsi.jimmer.immutable.generator.EmbeddedPropExpressionGenerator
import site.addzero.lsi.jimmer.immutable.generator.FetcherGenerator
import site.addzero.lsi.jimmer.immutable.generator.ImmutableGenerationMode
import site.addzero.lsi.jimmer.immutable.generator.ImmutableSourceGenerationPlan
import site.addzero.lsi.jimmer.immutable.generator.PropsGenerator
import site.addzero.lsi.jimmer.immutable.generator.TableGenerator
import site.addzero.lsi.jimmer.immutable.generator.toImmutableSourceGenerationPlan
import site.addzero.lsi.jimmer.EMBEDDABLE
import site.addzero.lsi.jimmer.ENTITY
import site.addzero.lsi.jimmer.IMMUTABLE
import site.addzero.lsi.jimmer.MAPPED_SUPERCLASS
import site.addzero.lsi.poet.LsiFileSpec
import java.io.File
import java.util.regex.Pattern
import kotlin.math.min

internal fun ImmutableCollectedSourceResolution.toGeneratedOutput(
    excludedUserTypePrefixes: List<String>,
    jacksonTypes: JacksonTypes,
    existingEntitiesResourceFile: File?,
    isResourceGenerationIgnored: Boolean,
    isModuleRequired: Boolean,
    generationMode: ImmutableGenerationMode,
    currentVersionValue: String = org.babyfish.jimmer.currentVersion(),
): ImmutableGeneratedOutput =
    sources.map { source ->
        source.toImmutableSourceGenerationPlan(excludedUserTypePrefixes)
    }.toGeneratedOutput(
        jacksonTypes = jacksonTypes,
        existingEntitiesResourceFile = existingEntitiesResourceFile,
        isResourceGenerationIgnored = isResourceGenerationIgnored,
        isModuleRequired = isModuleRequired,
        generationMode = generationMode,
        currentVersionValue = currentVersionValue,
    )

internal fun List<ImmutableSourceGenerationPlan>.toGeneratedOutput(
    jacksonTypes: JacksonTypes,
    existingEntitiesResourceFile: File?,
    isResourceGenerationIgnored: Boolean,
    isModuleRequired: Boolean,
    generationMode: ImmutableGenerationMode,
    currentVersionValue: String = org.babyfish.jimmer.currentVersion(),
): ImmutableGeneratedOutput {
    val sourceFileSpecs = mutableListOf<LsiFileSpec>()
    val resourceArtifacts = mutableListOf<site.addzero.lsi.codegen.GeneratedResourceArtifact>()
    for (source in this) {
        sourceFileSpecs += DraftGenerator(
            jacksonTypes,
            source.metadata.sourcePackageName,
            source.metadata.sourceFileName,
            source.draftTypes,
            currentVersionValue = currentVersionValue,
        ).generate(mode = generationMode)
        if (source.metadata.typeQualifiedNames.size > 1) {
            throw GeneratorException(
                "The source file '${source.metadata.sourceKey}' declares several types decorated by " +
                    "@$IMMUTABLE, " +
                    "@$ENTITY, " +
                    "@$MAPPED_SUPERCLASS " +
                    "or @$EMBEDDABLE: " +
                    source.metadata.typeQualifiedNames.joinToString()
            )
        }
        source.propsTypeMetadata?.let { propsTypeMetadata ->
            sourceFileSpecs += PropsGenerator(
                source.metadata.sourcePackageName,
                source.metadata.sourceFileName,
                propsTypeMetadata,
            ).generate(mode = generationMode)
            if (generationMode == ImmutableGenerationMode.JAVA_SHARED && propsTypeMetadata.isEntity) {
                sourceFileSpecs += TableGenerator(propsTypeMetadata).generate()
            }
            if (propsTypeMetadata.isEmbeddable) {
                sourceFileSpecs += EmbeddedPropExpressionGenerator(propsTypeMetadata).generate()
            }
        }
        source.fetcherTypeMetadata?.let { fetcherTypeMetadata ->
            sourceFileSpecs += FetcherGenerator(
                source.metadata.sourcePackageName,
                source.metadata.sourceFileName,
                fetcherTypeMetadata,
            ).generate(mode = generationMode)
        }
    }
    if (isResourceGenerationIgnored) {
        if (generationMode == ImmutableGenerationMode.JAVA_SHARED) {
            sourceFileSpecs.validateJavaSharedFileSpecs()
        }
        return ImmutableGeneratedOutput(
            sourceFileSpecs = sourceFileSpecs,
            resourceArtifacts = emptyList(),
        )
    }
    val packageCollector = PackageCollector()
    for (source in this) {
        for (qualifiedName in source.metadata.entityQualifiedNames) {
            packageCollector.accept(qualifiedName)
        }
    }
    val moduleOutput = JimmerModuleMetadataGenerator(
        existingEntitiesResourceFile = existingEntitiesResourceFile,
        packageName = packageCollector.commonPackageName(),
        entityQualifiedNames = packageCollector.entityQualifiedNames,
        isModuleRequired = isModuleRequired,
    ).generateOutput()
    sourceFileSpecs += moduleOutput.sourceFileSpecs
    resourceArtifacts += moduleOutput.resourceArtifacts
    if (generationMode == ImmutableGenerationMode.JAVA_SHARED) {
        sourceFileSpecs.validateJavaSharedFileSpecs()
    }
    return ImmutableGeneratedOutput(
        sourceFileSpecs = sourceFileSpecs,
        resourceArtifacts = resourceArtifacts,
    )
}

private fun List<LsiFileSpec>.validateJavaSharedFileSpecs() {
    for (fileSpec in this) {
        val role = fileSpec.immutableArtifactRole()
        if (role != ImmutableArtifactRole.JAVA_SHARED) {
            val blockers = fileSpec.javaBoundaryBlockers()
            throw GeneratorException(
                "Immutable JAVA_SHARED artifact '${fileSpec.qualifiedName}' resolved to role $role with blockers: ${blockers.joinToString()}",
            )
        }
    }
}

private class PackageCollector {

    private var paths: MutableList<String>? = null

    private var cachedPackageName: String? = null

    private val collectedEntityQualifiedNames = mutableListOf<String>()

    fun accept(qualifiedName: String) {
        collectedEntityQualifiedNames += qualifiedName
        if (paths != null && paths!!.isEmpty()) {
            return
        }
        cachedPackageName = null
        val packageName = qualifiedName.substringBeforeLast('.', "")
        val newPaths = DOT_PATTERN.split(packageName).toMutableList()
        if (paths == null) {
            paths = newPaths
            return
        }
        val currentPaths = paths!!
        val len = min(currentPaths.size, newPaths.size)
        var index = 0
        while (index < len) {
            if (currentPaths[index] != newPaths[index]) {
                break
            }
            index++
        }
        if (index < currentPaths.size) {
            currentPaths.subList(index, currentPaths.size).clear()
        }
    }

    val entityQualifiedNames: List<String>
        get() = collectedEntityQualifiedNames

    fun commonPackageName(): String {
        val current = cachedPackageName
        if (current != null) {
            return current
        }
        val resolved = paths
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(".")
            .orEmpty()
        cachedPackageName = resolved
        return resolved
    }

    companion object {
        private val DOT_PATTERN = Pattern.compile("\\.")
    }
}
