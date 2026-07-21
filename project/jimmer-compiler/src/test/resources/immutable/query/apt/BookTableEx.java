package demo;

import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.function.Function;
import org.babyfish.jimmer.internal.GeneratedBy;
import org.babyfish.jimmer.sql.JoinType;
import org.babyfish.jimmer.sql.ast.Predicate;
import org.babyfish.jimmer.sql.ast.impl.base.BaseTableOwner;
import org.babyfish.jimmer.sql.ast.impl.base.BaseTableSymbol;
import org.babyfish.jimmer.sql.ast.impl.base.BaseTableSymbols;
import org.babyfish.jimmer.sql.ast.impl.table.JWeakJoinLambdaFactory;
import org.babyfish.jimmer.sql.ast.impl.table.TableImplementor;
import org.babyfish.jimmer.sql.ast.impl.table.TableProxies;
import org.babyfish.jimmer.sql.ast.impl.table.WeakJoinHandle;
import org.babyfish.jimmer.sql.ast.impl.table.WeakJoinLambda;
import org.babyfish.jimmer.sql.ast.table.BaseTable;
import org.babyfish.jimmer.sql.ast.table.Table;
import org.babyfish.jimmer.sql.ast.table.WeakJoin;
import org.babyfish.jimmer.sql.ast.table.spi.AbstractTypedTable;
import org.babyfish.jimmer.sql.ast.table.spi.TableExProxy;
import org.babyfish.jimmer.sql.ast.table.spi.TableLike;

@GeneratedBy(
        type = Book.class
)
public class BookTableEx extends BookTable implements TableExProxy<Book, BookTable> {
    public static final BookTableEx $ = new BookTableEx(BookTable.$, (String)null);

    public BookTableEx() {
        super();
    }

    public BookTableEx(AbstractTypedTable.DelayedOperation<Book> delayedOperation) {
        super(delayedOperation);
    }

    public BookTableEx(TableImplementor<Book> table) {
        super(table);
    }

    protected BookTableEx(BookTable base, String joinDisabledReason) {
        super(base, joinDisabledReason);
    }

    protected BookTableEx(BookTable base, BaseTableOwner baseTableOwner) {
        super(base, baseTableOwner);
    }

    public BookTableEx parent() {
        __beforeJoin();
        if (raw != null) {
            return new BookTableEx(raw.joinImplementor(BookProps.PARENT.unwrap()));
        }
        return new BookTableEx(joinOperation(BookProps.PARENT.unwrap()));
    }

    public BookTableEx parent(JoinType joinType) {
        __beforeJoin();
        if (raw != null) {
            return new BookTableEx(raw.joinImplementor(BookProps.PARENT.unwrap(), joinType));
        }
        return new BookTableEx(joinOperation(BookProps.PARENT.unwrap(), joinType));
    }

    public BookTableEx children() {
        __beforeJoin();
        if (raw != null) {
            return new BookTableEx(raw.joinImplementor(BookProps.CHILDREN.unwrap()));
        }
        return new BookTableEx(joinOperation(BookProps.CHILDREN.unwrap()));
    }

    public BookTableEx children(JoinType joinType) {
        __beforeJoin();
        if (raw != null) {
            return new BookTableEx(raw.joinImplementor(BookProps.CHILDREN.unwrap(), joinType));
        }
        return new BookTableEx(joinOperation(BookProps.CHILDREN.unwrap(), joinType));
    }

    @Override
    public Predicate children(Function<BookTableEx, Predicate> block) {
        return exists(BookProps.CHILDREN.unwrap(), block);
    }

    public StoreTableEx store() {
        __beforeJoin();
        if (raw != null) {
            return new StoreTableEx(raw.joinImplementor(BookProps.STORE.unwrap()));
        }
        return new StoreTableEx(joinOperation(BookProps.STORE.unwrap()));
    }

    public StoreTableEx store(JoinType joinType) {
        __beforeJoin();
        if (raw != null) {
            return new StoreTableEx(raw.joinImplementor(BookProps.STORE.unwrap(), joinType));
        }
        return new StoreTableEx(joinOperation(BookProps.STORE.unwrap(), joinType));
    }

    @Override
    public BookTableEx asTableEx() {
        return this;
    }

    @Override
    public BookTableEx __disableJoin(String reason) {
        return new BookTableEx(this, reason);
    }

    @Override
    public BookTableEx __baseTableOwner(BaseTableOwner baseTableOwner) {
        return new BookTableEx(this, baseTableOwner);
    }

    public <TT extends Table<?>, WJ extends WeakJoin<BookTable, TT>> TT weakJoin(
            Class<WJ> weakJoinType) {
        return weakJoin(weakJoinType, JoinType.INNER);
    }

    @SuppressWarnings("all")
    public <TT extends Table<?>, WJ extends WeakJoin<BookTable, TT>> TT weakJoin(
            Class<WJ> weakJoinType, JoinType joinType) {
        __beforeJoin();
        if (raw != null) {
            return (TT)TableProxies.wrap(raw.weakJoinImplementor(weakJoinType, joinType));
        }
        return (TT)TableProxies.fluent(joinOperation(weakJoinType, joinType));
    }

    public <TT extends Table<?>> TT weakJoin(Class<TT> targetTableType,
            WeakJoin<BookTable, TT> weakJoinLambda) {
        return weakJoin(targetTableType, JoinType.INNER, weakJoinLambda);
    }

    @SuppressWarnings("all")
    public <TT extends Table<?>> TT weakJoin(Class<TT> targetTableType, JoinType joinType,
            WeakJoin<BookTable, TT> weakJoinLambda) {
        __beforeJoin();
        if (raw != null) {
            return (TT)TableProxies.wrap(raw.weakJoinImplementor(targetTableType, joinType, weakJoinLambda));
        }
        return (TT)TableProxies.fluent(joinOperation(targetTableType, joinType, weakJoinLambda));
    }

    public <TT extends BaseTable> TT weakJoin(TT targetBaseTable,
            WeakJoin<BookTable, TT> weakJoinLambda) {
        return weakJoin(targetBaseTable, JoinType.INNER, weakJoinLambda);
    }

    public <TT extends BaseTable> TT weakJoin(TT targetBaseTable, JoinType joinType,
            WeakJoin<BookTable, TT> weakJoinLambda) {
        WeakJoinLambda lambda = JWeakJoinLambdaFactory.get(weakJoinLambda);
        WeakJoinHandle handle = WeakJoinHandle.of(
            lambda,
            true,
            true,
            (WeakJoin<TableLike<?>, TableLike<?>>)(WeakJoin<?, ?>) weakJoinLambda
        );
        return (TT) BaseTableSymbols.of((BaseTableSymbol) targetBaseTable, this, handle, joinType);
    }
}
