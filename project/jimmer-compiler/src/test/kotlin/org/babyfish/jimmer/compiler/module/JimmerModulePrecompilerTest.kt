package org.babyfish.jimmer.compiler.module

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableType
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypeKind
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiSymbolId

class JimmerModulePrecompilerTest {

    @Test
    fun `apt plans four summaries and two aggregating indexes`() {
        val schema = precompiler(
            platform = JimmerModulePlatform.APT,
            moduleRequired = true,
        ).compile(
            immutableSchema(
                "demo.book.Book" to JimmerImmutableTypeKind.ENTITY,
                "demo.store.Store" to JimmerImmutableTypeKind.ENTITY,
                "demo.common.Address" to JimmerImmutableTypeKind.EMBEDDABLE,
                "demo.common.Page" to JimmerImmutableTypeKind.IMMUTABLE,
                "demo.common.Base" to JimmerImmutableTypeKind.MAPPED_SUPERCLASS,
            )
        )

        assertEquals(JimmerModulePlatform.APT, schema.platform)
        assertEquals("demo", schema.packageName)
        assertNull(schema.module)
        assertEquals(
            listOf(
                JimmerModuleSummaryKind.IMMUTABLES,
                JimmerModuleSummaryKind.TABLES,
                JimmerModuleSummaryKind.TABLE_EXES,
                JimmerModuleSummaryKind.FETCHERS,
            ),
            schema.summaries.map(JimmerModuleSummary::kind),
        )
        assertEquals(
            listOf("demo.book.Book", "demo.common.Address", "demo.common.Page", "demo.store.Store"),
            schema.summaries.first().members.map(JimmerModuleSummaryMember::qualifiedTypeName),
        )
        assertTrue(schema.summaries.all { summary ->
            summary.dependencies.aggregationMode == ArtifactAggregationMode.AGGREGATING
        })
        assertEquals(
            listOf(JimmerModuleResourceKind.ENTITIES, JimmerModuleResourceKind.IMMUTABLES),
            schema.resources.map(JimmerModuleResource::kind),
        )
        assertEquals(
            listOf("demo.book.Book", "demo.store.Store"),
            schema.resources.single { resource -> resource.kind == JimmerModuleResourceKind.ENTITIES }
                .qualifiedTypeNames,
        )
        assertEquals(
            listOf("demo.common.Address", "demo.common.Page"),
            schema.resources.single { resource -> resource.kind == JimmerModuleResourceKind.IMMUTABLES }
                .qualifiedTypeNames,
        )
        assertTrue(schema.resources.all(JimmerModuleResource::mergeExistingContent))
        assertTrue(schema.resources.all { resource ->
            resource.dependencies.scope == JimmerModuleDependencyScope.MANAGED_TYPES_AND_EXISTING_RESOURCES
        })
    }

    @Test
    fun `apt freezes custom names and summary member collision suffixes`() {
        val schema = JimmerModulePrecompiler(
            JimmerModulePrecompileOptions(
                platform = JimmerModulePlatform.APT,
                immutablesName = "DomainObjects",
                tablesName = "DomainTables",
                tableExesName = "DomainTableExes",
                fetchersName = "DomainFetchers",
                resourceGeneration = false,
            )
        ).compile(
            immutableSchema(
                "alpha.Book" to JimmerImmutableTypeKind.ENTITY,
                "beta.Book" to JimmerImmutableTypeKind.ENTITY,
                "beta.URLValue" to JimmerImmutableTypeKind.ENTITY,
            )
        )

        assertEquals("", schema.packageName)
        assertTrue(schema.resources.isEmpty())
        assertEquals(
            listOf("DomainObjects", "DomainTables", "DomainTableExes", "DomainFetchers"),
            schema.summaries.map(JimmerModuleSummary::simpleName),
        )
        assertEquals(
            listOf("createBook", "createBook_2", "createURLValue"),
            schema.summaries.single { summary -> summary.kind == JimmerModuleSummaryKind.IMMUTABLES }
                .members.map(JimmerModuleSummaryMember::generatedName),
        )
        assertEquals(
            listOf("BOOK_TABLE", "BOOK_TABLE_2", "URLVALUE_TABLE"),
            schema.summaries.single { summary -> summary.kind == JimmerModuleSummaryKind.TABLES }
                .members.map(JimmerModuleSummaryMember::generatedName),
        )
    }

