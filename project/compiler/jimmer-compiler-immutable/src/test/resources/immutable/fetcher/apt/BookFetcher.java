package demo;

import java.lang.Override;
import java.util.function.Consumer;
import org.babyfish.jimmer.internal.GeneratedBy;
import org.babyfish.jimmer.lang.NewChain;
import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.sql.ast.table.Table;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.fetcher.FieldConfig;
import org.babyfish.jimmer.sql.fetcher.IdOnlyFetchType;
import org.babyfish.jimmer.sql.fetcher.ListFieldConfig;
import org.babyfish.jimmer.sql.fetcher.RecursiveListFieldConfig;
import org.babyfish.jimmer.sql.fetcher.RecursiveReferenceFieldConfig;
import org.babyfish.jimmer.sql.fetcher.ReferenceFetchType;
import org.babyfish.jimmer.sql.fetcher.ReferenceFieldConfig;
import org.babyfish.jimmer.sql.fetcher.impl.FetcherImpl;
import org.babyfish.jimmer.sql.fetcher.spi.AbstractTypedFetcher;

@GeneratedBy(
        type = Book.class
)
public class BookFetcher extends AbstractTypedFetcher<Book, BookFetcher> {
    public static final BookFetcher $ = new BookFetcher(null);

    private BookFetcher(FetcherImpl<Book> base) {
        super(Book.class, base);
    }

    private BookFetcher(BookFetcher prev, ImmutableProp prop, boolean negative,
            IdOnlyFetchType idOnlyFetchType) {
        super(prev, prop, negative, idOnlyFetchType);
    }

    private BookFetcher(BookFetcher prev, ImmutableProp prop,
            FieldConfig<?, ? extends Table<?>> fieldConfig) {
        super(prev, prop, fieldConfig);
    }

    private BookFetcher(BookFetcher prev, FetcherImpl<?> typeBranchFetcher) {
        super(prev, typeBranchFetcher);
    }

    public static BookFetcher $from(Fetcher<Book> base) {
        return base instanceof BookFetcher ? 
        	(BookFetcher)base : 
        	new BookFetcher((FetcherImpl<Book>)base);
    }

    /**
     * Book title.
     */
    @NewChain
    public BookFetcher title() {
        return add("title");
    }

    /**
     * Book title.
     */
    @NewChain
    public BookFetcher title(boolean enabled) {
        return enabled ? add("title") : remove("title");
    }

    @NewChain
    public BookFetcher parent() {
        return add("parent");
    }

    @NewChain
    public BookFetcher parent(boolean enabled) {
        return enabled ? add("parent") : remove("parent");
    }

    @NewChain
    public BookFetcher parent(Fetcher<Book> childFetcher) {
        return add("parent", childFetcher);
    }

    @NewChain
    public BookFetcher parent(IdOnlyFetchType idOnlyFetchType) {
        return add("parent", idOnlyFetchType);
    }

    @NewChain
    public BookFetcher parent(Fetcher<Book> childFetcher,
            Consumer<ReferenceFieldConfig<Book, BookTable>> fieldConfig) {
        return add("parent", childFetcher, fieldConfig);
    }

    @NewChain
    public BookFetcher parent(ReferenceFetchType fetchType, Fetcher<Book> childFetcher) {
        return parent(childFetcher, cfg -> cfg.fetchType(fetchType));
    }

    @NewChain
    public BookFetcher recursiveParent() {
        return addRecursion("parent", null);
    }

    @NewChain
    public BookFetcher recursiveParent(
            Consumer<RecursiveReferenceFieldConfig<Book, BookTable>> fieldConfig) {
        return addRecursion("parent", fieldConfig);
    }

    @NewChain
    public BookFetcher parentId() {
        return add("parentId");
    }

    @NewChain
    public BookFetcher parentId(boolean enabled) {
        return enabled ? add("parentId") : remove("parentId");
    }

    @NewChain
    public BookFetcher children() {
        return add("children");
    }

    @NewChain
    public BookFetcher children(boolean enabled) {
        return enabled ? add("children") : remove("children");
    }

    @NewChain
    public BookFetcher children(Fetcher<Book> childFetcher) {
        return add("children", childFetcher);
    }

    @NewChain
    public BookFetcher children(Fetcher<Book> childFetcher,
            Consumer<ListFieldConfig<Book, BookTable>> fieldConfig) {
        return add("children", childFetcher, fieldConfig);
    }

    @NewChain
    public BookFetcher recursiveChildren() {
        return addRecursion("children", null);
    }

    @NewChain
    public BookFetcher recursiveChildren(
            Consumer<RecursiveListFieldConfig<Book, BookTable>> fieldConfig) {
        return addRecursion("children", fieldConfig);
    }

    @Override
    protected BookFetcher createFetcher(ImmutableProp prop, boolean negative,
            IdOnlyFetchType idOnlyFetchType) {
        return new BookFetcher(this, prop, negative, idOnlyFetchType);
    }

    @Override
    protected BookFetcher createFetcher(ImmutableProp prop,
            FieldConfig<?, ? extends Table<?>> fieldConfig) {
        return new BookFetcher(this, prop, fieldConfig);
    }

    @Override
    protected BookFetcher createFetcher(FetcherImpl<?> typeBranchFetcher) {
        return new BookFetcher(this, typeBranchFetcher);
    }
}
