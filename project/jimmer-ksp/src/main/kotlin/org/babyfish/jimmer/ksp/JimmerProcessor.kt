package org.babyfish.jimmer.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import org.babyfish.jimmer.dto.compiler.DtoAstException
import site.addzero.context.Context
import site.addzero.context.Settings
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.file.guessResourceFile
import site.addzero.lsi.ksp.clazz.findKspDraftImplDocMap
import site.addzero.lsi.ksp.codegen.toLsiFiler
import site.addzero.lsi.ksp.context.KspLsiContext
import site.addzero.lsi.ksp.file.KspLsiFile
import site.addzero.lsi.ksp.resolver.toLsiResolver
import site.addzero.lsi.processor.ProcessorSpi
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
     * All SPI processors, discovered once and topologically sorted by dependsOn edges.
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
            logger.info("[jimmer]   $tag | id=${spi.id}, dependsOn=${spi.dependsOn}, barrier=$isBarrier")
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

    /** IDs of lifecycle processors that use onRound/onFinish. */
    private val lifecycleProcessorIds: Set<String> by lazy {
        sortedSpis
            .filter { it.processorMode() == ProcessorMode.LIFECYCLE }
            .map { it.id }
            .toSet()
    }

    /** IDs of legacy processors that still override process(). */
    private val legacyProcessorIds: Set<String> by lazy {
        sortedSpis
            .filter { it.processorMode() == ProcessorMode.LEGACY_PROCESS }
            .map { it.id }
            .toSet()
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        Settings.fromOptions(environment.options)
        // 覆盖来源：project/jimmer-ksp/.../JimmerProcessor.process 的 `Context.reset(resolver, environment)`
        // 迁移说明：KSP resolver/environment 仅在最外层处理器完成 `KSP -> LSI` 单向适配后再注入 compiler Context
        KspLsiContext.init(environment)
        KspLsiContext.resetRound(resolver)
        val lsiResolver = resolver.toLsiResolver()
        Context.reset(
            lsiResolver = lsiResolver,
            lsiFiler = environment.codeGenerator.toLsiFiler(),
            options = environment.options,
            firstLsiFileProvider = firstLsiFileProvider@{
                val firstFile = resolver.getAllFiles().firstOrNull() ?: return@firstLsiFileProvider null
                KspLsiFile(resolver, firstFile)
            },
            sourceAnchorFilePathProvider = sourceAnchorFilePathProvider@{
                resolver.getAllFiles().firstOrNull()?.filePath
            },
            generatedJimmerResourceFileProvider = { name ->
                guessResourceFile(environment.codeGenerator.generatedFile.firstOrNull(), name)
            },
            infoLogger = { message ->
                environment.logger.info(message)
            },
            draftImplDocMapProvider = { type, annotationQualifiedName, valueAttributeName ->
                type.findKspDraftImplDocMap(annotationQualifiedName, valueAttributeName)
            },
        )

        return try {
            val deferred = mutableListOf<KSAnnotated>()
            val pendingBarriers = mutableSetOf<String>()
            val lifecycleRoundExecuted = mutableSetOf<String>()
            val knownIds = sortedSpis.map { it.id }.toSet()

            for (spi in sortedSpis) {
                when (spi.processorMode()) {
                    ProcessorMode.LIFECYCLE -> {
                        val hardDeps = spi.dependsOn.filter { it in knownIds }.toSet()
                        if (!hardDeps.all { dep ->
                                dep in executed || dep in lifecycleRoundExecuted
                            }) {
                            continue
                        }
                        val legacyBarrierDeps = hardDeps
                            .filter { it in legacyProcessorIds && it in barriers }
                            .toSet()
                        if (!compiled.containsAll(legacyBarrierDeps)) {
                            continue
                        }
                        spi.onRound()
                        lifecycleRoundExecuted += spi.id
                        continue
                    }

                    ProcessorMode.LEGACY_PROCESS -> {
                        if (spi.id in executed) continue

                        // dependsOn: hard ordering — must have executed AND compiled (if barrier).
                        val hardDeps = spi.dependsOn.filter { it in knownIds }.toSet()
                        if (!hardDeps.all { dep ->
                                dep in executed || dep in lifecycleRoundExecuted
                            }) {
                            continue
                        }
                        val barrierDeps = hardDeps
                            .filter { it in barriers && it in legacyProcessorIds }
                            .toSet()
                        if (!compiled.containsAll(barrierDeps)) continue

                        @Suppress("DEPRECATION")
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
                }
            }

            // Pending barriers become compiled after KSP compiles the new files.
            compiled += pendingBarriers

            deferred
        } catch (ex: MetaException) {
            // 覆盖来源：project/jimmer-ksp/.../JimmerProcessor.process 的 `ex.declaration?.let(logger.error(..., it))`
            // 迁移说明：`MetaException` 已完全 LSI 化，KSP 最外层仅消费格式化后的错误消息，不再依赖 KS 声明载荷
            environment.logger.error(ex.message!!)
            emptyList()
        } catch (ex: DtoAstException) {
            environment.logger.error(ex.message!!)
            emptyList()
        }
    }

    override fun finish() {
        try {
            for (spi in sortedSpis) {
                if (spi.id !in lifecycleProcessorIds) {
                    continue
                }
                spi.onFinish()
            }
        } catch (ex: MetaException) {
            // 覆盖来源：project/jimmer-ksp/.../JimmerProcessor.finish 的 `ex.declaration?.let(logger.error(..., it))`
            // 迁移说明：finish 阶段同样只消费 LSI 化后的错误消息，移除 `MetaException -> KSDeclaration` 反向依赖
            environment.logger.error(ex.message!!)
        } catch (ex: DtoAstException) {
            environment.logger.error(ex.message!!)
        }
    }

    private enum class ProcessorMode {
        LIFECYCLE,
        LEGACY_PROCESS
    }

    private fun ProcessorSpi<*, *>.processorMode(): ProcessorMode {
        val overridesOnRound = declaresNoArgMethod("onRound")
        val overridesOnFinish = declaresNoArgMethod("onFinish")
        return if (overridesOnRound || overridesOnFinish) {
            ProcessorMode.LIFECYCLE
        } else {
            ProcessorMode.LEGACY_PROCESS
        }
    }

    private fun ProcessorSpi<*, *>.declaresNoArgMethod(name: String): Boolean =
        this::class.java.declaredMethods.any { method ->
            method.name == name && method.parameterCount == 0
        }

    companion object {

        /**
         * Topological sort of [site.addzero.lsi.jimmer.processor.spi.ProcessorSpi]s by [dependsOn] edges only.
         *
         * Uses Kahn's algorithm. Processors with unsatisfied dependencies
         * (missing or cyclic) are appended at the end.
         */
        internal fun topologicalSort(processors: List<ProcessorSpi<*, *>>): List<ProcessorSpi<*, *>> {
            val byId = processors.associateBy { it.id }
            val inDegree = mutableMapOf<String, Int>()
            val dependents = mutableMapOf<String, MutableList<String>>()

            for (spi in processors) {
                val deps = spi.dependsOn.filter { it in byId }
                inDegree[spi.id] = deps.size
                for (dep in deps) {
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
