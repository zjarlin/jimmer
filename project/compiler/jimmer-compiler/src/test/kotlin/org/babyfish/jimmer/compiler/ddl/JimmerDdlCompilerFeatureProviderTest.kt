package org.babyfish.jimmer.compiler.ddl

import kotlin.test.Test
import kotlin.test.assertEquals
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerRound
import org.babyfish.jimmer.compiler.CompilerSessionSnapshot
import org.babyfish.jimmer.compiler.JimmerCompilerCollectContext
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class JimmerDdlCompilerFeatureProviderTest {

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

        val collection = JimmerDdlCompilerFeatureProvider().collect(
            JimmerCompilerCollectContext(
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
