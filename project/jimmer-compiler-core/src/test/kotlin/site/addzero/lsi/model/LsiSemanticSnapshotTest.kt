package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

class LsiSemanticSnapshotTest {

    @Test
    fun `normalizes frontend-only property differences`() {
        val ownerId = LsiSymbolId.type("sample.Switch")
        val propertyId = LsiSymbolId.property(ownerId, "status")
        val annotationType = LsiSymbolId.type("org.babyfish.jimmer.sql.Default")
        val javaWorkspace = workspace(
            ownerId = ownerId,
            property = property(
                id = propertyId,
                ownerId = ownerId,
                getterName = "getStatus",
                type = LsiDeclaredType(
                    declarationId = LsiSymbolId.type("java.lang.Integer"),
                    nullability = LsiNullability.PLATFORM,
                ),
                annotation = annotation(annotationType, LsiAnnotationUseSiteTarget.METHOD),
            ),
        )
        val kotlinWorkspace = workspace(
            ownerId = ownerId,
            property = property(
                id = propertyId,
                ownerId = ownerId,
                getterName = "status",
                type = LsiDeclaredType(
                    declarationId = LsiSymbolId.type("java.lang.Integer"),
                    nullability = LsiNullability.NON_NULL,
                ),
                annotation = annotation(annotationType, LsiAnnotationUseSiteTarget.PROPERTY),
            ),
        )

        assertEquals(javaWorkspace.toSemanticSnapshot(), kotlinWorkspace.toSemanticSnapshot())
        assertNotEquals(
            javaWorkspace.toSemanticSnapshot(
                LsiSemanticSnapshotOptions(
                    platformNullability = LsiNullability.UNKNOWN,
                    includeAnnotationUseSiteTarget = true,
                ),
            ),
            kotlinWorkspace.toSemanticSnapshot(
                LsiSemanticSnapshotOptions(
                    platformNullability = LsiNullability.UNKNOWN,
                    includeAnnotationUseSiteTarget = true,
                ),
            ),
        )
    }

    @Test
    fun `snapshots type use annotations without changing stable signatures`() {
        val ownerId = LsiSymbolId.type("sample.Service")
        val propertyId = LsiSymbolId.property(ownerId, "books")
        val markerType = LsiSymbolId.type("sample.TypeUseMarker")
        val elementType = LsiDeclaredType(
            declarationId = LsiSymbolId.type("sample.Book"),
            annotations = listOf(annotation(markerType, null)),
        )
        val annotatedType = LsiDeclaredType(
            declarationId = LsiSymbolId.type("java.util.List"),
            arguments = listOf(LsiTypeArgument.invariant(elementType)),
        )
        val plainType = annotatedType.copy(
            arguments = listOf(
                LsiTypeArgument.invariant(elementType.copy(annotations = emptyList())),
            ),
        )
        val declarationAnnotation = annotation(
            type = LsiSymbolId.type("sample.PropertyMarker"),
            target = LsiAnnotationUseSiteTarget.METHOD,
        )
        val annotatedWorkspace = workspace(
            ownerId = ownerId,
            property = property(propertyId, ownerId, "books", annotatedType, declarationAnnotation),
        )
        val plainWorkspace = workspace(
            ownerId = ownerId,
            property = property(propertyId, ownerId, "books", plainType, declarationAnnotation),
        )

        assertEquals(plainType.stableSignature(), annotatedType.stableSignature())
        assertNotEquals(plainWorkspace.toSemanticSnapshot(), annotatedWorkspace.toSemanticSnapshot())
        assertTrue(
            annotatedWorkspace.toSemanticSnapshot().contains(
                "type:sample.Book:non_null@[type:sample.TypeUseMarker(value=EXPLICIT:string:1)]",
            ),
        )
    }

    @Test
    fun `boxed primitive representation contributes to stable and semantic signatures`() {
        val ownerId = LsiSymbolId.type("sample.Service")
        val propertyId = LsiSymbolId.property(ownerId, "count")
        val rawType = LsiPrimitiveType(LsiPrimitiveKind.INT)
        val boxedType = rawType.copy(boxed = true)

        assertNotEquals(rawType.stableSignature(), boxedType.stableSignature())
        assertTrue(
            workspace(
                ownerId = ownerId,
                property = property(
                    propertyId,
                    ownerId,
                    "count",
                    boxedType,
                    annotation(LsiSymbolId.type("sample.Marker"), null),
                ),
            ).toSemanticSnapshot().contains("primitive:int:boxed:non_null"),
        )
    }

