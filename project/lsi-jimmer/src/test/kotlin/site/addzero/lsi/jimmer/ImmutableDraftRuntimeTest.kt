package site.addzero.lsi.jimmer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef

class ImmutableDraftRuntimeTest {

    @Test
    fun `freezes every runtime property role`() {
        val cases = listOf(
            prop("id", primaryMapping = PrimaryMapping.ID) to ImmutableDraftRuntimePropKind.ID,
            prop("version", primaryMapping = PrimaryMapping.VERSION) to ImmutableDraftRuntimePropKind.VERSION,
            prop("deleted", primaryMapping = PrimaryMapping.LOGICAL_DELETED) to
                ImmutableDraftRuntimePropKind.LOGICAL_DELETED,
            prop("code", annotations = listOf(LsiAnnotation(KEY))) to ImmutableDraftRuntimePropKind.KEY_SCALAR,
            prop(
                "author",
                annotations = listOf(LsiAnnotation(KEY)),
                primaryMapping = PrimaryMapping.ASSOCIATION,
                associationKind = AssociationKind.MANY_TO_ONE,
            ) to ImmutableDraftRuntimePropKind.KEY_REFERENCE,
            prop(
                "books",
                type = listType(AUTHOR_TYPE),
                list = true,
                primaryMapping = PrimaryMapping.ASSOCIATION,
                associationKind = AssociationKind.ONE_TO_MANY,
            ) to
                ImmutableDraftRuntimePropKind.ASSOCIATION,
            prop("name") to ImmutableDraftRuntimePropKind.VALUE,
        )
        val schema = schemaOf(cases.map { case -> case.first })

        cases.forEach { (prop, expectedKind) ->
            val runtime = schema.toDraftRuntimeProp(prop)
            assertEquals(expectedKind, runtime.kind)
        }
        assertEquals(
            MANY_TO_ONE,
            schema.toDraftRuntimeProp(cases[4].first).associationAnnotationTypeId,
        )
        assertEquals(
            ONE_TO_MANY,
            schema.toDraftRuntimeProp(cases[5].first).associationAnnotationTypeId,
        )
        assertNull(schema.toDraftRuntimeProp(cases.last().first).associationAnnotationTypeId)
    }

    @Test
    fun `freezes value categories and erases metadata types`() {
        val scalarProp = prop("scalar")
        val scalarListProp = prop("scalarList", type = listType(STRING_TYPE), list = true)
        val referenceProp = prop(
            "reference",
            type = AUTHOR_TYPE,
            primaryMapping = PrimaryMapping.ASSOCIATION,
            associationKind = AssociationKind.MANY_TO_ONE,
        )
        val referenceListProp = prop(
            "references",
            type = listType(AUTHOR_TYPE),
            list = true,
            primaryMapping = PrimaryMapping.ASSOCIATION,
            associationKind = AssociationKind.ONE_TO_MANY,
        )
        val schema = schemaOf(listOf(scalarProp, scalarListProp, referenceProp, referenceListProp))
        val scalar = schema.toDraftRuntimeProp(scalarProp)
        val scalarList = schema.toDraftRuntimeProp(scalarListProp)
        val reference = schema.toDraftRuntimeProp(referenceProp)
        val referenceList = schema.toDraftRuntimeProp(referenceListProp)

        assertEquals(ImmutableDraftRuntimeValueCategory.SCALAR, scalar.valueCategory)
        assertEquals(ImmutableDraftRuntimeValueCategory.SCALAR_LIST, scalarList.valueCategory)
        assertEquals(ImmutableDraftRuntimeValueCategory.REFERENCE, reference.valueCategory)
        assertEquals(ImmutableDraftRuntimeValueCategory.REFERENCE_LIST, referenceList.valueCategory)

        val genericType = LsiDeclaredType(
            declarationId = AUTHOR,
            arguments = listOf(LsiTypeArgument.invariant(STRING_TYPE)),
            nullability = LsiNullability.NULLABLE,
            annotations = listOf(LsiAnnotation(LsiSymbolId.type("demo.TypeUse"))),
        )
        val genericProp = prop("generic", type = genericType)
        val parameterProp = prop("parameter", type = LsiTypeParameterRef(TYPE_PARAMETER))
        val primitiveProp = prop(
            "number",
            type = LsiPrimitiveType(LsiPrimitiveKind.INT, nullability = LsiNullability.NULLABLE),
        )
        val arrayProp = prop(
            "array",
            type = LsiArrayType(genericType, nullability = LsiNullability.NULLABLE),
        )
        val erasureSchema = schemaOf(listOf(genericProp, parameterProp, primitiveProp, arrayProp))
        assertEquals(
            LsiDeclaredType(AUTHOR),
            erasureSchema.toDraftRuntimeProp(genericProp).metadataElementType,
        )
        assertEquals(
            LsiDeclaredType(LsiSymbolId.type("java.lang.Object")),
            erasureSchema.toDraftRuntimeProp(parameterProp).metadataElementType,
        )
        assertEquals(
            LsiPrimitiveType(LsiPrimitiveKind.INT),
            erasureSchema.toDraftRuntimeProp(primitiveProp).metadataElementType,
        )
        assertEquals(
            LsiArrayType(LsiDeclaredType(AUTHOR)),
            erasureSchema.toDraftRuntimeProp(arrayProp).metadataElementType,
        )
    }

