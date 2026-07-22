package site.addzero.lsi.jimmer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeArgument

class ImmutableSchemaExtensionsTest {

    @Test
    fun `resolves concrete and generic association semantics`() {
        val authorId = LsiSymbolId.type("demo.Author")
        val authorIdProp = prop(
            ownerTypeId = authorId,
            name = "id",
            primaryMapping = PrimaryMapping.ID,
        )
        val author = type(
            id = authorId,
            kind = ImmutableTypeKind.ENTITY,
            props = listOf(authorIdProp),
            idPropId = authorIdProp.id,
        )
        val bookId = LsiSymbolId.type("demo.Book")
        val authorProp = prop(
            ownerTypeId = bookId,
            name = "author",
            type = LsiDeclaredType(authorId),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            associationKind = AssociationKind.MANY_TO_ONE,
            targetTypeId = authorId,
        )
        val book = type(bookId, ImmutableTypeKind.IMMUTABLE, listOf(authorProp))
        val genericOwnerId = LsiSymbolId.type("demo.GenericOwner")
        val genericTargetProp = prop(
            ownerTypeId = genericOwnerId,
            name = "target",
            primaryMapping = PrimaryMapping.ASSOCIATION,
            associationKind = AssociationKind.MANY_TO_ONE,
            genericTarget = true,
        )
        val genericOwner = type(
            genericOwnerId,
            ImmutableTypeKind.MAPPED_SUPERCLASS,
            listOf(genericTargetProp),
        )
        val schema = ImmutableSchema(listOf(author, book, genericOwner))

        assertSame(author, schema.targetTypeOf(authorProp))
        assertSame(authorIdProp, schema.targetIdPropOf(authorProp))
        assertTrue(schema.isEntityAssociation(authorProp))
        assertTrue(schema.isConcreteEntityAssociation(authorProp))
        assertTrue(schema.isImmutableReference(authorProp))
        assertNull(schema.targetTypeOf(genericTargetProp))
        assertTrue(schema.isEntityAssociation(genericTargetProp))
        assertFalse(schema.isConcreteEntityAssociation(genericTargetProp))
    }

    @Test
    fun `exposes stable property primitives without metadata wrappers`() {
        val ownerId = LsiSymbolId.type("demo.Book")
        val rootPropId = LsiSymbolId.property(LsiSymbolId.type("demo.Base"), "name")
        val annotationId = LsiSymbolId.type("demo.Marker")
        val scalarProp = prop(
            ownerTypeId = ownerId,
            name = "name",
            annotations = listOf(LsiAnnotation(annotationId)),
            overrideChain = listOf(rootPropId),
        )
        val elementType = LsiDeclaredType(LsiSymbolId.type("demo.Author"))
        val listProp = prop(
            ownerTypeId = ownerId,
            name = "authors",
            type = LsiDeclaredType(
                declarationId = LsiSymbolId.type("java.util.List"),
                arguments = listOf(LsiTypeArgument.invariant(elementType)),
            ),
            list = true,
        )

        assertTrue(scalarProp.hasAnnotation(annotationId))
        assertEquals(rootPropId, scalarProp.lineageRootId())
        assertSame(scalarProp.type, scalarProp.elementTypeOrSelf())
        assertEquals(elementType, listProp.elementTypeOrSelf())
    }
}

private fun type(
    id: LsiSymbolId,
    kind: ImmutableTypeKind,
    props: List<ImmutableProp>,
    idPropId: LsiSymbolId? = null,
): ImmutableType {
    return ImmutableType(
        id = id,
        qualifiedName = id.value,
        kind = kind,
        documentation = null,
        annotations = emptyList(),
        typeParameterIds = emptyList(),
        superTypeIds = emptyList(),
        props = props,
        primarySuperTypeId = null,
        inheritanceRootTypeId = null,
        inheritanceStrategy = null,
        joinedTableDissociateAction = null,
        instantiable = kind == ImmutableTypeKind.ENTITY,
        discriminatorValue = null,
        discriminatorPropId = null,
        idPropId = idPropId,
        versionPropId = null,
        logicalDeletedPropId = null,
        acrossMicroServices = false,
        microServiceName = "",
    )
}

private fun prop(
    ownerTypeId: LsiSymbolId,
    name: String,
    type: LsiDeclaredType = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
    annotations: List<LsiAnnotation> = emptyList(),
    overrideChain: List<LsiSymbolId> = emptyList(),
    list: Boolean = false,
    primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
    associationKind: AssociationKind = AssociationKind.NONE,
    targetTypeId: LsiSymbolId? = null,
    genericTarget: Boolean = false,
): ImmutableProp {
    val id = LsiSymbolId.property(ownerTypeId, name)
    return ImmutableProp(
        id = id,
        declarationId = id,
        ownerTypeId = ownerTypeId,
        declaringTypeId = ownerTypeId,
        name = name,
        documentation = null,
        type = type,
        annotations = annotations,
        overrideChain = overrideChain,
        inherited = false,
        overridden = overrideChain.isNotEmpty(),
        nullable = false,
        list = list,
        association = associationKind != AssociationKind.NONE,
        embedded = false,
        targetTypeId = targetTypeId,
        primaryMapping = primaryMapping,
        primaryAnnotationTypeId = null,
        defaultContract = null,
        associationKind = associationKind,
        formulaKind = FormulaKind.NONE,
        mappedBy = null,
        associationStorage = AssociationStorageKind.NONE,
        transientResolver = null,
        view = null,
        genericTarget = genericTarget,
        remote = false,
        recursive = false,
        validations = emptyList(),
        converter = null,
    )
}
