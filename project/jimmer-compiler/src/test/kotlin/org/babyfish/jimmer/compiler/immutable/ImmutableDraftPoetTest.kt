package org.babyfish.jimmer.compiler.immutable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.toImmutableSchema
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetArtifact

class ImmutableDraftPoetTest {

    @Test
    fun `same source draft artifacts are isolating and immediate`() {
        val source = source("src/main/java/demo/Book.java")
        val bookId = typeId("demo.Book")
        val idPropId = LsiSymbolId.property(bookId, "id")
        val origin = origin(source)
        val workspace = LsiWorkspace(
            sources = listOf(source),
            declarations = listOf(
                immutableType(
                    id = bookId,
                    memberIds = listOf(idPropId),
                    origin = origin,
                ),
                property(
                    id = idPropId,
                    ownerId = bookId,
                    type = LONG_TYPE,
                    annotations = listOf(LsiAnnotation(ID)),
                    origin = origin,
                ),
            ),
        )

        val artifacts = workspace.draftArtifacts(bookId)

        assertEquals(
            setOf(ArtifactKind.JAVA_SOURCE, ArtifactKind.KOTLIN_SOURCE),
            artifacts.mapTo(mutableSetOf()) { artifact -> artifact.kind },
        )
        assertTrue(artifacts.all { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.ISOLATING &&
                artifact.emissionMode == ArtifactEmissionMode.IMMEDIATE
        })
        assertTrue(artifacts.all { artifact -> artifact.originatingSymbols == setOf(bookId) })
        assertTrue(artifacts.all { artifact -> artifact.originatingSources == setOf(source) })
        assertTrue(artifacts.all { artifact -> artifact.dependencySources == setOf(source) })
        assertTrue(artifacts.all { artifact ->
            artifact.dependencySymbols.containsAll(setOf(bookId, idPropId))
        })
        artifacts.forEach(::assertGeneratedArtifactBoundary)
    }

    @Test
    fun `cross source inherited draft artifacts are aggregating and immediate`() {
        val bookSource = source("src/main/java/demo/Book.java")
        val baseSource = source("src/main/java/shared/BaseRecord.java")
        val bookId = typeId("demo.Book")
        val baseId = typeId("shared.BaseRecord")
        val baseIdPropId = LsiSymbolId.property(baseId, "id")
        val workspace = LsiWorkspace(
            sources = listOf(bookSource, baseSource),
            declarations = listOf(
                immutableType(
                    id = baseId,
                    memberIds = listOf(baseIdPropId),
                    marker = MAPPED_SUPERCLASS,
                    origin = origin(baseSource),
                ),
                property(
                    id = baseIdPropId,
                    ownerId = baseId,
                    type = LONG_TYPE,
                    annotations = listOf(LsiAnnotation(ID)),
                    origin = origin(baseSource),
                ),
                immutableType(
                    id = bookId,
                    superTypeIds = listOf(baseId),
                    origin = origin(bookSource),
                ),
            ),
        )

        val artifacts = workspace.draftArtifacts(bookId)

        assertTrue(artifacts.all { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.AGGREGATING &&
                artifact.emissionMode == ArtifactEmissionMode.IMMEDIATE
        })
        assertTrue(artifacts.all { artifact -> artifact.originatingSources == setOf(bookSource) })
        assertTrue(artifacts.all { artifact ->
            artifact.dependencySymbols.containsAll(setOf(bookId, baseId, baseIdPropId))
        })
        assertTrue(artifacts.all { artifact ->
            artifact.dependencySources == setOf(bookSource, baseSource)
        })
        artifacts.forEach(::assertGeneratedArtifactBoundary)
    }

    @Test
    fun `binary draft dependencies keep artifacts isolating`() {
        val bookSource = source("src/main/java/demo/Book.java")
        val binarySource = source(
            path = "binary/library/ExternalPayload.class",
            kind = LsiSourceKind.BINARY,
        )
        val bookId = typeId("demo.Book")
        val payloadId = typeId("library.ExternalPayload")
        val idPropId = LsiSymbolId.property(bookId, "id")
        val payloadPropId = LsiSymbolId.property(bookId, "payload")
        val bookOrigin = origin(bookSource)
        val workspace = LsiWorkspace(
            sources = listOf(bookSource, binarySource),
            declarations = listOf(
                plainType(payloadId, origin(binarySource)),
                immutableType(
                    id = bookId,
                    memberIds = listOf(idPropId, payloadPropId),
                    origin = bookOrigin,
                ),
                property(
                    id = idPropId,
                    ownerId = bookId,
                    type = LONG_TYPE,
                    annotations = listOf(LsiAnnotation(ID)),
                    origin = bookOrigin,
                ),
                property(
                    id = payloadPropId,
                    ownerId = bookId,
                    type = LsiDeclaredType(payloadId),
                    origin = bookOrigin,
                ),
            ),
        )

        val artifacts = workspace.draftArtifacts(bookId)

        assertTrue(artifacts.all { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.ISOLATING &&
                artifact.emissionMode == ArtifactEmissionMode.IMMEDIATE
        })
        assertTrue(artifacts.all { artifact -> payloadId in artifact.dependencySymbols })
        assertTrue(artifacts.all { artifact -> artifact.dependencySources == setOf(bookSource) })
        assertTrue(artifacts.all { artifact -> binarySource !in artifact.dependencySources })
        artifacts.forEach(::assertGeneratedArtifactBoundary)
    }

