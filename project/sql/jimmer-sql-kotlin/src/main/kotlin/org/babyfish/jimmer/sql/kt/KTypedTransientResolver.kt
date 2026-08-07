package org.babyfish.jimmer.sql.kt

import org.babyfish.jimmer.sql.TypedTransientResolver

/**
 * A specialized variant of [KTransientResolver]. When conditions are appropriate, it will use
 * [resolve(Collection, Context)][resolve] to provide an extra [Context][TypedTransientResolver.Context] parameter,
 * which supplies the collection of entities currently being calculated, allowing access to other properties
 * during calculation.
 *
 * You need to implement both abstract `resolve` functions to ensure that when the [Context][TypedTransientResolver.Context]
 * generation conditions are not met, it can fall back to the same behavior as [KTransientResolver].
 *
 * [Context][TypedTransientResolver.Context] may not be generated in scenarios where caching is configured.
 *
 * This type is only for Kotlin. If you use Java, see [TypedTransientResolver][org.babyfish.jimmer.sql.TypedTransientResolver].
 *
 * NOTE: This type is experimental and may undergo major changes or be removed in future versions.
 *
 * @since 0.9.121
 * @param [E] The current entity type
 * @param [ID] The id type of current entity
 * @param [V] The calculated type, there are three possibilities
 * - If the calculated property is NOT association,
 * `V` should be the property type
 * - If the calculated property is associated reference,
 * `V`should be the associated-id type
 * - If the calculated property is associated list,
 * `V` should be the type of list whose elements
 * are associated ids
 */
@ExperimentalTypedTransientResolver
interface KTypedTransientResolver<E : Any, ID: Any, V> : KTransientResolver<ID, V>, TypedTransientResolver<E, ID, V> {
    /**
     * When the conditions for generating [TypedTransientResolver.Context] are met, this method will be used
     * instead of [resolve(Collection)][resolve] to perform complex property calculations.
     *
     * @param ids     A batch of ids of the current objects that are resolving calculated property,
     *                it is not null and not empty
     * @param context The context of current entities being resolved
     * @return A map contains resolved values
     */
    override fun resolve(ids: Collection<ID>, context: TypedTransientResolver.Context<E>): Map<ID, V>
}
