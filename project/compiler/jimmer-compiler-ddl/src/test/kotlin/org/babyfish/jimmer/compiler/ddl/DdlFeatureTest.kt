package org.babyfish.jimmer.compiler.ddl

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.compiler.CompilerRound
import site.addzero.lsi.compiler.CompilerSessionSnapshot
import site.addzero.lsi.compiler.CompilerCollectContext
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class DdlFeatureTest {

    @Test
    fun `collect ignores current workspace entities without a source`() {
        val source = LsiSource.of("src/main/java/demo/LocalEntity.java", LsiLanguage.JAVA)
        val localEntity = entity(
            qualifiedName = "demo.LocalEntity",
            origin = LsiOrigin(LsiOriginKind.SOURCE, source),
        )
        val classpathEntity = entity(
            qualifiedName = "external.ClasspathEntity",
            origin = LsiOrigin(LsiOriginKind.BINARY),
        )
        val workspace = LsiWorkspace(
            sources = listOf(source),
            declarations = listOf(localEntity, classpathEntity),
        )

        val feature = DdlFeature()
        val collection = feature.collect(
            CompilerCollectContext(
                session = CompilerSessionSnapshot("ddl-collect", emptyList()),
                round = CompilerRound(
                    number = 0,
                    workspace = workspace,
                    currentWorkspace = workspace,
                    currentRootTypeIds = setOf(localEntity.id),
                    platform = CompilerPlatform.APT,
                    inputDocumentSnapshots = emptyList(),
                ),
            )
        )

        assertEquals(DdlFeature.Key, feature.key)
        assertEquals(localEntity.id.value, collection.state.fingerprint)
    }

    private fun entity(
        qualifiedName: String,
        origin: LsiOrigin,
    ): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = LsiSymbolId.type(qualifiedName),
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.INTERFACE,
            annotations = listOf(LsiAnnotation(ENTITY_ANNOTATION)),
            origin = origin,
        )
    }

    private companion object {
        val ENTITY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
    }
}
