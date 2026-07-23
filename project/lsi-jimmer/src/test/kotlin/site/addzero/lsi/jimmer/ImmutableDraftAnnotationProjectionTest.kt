package site.addzero.lsi.jimmer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class ImmutableDraftAnnotationProjectionTest {

    @Test
    fun `projects only annotations allowed on methods`() {
        val allowed = annotationType(ALLOWED, "METHOD")
        val forbidden = annotationType(FORBIDDEN, "FIELD")
        val prop = projectionProp(
            annotations = listOf(
                LsiAnnotation(ALLOWED, useSiteTarget = LsiAnnotationUseSiteTarget.GETTER),
                LsiAnnotation(FORBIDDEN, useSiteTarget = LsiAnnotationUseSiteTarget.PROPERTY),
                LsiAnnotation(NULLABLE, useSiteTarget = LsiAnnotationUseSiteTarget.GETTER),
                LsiAnnotation(JIMMER_ANNOTATION),
                LsiAnnotation(USER_EXCLUDED),
            ),
        )
        val projection = prop.toDraftAnnotationProjection(
            workspace = LsiWorkspace(
                declarations = listOf(allowed, forbidden, annotationType(NULLABLE, "METHOD")),
            ),
            excludedUserAnnotationPrefixes = listOf(" demo.internal. "),
        )

        assertEquals(listOf(ALLOWED), projection.builderMethodAnnotations.map(LsiAnnotation::type))
        assertEquals(listOf(ALLOWED, NULLABLE), projection.methodAnnotations.map(LsiAnnotation::type))
        assertTrue(projection.builderMethodAnnotations.all { annotation -> annotation.useSiteTarget == null })
        assertTrue(projection.methodAnnotations.all { annotation -> annotation.useSiteTarget == null })
    }

    @Test
    fun `orders equivalent projections by use-site priority`() {
        val annotations = listOf(
            marker(ALL_MARKER, LsiAnnotationUseSiteTarget.ALL, "all"),
            marker(PLAIN_MARKER, null, "plain"),
            marker(PROPERTY_MARKER, LsiAnnotationUseSiteTarget.PROPERTY, "property"),
            marker(METHOD_MARKER, LsiAnnotationUseSiteTarget.METHOD, "method"),
            marker(GETTER_MARKER, LsiAnnotationUseSiteTarget.GETTER, "getter"),
        )
        val projection = projectionProp(annotations).toDraftAnnotationProjection(
            LsiWorkspace(
                declarations = listOf(
                    annotationType(ALL_MARKER, "METHOD"),
                    annotationType(PLAIN_MARKER, "METHOD"),
                    annotationType(PROPERTY_MARKER, "METHOD"),
                    annotationType(METHOD_MARKER, "METHOD"),
                    annotationType(GETTER_MARKER, "METHOD"),
                ),
            ),
        )

        assertEquals(
            listOf("getter", "method", "property", "all", "plain"),
            projection.methodAnnotations.map { annotation ->
                (annotation.arguments.getValue("value").value as LsiAnnotationValue.StringValue).value
            },
        )
    }
}

private fun marker(
    type: LsiSymbolId,
    target: LsiAnnotationUseSiteTarget?,
    value: String,
): LsiAnnotation {
    return LsiAnnotation(
        type = type,
        arguments = mapOf(
            "value" to LsiAnnotationArgument(
                value = LsiAnnotationValue.StringValue(value),
                origin = LsiAnnotationArgumentOrigin.EXPLICIT,
            ),
        ),
        useSiteTarget = target,
    )
}

private fun annotationType(id: LsiSymbolId, target: String): LsiTypeDeclaration {
    return LsiTypeDeclaration(
        id = id,
        name = id.value.substringAfterLast('.'),
        qualifiedName = id.value,
        kind = LsiTypeDeclarationKind.ANNOTATION,
        annotations = listOf(
            LsiAnnotation(
                type = JAVA_TARGET,
                arguments = mapOf(
                    "value" to LsiAnnotationArgument(
                        value = LsiAnnotationValue.ArrayValue(
                            listOf(LsiAnnotationValue.EnumValue(JAVA_ELEMENT_TYPE, target)),
                        ),
                        origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                    ),
                ),
            ),
        ),
        origin = SYNTHETIC_ORIGIN,
    )
}

private fun projectionProp(annotations: List<LsiAnnotation>): ImmutableProp {
    val id = LsiSymbolId.property(OWNER, "name")
    return ImmutableProp(
        id = id,
        declarationId = id,
        ownerTypeId = OWNER,
        declaringTypeId = OWNER,
        name = "name",
        documentation = null,
        type = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
        annotations = annotations,
        overrideChain = emptyList(),
        inherited = false,
        overridden = false,
        nullable = true,
        list = false,
        association = false,
        embedded = false,
        targetTypeId = null,
        primaryMapping = PrimaryMapping.SCALAR,
        primaryAnnotationTypeId = null,
        defaultContract = null,
        associationKind = AssociationKind.NONE,
        formulaKind = FormulaKind.NONE,
        mappedBy = null,
        associationStorage = AssociationStorageKind.NONE,
        transientResolver = null,
        view = null,
        genericTarget = false,
        remote = false,
        recursive = false,
        validations = emptyList(),
        converter = null,
    )
}

private val OWNER = LsiSymbolId.type("demo.Book")
private val ALLOWED = LsiSymbolId.type("demo.Allowed")
private val FORBIDDEN = LsiSymbolId.type("demo.Forbidden")
private val ALL_MARKER = LsiSymbolId.type("demo.AllMarker")
private val PLAIN_MARKER = LsiSymbolId.type("demo.PlainMarker")
private val PROPERTY_MARKER = LsiSymbolId.type("demo.PropertyMarker")
private val METHOD_MARKER = LsiSymbolId.type("demo.MethodMarker")
private val GETTER_MARKER = LsiSymbolId.type("demo.GetterMarker")
private val NULLABLE = LsiSymbolId.type("org.jetbrains.annotations.Nullable")
private val JIMMER_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Column")
private val USER_EXCLUDED = LsiSymbolId.type("demo.internal.Secret")
private val JAVA_TARGET = LsiSymbolId.type("java.lang.annotation.Target")
private val JAVA_ELEMENT_TYPE = LsiSymbolId.type("java.lang.annotation.ElementType")
private val SYNTHETIC_ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
