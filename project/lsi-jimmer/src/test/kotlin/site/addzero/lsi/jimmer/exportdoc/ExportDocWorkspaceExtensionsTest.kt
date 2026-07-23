package site.addzero.lsi.jimmer.exportdoc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFileAnnotationScope
import site.addzero.lsi.model.LsiPackageAnnotationScope
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace

class ExportDocWorkspaceExtensionsTest {

    @Test
    fun `package and file configurations use nearest package while type overrides and nested types inherit`() {
        val demoScope = packageScope("demo", exported = true)
        val internalScope = fileScope("demo.internal", "package.kt", exported = false)
        val book = type("demo.Book", sourceDocumentation = "Book.")
        val hidden = type("demo.internal.Hidden", sourceDocumentation = "Hidden.")
        val forced = type(
            qualifiedName = "demo.internal.Forced",
            annotations = listOf(exportDoc()),
            sourceDocumentation = "Forced.",
        )
        val outer = type(
            qualifiedName = "demo.internal.Outer",
            annotations = listOf(exportDoc()),
            sourceDocumentation = "Outer.",
        )
        val nested = type(
            qualifiedName = "demo.internal.Outer.Nested",
            enclosingTypeId = outer.id,
            sourceDocumentation = "Nested.",
        )
        val secret = type(
            qualifiedName = "demo.internal.Outer.Secret",
            enclosingTypeId = outer.id,
            annotations = listOf(exportDoc(excluded = true)),
            sourceDocumentation = "Secret.",
        )

        val schema = LsiWorkspace(
            declarations = listOf(book, hidden, forced, outer, nested, secret),
            annotationScopes = listOf(demoScope, internalScope),
        ).toExportDocSchema()

        assertEquals(
            listOf(demoScope.id, internalScope.id, forced.id, outer.id, secret.id).sorted(),
            schema.effectiveConfigurationIds,
        )
        assertEquals(
            listOf(book.id, forced.id, outer.id, nested.id).sorted(),
            schema.exportedTypeIds,
        )
        assertEquals(
            listOf("demo.Book", "demo.internal.Forced", "demo.internal.Outer", "demo.internal.Outer.Nested"),
            schema.entries.map(ExportDocEntry::key),
        )
        assertFalse(schema.entries.any { entry -> entry.key.endsWith("Hidden") || entry.key.endsWith("Secret") })
    }

    @Test
    fun `duplicate package configurations fail deterministically`() {
        val packageScope = packageScope("demo", exported = true)
        val fileScope = fileScope("demo", "Exports.kt", exported = false)

        val exception = assertFailsWith<ExportDocValidationException> {
            LsiWorkspace(annotationScopes = listOf(fileScope, packageScope)).toExportDocSchema()
        }

        assertEquals(listOf(packageScope.id, fileScope.id).sorted(), exception.scopeIds)
        assertEquals(
            "Conflicting @ExportDoc configurations for package 'demo': " +
                exception.scopeIds.joinToString { scopeId -> scopeId.value },
            exception.message,
        )
    }

