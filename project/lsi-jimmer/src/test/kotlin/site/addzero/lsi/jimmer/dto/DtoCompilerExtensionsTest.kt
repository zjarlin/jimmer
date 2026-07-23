package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.babyfish.jimmer.dto.compiler.DtoFile
import org.babyfish.jimmer.dto.compiler.DtoModifier as AstDtoModifier
import org.babyfish.jimmer.dto.compiler.DtoTypeKind
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace

class DtoCompilerExtensionsTest {

    @Test
    fun `resolves inherited generic input for Java target`() {
        val bridgeTypeId = typeId("contract.BaseInput")
        val bridgeParameterId = LsiSymbolId.typeParameter(bridgeTypeId, "T")
        val reusableTypeId = typeId("contract.BookInput")
        val registry = registry(
            declarations = listOf(
                declaration(
                    id = bridgeTypeId,
                    typeParameters = listOf(LsiTypeParameter(bridgeParameterId, "T")),
                    superTypes = listOf(
                        declared(INPUT_TYPE_ID, LsiTypeParameterRef(bridgeParameterId)),
                    ),
                ),
                declaration(
                    id = reusableTypeId,
                    superTypes = listOf(declared(bridgeTypeId, LsiDeclaredType(BOOK_TYPE_ID))),
                ),
            ),
        )

        val typeInfo = registry.resolveDtoTypeInfo(
            reusableTypeId.requireTypeQualifiedName(),
            LsiLanguage.JAVA,
        )

        assertEquals(DtoTypeKind.INPUT, typeInfo?.kind)
        assertEquals(BOOK_TYPE_ID, typeInfo?.baseType?.id)
    }

    @Test
    fun `resolves view entity for Kotlin target`() {
        val reusableTypeId = typeId("contract.BookView")
        val registry = registry(
            declarations = listOf(
                declaration(
                    id = reusableTypeId,
                    superTypes = listOf(declared(VIEW_TYPE_ID, LsiDeclaredType(BOOK_TYPE_ID))),
                ),
            ),
        )

        val typeInfo = registry.resolveDtoTypeInfo(
            reusableTypeId.requireTypeQualifiedName(),
            LsiLanguage.KOTLIN,
        )

        assertEquals(DtoTypeKind.VIEW, typeInfo?.kind)
        assertEquals(BOOK_TYPE_ID, typeInfo?.baseType?.id)
    }

    @Test
    fun `uses target language specification marker`() {
        val javaSpecificationId = typeId("contract.BookJavaSpecification")
        val kotlinSpecificationId = typeId("contract.BookKotlinSpecification")
        val registry = registry(
            declarations = listOf(
                declaration(
                    id = javaSpecificationId,
                    superTypes = listOf(
                        declared(
                            J_SPECIFICATION_TYPE_ID,
                            LsiDeclaredType(BOOK_TYPE_ID),
                            LsiDeclaredType(typeId("contract.BookTable")),
                        ),
                    ),
                ),
                declaration(
                    id = kotlinSpecificationId,
                    superTypes = listOf(
                        declared(K_SPECIFICATION_TYPE_ID, LsiDeclaredType(BOOK_TYPE_ID)),
                    ),
                ),
            ),
        )

        assertEquals(
            DtoTypeKind.SPECIFICATION,
            registry.resolveDtoTypeInfo(
                javaSpecificationId.requireTypeQualifiedName(),
                LsiLanguage.JAVA,
            )?.kind,
        )
        assertNull(
            registry.resolveDtoTypeInfo(
                kotlinSpecificationId.requireTypeQualifiedName(),
                LsiLanguage.JAVA,
            )
        )
        assertEquals(
            DtoTypeKind.SPECIFICATION,
            registry.resolveDtoTypeInfo(
                kotlinSpecificationId.requireTypeQualifiedName(),
                LsiLanguage.KOTLIN,
            )?.kind,
        )
        assertNull(
            registry.resolveDtoTypeInfo(
                javaSpecificationId.requireTypeQualifiedName(),
                LsiLanguage.KOTLIN,
            )
        )
    }

