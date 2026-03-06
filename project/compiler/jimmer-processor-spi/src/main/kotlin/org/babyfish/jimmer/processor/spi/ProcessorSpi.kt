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
}

/**
 * Well-known processor IDs used in [ProcessorSpi.dependsOn] declarations.
 *
 * These constants are the fully qualified class names of built-in processors.
 * Using constants avoids typos and enables IDE navigation.
 */
const val IMMUTABLE_PROCESSOR = "org.babyfish.jimmer.ksp.immutable.ImmutableProcessor"
const val ERROR_PROCESSOR = "org.babyfish.jimmer.ksp.error.ErrorProcessor"
const val DTO_PROCESSOR = "org.babyfish.jimmer.ksp.dto.DtoProcessor"
const val TX_PROCESSOR = "org.babyfish.jimmer.ksp.transactional.TxProcessor"
const val EXPORT_DOC_PROCESSOR = "org.babyfish.jimmer.ksp.client.ExportDocProcessor"
const val TYPED_TUPLE_PROCESSOR = "org.babyfish.jimmer.ksp.tuple.TypedTupleProcessor"
const val CLIENT_PROCESSOR = "org.babyfish.jimmer.ksp.client.ClientProcessor"
