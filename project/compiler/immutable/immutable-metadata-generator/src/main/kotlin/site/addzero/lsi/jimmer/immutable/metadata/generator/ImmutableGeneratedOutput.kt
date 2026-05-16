package site.addzero.lsi.jimmer.immutable.metadata.generator

import site.addzero.lsi.codegen.GeneratedResourceArtifact
import site.addzero.lsi.poet.LsiFileSpec

data class ImmutableGeneratedOutput(
    val sourceFileSpecs: List<LsiFileSpec>,
    val resourceArtifacts: List<GeneratedResourceArtifact>,
)

data class ImmutableGeneratedSourcePartition(
    val javaSharedFileSpecs: List<LsiFileSpec>,
    val kotlinSidecarFileSpecs: List<LsiFileSpec>,
)

internal fun ImmutableGeneratedOutput.plus(
    other: ImmutableGeneratedOutput,
): ImmutableGeneratedOutput =
    ImmutableGeneratedOutput(
        sourceFileSpecs = sourceFileSpecs + other.sourceFileSpecs,
        resourceArtifacts = resourceArtifacts + other.resourceArtifacts,
    )

fun ImmutableGeneratedOutput.partitionSourceFileSpecs(): ImmutableGeneratedSourcePartition {
    val javaSharedFileSpecs = mutableListOf<LsiFileSpec>()
    val kotlinSidecarFileSpecs = mutableListOf<LsiFileSpec>()
    for (fileSpec in sourceFileSpecs) {
        when (fileSpec.immutableArtifactRole()) {
            ImmutableArtifactRole.JAVA_SHARED -> javaSharedFileSpecs += fileSpec
            ImmutableArtifactRole.KOTLIN_SIDECAR -> kotlinSidecarFileSpecs += fileSpec
        }
    }
    return ImmutableGeneratedSourcePartition(
        javaSharedFileSpecs = javaSharedFileSpecs,
        kotlinSidecarFileSpecs = kotlinSidecarFileSpecs,
    )
}

fun ImmutableGeneratedOutput.javaSharedOutput(): ImmutableGeneratedOutput {
    val partition = partitionSourceFileSpecs()
    return ImmutableGeneratedOutput(
        sourceFileSpecs = partition.javaSharedFileSpecs,
        resourceArtifacts = resourceArtifacts,
    )
}

fun ImmutableGeneratedOutput.kotlinSidecarOutput(): ImmutableGeneratedOutput {
    val partition = partitionSourceFileSpecs()
    return ImmutableGeneratedOutput(
        sourceFileSpecs = partition.kotlinSidecarFileSpecs,
        resourceArtifacts = emptyList(),
    )
}
