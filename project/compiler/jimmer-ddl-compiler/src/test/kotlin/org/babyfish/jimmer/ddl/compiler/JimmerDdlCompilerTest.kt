package org.babyfish.jimmer.ddl.compiler

import java.io.File
import java.sql.DriverManager
import java.sql.Types
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.babyfish.jimmer.ddl.generator.model.AutoDdlColumn
import org.babyfish.jimmer.ddl.generator.model.AutoDdlLogicalType
import org.babyfish.jimmer.ddl.generator.model.AutoDdlSchema
import org.babyfish.jimmer.ddl.generator.model.AutoDdlTable
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.LsiAnnotationArgument
import site.addzero.lsi.anno.LsiAnnotationArgumentOrigin
import site.addzero.lsi.anno.LsiAnnotationValue
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiNullability
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.field.LsiProperty
import site.addzero.lsi.type.LsiTypeArgument
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiWorkspace

class JimmerDdlCompilerTest {

    @Test
    fun `ddl processor is disabled without explicit option`() {
        assertFalse(JimmerDdlCompilerSettings.fromOptions(emptyMap()).enabled)
        assertFalse(JimmerDdlCompilerSettings.fromOptions(emptyMap()).allowDestructiveChanges)
        assertTrue(
            JimmerDdlCompilerSettings.fromOptions(
                mapOf("jimmerDdl.allowDestructiveChanges" to "true"),
            ).allowDestructiveChanges,
        )
    }

    @Test
    fun `flyway history table is configurable globally and per profile`() {
        assertEquals(
            "flyway_schema_history",
            JimmerDdlCompilerSettings.fromOptions(emptyMap()).flywayHistoryTable,
        )
        val profiles = JimmerDdlCompilerSettings.allFromOptions(
            mapOf(
                "jimmerDdl.profiles" to "default,ai",
                "jimmerDdl.flywayHistoryTable" to "flyway_schema_history",
                "jimmerDdl.profile.ai.flywayHistoryTable" to "flyway_schema_history_ai_rag",
            )
        )
        assertEquals("flyway_schema_history", profiles[0].flywayHistoryTable)
        assertEquals("flyway_schema_history_ai_rag", profiles[1].flywayHistoryTable)
    }

    @Test
    fun `missing local spring profile falls back to dev datasource`() {
        val projectDir = createTempDirectory(prefix = "jimmer-ddl-settings-test").toFile()
        val resourceDir = projectDir.resolve("src/main/resources/config").apply(File::mkdirs)
        resourceDir.resolve("datasource.yml").writeText(
            """
            spring.config.activate.on-profile: dev
            spring:
              datasource:
                dynamic:
                  primary: master
                  datasource:
                    master:
                      url: jdbc:postgresql://localhost:5432/example
                      username: postgres
                      password: secret
            """.trimIndent()
        )

        val settings = JimmerDdlCompilerSettings.fromOptions(
            mapOf(
                "jimmerDdl.outputDir" to projectDir.resolve(
                    "build/generated/jimmer-ddl/main/resources/db/migration"
                ).absolutePath,
                "jimmerDdl.springProfile" to "local",
            )
        )

        assertEquals("jdbc:postgresql://localhost:5432/example", settings.jdbc.url)
        assertEquals("postgres", settings.jdbc.username)
        assertEquals("secret", settings.jdbc.password)
        assertEquals(JimmerDatabaseType.POSTGRESQL, settings.databaseType)
    }

    @Test
    fun `compare database without jdbc url skips ddl generation`() {
        val result = compile(
            workspace = bookWorkspace(),
            settings = settings().copy(compareDatabase = true, jdbc = JimmerDdlJdbcSettings()),
        )

        assertTrue(result.warnings.any { warning -> "no JDBC URL" in warning })
        assertTrue(result.sql.isBlank())
    }

