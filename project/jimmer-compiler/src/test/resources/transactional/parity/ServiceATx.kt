@file:Suppress("warnings")

package demo

import kotlin.Int
import kotlin.Suppress
import org.babyfish.jimmer.sql.transaction.Propagation

@Component
public class ServiceATx() : ServiceA() {
    override fun a(): Int = this.sqlClient.transaction(Propagation.MANDATORY) {
        super.a()
    }

    protected override fun b(): Int = this.sqlClient.transaction(Propagation.REQUIRES_NEW) {
        super.b()
    }
}
