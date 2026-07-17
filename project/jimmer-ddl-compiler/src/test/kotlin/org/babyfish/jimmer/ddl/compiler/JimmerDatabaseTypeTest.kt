package org.babyfish.jimmer.ddl.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.babyfish.jimmer.ddl.generator.dialect.AutoDdlDialects

class JimmerDatabaseTypeTest {

    @Test
    fun `all supported database types have deterministic dialects`() {
        assertEquals(9, JimmerDatabaseType.entries.size)
        JimmerDatabaseType.entries.forEach { databaseType ->
            assertEquals(databaseType, AutoDdlDialects.require(databaseType).databaseType)
        }
    }

    @Test
    fun `jdbc urls resolve to canonical database families`() {
        val urls = mapOf(
            "jdbc:mysql://localhost/demo" to JimmerDatabaseType.MYSQL,
            "jdbc:mariadb://localhost/demo" to JimmerDatabaseType.MYSQL,
            "jdbc:oceanbase://localhost/demo" to JimmerDatabaseType.MYSQL,
            "jdbc:polardb://localhost/demo" to JimmerDatabaseType.MYSQL,
            "jdbc:tidb://localhost/demo" to JimmerDatabaseType.MYSQL,
            "jdbc:postgresql://localhost/demo" to JimmerDatabaseType.POSTGRESQL,
            "jdbc:oracle:thin:@localhost:1521:demo" to JimmerDatabaseType.ORACLE,
            "jdbc:sqlserver://localhost;databaseName=demo" to JimmerDatabaseType.SQLSERVER,
            "jdbc:h2:mem:demo" to JimmerDatabaseType.H2,
            "jdbc:sqlite:demo.db" to JimmerDatabaseType.SQLITE,
            "JDBC:TAOS-RS://localhost:6041/demo" to JimmerDatabaseType.TAOS,
            "jdbc:dm://localhost:5236/demo" to JimmerDatabaseType.DM,
            "jdbc:kingbase8://localhost:54321/demo" to JimmerDatabaseType.KINGBASE,
        )

        urls.forEach { (url, databaseType) ->
            assertEquals(databaseType, JimmerDatabaseType.fromUrl(url), url)
            assertNotNull(JimmerDatabaseType.driverClassName(url), url)
        }
    }
}
