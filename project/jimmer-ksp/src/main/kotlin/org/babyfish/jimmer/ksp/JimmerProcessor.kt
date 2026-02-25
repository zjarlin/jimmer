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
 * The single KSP [SymbolProcessor] entry point for Jimmer.
 *
 * ## Architecture
 *
 * All Jimmer code-generation logic is implemented as [ProcessorSpi] modules,
 * discovered at runtime via [ServiceLoader]. Each SPI declares:
 * - **[id][ProcessorSpi.id]** — unique identifier (defaults to qualified class name)
 * - **[dependsOn][ProcessorSpi.dependsOn]** — needs compiled output → triggers barrier
 * - **[runsAfter][ProcessorSpi.runsAfter]** — needs in-memory state → same-round ordering
 *
 * ## Barrier derivation
 *
 * A processor is automatically a **barrier** if any other processor lists it in
 * [dependsOn][ProcessorSpi.dependsOn]. No manual annotation needed — the orchestrator
 * computes `barriers = allSpis.flatMap { it.dependsOn }.toSet()` at init time.
 *
 * ## Scheduling Algorithm
 *
 * Processors are topologically sorted by [dependsOn] + [runsAfter] edges.
 * Each KSP round iterates the sorted list and runs every processor whose
 * prerequisites are met:
 * - **[runsAfter]** deps: must have **executed** (this or prior round)
 * - **[dependsOn]** deps: must have **executed** AND if the dep is a barrier
 *   that generated output, must have been **compiled** (prior round only)
 *
 * If a barrier generates nothing, it is marked compiled immediately and
 * its [dependsOn] dependents can run in the same round (fall-through).
 *
 * ### Example execution trace
 *
 * ```
 * barriers (derived) = {ImmutableProcessor, TypedTupleProcessor}
 *   (because ClientProcessor dependsOn both)
 *
 * Topo order: [TxProcessor, ImmutableProcessor, ErrorProcessor,
 *              DtoProcessor, ExportDocProcessor, TypedTupleProcessor, ClientProcessor]
 *
 * Round 1:
 *   TxProcessor         — no deps, runs             → no output
 *   ImmutableProcessor  — no deps, barrier, runs    → generated ⇒ pendingBarrier
 *   ErrorProcessor      — no deps, runs             → generated
 *   DtoProcessor        — runsAfter Immutable (executed ✓) → runs
 *   ExportDocProcessor  — runsAfter Immutable (executed ✓) → runs
 *   TypedTupleProcessor — runsAfter Immutable (executed ✓), barrier → no output ⇒ compiled
 *   ClientProcessor     — dependsOn Immutable (pending barrier, not compiled) → SKIP
 *   ⇒ return deferred, KSP compiles
 *
 * Round 2:
 *   ClientProcessor     — Immutable+Tuple both compiled ✓ → runs
 *   ⇒ done
 * ```
 *
 * ## Why a single SymbolProcessor?
 *
 * KSP does not guarantee execution order across multiple [SymbolProcessor]s.
 * By keeping a single entry point and using SPI for modularity, we get:
 * - **Deterministic ordering** via the dependency graph
 * - **Easy extensibility** — add a module + `@AutoService(ProcessorSpi::class)`, done
 * - **Single user-facing dependency** — users only need `ksp(jimmer-ksp)`
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
            val tag = if (spi::class.qualifiedName?.startsWith("org.babyfish.jimmer.ksp.") == true) "builtin " else "user-ext"
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
