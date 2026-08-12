package org.babyfish.jimmer.ddl.compiler

import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

data class JimmerDdlFlywayHistory(
    val available: Boolean,
    val appliedScripts: Set<String> = emptySet(),
)

object JimmerDdlFlywayHistoryReader {
    fun read(settings: JimmerDdlCompilerSettings): JimmerDdlFlywayHistory {
        if (!settings.compareDatabase || !settings.jdbc.configured) {
            return JimmerDdlFlywayHistory(available = false)
        }
        return runCatching {
            settings.openJdbcConnection().use { connection ->
                val schemaName = settings.jdbc.schema ?: runCatching { connection.schema }.getOrNull()
                val historyTableName = settings.flywayHistoryTable.trim()
                    .ifBlank { DEFAULT_FLYWAY_HISTORY_TABLE }
                val historyTable = connection.findTable(schemaName, historyTableName)
                    ?: return@use JimmerDdlFlywayHistory(available = true)
                val scripts = linkedSetOf<String>()
                connection.createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT ${connection.quoteIdentifier("script")} " +
                            "FROM ${historyTable.toQualifiedSql(connection)} " +
                            "WHERE ${connection.quoteIdentifier("success")} = TRUE"
                    ).use { resultSet ->
                        while (resultSet.next()) {
                            resultSet.getString(1)?.takeIf(String::isNotBlank)?.let(scripts::add)
                        }
                    }
                }
                JimmerDdlFlywayHistory(available = true, appliedScripts = scripts)
            }
        }.getOrElse {
            JimmerDdlFlywayHistory(available = false)
        }
    }
}

internal fun JimmerDdlCompilerSettings.openJdbcConnection(): Connection {
    val driverClassName = jdbc.driverClassName ?: JimmerDatabaseType.driverClassName(jdbc.url)
    if (!driverClassName.isNullOrBlank()) {
        Class.forName(driverClassName)
    }
    val properties = Properties().apply {
        if (jdbc.username.isNotBlank()) {
            setProperty("user", jdbc.username)
        }
        if (jdbc.password.isNotBlank()) {
            setProperty("password", jdbc.password)
        }
        setProperty("connectTimeout", "5")
        setProperty("loginTimeout", "5")
    }
    return DriverManager.getConnection(jdbc.url, properties)
}

private data class JdbcTableName(
    val schema: String?,
    val table: String,
)

private fun Connection.findTable(schemaName: String?, tableName: String): JdbcTableName? {
    val schemas = listOfNotNull(schemaName, runCatching { schema }.getOrNull()).distinct() + null
    val tableCandidates = listOf(tableName, tableName.lowercase(), tableName.uppercase()).distinct()
    for (schemaCandidate in schemas) {
        for (tableCandidate in tableCandidates) {
            metaData.getTables(catalog, schemaCandidate, tableCandidate, arrayOf("TABLE")).use { resultSet ->
                if (resultSet.next()) {
                    return JdbcTableName(
                        schema = resultSet.getString("TABLE_SCHEM"),
                        table = resultSet.getString("TABLE_NAME") ?: tableCandidate,
                    )
                }
            }
        }
    }
    return null
}

private fun JdbcTableName.toQualifiedSql(connection: Connection): String {
    val tableSql = connection.quoteIdentifier(table)
    return schema?.let { schemaName -> "${connection.quoteIdentifier(schemaName)}.$tableSql" } ?: tableSql
}

private fun Connection.quoteIdentifier(identifier: String): String {
    val quote = metaData.identifierQuoteString?.trim().orEmpty()
    if (quote.isBlank()) {
        return identifier
    }
    return quote + identifier.replace(quote, quote + quote) + quote
}
