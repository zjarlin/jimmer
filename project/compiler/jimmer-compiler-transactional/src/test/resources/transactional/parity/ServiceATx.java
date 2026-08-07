package demo;

import java.lang.Override;
import org.babyfish.jimmer.sql.transaction.Propagation;

@Component
public class ServiceATx extends ServiceA {
    ServiceATx() {
        super();
    }

    @Override
    public int a() {
        return sqlClient.transaction(Propagation.MANDATORY, () ->  {
            return super.a();
        });
    }

    @Override
    protected int b() {
        return sqlClient.transaction(Propagation.REQUIRES_NEW, () ->  {
            return super.b();
        });
    }
}
