package org.babyfish.jimmer.ddl.generator.dialect

import org.babyfish.jimmer.ddl.generator.diff.AddColumn
import org.babyfish.jimmer.ddl.generator.diff.AddComment
import org.babyfish.jimmer.ddl.generator.diff.AddForeignKey
import org.babyfish.jimmer.ddl.generator.diff.AlterColumn
import org.babyfish.jimmer.ddl.generator.diff.AutoDdlOperation
import org.babyfish.jimmer.ddl.generator.diff.CreateIndex
import org.babyfish.jimmer.ddl.generator.diff.CreateSequence
import org.babyfish.jimmer.ddl.generator.diff.CreateTable
import org.babyfish.jimmer.ddl.generator.diff.DropColumn
import org.babyfish.jimmer.ddl.generator.diff.DropColumnNotNull
import org.babyfish.jimmer.ddl.generator.diff.DropForeignKey
import org.babyfish.jimmer.ddl.generator.diff.DropIndex
import org.babyfish.jimmer.ddl.generator.diff.DropTable
import org.babyfish.jimmer.ddl.generator.diff.RenameTable
import org.babyfish.jimmer.ddl.generator.diff.SetColumnNotNull
import org.babyfish.jimmer.ddl.generator.model.AutoDdlColumn
import org.babyfish.jimmer.ddl.generator.model.AutoDdlComment
import org.babyfish.jimmer.ddl.generator.model.AutoDdlCommentTargetType
import org.babyfish.jimmer.ddl.generator.model.AutoDdlIndexType
import org.babyfish.jimmer.ddl.generator.model.AutoDdlLogicalType
import org.babyfish.jimmer.ddl.generator.model.AutoDdlTable
import org.babyfish.jimmer.ddl.compiler.JimmerDatabaseType
import org.babyfish.jimmer.ddl.generator.dialect.dm.DmAutoDdlDialect
import org.babyfish.jimmer.ddl.generator.dialect.h2.H2AutoDdlDialect
import org.babyfish.jimmer.ddl.generator.dialect.kingbase.KingbaseAutoDdlDialect
import org.babyfish.jimmer.ddl.generator.dialect.mysql.MySqlAutoDdlDialect
import org.babyfish.jimmer.ddl.generator.dialect.oracle.OracleAutoDdlDialect
import org.babyfish.jimmer.ddl.generator.dialect.postgresql.PostgreSqlAutoDdlDialect
import org.babyfish.jimmer.ddl.generator.dialect.sqlite.SQLiteAutoDdlDialect
import org.babyfish.jimmer.ddl.generator.dialect.sqlserver.SqlServerAutoDdlDialect
import org.babyfish.jimmer.ddl.generator.dialect.taos.TaosAutoDdlDialect

data class AutoDdlIdentifierQuote(
    val prefix: String = "\"",
    val suffix: String = prefix,
)

data class AutoDdlRenderContext(
    val appendSemicolon: Boolean = true,
)

interface AutoDdlDialect {
    val databaseType: JimmerDatabaseType

    fun supports(type: JimmerDatabaseType): Boolean {
        return databaseType == type
    }

    fun render(
        operation: AutoDdlOperation,
        context: AutoDdlRenderContext = AutoDdlRenderContext(),
    ): List<String>

    fun render(
        operations: List<AutoDdlOperation>,
        context: AutoDdlRenderContext = AutoDdlRenderContext(),
    ): List<String> {
        return operations.flatMap { render(it, context) }.filter { it.isNotBlank() }
    }
}

object AutoDdlDialects {
    private val dialectByDatabaseType: Map<JimmerDatabaseType, AutoDdlDialect> = buildMap {
        put(JimmerDatabaseType.MYSQL, MySqlAutoDdlDialect())
        put(JimmerDatabaseType.POSTGRESQL, PostgreSqlAutoDdlDialect())
        put(JimmerDatabaseType.H2, H2AutoDdlDialect())
        put(JimmerDatabaseType.SQLITE, SQLiteAutoDdlDialect())
        put(JimmerDatabaseType.SQLSERVER, SqlServerAutoDdlDialect())
        put(JimmerDatabaseType.ORACLE, OracleAutoDdlDialect())
        put(JimmerDatabaseType.DM, DmAutoDdlDialect())
        put(JimmerDatabaseType.KINGBASE, KingbaseAutoDdlDialect())
        put(JimmerDatabaseType.TAOS, TaosAutoDdlDialect())
    }

