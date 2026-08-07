package org.babyfish.jimmer.ddl.generator.dialect.taos

import org.babyfish.jimmer.ddl.generator.dialect.AbstractSqlDialect
import org.babyfish.jimmer.ddl.generator.dialect.AutoDdlIdentifierQuote
import org.babyfish.jimmer.ddl.generator.model.AutoDdlColumn
import org.babyfish.jimmer.ddl.generator.model.AutoDdlComment
import org.babyfish.jimmer.ddl.generator.model.AutoDdlCommentTargetType
import org.babyfish.jimmer.ddl.generator.model.AutoDdlLogicalType
import org.babyfish.jimmer.ddl.generator.model.AutoDdlTable
import org.babyfish.jimmer.ddl.compiler.JimmerDatabaseType

class TaosAutoDdlDialect : AbstractSqlDialect(
    databaseType = JimmerDatabaseType.TAOS,
    quote = AutoDdlIdentifierQuote("", ""),
) {

    override fun renderCreateTable(table: AutoDdlTable): String {
        require(table.columns.isNotEmpty()) { "TAOS table '${table.name}' must have at least one column" }
        require(table.columns.first().logicalType == AutoDdlLogicalType.TIMESTAMP || table.columns.first().logicalType == AutoDdlLogicalType.DATETIME) {
            "TAOS table '${table.name}' requires the first column to be a timestamp-compatible column"
        }
        return super.renderCreateTable(table)
    }

    override fun renderCreateIndex(tableName: String, index: org.babyfish.jimmer.ddl.generator.model.AutoDdlIndex): String {
        return ""
    }

    override fun renderAddForeignKey(tableName: String, foreignKey: org.babyfish.jimmer.ddl.generator.model.AutoDdlForeignKey): List<String> {
        return emptyList()
    }

    override fun renderDropForeignKey(tableName: String, foreignKeyName: String): List<String> {
        return emptyList()
    }

    override fun renderComment(comment: AutoDdlComment): List<String> {
        return when (comment.targetType) {
            AutoDdlCommentTargetType.TABLE -> listOf("ALTER TABLE ${comment.tableName} COMMENT '${comment.value.replace("'", "''")}'")
            AutoDdlCommentTargetType.COLUMN,
            AutoDdlCommentTargetType.SEQUENCE -> emptyList()
        }
    }

    override fun columnType(column: AutoDdlColumn): String {
        return when (column.logicalType) {
            AutoDdlLogicalType.TIMESTAMP,
            AutoDdlLogicalType.DATETIME -> "TIMESTAMP"
            AutoDdlLogicalType.STRING -> "VARCHAR(${column.length ?: 255})"
            AutoDdlLogicalType.TEXT -> "NCHAR(${column.length ?: 255})"
            AutoDdlLogicalType.CHAR -> "BINARY(${column.length ?: 1})"
            AutoDdlLogicalType.BOOLEAN -> "BOOL"
            AutoDdlLogicalType.INT8 -> "TINYINT"
            AutoDdlLogicalType.INT16 -> "SMALLINT"
            AutoDdlLogicalType.INT32 -> "INT"
            AutoDdlLogicalType.INT64 -> "BIGINT"
            AutoDdlLogicalType.DECIMAL -> "DECIMAL(${column.precision ?: 19}, ${column.scale ?: 2})"
            AutoDdlLogicalType.FLOAT32 -> "FLOAT"
            AutoDdlLogicalType.FLOAT64 -> "DOUBLE"
            AutoDdlLogicalType.BINARY -> "VARBINARY(${column.length ?: 255})"
            AutoDdlLogicalType.JSON -> "JSON"
            else -> column.nativeTypeHint ?: "VARCHAR(255)"
        }
    }
}
