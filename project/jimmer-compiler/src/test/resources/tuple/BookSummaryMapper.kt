package demo

import java.util.Collections
import kotlin.Any
import kotlin.Array
import kotlin.Long
import kotlin.Suppress
import kotlin.collections.List
import kotlin.reflect.KClass
import org.babyfish.jimmer.sql.ast.Selection
import org.babyfish.jimmer.sql.ast.table.BaseTable
import org.babyfish.jimmer.sql.ast.table.spi.BaseTableFactory
import org.babyfish.jimmer.sql.ast.table.spi.BaseTableSelectionKind
import org.babyfish.jimmer.sql.ast.table.spi.BaseTableSelectionLayout
import org.babyfish.jimmer.sql.kt.ast.expression.KNonNullExpression
import org.babyfish.jimmer.sql.kt.ast.expression.KNullableExpression
import org.babyfish.jimmer.sql.kt.ast.query.KBaseTableProjection
import org.babyfish.jimmer.sql.kt.ast.table.KBaseTableSymbol
import org.babyfish.jimmer.sql.kt.ast.table.KNonNullBaseTable
import org.babyfish.jimmer.sql.kt.ast.table.KNullableBaseTable
import org.babyfish.jimmer.sql.kt.ast.table.KPropsWeakJoin
import org.babyfish.jimmer.sql.kt.ast.table.KPropsWeakJoinFun
import org.babyfish.jimmer.sql.kt.ast.table.`impl`.AbstractKBaseTable
import org.babyfish.jimmer.sql.runtime.TupleMapper

public class BookSummaryMapper private constructor(
    private val selections: Array<Selection<*>?>,
) : TupleMapper<BookSummary>,
    KBaseTableProjection<BookSummaryTable, BookSummaryTable.Nullable> {
    @Suppress("UNCHECKED_CAST")
    override fun getSelections(): List<Selection<*>> = Collections.unmodifiableList(listOf(*selections as Array<Selection<*>>))

    override fun getBaseTableFactory(): BaseTableFactory<BookSummaryTable, BookSummaryTable.Nullable> = BookSummaryTable.FACTORY

    override fun getSelectionLayout(): BaseTableSelectionLayout = BookSummaryTable.SELECTION_LAYOUT

    override fun createTuple(args: Array<Any?>): BookSummary = BookSummary(
        book = args[0] as BookView,
        authorCount = args[1] as Long
    )

    public class AuthorCountBuilder internal constructor(
        private val selections: Array<Selection<*>?>,
    ) {
        public fun authorCount(selection: Selection<Long>): BookSummaryMapper {
            selections[1] = selection
            return BookSummaryMapper(selections)
        }
    }

    public companion object {
        public fun book(selection: Selection<BookView>): AuthorCountBuilder {
            val selections = arrayOfNulls<Selection<*>>(2)
            selections[0] = selection
            return AuthorCountBuilder(selections)
        }
    }
}

public class BookSummaryTable internal constructor(
    baseTable: BaseTable,
) : AbstractKBaseTable(baseTable),
    KNonNullBaseTable<BookSummaryTable.Nullable> {
    public val book: KNonNullExpression<BookView>
        get() = selection(0, false)

    public val authorCount: KNonNullExpression<Long>
        get() = selection(1, false)

    public fun <TT : KNonNullBaseTable<*>> weakJoin(targetSymbol: KBaseTableSymbol<TT>, weakJoinLambda: KPropsWeakJoinFun<BookSummaryTable, TT>): TT = weakJoinImpl(targetSymbol, weakJoinLambda)

    public fun <TT : KNonNullBaseTable<*>> weakJoin(targetSymbol: KBaseTableSymbol<TT>, weakJoinType: KClass<out KPropsWeakJoin<BookSummaryTable, TT>>): TT = weakJoinImpl(targetSymbol, weakJoinType)

    public fun <TNT : KNullableBaseTable, TT : KNonNullBaseTable<TNT>> weakOuterJoin(targetSymbol: KBaseTableSymbol<TT>, weakJoinLambda: KPropsWeakJoinFun<BookSummaryTable, TT>): TNT = weakOuterJoinImpl(targetSymbol, weakJoinLambda)

    public fun <TNT : KNullableBaseTable, TT : KNonNullBaseTable<TNT>> weakOuterJoin(targetSymbol: KBaseTableSymbol<TT>, weakJoinType: KClass<out KPropsWeakJoin<BookSummaryTable, TT>>): TNT = weakOuterJoinImpl(targetSymbol, weakJoinType)

    public class Nullable internal constructor(
        baseTable: BaseTable,
    ) : AbstractKBaseTable(baseTable),
        KNullableBaseTable {
        public val book: KNullableExpression<BookView>
            get() = selection(0, true)

        public val authorCount: KNullableExpression<Long>
            get() = selection(1, true)

        public fun <TT : KNonNullBaseTable<*>> weakJoin(targetSymbol: KBaseTableSymbol<TT>, weakJoinLambda: KPropsWeakJoinFun<Nullable, TT>): TT = weakJoinImpl(targetSymbol, weakJoinLambda)

        public fun <TT : KNonNullBaseTable<*>> weakJoin(targetSymbol: KBaseTableSymbol<TT>, weakJoinType: KClass<out KPropsWeakJoin<Nullable, TT>>): TT = weakJoinImpl(targetSymbol, weakJoinType)

        public fun <TNT : KNullableBaseTable, TT : KNonNullBaseTable<TNT>> weakOuterJoin(targetSymbol: KBaseTableSymbol<TT>, weakJoinLambda: KPropsWeakJoinFun<Nullable, TT>): TNT = weakOuterJoinImpl(targetSymbol, weakJoinLambda)

        public fun <TNT : KNullableBaseTable, TT : KNonNullBaseTable<TNT>> weakOuterJoin(targetSymbol: KBaseTableSymbol<TT>, weakJoinType: KClass<out KPropsWeakJoin<Nullable, TT>>): TNT = weakOuterJoinImpl(targetSymbol, weakJoinType)
    }

    public companion object {
        internal val FACTORY: BaseTableFactory<BookSummaryTable, Nullable> = BaseTableFactory.of(
                    { BookSummaryTable(it) },
                    { Nullable(it) }
                )

        internal val SELECTION_LAYOUT: BaseTableSelectionLayout = BaseTableSelectionLayout.of(
                    BaseTableSelectionKind.NON_NULL_EXPRESSION,
                    BaseTableSelectionKind.NON_NULL_EXPRESSION
                )
    }
}
