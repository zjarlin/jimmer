package org.babyfish.jimmer.compiler

import java.util.ServiceLoader
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeSeed

data class JimmerCompilerFeatureDescriptor(
    val id: String,
    val dependsOn: Set<String> = emptySet(),
    val classpathTypeIds: Set<LsiSymbolId> = emptySet(),
    val inputResourcePaths: Set<String> = emptySet(),
    val inputDocumentKinds: Set<CompilerInputDocumentKind> = emptySet(),
) {

    init {
        requireFeatureId(id)
        dependsOn.forEach(::requireFeatureId)
        require(id !in dependsOn) { "Compiler feature '$id' cannot depend on itself" }
        classpathTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        inputResourcePaths.forEach(::requireCompilerResourcePath)
    }
}

/**
 * 由 ServiceLoader 发现的无平台编译功能。
 */
interface JimmerCompilerFeatureProvider {
    val descriptor: JimmerCompilerFeatureDescriptor

    fun requestTypeSeeds(context: JimmerCompilerTypeSeedContext): Collection<LsiTypeSeed> = emptyList()

    fun collect(context: JimmerCompilerCollectContext): JimmerCompilerFeatureCollection =
        JimmerCompilerFeatureCollection()

    fun precompile(context: JimmerCompilerPrecompileContext): JimmerCompilerFeaturePrecompileResult =
        JimmerCompilerFeaturePrecompileResult(JimmerCompilerFeatureState.EMPTY)

    fun render(context: JimmerCompilerRenderContext): JimmerCompilerFeatureRenderResult =
        JimmerCompilerFeatureRenderResult()
}

/**
 * 从指定类加载器发现 compiler feature，并立即执行严格图校验。
 */
object JimmerCompilerFeatureProviders {

    fun load(
        classLoader: ClassLoader = JimmerCompilerFeatureProvider::class.java.classLoader
    ): List<JimmerCompilerFeatureProvider> {
        val providers = ServiceLoader
            .load(JimmerCompilerFeatureProvider::class.java, classLoader)
            .toList()
        return JimmerCompilerFeatureGraph.sort(providers)
    }
}

sealed class JimmerCompilerFeatureGraphException(message: String) : IllegalArgumentException(message)

class DuplicateCompilerFeatureException(
    val featureId: String
) : JimmerCompilerFeatureGraphException("Duplicate compiler feature id: '$featureId'")

class MissingCompilerFeatureDependencyException(
    val featureId: String,
    val dependencyId: String
) : JimmerCompilerFeatureGraphException(
    "Compiler feature '$featureId' depends on missing feature '$dependencyId'"
)

class CyclicCompilerFeatureDependencyException(
    val cycle: List<String>
) : JimmerCompilerFeatureGraphException(
    "Compiler feature dependency cycle: ${cycle.joinToString(" -> ")}"
)

/**
 * 以 feature id 为稳定排序键计算严格依赖顺序。
 */
object JimmerCompilerFeatureGraph {

    fun sort(providers: Iterable<JimmerCompilerFeatureProvider>): List<JimmerCompilerFeatureProvider> {
        val providersById = linkedMapOf<String, JimmerCompilerFeatureProvider>()
        for (provider in providers) {
            val id = provider.descriptor.id
            if (providersById.putIfAbsent(id, provider) != null) {
                throw DuplicateCompilerFeatureException(id)
            }
        }
        validateDependencies(providersById)

        val remainingDependencies = providersById.mapValues { (_, provider) ->
            provider.descriptor.dependsOn.size
        }.toMutableMap()
        val dependents = mutableMapOf<String, MutableList<String>>()
        for ((id, provider) in providersById) {
            for (dependencyId in provider.descriptor.dependsOn) {
                dependents.getOrPut(dependencyId, ::mutableListOf) += id
            }
        }
        val ready = sortedSetOf<String>()
        remainingDependencies
            .filterValues { dependencyCount -> dependencyCount == 0 }
            .keys
            .let(ready::addAll)
        val sorted = mutableListOf<JimmerCompilerFeatureProvider>()
        while (ready.isNotEmpty()) {
            val id = ready.first()
            ready.remove(id)
            sorted += requireNotNull(providersById[id])
            for (dependentId in dependents[id].orEmpty().sorted()) {
                val dependencyCount = requireNotNull(remainingDependencies[dependentId]) - 1
                remainingDependencies[dependentId] = dependencyCount
                if (dependencyCount == 0) {
                    ready += dependentId
                }
            }
        }
        if (sorted.size != providersById.size) {
            throw CyclicCompilerFeatureDependencyException(findCycle(providersById))
        }
        return sorted
    }

    private fun validateDependencies(providersById: Map<String, JimmerCompilerFeatureProvider>) {
        for ((id, provider) in providersById.toSortedMap()) {
            for (dependencyId in provider.descriptor.dependsOn.sorted()) {
                if (dependencyId !in providersById) {
                    throw MissingCompilerFeatureDependencyException(id, dependencyId)
                }
            }
        }
    }

    private fun findCycle(
        providersById: Map<String, JimmerCompilerFeatureProvider>
    ): List<String> {
        val states = mutableMapOf<String, VisitState>()
        val stack = mutableListOf<String>()
        for (id in providersById.keys.sorted()) {
            val cycle = findCycleFrom(id, providersById, states, stack)
            if (cycle != null) {
                return cycle
            }
        }
        error("Cyclic compiler feature graph did not expose a cycle")
    }

    private fun findCycleFrom(
        id: String,
        providersById: Map<String, JimmerCompilerFeatureProvider>,
        states: MutableMap<String, VisitState>,
        stack: MutableList<String>
    ): List<String>? {
        when (states[id]) {
            VisitState.VISITED -> return null
            VisitState.VISITING -> return cycleFrom(stack, id)
            null -> Unit
        }

        states[id] = VisitState.VISITING
        stack += id
        val provider = requireNotNull(providersById[id])
        for (dependencyId in provider.descriptor.dependsOn.sorted()) {
            val cycle = findCycleFrom(dependencyId, providersById, states, stack)
            if (cycle != null) {
                return cycle
            }
        }
        stack.removeLast()
        states[id] = VisitState.VISITED
        return null
    }

    private fun cycleFrom(stack: List<String>, repeatedId: String): List<String> {
        val cycleStart = stack.indexOf(repeatedId)
        return stack.subList(cycleStart, stack.size) + repeatedId
    }

    private enum class VisitState {
        VISITING,
        VISITED
    }
}

private fun requireFeatureId(id: String) {
    require(id.isNotBlank()) { "Compiler feature id cannot be blank" }
    require(id == id.trim()) { "Compiler feature id cannot have surrounding whitespace: '$id'" }
    require(id.none(Char::isWhitespace)) { "Compiler feature id cannot contain whitespace: '$id'" }
}

internal fun requireCompilerResourcePath(path: String) {
    require(path.isNotBlank()) { "Compiler resource path cannot be blank" }
    require(path == path.trim().replace('\\', '/')) {
        "Compiler resource path must be normalized: '$path'"
    }
    require(!path.startsWith('/')) { "Compiler resource path must be relative: '$path'" }
    require(path.split('/').none { segment -> segment.isBlank() || segment == "." || segment == ".." }) {
        "Compiler resource path contains an invalid segment: '$path'"
    }
}
