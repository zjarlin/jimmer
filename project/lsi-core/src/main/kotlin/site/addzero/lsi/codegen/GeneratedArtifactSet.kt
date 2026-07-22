package site.addzero.lsi.codegen

enum class ArtifactRegistration {
    ADDED,
    DUPLICATE
}

class GeneratedArtifactConflictException(
    val existing: GeneratedArtifact,
    val incoming: GeneratedArtifact
) : IllegalStateException(
    "Conflicting generated artifact '${incoming.path}' for ${incoming.kind}: " +
        "contentChanged=${existing.content != incoming.content}, " +
        "aggregation=${existing.aggregationMode}->${incoming.aggregationMode}, " +
        "emission=${existing.emissionMode}->${incoming.emissionMode}, " +
        "symbols=${existing.originatingSymbols}->${incoming.originatingSymbols}, " +
        "sources=${existing.originatingSources}->${incoming.originatingSources}, " +
        "dependencySymbols=${existing.dependencySymbols}->${incoming.dependencySymbols}, " +
        "dependencySources=${existing.dependencySources}->${incoming.dependencySources}"
)

/**
 * 跨 feature、跨编译轮统一检查输出身份。
 */
class GeneratedArtifactSet(
    initialArtifacts: Iterable<GeneratedArtifact> = emptyList()
) {
    private val artifacts = linkedMapOf<GeneratedArtifactKey, GeneratedArtifact>()

    init {
        registerAll(initialArtifacts)
    }

    fun register(artifact: GeneratedArtifact): ArtifactRegistration {
        return register(artifacts, artifact)
    }

    fun registerAll(artifacts: Iterable<GeneratedArtifact>): List<GeneratedArtifact> {
        val staged = LinkedHashMap(this.artifacts)
        val added = mutableListOf<GeneratedArtifact>()
        for (artifact in artifacts) {
            if (register(staged, artifact) == ArtifactRegistration.ADDED) {
                added += artifact
            }
        }
        this.artifacts.clear()
        this.artifacts.putAll(staged)
        return added.sortedBy(GeneratedArtifact::key)
    }

    fun snapshot(): List<GeneratedArtifact> = artifacts.values.sortedBy(GeneratedArtifact::key)

    operator fun get(key: GeneratedArtifactKey): GeneratedArtifact? = artifacts[key]

    val size: Int
        get() = artifacts.size

    private fun register(
        target: MutableMap<GeneratedArtifactKey, GeneratedArtifact>,
        artifact: GeneratedArtifact
    ): ArtifactRegistration {
        val existing = target[artifact.key]
        if (existing == null) {
            target[artifact.key] = artifact
            return ArtifactRegistration.ADDED
        }
        if (existing == artifact) {
            return ArtifactRegistration.DUPLICATE
        }
        throw GeneratedArtifactConflictException(existing, artifact)
    }
}