    @Test
    fun `apt retains resolved index types and reclassifies changed current types`() {
        val state = JimmerModuleResourceState(
            entityQualifiedTypeNames = listOf("legacy.Book", "legacy.Changed"),
            immutableQualifiedTypeNames = listOf("legacy.Address"),
        )
        val schema = precompiler(JimmerModulePlatform.APT).compile(
            immutableSchema(
                "demo.Store" to JimmerImmutableTypeKind.ENTITY,
                "legacy.Changed" to JimmerImmutableTypeKind.EMBEDDABLE,
            ),
            state,
        )

        val entityResource = schema.resources.single { resource ->
            resource.kind == JimmerModuleResourceKind.ENTITIES
        }
        val immutableResource = schema.resources.single { resource ->
            resource.kind == JimmerModuleResourceKind.IMMUTABLES
        }
        assertEquals(listOf("demo.Store", "legacy.Book"), entityResource.qualifiedTypeNames)
        assertEquals(listOf("legacy.Address", "legacy.Changed"), immutableResource.qualifiedTypeNames)
        assertEquals(
            listOf(LsiSymbolId.type("demo.Store")),
            entityResource.dependencies.originatingTypeIds,
        )
        assertEquals(
            listOf(LsiSymbolId.type("legacy.Changed")),
            immutableResource.dependencies.originatingTypeIds,
        )
        assertEquals(
            listOf("demo.Store", "legacy.Book"),
            schema.summaries.single { summary -> summary.kind == JimmerModuleSummaryKind.TABLES }
                .members.map(JimmerModuleSummaryMember::qualifiedTypeName),
        )
        assertEquals(
            listOf("demo.Store", "legacy.Address", "legacy.Book", "legacy.Changed"),
            schema.summaries.single { summary -> summary.kind == JimmerModuleSummaryKind.IMMUTABLES }
                .members.map(JimmerModuleSummaryMember::qualifiedTypeName),
        )
    }

    @Test
    fun `apt emits empty index plans without immutable models`() {
        val schema = precompiler(JimmerModulePlatform.APT).compile(JimmerImmutableSchema(emptyList()))

        assertTrue(schema.summaries.isEmpty())
        assertEquals(2, schema.resources.size)
        assertTrue(schema.resources.all { resource -> resource.qualifiedTypeNames.isEmpty() })
        assertTrue(schema.resources.all { resource -> resource.contentTypeIds.isEmpty() })
        assertEquals(64, schema.fingerprint().length)
    }

    @Test
    fun `compilation scope separates cumulative content from current origins`() {
        val immutableSchema = immutableSchema(
            "demo.Book" to JimmerImmutableTypeKind.ENTITY,
            "demo.Store" to JimmerImmutableTypeKind.ENTITY,
        )
        val bookId = LsiSymbolId.type("demo.Book")
        val storeId = LsiSymbolId.type("demo.Store")
        val schema = precompiler(JimmerModulePlatform.APT).compile(
            immutableSchema = immutableSchema,
            compilationScope = JimmerModuleCompilationScope(
                currentImmutableTypeIds = listOf(bookId),
                compilationSourceTypeIds = listOf(bookId),
                cumulativeImmutableTypeIds = listOf(bookId, storeId),
            ),
        )

        assertEquals(
            listOf("demo.Book", "demo.Store"),
            schema.summaries.single { summary -> summary.kind == JimmerModuleSummaryKind.IMMUTABLES }
                .members.map(JimmerModuleSummaryMember::qualifiedTypeName),
        )
        assertEquals(
            listOf(bookId),
            schema.summaries.single { summary -> summary.kind == JimmerModuleSummaryKind.IMMUTABLES }
                .dependencies.originatingTypeIds,
        )
        assertEquals(
            listOf("demo.Book", "demo.Store"),
            schema.resources.single { resource -> resource.kind == JimmerModuleResourceKind.ENTITIES }
                .qualifiedTypeNames,
        )
    }

