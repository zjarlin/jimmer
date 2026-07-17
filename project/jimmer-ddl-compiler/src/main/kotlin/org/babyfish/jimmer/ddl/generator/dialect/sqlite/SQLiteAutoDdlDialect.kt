package org.babyfish.jimmer.ddl.generator.dialect.sqlite

import org.babyfish.jimmer.ddl.generator.dialect.AbstractSqlDialect
import org.babyfish.jimmer.ddl.generator.model.AutoDdlColumn
import org.babyfish.jimmer.ddl.generator.model.AutoDdlLogicalType
import org.babyfish.jimmer.ddl.generator.model.AutoDdlTable
import org.babyfish.jimmer.ddl.compiler.JimmerDatabaseType

class SQLiteAutoDdlDialect : AbstractSqlDialect(JimmerDatabaseType.SQLITE) {

    override fun renderCreateTable(table: AutoDdlTable): String {
        val body = buildList {
            addAll(table.columns.map { renderColumnDefinition(it) })
            table.foreignKeys.forEach { foreignKey ->
                val columns = foreignKey.columnNames.joinToString(", ") { quoteIdentifier(it) }
                val referencedColumns = foreignKey.referencedColumnNames.joinToString(", ") { quoteIdentifier(it) }
                add("FOREIGN KEY ($columns) REFERENCES ${quoteIdentifier(foreignKey.referencedTableName)} ($referencedColumns)")
            }
        }.joinToString(",\n")
        return buildString {
            append("CREATE TABLE ${quoteIdentifier(table.name)} (\n")
            append(body.prependIndent("  "))
            append("\n)")
        }
    }

    override fun supportsInlinePrimaryKey(column: AutoDdlColumn): Boolean {
        return column.primaryKey
    }

    override fun renderAutoIncrementClause(column: AutoDdlColumn): String? {
        return if (column.autoIncrement) "AUTOINCREMENT" else null
    }

    override fun renderAlterColumn(tableName: String, column: AutoDdlColumn): List<String> {
        return emptyList()
    }

    override fun renderAddForeignKey(tableName: String, foreignKey: org.babyfish.jimmer.ddl.generator.model.AutoDdlForeignKey): List<String> {
        return emptyList()
    }

    override fun renderComment(comment: org.babyfish.jimmer.ddl.generator.model.AutoDdlComment): List<String> {
        return emptyList()
    }

    override fun columnType(column: AutoDdlColumn): String {
        return when (column.logicalType) {
            AutoDdlLogicalType.BOOLEAN -> "INTEGER"
            AutoDdlLogicalType.INT8,
            AutoDdlLogicalType.INT16,
            AutoDdlLogicalType.INT32,
            AutoDdlLogicalType.INT64 -> "INTEGER"
            AutoDdlLogicalType.FLOAT32,
            AutoDdlLogicalType.FLOAT64,
            AutoDdlLogicalType.DECIMAL -> "REAL"
            AutoDdlLogicalType.BINARY -> "BLOB"
            else -> "TEXT"
        }
    }
}
