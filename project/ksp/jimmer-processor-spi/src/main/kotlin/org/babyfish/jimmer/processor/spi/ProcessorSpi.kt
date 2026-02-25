package org.babyfish.jimmer.processor.spi

/**
 * Service Provider Interface for Jimmer KSP processors.
 *
 * Each processor declares:
 * - [id]: unique identifier, defaults to the fully qualified class name
 * - [dependsOn]: IDs of processors whose **compiled output** this one needs
 * - [runsAfter]: IDs of processors whose **in-memory state** this one needs
 *
 * ## Barrier derivation
 *
 * A processor is automatically a **barrier** if any other processor lists it
 * in [dependsOn]. The orchestrator derives this from the dependency graph —
 * no manual annotation needed. When a barrier generates output, KSP compiles
 * the new files before [dependsOn] dependents run in the next round.
 *
 * ## dependsOn vs runsAfter
 *
 * | Declaration      | Meaning                                                    |
 * |------------------|------------------------------------------------------------|
 * | `dependsOn = X`  | I need X's **compiled types** → wait for KSP to compile X |
 * | `runsAfter = X`  | I need X's **in-memory state** → same-round ordering only  |
 *
 * @param T context type (typically [Context][org.babyfish.jimmer.ksp.Context])
 * @param R return type of [process]; may be `Collection<KSAnnotated>` (deferred symbols),
 *          `Boolean` (whether files were generated), or `Unit`.
 */
interface ProcessorSpi<T, R> {

    /** Unique identifier. Defaults to this class's fully qualified name. */
    val id: String get() = this::class.qualifiedName ?: this::class.java.name

    /**
     * IDs of processors whose **compiled output** this processor references at the type level.
     *
     * The dependency is automatically treated as a barrier: if the depended-on processor
     * generated files, this processor waits until the next KSP round (after compilation).
     * If the depended-on processor generated nothing, this processor runs in the same round.
     *
     * Example: ClientProcessor `dependsOn` ImmutableProcessor because it resolves
     * `Fetcher<Book>` types that ImmutableProcessor generated.
     */
    val dependsOn: Set<String> get() = emptySet()

    /**
     * IDs of processors that must have **executed** (but not necessarily compiled)
     * before this one runs. Only ensures execution order within the same round.
     *
     * Example: DtoProcessor `runsAfter` ImmutableProcessor because it reads
     * ImmutableType metadata from the shared Context, but does not reference
     * any compiled types that ImmutableProcessor generated.
     */
    val runsAfter: Set<String> get() = emptySet()

    /** Shared context, set by the orchestrator before [process] is called. */
    var ctx: T

    /** Execute this processor's logic. Called once per KSP round it participates in. */
    fun process(): R

    // ---- Deprecated: retained for binary compatibility, will be removed ----

    @Deprecated("Use dependsOn instead. Will be removed in a future version.", ReplaceWith("dependsOn"))
    val phase: Int get() = 1

    @Deprecated("Use dependsOn instead. Will be removed in a future version.", ReplaceWith("dependsOn"))
    val order: Int get() = 0
}

/**
 * Well-known processor IDs used in [ProcessorSpi.dependsOn] declarations.
 *
 * These constants are the fully qualified class names of built-in processors.
 * Using constants avoids typos and enables IDE navigation.
 */
const val ID_IMMUTABLE = "org.babyfish.jimmer.ksp.immutable.ImmutableProcessor"
const val ID_ERROR = "org.babyfish.jimmer.ksp.error.ErrorProcessor"
const val ID_DTO = "org.babyfish.jimmer.ksp.dto.DtoProcessor"
const val ID_TX = "org.babyfish.jimmer.ksp.transactional.TxProcessor"
const val ID_EXPORT_DOC = "org.babyfish.jimmer.ksp.client.ExportDocProcessor"
const val ID_TUPLE = "org.babyfish.jimmer.ksp.tuple.TypedTupleProcessor"
const val ID_CLIENT = "org.babyfish.jimmer.ksp.client.ClientProcessor"
