package org.babyfish.jimmer.ddl.compiler

import org.babyfish.jimmer.ddl.generator.dialect.AutoDdlDialects
import org.babyfish.jimmer.ddl.generator.diff.AddComment
import org.babyfish.jimmer.ddl.generator.diff.AddForeignKey
import org.babyfish.jimmer.ddl.generator.diff.AlterColumn
import org.babyfish.jimmer.ddl.generator.diff.AutoDdlOperation
import org.babyfish.jimmer.ddl.generator.diff.CreateIndex
import org.babyfish.jimmer.ddl.generator.diff.CreateSequence
import org.babyfish.jimmer.ddl.generator.diff.CreateTable
import org.babyfish.jimmer.ddl.generator.diff.SchemaDiffPlanner
import org.babyfish.jimmer.ddl.generator.model.AutoDdlComment
import org.babyfish.jimmer.ddl.generator.model.AutoDdlCommentTargetType
import org.babyfish.jimmer.ddl.generator.model.AutoDdlSchema
import org.babyfish.jimmer.ddl.generator.model.AutoDdlTable
import org.babyfish.jimmer.ddl.generator.options.AutoDdlDiffOptions
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiWorkspace

object JimmerDdlCompiler {
    fun compile(
        workspace: LsiWorkspace,
        entityTypeIds: Collection<LsiSymbolId>,
        settings: JimmerDdlCompilerSettings,
        relationTargetWorkspace: LsiWorkspace = workspace,
    ): JimmerDdlCompilerResult {
        if (!settings.enabled) {
            return JimmerDdlCompilerResult.empty(settings)
        }

        val entityById = workspace.jimmerEntityTypes().associateBy(LsiTypeDeclaration::id)
        val requestedEntityIds = entityTypeIds.toSortedSet()
        val missingEntityIds = requestedEntityIds - entityById.keys
        require(missingEntityIds.isEmpty()) {
            "Jimmer DDL root types must be @Entity declarations in the workspace: " +
                missingEntityIds.joinToString { id -> id.value }
        }
        val entities = requestedEntityIds.map { id -> entityById.getValue(id) }
            .filter { settings.includesClass(it.qualifiedName) }
        if (entities.isEmpty()) {
            return JimmerDdlCompilerResult.empty(settings)
        }
        val schema = buildJimmerDdlSchema(
            workspace = workspace,
            entities = entities,
            relationTargetWorkspace = relationTargetWorkspace,
            settings = settings,
        )
        val changePlan = JimmerDdlEntityTableSnapshot.planSchemaChanges(
            entities = entities,
            schema = schema,
            settings = settings,
        )
        if (!settings.compareDatabase && !changePlan.hasChanges) {
            return JimmerDdlCompilerResult.empty(
                settings = settings,
                entities = entities,
                schema = schema,
            )
        }

        val plan = generateDdl(
            schema = schema,
            changePlan = changePlan,
            settings = settings,
        )
        val sql = plan.statements.joinToString(separator = "\n")
            .trim()
            .let { content ->
                if (content.isBlank()) {
                    content
                } else {
                    content + "\n"
                }
            }
        return JimmerDdlCompilerResult(
            settings = settings,
            entities = entities,
            schema = schema,
            snapshotSchema = plan.snapshotSchema,
            statements = plan.statements,
            sql = sql,
            warnings = plan.warnings,
        )
    }

    private fun generateDdl(
        schema: AutoDdlSchema,
        changePlan: JimmerDdlSchemaChangePlan,
        settings: JimmerDdlCompilerSettings,
    ): JimmerDdlCompilePlan {
        val renameOperations = changePlan.renameOperations
        val operationPlan = if (settings.compareDatabase && settings.jdbc.url.isNotBlank()) {
            generateComparedOperations(
                schema = schema,
                settings = settings,
                renamedTables = renameOperations.associate { operation ->
                    operation.newTableName.lowercase() to operation.oldTableName
                },
            )
        } else {
            buildOfflineIncrementalOperationPlan(
                schema = schema,
                changePlan = changePlan,
                settings = settings,
            )
        }
        val renamedTableNames = renameOperations.map { operation -> operation.newTableName.lowercase() }.toSet()
        val operations = operationPlan.operations.filterNot { operation ->
            operation is CreateTable && operation.table.name.lowercase() in renamedTableNames
        }
        val statements = buildRenameTableStatements(renameOperations, settings) +
            AutoDdlDialects.require(settings.databaseType).render(operations) +
            buildPostgreSqlNullabilityRepairStatements(schema, settings)
        return JimmerDdlCompilePlan(
            statements = statements,
            snapshotSchema = operationPlan.snapshotSchema ?: schema,
            warnings = operationPlan.warnings,
        )
    }