    @Test
    fun `database comparison failure skips ddl generation`() {
        val result = compile(
            workspace = bookWorkspace(),
            settings = settings().copy(
                compareDatabase = true,
                jdbc = JimmerDdlJdbcSettings(url = "jdbc:unsupported://localhost/example"),
            ),
        )

        assertTrue(result.warnings.any { warning -> "comparison failed" in warning && "skipped" in warning })
        assertTrue(result.sql.isBlank())
    }

    @Test
    fun `postgresql native type names override generic jdbc type codes`() {
        assertEquals(AutoDdlLogicalType.TEXT, Types.VARCHAR.toAutoDdlLogicalType("text"))
        assertEquals(AutoDdlLogicalType.STRING, Types.VARCHAR.toAutoDdlLogicalType("varchar"))
        assertEquals(AutoDdlLogicalType.JSON, Types.OTHER.toAutoDdlLogicalType("jsonb"))
        assertEquals(AutoDdlLogicalType.UUID, Types.OTHER.toAutoDdlLogicalType("uuid"))
    }

    @Test
    fun `database reader keeps actual columns missing from desired schema`() {
        val jdbcUrl = "jdbc:h2:mem:jimmer_ddl_reader;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(jdbcUrl, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE book (id BIGINT PRIMARY KEY, legacy_name VARCHAR(255))")
            }
        }
        val desiredSchema = AutoDdlSchema(
            tables = listOf(
                AutoDdlTable(
                    name = "book",
                    columns = listOf(
                        AutoDdlColumn("id", AutoDdlLogicalType.INT64, nullable = false, primaryKey = true)
                    ),
                )
            )
        )

        val actualSchema = JimmerDdlDatabaseSchemaReader.read(
            settings = JimmerDdlCompilerSettings(
                databaseType = JimmerDatabaseType.H2,
                jdbc = JimmerDdlJdbcSettings(url = jdbcUrl, username = "sa"),
            ),
            desiredSchema = desiredSchema,
        )

        assertTrue(actualSchema.tables.single().columns.any { column -> column.name == "legacy_name" })
    }

    @Test
    fun `flyway history reader returns only successful scripts from configured table`() {
        val jdbcUrl = "jdbc:h2:mem:jimmer_ddl_flyway_history;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(jdbcUrl, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE flyway_schema_history_iot_management " +
                        "(script VARCHAR(255) NOT NULL, success BOOLEAN NOT NULL)"
                )
                statement.execute(
                    "INSERT INTO flyway_schema_history_iot_management(script, success) VALUES " +
                        "('V1__applied.sql', TRUE), ('V2__failed.sql', FALSE)"
                )
            }
        }

        val history = JimmerDdlFlywayHistoryReader.read(
            JimmerDdlCompilerSettings(
                databaseType = JimmerDatabaseType.H2,
                compareDatabase = true,
                flywayHistoryTable = "flyway_schema_history_iot_management",
                jdbc = JimmerDdlJdbcSettings(url = jdbcUrl, username = "sa"),
            )
        )

        assertTrue(history.available)
        assertEquals(setOf("V1__applied.sql"), history.appliedScripts)
    }

    @Test
    fun `table annotation rename emits rename table operation from entity snapshot`() {
        val outputDir = createTempDirectory(prefix = "jimmer-ddl-test")
            .toFile()
            .resolve("build/generated/jimmer-ddl/main/resources/db/migration")
        val settings = settings(outputDir)
        val originalEntity = bookWorkspace(tableName = "biz_user").singleEntity()
        JimmerDdlEntityTableSnapshot.writeSnapshot(
            entities = listOf(originalEntity),
            schema = AutoDdlSchema(
                listOf(
                    AutoDdlTable(
                        "biz_user",
                        listOf(
                            AutoDdlColumn(
                                "id",
                                AutoDdlLogicalType.INT64,
                                false,
                                null,
                                null,
                                null,
                                null,
                                null,
                                true,
                                false,
                                null,
                                null,
                            ),
                        ),
                        emptyList(),
                        emptyList(),
                        null,
                        null,
                    )
                ),
                emptyList(),
            ),
            settings = settings,
        )

        val renamedEntity = bookWorkspace(tableName = "biz_user_ext").singleEntity()
        val renamedSchema = AutoDdlSchema(
            listOf(
                AutoDdlTable(
                    "biz_user_ext",
                    listOf(
                        AutoDdlColumn(
                            "id",
                            AutoDdlLogicalType.INT64,
                            false,
                            null,
                            null,
                            null,
                            null,
                            null,
                            true,
                            false,
                            null,
                            null,
                        ),
                    ),
                    emptyList(),
                    emptyList(),
                    null,
                    null,
                )
            ),
            emptyList(),
        )

        val operations = JimmerDdlEntityTableSnapshot.planRenameTables(
            entities = listOf(renamedEntity),
            schema = renamedSchema,
            settings = settings,
        )

        assertEquals(listOf(RenameTable("biz_user", "biz_user_ext")), operations)
    }

    @Test
    fun `postgresql ddl contains idempotent table column and nullable repair statements`() {
        val result = compile(bookWorkspace())

        assertContains(result.sql, "CREATE TABLE IF NOT EXISTS \"book\"")
        assertContains(result.sql, "\"title\" VARCHAR(255) NOT NULL")
        assertContains(result.sql, "ALTER TABLE \"book\" ALTER COLUMN \"subtitle\" DROP NOT NULL;")
    }

    @Test
    fun `same schema snapshot does not emit ddl`() {
        val outputDir = tempOutputDir()
        val settings = settings(outputDir)
        val workspace = bookWorkspace()
        val first = compile(workspace, settings)
        JimmerDdlEntityTableSnapshot.writeSnapshot(
            entities = first.entities,
            schema = first.schema,
            settings = settings,
        )

        val second = compile(workspace, settings)

        assertEquals("", second.sql)
    }

    @Test
    fun `changed schema snapshot only emits incremental ddl`() {
        val outputDir = tempOutputDir()
        val settings = settings(outputDir)
        val first = compile(bookWorkspace(), settings)
        JimmerDdlEntityTableSnapshot.writeSnapshot(
            entities = first.entities,
            schema = first.schema,
            settings = settings,
        )

        val changed = compile(bookWorkspace(extraField = true), settings)

        assertContains(changed.sql, "ALTER TABLE \"book\" ADD COLUMN IF NOT EXISTS \"summary\" VARCHAR(255);")
        assertFalse("CREATE TABLE" in changed.sql)
    }

    @Test
    fun `removed property emits drop column ddl`() {
        val outputDir = tempOutputDir()
        val settings = settings(outputDir)
        val first = compile(bookWorkspace(extraField = true), settings)
        JimmerDdlEntityTableSnapshot.writeSnapshot(
            entities = first.entities,
            schema = first.schema,
            settings = settings,
        )

        val changed = compile(bookWorkspace(extraField = false), settings)

        assertContains(changed.sql, "ALTER TABLE \"book\" DROP COLUMN IF EXISTS \"summary\";")
    }

    @Test
    fun `removed property is preserved when destructive changes are disabled`() {
        val outputDir = tempOutputDir()
        val settings = settings(outputDir).copy(allowDestructiveChanges = false)
        val first = compile(bookWorkspace(extraField = true), settings)
        JimmerDdlEntityTableSnapshot.writeSnapshot(
            entities = first.entities,
            schema = first.schema,
            settings = settings,
        )

        val changed = compile(bookWorkspace(extraField = false), settings)

        assertFalse("DROP COLUMN" in changed.sql)
    }

    @Test
    fun `offline incremental ddl emits structural column changes`() {
        val outputDir = tempOutputDir()
        val settings = settings(outputDir).copy(nullabilityRepairOnly = true)
        val first = compile(bookWorkspace(), settings)
        JimmerDdlEntityTableSnapshot.writeSnapshot(
            entities = first.entities,
            schema = first.schema,
            settings = settings,
        )

        val changed = compile(
            workspace = bookWorkspace(extraField = true, titleTypeName = "Int"),
            settings = settings,
        )

        assertContains(changed.sql, "ALTER TABLE \"book\" ADD COLUMN IF NOT EXISTS \"summary\" VARCHAR(255);")
        assertContains(changed.sql, "ALTER COLUMN \"title\" TYPE INTEGER")
        assertContains(changed.sql, "USING CASE")
        assertTrue(changed.warnings.none { warning -> "skipped column structure changes" in warning })
    }

    @Test
    fun `generated snapshot is staged under generated resources instead of source snapshot`() {
        val projectDir = createTempDirectory(prefix = "jimmer-ddl-test").toFile()
        val outputDir = projectDir.resolve("build/generated/jimmer-ddl/main/resources/db/migration")
        val settings = settings(outputDir)
        val first = compile(bookWorkspace(), settings)
        JimmerDdlEntityTableSnapshot.writeSnapshot(
            entities = first.entities,
            schema = first.schema,
            settings = settings,
        )
        val sourceSnapshot = requireNotNull(JimmerDdlCompilerFiles.resolveSnapshotDirectory(settings))
        val sourceContent = sourceSnapshot.readSnapshotContents()
        val changed = compile(bookWorkspace(extraField = true), settings)

        JimmerDdlEntityTableSnapshot.writeGeneratedSnapshot(
            entities = changed.entities,
            schema = changed.schema,
            settings = settings,
        )

        val generatedSnapshot = JimmerDdlCompilerFiles.resolveGeneratedSnapshotDirectory(settings)
        assertTrue(generatedSnapshot.isDirectory)
        assertEquals(sourceContent, sourceSnapshot.readSnapshotContents())
        assertFalse(sourceContent == generatedSnapshot.readSnapshotContents())
    }

    @Test
    fun `cross module inherited many to many emits junction table without target table`() {
        val userId = LsiSymbolId.type(USER_TYPE)
        val userPropertyId = LsiSymbolId.property(userId, "id")
        val userWorkspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = USER_TYPE,
                    annotations = listOf(entity(), table("system_users")),
                    memberIds = listOf(userPropertyId),
                ),
                property(userId, "id", LsiPrimitiveType(LsiPrimitiveKind.LONG), listOf(id())),
            ),
        )
        val baseId = LsiSymbolId.type(BASE_PERSON_TYPE)
        val peoplePropertyId = LsiSymbolId.property(baseId, "personInCharge")
        val deviceId = LsiSymbolId.type(DEVICE_TYPE)
        val devicePropertyId = LsiSymbolId.property(deviceId, "id")
        val deviceWorkspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = BASE_PERSON_TYPE,
                    annotations = listOf(mappedSuperclass()),
                    memberIds = listOf(peoplePropertyId),
                ),
                property(
                    ownerId = baseId,
                    name = "personInCharge",
                    type = LsiDeclaredType(
                        declarationId = LsiSymbolId.type("kotlin.collections.List"),
                        arguments = listOf(LsiTypeArgument.invariant(LsiDeclaredType(userId))),
                    ),
                    annotations = listOf(manyToMany()),
                ),
                type(
                    qualifiedName = DEVICE_TYPE,
                    annotations = listOf(entity(), table("ai_power_device")),
                    superTypes = listOf(LsiDeclaredType(baseId)),
                    memberIds = listOf(devicePropertyId),
                ),
                property(deviceId, "id", LsiPrimitiveType(LsiPrimitiveKind.LONG), listOf(id())),
            ),
        )

        val result = compile(
            workspace = deviceWorkspace,
            relationTargetWorkspace = userWorkspace,
        )

        assertContains(result.sql, "CREATE TABLE IF NOT EXISTS \"ai_power_device\"")
        assertContains(
            result.sql,
            "CREATE TABLE IF NOT EXISTS \"equipment_information_archive_person_in_charge_mapping\"",
        )
        assertContains(result.sql, "\"equipment_information_archive_id\" BIGINT NOT NULL")
        assertContains(result.sql, "\"user_id\" BIGINT NOT NULL")
        assertFalse("\"system_users\"" in result.sql)
    }

    @Test
    fun `cross module many to one emits reference column without target table`() {
        val customerId = LsiSymbolId.type("demo.Customer")
        val customerPropertyId = LsiSymbolId.property(customerId, "id")
        val customerWorkspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.Customer",
                    annotations = listOf(entity(), table("customers")),
                    memberIds = listOf(customerPropertyId),
                ),
                property(customerId, "id", LsiPrimitiveType(LsiPrimitiveKind.LONG), listOf(id())),
            ),
        )
        val orderId = LsiSymbolId.type("demo.Order")
        val orderPropertyIds = listOf(
            LsiSymbolId.property(orderId, "id"),
            LsiSymbolId.property(orderId, "customer"),
        )
        val orderWorkspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.Order",
                    annotations = listOf(entity(), table("orders")),
                    memberIds = orderPropertyIds,
                ),
                property(orderId, "id", LsiPrimitiveType(LsiPrimitiveKind.LONG), listOf(id())),
                property(
                    ownerId = orderId,
                    name = "customer",
                    type = LsiDeclaredType(customerId),
                    annotations = listOf(manyToOne(), joinColumn("customer_id")),
                ),
            ),
        )

        val result = compile(
            workspace = orderWorkspace,
            relationTargetWorkspace = customerWorkspace,
        )

        assertContains(result.sql, "\"customer_id\" BIGINT NOT NULL")
        assertContains(result.sql, "REFERENCES \"customers\" (\"id\")")
        assertFalse("CREATE TABLE IF NOT EXISTS \"customers\"" in result.sql)
    }

    @Test
    fun `association inputNotNull produces nonnull foreign key column`() {
        val nodeId = LsiSymbolId.type("demo.BaseNode")
        val relationId = LsiSymbolId.type("demo.BaseNodeRelation")
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.BaseNode",
                    annotations = listOf(entity(), table("iot_base_node")),
                    memberIds = listOf(LsiSymbolId.property(nodeId, "id")),
                ),
                property(nodeId, "id", LsiPrimitiveType(LsiPrimitiveKind.LONG), listOf(id())),
                type(
                    qualifiedName = "demo.BaseNodeRelation",
                    annotations = listOf(entity(), table("iot_base_node_relation")),
                    memberIds = listOf(
                        LsiSymbolId.property(relationId, "id"),
                        LsiSymbolId.property(relationId, "fromNode"),
                    ),
                ),
                property(relationId, "id", LsiPrimitiveType(LsiPrimitiveKind.LONG), listOf(id())),
                property(
                    ownerId = relationId,
                    name = "fromNode",
                    type = LsiDeclaredType(nodeId, nullability = LsiNullability.NULLABLE),
                    annotations = listOf(manyToOne(inputNotNull = true), joinColumn("from_node_id")),
                ),
            )
        )

        val result = compile(workspace)

        assertEquals(false, result.schema.table("iot_base_node_relation")?.column("from_node_id")?.nullable)
        assertContains(result.sql, "\"from_node_id\" BIGINT NOT NULL")
    }

    @Test
    fun `join table annotation controls names filter and composite primary key`() {
        val noticeId = LsiSymbolId.type("demo.Notice")
        val deptId = LsiSymbolId.type("demo.Dept")
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.Notice",
                    annotations = listOf(entity(), table("system_notice")),
                    memberIds = listOf(
                        LsiSymbolId.property(noticeId, "id"),
                        LsiSymbolId.property(noticeId, "depts"),
                    ),
                ),
                property(noticeId, "id", LsiPrimitiveType(LsiPrimitiveKind.LONG), listOf(id())),
                property(
                    ownerId = noticeId,
                    name = "depts",
                    type = LsiDeclaredType(
                        declarationId = LsiSymbolId.type("kotlin.collections.List"),
                        arguments = listOf(LsiTypeArgument.invariant(LsiDeclaredType(deptId))),
                    ),
                    annotations = listOf(
                        manyToMany(),
                        joinTable(
                            name = "biz_mapping",
                            joinColumnName = "from_id",
                            inverseJoinColumnName = "to_id",
                            filterColumnName = "mapping_type",
                        ),
                    ),
                ),
                type(
                    qualifiedName = "demo.Dept",
                    annotations = listOf(entity(), table("system_dept")),
                    memberIds = listOf(LsiSymbolId.property(deptId, "id")),
                ),
                property(deptId, "id", LsiPrimitiveType(LsiPrimitiveKind.LONG), listOf(id())),
            )
        )

        val result = compile(workspace)

        assertContains(result.sql, "CREATE TABLE IF NOT EXISTS \"biz_mapping\"")
        assertContains(result.sql, "\"mapping_type\" TEXT NOT NULL")
        assertContains(result.sql, "PRIMARY KEY (\"from_id\", \"to_id\", \"mapping_type\")")
    }

    @Test
    fun `ordinal enum property uses integer storage`() {
        val enumId = LsiSymbolId.type("demo.EnumKnowledgeType")
        val documentId = LsiSymbolId.type("demo.KnowledgeDocument")
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.EnumKnowledgeType",
                    annotations = listOf(enumType("ORDINAL")),
                    kind = LsiTypeDeclarationKind.ENUM,
                ),
                type(
                    qualifiedName = "demo.KnowledgeDocument",
                    annotations = listOf(entity()),
                    memberIds = listOf(
                        LsiSymbolId.property(documentId, "id"),
                        LsiSymbolId.property(documentId, "knowledgeType"),
                    ),
                ),
                property(documentId, "id", LsiPrimitiveType(LsiPrimitiveKind.LONG), listOf(id())),
                property(documentId, "knowledgeType", LsiDeclaredType(enumId)),
            )
        )

        val result = compile(workspace)

        assertContains(result.sql, "\"knowledge_type\" INTEGER NOT NULL")
    }

    @Test
    fun `nested embeddable applies prop override and parent nullability`() {
        val coordinatesId = LsiSymbolId.type("demo.Coordinates")
        val locationId = LsiSymbolId.type("demo.Location")
        val siteId = LsiSymbolId.type("demo.Site")
        val workspace = LsiWorkspace(
            declarations = listOf(
                type(
                    qualifiedName = "demo.Coordinates",
                    annotations = listOf(embeddable()),
                    memberIds = listOf(LsiSymbolId.property(coordinatesId, "latitude")),
                ),
                property(coordinatesId, "latitude", stringType(), listOf(column("latitude_value"))),
                type(
                    qualifiedName = "demo.Location",
                    annotations = listOf(embeddable()),
                    memberIds = listOf(LsiSymbolId.property(locationId, "coordinates")),
                ),
                property(
                    ownerId = locationId,
                    name = "coordinates",
                    type = LsiDeclaredType(coordinatesId, nullability = LsiNullability.NULLABLE),
                ),
                type(
                    qualifiedName = "demo.Site",
                    annotations = listOf(entity()),
                    memberIds = listOf(LsiSymbolId.property(siteId, "location")),
                ),
                property(
                    ownerId = siteId,
                    name = "location",
                    type = LsiDeclaredType(locationId),
                    annotations = listOf(propOverride("coordinates.latitude", "site_latitude")),
                ),
            )
        )

        val result = compile(workspace)
        val columns = result.schema.table("site")?.columns.orEmpty()

        assertEquals(listOf("site_latitude"), columns.map(AutoDdlColumn::name))
        assertEquals(true, columns.single().nullable)
    }

    private fun compile(
        workspace: LsiWorkspace,
        settings: JimmerDdlCompilerSettings = settings(),
        relationTargetWorkspace: LsiWorkspace = workspace,
    ): JimmerDdlCompilerResult {
        return JimmerDdlCompiler.compile(
            workspace = workspace,
            entityTypeIds = workspace.jimmerEntityTypes().map(LsiClass::id),
            settings = settings,
            relationTargetWorkspace = relationTargetWorkspace,
        )
    }

    private fun bookWorkspace(
        tableName: String = "book",
        extraField: Boolean = false,
        titleTypeName: String = "String",
    ): LsiWorkspace {
        val entityId = LsiSymbolId.type("demo.Book")
        val propertyNames = buildList {
            add("id")
            add("title")
            add("subtitle")
            if (extraField) {
                add("summary")
            }
        }
        val declarations = buildList {
            add(
                type(
                    qualifiedName = "demo.Book",
                    annotations = listOf(entity(), table(tableName)),
                    memberIds = propertyNames.map { name -> LsiSymbolId.property(entityId, name) },
                )
            )
            add(property(entityId, "id", LsiPrimitiveType(LsiPrimitiveKind.LONG), listOf(id())))
            add(
                property(
                    ownerId = entityId,
                    name = "title",
                    type = if (titleTypeName == "Int") {
                        LsiPrimitiveType(LsiPrimitiveKind.INT)
                    } else {
                        stringType()
                    },
                    annotations = listOf(column("title")),
                )
            )
            add(
                property(
                    ownerId = entityId,
                    name = "subtitle",
                    type = stringType(nullable = true),
                    annotations = listOf(column("subtitle")),
                )
            )
            if (extraField) {
                add(
                    property(
                        ownerId = entityId,
                        name = "summary",
                        type = stringType(nullable = true),
                        annotations = listOf(column("summary")),
                    )
                )
            }
        }
        return LsiWorkspace(declarations = declarations)
    }

    private fun type(
        qualifiedName: String,
        annotations: List<LsiAnnotation> = emptyList(),
        superTypes: List<LsiType> = emptyList(),
        memberIds: List<LsiSymbolId> = emptyList(),
        kind: LsiTypeDeclarationKind = LsiTypeDeclarationKind.INTERFACE,
    ): LsiClass {
        return LsiClass(
            id = LsiSymbolId.type(qualifiedName),
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = kind,
            superTypes = superTypes,
            memberIds = memberIds,
            annotations = annotations,
            origin = ORIGIN,
        )
    }

    private fun property(
        ownerId: LsiSymbolId,
        name: String,
        type: LsiType,
        annotations: List<LsiAnnotation> = emptyList(),
    ): LsiProperty {
        return LsiProperty(
            id = LsiSymbolId.property(ownerId, name),
            name = name,
            ownerId = ownerId,
            type = type,
            annotations = annotations,
            origin = ORIGIN,
        )
    }

    private fun LsiWorkspace.singleEntity(): LsiClass {
        return jimmerEntityTypes().single()
    }

    private fun stringType(nullable: Boolean = false): LsiDeclaredType {
        return LsiDeclaredType(
            declarationId = LsiSymbolId.type("java.lang.String"),
            nullability = if (nullable) LsiNullability.NULLABLE else LsiNullability.NON_NULL,
        )
    }

    private fun entity(): LsiAnnotation = annotation("org.babyfish.jimmer.sql.Entity")

    private fun mappedSuperclass(): LsiAnnotation = annotation("org.babyfish.jimmer.sql.MappedSuperclass")

    private fun table(name: String): LsiAnnotation {
        return annotation(
            qualifiedName = "org.babyfish.jimmer.sql.Table",
            arguments = mapOf("name" to LsiAnnotationValue.StringValue(name)),
        )
    }

    private fun id(): LsiAnnotation = annotation("org.babyfish.jimmer.sql.Id")

    private fun column(name: String): LsiAnnotation {
        return annotation(
            qualifiedName = "org.babyfish.jimmer.sql.Column",
            arguments = mapOf("name" to LsiAnnotationValue.StringValue(name)),
        )
    }

    private fun manyToMany(): LsiAnnotation = annotation("org.babyfish.jimmer.sql.ManyToMany")

    private fun manyToOne(inputNotNull: Boolean = false): LsiAnnotation {
        return annotation(
            qualifiedName = "org.babyfish.jimmer.sql.ManyToOne",
            arguments = mapOf("inputNotNull" to LsiAnnotationValue.BooleanValue(inputNotNull)),
        )
    }

    private fun joinTable(
        name: String,
        joinColumnName: String,
        inverseJoinColumnName: String,
        filterColumnName: String,
    ): LsiAnnotation {
        val filter = annotation(
            qualifiedName = "org.babyfish.jimmer.sql.JoinTable.JoinTableFilter",
            arguments = mapOf("columnName" to LsiAnnotationValue.StringValue(filterColumnName)),
        )
        return annotation(
            qualifiedName = "org.babyfish.jimmer.sql.JoinTable",
            arguments = mapOf(
                "name" to LsiAnnotationValue.StringValue(name),
                "joinColumnName" to LsiAnnotationValue.StringValue(joinColumnName),
                "inverseJoinColumnName" to LsiAnnotationValue.StringValue(inverseJoinColumnName),
                "filter" to LsiAnnotationValue.NestedAnnotationValue(filter),
            ),
        )
    }

    private fun enumType(strategy: String): LsiAnnotation {
        return annotation(
            qualifiedName = "org.babyfish.jimmer.sql.EnumType",
            arguments = mapOf(
                "value" to LsiAnnotationValue.EnumValue(
                    enumType = LsiSymbolId.type("org.babyfish.jimmer.sql.EnumType.Strategy"),
                    entryName = strategy,
                )
            ),
        )
    }

    private fun embeddable(): LsiAnnotation = annotation("org.babyfish.jimmer.sql.Embeddable")

    private fun propOverride(prop: String, columnName: String): LsiAnnotation {
        return annotation(
            qualifiedName = "org.babyfish.jimmer.sql.PropOverride",
            arguments = mapOf(
                "prop" to LsiAnnotationValue.StringValue(prop),
                "columnName" to LsiAnnotationValue.StringValue(columnName),
            ),
        )
    }

    private fun joinColumn(name: String): LsiAnnotation {
        return annotation(
            qualifiedName = "org.babyfish.jimmer.sql.JoinColumn",
            arguments = mapOf("name" to LsiAnnotationValue.StringValue(name)),
        )
    }

    private fun annotation(
        qualifiedName: String,
        arguments: Map<String, LsiAnnotationValue> = emptyMap(),
    ): LsiAnnotation {
        return LsiAnnotation(
            type = LsiSymbolId.type(qualifiedName),
            arguments = arguments.mapValues { (_, value) ->
                LsiAnnotationArgument(value, LsiAnnotationArgumentOrigin.EXPLICIT)
            },
        )
    }

    private fun settings(outputDir: File = tempOutputDir()): JimmerDdlCompilerSettings {
        return JimmerDdlCompilerSettings(
            databaseType = JimmerDatabaseType.POSTGRESQL,
            outputFormat = JimmerDdlOutputFormat.PLAIN,
            outputDir = outputDir.absolutePath,
            compareDatabase = false,
            allowDestructiveChanges = true,
        )
    }

    private fun File.readSnapshotContents(): Map<String, String> {
        return listFiles { file -> file.isFile && file.extension == "properties" }
            .orEmpty()
            .associate { file -> file.name to file.readText() }
    }

    private fun tempOutputDir(): File {
        return createTempDirectory(prefix = "jimmer-ddl-test")
            .toFile()
            .resolve("build/generated/jimmer-ddl/main/resources/db/migration")
    }

    companion object {
        private const val USER_TYPE = "site.addzero.crud.model.system.user.User"
        private const val BASE_PERSON_TYPE =
            "cn.iocoder.yudao.module.ai.power.equipment_information_archive.entity.BasePersonInCharge"
        private const val DEVICE_TYPE =
            "cn.iocoder.yudao.module.ai.power.equipment_information_archive.entity.EquipmentInformationArchive"

        private val ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
    }
}
