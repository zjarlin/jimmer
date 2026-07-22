package site.addzero.lsi.jimmer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace

class ImmutableSemanticDependenciesTest {

    @Test
    fun `custom validation dependencies include annotation and validator types`() {
        val annotationTypeId = typeId("demo.ValidBook")
        val validatorTypeId = typeId("demo.ValidBookValidator")
        val dependencies = sortedSetOf<LsiSymbolId>()

        dependencies.collectImmutableValidationDependencies(
            listOf(
                ImmutableValidation(
                    annotationTypeId = annotationTypeId,
                    validatorTypeIds = listOf(validatorTypeId),
                    message = "invalid book",
                    sourceAnnotationUseSiteTarget = null,
                )
            )
        )

        assertEquals(setOf(annotationTypeId, validatorTypeId), dependencies)
    }

    @Test
    fun `type parameter upper bounds and direct super references contribute workspace dependencies`() {
        val genericId = typeId("demo.GenericRecord")
        val parameterId = LsiSymbolId.typeParameter(genericId, "T")
        val upperBoundId = typeId("support.UpperBoundOnly")
        val directSuperId = typeId("support.DirectSuperOnly")
        val genericSource = source("demo/GenericRecord.kt")
        val upperBoundSource = source("support/UpperBoundOnly.kt")
        val directSuperSource = source("support/DirectSuperOnly.kt")
        val genericType = type(
            id = genericId,
            kind = ImmutableTypeKind.MAPPED_SUPERCLASS,
            props = emptyList(),
            typeParameterIds = listOf(parameterId),
        )
        val workspace = LsiWorkspace(
            sources = setOf(genericSource, upperBoundSource, directSuperSource),
            declarations = listOf(
                declaration(
                    id = genericId,
                    source = genericSource,
                    typeParameters = listOf(
                        LsiTypeParameter(
                            id = parameterId,
                            name = "T",
                            upperBounds = listOf(LsiDeclaredType(upperBoundId)),
                        )
                    ),
                    superTypes = listOf(LsiDeclaredType(directSuperId)),
                ),
                declaration(upperBoundId, upperBoundSource),
                declaration(directSuperId, directSuperSource),
            ),
        )
        val schema = ImmutableSchema(listOf(genericType))

        val dependencies = schema.semanticDependencySymbols(
            rootTypeIds = listOf(genericId),
            rootProps = emptyList(),
            workspace = workspace,
        )

        assertTrue(dependencies.containsAll(setOf(parameterId, upperBoundId, directSuperId)))
        assertEquals(
            setOf(genericSource, upperBoundSource, directSuperSource),
            workspace.originatingSources(dependencies),
        )
    }

    @Test
    fun `association target embedded id contributes its type and structured annotations`() {
        val bookId = typeId("demo.Book")
        val authorId = typeId("demo.Author")
        val authorIdTypeId = typeId("demo.AuthorId")
        val markerId = typeId("support.IdMarker")
        val payloadId = typeId("support.IdPayload")
        val enumId = typeId("support.IdMode")
        val nestedId = typeId("support.IdNested")
        val propertyMarkerId = typeId("support.IdPropertyMarker")
        val typeUseMarkerId = typeId("support.IdTypeUseMarker")
        val structuredMarker = LsiAnnotation(
            type = markerId,
            arguments = mapOf(
                "values" to explicit(
                    LsiAnnotationValue.ArrayValue(
                        listOf(
                            LsiAnnotationValue.ClassValue(LsiDeclaredType(payloadId)),
                            LsiAnnotationValue.EnumValue(enumId, "PRIMARY"),
                            LsiAnnotationValue.NestedAnnotationValue(LsiAnnotation(nestedId)),
                        )
                    )
                )
            ),
        )
        val authorIdValue = prop(
            ownerTypeId = authorIdTypeId,
            name = "value",
            type = STRING_TYPE,
        )
        val authorIdType = type(
            id = authorIdTypeId,
            kind = ImmutableTypeKind.EMBEDDABLE,
            props = listOf(authorIdValue),
            annotations = listOf(structuredMarker),
        )
        val authorIdentity = prop(
            ownerTypeId = authorId,
            name = "id",
            type = LsiDeclaredType(
                declarationId = authorIdTypeId,
                annotations = listOf(LsiAnnotation(typeUseMarkerId)),
            ),
            annotations = listOf(LsiAnnotation(ID_ANNOTATION_ID), LsiAnnotation(propertyMarkerId)),
            primaryMapping = PrimaryMapping.ID,
            primaryAnnotationTypeId = ID_ANNOTATION_ID,
            embedded = true,
            targetTypeId = authorIdTypeId,
        )
        val author = type(
            id = authorId,
            kind = ImmutableTypeKind.ENTITY,
            props = listOf(authorIdentity),
            idPropId = authorIdentity.id,
        )
        val bookIdentity = prop(
            ownerTypeId = bookId,
            name = "id",
            type = LONG_TYPE,
            annotations = listOf(LsiAnnotation(ID_ANNOTATION_ID)),
            primaryMapping = PrimaryMapping.ID,
            primaryAnnotationTypeId = ID_ANNOTATION_ID,
        )
        val authorProp = prop(
            ownerTypeId = bookId,
            name = "author",
            type = LsiDeclaredType(authorId),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            associationKind = AssociationKind.MANY_TO_ONE,
            associationStorage = AssociationStorageKind.COLUMN,
            targetTypeId = authorId,
        )
        val book = type(
            id = bookId,
            kind = ImmutableTypeKind.ENTITY,
            props = listOf(bookIdentity, authorProp),
            idPropId = bookIdentity.id,
        )
        val schema = ImmutableSchema(listOf(book, author, authorIdType))

        val dependencies = schema.semanticDependencySymbols(
            rootTypeIds = listOf(bookId),
            rootProps = book.props,
            workspace = LsiWorkspace.EMPTY,
        )

        assertTrue(
            dependencies.containsAll(
                setOf(
                    bookId,
                    authorId,
                    authorIdentity.id,
                    authorIdTypeId,
                    propertyMarkerId,
                    typeUseMarkerId,
                    markerId,
                    payloadId,
                    enumId,
                    nestedId,
                )
            ),
            dependencies.joinToString { id -> id.value },
        )
    }

