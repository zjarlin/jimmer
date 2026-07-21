package demo;

import java.lang.Class;
import java.lang.Deprecated;
import java.lang.Long;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.function.Function;
import org.babyfish.jimmer.internal.GeneratedBy;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.JoinType;
import org.babyfish.jimmer.sql.ast.Predicate;
import org.babyfish.jimmer.sql.ast.PropExpression;
import org.babyfish.jimmer.sql.ast.impl.base.BaseTableOwner;
import org.babyfish.jimmer.sql.ast.impl.table.TableImplementor;
import org.babyfish.jimmer.sql.ast.impl.table.TableProxies;
import org.babyfish.jimmer.sql.ast.table.PolymorphicTable;
import org.babyfish.jimmer.sql.ast.table.Table;
import org.babyfish.jimmer.sql.ast.table.TableEx;
import org.babyfish.jimmer.sql.ast.table.spi.AbstractTypedTable;

@GeneratedBy(
        type = Book.class
)
public class BookTable extends AbstractTypedTable<Book> implements BookProps, PolymorphicTable<Book> {
    public static final BookTable $ = new BookTable();

    public BookTable() {
        super(Book.class);
    }

    public BookTable(AbstractTypedTable.DelayedOperation<Book> delayedOperation) {
        super(Book.class, delayedOperation);
    }

    public BookTable(TableImplementor<Book> table) {
        super(table);
    }

    protected BookTable(BookTable base, String joinDisabledReason) {
        super(base, joinDisabledReason);
    }

    protected BookTable(BookTable base, BaseTableOwner baseTableOwner) {
        super(base, baseTableOwner);
    }

    @Override
    public PropExpression.Num<Long> id() {
        return __get(BookProps.ID.unwrap());
    }

    @Override
    public BookTable parent() {
        __beforeJoin();
        if (raw != null) {
            return new BookTable(raw.joinImplementor(BookProps.PARENT.unwrap()));
        }
        return new BookTable(joinOperation(BookProps.PARENT.unwrap()));
    }

    @Override
    public BookTable parent(JoinType joinType) {
        __beforeJoin();
        if (raw != null) {
            return new BookTable(raw.joinImplementor(BookProps.PARENT.unwrap(), joinType));
        }
        return new BookTable(joinOperation(BookProps.PARENT.unwrap(), joinType));
    }

    @Override
    public PropExpression.Num<Long> parentId() {
        return __getAssociatedId(BookProps.PARENT.unwrap());
    }

    @Override
    public Predicate children(Function<BookTableEx, Predicate> block) {
        return exists(BookProps.CHILDREN.unwrap(), block);
    }

    @Override
    public PropExpression.Str kind() {
        return __get(BookProps.KIND.unwrap());
    }

    @Override
    public PropExpression.Str name() {
        return __get(BookProps.NAME.unwrap());
    }

    @Override
    public StoreTable store() {
        __beforeJoin();
        if (raw != null) {
            return new StoreTable(raw.joinImplementor(BookProps.STORE.unwrap()));
        }
        return new StoreTable(joinOperation(BookProps.STORE.unwrap()));
    }

    @Override
    public StoreTable store(JoinType joinType) {
        __beforeJoin();
        if (raw != null) {
            return new StoreTable(raw.joinImplementor(BookProps.STORE.unwrap(), joinType));
        }
        return new StoreTable(joinOperation(BookProps.STORE.unwrap(), joinType));
    }

    @Override
    public PropExpression.Num<Long> storeId() {
        return __getAssociatedId(BookProps.STORE.unwrap());
    }

    @Override
    public LocationPropExpression location() {
        return new LocationPropExpression(__get(BookProps.LOCATION.unwrap()));
    }

    @Override
    public BookTableEx asTableEx() {
        return new BookTableEx(this, (String)null);
    }

    @Override
    public BookTable __disableJoin(String reason) {
        return new BookTable(this, reason);
    }

    @Override
    public BookTable __baseTableOwner(BaseTableOwner baseTableOwner) {
        return new BookTable(this, baseTableOwner);
    }

    @Override
    @SuppressWarnings("all")
    public <TT extends Table<?>> TT treatAs(Class<TT> tableType) {
        ImmutableType treatedAs = TableProxies.tableType(tableType);
        __beforeJoin();
        if (raw != null) {
            return (TT)TableProxies.wrap(raw.treatAsImplementor(treatedAs, JoinType.INNER));
        }
        return (TT)TableProxies.fluent(treatAsOperation(treatedAs, JoinType.INNER));
    }

    @Override
    @SuppressWarnings("all")
    public <TT extends Table<?>> TT tryTreatAs(Class<TT> tableType) {
        ImmutableType treatedAs = TableProxies.tableType(tableType);
        __beforeJoin();
        if (raw != null) {
            return (TT)TableProxies.wrap(raw.treatAsImplementor(treatedAs, JoinType.LEFT));
        }
        return (TT)TableProxies.fluent(treatAsOperation(treatedAs, JoinType.LEFT));
    }

    @Override
    public Predicate instanceOf(Class<? extends Book> type) {
        return TableProxies.instanceOf(this, type);
    }

    @Override
    public Predicate exactType(Class<? extends Book> type) {
        return TableProxies.exactType(this, type);
    }

    @GeneratedBy(
            type = Book.class
    )
    public static class Remote extends AbstractTypedTable<Book> {
        public Remote(AbstractTypedTable.DelayedOperation delayedOperation) {
            super(Book.class, delayedOperation);
        }

        public Remote(TableImplementor<Book> table) {
            super(table);
        }

        public Remote(Remote base, BaseTableOwner baseTableOwner) {
            super(base, baseTableOwner);
        }

        public PropExpression.Num<Long> id() {
            return (org.babyfish.jimmer.sql.ast.PropExpression.Num<java.lang.Long>)this.<Long>get(BookProps.ID.unwrap());
        }

        @Override
        @Deprecated
        public TableEx<Book> asTableEx() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Remote __disableJoin(String reason) {
            return this;
        }

        @Override
        public Remote __baseTableOwner(BaseTableOwner baseTableOwner) {
            return new Remote(this, baseTableOwner);
        }
    }
}
