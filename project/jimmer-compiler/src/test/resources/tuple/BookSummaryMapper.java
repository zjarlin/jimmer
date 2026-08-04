package demo;

import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.babyfish.jimmer.sql.ast.Selection;
import org.babyfish.jimmer.sql.ast.query.BaseTableProjection;
import org.babyfish.jimmer.sql.ast.table.spi.BaseTableFactory;
import org.babyfish.jimmer.sql.runtime.TupleMapper;

public class BookSummaryMapper implements TupleMapper<BookSummary>, BaseTableProjection<BookSummaryTable> {
    private final Selection<?>[] selections;

    BookSummaryMapper(Selection<?>[] selections) {
        this.selections = selections;
    }

    @Override
    public List<Selection<?>> getSelections() {
        return Collections.unmodifiableList(Arrays.asList(selections));
    }

    @Override
    public BaseTableFactory<BookSummaryTable, BookSummaryTable> getBaseTableFactory() {
        return BookSummaryTable.FACTORY;
    }

    @Override
    public BookSummary createTuple(Object[] args) {
        BookSummary __tuple = new BookSummary();
        __tuple.setBook((BookView)args[0]);
        __tuple.setAuthorCount((Long)args[1]);
        return __tuple;
    }

    public static AuthorCountBuilder book(Selection<BookView> selection) {
        Selection<?>[] selections = new Selection<?>[2];
        selections[0] = selection;
        return new AuthorCountBuilder(selections);
    }

    public static class AuthorCountBuilder {
        private final Selection<?>[] selections;

        AuthorCountBuilder(Selection<?>[] selections) {
            this.selections = selections;
        }

        public BookSummaryMapper authorCount(Selection<Long> selection) {
            selections[1] = selection;
            return new BookSummaryMapper(selections);
        }
    }
}