    @Test
    fun `override view and formula dependencies form one stable property closure`() {
        val baseId = typeId("demo.BaseRecord")
        val middleId = typeId("demo.MiddleRecord")
        val bookId = typeId("demo.Book")
        val authorId = typeId("demo.Author")
        val overrideMarkerId = typeId("support.OverrideMarker")
        val formulaMarkerId = typeId("support.FormulaMarker")
        val baseName = prop(baseId, "name", STRING_TYPE)
        val middleName = prop(
            ownerTypeId = middleId,
            declaringTypeId = middleId,
            name = "name",
            type = STRING_TYPE,
            annotations = listOf(LsiAnnotation(overrideMarkerId)),
            overrideChain = listOf(baseName.id),
            overridden = true,
        )
        val effectiveName = prop(
            ownerTypeId = bookId,
            declarationId = middleName.id,
            declaringTypeId = middleId,
            name = "name",
            type = STRING_TYPE,
            annotations = middleName.annotations,
            overrideChain = listOf(middleName.id, baseName.id),
            inherited = true,
        )
        val base = type(
            id = baseId,
            kind = ImmutableTypeKind.MAPPED_SUPERCLASS,
            props = listOf(baseName),
        )
        val middle = type(
            id = middleId,
            kind = ImmutableTypeKind.MAPPED_SUPERCLASS,
            props = listOf(middleName),
            superTypeIds = listOf(baseId),
            primarySuperTypeId = baseId,
        )
        val authorIdentity = prop(
            ownerTypeId = authorId,
            name = "id",
            type = LONG_TYPE,
            annotations = listOf(LsiAnnotation(ID_ANNOTATION_ID)),
            primaryMapping = PrimaryMapping.ID,
            primaryAnnotationTypeId = ID_ANNOTATION_ID,
        )
        val author = type(
            id = authorId,
            kind = ImmutableTypeKind.ENTITY,
            props = listOf(authorIdentity),
            idPropId = authorIdentity.id,
        )
        val bookIdentity = prop(
            ownerTypeId = bookId,
            name = "id",
            type = LONG_TYPE,
            annotations = listOf(LsiAnnotation(ID_ANNOTATION_ID)),
            primaryMapping = PrimaryMapping.ID,
            primaryAnnotationTypeId = ID_ANNOTATION_ID,
        )
        val authorProp = prop(
            ownerTypeId = bookId,
            name = "author",
            type = LsiDeclaredType(authorId),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            associationKind = AssociationKind.MANY_TO_ONE,
            associationStorage = AssociationStorageKind.COLUMN,
            targetTypeId = authorId,
        )
        val authorIdView = prop(
            ownerTypeId = bookId,
            name = "authorId",
            type = LONG_TYPE,
            primaryMapping = PrimaryMapping.VIEW,
            view = ImmutableView.Id(authorProp.id, authorIdentity.id),
        )
        val displayName = prop(
            ownerTypeId = bookId,
            name = "displayName",
            type = STRING_TYPE,
            annotations = listOf(LsiAnnotation(formulaMarkerId)),
            primaryMapping = PrimaryMapping.FORMULA,
            formulaKind = FormulaKind.LANGUAGE,
            formulaDependencies = listOf(FormulaDependency(listOf(effectiveName.id))),
        )
        val book = type(
            id = bookId,
            kind = ImmutableTypeKind.ENTITY,
            props = listOf(bookIdentity, effectiveName, authorProp, authorIdView, displayName),
            superTypeIds = listOf(middleId),
            primarySuperTypeId = middleId,
            idPropId = bookIdentity.id,
        )
        val schema = ImmutableSchema(listOf(base, middle, book, author))

        val dependencies = schema.semanticDependencySymbols(
            rootTypeIds = listOf(bookId),
            rootProps = listOf(effectiveName, authorIdView, displayName),
            workspace = LsiWorkspace.EMPTY,
        )

        assertTrue(
            dependencies.containsAll(
                setOf(
                    baseId,
                    middleId,
                    bookId,
                    authorId,
                    baseName.id,
                    middleName.id,
                    effectiveName.id,
                    overrideMarkerId,
                    authorProp.id,
                    authorIdentity.id,
                    authorIdView.id,
                    displayName.id,
                    formulaMarkerId,
                )
            ),
            dependencies.joinToString { id -> id.value },
        )
    }