    fun resolve(databaseType: JimmerDatabaseType): AutoDdlDialect? {
        return dialectByDatabaseType[databaseType]
    }

    fun require(databaseType: JimmerDatabaseType): AutoDdlDialect {
        return resolve(databaseType)
            ?: error("No AutoDDL dialect registered for $databaseType")
    }
}

abstract class AbstractSqlDialect(
    final override val databaseType: JimmerDatabaseType,
    private val quote: AutoDdlIdentifierQuote = AutoDdlIdentifierQuote(),
) : AutoDdlDialect {

    override fun render(
        operation: AutoDdlOperation,
        context: AutoDdlRenderContext,
    ): List<String> {
        return when (operation) {
            is CreateSequence -> listOf(withTerminator(renderCreateSequence(operation), context))
            is CreateTable -> listOf(withTerminator(renderCreateTable(operation.table), context))
            is DropTable -> listOf(withTerminator(renderDropTable(operation.tableName), context))
            is RenameTable -> renderRenameTable(operation.oldTableName, operation.newTableName).map { withTerminator(it, context) }
            is AddColumn -> renderAddColumn(operation.tableName, operation.column).map { withTerminator(it, context) }
            is AlterColumn -> renderAlterColumn(operation.tableName, operation.column, operation.previousColumn).map { withTerminator(it, context) }
            is DropColumnNotNull -> renderDropColumnNotNull(operation.tableName, operation.columnName).map { withTerminator(it, context) }
            is SetColumnNotNull -> renderSetColumnNotNull(operation.tableName, operation.column).map { withTerminator(it, context) }
            is DropColumn -> listOf(withTerminator(renderDropColumn(operation.tableName, operation.columnName), context))
            is CreateIndex -> listOf(withTerminator(renderCreateIndex(operation.tableName, operation.index), context))
            is DropIndex -> listOf(withTerminator(renderDropIndex(operation.tableName, operation.indexName), context))
            is AddForeignKey -> renderAddForeignKey(operation.tableName, operation.foreignKey).map { withTerminator(it, context) }
            is DropForeignKey -> renderDropForeignKey(operation.tableName, operation.foreignKeyName).map { withTerminator(it, context) }
            is AddComment -> renderComment(operation.comment).map { withTerminator(it, context) }
        }
    }

    protected fun quoteIdentifier(value: String): String {
        if (quote.prefix.isEmpty() && quote.suffix.isEmpty()) {
            return value
        }
        return "${quote.prefix}$value${quote.suffix}"
    }

    protected open fun renderCreateSequence(operation: CreateSequence): String {
        return "CREATE SEQUENCE ${quoteIdentifier(operation.sequence.name)} START WITH ${operation.sequence.startWith} INCREMENT BY ${operation.sequence.incrementBy}"
    }

    protected open fun renderCreateTable(table: AutoDdlTable): String {
        val primaryKeyColumns = table.columns.filter { it.primaryKey }
        val body = buildList {
            addAll(table.columns.map { renderColumnDefinition(it) })
            if (
                primaryKeyColumns.size > 1 ||
                (primaryKeyColumns.size == 1 && !supportsInlinePrimaryKey(primaryKeyColumns.single()))
            ) {
                add("PRIMARY KEY (${table.primaryKeyColumnNames.joinToString(", ") { quoteIdentifier(it) }})")
            }
        }.joinToString(",\n")
        return buildString {
            append("CREATE TABLE ${quoteIdentifier(table.name)} (\n")
            append(body.prependIndent("  "))
            append("\n)")
        }
    }

    protected open fun renderDropTable(tableName: String): String {
        return "DROP TABLE IF EXISTS ${quoteIdentifier(tableName)}"
    }

    protected open fun renderRenameTable(
        oldTableName: String,
        newTableName: String,
    ): List<String> {
        return listOf("ALTER TABLE ${quoteIdentifier(oldTableName)} RENAME TO ${quoteIdentifier(newTableName)}")
    }

    protected open fun renderAddColumn(tableName: String, column: AutoDdlColumn): List<String> {
        return listOf("ALTER TABLE ${quoteIdentifier(tableName)} ADD COLUMN ${renderColumnDefinition(column)}")
    }

    protected open fun renderAlterColumn(tableName: String, column: AutoDdlColumn): List<String> {
        return listOf("ALTER TABLE ${quoteIdentifier(tableName)} ALTER COLUMN ${renderColumnDefinition(column)}")
    }

    protected open fun renderAlterColumn(
        tableName: String,
        column: AutoDdlColumn,
        previousColumn: AutoDdlColumn?,
    ): List<String> {
        return renderAlterColumn(tableName, column)
    }

    protected open fun renderDropColumnNotNull(tableName: String, columnName: String): List<String> {
        return emptyList()
    }

    protected open fun renderSetColumnNotNull(tableName: String, column: AutoDdlColumn): List<String> {
        return emptyList()
    }

    protected open fun renderDropColumn(tableName: String, columnName: String): String {
        return "ALTER TABLE ${quoteIdentifier(tableName)} DROP COLUMN IF EXISTS ${quoteIdentifier(columnName)}"
    }

    protected open fun renderCreateIndex(tableName: String, index: org.babyfish.jimmer.ddl.generator.model.AutoDdlIndex): String {
        val indexKeyword = when (index.type) {
            AutoDdlIndexType.UNIQUE -> "UNIQUE INDEX"
            AutoDdlIndexType.FULLTEXT -> "FULLTEXT INDEX"
            AutoDdlIndexType.NORMAL -> "INDEX"
        }
        val columns = index.columnNames.joinToString(", ") { quoteIdentifier(it) }
        return "CREATE $indexKeyword ${quoteIdentifier(index.name)} ON ${quoteIdentifier(tableName)} ($columns)"
    }

    protected open fun renderDropIndex(tableName: String, indexName: String): String {
        return "DROP INDEX ${quoteIdentifier(indexName)}"
    }

    protected open fun renderAddForeignKey(
        tableName: String,
        foreignKey: org.babyfish.jimmer.ddl.generator.model.AutoDdlForeignKey,
    ): List<String> {
        val columns = foreignKey.columnNames.joinToString(", ") { quoteIdentifier(it) }
        val referencedColumns = foreignKey.referencedColumnNames.joinToString(", ") { quoteIdentifier(it) }
        val builder = StringBuilder()
        builder.append("ALTER TABLE ${quoteIdentifier(tableName)} ADD CONSTRAINT ${quoteIdentifier(foreignKey.name)} ")
        builder.append("FOREIGN KEY ($columns) REFERENCES ${quoteIdentifier(foreignKey.referencedTableName)} ($referencedColumns)")
        foreignKey.onDelete?.takeIf { it.isNotBlank() }?.let { builder.append(" ON DELETE $it") }
        foreignKey.onUpdate?.takeIf { it.isNotBlank() }?.let { builder.append(" ON UPDATE $it") }
        return listOf(builder.toString())
    }

    protected open fun renderDropForeignKey(tableName: String, foreignKeyName: String): List<String> {
        return listOf("ALTER TABLE ${quoteIdentifier(tableName)} DROP CONSTRAINT ${quoteIdentifier(foreignKeyName)}")
    }

    protected open fun renderComment(comment: AutoDdlComment): List<String> {
        return when (comment.targetType) {
            AutoDdlCommentTargetType.TABLE -> renderTableComment(comment.tableName.orEmpty(), comment.value)
            AutoDdlCommentTargetType.COLUMN -> renderColumnComment(comment.tableName.orEmpty(), comment.columnName.orEmpty(), comment.value)
            AutoDdlCommentTargetType.SEQUENCE -> renderSequenceComment(comment.sequenceName.orEmpty(), comment.value)
        }
    }

    protected open fun renderTableComment(tableName: String, comment: String): List<String> {
        return listOf("COMMENT ON TABLE ${quoteIdentifier(tableName)} IS '${comment.escapeSqlLiteral()}'")
    }

    protected open fun renderColumnComment(tableName: String, columnName: String, comment: String): List<String> {
        return listOf("COMMENT ON COLUMN ${quoteIdentifier(tableName)}.${quoteIdentifier(columnName)} IS '${comment.escapeSqlLiteral()}'")
    }

    protected open fun renderSequenceComment(sequenceName: String, comment: String): List<String> {
        return emptyList()
    }

    protected open fun renderColumnDefinition(column: AutoDdlColumn): String {
        val parts = mutableListOf<String>()
        parts += quoteIdentifier(column.name)
        parts += columnType(column)
        if (column.autoIncrement) {
            renderAutoIncrementClause(column)?.let { parts += it }
        }
        if (!column.nullable) {
            parts += "NOT NULL"
        }
        column.defaultValue?.takeIf { it.isNotBlank() }?.let { parts += "DEFAULT $it" }
        if (column.primaryKey && supportsInlinePrimaryKey(column)) {
            parts += "PRIMARY KEY"
        }
        return parts.joinToString(" ")
    }

    protected open fun supportsInlinePrimaryKey(column: AutoDdlColumn): Boolean {
        return false
    }

    protected open fun renderAutoIncrementClause(column: AutoDdlColumn): String? {
        return null
    }

    protected open fun columnType(column: AutoDdlColumn): String {
        return when (column.logicalType) {
            AutoDdlLogicalType.STRING -> "VARCHAR(${column.length ?: 255})"
            AutoDdlLogicalType.TEXT -> "TEXT"
            AutoDdlLogicalType.CHAR -> "CHAR(${column.length ?: 1})"
            AutoDdlLogicalType.BOOLEAN -> "BOOLEAN"
            AutoDdlLogicalType.INT8 -> "TINYINT"
            AutoDdlLogicalType.INT16 -> "SMALLINT"
            AutoDdlLogicalType.INT32 -> "INT"
            AutoDdlLogicalType.INT64 -> "BIGINT"
            AutoDdlLogicalType.DECIMAL -> {
                val precision = column.precision ?: 19
                val scale = column.scale ?: 2
                "DECIMAL($precision, $scale)"
            }
            AutoDdlLogicalType.BIG_INTEGER -> "DECIMAL(65, 0)"
            AutoDdlLogicalType.FLOAT32 -> "REAL"
            AutoDdlLogicalType.FLOAT64 -> "DOUBLE"
            AutoDdlLogicalType.DATE -> "DATE"
            AutoDdlLogicalType.TIME -> "TIME"
            AutoDdlLogicalType.DATETIME -> "TIMESTAMP"
            AutoDdlLogicalType.DATETIME_TZ -> "TIMESTAMP WITH TIME ZONE"
            AutoDdlLogicalType.TIMESTAMP -> "TIMESTAMP"
            AutoDdlLogicalType.DURATION -> "BIGINT"
            AutoDdlLogicalType.BINARY -> "BLOB"
            AutoDdlLogicalType.UUID -> "VARCHAR(36)"
            AutoDdlLogicalType.JSON -> "JSON"
            AutoDdlLogicalType.UNKNOWN -> column.nativeTypeHint ?: "VARCHAR(255)"
        }
    }

    private fun withTerminator(statement: String, context: AutoDdlRenderContext): String {
        if (!context.appendSemicolon || statement.endsWith(";")) {
            return statement
        }
        return "$statement;"
    }

    private fun String.escapeSqlLiteral(): String {
        return replace("'", "''")
    }
}
