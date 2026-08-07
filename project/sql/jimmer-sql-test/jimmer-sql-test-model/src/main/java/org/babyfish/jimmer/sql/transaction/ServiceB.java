package org.babyfish.jimmer.sql.transaction;

import java.util.concurrent.CompletionException;
import org.babyfish.jimmer.sql.JSqlClient;

@TargetAnnotation(Component.class)
public class ServiceB {

    protected final JSqlClient sqlClient;

    public ServiceB(JSqlClient sqlClient) {
        this.sqlClient = sqlClient;
    }

    @Tx
    public void a() {}

    @Tx(Propagation.REQUIRES_NEW)
    public void b() {}

    @Tx
    public void c() throws CompletionException {}
}
