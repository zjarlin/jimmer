package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.babyfish.jimmer.compiler.render.apt.AptDtoSpecificationRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoSpecificationRenderer
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableConverter
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoEnumMapping
import site.addzero.lsi.jimmer.dto.DtoEnumType
import site.addzero.lsi.jimmer.dto.DtoFoldProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import org.babyfish.jimmer.dto.compiler.LikeOption
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class DtoSpecificationPoetTest {

    @Test
    fun `renders root entity type contract for Java and Kotlin`() {
        val baseType = entityType()
        val schema = ImmutableSchema(listOf(baseType))
        val dtoType = specification(baseType.id)
        val workspace = workspace(listOf(baseType.id))

        val javaMethod = AptDtoSpecificationRenderer
            .renderEntityType(dtoType, schema, workspace)
            .toString()
            .trimEnd()
        val kotlinMethod = KspDtoSpecificationRenderer
            .renderEntityType(dtoType, schema, workspace)
            .toString()
            .trimEnd()

        assertEquals(
            """
                @java.lang.Override
                public java.lang.Class<demo.Book> entityType() {
                  return demo.Book.class;
                }
            """.trimIndent(),
            javaMethod,
        )
        assertEquals(
            "override fun entityType(): java.lang.Class<demo.Book> = demo.Book::class.java",
            kotlinMethod,
        )
    }

    @Test
    fun `omits override for nested specification fragment`() {
        val baseType = immutableType(
            id = FRAGMENT_TYPE_ID,
            kind = ImmutableTypeKind.EMBEDDABLE,
            props = emptyList(),
        )
        val schema = ImmutableSchema(listOf(baseType))
        val dtoType = specification(baseType.id)
        val workspace = workspace(listOf(baseType.id))

        val javaMethod = AptDtoSpecificationRenderer
            .renderEntityType(dtoType, schema, workspace)
            .toString()
            .trimEnd()
        val kotlinMethod = KspDtoSpecificationRenderer
            .renderEntityType(dtoType, schema, workspace)
            .toString()
            .trimEnd()

        assertEquals(
            """
                public java.lang.Class<demo.Location> entityType() {
                  return demo.Location.class;
                }
            """.trimIndent(),
            javaMethod,
        )
        assertEquals(
            "public fun entityType(): java.lang.Class<demo.Location> = demo.Location::class.java",
            kotlinMethod,
        )
    }

    @Test
    fun `renders complete root applyTo contract for Java and Kotlin`() {
        val fixture = applyToFixture()

        assertEquals(
            """
                @java.lang.Override
                public void applyTo(
                    org.babyfish.jimmer.sql.ast.query.specification.SpecificationArgs<demo.Book, demo.BookTable> args) {
                  org.babyfish.jimmer.sql.ast.query.specification.PredicateApplier __applier = args.getApplier();
                  __applier.push(demo.BookProps.STORE.unwrap());
                  __applier.ge(demo.StoreProps.NAME.unwrap(), this.getMinStoreName());
                  __applier.like(new org.babyfish.jimmer.meta.ImmutableProp[] { demo.StoreProps.NAME.unwrap() }, this.getStoreName(), true, false, true);
                  __applier.pop();
                  __applier.push(demo.BookProps.AUTHORS.unwrap());
                  __applier.like(new org.babyfish.jimmer.meta.ImmutableProp[] { demo.AuthorProps.FIRST_NAME.unwrap(), demo.AuthorProps.LAST_NAME.unwrap() }, this.getAuthorName(), false, true, false);
                  __applier.pop();
                  __applier.push(demo.BookProps.STORE.unwrap());
                  if (this.storeFilter != null) {
                    this.storeFilter.applyTo(args.child());
                  }
                  __applier.pop();
                  __applier.push(demo.BookProps.LOCATION.unwrap());
                  if (this.locationFilter != null) {
                    this.locationFilter.applyTo(args.getApplier());
                  }
                  __applier.pop();
                  if (this.summary != null) {
                    this.summary.applyTo(args);
                  }
                  __applier.eq(new org.babyfish.jimmer.meta.ImmutableProp[] { demo.BookProps.STATUS.unwrap() }, __convertStatus(this.getStatus()));
                }
            """.trimIndent(),
            fixture.renderRoot(LsiLanguage.JAVA),
        )
        assertEquals(
            """
                override fun applyTo(args: org.babyfish.jimmer.sql.kt.ast.query.specification.KSpecificationArgs<demo.Book>) {
                  val _applier = args.applier
                  _applier.push(demo.BookProps.STORE.unwrap())
                  _applier.ge(demo.StoreProps.NAME.unwrap(), this.minStoreName)
                  _applier.like(arrayOf(demo.StoreProps.NAME.unwrap()), this.storeName, true, false, true)
                  _applier.pop()
                  _applier.push(demo.BookProps.AUTHORS.unwrap())
                  _applier.like(arrayOf(demo.AuthorProps.FIRST_NAME.unwrap(), demo.AuthorProps.LAST_NAME.unwrap()), this.authorName, false, true, false)
                  _applier.pop()
                  _applier.push(demo.BookProps.STORE.unwrap())
                  this.storeFilter?.let { it.applyTo(args.child()) }
                  _applier.pop()
                  _applier.push(demo.BookProps.LOCATION.unwrap())
                  this.locationFilter?.let { it.applyTo(args.applier) }
                  _applier.pop()
                  this.summary?.applyTo(args)
                  _applier.eq(arrayOf(demo.BookProps.STATUS.unwrap()), _convertStatus(this.status))
                }
            """.trimIndent(),
            fixture.renderRoot(LsiLanguage.KOTLIN),
        )
    }

    @Test
    fun `renders nested applyTo contract for Java and Kotlin`() {
        val fixture = applyToFixture()

        assertEquals(
            """
                public void applyTo(org.babyfish.jimmer.sql.ast.query.specification.PredicateApplier __applier) {
                  __applier.eq(new org.babyfish.jimmer.meta.ImmutableProp[] { demo.LocationProps.HOST.unwrap() }, this.getHost());
                  __applier.eq(new org.babyfish.jimmer.meta.ImmutableProp[] { demo.LocationProps.PORT.unwrap() }, this.getPort());
                }
            """.trimIndent(),
            fixture.renderNested(LsiLanguage.JAVA),
        )
        assertEquals(
            """
                public fun applyTo(_applier: org.babyfish.jimmer.sql.ast.query.specification.PredicateApplier) {
                  _applier.eq(arrayOf(demo.LocationProps.HOST.unwrap()), this.host)
                  _applier.eq(arrayOf(demo.LocationProps.PORT.unwrap()), this.port)
                }
            """.trimIndent(),
            fixture.renderNested(LsiLanguage.KOTLIN),
        )
    }

    @Test
    fun `renders converter methods with scalar and collection semantics for Java`() {
        val fixture = applyToFixture()

        assertEquals(
            """
                private java.lang.Long __convertConvertedCode(java.lang.String value) {
                  if (convertedCode == null) {
                    return null;
                  }
                  return demo.BookProps.CONVERTED_CODE.unwrap().<java.lang.Long, java.lang.String>getConverter().input(value);
                }
            """.trimIndent(),
            fixture.renderConverter(fixture.convertedCode, LsiLanguage.JAVA),
        )
        assertEquals(
            """
                private java.util.List<java.lang.Long> __convertConvertedCodes(
                    java.util.Collection<java.lang.String> value) {
                  if (convertedCodes == null) {
                    return null;
                  }
                  return demo.BookProps.CONVERTED_CODE.unwrap().<java.util.List<java.lang.Long>, java.util.Collection<java.lang.String>>getConverter(true).input(value);
                }
            """.trimIndent(),
            fixture.renderConverter(fixture.convertedCodes, LsiLanguage.JAVA),
        )
        assertEquals(
            """
                private java.lang.Long __convertStoreId(java.lang.String value) {
                  if (storeId == null) {
                    return null;
                  }
                  return demo.BookProps.STORE.unwrap().<java.lang.Long, java.lang.String>getAssociatedIdConverter(false).input(value);
                }
            """.trimIndent(),
            fixture.renderConverter(fixture.storeId, LsiLanguage.JAVA),
        )
        assertEquals(
            """
                private java.util.List<java.lang.Long> __convertAuthorIds(
                    java.util.Collection<java.lang.String> value) {
                  if (authorIds == null) {
                    return null;
                  }
                  return demo.BookProps.AUTHORS.unwrap().<java.util.List<java.lang.Long>, java.util.Collection<java.lang.String>>getAssociatedIdConverter(true).input(value);
                }
            """.trimIndent(),
            fixture.renderConverter(fixture.authorIds, LsiLanguage.JAVA),
        )
    }

    @Test
    fun `renders converter methods with scalar and collection semantics for Kotlin`() {
        val fixture = applyToFixture()

        assertEquals(
            """
                public fun _convertConvertedCode(`value`: kotlin.String?): kotlin.Long? {
                  if (value === null) {
                    return null
                  }
                  return demo.BookProps.CONVERTED_CODE.unwrap().getConverter<kotlin.Long?, kotlin.String>().input(value)
                }
            """.trimIndent(),
            fixture.renderConverter(fixture.convertedCode, LsiLanguage.KOTLIN),
        )
        assertEquals(
            """
                public fun _convertConvertedCodes(`value`: kotlin.collections.Collection<kotlin.String>?): kotlin.collections.List<kotlin.Long>? {
                  if (value === null) {
                    return null
                  }
                  return demo.BookProps.CONVERTED_CODE.unwrap().getConverter<kotlin.collections.List<kotlin.Long>?, kotlin.collections.Collection<kotlin.String>>(true).input(value)
                }
            """.trimIndent(),
            fixture.renderConverter(fixture.convertedCodes, LsiLanguage.KOTLIN),
        )
        assertEquals(
            """
                public fun _convertStoreId(`value`: kotlin.String?): kotlin.Long? {
                  if (value === null) {
                    return null
                  }
                  return demo.BookProps.STORE.unwrap().getAssociatedIdConverter<kotlin.Long?, kotlin.String>(false).input(value)
                }
            """.trimIndent(),
            fixture.renderConverter(fixture.storeId, LsiLanguage.KOTLIN),
        )
        assertEquals(
            """
                public fun _convertAuthorIds(`value`: kotlin.collections.Collection<kotlin.String>?): kotlin.collections.List<kotlin.Long>? {
                  if (value === null) {
                    return null
                  }
                  return demo.BookProps.AUTHORS.unwrap().getAssociatedIdConverter<kotlin.collections.List<kotlin.Long>?, kotlin.collections.Collection<kotlin.String>>(true).input(value)
                }
            """.trimIndent(),
            fixture.renderConverter(fixture.authorIds, LsiLanguage.KOTLIN),
        )
    }

    @Test
    fun `renders enum converter and rejects unsupported targets`() {
        val fixture = applyToFixture()

        assertEquals(
            """
                private demo.Status __convertStatus(java.lang.Integer value) {
                  if (status == null) {
                    return null;
                  }
                  switch ((int)value) {
                    case 1:
                      return demo.Status.ACTIVE;
                    case 0:
                      return demo.Status.INACTIVE;
                    default:
                      throw new IllegalArgumentException("Illegal value `\"" + value + "\"`for enum type: \"demo.Status\"");
                  }
                }
            """.trimIndent(),
            fixture.renderConverter(fixture.status, LsiLanguage.JAVA),
        )
        assertEquals(
            """
                public fun _convertStatus(`value`: kotlin.Int?): demo.Status? {
                  if (value === null) {
                    return null
                  }
                  return when (value as kotlin.Int) {
                    1 -> demo.Status.ACTIVE
                    0 -> demo.Status.INACTIVE
                    else -> throw IllegalArgumentException(
                      "Illegal value \"" + value + "\" for the enum type \"demo.Status\""
                    )
                  }
                }
            """.trimIndent(),
            fixture.renderConverter(fixture.status, LsiLanguage.KOTLIN),
        )
        assertNull(fixture.renderConverter(fixture.plain, LsiLanguage.JAVA))
        assertFailsWith<IllegalArgumentException> {
            fixture.status.toLsiSpecificationConverterPoetFunctionOrNull(
                fixture.graph,
                fixture.schema,
                LsiLanguage.UNKNOWN,
            )
        }
    }

    private fun specification(baseTypeId: LsiSymbolId): DtoType {
        return DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = baseTypeId,
            packageName = "demo.dto",
            name = "SampleSpecification",
            modifiers = setOf(DtoModifier.SPECIFICATION),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
    }

    private fun applyToFixture(): ApplyToFixture {
        val bookId = immutableProp(BOOK_TYPE_ID, "id", PrimaryMapping.ID)
        val bookStore = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "store",
            primaryMapping = PrimaryMapping.ASSOCIATION,
            type = LsiDeclaredType(STORE_TYPE_ID),
            targetTypeId = STORE_TYPE_ID,
            associationKind = AssociationKind.MANY_TO_ONE,
            associationStorage = AssociationStorageKind.COLUMN,
        )
        val bookAuthors = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "authors",
            primaryMapping = PrimaryMapping.ASSOCIATION,
            type = LsiDeclaredType(AUTHOR_TYPE_ID),
            targetTypeId = AUTHOR_TYPE_ID,
            list = true,
            associationKind = AssociationKind.MANY_TO_MANY,
            associationStorage = AssociationStorageKind.MIDDLE_TABLE,
        )
        val bookLocation = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "location",
            primaryMapping = PrimaryMapping.SCALAR,
            type = LsiDeclaredType(LOCATION_TYPE_ID),
            targetTypeId = LOCATION_TYPE_ID,
            embedded = true,
        )
        val bookStatus = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "status",
            primaryMapping = PrimaryMapping.SCALAR,
            type = LsiDeclaredType(STATUS_TYPE_ID),
        )
        val bookConvertedCode = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "convertedCode",
            primaryMapping = PrimaryMapping.SCALAR,
            converter = longToStringConverter(),
        )
        val bookPlain = immutableProp(
            ownerTypeId = BOOK_TYPE_ID,
            name = "plain",
            primaryMapping = PrimaryMapping.SCALAR,
        )
        val storeId = immutableProp(
            STORE_TYPE_ID,
            "id",
            PrimaryMapping.ID,
            converter = longToStringConverter(),
        )
        val storeName = immutableProp(
            STORE_TYPE_ID,
            "name",
            PrimaryMapping.SCALAR,
            LsiDeclaredType(STRING_TYPE_ID),
        )
        val authorId = immutableProp(
            AUTHOR_TYPE_ID,
            "id",
            PrimaryMapping.ID,
            converter = longToStringConverter(),
        )
        val authorFirstName = immutableProp(
            AUTHOR_TYPE_ID,
            "firstName",
            PrimaryMapping.SCALAR,
            LsiDeclaredType(STRING_TYPE_ID),
        )
        val authorLastName = immutableProp(
            AUTHOR_TYPE_ID,
            "lastName",
            PrimaryMapping.SCALAR,
            LsiDeclaredType(STRING_TYPE_ID),
        )
        val locationHost = immutableProp(
            LOCATION_TYPE_ID,
            "host",
            PrimaryMapping.SCALAR,
            LsiDeclaredType(STRING_TYPE_ID),
        )
        val locationPort = immutableProp(
            LOCATION_TYPE_ID,
            "port",
            PrimaryMapping.SCALAR,
            LsiPrimitiveType(LsiPrimitiveKind.INT),
        )
        val schema = ImmutableSchema(
            listOf(
                immutableType(
                    AUTHOR_TYPE_ID,
                    ImmutableTypeKind.ENTITY,
                    listOf(authorId, authorFirstName, authorLastName),
                    authorId.id,
                ),
                immutableType(
                    BOOK_TYPE_ID,
                    ImmutableTypeKind.ENTITY,
                    listOf(
                        bookId,
                        bookStore,
                        bookAuthors,
                        bookLocation,
                        bookStatus,
                        bookConvertedCode,
                        bookPlain,
                    ),
                    bookId.id,
                ),
                immutableType(
                    LOCATION_TYPE_ID,
                    ImmutableTypeKind.EMBEDDABLE,
                    listOf(locationHost, locationPort),
                ),
                immutableType(
                    STORE_TYPE_ID,
                    ImmutableTypeKind.ENTITY,
                    listOf(storeId, storeName),
                    storeId.id,
                ),
            ),
        )

        val props = mutableListOf<DtoProp>()
        val visibleProps = mutableListOf<DtoProp>()
        val hiddenProps = mutableListOf<DtoBaseProp>()
        fun pathProp(
            name: String,
            pathProp: ImmutableProp,
            argumentProps: List<ImmutableProp>,
            functionName: String,
            likeOptions: Set<LikeOption> = emptySet(),
        ) {
            val headId = DtoPropId("${ROOT_SPEC_TYPE_ID.value}#prop:$name")
            val tailId = DtoPropId("${ROOT_SPEC_TYPE_ID.value}#tail:$name")
            val tail = specificationProp(
                id = tailId,
                ownerTypeId = ROOT_SPEC_TYPE_ID,
                name = "${name}Tail",
                baseProps = argumentProps,
                functionName = functionName,
                likeOptions = likeOptions,
            )
            val head = specificationProp(
                id = headId,
                ownerTypeId = ROOT_SPEC_TYPE_ID,
                name = name,
                baseProps = listOf(pathProp),
                nextPropId = tail.id,
                tailPropId = tail.id,
            )
            props += listOf(head, tail)
            visibleProps += head
            hiddenProps += tail
        }
        pathProp("minStoreName", bookStore, listOf(storeName), "ge")
        pathProp(
            name = "storeName",
            pathProp = bookStore,
            argumentProps = listOf(storeName),
            functionName = "like",
            likeOptions = setOf(LikeOption.INSENSITIVE, LikeOption.MATCH_END),
        )
        pathProp(
            name = "authorName",
            pathProp = bookAuthors,
            argumentProps = listOf(authorFirstName, authorLastName),
            functionName = "like",
            likeOptions = setOf(LikeOption.MATCH_START),
        )
        val storeFilter = specificationProp(
            id = DtoPropId("${ROOT_SPEC_TYPE_ID.value}#prop:storeFilter"),
            ownerTypeId = ROOT_SPEC_TYPE_ID,
            name = "storeFilter",
            baseProps = listOf(bookStore),
            targetTypeId = STORE_TARGET_TYPE_ID,
        )
        val locationFilter = specificationProp(
            id = DtoPropId("${ROOT_SPEC_TYPE_ID.value}#prop:locationFilter"),
            ownerTypeId = ROOT_SPEC_TYPE_ID,
            name = "locationFilter",
            baseProps = listOf(bookLocation),
            targetTypeId = LOCATION_TARGET_TYPE_ID,
        )
        val summary = DtoFoldProp(
            id = DtoPropId("${ROOT_SPEC_TYPE_ID.value}#prop:summary"),
            ownerTypeId = ROOT_SPEC_TYPE_ID,
            name = "summary",
            alias = "summary",
            nullable = true,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            nullGuardPropId = null,
            targetTypeId = SUMMARY_TARGET_TYPE_ID,
        )
        val status = specificationProp(
            id = DtoPropId("${ROOT_SPEC_TYPE_ID.value}#prop:status"),
            ownerTypeId = ROOT_SPEC_TYPE_ID,
            name = "status",
            baseProps = listOf(bookStatus),
            functionName = "eq",
            enumType = DtoEnumType(
                numeric = true,
                mappings = listOf(
                    DtoEnumMapping("ACTIVE", "1"),
                    DtoEnumMapping("INACTIVE", "0"),
                ),
            ),
        )
        visibleProps += listOf(storeFilter, locationFilter, summary, status)
        props += listOf(storeFilter, locationFilter, summary, status)
        val convertedCode = specificationProp(
            id = DtoPropId("${ROOT_SPEC_TYPE_ID.value}#converter:convertedCode"),
            ownerTypeId = ROOT_SPEC_TYPE_ID,
            name = "convertedCode",
            baseProps = listOf(bookConvertedCode),
        )
        val convertedCodes = specificationProp(
            id = DtoPropId("${ROOT_SPEC_TYPE_ID.value}#converter:convertedCodes"),
            ownerTypeId = ROOT_SPEC_TYPE_ID,
            name = "convertedCodes",
            baseProps = listOf(bookConvertedCode),
            functionName = "valueIn",
        )
        val storeIdDto = specificationProp(
            id = DtoPropId("${ROOT_SPEC_TYPE_ID.value}#converter:storeId"),
            ownerTypeId = ROOT_SPEC_TYPE_ID,
            name = "storeId",
            baseProps = listOf(bookStore),
            functionName = "associatedIdEq",
        )
        val authorIds = specificationProp(
            id = DtoPropId("${ROOT_SPEC_TYPE_ID.value}#converter:authorIds"),
            ownerTypeId = ROOT_SPEC_TYPE_ID,
            name = "authorIds",
            baseProps = listOf(bookAuthors),
            functionName = "associatedIdIn",
        )
        val plain = specificationProp(
            id = DtoPropId("${ROOT_SPEC_TYPE_ID.value}#converter:plain"),
            ownerTypeId = ROOT_SPEC_TYPE_ID,
            name = "plain",
            baseProps = listOf(bookPlain),
        )
        props += listOf(convertedCode, convertedCodes, storeIdDto, authorIds, plain)

        val locationHostDto = specificationProp(
            id = DtoPropId("${LOCATION_TARGET_TYPE_ID.value}#prop:host"),
            ownerTypeId = LOCATION_TARGET_TYPE_ID,
            name = "host",
            baseProps = listOf(locationHost),
            functionName = "eq",
        )
        val locationPortDto = specificationProp(
            id = DtoPropId("${LOCATION_TARGET_TYPE_ID.value}#prop:port"),
            ownerTypeId = LOCATION_TARGET_TYPE_ID,
            name = "port",
            baseProps = listOf(locationPort),
            functionName = "eq",
        )
        props += listOf(locationHostDto, locationPortDto)

        val rootType = specificationType(
            id = ROOT_SPEC_TYPE_ID,
            baseTypeId = BOOK_TYPE_ID,
            name = "BookSpecification",
            propIds = visibleProps.map(DtoProp::id),
            hiddenFlatPropIds = hiddenProps.map(DtoProp::id),
        )
        val locationTargetType = specificationType(
            id = LOCATION_TARGET_TYPE_ID,
            baseTypeId = LOCATION_TYPE_ID,
            name = null,
            propIds = listOf(locationHostDto.id, locationPortDto.id),
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(rootType.id),
            types = listOf(
                rootType,
                locationTargetType,
                specificationType(STORE_TARGET_TYPE_ID, STORE_TYPE_ID, null),
                specificationType(SUMMARY_TARGET_TYPE_ID, BOOK_TYPE_ID, null),
            ).sortedBy(DtoType::id),
            props = props.sortedBy(DtoProp::id),
        )
        return ApplyToFixture(
            graph = graph,
            schema = schema,
            workspace = workspace(listOf(BOOK_TYPE_ID, STATUS_TYPE_ID)),
            rootType = rootType,
            nestedType = locationTargetType,
            convertedCode = convertedCode,
            convertedCodes = convertedCodes,
            storeId = storeIdDto,
            authorIds = authorIds,
            status = status,
            plain = plain,
        )
    }

    private fun specificationProp(
        id: DtoPropId,
        ownerTypeId: DtoTypeId,
        name: String,
        baseProps: List<ImmutableProp>,
        nextPropId: DtoPropId? = null,
        tailPropId: DtoPropId = id,
        functionName: String? = null,
        targetTypeId: DtoTypeId? = null,
        enumType: DtoEnumType? = null,
        likeOptions: Set<LikeOption> = emptySet(),
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = ownerTypeId,
            name = name,
            alias = null,
            nullable = true,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = baseProps.map { prop -> DtoBasePropBinding(prop.name, prop.id) },
            basePath = baseProps.joinToString(".", transform = ImmutableProp::name),
            nextPropId = nextPropId,
            tailPropId = tailPropId,
            baseNullable = true,
            inputModifier = DtoModifier.STATIC,
            functionName = functionName,
            targetTypeId = targetTypeId,
            enumType = enumType,
            config = null,
            recursive = false,
            likeOptions = likeOptions,
        )
    }

    private fun specificationType(
        id: DtoTypeId,
        baseTypeId: LsiSymbolId,
        name: String?,
        propIds: List<DtoPropId> = emptyList(),
        hiddenFlatPropIds: List<DtoPropId> = emptyList(),
    ): DtoType {
        return DtoType(
            id = id,
            baseTypeId = baseTypeId,
            packageName = "demo.dto",
            name = name,
            modifiers = setOf(DtoModifier.SPECIFICATION),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = propIds,
            hiddenFlatPropIds = hiddenFlatPropIds,
            polymorphism = null,
        )
    }

    private fun entityType(): ImmutableType {
        val idProp = immutableProp(ENTITY_TYPE_ID, "id", PrimaryMapping.ID)
        return immutableType(
            id = ENTITY_TYPE_ID,
            kind = ImmutableTypeKind.ENTITY,
            props = listOf(idProp),
            idPropId = idProp.id,
        )
    }

    private fun immutableType(
        id: LsiSymbolId,
        kind: ImmutableTypeKind,
        props: List<ImmutableProp>,
        idPropId: LsiSymbolId? = null,
    ): ImmutableType {
        return ImmutableType(
            id = id,
            qualifiedName = id.requireTypeQualifiedName(),
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

    private fun immutableProp(
        ownerTypeId: LsiSymbolId,
        name: String,
        primaryMapping: PrimaryMapping,
        type: LsiTypeRef = LsiPrimitiveType(LsiPrimitiveKind.LONG),
        targetTypeId: LsiSymbolId? = null,
        list: Boolean = false,
        embedded: Boolean = false,
        associationKind: AssociationKind = AssociationKind.NONE,
        associationStorage: AssociationStorageKind = AssociationStorageKind.NONE,
        converter: ImmutableConverter? = null,
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
            annotations = emptyList(),
            overrideChain = emptyList(),
            inherited = false,
            overridden = false,
            nullable = false,
            list = list,
            association = associationKind != AssociationKind.NONE,
            embedded = embedded,
            targetTypeId = targetTypeId,
            primaryMapping = primaryMapping,
            primaryAnnotationTypeId = null,
            defaultContract = null,
            associationKind = associationKind,
            formulaKind = FormulaKind.NONE,
            mappedBy = null,
            associationStorage = associationStorage,
            transientResolver = null,
            view = null,
            genericTarget = false,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = converter,
        )
    }

    private fun longToStringConverter(): ImmutableConverter {
        return ImmutableConverter(
            converterTypeId = LONG_TO_STRING_CONVERTER_TYPE_ID,
            sourceType = LsiPrimitiveType(LsiPrimitiveKind.LONG),
            targetType = LsiDeclaredType(STRING_TYPE_ID),
            sourceNullable = false,
            targetNullable = false,
            propertyNullable = false,
        )
    }

    private data class ApplyToFixture(
        val graph: DtoGraph,
        val schema: ImmutableSchema,
        val workspace: LsiWorkspace,
        val rootType: DtoType,
        val nestedType: DtoType,
        val convertedCode: DtoBaseProp,
        val convertedCodes: DtoBaseProp,
        val storeId: DtoBaseProp,
        val authorIds: DtoBaseProp,
        val status: DtoBaseProp,
        val plain: DtoBaseProp,
    ) {
        fun renderRoot(language: LsiLanguage): String = render(rootType, language)

        fun renderNested(language: LsiLanguage): String = render(nestedType, language)

        fun renderConverter(prop: DtoBaseProp, language: LsiLanguage): String? {
            val function = prop.toLsiSpecificationConverterPoetFunctionOrNull(
                graph,
                schema,
                language,
            ) ?: return null
            val typeNames = workspace.dtoSpecificationPoetTypeNames(function, schema)
            return when (language) {
                LsiLanguage.JAVA -> LsiJavaPoetRenderer()
                    .renderFunction(function, typeNames)
                    .toString()
                    .trimEnd()
                LsiLanguage.KOTLIN -> LsiKotlinPoetRenderer()
                    .renderFunction(function, typeNames)
                    .toString()
                    .trimEnd()
                LsiLanguage.UNKNOWN -> error("测试只支持 Java 或 Kotlin")
            }
        }

        private fun render(type: DtoType, language: LsiLanguage): String {
            val function = type.toLsiSpecificationApplyToPoetFunction(graph, schema, language)
            val typeNames = workspace.dtoSpecificationPoetTypeNames(function, schema)
            return when (language) {
                LsiLanguage.JAVA -> LsiJavaPoetRenderer()
                    .renderFunction(function, typeNames)
                    .toString()
                    .trimEnd()
                LsiLanguage.KOTLIN -> LsiKotlinPoetRenderer()
                    .renderFunction(function, typeNames)
                    .toString()
                    .trimEnd()
                LsiLanguage.UNKNOWN -> error("测试只支持 Java 或 Kotlin")
            }
        }
    }

    private fun workspace(typeIds: List<LsiSymbolId>): LsiWorkspace {
        return LsiWorkspace(
            declarations = typeIds.map { typeId ->
                LsiTypeDeclaration(
                    id = typeId,
                    name = typeId.requireTypeQualifiedName().substringAfterLast('.'),
                    qualifiedName = typeId.requireTypeQualifiedName(),
                    kind = LsiTypeDeclarationKind.INTERFACE,
                    origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
                )
            }.sortedBy(LsiTypeDeclaration::id),
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("src/main/dto/demo/Sample.dto", LsiLanguage.KOTLIN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1), LsiPosition(1, 1))
        val DTO_TYPE_ID = DtoTypeId("demo.dto.SampleSpecification")
        val ENTITY_TYPE_ID = LsiSymbolId.type("demo.Book")
        val FRAGMENT_TYPE_ID = LsiSymbolId.type("demo.Location")
        val BOOK_TYPE_ID = LsiSymbolId.type("demo.Book")
        val STORE_TYPE_ID = LsiSymbolId.type("demo.Store")
        val AUTHOR_TYPE_ID = LsiSymbolId.type("demo.Author")
        val LOCATION_TYPE_ID = LsiSymbolId.type("demo.Location")
        val STATUS_TYPE_ID = LsiSymbolId.type("demo.Status")
        val STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")
        val LONG_TO_STRING_CONVERTER_TYPE_ID = LsiSymbolId.type("demo.LongToStringConverter")
        val ROOT_SPEC_TYPE_ID = DtoTypeId("demo.dto.BookSpecification")
        val STORE_TARGET_TYPE_ID = DtoTypeId("demo.dto.BookSpecification#target:store")
        val LOCATION_TARGET_TYPE_ID = DtoTypeId("demo.dto.BookSpecification#target:location")
        val SUMMARY_TARGET_TYPE_ID = DtoTypeId("demo.dto.BookSpecification#fold:summary")
    }
}
