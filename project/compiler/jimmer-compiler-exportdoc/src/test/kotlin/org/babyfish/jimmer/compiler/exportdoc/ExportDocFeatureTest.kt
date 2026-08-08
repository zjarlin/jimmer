package org.babyfish.jimmer.compiler.exportdoc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.compiler.CompilerRound
import site.addzero.lsi.compiler.CompilerSession
import site.addzero.lsi.compiler.CompilerFeatureLoader
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiFileAnnotationScope
import site.addzero.lsi.model.LsiPackageAnnotationScope
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class ExportDocFeatureTest {

    @Test
    fun `registered export doc feature is independent`() {
        val feature = CompilerFeatureLoader.load()
            .single { candidate -> candidate.key == ExportDocFeature.Key }

        assertTrue(feature.dependencies.isEmpty())
    }

    @Test
    fun `source filters and buddy resource option do not disable export docs`() {
        val type = type("demo.Book", sourceDocumentation = "Book docs.")
        val scope = packageScope("demo")
        val workspace = LsiWorkspace(
            declarations = listOf(type),
            annotationScopes = listOf(scope),
        )
        val session = CompilerSession(
            id = "export-doc-options",
            features = listOf(ExportDocFeature()),
        )

        val sourceRound = session.execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                currentRootTypeIds = setOf(type.id),
                options = mapOf(
                    "jimmer.source.includes" to "other",
                    "jimmer.source.excludes" to "demo",
                    "jimmer.buddy.ignoreResourceGeneration" to "true",
                ),
            )
        )
        val sourceFeature = sourceRound.featureResults.getValue(ExportDocFeature.Key)
        val sourceState = sourceFeature.state
        assertEquals(ExportDocFeatureStatus.RESOLVED, sourceState.status)
        assertEquals(listOf(type.id), sourceState.schema.exportedTypeIds)
        assertEquals(setOf(type.id), sourceFeature.processedSymbols)
        assertTrue(sourceFeature.artifacts.isEmpty())

        val finalRound = session.execute(
            round(
                number = 1,
                workspace = workspace,
                currentWorkspace = LsiWorkspace.EMPTY,
                currentRootTypeIds = emptySet(),
                options = mapOf(
                    "jimmer.source.includes" to "other",
                    "jimmer.source.excludes" to "demo",
                    "jimmer.buddy.ignoreResourceGeneration" to "true",
                ),
                isFinal = true,
            )
        )
        val artifact = requireNotNull(
            finalRound.featureResults[ExportDocFeature.Key]?.artifacts?.singleOrNull()
        )
        assertEquals(EXPORT_DOC_RESOURCE_PATH, artifact.path)
    }

    @Test
    fun `conflicting package configurations produce an anchored diagnostic`() {
        val packageScope = packageScope("demo")
        val fileScope = LsiFileAnnotationScope(
            packageName = "demo",
            logicalPath = "Exports.kt",
            annotations = listOf(LsiAnnotation(EXPORT_DOC)),
            origin = origin("src/main/kotlin/demo/Exports.kt", LsiLanguage.KOTLIN),
        )
        val workspace = LsiWorkspace(annotationScopes = listOf(fileScope, packageScope))
        val session = CompilerSession(
            id = "export-doc-conflict",
            features = listOf(ExportDocFeature()),
        )

        val result = session.execute(
            round(
                number = 0,
                workspace = workspace,
                currentWorkspace = workspace,
                currentRootTypeIds = emptySet(),
            )
        )
        val feature = result.featureResults.getValue(ExportDocFeature.Key)
        val state = feature.state

        assertEquals(ExportDocFeatureStatus.INVALID, state.status)
        assertEquals(listOf(packageScope.id, fileScope.id).sorted(), state.failures.single().configurationIds)
        assertEquals("jimmer.export-doc.invalid", feature.diagnostics.single().code)
        assertEquals(state.failures.single().configurationIds.first(), feature.diagnostics.single().symbolId)
    }

    private fun round(
        number: Int,
        workspace: LsiWorkspace,
        currentWorkspace: LsiWorkspace,
        currentRootTypeIds: Set<LsiSymbolId>,
        options: Map<String, String> = emptyMap(),
        isFinal: Boolean = false,
    ): CompilerRound {
        return CompilerRound(
            number = number,
            workspace = workspace,
            currentWorkspace = currentWorkspace,
            currentRootTypeIds = currentRootTypeIds,
            platform = CompilerPlatform.APT,
            isFinal = isFinal,
            options = options,
            inputDocumentSnapshots = emptyList(),
        )
    }

    private fun packageScope(packageName: String): LsiPackageAnnotationScope {
        return LsiPackageAnnotationScope(
            packageName = packageName,
            annotations = listOf(LsiAnnotation(EXPORT_DOC)),
            origin = origin("src/main/java/${packageName.replace('.', '/')}/package-info.java", LsiLanguage.JAVA),
        )
    }

    private fun type(
        qualifiedName: String,
        sourceDocumentation: String?,
    ): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = LsiSymbolId.type(qualifiedName),
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.CLASS,
            sourceDocumentation = sourceDocumentation,
            origin = origin(
                "src/main/kotlin/${qualifiedName.replace('.', '/')}.kt",
                LsiLanguage.KOTLIN,
            ),
        )
    }

    private fun origin(path: String, language: LsiLanguage): LsiOrigin {
        return LsiOrigin(
            kind = LsiOriginKind.SOURCE,
            source = LsiSource.of(path, language),
        )
    }

    private companion object {
        val EXPORT_DOC = LsiSymbolId.type("org.babyfish.jimmer.client.ExportDoc")
    }
}
