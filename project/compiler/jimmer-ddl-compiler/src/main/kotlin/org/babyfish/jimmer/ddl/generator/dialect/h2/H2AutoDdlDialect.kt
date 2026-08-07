package org.babyfish.jimmer.ddl.generator.dialect.h2

import org.babyfish.jimmer.ddl.generator.dialect.AbstractSqlDialect
import org.babyfish.jimmer.ddl.generator.model.AutoDdlColumn
import org.babyfish.jimmer.ddl.generator.model.AutoDdlLogicalType
import org.babyfish.jimmer.ddl.compiler.JimmerDatabaseType

class H2AutoDdlDialect : AbstractSqlDialect(JimmerDatabaseType.H2) {

    override fun supportsInlinePrimaryKey(column: AutoDdlColumn): Boolean {
        return column.primaryKey
    }

    override fun renderAutoIncrementClause(column: AutoDdlColumn): String? {
        return if (column.autoIncrement) "AUTO_INCREMENT" else null
    }

    override fun columnType(column: AutoDdlColumn): String {
        return when (column.logicalType) {
            AutoDdlLogicalType.TEXT -> "CLOB"
            AutoDdlLogicalType.UUID -> "UUID"
            AutoDdlLogicalType.JSON -> "JSON"
            AutoDdlLogicalType.BINARY -> "BLOB"
            else -> super.columnType(column)
        }
    }
}
