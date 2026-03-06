package org.babyfish.jimmer.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.processor.spi.ProcessorSpi
import site.addzero.context.Settings
import java.util.*

/**
 * 1. tuple：dependsOn [dto、不可变对象]
 * 2. transactional：dependsOn [无]
 * 3. error：dependsOn [无]
 * 4. dto：dependsOn [不可变对象]
 * 5. client：dependsOn [不可变对象、tuple]（原规则直接沿用）
 * 6. 不可变对象：dependsOn [无]（节点属性：独立、不可变对象）*
 * 根据dependsOn对loadedService进行拓扑排序
 *
 */
class JimmerProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    /**
     * All SPI processors, discovered once and topologically sorted by
     * [dependsOn][ProcessorSpi.dependsOn] + [runsAfter][ProcessorSpi.runsAfter] edges.
     */
    private val sortedSpis: List<ProcessorSpi<*, *>> by lazy {
        val all = ServiceLoader.load(ProcessorSpi::class.java, JimmerProcessor::class.java.classLoader).toList()
        val sorted = topologicalSort(all)

        val logger = environment.logger
        val (builtin, userDefined) = sorted.partition {
            it::class.qualifiedName?.startsWith("org.babyfish.jimmer.ksp.") == true
        }
        logger.info("[jimmer] Loaded ${sorted.size} ProcessorSpi(s): ${builtin.size} builtin, ${userDefined.size} user-defined")
        for (spi in sorted) {
            val tag =
                if (spi::class.qualifiedName?.startsWith("org.babyfish.jimmer.ksp.") == true) "builtin " else "user-ext"
            val isBarrier = spi.id in barriers
            logger.info("[jimmer]   $tag | id=${spi.id}, dependsOn=${spi.dependsOn}, runsAfter=${spi.runsAfter}, barrier=$isBarrier")
        }
        sorted
    }

    /**
     * IDs of processors that are **barriers** — derived from the dependency graph.
     * A processor is a barrier iff any other processor lists it in [dependsOn].
     */
    private val barriers: Set<String> by lazy {
        val all = ServiceLoader.load(ProcessorSpi::class.java, JimmerProcessor::class.java.classLoader).toList()
        all.flatMap { it.dependsOn }.toSet()
    }

    /** IDs of processors that have already been executed (persists across KSP rounds). */
    private val executed = mutableSetOf<String>()

    /** IDs of barrier processors whose compiled output is now available. */
    private val compiled = mutableSetOf<String>()

    /** Whether the initial type-name snapshot has been taken. */
    private var snapshotTaken = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        Settings.fromOptions(environment.options)
        Context.reset(resolver, environment)

        return try {
            val deferred = mutableListOf<KSAnnotated>()
            val pendingBarriers = mutableSetOf<String>()
            val knownIds = sortedSpis.map { it.id }.toSet()

            for (spi in sortedSpis) {
                if (spi.id in executed) continue

                // runsAfter: soft ordering — must have executed (same or prior round).
                val softDeps = spi.runsAfter.filter { it in knownIds }.toSet()
                if (!executed.containsAll(softDeps)) continue

                // dependsOn: hard ordering — must have executed AND compiled (if barrier).
                val hardDeps = spi.dependsOn.filter { it in knownIds }.toSet()
                if (!executed.containsAll(hardDeps)) continue
                val barrierDeps = hardDeps.filter { it in barriers }
                if (!compiled.containsAll(barrierDeps)) continue

                val result = spi.process()
                executed += spi.id

                var generated = false
                when (result) {
                    is Collection<*> -> {
                        val ksAnnotated = result.filterIsInstance<KSAnnotated>()
                        deferred += ksAnnotated
                        if (ksAnnotated.isNotEmpty()) generated = true
                    }

                    is Boolean -> if (result) generated = true
                }

                if (generated && !snapshotTaken) {
                    snapshotTaken = true
                    Context.snapshotAllTypeNames()
                }

                // Barrier handling (only for processors that someone dependsOn).
                if (spi.id in barriers) {
                    if (generated) {
                        pendingBarriers += spi.id
                    } else {
                        // No output → no compilation needed → dependents can run this round.
                        compiled += spi.id
                    }
                }
            }

            // Pending barriers become compiled after KSP compiles the new files.
            compiled += pendingBarriers

            deferred
        } catch (ex: MetaException) {
            environment.logger.error(ex.message!!, ex.declaration)
            emptyList()
        } catch (ex: DtoAstException) {
            environment.logger.error(ex.message!!)
            emptyList()
        }
    }

    companion object {

        /**
         * Topological sort of [ProcessorSpi]s by their [dependsOn] and [runsAfter] edges.
         *
         * Both [dependsOn] and [runsAfter] contribute edges to the dependency graph.
         * The difference between them is handled at **execution time** (barrier semantics),
         * not at sort time — here they both simply mean "must come before me."
         *
         * Uses Kahn's algorithm. Processors with unsatisfied dependencies
         * (missing or cyclic) are appended at the end — this is lenient
         * so that a missing optional dependency doesn't block execution.
         */
        internal fun topologicalSort(processors: List<ProcessorSpi<*, *>>): List<ProcessorSpi<*, *>> {
            val byId = processors.associateBy { it.id }
            val inDegree = mutableMapOf<String, Int>()
            val dependents = mutableMapOf<String, MutableList<String>>()

            for (spi in processors) {
                // Both dependsOn and runsAfter are ordering edges
                val allDeps = (spi.dependsOn + spi.runsAfter).filter { it in byId }
                inDegree[spi.id] = allDeps.size
                for (dep in allDeps) {
                    dependents.getOrPut(dep) { mutableListOf() }.add(spi.id)
                }
            }

            val queue: ArrayDeque<String> = ArrayDeque(
                processors.filter { (inDegree[it.id] ?: 0) == 0 }.map { it.id }
            )
            val result = mutableListOf<ProcessorSpi<*, *>>()

            while (queue.isNotEmpty()) {
                val id = queue.poll()
                result += byId[id] ?: continue
                for (dependent in dependents[id].orEmpty()) {
                    val newDegree = (inDegree[dependent] ?: 1) - 1
                    inDegree[dependent] = newDegree
                    if (newDegree == 0) {
                        queue.add(dependent)
                    }
                }
            }

            // Append any remaining processors (cyclic or broken deps) at the end
            for (spi in processors) {
                if (spi !in result) {
                    result += spi
                }
            }
            return result
        }
    }
}
