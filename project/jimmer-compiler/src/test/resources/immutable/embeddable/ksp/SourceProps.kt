@file:Suppress("warnings")
@file:GeneratedBy(type = demo.Location::class)

package demo

import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import org.babyfish.jimmer.`internal`.GeneratedBy
import org.babyfish.jimmer.kt.toImmutableProp
import org.babyfish.jimmer.meta.TypedProp
import org.babyfish.jimmer.sql.ast.Selection
import org.babyfish.jimmer.sql.kt.ast.expression.KEmbeddedPropExpression
import org.babyfish.jimmer.sql.kt.ast.expression.KNonNullEmbeddedPropExpression
import org.babyfish.jimmer.sql.kt.ast.expression.KNonNullPropExpression
import org.babyfish.jimmer.sql.kt.ast.expression.KNullableEmbeddedPropExpression
import org.babyfish.jimmer.sql.kt.ast.expression.KNullablePropExpression
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher

public val KNonNullEmbeddedPropExpression<Location>.city: KNonNullPropExpression<String>
    @GeneratedBy(type = Location::class)
    get() = get<String>(LocationProps.CITY.unwrap()) as KNonNullPropExpression<String>

public val KNullableEmbeddedPropExpression<Location>.city: KNullablePropExpression<String>
    @GeneratedBy(type = Location::class)
    get() = get(LocationProps.CITY.unwrap())

public val KEmbeddedPropExpression<Location>.zipCode: KNullablePropExpression<Int>
    @GeneratedBy(type = Location::class)
    get() = get<Int>(LocationProps.ZIP_CODE.unwrap()) as KNullablePropExpression<Int>

@GeneratedBy(type = Location::class)
public fun KNonNullEmbeddedPropExpression<Location>.fetchBy(block: LocationFetcherDsl.() -> Unit): Selection<Location> = fetch(newFetcher(Location::class).`by`(block))

@GeneratedBy(type = Location::class)
public fun KNullableEmbeddedPropExpression<Location>.fetchBy(block: LocationFetcherDsl.() -> Unit): Selection<Location?> = fetch(newFetcher(Location::class).`by`(block))

@GeneratedBy(type = Location::class)
public object LocationProps {
    public val CITY: TypedProp.Scalar<Location, String> =
            TypedProp.scalar(Location::city.toImmutableProp())

    public val ZIP_CODE: TypedProp.Scalar<Location, Int?> =
            TypedProp.scalar(Location::zipCode.toImmutableProp())
}
