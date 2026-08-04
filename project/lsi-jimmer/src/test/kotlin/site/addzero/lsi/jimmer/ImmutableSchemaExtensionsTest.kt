package site.addzero.lsi.jimmer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeArgument

class ImmutableSchemaExtensionsTest {

    @Test
    fun `解析不可变类型的包名和简单名`() {
        val packagedType = type(
            id = LsiSymbolId.type("Demo.API.Book"),
            kind = ImmutableTypeKind.IMMUTABLE,
            props = emptyList(),
        )
        val defaultPackageType = type(
            id = LsiSymbolId.type("Book"),
            kind = ImmutableTypeKind.IMMUTABLE,
            props = emptyList(),
        )

        assertEquals("Demo.API", packagedType.packageName)
        assertEquals("Book", packagedType.simpleName)
        assertEquals("", defaultPackageType.packageName)
        assertEquals("Book", defaultPackageType.simpleName)
    }

    @Test
    fun `resolves generated query type and property constant names`() {
        val baseTypeId = LsiSymbolId.type("demo.Base")
        val baseProp = prop(baseTypeId, "urlValue")
        val baseType = type(
            id = baseTypeId,
            kind = ImmutableTypeKind.MAPPED_SUPERCLASS,
            props = listOf(baseProp),
        )
        val bookTypeId = LsiSymbolId.type("demo.Book")
        val bookIdProp = prop(
            ownerTypeId = bookTypeId,
            name = "id",
            primaryMapping = PrimaryMapping.ID,
        )
        val inheritedProp = baseProp.copy(
            id = LsiSymbolId.property(bookTypeId, baseProp.name),
            ownerTypeId = bookTypeId,
            declaringTypeId = baseTypeId,
            overrideChain = listOf(baseProp.id),
            inherited = true,
        )
        val bookType = type(
            id = bookTypeId,
            kind = ImmutableTypeKind.ENTITY,
            props = listOf(bookIdProp, inheritedProp),
            idPropId = bookIdProp.id,
        )
        val schema = ImmutableSchema(listOf(baseType, bookType))

        assertEquals(
            LsiDeclaredType(LsiSymbolId.type("demo.BaseProps")),
            baseType.generatedPropsType(),
        )
        assertEquals(LsiDeclaredType(bookTypeId), bookType.sourceTypeRef())
        assertEquals(
            LsiDeclaredType(LsiSymbolId.type("demo.BookTable")),
            bookType.generatedTableType(),
        )
        assertEquals(
            LsiDeclaredType(LsiSymbolId.type("demo.BookDraft")),
            bookType.generatedDraftType(),
        )
        assertEquals(
            LsiDeclaredType(LsiSymbolId.type("demo.BookDraft.Producer")),
            bookType.generatedDraftProducerType(),
        )
        assertSame(bookIdProp, schema.idPropOf(bookType))
        assertNull(schema.idPropOf(baseType))
        assertEquals(baseType.generatedPropsType(), schema.generatedPropsTypeOf(inheritedProp))
        assertEquals("URL_VALUE", inheritedProp.generatedPropsConstantName())
        assertEquals(
            "VERSION2_VALUE",
            inheritedProp.copy(name = "version2Value").generatedPropsConstantName(),
        )
        assertEquals(
            "URLVALUE",
            inheritedProp.copy(name = "URLValue").generatedPropsConstantName(),
        )
        assertFailsWith<IllegalArgumentException> {
            baseType.generatedTableType()
        }
        assertFailsWith<IllegalArgumentException> {
            schema.generatedPropsTypeOf(inheritedProp.copy(name = "foreign"))
        }
        assertFailsWith<IllegalArgumentException> {
            schema.idPropOf(bookType.copy(documentation = "foreign"))
        }
    }

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
    fun `resolves concrete primary entity types in stable order`() {
        val rootId = LsiSymbolId.type("demo.Root")
        val middleId = LsiSymbolId.type("demo.Middle")
        val leafBId = LsiSymbolId.type("demo.LeafB")
        val leafAId = LsiSymbolId.type("demo.LeafA")
        val mappedId = LsiSymbolId.type("demo.Mapped")
        val unrelatedId = LsiSymbolId.type("demo.Unrelated")
        fun inheritanceEntity(
            id: LsiSymbolId,
            primarySuperTypeId: LsiSymbolId?,
            instantiable: Boolean,
        ): ImmutableType {
            val idProp = prop(id, "id", primaryMapping = PrimaryMapping.ID)
            val discriminatorProp = prop(id, "kind", primaryMapping = PrimaryMapping.DISCRIMINATOR)
            return type(
                id = id,
                kind = ImmutableTypeKind.ENTITY,
                props = listOf(idProp, discriminatorProp),
                idPropId = idProp.id,
                primarySuperTypeId = primarySuperTypeId,
                inheritanceRootTypeId = rootId,
                inheritanceStrategy = InheritanceStrategy.SINGLE_TABLE.takeIf { id == rootId },
                joinedTableDissociateAction = JoinedTableDissociateAction.DELETE.takeIf { id == rootId },
                discriminatorPropId = discriminatorProp.id,
                instantiable = instantiable,
            )
        }
        val root = inheritanceEntity(rootId, primarySuperTypeId = null, instantiable = false)
        val middle = inheritanceEntity(middleId, primarySuperTypeId = rootId, instantiable = false)
        val leafB = inheritanceEntity(leafBId, primarySuperTypeId = middleId, instantiable = true)
        val leafA = inheritanceEntity(leafAId, primarySuperTypeId = rootId, instantiable = true)
        val mapped = type(
            id = mappedId,
            kind = ImmutableTypeKind.MAPPED_SUPERCLASS,
            props = emptyList(),
            primarySuperTypeId = rootId,
        )
        val unrelatedIdProp = prop(unrelatedId, "id", primaryMapping = PrimaryMapping.ID)
        val unrelated = type(
            id = unrelatedId,
            kind = ImmutableTypeKind.ENTITY,
            props = listOf(unrelatedIdProp),
            idPropId = unrelatedIdProp.id,
        )
        val schema = ImmutableSchema(listOf(root, middle, leafB, leafA, mapped, unrelated))

        assertEquals(
            listOf("demo.LeafA", "demo.LeafB"),
            schema.knownConcreteEntityTypesOf(root).map(ImmutableType::qualifiedName),
        )
        assertEquals(
            listOf("demo.LeafA"),
            schema.knownConcreteEntityTypesOf(leafA).map(ImmutableType::qualifiedName),
        )
        assertEquals(
            listOf("demo.Unrelated"),
            schema.knownConcreteEntityTypesOf(unrelated).map(ImmutableType::qualifiedName),
        )
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

    @Test
    fun `resolves language formula semantics for each target language`() {
        val ownerTypeId = LsiSymbolId.type("demo.Book")
        val languageFormula = prop(ownerTypeId, "languageFormula", formulaKind = FormulaKind.LANGUAGE)
        val abstractFormula = prop(ownerTypeId, "abstractFormula", formulaKind = FormulaKind.ABSTRACT)
        val sqlFormula = prop(ownerTypeId, "sqlFormula", formulaKind = FormulaKind.SQL)
        val scalar = prop(ownerTypeId, "scalar")

        assertTrue(languageFormula.isLanguageFormula(LsiLanguage.JAVA))
        assertTrue(languageFormula.isLanguageFormula(LsiLanguage.KOTLIN))
        assertTrue(abstractFormula.isLanguageFormula(LsiLanguage.JAVA))
        assertFalse(abstractFormula.isLanguageFormula(LsiLanguage.KOTLIN))
        assertFalse(sqlFormula.isLanguageFormula(LsiLanguage.JAVA))
        assertFalse(sqlFormula.isLanguageFormula(LsiLanguage.KOTLIN))
        assertFalse(scalar.isLanguageFormula(LsiLanguage.JAVA))
        assertFalse(scalar.isLanguageFormula(LsiLanguage.KOTLIN))
        assertFailsWith<IllegalArgumentException> {
            scalar.isLanguageFormula(LsiLanguage.UNKNOWN)
        }
    }
}

private fun type(
    id: LsiSymbolId,
    kind: ImmutableTypeKind,
    props: List<ImmutableProp>,
    idPropId: LsiSymbolId? = null,
    primarySuperTypeId: LsiSymbolId? = null,
    inheritanceRootTypeId: LsiSymbolId? = null,
    inheritanceStrategy: InheritanceStrategy? = null,
    joinedTableDissociateAction: JoinedTableDissociateAction? = null,
    discriminatorPropId: LsiSymbolId? = null,
    instantiable: Boolean = kind == ImmutableTypeKind.ENTITY,
): ImmutableType {
    return ImmutableType(
        id = id,
        qualifiedName = id.requireTypeQualifiedName(),
        kind = kind,
        documentation = null,
        annotations = emptyList(),
        typeParameterIds = emptyList(),
        superTypeIds = listOfNotNull(primarySuperTypeId),
        props = props,
        primarySuperTypeId = primarySuperTypeId,
        inheritanceRootTypeId = inheritanceRootTypeId,
        inheritanceStrategy = inheritanceStrategy,
        joinedTableDissociateAction = joinedTableDissociateAction,
        instantiable = instantiable,
        discriminatorValue = null,
        discriminatorPropId = discriminatorPropId,
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
    formulaKind: FormulaKind = FormulaKind.NONE,
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
        formulaKind = formulaKind,
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