    @Test
    fun `resolves transitive originating sources`() {
        val generatedTypeId = LsiSymbolId.type("sample.Generated")
        val sourceTypeId = LsiSymbolId.type("sample.Source")
        val source = LsiSource.of("src/main/kotlin/sample/Source.kt")
        val workspace = LsiWorkspace(
            declarations = listOf(
                LsiTypeDeclaration(
                    id = sourceTypeId,
                    name = "Source",
                    qualifiedName = "sample.Source",
                    kind = LsiTypeDeclarationKind.INTERFACE,
                    origin = LsiOrigin(LsiOriginKind.SOURCE, source),
                ),
                LsiTypeDeclaration(
                    id = generatedTypeId,
                    name = "Generated",
                    qualifiedName = "sample.Generated",
                    kind = LsiTypeDeclarationKind.INTERFACE,
                    origin = LsiOrigin(
                        kind = LsiOriginKind.GENERATED,
                        originatingSymbols = setOf(sourceTypeId),
                    ),
                ),
            ),
        )

        assertEquals(setOf(source), workspace.originatingSources(setOf(generatedTypeId)))
    }

    @Test
    fun `normalizes constructor parameters and snapshots fields`() {
        val ownerId = LsiSymbolId.type("sample.Model")
        val stringId = LsiSymbolId.type("java.lang.String")
        val constructorId = LsiSymbolId.constructor(ownerId, listOf("type:java.lang.String"))
        val fieldId = LsiSymbolId.field(ownerId, "VERSION")
        val javaWorkspace = declarationWorkspace(
            ownerId = ownerId,
            field = LsiField(
                id = fieldId,
                name = "VERSION",
                ownerId = ownerId,
                type = LsiDeclaredType(stringId, nullability = LsiNullability.PLATFORM),
                static = true,
                visibility = LsiVisibility.PRIVATE,
                origin = ORIGIN,
            ),
            constructor = constructor(
                id = constructorId,
                ownerId = ownerId,
                type = LsiDeclaredType(stringId, nullability = LsiNullability.PLATFORM),
            ),
        )
        val kotlinWorkspace = declarationWorkspace(
            ownerId = ownerId,
            field = LsiField(
                id = fieldId,
                name = "VERSION",
                ownerId = ownerId,
                type = LsiDeclaredType(stringId, nullability = LsiNullability.NON_NULL),
                static = true,
                visibility = LsiVisibility.PRIVATE,
                origin = ORIGIN,
            ),
            constructor = constructor(
                id = constructorId,
                ownerId = ownerId,
                type = LsiDeclaredType(stringId, nullability = LsiNullability.NON_NULL),
            ),
        )

        val snapshot = javaWorkspace.toSemanticSnapshot()
        assertEquals(snapshot, kotlinWorkspace.toSemanticSnapshot())
        assertTrue(snapshot.contains("field|${fieldId.value}|VERSION|${ownerId.value}"))
        assertTrue(snapshot.contains("constructor|${constructorId.value}|${ownerId.value}"))
        assertTrue(snapshot.contains("${constructorId.value}/parameter:0:value:value:0"))
    }

    @Test
    fun `snapshots enclosing type and data class semantics`() {
        val outerId = LsiSymbolId.type("sample.Outer")
        val nestedId = LsiSymbolId.type("sample.Outer.Row")
        val dataWorkspace = LsiWorkspace(
            declarations = listOf(
                LsiTypeDeclaration(
                    id = outerId,
                    name = "Outer",
                    qualifiedName = "sample.Outer",
                    kind = LsiTypeDeclarationKind.CLASS,
                    origin = ORIGIN,
                ),
                LsiTypeDeclaration(
                    id = nestedId,
                    name = "Row",
                    qualifiedName = "sample.Outer.Row",
                    kind = LsiTypeDeclarationKind.CLASS,
                    enclosingTypeId = outerId,
                    requiresEnclosingInstance = true,
                    abstractDeclaration = false,
                    dataClass = true,
                    origin = ORIGIN,
                ),
            ),
        )
        val plainWorkspace = LsiWorkspace(
            declarations = dataWorkspace.declarations.map { declaration ->
                if (declaration is LsiTypeDeclaration && declaration.id == nestedId) {
                    declaration.copy(
                        enclosingTypeId = null,
                        requiresEnclosingInstance = false,
                        abstractDeclaration = false,
                        dataClass = false,
                    )
                } else {
                    declaration
                }
            },
        )

        val snapshot = dataWorkspace.toSemanticSnapshot()
        assertNotEquals(snapshot, plainWorkspace.toSemanticSnapshot())
        assertTrue(snapshot.contains("type|${nestedId.value}|Row|sample.Outer.Row|CLASS|${outerId.value}|true|false|true|"))
    }

