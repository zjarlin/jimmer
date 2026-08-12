package org.babyfish.jimmer.ddl.compiler

import org.babyfish.jimmer.ddl.generator.model.AutoDdlColumn
import org.babyfish.jimmer.ddl.generator.model.AutoDdlLogicalType
import org.babyfish.jimmer.ddl.generator.model.AutoDdlSchema
import org.babyfish.jimmer.ddl.generator.model.AutoDdlTable
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.Types

object JimmerDdlDatabaseSchemaReader {
    fun read(
        settings: JimmerDdlCompilerSettings,
        desiredSchema: AutoDdlSchema,
        renamedTables: Map<String, String> = emptyMap(),
    ): AutoDdlSchema {
        settings.openJdbcConnection().use { connection ->
            val schemaName = settings.jdbc.schema ?: runCatching { connection.schema }.getOrNull()
            return readSchema(connection, schemaName, desiredSchema, renamedTables)
        }
    }

    private fun readSchema(
        connection: Connection,
        schemaName: String?,
        desiredSchema: AutoDdlSchema,
        renamedTables: Map<String, String>,
    ): AutoDdlSchema {
        val actualTables = desiredSchema.tables.mapNotNull { desiredTable ->
            readTable(connection, schemaName, desiredTable, renamedTables)
        }
        return AutoDdlSchema(actualTables, emptyList())
    }

    private fun readTable(
        connection: Connection,
        schemaName: String?,
        desiredTable: AutoDdlTable,
        renamedTables: Map<String, String>,
    ): AutoDdlTable? {
        val metaData = connection.metaData
        val tableName = findExistingTableName(metaData, connection.catalog, schemaName, desiredTable.name)
            ?: renamedTables[desiredTable.name.lowercase()]?.let { oldTableName ->
                findExistingTableName(metaData, connection.catalog, schemaName, oldTableName)
            }
            ?: return null
        val primaryKeys = readPrimaryKeys(metaData, connection.catalog, schemaName, tableName)
        val desiredColumns = desiredTable.columns.associateBy { column -> column.name.lowercase() }
        val columns = mutableListOf<AutoDdlColumn>()
        metaData.findColumns(connection.catalog, schemaName, tableName).use { resultSet ->
            while (resultSet.next()) {
                val columnName = resultSet.getString("COLUMN_NAME") ?: continue
                val jdbcType = resultSet.getInt("DATA_TYPE")
                val nativeTypeName = resultSet.getStringOrNull("TYPE_NAME")
                val logicalType = jdbcType.toAutoDdlLogicalType(nativeTypeName)
                val columnSize = resultSet.getIntOrNull("COLUMN_SIZE")
                val decimalDigits = resultSet.getIntOrNull("DECIMAL_DIGITS")
                val nullable = resultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable
                val desiredColumn = desiredColumns[columnName.lowercase()]
                val actualColumn = desiredColumn ?: AutoDdlColumn(
                    name = columnName,
                    logicalType = logicalType,
                )
                columns += actualColumn.copy(
                    name = columnName,
                    logicalType = logicalType,
                    nullable = nullable,
                    length = logicalType.columnLength(columnSize),
                    precision = logicalType.columnPrecision(columnSize),
                    scale = logicalType.columnScale(decimalDigits),
                    defaultValue = if (actualColumn.autoIncrement) {
                        actualColumn.defaultValue
                    } else {
                        resultSet.getStringOrNull("COLUMN_DEF")
                    },
                    primaryKey = primaryKeys.any { key -> key.equals(columnName, ignoreCase = true) },
                    nativeTypeHint = actualColumn.nativeTypeHint?.let { nativeTypeName },
                )
            }
        }
        return AutoDdlTable(
            desiredTable.name,
            columns,
            desiredTable.foreignKeys,
            desiredTable.indexes,
            desiredTable.comment,
            desiredTable.junction,
        )
    }

    private fun findExistingTableName(
        metaData: DatabaseMetaData,
        catalog: String?,
        schemaName: String?,
        tableName: String,
    ): String? {
        val candidates = listOf(tableName, tableName.lowercase(), tableName.uppercase()).distinct()
        candidates.forEach { candidate ->
            metaData.getTables(catalog, schemaName, candidate, arrayOf("TABLE")).use { resultSet ->
                if (resultSet.next()) {
                    return resultSet.getString("TABLE_NAME") ?: candidate
                }
            }
        }
        return null
    }

