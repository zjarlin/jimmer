package demo;

import java.lang.Long;
import java.lang.String;
import java.util.function.Function;
import org.babyfish.jimmer.internal.GeneratedBy;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.meta.TypedProp;
import org.babyfish.jimmer.sql.JoinType;
import org.babyfish.jimmer.sql.ast.Predicate;
import org.babyfish.jimmer.sql.ast.PropExpression;
import org.babyfish.jimmer.sql.ast.Selection;
import org.babyfish.jimmer.sql.ast.table.Props;
import org.babyfish.jimmer.sql.ast.table.PropsFor;

@GeneratedBy(
        type = Book.class
)
@PropsFor(Book.class)
public interface BookProps extends Props, Selection<Book> {
    TypedProp.Scalar<Book, Long> ID = 
        TypedProp.scalar(ImmutableType.get(Book.class).getProp("id"));

    TypedProp.Reference<Book, Book> PARENT = 
        TypedProp.reference(ImmutableType.get(Book.class).getProp("parent"));

    TypedProp.Scalar<Book, Long> PARENT_ID = 
        TypedProp.scalar(ImmutableType.get(Book.class).getProp("parentId"));

    TypedProp.ReferenceList<Book, Book> CHILDREN = 
        TypedProp.referenceList(ImmutableType.get(Book.class).getProp("children"));

    TypedProp.Scalar<Book, String> KIND = 
        TypedProp.scalar(ImmutableType.get(Book.class).getProp("kind"));

    TypedProp.Scalar<Book, String> NAME = 
        TypedProp.scalar(ImmutableType.get(Book.class).getProp("name"));

    TypedProp.Reference<Book, Store> STORE = 
        TypedProp.reference(ImmutableType.get(Book.class).getProp("store"));

    TypedProp.Scalar<Book, Long> STORE_ID = 
        TypedProp.scalar(ImmutableType.get(Book.class).getProp("storeId"));

    TypedProp.Reference<Book, Location> LOCATION = 
        TypedProp.reference(ImmutableType.get(Book.class).getProp("location"));

    PropExpression.Num<Long> id();

    PropExpression.Str kind();

    PropExpression.Str name();

    StoreTable store();

    StoreTable store(JoinType joinType);

    PropExpression.Num<Long> storeId();

    LocationPropExpression location();

    BookTable parent();

    BookTable parent(JoinType joinType);

    PropExpression.Num<Long> parentId();

    Predicate children(Function<BookTableEx, Predicate> block);
}