    private fun buildOfflineIncrementalOperationPlan(
        schema: AutoDdlSchema,
        changePlan: JimmerDdlSchemaChangePlan,
        settings: JimmerDdlCompilerSettings,
    ): JimmerDdlOperationPlan {
        val diffSchema = schema.filterTables(changePlan.changedTableNames)
        val operations = buildOfflineIncrementalOperations(
            schema = diffSchema,
            changePlan = changePlan,
            settings = settings,
        )
        return JimmerDdlOperationPlan(
            operations = operations,
            snapshotSchema = schema,
        )
    }

    private fun generateComparedOperations(
        schema: AutoDdlSchema,
        settings: JimmerDdlCompilerSettings,
        renamedTables: Map<String, String>,
    ): JimmerDdlOperationPlan {
        return runCatching {
            val actualSchema = JimmerDdlDatabaseSchemaReader.read(
                settings = settings,
                desiredSchema = schema,
                renamedTables = renamedTables,
            )
            val operations = SchemaDiffPlanner.plan(
                schema,
                actualSchema,
                AutoDdlDiffOptions(settings.options, true, emptyList(), emptyList())
            )
            JimmerDdlOperationPlan(operations = operations)
        }.getOrElse { cause ->
            JimmerDdlOperationPlan(
                operations = buildOfflineOperations(schema, settings),
                warnings = listOf("Jimmer DDL database comparison failed; fallback to offline DDL generation: ${cause.message ?: cause::class.qualifiedName}"),
            )
        }
    }

    private fun buildOfflineOperations(
        schema: AutoDdlSchema,
        settings: JimmerDdlCompilerSettings,
    ): List<AutoDdlOperation> {
        return buildList {
            if (settings.options.includeSequences) {
                schema.sequences.forEach { sequence ->
                    add(CreateSequence(sequence))
                }
            }

            schema.tables.forEach { table ->
                add(CreateTable(table))
            }

            if (settings.options.includeIndexes) {
                schema.tables.forEach { table ->
                    table.indexes.forEach { index ->
                        add(CreateIndex(table.name, index))
                    }
                }
            }

            if (settings.options.includeForeignKeys) {
                schema.tables.forEach { table ->
                    table.foreignKeys.forEach { foreignKey ->
                        add(AddForeignKey(table.name, foreignKey))
                    }
                }
            }

            if (settings.options.includeComments) {
                schema.tables.forEach { table ->
                    val tableComment = table.comment
                    if (!tableComment.isNullOrBlank()) {
                        add(
                            AddComment(
                                AutoDdlComment(
                                    AutoDdlCommentTargetType.TABLE,
                                    tableComment,
                                    table.name,
                                    null,
                                    null,
                                )
                            )
                        )
                    }
                    table.columns
                        .filter { column -> !column.comment.isNullOrBlank() }
                        .forEach { column ->
                            add(
                                AddComment(
                                    AutoDdlComment(
                                        AutoDdlCommentTargetType.COLUMN,
                                        column.comment.orEmpty(),
                                        table.name,
                                        column.name,
                                        null,
                                    )
                                )
                            )
                        }
                }
            }
        }
    }

    private fun buildOfflineIncrementalOperations(
        schema: AutoDdlSchema,
        changePlan: JimmerDdlSchemaChangePlan,
        settings: JimmerDdlCompilerSettings,
    ): List<AutoDdlOperation> {
        val previousSchema = changePlan.previous.toSchemaFor(
            schema = schema,
            renameOperations = changePlan.renameOperations,
        )
        if (previousSchema.tables.isEmpty() && previousSchema.sequences.isEmpty()) {
            return buildOfflineOperations(schema, settings)
        }
        return SchemaDiffPlanner.plan(
            schema,
            previousSchema,
            AutoDdlDiffOptions(settings.options, true, emptyList(), emptyList())
        )
    }

