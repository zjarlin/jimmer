package demo;

import demo.alpha.BookTable;
import org.babyfish.jimmer.internal.GeneratedBy;

@GeneratedBy
public interface Tables {
    BookTable BOOK_TABLE = BookTable.$;

    demo.beta.BookTable BOOK_TABLE_2 = demo.beta.BookTable.$;
}