    private fun type(
        id: LsiSymbolId,
        kind: ImmutableTypeKind,
        props: List<ImmutableProp>,
        annotations: List<LsiAnnotation> = emptyList(),
        typeParameterIds: List<LsiSymbolId> = emptyList(),
        superTypeIds: List<LsiSymbolId> = emptyList(),
        primarySuperTypeId: LsiSymbolId? = null,
        idPropId: LsiSymbolId? = null,
    ): ImmutableType {
        return ImmutableType(
            id = id,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = kind,
            documentation = null,
            annotations = annotations,
            typeParameterIds = typeParameterIds,
            superTypeIds = superTypeIds,
            props = props,
            primarySuperTypeId = primarySuperTypeId,
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
        type: LsiTypeRef,
        declarationId: LsiSymbolId = LsiSymbolId.property(ownerTypeId, name),
        declaringTypeId: LsiSymbolId = ownerTypeId,
        annotations: List<LsiAnnotation> = emptyList(),
        overrideChain: List<LsiSymbolId> = listOf(declarationId),
        inherited: Boolean = false,
        overridden: Boolean = false,
        embedded: Boolean = false,
        targetTypeId: LsiSymbolId? = null,
        primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
        primaryAnnotationTypeId: LsiSymbolId? = null,
        associationKind: AssociationKind = AssociationKind.NONE,
        associationStorage: AssociationStorageKind = AssociationStorageKind.NONE,
        view: ImmutableView? = null,
        formulaKind: FormulaKind = FormulaKind.NONE,
        formulaDependencies: List<FormulaDependency> = emptyList(),
    ): ImmutableProp {
        val id = LsiSymbolId.property(ownerTypeId, name)
        return ImmutableProp(
            id = id,
            declarationId = declarationId,
            ownerTypeId = ownerTypeId,
            declaringTypeId = declaringTypeId,
            name = name,
            documentation = null,
            type = type,
            annotations = annotations,
            overrideChain = overrideChain,
            inherited = inherited,
            overridden = overridden,
            nullable = false,
            list = false,
            association = associationKind != AssociationKind.NONE,
            embedded = embedded,
            targetTypeId = targetTypeId,
            primaryMapping = primaryMapping,
            primaryAnnotationTypeId = primaryAnnotationTypeId,
            defaultContract = null,
            associationKind = associationKind,
            formulaKind = formulaKind,
            mappedBy = null,
            associationStorage = associationStorage,
            transientResolver = null,
            view = view,
            genericTarget = false,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = null,
            formulaDependencies = formulaDependencies,
        )
    }

    private fun explicit(value: LsiAnnotationValue): LsiAnnotationArgument {
        return LsiAnnotationArgument(value, LsiAnnotationArgumentOrigin.EXPLICIT)
    }

    private fun declaration(
        id: LsiSymbolId,
        source: LsiSource,
        typeParameters: List<LsiTypeParameter> = emptyList(),
        superTypes: List<LsiTypeRef> = emptyList(),
    ): LsiTypeDeclaration {
        val qualifiedName = id.requireTypeQualifiedName()
        return LsiTypeDeclaration(
            id = id,
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.INTERFACE,
            typeParameters = typeParameters,
            superTypes = superTypes,
            origin = LsiOrigin(LsiOriginKind.SOURCE, source),
        )
    }

    private fun source(path: String): LsiSource {
        return LsiSource.of(path, LsiLanguage.KOTLIN)
    }

    private fun typeId(qualifiedName: String): LsiSymbolId = LsiSymbolId.type(qualifiedName)

    private companion object {
        val ID_ANNOTATION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Id")
        val STRING_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val LONG_TYPE = LsiPrimitiveType(LsiPrimitiveKind.LONG)
    }
}