    @Test
    fun `generated dependency origin chain is preserved transitively`() {
        val bookSource = source("src/main/java/demo/Book.java")
        val contractSource = source("src/main/java/contracts/PayloadContract.java")
        val modelSource = source(
            path = "build/generated/source/contracts/PayloadModel.java",
            kind = LsiSourceKind.GENERATED,
        )
        val payloadSource = source(
            path = "build/generated/source/models/Payload.java",
            kind = LsiSourceKind.GENERATED,
        )
        val bookId = typeId("demo.Book")
        val contractId = typeId("contracts.PayloadContract")
        val modelId = typeId("contracts.PayloadModel")
        val payloadId = typeId("models.Payload")
        val idPropId = LsiSymbolId.property(bookId, "id")
        val payloadPropId = LsiSymbolId.property(bookId, "payload")
        val bookOrigin = origin(bookSource)
        val workspace = LsiWorkspace(
            sources = listOf(bookSource, contractSource, modelSource, payloadSource),
            declarations = listOf(
                plainType(contractId, origin(contractSource)),
                plainType(
                    id = modelId,
                    origin = origin(modelSource, setOf(contractId)),
                ),
                plainType(
                    id = payloadId,
                    origin = origin(payloadSource, setOf(modelId)),
                ),
                immutableType(
                    id = bookId,
                    memberIds = listOf(idPropId, payloadPropId),
                    origin = bookOrigin,
                ),
                property(
                    id = idPropId,
                    ownerId = bookId,
                    type = LONG_TYPE,
                    annotations = listOf(LsiAnnotation(ID)),
                    origin = bookOrigin,
                ),
                property(
                    id = payloadPropId,
                    ownerId = bookId,
                    type = LsiDeclaredType(payloadId),
                    origin = bookOrigin,
                ),
            ),
        )

        val artifacts = workspace.draftArtifacts(bookId)
        val expectedSources = setOf(bookSource, payloadSource, modelSource, contractSource)

        assertTrue(artifacts.all { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.AGGREGATING &&
                artifact.emissionMode == ArtifactEmissionMode.IMMEDIATE
        })
        assertTrue(artifacts.all { artifact -> artifact.originatingSources == setOf(bookSource) })
        assertTrue(artifacts.all { artifact -> payloadId in artifact.dependencySymbols })
        assertTrue(artifacts.all { artifact -> artifact.dependencySources == expectedSources })
        artifacts.forEach(::assertGeneratedArtifactBoundary)
    }

    @Test
    fun `generated draft from a later round does not become its own source dependency`() {
        val bookSource = source("src/main/java/demo/Book.java")
        val bookId = typeId("demo.Book")
        val idPropId = LsiSymbolId.property(bookId, "id")
        val bookOrigin = origin(bookSource)
        val firstWorkspace = LsiWorkspace(
            sources = listOf(bookSource),
            declarations = listOf(
                immutableType(
                    id = bookId,
                    memberIds = listOf(idPropId),
                    origin = bookOrigin,
                ),
                property(
                    id = idPropId,
                    ownerId = bookId,
                    type = LONG_TYPE,
                    annotations = listOf(LsiAnnotation(ID)),
                    origin = bookOrigin,
                ),
            ),
        )
        val firstArtifacts = firstWorkspace.draftArtifacts(bookId)
        val generatedDraftSource = source(
            path = "build/generated/source/demo/BookDraft.java",
            kind = LsiSourceKind.GENERATED,
        )
        val secondWorkspace = firstWorkspace.merge(
            LsiWorkspace(
                sources = listOf(generatedDraftSource),
                declarations = listOf(
                    plainType(
                        id = typeId("demo.BookDraft"),
                        origin = origin(generatedDraftSource, setOf(bookId)),
                    )
                ),
            )
        )
        val secondArtifacts = secondWorkspace.draftArtifacts(bookId)

        assertEquals(
            firstArtifacts.map(LsiPoetArtifact::aggregationMode),
            secondArtifacts.map(LsiPoetArtifact::aggregationMode),
        )
        assertEquals(
            firstArtifacts.map(LsiPoetArtifact::dependencySources),
            secondArtifacts.map(LsiPoetArtifact::dependencySources),
        )
        assertTrue(secondArtifacts.all { artifact ->
            generatedDraftSource !in artifact.dependencySources
        })
    }