    @Test
    fun `snapshots package and file annotation scopes`() {
        val packageMarker = annotation(
            type = LsiSymbolId.type("sample.PackageMarker"),
            target = LsiAnnotationUseSiteTarget.PACKAGE,
        )
        val fileMarker = annotation(
            type = LsiSymbolId.type("sample.FileMarker"),
            target = LsiAnnotationUseSiteTarget.FILE,
        )
        val workspace = LsiWorkspace(
            annotationScopes = listOf(
                LsiFileAnnotationScope(
                    packageName = "sample",
                    logicalPath = "generated/Model.kt",
                    annotations = listOf(fileMarker),
                    origin = ORIGIN,
                ),
                LsiPackageAnnotationScope(
                    packageName = "sample",
                    annotations = listOf(packageMarker),
                    origin = ORIGIN,
                ),
            ),
        )

        val snapshot = workspace.toSemanticSnapshot(
            LsiSemanticSnapshotOptions(includeAnnotationUseSiteTarget = true),
        )

        assertEquals(
            "annotation-scope|package-scope:named:sample|PACKAGE|sample||" +
                "type:sample.PackageMarker@PACKAGE(value=EXPLICIT:string:1)\n" +
            "annotation-scope|package-scope:named:sample/file:generated%2FModel.kt|FILE|sample|generated/Model.kt|" +
                "type:sample.FileMarker@FILE(value=EXPLICIT:string:1)\n",
            snapshot,
        )
    }

    private fun workspace(
        ownerId: LsiSymbolId,
        property: LsiProperty,
    ): LsiWorkspace {
        return LsiWorkspace(
            declarations = listOf(
                LsiTypeDeclaration(
                    id = ownerId,
                    name = "Switch",
                    qualifiedName = "sample.Switch",
                    kind = LsiTypeDeclarationKind.INTERFACE,
                    modality = LsiModality.ABSTRACT,
                    memberIds = listOf(property.id),
                    origin = ORIGIN,
                ),
                property,
            ),
        )
    }

    private fun property(
        id: LsiSymbolId,
        ownerId: LsiSymbolId,
        getterName: String,
        type: LsiTypeRef,
        annotation: LsiAnnotation,
    ): LsiProperty {
        return LsiProperty(
            id = id,
            name = "status",
            ownerId = ownerId,
            getterName = getterName,
            type = type,
            modality = LsiModality.ABSTRACT,
            annotations = listOf(annotation),
            origin = ORIGIN,
        )
    }

    private fun declarationWorkspace(
        ownerId: LsiSymbolId,
        field: LsiField,
        constructor: LsiConstructor,
    ): LsiWorkspace {
        return LsiWorkspace(
            declarations = listOf(
                LsiTypeDeclaration(
                    id = ownerId,
                    name = "Model",
                    qualifiedName = "sample.Model",
                    kind = LsiTypeDeclarationKind.CLASS,
                    memberIds = listOf(field.id, constructor.id),
                    origin = ORIGIN,
                ),
                field,
                constructor,
            ),
        )
    }

    private fun constructor(
        id: LsiSymbolId,
        ownerId: LsiSymbolId,
        type: LsiTypeRef,
    ): LsiConstructor {
        return LsiConstructor(
            id = id,
            ownerId = ownerId,
            parameters = listOf(
                LsiParameter(
                    id = LsiSymbolId.parameter(id, 0, "value"),
                    name = "value",
                    callableId = id,
                    index = 0,
                    type = type,
                    annotations = listOf(
                        annotation(
                            type = LsiSymbolId.type("sample.ParameterMarker"),
                            target = LsiAnnotationUseSiteTarget.PARAMETER,
                        ),
                    ),
                    origin = ORIGIN,
                ),
            ),
            annotations = listOf(
                annotation(
                    type = LsiSymbolId.type("sample.ConstructorMarker"),
                    target = LsiAnnotationUseSiteTarget.CONSTRUCTOR,
                ),
            ),
            origin = ORIGIN,
        )
    }

    private fun annotation(
        type: LsiSymbolId,
        target: LsiAnnotationUseSiteTarget?,
    ): LsiAnnotation {
        return LsiAnnotation(
            type = type,
            arguments = mapOf(
                "value" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.StringValue("1"),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                ),
            ),
            useSiteTarget = target,
        )
    }

    companion object {
        private val ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
    }
}