    @Test
    fun `rejects inconsistent runtime association metadata`() {
        assertFailsWith<IllegalArgumentException> {
            ImmutableDraftRuntimeProp(
                kind = ImmutableDraftRuntimePropKind.VALUE,
                valueCategory = ImmutableDraftRuntimeValueCategory.SCALAR,
                associationAnnotationTypeId = MANY_TO_ONE,
                metadataElementType = STRING_TYPE,
            )
        }

        val prop = prop("name")
        val schema = schemaOf(listOf(prop))
        assertFailsWith<IllegalArgumentException> {
            schema.toDraftRuntimeProp(prop("other"))
        }
    }
}

private fun prop(
    name: String,
    type: LsiTypeRef = STRING_TYPE,
    annotations: List<LsiAnnotation> = emptyList(),
    list: Boolean = false,
    primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
    associationKind: AssociationKind = AssociationKind.NONE,
): ImmutableProp {
    val id = LsiSymbolId.property(OWNER, name)
    return ImmutableProp(
        id = id,
        declarationId = id,
        ownerTypeId = OWNER,
        declaringTypeId = OWNER,
        name = name,
        documentation = null,
        type = type,
        annotations = annotations,
        overrideChain = emptyList(),
        inherited = false,
        overridden = false,
        nullable = false,
        list = list,
        association = associationKind != AssociationKind.NONE,
        embedded = false,
        targetTypeId = null,
        primaryMapping = primaryMapping,
        primaryAnnotationTypeId = null,
        defaultContract = when (primaryMapping) {
            PrimaryMapping.VERSION -> ImmutableDefault.Application(
                annotationValue = null,
                strategy = ApplicationDefaultStrategy.VERSION_ZERO,
            )
            PrimaryMapping.LOGICAL_DELETED -> ImmutableDefault.Application(
                annotationValue = null,
                strategy = ApplicationDefaultStrategy.LOGICAL_DELETED,
            )
            else -> null
        },
        associationKind = associationKind,
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

private fun schemaOf(props: List<ImmutableProp>): ImmutableSchema {
    return ImmutableSchema(
        listOf(
            ImmutableType(
                id = OWNER,
                qualifiedName = OWNER.value,
                kind = ImmutableTypeKind.MAPPED_SUPERCLASS,
                documentation = null,
                annotations = emptyList(),
                typeParameterIds = listOf(TYPE_PARAMETER),
                superTypeIds = emptyList(),
                props = props,
                primarySuperTypeId = null,
                inheritanceRootTypeId = null,
                inheritanceStrategy = null,
                joinedTableDissociateAction = null,
                instantiable = false,
                discriminatorValue = null,
                discriminatorPropId = null,
                idPropId = props.singleOrNull { prop -> prop.primaryMapping == PrimaryMapping.ID }?.id,
                versionPropId = props.singleOrNull { prop -> prop.primaryMapping == PrimaryMapping.VERSION }?.id,
                logicalDeletedPropId = props
                    .singleOrNull { prop -> prop.primaryMapping == PrimaryMapping.LOGICAL_DELETED }
                    ?.id,
                acrossMicroServices = false,
                microServiceName = "",
            ),
        ),
    )
}

private fun listType(elementType: LsiTypeRef): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = LsiSymbolId.type("java.util.List"),
        arguments = listOf(LsiTypeArgument.invariant(elementType)),
    )
}

private val OWNER = LsiSymbolId.type("demo.Book")
private val TYPE_PARAMETER = LsiSymbolId.typeParameter(OWNER, "T")
private val AUTHOR = LsiSymbolId.type("demo.Author")
private val KEY = LsiSymbolId.type("org.babyfish.jimmer.sql.Key")
private val MANY_TO_ONE = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToOne")
private val ONE_TO_MANY = LsiSymbolId.type("org.babyfish.jimmer.sql.OneToMany")
private val STRING_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
private val AUTHOR_TYPE = LsiDeclaredType(AUTHOR)
