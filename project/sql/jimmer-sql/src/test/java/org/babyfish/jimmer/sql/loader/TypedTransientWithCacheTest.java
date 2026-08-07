package org.babyfish.jimmer.sql.loader;

import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.fetcher.impl.DataLoader;
import org.babyfish.jimmer.sql.model.BookStore;
import org.babyfish.jimmer.sql.model.BookStoreFetcher;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TypedTransientWithCacheTest extends AbstractCachedLoaderTest {

    @Test
    public void testTypedResolverCacheFallback() {
        Fetcher<BookStore> fetcher = BookStoreFetcher.$.nameWithVersion();
        for (int i = 0; i < 2; i++) {
            boolean useSql = i == 0;
            connectAndExpect(
                    con -> new DataLoader(
                            (JSqlClientImplementor) getCachedSqlClient(),
                            con,
                            null,
                            fetcher.getFieldMap().get("nameWithVersion")
                    ).load(Entities.BOOK_STORES),
                    ctx -> {
                        if (useSql) {
                            ctx.sql(
                                    "select tb_1_.ID, tb_1_.NAME, tb_1_.VERSION " +
                                            "from BOOK_STORE tb_1_ " +
                                            "where tb_1_.ID in (?, ?)"
                            );
                        }
                        ctx.row(0, map -> {
                            Assertions.assertEquals(
                                    "O'REILLY#0",
                                    map.get(Entities.BOOK_STORES.get(0))
                            );
                            Assertions.assertEquals(
                                    "MANNING#0",
                                    map.get(Entities.BOOK_STORES.get(1))
                            );
                        });
                    }
            );
        }
    }
}
