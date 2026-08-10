package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.jimmer.input.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import site.addzero.lsi.compiler.CompilerInputDocument
import site.addzero.lsi.compiler.CompilerInputDocumentOrigin
import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.compiler.CompilerRound
import site.addzero.lsi.compiler.CompilerSession
import site.addzero.lsi.compiler.CompilerSourceSet
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.immutable.ImmutableFeature
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.completeEntityProps
import org.babyfish.jimmer.compiler.input.CompilerInputDocumentReferenceFreezer
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class JimmerDtoSelectorStagingTest {

    @Test
    fun `default package immutable target wins wildcard candidate`() {
        val defaultTarget = typeId("demo.Book")
        val wildcardTarget = typeId("other.Book")
        val workspace = workspace(
            TypeFixture("demo.Book", LsiLanguage.KOTLIN),
            TypeFixture("other.Book", LsiLanguage.KOTLIN),
        )

        val outcome = compile(
            document(
                "demo/DefaultWins.dto",
                """
                    package demo
                    import other.*

                    BookView for Book {}
                """.trimIndent(),
            ),
            workspace,
            setOf(defaultTarget, wildcardTarget),
        )

        assertTrue(outcome.failures.isEmpty(), outcome.failures.joinToString("\n"))
        assertTrue(outcome.unresolvedDocuments.isEmpty())
        assertEquals(listOf(defaultTarget), outcome.resolvedInputs.single().targetTypeIds)
    }

    @Test
    fun `unique wildcard immutable target is selected`() {
        val wildcardTarget = typeId("other.Book")
        val workspace = workspace(TypeFixture("other.Book", LsiLanguage.KOTLIN))

        val outcome = compile(
            document(
                "demo/dto/UniqueWildcard.dto",
                """
                    package demo.dto
                    import other.*

                    BookView for Book {}
                """.trimIndent(),
            ),
            workspace,
            setOf(wildcardTarget),
        )

        assertTrue(outcome.failures.isEmpty(), outcome.failures.joinToString("\n"))
        assertTrue(outcome.unresolvedDocuments.isEmpty())
        assertEquals(listOf(wildcardTarget), outcome.resolvedInputs.single().targetTypeIds)
    }

    @Test
    fun `two wildcard immutable targets report the legacy ambiguity and location`() {
        val firstTarget = typeId("first.Book")
        val secondTarget = typeId("second.Book")
        val input = document(
            "demo/dto/Ambiguous.dto",
            """
                package demo.dto
                import first.*
                import second.*

                BookView for Book {}
            """.trimIndent(),
        )
        val snapshot = FREEZER.freeze(input)
        val targetReference = snapshot.references.single()
        val outcome = compile(
            input,
            workspace(
                TypeFixture("first.Book", LsiLanguage.KOTLIN),
                TypeFixture("second.Book", LsiLanguage.KOTLIN),
            ),
            setOf(firstTarget, secondTarget),
        )

        val failure = outcome.failures.single()
        assertEquals(
            "Ambiguous type name \"Book\", both \"first.Book\" and " +
                "\"second.Book\" are matched by wildcard imports",
            failure.message,
        )
        assertEquals(targetReference.location, failure.location)
        assertTrue(outcome.resolvedInputs.isEmpty())
    }

    @Test
    fun `no matching target keeps fallback and becomes unresolved`() {
        val fallbackTarget = typeId("demo.dto.Book")
        val outcome = compile(
            document(
                "demo/dto/UnresolvedFallback.dto",
                """
                    package demo.dto
                    import other.*

                    BookView for Book {}
                """.trimIndent(),
            ),
            LsiWorkspace.EMPTY,
            emptySet(),
        )

        val unresolved = outcome.unresolvedDocuments.single()
        assertEquals(listOf(fallbackTarget), unresolved.targetTypeIds)
        assertEquals(listOf(fallbackTarget), unresolved.unresolvedTypeIds)
        assertTrue(outcome.resolvedInputs.isEmpty())
        assertTrue(outcome.failures.isEmpty())
    }

    @Test
    fun `body ambiguity owned by source filtered target is ignored`() {
        val inactiveTarget = typeId("demo.Inactive")
        val workspace = workspace(
            TypeFixture("demo.Inactive", LsiLanguage.KOTLIN),
            TypeFixture("first.Payload", LsiLanguage.KOTLIN),
            TypeFixture("second.Payload", LsiLanguage.KOTLIN),
        )

        val outcome = compile(
            document(
                "demo/dto/InactiveBody.dto",
                """
                    package demo.dto
                    import first.*
                    import second.*

                    InactiveView for demo.Inactive {
                        payload: Payload
                        missing: MissingPayload
                    }
                """.trimIndent(),
            ),
            workspace,
            setOf(inactiveTarget),
            sourceFilter = JimmerCompilerSourceFilter(excludes = listOf("demo.Inactive")),
        )

        assertTrue(outcome.failures.isEmpty(), outcome.failures.joinToString("\n"))
        assertTrue(outcome.unresolvedDocuments.isEmpty(), outcome.unresolvedDocuments.joinToString("\n"))
        assertTrue(outcome.resolvedInputs.isEmpty())
    }

    @Test
    fun `ksp does not reselect a java fallback target to a kotlin wildcard target`() {
        val javaTarget = typeId("demo.Book")
        val kotlinWildcardTarget = typeId("other.Book")
        val workspace = workspace(
            TypeFixture("demo.Book", LsiLanguage.JAVA),
            TypeFixture("other.Book", LsiLanguage.KOTLIN),
        )
        val input = document(
            "demo/PlatformFallback.dto",
            """
                package demo
                import other.*

                BookView for Book {}
            """.trimIndent(),
        )
        val result = CompilerSession(
            id = "dto-selector-platform-fallback",
            features = listOf(
                ImmutableFeature(),
                DtoFeature(),
            ),
        ).execute(
            CompilerRound(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                currentRootTypeIds = workspace.declarations
                    .filterIsInstance<LsiClass>()
                    .mapTo(sortedSetOf(), LsiClass::id),
                platform = CompilerPlatform.KSP,
                inputDocumentSnapshots = listOf(FREEZER.freeze(input)),
            ),
        )

        val dtoResult = result.featureResults.getValue(DtoFeature.Key)
        val dtoState = dtoResult.state
        val immutableState = result.featureResults.getValue(ImmutableFeature.Key).state
        assertEquals(
            setOf(kotlinWildcardTarget),
            immutableState.targetTypeIds,
        )
        assertTrue(dtoState.graphs.isEmpty())
        assertTrue(dtoState.unresolvedDocuments.isEmpty())
        assertTrue(dtoState.failures.isEmpty())
        assertTrue(javaTarget !in dtoResult.processedSymbols)
        assertTrue(kotlinWildcardTarget !in dtoResult.processedSymbols)
    }

    private fun compile(
        document: CompilerInputDocument,
        workspace: LsiWorkspace,
        semanticRootTypeIds: Set<LsiSymbolId>,
        sourceFilter: JimmerCompilerSourceFilter = JimmerCompilerSourceFilter(),
    ): JimmerDtoRoundResolution {
        return JimmerDtoPrecompiler().compile(
            inputDocumentSnapshots = listOf(FREEZER.freeze(document)),
            immutableSchema = immutableSchema(semanticRootTypeIds),
            immutableSemanticRootTypeIds = semanticRootTypeIds,
            workspace = workspace,
            sourceFilter = sourceFilter,
            defaultNullableInputModifier = org.babyfish.jimmer.dto.compiler.DtoModifier.STATIC,
            platform = CompilerPlatform.APT,
        )
    }

    private fun immutableSchema(typeIds: Collection<LsiSymbolId>): ImmutableSchema {
        return ImmutableSchema(typeIds.sorted().map { typeId ->
            val props = completeEntityProps(typeId)
            ImmutableType(
                id = typeId,
                qualifiedName = typeId.requireTypeQualifiedName(),
                kind = ImmutableTypeKind.ENTITY,
                documentation = null,
                annotations = emptyList(),
                typeParameterIds = emptyList(),
                superTypeIds = emptyList(),
                props = props,
                primarySuperTypeId = null,
                inheritanceRootTypeId = null,
                inheritanceStrategy = null,
                joinedTableDissociateAction = null,
                instantiable = true,
                discriminatorValue = null,
                discriminatorPropId = null,
                idPropId = props.single().id,
                versionPropId = null,
                logicalDeletedPropId = null,
                acrossMicroServices = false,
                microServiceName = "",
            )
        })
    }

    private fun workspace(vararg fixtures: TypeFixture): LsiWorkspace {
        val sources = fixtures.map { fixture -> fixture.source() }
        return LsiWorkspace(
            sources = sources,
            declarations = fixtures.map { fixture -> fixture.declaration() },
        )
    }

    private fun document(relativePath: String, content: String): CompilerInputDocument {
        return CompilerInputDocument(
            kind = DTO_INPUT_DOCUMENT_KIND,
            sourceSet = CompilerSourceSet.MAIN,
            origin = CompilerInputDocumentOrigin.Project("selector-test", "src/main/dto"),
            relativePath = relativePath,
            content = content,
        )
    }

    private data class TypeFixture(
        val qualifiedName: String,
        val language: LsiLanguage,
    ) {
        fun source(): LsiSource {
            val extension = if (language == LsiLanguage.JAVA) "java" else "kt"
            return LsiSource.of(
                path = qualifiedName.replace('.', '/') + "." + extension,
                language = language,
            )
        }

        fun declaration(): LsiClass {
            val source = source()
            return LsiClass(
                id = typeId(qualifiedName),
                name = qualifiedName.substringAfterLast('.'),
                qualifiedName = qualifiedName,
                kind = LsiTypeDeclarationKind.INTERFACE,
                modality = LsiModality.ABSTRACT,
                annotations = listOf(LsiAnnotation(ENTITY_ANNOTATION)),
                origin = LsiOrigin(LsiOriginKind.SOURCE, source),
            )
        }
    }

    private companion object {
        val FREEZER = CompilerInputDocumentReferenceFreezer()
        val ENTITY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")

        fun typeId(qualifiedName: String): LsiSymbolId = LsiSymbolId.type(qualifiedName)
    }
}
