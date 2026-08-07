package org.babyfish.jimmer.ddl.compiler

enum class JimmerDatabaseType(
    val description: String,
) {
    MYSQL("MySQL数据库"),
    POSTGRESQL("PostgreSQL数据库"),
    ORACLE("Oracle数据库"),
    SQLSERVER("SQL Server数据库"),
    H2("H2数据库"),
    SQLITE("SQLite数据库"),
    TAOS("TDengine / TAOS数据库"),
    DM("达梦数据库"),
    KINGBASE("人大金仓数据库");

    val code: String
        get() = name.lowercase()

    companion object {
        fun fromCode(code: String): JimmerDatabaseType? {
            return entries.firstOrNull { databaseType ->
                databaseType.code.equals(code, ignoreCase = true)
            }
        }

        fun fromUrl(url: String): JimmerDatabaseType? {
            return when {
                url.startsWith("jdbc:mysql:") ||
                    url.startsWith("jdbc:mysqlc:") ||
                    url.startsWith("jdbc:mariadb:") ||
                    url.startsWith("jdbc:oceanbase:") ||
                    url.startsWith("jdbc:polardb:") ||
                    url.startsWith("jdbc:tidb:") -> MYSQL
                url.startsWith("jdbc:postgresql:") -> POSTGRESQL
                url.startsWith("jdbc:oracle:") || url.startsWith("jdbc:oracle:thin:") -> ORACLE
                url.startsWith("jdbc:sqlserver:") -> SQLSERVER
                url.startsWith("jdbc:h2:") -> H2
                url.startsWith("jdbc:sqlite:") -> SQLITE
                url.startsWith("jdbc:taos:", ignoreCase = true) || url.startsWith("jdbc:taos-rs:", ignoreCase = true) -> TAOS
                url.startsWith("jdbc:dm:") -> DM
                url.startsWith("jdbc:kingbase8:") || url.startsWith("jdbc:kingbase:") -> KINGBASE
                else -> null
            }
        }

        fun driverClassName(url: String): String? {
            return when {
                url.startsWith("jdbc:postgresql:") -> "org.postgresql.Driver"
                url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mysqlc:") -> "com.mysql.cj.jdbc.Driver"
                url.startsWith("jdbc:mariadb:") -> "org.mariadb.jdbc.Driver"
                url.startsWith("jdbc:sqlserver:") -> "com.microsoft.sqlserver.jdbc.SQLServerDriver"
                url.startsWith("jdbc:oracle:") || url.startsWith("jdbc:oracle:thin:") -> "oracle.jdbc.OracleDriver"
                url.startsWith("jdbc:h2:") -> "org.h2.Driver"
                url.startsWith("jdbc:sqlite:") -> "org.sqlite.JDBC"
                url.startsWith("jdbc:taos:", ignoreCase = true) || url.startsWith("jdbc:taos-rs:", ignoreCase = true) -> "com.taosdata.jdbc.TSDBDriver"
                url.startsWith("jdbc:dm:") -> "dm.jdbc.driver.DmDriver"
                url.startsWith("jdbc:kingbase8:") || url.startsWith("jdbc:kingbase:") -> "com.kingbase8.Driver"
                url.startsWith("jdbc:oceanbase:") -> "com.oceanbase.jdbc.Driver"
                url.startsWith("jdbc:polardb:") || url.startsWith("jdbc:tidb:") -> "com.mysql.cj.jdbc.Driver"
                else -> null
            }
        }
    }
}