    private fun buildRenameTableStatements(
        renameOperations: List<RenameTable>,
        settings: JimmerDdlCompilerSettings,
    ): List<String> {
        return renameOperations.map { operation ->
            when (settings.databaseType) {
                JimmerDatabaseType.SQLSERVER -> "EXEC sp_rename '${operation.oldTableName}', '${operation.newTableName}';"
                else -> "ALTER TABLE ${quoteIdentifier(operation.oldTableName, settings.databaseType)} RENAME TO ${quoteIdentifier(operation.newTableName, settings.databaseType)};"
            }
        }
    }

    private fun buildPostgreSqlNullabilityRepairStatements(
        schema: AutoDdlSchema,
        settings: JimmerDdlCompilerSettings,
    ): List<String> {
        if (settings.databaseType != JimmerDatabaseType.POSTGRESQL) {
            return emptyList()
        }
        return schema.tables.flatMap { table ->
            table.columns
                .filter { column -> column.nullable }
                .map { column ->
                    "ALTER TABLE ${quoteIdentifier(table.name, settings.databaseType)} ALTER COLUMN ${quoteIdentifier(column.name, settings.databaseType)} DROP NOT NULL;"
                }
        }
    }

    private fun quoteIdentifier(
        name: String,
        databaseType: JimmerDatabaseType,
    ): String {
        val escaped = name.replace("\"", "\"\"")
        return when (databaseType) {
            JimmerDatabaseType.MYSQL -> "`$name`"
            JimmerDatabaseType.SQLSERVER -> "[$name]"
            else -> "\"$escaped\""
        }
    }

    private fun AutoDdlSchema.filterTables(tableNames: Set<String>): AutoDdlSchema {
        if (tableNames.isEmpty()) {
            return AutoDdlSchema(emptyList(), sequences)
        }
        val normalizedTableNames = tableNames.map { tableName -> tableName.lowercase() }.toSet()
        return AutoDdlSchema(
            tables.filter { table -> table.name.lowercase() in normalizedTableNames },
            sequences,
        )
    }

    private fun JimmerDdlSnapshot.toSchemaFor(
        schema: AutoDdlSchema,
        renameOperations: List<RenameTable>,
    ): AutoDdlSchema {
        val oldTableNameByNewName = renameOperations.associate { operation ->
            operation.newTableName.lowercase() to operation.oldTableName.lowercase()
        }
        val tables = schema.tables.mapNotNull { table ->
            val lookupName = oldTableNameByNewName[table.name.lowercase()] ?: table.name.lowercase()
            tableSchemas[lookupName]?.let { previous ->
                previous.copy(table.name, previous.columns, previous.foreignKeys, previous.indexes, previous.comment, previous.junction)
            }
        }
        return AutoDdlSchema(tables, schema.sequences)
    }
}

data class RenameTable(
    val oldTableName: String,
    val newTableName: String,
)

private data class JimmerDdlOperationPlan(
    val operations: List<AutoDdlOperation>,
    val snapshotSchema: AutoDdlSchema? = null,
    val warnings: List<String> = emptyList(),
)

private data class JimmerDdlCompilePlan(
    val statements: List<String>,
    val snapshotSchema: AutoDdlSchema,
    val warnings: List<String> = emptyList(),
)

private data class JimmerDdlColumnKey(
    val tableName: String,
    val columnName: String,
)

data class JimmerDdlCompilerResult(
    val settings: JimmerDdlCompilerSettings,
    val entities: List<LsiTypeDeclaration>,
    val schema: AutoDdlSchema,
    val snapshotSchema: AutoDdlSchema,
    val statements: List<String>,
    val sql: String,
    val warnings: List<String> = emptyList(),
) {
    val isEmpty
        get() = entities.isEmpty() || sql.isBlank()

    companion object {
        fun empty(
            settings: JimmerDdlCompilerSettings,
            entities: List<LsiTypeDeclaration> = emptyList(),
            schema: AutoDdlSchema = AutoDdlSchema(emptyList(), emptyList()),
        ): JimmerDdlCompilerResult {
            return JimmerDdlCompilerResult(
                settings = settings,
                entities = entities,
                schema = schema,
                snapshotSchema = schema,
                statements = emptyList(),
                sql = "",
                warnings = emptyList(),
            )
        }
    }
}
