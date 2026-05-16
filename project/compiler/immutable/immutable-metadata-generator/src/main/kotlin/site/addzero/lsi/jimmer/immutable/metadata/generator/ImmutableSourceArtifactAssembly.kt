package site.addzero.lsi.jimmer.immutable.metadata.generator

import site.addzero.lsi.codegen.GeneratorException
import site.addzero.lsi.jimmer.immutable.generator.ImmutableGenerationMode
import site.addzero.lsi.poet.LsiFileSpec

internal fun immutableSourceFileSpecs(
    coreFileSpec: LsiFileSpec,
    generationMode: ImmutableGenerationMode,
    sidecarFileSpec: () -> LsiFileSpec? = { null },
): List<LsiFileSpec> {
    val coreRole = coreFileSpec.immutableArtifactRole()
    if (coreRole != ImmutableArtifactRole.JAVA_SHARED) {
        throw GeneratorException(
            "Immutable core artifact '${coreFileSpec.qualifiedName}' must stay JAVA_SHARED, resolved to $coreRole with blockers: ${coreFileSpec.javaBoundaryBlockers().joinToString()}",
        )
    }
    val fileSpecs = mutableListOf(coreFileSpec)
    if (generationMode == ImmutableGenerationMode.JAVA_SHARED) {
        return fileSpecs
    }
    val resolvedSidecarFileSpec = sidecarFileSpec() ?: return fileSpecs
    val sidecarRole = resolvedSidecarFileSpec.immutableArtifactRole()
    if (sidecarRole != ImmutableArtifactRole.KOTLIN_SIDECAR) {
        throw GeneratorException(
            "Immutable DSL sidecar '${resolvedSidecarFileSpec.qualifiedName}' must stay KOTLIN_SIDECAR, resolved to $sidecarRole with blockers: ${resolvedSidecarFileSpec.javaBoundaryBlockers().joinToString()}",
        )
    }
    fileSpecs += resolvedSidecarFileSpec
    return fileSpecs
}
