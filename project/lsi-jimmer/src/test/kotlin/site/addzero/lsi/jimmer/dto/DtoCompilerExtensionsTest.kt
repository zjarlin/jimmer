package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.babyfish.jimmer.dto.compiler.DtoAstException
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
import site.addzero.lsi.jimmer.idViewBasePropOf
import site.addzero.lsi.jimmer.manyToManyViewBasePropOf
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
    fun `non inherited DTO compiler places id before other scalar properties`() {
        val nameProp = prop(BOOK_TYPE_ID, "name", STRING_TYPE)
        val idProp = idProp(BOOK_TYPE_ID)
        val schema = ImmutableSchema(
            listOf(immutableEntity(BOOK_TYPE_ID, listOf(nameProp, idProp)))
        )
        val source = LsiSource.of(
            path = "src/main/dto/demo/Book.dto",
            language = LsiLanguage.KOTLIN,
        )
        val compiledTypes = DtoFile(
            source.path,
            "demo/Book.dto",
            """
                BookView {
                    #allScalars
                }

                ExplicitBookView {
                    name
                    id
                }
            """.trimIndent(),
        ).toLsiDtoCompiler(
            immutableSchema = schema,
            workspace = LsiWorkspace.EMPTY,
            defaultNullableInputModifier = AstDtoModifier.STATIC,
        ).compile(schema.typesById.getValue(BOOK_TYPE_ID))

        val compiledMacroType = compiledTypes.single { type -> type.name == "BookView" }
        val graph = compiledTypes.toLsiDtoGraph(source)
        val rootTypes = graph.rootTypeIds.map(graph.typesById::getValue)
        val macroType = rootTypes.single { type -> type.name == "BookView" }
        val explicitType = rootTypes.single { type -> type.name == "ExplicitBookView" }

        assertSame(idProp, compiledMacroType.dtoProps[0].baseProp)
        assertSame(nameProp, compiledMacroType.dtoProps[1].baseProp)
        assertEquals(
            listOf("id", "name"),
            macroType.propIds.map { propId -> graph.propsById.getValue(propId).name },
        )
        assertEquals(
            listOf("name", "id"),
            explicitType.propIds.map { propId -> graph.propsById.getValue(propId).name },
        )
    }

    @Test
    fun `DTO compiler diagnostics use stable immutable property display names`() {
        val idProp = idProp(BOOK_TYPE_ID)
        val nameProp = prop(BOOK_TYPE_ID, "name", STRING_TYPE)
        val schema = ImmutableSchema(
            listOf(immutableEntity(BOOK_TYPE_ID, listOf(idProp, nameProp)))
        )
        val dtoFile = DtoFile(
            "src/main/dto/demo/Book.dto",
            "demo/Book.dto",
            "BookView { id(name) }",
        )
        val compiler = dtoFile.toLsiDtoCompiler(
            immutableSchema = schema,
            workspace = LsiWorkspace.EMPTY,
            defaultNullableInputModifier = AstDtoModifier.STATIC,
        )

        val exception = assertFailsWith<DtoAstException> {
            compiler.compile(schema.typesById.getValue(BOOK_TYPE_ID))
        }
        val message = requireNotNull(exception.message)

        assertTrue("\"demo.Book.name\"" in message, message)
        assertTrue("ImmutableProp(" !in message, message)
    }

    @Test
    fun `resolves inherited generic input for Java target`() {
        val bridgeTypeId = typeId("contract.BaseInput")
        val bridgeParameterId = LsiSymbolId.typeParameter(bridgeTypeId, "T")
        val reusableTypeId = typeId("contract.BookInput")
        val (schema, workspace) = schemaAndWorkspace(
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

        val typeInfo = workspace.resolveDtoTypeInfo(
            schema,
            reusableTypeId.requireTypeQualifiedName(),
            LsiLanguage.JAVA,
        )

        assertEquals(DtoTypeKind.INPUT, typeInfo?.kind)
        assertEquals(BOOK_TYPE_ID.requireTypeQualifiedName(), typeInfo?.baseTypeQualifiedName)
    }

    @Test
    fun `resolves view entity for Kotlin target`() {
        val reusableTypeId = typeId("contract.BookView")
        val (schema, workspace) = schemaAndWorkspace(
            declarations = listOf(
                declaration(
                    id = reusableTypeId,
                    superTypes = listOf(declared(VIEW_TYPE_ID, LsiDeclaredType(BOOK_TYPE_ID))),
                ),
            ),
        )

        val typeInfo = workspace.resolveDtoTypeInfo(
            schema,
            reusableTypeId.requireTypeQualifiedName(),
            LsiLanguage.KOTLIN,
        )

        assertEquals(DtoTypeKind.VIEW, typeInfo?.kind)
        assertEquals(BOOK_TYPE_ID.requireTypeQualifiedName(), typeInfo?.baseTypeQualifiedName)
    }

    @Test
    fun `uses target language specification marker`() {
        val javaSpecificationId = typeId("contract.BookJavaSpecification")
        val kotlinSpecificationId = typeId("contract.BookKotlinSpecification")
        val (schema, workspace) = schemaAndWorkspace(
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
            workspace.resolveDtoTypeInfo(
                schema,
                javaSpecificationId.requireTypeQualifiedName(),
                LsiLanguage.JAVA,
            )?.kind,
        )
        assertNull(
            workspace.resolveDtoTypeInfo(
                schema,
                kotlinSpecificationId.requireTypeQualifiedName(),
                LsiLanguage.JAVA,
            )
        )
        assertEquals(
            DtoTypeKind.SPECIFICATION,
            workspace.resolveDtoTypeInfo(
                schema,
                kotlinSpecificationId.requireTypeQualifiedName(),
                LsiLanguage.KOTLIN,
            )?.kind,
        )
        assertNull(
            workspace.resolveDtoTypeInfo(
                schema,
                javaSpecificationId.requireTypeQualifiedName(),
                LsiLanguage.KOTLIN,
            )
        )
    }

    @Test
    fun `returns null for non DTO type`() {
        val otherTypeId = typeId("contract.Other")
        val (schema, workspace) = schemaAndWorkspace(listOf(declaration(otherTypeId)))

        assertNull(
            workspace.resolveDtoTypeInfo(schema, otherTypeId.requireTypeQualifiedName(), LsiLanguage.JAVA)
        )
        assertNull(workspace.resolveDtoTypeInfo(schema, "contract.Missing", LsiLanguage.KOTLIN))
    }

    @Test
    fun `rejects DTO whose entity argument is not immutable`() {
        val reusableTypeId = typeId("contract.InvalidView")
        val (schema, workspace) = schemaAndWorkspace(
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
            workspace.resolveDtoTypeInfo(
                schema,
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
    fun `rejects reusable DTO declarations with type parameters`() {
        val reusableTypeId = typeId("contract.GenericBookInput")
        val parameterId = LsiSymbolId.typeParameter(reusableTypeId, "T")
        val (schema, workspace) = schemaAndWorkspace(
            declarations = listOf(
                declaration(
                    id = reusableTypeId,
                    typeParameters = listOf(LsiTypeParameter(parameterId, "T")),
                    superTypes = listOf(declared(INPUT_TYPE_ID, LsiDeclaredType(BOOK_TYPE_ID))),
                ),
            ),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            workspace.resolveDtoTypeInfo(
                schema,
                reusableTypeId.requireTypeQualifiedName(),
                LsiLanguage.JAVA,
            )
        }

        assertEquals(
            "Reusable DTO type \"contract.GenericBookInput\" cannot declare type parameters",
            exception.message,
        )
    }

    @Test
    fun `rejects unknown target language`() {
        val (schema, workspace) = schemaAndWorkspace(emptyList())

        val exception = assertFailsWith<IllegalArgumentException> {
            workspace.resolveDtoTypeInfo(schema, "contract.Missing", LsiLanguage.UNKNOWN)
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
        assertSame(storeProp, schema.idViewBasePropOf(storeIdViewProp))
        assertSame(linksProp, schema.manyToManyViewBasePropOf(authorsViewProp))
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
            immutableSchema = schema,
            workspace = LsiWorkspace.EMPTY,
            defaultNullableInputModifier = AstDtoModifier.STATIC,
        )
        val compiledTypes = compiler.compile(schema.typesById.getValue(BOOK_TYPE_ID))

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

    @Test
    fun `inherited DTO compiler order places id before other scalar properties`() {
        val baseTypeId = typeId("demo.BaseRecord")
        val administratorTypeId = typeId("demo.Administrator")
        val baseName = prop(
            ownerTypeId = baseTypeId,
            name = "name",
            type = STRING_TYPE,
        )
        val inheritedName = baseName.copy(
            id = LsiSymbolId.property(administratorTypeId, "name"),
            ownerTypeId = administratorTypeId,
            declarationId = baseName.id,
            declaringTypeId = baseTypeId,
            overrideChain = listOf(baseName.id),
            inherited = true,
        )
        val administratorId = idProp(administratorTypeId)
        val baseType = ImmutableType(
            id = baseTypeId,
            qualifiedName = baseTypeId.requireTypeQualifiedName(),
            kind = ImmutableTypeKind.MAPPED_SUPERCLASS,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = emptyList(),
            props = listOf(baseName),
            primarySuperTypeId = null,
            inheritanceRootTypeId = null,
            inheritanceStrategy = null,
            joinedTableDissociateAction = null,
            instantiable = false,
            discriminatorValue = null,
            discriminatorPropId = null,
            idPropId = null,
            versionPropId = null,
            logicalDeletedPropId = null,
            acrossMicroServices = false,
            microServiceName = "",
        )
        val administratorType = ImmutableType(
            id = administratorTypeId,
            qualifiedName = administratorTypeId.requireTypeQualifiedName(),
            kind = ImmutableTypeKind.ENTITY,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = listOf(baseTypeId),
            props = listOf(inheritedName, administratorId),
            primarySuperTypeId = baseTypeId,
            inheritanceRootTypeId = null,
            inheritanceStrategy = null,
            joinedTableDissociateAction = null,
            instantiable = true,
            discriminatorValue = null,
            discriminatorPropId = null,
            idPropId = administratorId.id,
            versionPropId = null,
            logicalDeletedPropId = null,
            acrossMicroServices = false,
            microServiceName = "",
        )
        val source = LsiSource.of(
            path = "src/main/dto/demo/Administrator.dto",
            language = LsiLanguage.KOTLIN,
        )
        val schema = ImmutableSchema(listOf(baseType, administratorType))
        val dtoFile = DtoFile(
            source.path,
            "demo/Administrator.dto",
            """
                input AdministratorInput {
                    #allScalars
                }
            """.trimIndent(),
        )
        val compiledTypes = dtoFile.toLsiDtoCompiler(
            immutableSchema = schema,
            workspace = LsiWorkspace.EMPTY,
            defaultNullableInputModifier = AstDtoModifier.STATIC,
        ).compile(schema.typesById.getValue(administratorTypeId))

        val graph = compiledTypes.toLsiDtoGraph(source)
        val rootType = graph.typesById.getValue(graph.rootTypeIds.single())

        assertEquals(
            listOf("id", "name"),
            rootType.propIds.map { propId -> graph.propsById.getValue(propId).name },
        )
    }

    private fun schemaAndWorkspace(
        declarations: List<LsiTypeDeclaration>,
    ): Pair<ImmutableSchema, LsiWorkspace> {
        val workspace = LsiWorkspace(declarations = declarations)
        val book = immutableEntity(BOOK_TYPE_ID, listOf(idProp(BOOK_TYPE_ID)))
        return ImmutableSchema(listOf(book)) to workspace
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