    private fun DatabaseMetaData.findColumns(
        catalog: String?,
        schemaName: String?,
        tableName: String,
    ): ResultSet {
        return getColumns(catalog, schemaName, tableName, "%")
    }

    private fun readPrimaryKeys(
        metaData: DatabaseMetaData,
        catalog: String?,
        schemaName: String?,
        tableName: String,
    ): Set<String> {
        val primaryKeys = linkedSetOf<String>()
        metaData.getPrimaryKeys(catalog, schemaName, tableName).use { resultSet ->
            while (resultSet.next()) {
                resultSet.getString("COLUMN_NAME")?.let { columnName -> primaryKeys += columnName }
            }
        }
        return primaryKeys
    }

    private fun ResultSet.getIntOrNull(columnName: String): Int? {
        val value = getInt(columnName)
        return if (wasNull()) null else value
    }

    private fun ResultSet.getStringOrNull(columnName: String): String? {
        return runCatching { getString(columnName)?.takeIf { it.isNotBlank() } }.getOrNull()
    }

    private fun AutoDdlLogicalType.columnLength(columnSize: Int?): Int? {
        return columnSize.takeIf { this == AutoDdlLogicalType.STRING || this == AutoDdlLogicalType.CHAR }
    }

    private fun AutoDdlLogicalType.columnPrecision(columnSize: Int?): Int? {
        return columnSize.takeIf { this == AutoDdlLogicalType.DECIMAL || this == AutoDdlLogicalType.BIG_INTEGER }
    }

    private fun AutoDdlLogicalType.columnScale(decimalDigits: Int?): Int? {
        return decimalDigits.takeIf { this == AutoDdlLogicalType.DECIMAL || this == AutoDdlLogicalType.BIG_INTEGER }
    }
}

internal fun Int.toAutoDdlLogicalType(nativeTypeName: String?): AutoDdlLogicalType {
    return when (nativeTypeName?.trim()?.lowercase()) {
        "text" -> AutoDdlLogicalType.TEXT
        "json", "jsonb" -> AutoDdlLogicalType.JSON
        "uuid" -> AutoDdlLogicalType.UUID
        "bytea" -> AutoDdlLogicalType.BINARY
        "interval" -> AutoDdlLogicalType.DURATION
        "timestamptz", "timestamp with time zone" -> AutoDdlLogicalType.DATETIME_TZ
        "timestamp", "timestamp without time zone" -> AutoDdlLogicalType.DATETIME
        else -> when (this) {
            Types.CHAR, Types.NCHAR -> AutoDdlLogicalType.CHAR
            Types.VARCHAR, Types.NVARCHAR -> AutoDdlLogicalType.STRING
            Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> AutoDdlLogicalType.TEXT
            Types.BOOLEAN, Types.BIT -> AutoDdlLogicalType.BOOLEAN
            Types.TINYINT -> AutoDdlLogicalType.INT8
            Types.SMALLINT -> AutoDdlLogicalType.INT16
            Types.INTEGER -> AutoDdlLogicalType.INT32
            Types.BIGINT -> AutoDdlLogicalType.INT64
            Types.NUMERIC, Types.DECIMAL -> AutoDdlLogicalType.DECIMAL
            Types.REAL -> AutoDdlLogicalType.FLOAT32
            Types.FLOAT, Types.DOUBLE -> AutoDdlLogicalType.FLOAT64
            Types.DATE -> AutoDdlLogicalType.DATE
            Types.TIME, Types.TIME_WITH_TIMEZONE -> AutoDdlLogicalType.TIME
            Types.TIMESTAMP -> AutoDdlLogicalType.DATETIME
            Types.TIMESTAMP_WITH_TIMEZONE -> AutoDdlLogicalType.DATETIME_TZ
            Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> AutoDdlLogicalType.BINARY
            Types.OTHER -> AutoDdlLogicalType.UNKNOWN
            else -> AutoDdlLogicalType.UNKNOWN
        }
    }
}
