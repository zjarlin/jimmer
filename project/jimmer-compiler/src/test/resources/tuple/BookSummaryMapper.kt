package demo

import java.util.Collections
import kotlin.Any
import kotlin.Array
import kotlin.Long
import kotlin.Suppress
import kotlin.collections.List
import org.babyfish.jimmer.sql.ast.Selection
import org.babyfish.jimmer.sql.runtime.TupleMapper

public class BookSummaryMapper private constructor(
    private val selections: Array<Selection<*>?>,
) : TupleMapper<BookSummary> {
    @Suppress("UNCHECKED_CAST")
    override fun getSelections(): List<Selection<*>> = Collections.unmodifiableList(listOf(*selections as Array<Selection<*>>))

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