    @Test
    fun `returns null for non DTO type`() {
        val otherTypeId = typeId("contract.Other")
        val registry = registry(listOf(declaration(otherTypeId)))

        assertNull(registry.resolveDtoTypeInfo(otherTypeId.requireTypeQualifiedName(), LsiLanguage.JAVA))
        assertNull(registry.resolveDtoTypeInfo("contract.Missing", LsiLanguage.KOTLIN))
    }

    @Test
    fun `rejects DTO whose entity argument is not immutable`() {
        val reusableTypeId = typeId("contract.InvalidView")
        val registry = registry(
            declarations = listOf(
                declaration(
                    id = reusableTypeId,
                    superTypes = listOf(
                        declared(VIEW_TYPE_ID, LsiDeclaredType(typeId("contract.NotImmutable"))),
                    ),
                ),
            ),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            registry.resolveDtoTypeInfo(
                reusableTypeId.requireTypeQualifiedName(),
                LsiLanguage.JAVA,
            )
        }

        assertEquals(
            "The entity type argument of reusable DTO type \"contract.InvalidView\" is not an immutable type",
            exception.message,
        )
    }

    @Test
    fun `rejects unknown target language`() {
        val registry = registry(emptyList())

        val exception = assertFailsWith<IllegalArgumentException> {
            registry.resolveDtoTypeInfo("contract.Missing", LsiLanguage.UNKNOWN)
        }

        assertEquals(
            "Reusable DTO type resolution requires Java or Kotlin target language",
            exception.message,
        )
    }

    @Test
    fun `resolves id and many-to-many view base properties by immutable ids`() {
        val storeTypeId = typeId("demo.Store")
        val linkTypeId = typeId("demo.BookAuthor")
        val authorTypeId = typeId("demo.Author")
        val storeIdProp = idProp(storeTypeId)
        val authorIdProp = idProp(authorTypeId)
        val linkIdProp = idProp(linkTypeId)
        val bookIdProp = idProp(BOOK_TYPE_ID)
        val storeProp = prop(
            ownerTypeId = BOOK_TYPE_ID,
            name = "store",
            type = LsiDeclaredType(storeTypeId),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            targetTypeId = storeTypeId,
            associationKind = AssociationKind.MANY_TO_ONE,
        )
        val storeIdViewProp = prop(
            ownerTypeId = BOOK_TYPE_ID,
            name = "storeId",
            type = LONG_TYPE,
            primaryMapping = PrimaryMapping.VIEW,
            view = ImmutableView.Id(storeProp.id, storeIdProp.id),
        )
        val linksProp = prop(
            ownerTypeId = BOOK_TYPE_ID,
            name = "links",
            type = listType(linkTypeId),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            list = true,
            targetTypeId = linkTypeId,
            associationKind = AssociationKind.ONE_TO_MANY,
        )
        val deeperProp = prop(
            ownerTypeId = linkTypeId,
            name = "author",
            type = LsiDeclaredType(authorTypeId),
            primaryMapping = PrimaryMapping.ASSOCIATION,
            targetTypeId = authorTypeId,
            associationKind = AssociationKind.MANY_TO_ONE,
        )
        val authorsViewProp = prop(
            ownerTypeId = BOOK_TYPE_ID,
            name = "authors",
            type = listType(authorTypeId),
            primaryMapping = PrimaryMapping.VIEW,
            list = true,
            targetTypeId = authorTypeId,
            associationKind = AssociationKind.MANY_TO_MANY_VIEW,
            view = ImmutableView.ManyToMany(linksProp.id, deeperProp.id),
        )
        val schema = ImmutableSchema(
            listOf(
                immutableEntity(storeTypeId, listOf(storeIdProp)),
                immutableEntity(authorTypeId, listOf(authorIdProp)),
                immutableEntity(linkTypeId, listOf(linkIdProp, deeperProp)),
                immutableEntity(
                    BOOK_TYPE_ID,
                    listOf(bookIdProp, storeProp, storeIdViewProp, linksProp, authorsViewProp),
                ),
            )
        )
        val registry = schema.toLsiDtoTypeRegistry(LsiWorkspace.EMPTY)
        val bookProps = registry.props(requireNotNull(registry[BOOK_TYPE_ID]))

        assertEquals(storeProp.id, bookProps.getValue("storeId").idViewBaseProp?.id)
        assertEquals(linksProp.id, bookProps.getValue("authors").manyToManyViewBaseProp?.id)
    }