    @Test
    fun `ksp plans entity index and optional module only`() {
        val immutableSchema = immutableSchema(
            "demo.book.Book" to JimmerImmutableTypeKind.ENTITY,
            "demo.store.Store" to JimmerImmutableTypeKind.ENTITY,
            "demo.value.Address" to JimmerImmutableTypeKind.EMBEDDABLE,
        )
        val currentTypeIds = immutableSchema.types.map(JimmerImmutableType::id).sorted()
        val compilationSourceTypeIds = (currentTypeIds + LsiSymbolId.type("demo.api.BookApi")).sorted()
        val schema = precompiler(
            platform = JimmerModulePlatform.KSP,
            moduleRequired = true,
        ).compile(
            immutableSchema,
            JimmerModuleResourceState(entityQualifiedTypeNames = listOf("legacy.LegacyEntity")),
            JimmerModuleCompilationScope(
                currentImmutableTypeIds = currentTypeIds,
                compilationSourceTypeIds = compilationSourceTypeIds,
            ),
        )

        assertEquals(JimmerModulePlatform.KSP, schema.platform)
        assertEquals("demo", schema.packageName)
        assertTrue(schema.summaries.isEmpty())
        val module = requireNotNull(schema.module)
        assertEquals("JimmerModule", module.simpleName)
        assertEquals("demo.", module.entityNamePrefix)
        assertEquals(
            listOf(LsiSymbolId.type("demo.book.Book"), LsiSymbolId.type("demo.store.Store")),
            module.entityTypeIds,
        )
        assertEquals(JimmerModuleDependencyScope.ALL_COMPILATION_SOURCES, module.dependencies.scope)
        assertEquals(compilationSourceTypeIds, module.dependencies.originatingTypeIds)
        val resource = schema.resources.single()
        assertEquals(JimmerModuleResourceKind.ENTITIES, resource.kind)
        assertEquals(
            listOf("demo.book.Book", "demo.store.Store", "legacy.LegacyEntity"),
            resource.qualifiedTypeNames,
        )
        assertEquals(JimmerModuleDependencyScope.ALL_COMPILATION_SOURCES, resource.dependencies.scope)
        assertEquals(ArtifactAggregationMode.AGGREGATING, resource.dependencies.aggregationMode)
        assertEquals(compilationSourceTypeIds, resource.dependencies.originatingTypeIds)
    }

    @Test
    fun `ksp resource switch suppresses module and module switch preserves resource`() {
        val entities = immutableSchema("demo.Book" to JimmerImmutableTypeKind.ENTITY)
        val disabledResources = precompiler(
            platform = JimmerModulePlatform.KSP,
            moduleRequired = true,
            resourceGeneration = false,
        ).compile(entities)
        assertNull(disabledResources.module)
        assertTrue(disabledResources.resources.isEmpty())

        val disabledModule = precompiler(
            platform = JimmerModulePlatform.KSP,
            moduleRequired = false,
        ).compile(entities)
        assertNull(disabledModule.module)
        assertEquals(1, disabledModule.resources.size)

        val empty = precompiler(
            platform = JimmerModulePlatform.KSP,
            moduleRequired = true,
        ).compile(JimmerImmutableSchema(emptyList()))
        assertNull(empty.module)
        assertTrue(empty.resources.isEmpty())
    }

    @Test
    fun `platform and options participate in snapshots and fingerprints`() {
        val immutableSchema = immutableSchema("demo.Book" to JimmerImmutableTypeKind.ENTITY)
        val apt = precompiler(JimmerModulePlatform.APT).compile(immutableSchema)
        val ksp = precompiler(JimmerModulePlatform.KSP).compile(immutableSchema)
        val renamed = JimmerModulePrecompiler(
            JimmerModulePrecompileOptions(
                platform = JimmerModulePlatform.APT,
                tablesName = "DomainTables",
            )
        ).compile(immutableSchema)

        assertNotEquals(apt.normalizedSnapshot(), ksp.normalizedSnapshot())
        assertNotEquals(apt.fingerprint(), ksp.fingerprint())
        assertNotEquals(apt.fingerprint(), renamed.fingerprint())
    }

    @Test
    fun `rejects invalid names and unstable retained resource state`() {
        assertFailsWith<IllegalArgumentException> {
            JimmerModulePrecompileOptions(
                platform = JimmerModulePlatform.APT,
                immutablesName = "demo.Immutables",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            JimmerModulePrecompileOptions(
                platform = JimmerModulePlatform.APT,
                fetchersName = "",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            JimmerModuleResourceState(entityQualifiedTypeNames = listOf("z.Book", "a.Book"))
        }
        assertFailsWith<IllegalArgumentException> {
            JimmerModuleResourceState(immutableQualifiedTypeNames = listOf("demo.Bad Type"))
        }
    }

    private fun precompiler(
        platform: JimmerModulePlatform,
        moduleRequired: Boolean = false,
        resourceGeneration: Boolean = true,
    ): JimmerModulePrecompiler {
        return JimmerModulePrecompiler(
            JimmerModulePrecompileOptions(
                platform = platform,
                moduleRequired = moduleRequired,
                resourceGeneration = resourceGeneration,
            )
        )
    }

    private fun immutableSchema(
        vararg types: Pair<String, JimmerImmutableTypeKind>,
    ): JimmerImmutableSchema {
        return JimmerImmutableSchema(
            types = types.map { (qualifiedName, kind) ->
                JimmerImmutableType(
                    id = LsiSymbolId.type(qualifiedName),
                    qualifiedName = qualifiedName,
                    kind = kind,
                    typeParameterIds = emptyList(),
                    superTypeIds = emptyList(),
                    props = emptyList(),
                )
            },
        )
    }
}