    @Test
    fun `exports only source and generated class interface and enum declarations`() {
        val scope = packageScope("demo", exported = true)
        val descriptionOnlyProperty = property(
            ownerId = LsiSymbolId.type("demo.SourceType"),
            name = "descriptionOnly",
            language = LsiLanguage.KOTLIN,
            documentation = "Description fallback.",
            annotations = listOf(description("Description annotation.")),
        )
        val sourceType = type(
            qualifiedName = "demo.SourceType",
            memberIds = listOf(descriptionOnlyProperty.id),
            documentation = "Description fallback.",
        )
        val generatedType = type(
            qualifiedName = "demo.GeneratedType",
            kind = LsiTypeDeclarationKind.INTERFACE,
            originKind = LsiOriginKind.GENERATED,
            sourceDocumentation = "Generated docs.",
        )
        val binaryType = type(
            qualifiedName = "demo.BinaryType",
            kind = LsiTypeDeclarationKind.ENUM,
            originKind = LsiOriginKind.BINARY,
            sourceDocumentation = "Binary docs.",
        )
        val objectType = type(
            qualifiedName = "demo.Singleton",
            kind = LsiTypeDeclarationKind.OBJECT,
            sourceDocumentation = "Object docs.",
        )
        val annotationType = type(
            qualifiedName = "demo.Marker",
            kind = LsiTypeDeclarationKind.ANNOTATION,
            sourceDocumentation = "Annotation docs.",
        )
        val nestedInObject = type(
            qualifiedName = "demo.Singleton.Nested",
            enclosingTypeId = objectType.id,
            annotations = listOf(exportDoc()),
            sourceDocumentation = "Nested object member docs.",
        )
        val nestedInAnnotation = type(
            qualifiedName = "demo.Marker.Nested",
            enclosingTypeId = annotationType.id,
            annotations = listOf(exportDoc()),
            sourceDocumentation = "Nested annotation member docs.",
        )

        val schema = LsiWorkspace(
            declarations = listOf(
                sourceType,
                descriptionOnlyProperty,
                generatedType,
                binaryType,
                objectType,
                annotationType,
                nestedInObject,
                nestedInAnnotation,
            ),
            annotationScopes = listOf(scope),
        ).toExportDocSchema()

        assertEquals(listOf(generatedType.id, sourceType.id).sorted(), schema.exportedTypeIds)
        assertEquals(listOf("demo.GeneratedType"), schema.entries.map(ExportDocEntry::key))
    }

    @Test
    fun `java fields are written first and getters overwrite while kotlin properties retain names`() {
        val typeId = LsiSymbolId.type("demo.Model")
        val javaField = field(
            ownerId = typeId,
            name = "name",
            sourceDocumentation = "Field name.",
        )
        val javaGetter = property(
            ownerId = typeId,
            name = "name",
            getterName = "getName",
            language = LsiLanguage.JAVA,
            sourceDocumentation = "Getter name.",
        )
        val javaBooleanGetter = property(
            ownerId = typeId,
            name = "enabled",
            getterName = "isEnabled",
            language = LsiLanguage.JAVA,
            type = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
            sourceDocumentation = "Enabled.",
        )
        val kotlinIsProperty = property(
            ownerId = typeId,
            name = "isReady",
            getterName = "isReady",
            language = LsiLanguage.KOTLIN,
            type = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
            sourceDocumentation = "Ready.",
        )
        val model = type(
            qualifiedName = "demo.Model",
            annotations = listOf(exportDoc()),
            memberIds = listOf(
                javaGetter.id,
                kotlinIsProperty.id,
                javaField.id,
                javaBooleanGetter.id,
            ),
            sourceDocumentation = "\r\n Model. \r\n",
        )

        val schema = LsiWorkspace(
            declarations = listOf(
                model,
                javaField,
                javaGetter,
                javaBooleanGetter,
                kotlinIsProperty,
            )
        ).toExportDocSchema()

        assertEquals(
            mapOf(
                "demo.Model" to "Model.",
                "demo.Model.enabled" to "Enabled.",
                "demo.Model.isReady" to "Ready.",
                "demo.Model.name" to "Getter name.",
            ),
            schema.entries.associate { entry -> entry.key to entry.content },
        )
        assertEquals(
            javaGetter.id,
            schema.entries.single { entry -> entry.key == "demo.Model.name" }.declarationId,
        )
    }

    private fun packageScope(
        packageName: String,
        exported: Boolean,
    ): LsiPackageAnnotationScope {
        return LsiPackageAnnotationScope(
            packageName = packageName,
            annotations = listOf(exportDoc(excluded = !exported)),
            origin = sourceOrigin("src/main/java/${packageName.replace('.', '/')}/package-info.java", LsiLanguage.JAVA),
        )
    }

    private fun fileScope(
        packageName: String,
        fileName: String,
        exported: Boolean,
    ): LsiFileAnnotationScope {
        return LsiFileAnnotationScope(
            packageName = packageName,
            logicalPath = fileName,
            annotations = listOf(exportDoc(excluded = !exported)),
            origin = sourceOrigin("src/main/kotlin/${packageName.replace('.', '/')}/$fileName", LsiLanguage.KOTLIN),
        )
    }

