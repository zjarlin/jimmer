package demo;

import java.lang.Long;
import org.babyfish.jimmer.sql.ast.Expression;
import org.babyfish.jimmer.sql.ast.NumericExpression;
import org.babyfish.jimmer.sql.ast.table.BaseTable;
import org.babyfish.jimmer.sql.ast.table.spi.AbstractTypedBaseTable;
import org.babyfish.jimmer.sql.ast.table.spi.BaseTableFactory;

public final class BookSummaryTable extends AbstractTypedBaseTable<BookSummaryTable> {
    static final BaseTableFactory<BookSummaryTable, BookSummaryTable> FACTORY = BaseTableFactory.of(BookSummaryTable::new);

    BookSummaryTable(BaseTable baseTable) {
        super(baseTable);
    }

    public Expression<BookView> getBook() {
        return selection(0);
    }

    public NumericExpression<Long> getAuthorCount() {
        return selection(1);
    }
}
