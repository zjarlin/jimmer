package org.babyfish.jimmer.sql.transaction;

import java.lang.Override;
import org.babyfish.jimmer.sql.JSqlClient;

@Component
public class ServiceATx extends ServiceA {
    ServiceATx(JSqlClient sqlClient) {
        super(sqlClient);
    }

    @Override
    public int a() {
        return sqlClient.transaction(Propagation.MANDATORY, () ->  {
            return super.a();
        });
    }

    @Override
    void b() {
        sqlClient.transaction(Propagation.REQUIRES_NEW, () ->  {
            super.b();
            return null;
        });
    }
}
