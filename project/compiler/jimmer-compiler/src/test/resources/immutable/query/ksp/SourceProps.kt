@file:Suppress("warnings")
@file:GeneratedBy(type = demo.Book::class)

package demo

import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.reflect.KClass
import org.babyfish.jimmer.`internal`.GeneratedBy
import org.babyfish.jimmer.kt.toImmutableProp
import org.babyfish.jimmer.meta.TypedProp
import org.babyfish.jimmer.sql.ast.Selection
import org.babyfish.jimmer.sql.kt.ast.expression.KNonNullEmbeddedPropExpression
import org.babyfish.jimmer.sql.kt.ast.expression.KNonNullExpression
import org.babyfish.jimmer.sql.kt.ast.expression.KNonNullPropExpression
import org.babyfish.jimmer.sql.kt.ast.expression.KNullableEmbeddedPropExpression
import org.babyfish.jimmer.sql.kt.ast.expression.KNullablePropExpression
import org.babyfish.jimmer.sql.kt.ast.table.KImplicitSubQueryTable
import org.babyfish.jimmer.sql.kt.ast.table.KNonNullProps
import org.babyfish.jimmer.sql.kt.ast.table.KNonNullTable
import org.babyfish.jimmer.sql.kt.ast.table.KNonNullTableEx
import org.babyfish.jimmer.sql.kt.ast.table.KNullableProps
import org.babyfish.jimmer.sql.kt.ast.table.KNullableTable
import org.babyfish.jimmer.sql.kt.ast.table.KNullableTableEx
import org.babyfish.jimmer.sql.kt.ast.table.KProps
import org.babyfish.jimmer.sql.kt.ast.table.KRemoteRef
import org.babyfish.jimmer.sql.kt.ast.table.KTableEx
import org.babyfish.jimmer.sql.kt.ast.table.`impl`.KPolymorphicTables
import org.babyfish.jimmer.sql.kt.ast.table.`impl`.KRemoteRefImplementor
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher

public val KNonNullProps<Book>.id: KNonNullPropExpression<Long>
    @GeneratedBy(type = Book::class)
    get() = get<Long>(BookProps.ID.unwrap()) as KNonNullPropExpression<Long>

public val KNullableProps<Book>.id: KNullablePropExpression<Long>
    @GeneratedBy(type = Book::class)
    get() = get<Long>(BookProps.ID.unwrap()) as KNullablePropExpression<Long>

public val KProps<Book>.parent: KNonNullTable<Book>
    @GeneratedBy(type = Book::class)
    get() = join(BookProps.PARENT.unwrap())

public val KProps<Book>.`parent?`: KNullableTable<Book>
    @GeneratedBy(type = Book::class)
    get() = outerJoin(BookProps.PARENT.unwrap())

public val KTableEx<Book>.parent: KNonNullTableEx<Book>
    @GeneratedBy(type = Book::class)
    get() = join(BookProps.PARENT.unwrap())

public val KTableEx<Book>.`parent?`: KNullableTableEx<Book>
    @GeneratedBy(type = Book::class)
    get() = outerJoin(BookProps.PARENT.unwrap())

public val KProps<Book>.parentId: KNullablePropExpression<Long>
    @GeneratedBy(type = Book::class)
    get() = getAssociatedId<Long>(BookProps.PARENT.unwrap()) as KNullablePropExpression<Long>

public fun KProps<Book>.children(block: KImplicitSubQueryTable<Book>.() -> KNonNullExpression<Boolean>?): KNonNullExpression<Boolean>? = exists(BookProps.CHILDREN.unwrap(), block)

public val KTableEx<Book>.children: KNonNullTableEx<Book>
    @GeneratedBy(type = Book::class)
    get() = join(BookProps.CHILDREN.unwrap())

public val KTableEx<Book>.`children?`: KNullableTableEx<Book>
    @GeneratedBy(type = Book::class)
    get() = outerJoin(BookProps.CHILDREN.unwrap())

public val KNonNullProps<Book>.kind: KNonNullPropExpression<String>
    @GeneratedBy(type = Book::class)
    get() = get<String>(BookProps.KIND.unwrap()) as KNonNullPropExpression<String>

public val KNullableProps<Book>.kind: KNullablePropExpression<String>
    @GeneratedBy(type = Book::class)
    get() = get<String>(BookProps.KIND.unwrap()) as KNullablePropExpression<String>

public val KNonNullProps<Book>.name: KNonNullPropExpression<String>
    @GeneratedBy(type = Book::class)
    get() = get<String>(BookProps.NAME.unwrap()) as KNonNullPropExpression<String>

public val KNullableProps<Book>.name: KNullablePropExpression<String>
    @GeneratedBy(type = Book::class)
    get() = get<String>(BookProps.NAME.unwrap()) as KNullablePropExpression<String>

public val KProps<Book>.store: KNonNullTable<Store>
    @GeneratedBy(type = Book::class)
    get() = join(BookProps.STORE.unwrap())

public val KProps<Book>.`store?`: KNullableTable<Store>
    @GeneratedBy(type = Book::class)
    get() = outerJoin(BookProps.STORE.unwrap())

public val KTableEx<Book>.store: KNonNullTableEx<Store>
    @GeneratedBy(type = Book::class)
    get() = join(BookProps.STORE.unwrap())

public val KTableEx<Book>.`store?`: KNullableTableEx<Store>
    @GeneratedBy(type = Book::class)
    get() = outerJoin(BookProps.STORE.unwrap())

