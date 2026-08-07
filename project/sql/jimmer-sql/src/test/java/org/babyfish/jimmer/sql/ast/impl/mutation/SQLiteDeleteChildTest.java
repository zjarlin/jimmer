package org.babyfish.jimmer.sql.ast.impl.mutation;

import org.babyfish.jimmer.sql.DissociateAction;
import org.babyfish.jimmer.sql.ast.mutation.AffectedTable;
import org.babyfish.jimmer.sql.ast.tuple.Tuple2;
import org.babyfish.jimmer.sql.common.NativeDatabases;
import org.babyfish.jimmer.sql.dialect.SQLiteDialect;
import org.babyfish.jimmer.sql.model.Book;
import org.babyfish.jimmer.sql.model.BookProps;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.babyfish.jimmer.sql.common.Constants.*;

public class SQLiteDeleteChildTest extends AbstractChildOperatorTest {
    @BeforeAll
    public static void beforeAll() {
        DataSource dataSource = NativeDatabases.SQLITE_DATA_SOURCE;
        jdbc(dataSource, false, con -> initDatabase(con, "database-sqlite.sql"));
    }

    @Test
    public void testDisconnectExceptBySimpleInPredicate() {
        connectAndExpect(NativeDatabases.SQLITE_DATA_SOURCE,
                con -> {
                    ChildTableOperator operator = operator(
                            getSqlClient(it -> {
                                it.setDialect(new SQLiteDialect());
                                it.setMaxCommandJoinCount(3);
                            }),
                            con,
                            BookProps.STORE.unwrap(),
                            DissociateAction.DELETE
                    );
                    operator.disconnectExcept(
                            RetainIdPairs.of(
                                    new Tuple2<>(manningId, graphQLInActionId1),
                                    new Tuple2<>(manningId, graphQLInActionId2)
                            )
                    );
                    return operator.ctx.affectedRowCountMap;
                },
                ctx -> {
                    ctx.statement(it -> {
                        it.sql(
                                "delete from BOOK_AUTHOR_MAPPING as tb_1_ " +
                                        "where exists (" +
                                        "--->select * " +
                                        "--->from BOOK tb_2_ " +
                                        "--->where " +
                                        "--->--->tb_1_.BOOK_ID = tb_2_.ID " +
                                        "--->and " +
                                        "--->--->tb_2_.STORE_ID = ? " +
                                        "--->and " +
                                        "--->--->tb_2_.ID not in (?, ?)" +
                                        ")"
                        );
                        it.variables(manningId, graphQLInActionId1, graphQLInActionId2);
                    });
                    ctx.statement(it -> {
                        it.sql(
                                "delete from BOOK " +
                                        "where STORE_ID = ? and ID not in (?, ?)"
                        );
                        it.variables(manningId, graphQLInActionId1, graphQLInActionId2);
                    });
                    ctx.value(map -> {
                        Assertions.assertEquals(2, map.size());
                        Assertions.assertEquals(1, map.get(AffectedTable.of(BookProps.AUTHORS)));
                        Assertions.assertEquals(1, map.get(AffectedTable.of(Book.class)));
                    });
                }
        );
    }

    @Test
    public void testDisconnectExceptByComplexInPredicate() {
        connectAndExpect(
                NativeDatabases.SQLITE_DATA_SOURCE,
                con -> {
                    ChildTableOperator operator = operator(
                            getSqlClient(it -> {
                                it.setDialect(new SQLiteDialect());
                            }),
                            con,
                            BookProps.STORE.unwrap(),
                            DissociateAction.DELETE
                    );
                    operator.disconnectExcept(
                            RetainIdPairs.of(
                                    new Tuple2<>(oreillyId, learningGraphQLId1),
                                    new Tuple2<>(oreillyId, effectiveTypeScriptId1),
                                    new Tuple2<>(oreillyId, programmingTypeScriptId1),
                                    new Tuple2<>(manningId, graphQLInActionId1)
                            )
                    );
                    return operator.ctx.affectedRowCountMap;
                },
                ctx -> {
                    ctx.statement(it -> {
                        it.sql(
                                "delete from BOOK_AUTHOR_MAPPING as tb_1_ " +
                                        "where exists (" +
                                        "--->select * " +
                                        "--->from BOOK tb_2_ " +
                                        "--->where " +
                                        "--->--->tb_1_.BOOK_ID = tb_2_.ID " +
                                        "--->and " +
                                        "--->--->tb_2_.STORE_ID in (?, ?) " +
                                        "--->and " +
                                        "--->--->(tb_2_.STORE_ID, tb_2_.ID) not in ((?, ?), (?, ?), (?, ?), (?, ?))" +
                                        ")"
                        );
                        it.variables(
                                oreillyId, manningId,
                                oreillyId, learningGraphQLId1,
                                oreillyId, effectiveTypeScriptId1,
                                oreillyId, programmingTypeScriptId1,
                                manningId, graphQLInActionId1
                        );
                    });
                    ctx.statement(it -> {
                        it.sql(
                                "delete from BOOK where " +
                                        "--->STORE_ID in (?, ?) " +
                                        "and " +
                                        "--->(STORE_ID, ID) not in ((?, ?), (?, ?), (?, ?), (?, ?))"
                        );
                        it.variables(
                                oreillyId, manningId,
                                oreillyId, learningGraphQLId1,
                                oreillyId, effectiveTypeScriptId1,
                                oreillyId, programmingTypeScriptId1,
                                manningId, graphQLInActionId1
                        );
                    });
                    ctx.value(map -> {
                        Assertions.assertEquals(2, map.size());
                        Assertions.assertEquals(10, map.get(AffectedTable.of(BookProps.AUTHORS)));
                        Assertions.assertEquals(8, map.get(AffectedTable.of(Book.class)));
                    });
                }
        );
    }
}