    private fun type(
        qualifiedName: String,
        kind: LsiTypeDeclarationKind = LsiTypeDeclarationKind.CLASS,
        enclosingTypeId: LsiSymbolId? = null,
        annotations: List<LsiAnnotation> = emptyList(),
        memberIds: List<LsiSymbolId> = emptyList(),
        documentation: String? = null,
        sourceDocumentation: String? = null,
        originKind: LsiOriginKind = LsiOriginKind.SOURCE,
    ): LsiTypeDeclaration {
        val language = LsiLanguage.KOTLIN
        return LsiTypeDeclaration(
            id = LsiSymbolId.type(qualifiedName),
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = kind,
            enclosingTypeId = enclosingTypeId,
            memberIds = memberIds,
            documentation = documentation,
            sourceDocumentation = sourceDocumentation,
            annotations = annotations,
            origin = origin(
                kind = originKind,
                path = "src/main/kotlin/${qualifiedName.substringBeforeLast('.').replace('.', '/')}/" +
                    "${qualifiedName.substringAfterLast('.')}.kt",
                language = language,
            ),
        )
    }

    private fun field(
        ownerId: LsiSymbolId,
        name: String,
        sourceDocumentation: String?,
    ): LsiField {
        return LsiField(
            id = LsiSymbolId.field(ownerId, name),
            name = name,
            ownerId = ownerId,
            type = LsiDeclaredType(STRING_TYPE),
            sourceDocumentation = sourceDocumentation,
            origin = sourceOrigin("src/main/java/demo/Model.java", LsiLanguage.JAVA),
        )
    }

    private fun property(
        ownerId: LsiSymbolId,
        name: String,
        getterName: String = name,
        language: LsiLanguage,
        type: LsiTypeRef = LsiDeclaredType(STRING_TYPE),
        documentation: String? = null,
        sourceDocumentation: String? = null,
        annotations: List<LsiAnnotation> = emptyList(),
    ): LsiProperty {
        val extension = if (language == LsiLanguage.JAVA) "java" else "kt"
        return LsiProperty(
            id = LsiSymbolId.property(ownerId, name),
            name = name,
            ownerId = ownerId,
            type = type,
            getterName = getterName,
            documentation = documentation,
            sourceDocumentation = sourceDocumentation,
            annotations = annotations,
            origin = sourceOrigin("src/main/$extension/demo/Model.$extension", language),
        )
    }

    private fun exportDoc(excluded: Boolean = false): LsiAnnotation {
        val arguments = if (excluded) {
            mapOf(
                "excluded" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.BooleanValue(true),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                )
            )
        } else {
            emptyMap()
        }
        return LsiAnnotation(EXPORT_DOC, arguments)
    }

    private fun description(value: String): LsiAnnotation {
        return LsiAnnotation(
            type = DESCRIPTION,
            arguments = mapOf(
                "value" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.StringValue(value),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                )
            ),
        )
    }

    private fun sourceOrigin(
        path: String,
        language: LsiLanguage,
    ): LsiOrigin = origin(LsiOriginKind.SOURCE, path, language)

    private fun origin(
        kind: LsiOriginKind,
        path: String,
        language: LsiLanguage,
    ): LsiOrigin {
        val sourceKind = when (kind) {
            LsiOriginKind.SOURCE -> LsiSourceKind.SOURCE
            LsiOriginKind.GENERATED -> LsiSourceKind.GENERATED
            LsiOriginKind.BINARY -> LsiSourceKind.BINARY
            LsiOriginKind.SYNTHETIC -> LsiSourceKind.SOURCE
        }
        return LsiOrigin(
            kind = kind,
            source = LsiSource.of(path, language, sourceKind),
        )
    }

    private companion object {
        val EXPORT_DOC = LsiSymbolId.type("org.babyfish.jimmer.client.ExportDoc")
        val DESCRIPTION = LsiSymbolId.type("org.babyfish.jimmer.client.Description")
        val STRING_TYPE = LsiSymbolId.type("java.lang.String")
    }
}