public val KProps<Book>.storeId: KNullablePropExpression<Long>
    @GeneratedBy(type = Book::class)
    get() = getAssociatedId<Long>(BookProps.STORE.unwrap()) as KNullablePropExpression<Long>

public val KNonNullProps<Book>.location: KNonNullEmbeddedPropExpression<Location>
    @GeneratedBy(type = Book::class)
    get() = get<Location>(BookProps.LOCATION.unwrap()) as KNonNullEmbeddedPropExpression<Location>

public val KNullableProps<Book>.location: KNullableEmbeddedPropExpression<Location>
    @GeneratedBy(type = Book::class)
    get() = get<Location>(BookProps.LOCATION.unwrap()) as KNullableEmbeddedPropExpression<Location>

public val KRemoteRef.NonNull<Book>.id: KNonNullPropExpression<Long>
    @GeneratedBy(type = Book::class)
    get() = (this as KRemoteRefImplementor<*>).id<Long>() as KNonNullPropExpression<Long>

public val KRemoteRef.Nullable<Book>.id: KNullablePropExpression<Long>
    @GeneratedBy(type = Book::class)
    get() = (this as KRemoteRefImplementor<*>).id<Long>() as KNullablePropExpression<Long>

@GeneratedBy(type = Book::class)
public fun KNonNullTable<Book>.fetchBy(block: BookFetcherDsl.() -> Unit): Selection<Book> = fetch(newFetcher(Book::class).`by`(block))

@GeneratedBy(type = Book::class)
public fun KNullableTable<Book>.fetchBy(block: BookFetcherDsl.() -> Unit): Selection<Book?> = fetch(newFetcher(Book::class).`by`(block))

@GeneratedBy(type = Book::class)
public fun <S : Book> KNonNullTableEx<Book>.treatAs(type: KClass<S>): KNonNullTableEx<S> = KPolymorphicTables.treatAs(this, type)

@GeneratedBy(type = Book::class)
public fun <S : Book> KNullableTableEx<Book>.treatAs(type: KClass<S>): KNonNullTableEx<S> = KPolymorphicTables.treatAs(this, type)

@GeneratedBy(type = Book::class)
public fun <S : Book> KNonNullTableEx<Book>.tryTreatAs(type: KClass<S>): KNullableTableEx<S> = KPolymorphicTables.tryTreatAs(this, type)

@GeneratedBy(type = Book::class)
public fun <S : Book> KNullableTableEx<Book>.tryTreatAs(type: KClass<S>): KNullableTableEx<S> = KPolymorphicTables.tryTreatAs(this, type)

@GeneratedBy(type = Book::class)
public inline fun <reified S : Book> KNonNullTableEx<Book>.treatAs(): KNonNullTableEx<S> = treatAs(S::class)

@GeneratedBy(type = Book::class)
public inline fun <reified S : Book> KNullableTableEx<Book>.treatAs(): KNonNullTableEx<S> = treatAs(S::class)

@GeneratedBy(type = Book::class)
public inline fun <reified S : Book> KNonNullTableEx<Book>.tryTreatAs(): KNullableTableEx<S> = tryTreatAs(S::class)

@GeneratedBy(type = Book::class)
public inline fun <reified S : Book> KNullableTableEx<Book>.tryTreatAs(): KNullableTableEx<S> = tryTreatAs(S::class)

@GeneratedBy(type = Book::class)
public fun KTableEx<Book>.instanceOf(type: KClass<out Book>): KNonNullExpression<Boolean> = KPolymorphicTables.instanceOf(this, type)

@GeneratedBy(type = Book::class)
public inline fun <reified S : Book> KTableEx<Book>.instanceOf(): KNonNullExpression<Boolean> = instanceOf(S::class)

@GeneratedBy(type = Book::class)
public fun KTableEx<Book>.exactType(type: KClass<out Book>): KNonNullExpression<Boolean> = KPolymorphicTables.exactType(this, type)

@GeneratedBy(type = Book::class)
public inline fun <reified S : Book> KTableEx<Book>.exactType(): KNonNullExpression<Boolean> = exactType(S::class)

@GeneratedBy(type = Book::class)
public object BookProps {
    public val ID: TypedProp.Scalar<Book, Long> = TypedProp.scalar(Book::id.toImmutableProp())

    public val PARENT: TypedProp.Reference<Book, Book?> =
            TypedProp.reference(Book::parent.toImmutableProp())

    public val PARENT_ID: TypedProp.Scalar<Book, Long?> =
            TypedProp.scalar(Book::parentId.toImmutableProp())

    public val CHILDREN: TypedProp.ReferenceList<Book, Book> =
            TypedProp.referenceList(Book::children.toImmutableProp())

    public val KIND: TypedProp.Scalar<Book, String> = TypedProp.scalar(Book::kind.toImmutableProp())

    public val NAME: TypedProp.Scalar<Book, String> = TypedProp.scalar(Book::name.toImmutableProp())

    public val STORE: TypedProp.Reference<Book, Store?> =
            TypedProp.reference(Book::store.toImmutableProp())

    public val STORE_ID: TypedProp.Scalar<Book, Long?> =
            TypedProp.scalar(Book::storeId.toImmutableProp())

    public val LOCATION: TypedProp.Reference<Book, Location> =
            TypedProp.reference(Book::location.toImmutableProp())
}