    private fun LsiWorkspace.draftArtifacts(typeId: LsiSymbolId): List<LsiPoetArtifact> {
        val schema = toImmutableSchema()
        val draftSchema = JimmerImmutableDraftCodegenPrecompiler().compile(
            schema = schema,
            workspace = this,
            options = JimmerImmutableDraftCodegenOptions.DEFAULT,
        )
        val type = draftSchema.typesById.getValue(typeId)
        return listOf(LsiLanguage.JAVA, LsiLanguage.KOTLIN).map { language ->
            schema.toDraftPoetArtifacts(
                draftSchema = draftSchema,
                types = listOf(type),
                language = language,
                workspace = this,
            ).single()
        }
    }

    private fun assertGeneratedArtifactBoundary(artifact: LsiPoetArtifact) {
        val content = "generated"
        val generated = artifact.generatedArtifact(content)

        assertEquals(artifact.kind, generated.kind)
        assertEquals(artifact.path, generated.path)
        assertEquals(content, generated.content)
        assertEquals(artifact.aggregationMode, generated.aggregationMode)
        assertEquals(artifact.emissionMode, generated.emissionMode)
        assertEquals(artifact.originatingSymbols, generated.originatingSymbols)
        assertEquals(artifact.originatingSources, generated.originatingSources)
        assertEquals(artifact.dependencySymbols, generated.dependencySymbols)
        assertEquals(artifact.dependencySources, generated.dependencySources)
    }

    private fun immutableType(
        id: LsiSymbolId,
        memberIds: List<LsiSymbolId> = emptyList(),
        marker: LsiSymbolId = ENTITY,
        superTypeIds: List<LsiSymbolId> = emptyList(),
        origin: LsiOrigin,
    ): LsiTypeDeclaration {
        val qualifiedName = id.requireTypeQualifiedName()
        return LsiTypeDeclaration(
            id = id,
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.INTERFACE,
            superTypes = superTypeIds.map { superTypeId -> LsiDeclaredType(superTypeId) },
            memberIds = memberIds,
            annotations = listOf(LsiAnnotation(marker)),
            origin = origin,
        )
    }

    private fun plainType(
        id: LsiSymbolId,
        origin: LsiOrigin,
    ): LsiTypeDeclaration {
        val qualifiedName = id.requireTypeQualifiedName()
        return LsiTypeDeclaration(
            id = id,
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.CLASS,
            origin = origin,
        )
    }

    private fun property(
        id: LsiSymbolId,
        ownerId: LsiSymbolId,
        type: LsiTypeRef,
        annotations: List<LsiAnnotation> = emptyList(),
        origin: LsiOrigin,
    ): LsiProperty {
        return LsiProperty(
            id = id,
            name = id.value.substringAfterLast(':'),
            ownerId = ownerId,
            type = type,
            annotations = annotations,
            origin = origin,
        )
    }

    private fun source(
        path: String,
        kind: LsiSourceKind = LsiSourceKind.SOURCE,
    ): LsiSource {
        return LsiSource.of(path, LsiLanguage.JAVA, kind)
    }

    private fun origin(
        source: LsiSource,
        originatingSymbols: Set<LsiSymbolId> = emptySet(),
    ): LsiOrigin {
        val kind = when (source.kind) {
            LsiSourceKind.SOURCE -> LsiOriginKind.SOURCE
            LsiSourceKind.GENERATED -> LsiOriginKind.GENERATED
            LsiSourceKind.BINARY -> LsiOriginKind.BINARY
        }
        return LsiOrigin(
            kind = kind,
            source = source,
            originatingSymbols = originatingSymbols,
        )
    }

    private fun typeId(qualifiedName: String): LsiSymbolId {
        return LsiSymbolId.type(qualifiedName)
    }

    private companion object {
        val LONG_TYPE = LsiPrimitiveType(LsiPrimitiveKind.LONG)
        val ENTITY = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
        val MAPPED_SUPERCLASS = LsiSymbolId.type("org.babyfish.jimmer.sql.MappedSuperclass")
        val ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Id")
    }
}