    @Test
    fun `compiles DTO file and freezes stable LSI graph with inherited documentation`() {
        val rawSourcePath = "/project/./src/main/dto/demo/Book.dto"
        val source = LsiSource.of(
            path = rawSourcePath,
            language = LsiLanguage.KOTLIN,
            kind = LsiSourceKind.GENERATED,
        )
        val idProp = idProp(BOOK_TYPE_ID)
        val nameProp = prop(
            ownerTypeId = BOOK_TYPE_ID,
            name = "name",
            type = STRING_TYPE,
            documentation = "Immutable name documentation",
        )
        val titleProp = prop(
            ownerTypeId = BOOK_TYPE_ID,
            name = "title",
            type = STRING_TYPE,
            documentation = "Immutable title documentation",
        )
        val schema = ImmutableSchema(
            listOf(
                immutableEntity(
                    id = BOOK_TYPE_ID,
                    props = listOf(idProp, nameProp, titleProp),
                    documentation = "Immutable book documentation",
                )
            )
        )
        val registry = schema.toLsiDtoTypeRegistry(LsiWorkspace.EMPTY)
        val dtoFile = DtoFile(
            rawSourcePath,
            "demo/Book.dto",
            """
                /**
                 * DTO book documentation
                 * @param name DTO name documentation
                 */
                BookView {
                    id
                    name
                    title
                }
            """.trimIndent(),
        )
        val compiler = dtoFile.toLsiDtoCompiler(
            registry = registry,
            defaultNullableInputModifier = AstDtoModifier.STATIC,
        )
        val compiledTypes = compiler.compile(requireNotNull(registry[BOOK_TYPE_ID]))

        val graph = compiledTypes.toLsiDtoGraph(source)
        val rootTypeId = DtoTypeId("${source.path}#root:00000000:BookView")
        val rootType = graph.typesById.getValue(rootTypeId)
        val props = rootType.propIds.map(graph.propsById::getValue)
        val id = props.single { dtoProp -> dtoProp.name == "id" } as DtoBaseProp
        val name = props.single { dtoProp -> dtoProp.name == "name" } as DtoBaseProp
        val title = props.single { dtoProp -> dtoProp.name == "title" } as DtoBaseProp

        assertEquals(source, graph.source)
        assertEquals(listOf(rootTypeId), graph.rootTypeIds)
        assertEquals(BOOK_TYPE_ID, rootType.baseTypeId)
        assertEquals("BookView", rootType.name)
        assertEquals(source, rootType.location.source)
        assertEquals("DTO book documentation\n@param name DTO name documentation\n", rootType.documentation)
        assertEquals(listOf("id", "name", "title"), props.map(DtoProp::name))
        assertEquals(idProp.id, id.baseProps.single().propId)
        assertEquals(nameProp.id, name.baseProps.single().propId)
        assertEquals(titleProp.id, title.baseProps.single().propId)
        assertEquals("DTO name documentation", name.documentation)
        assertEquals("DTO name documentation", name.dtoDocumentation)
        assertEquals("Immutable title documentation\n", title.documentation)
        assertNull(title.dtoDocumentation)
        assertTrue(props.all { dtoProp -> dtoProp.aliasLocation.source == source })
    }

    private fun registry(
        declarations: List<LsiTypeDeclaration>,
    ): LsiDtoTypeRegistry {
        val workspace = LsiWorkspace(declarations = declarations)
        val book = immutableEntity(BOOK_TYPE_ID, listOf(idProp(BOOK_TYPE_ID)))
        return ImmutableSchema(listOf(book)).toLsiDtoTypeRegistry(workspace)
    }

