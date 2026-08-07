@file:Suppress("warnings")

package org.babyfish.jimmer.sql.kt.transaction

import kotlin.Suppress
import kotlin.Unit
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.transaction.Propagation

@Component
public class ServiceATx(
    sqlClient: KSqlClient,
) : ServiceA(sqlClient) {
    override fun a(): Unit = this.sqlClient.transaction(Propagation.MANDATORY) {
        super.a()
    }

    internal override fun b(): Unit = this.sqlClient.transaction(Propagation.REQUIRES_NEW) {
        super.b()
    }
}