    private fun declaration(
        id: LsiSymbolId,
        typeParameters: List<LsiTypeParameter> = emptyList(),
        superTypes: List<LsiDeclaredType> = emptyList(),
    ): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = id,
            name = id.requireTypeQualifiedName().substringAfterLast('.'),
            qualifiedName = id.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.CLASS,
            modality = LsiModality.OPEN,
            typeParameters = typeParameters,
            superTypes = superTypes,
            origin = BINARY_ORIGIN,
        )
    }

    private fun declared(
        typeId: LsiSymbolId,
        vararg arguments: LsiTypeRef,
    ): LsiDeclaredType {
        return LsiDeclaredType(
            declarationId = typeId,
            arguments = arguments.map(LsiTypeArgument::invariant),
        )
    }

    private fun immutableEntity(
        id: LsiSymbolId,
        props: List<ImmutableProp>,
        documentation: String? = null,
    ): ImmutableType {
        val idProp = props.single { prop -> prop.primaryMapping == PrimaryMapping.ID }
        return ImmutableType(
            id = id,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = ImmutableTypeKind.ENTITY,
            documentation = documentation,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = emptyList(),
            props = props,
            primarySuperTypeId = null,
            inheritanceRootTypeId = null,
            inheritanceStrategy = null,
            joinedTableDissociateAction = null,
            instantiable = true,
            discriminatorValue = null,
            discriminatorPropId = null,
            idPropId = idProp.id,
            versionPropId = null,
            logicalDeletedPropId = null,
            acrossMicroServices = false,
            microServiceName = "",
        )
    }

    private fun idProp(ownerTypeId: LsiSymbolId): ImmutableProp {
        return prop(
            ownerTypeId = ownerTypeId,
            name = "id",
            type = LONG_TYPE,
            primaryMapping = PrimaryMapping.ID,
        )
    }

    private fun prop(
        ownerTypeId: LsiSymbolId,
        name: String,
        type: LsiTypeRef,
        documentation: String? = null,
        primaryMapping: PrimaryMapping = PrimaryMapping.SCALAR,
        list: Boolean = false,
        targetTypeId: LsiSymbolId? = null,
        associationKind: AssociationKind = AssociationKind.NONE,
        view: ImmutableView? = null,
    ): ImmutableProp {
        val id = LsiSymbolId.property(ownerTypeId, name)
        return ImmutableProp(
            id = id,
            declarationId = id,
            ownerTypeId = ownerTypeId,
            declaringTypeId = ownerTypeId,
            name = name,
            documentation = documentation,
            type = type,
            annotations = emptyList(),
            overrideChain = listOf(id),
            inherited = false,
            overridden = false,
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
            associationStorage = associationKind.storageKind(),
            transientResolver = null,
            view = view,
            genericTarget = false,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = null,
        )
    }

    private fun AssociationKind.storageKind(): AssociationStorageKind {
        return when (this) {
            AssociationKind.ONE_TO_ONE,
            AssociationKind.MANY_TO_ONE,
            -> AssociationStorageKind.COLUMN
            AssociationKind.MANY_TO_MANY -> AssociationStorageKind.MIDDLE_TABLE
            else -> AssociationStorageKind.NONE
        }
    }

    private fun listType(elementTypeId: LsiSymbolId): LsiDeclaredType {
        return LsiDeclaredType(
            declarationId = LIST_TYPE_ID,
            arguments = listOf(LsiTypeArgument.invariant(LsiDeclaredType(elementTypeId))),
        )
    }

    private fun typeId(qualifiedName: String): LsiSymbolId = LsiSymbolId.type(qualifiedName)

    private companion object {
        val BOOK_TYPE_ID = LsiSymbolId.type("demo.Book")
        val INPUT_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.Input")
        val VIEW_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.View")
        val J_SPECIFICATION_TYPE_ID =
            LsiSymbolId.type("org.babyfish.jimmer.sql.ast.query.specification.JSpecification")
        val K_SPECIFICATION_TYPE_ID =
            LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.query.specification.KSpecification")
        val LIST_TYPE_ID = LsiSymbolId.type("java.util.List")
        val LONG_TYPE: LsiTypeRef = LsiPrimitiveType(LsiPrimitiveKind.LONG)
        val STRING_TYPE: LsiTypeRef = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))
        val BINARY_ORIGIN = LsiOrigin(
            kind = LsiOriginKind.BINARY,
            language = LsiLanguage.UNKNOWN,
        )
    }
}
