package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.JacksonTypes
import site.addzero.lsi.codegen.KOTLIN_UNIT_LSI_CLASS_NAME
import site.addzero.lsi.codegen.LsiClassName
import site.addzero.lsi.jimmer.immutable.metadata.extractor.ImmutableResolvedSource
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableAssociatedIdMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableBuilderSetterMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableBuilderTypeMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableCallbackMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableFetcherFieldKind
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableFetcherPropMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableFetcherTypeMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsIdMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsPropMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsTypeMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsTypeRefMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableSourceMetadata
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.LsiTypeName

internal object ImmutableGeneratorTestFixtures {

    const val SOURCE_PACKAGE_NAME: String = "test.model"
    const val SOURCE_FILE_NAME: String = "Book"
    const val CURRENT_VERSION_VALUE: String = "test-version"

    fun sourceMetadata(): ImmutableSourceMetadata =
        ImmutableSourceMetadata(
            sourceKey = "$SOURCE_PACKAGE_NAME.$SOURCE_FILE_NAME",
            sourcePackageName = SOURCE_PACKAGE_NAME,
            sourceFileName = SOURCE_FILE_NAME,
            typeQualifiedNames = listOf("$SOURCE_PACKAGE_NAME.$SOURCE_FILE_NAME"),
            sqlTypeQualifiedName = "$SOURCE_PACKAGE_NAME.$SOURCE_FILE_NAME",
            fetcherTypeQualifiedName = "$SOURCE_PACKAGE_NAME.${SOURCE_FILE_NAME}Fetcher",
            entityQualifiedNames = listOf("$SOURCE_PACKAGE_NAME.$SOURCE_FILE_NAME"),
        )

    fun resolvedSourceWithReferenceFetcher(): ImmutableResolvedSource =
        ImmutableResolvedSource(
            metadata = sourceMetadata(),
            immutableTypes = emptyList(),
            propsTypeMetadata = bookPropsMetadata(),
            fetcherTypeMetadata = bookFetcherReferenceMetadata(),
        )

    fun referenceSourceGenerationPlan(): ImmutableSourceGenerationPlan =
        ImmutableSourceGenerationPlan(
            metadata = sourceMetadata(),
            draftTypes = listOf(refDraftTypeMetadata()),
            propsTypeMetadata = bookPropsMetadata(),
            fetcherTypeMetadata = bookFetcherReferenceMetadata(),
        )

    fun minimalDraftTypeMetadata(): ImmutableDraftTypeMetadata {
        val bookClassName = className("$SOURCE_PACKAGE_NAME.Book")
        val bookDraftClassName = className("$SOURCE_PACKAGE_NAME.BookDraft")
        return ImmutableDraftTypeMetadata(
            simpleName = SOURCE_FILE_NAME,
            className = bookClassName,
            draftClassName = bookDraftClassName,
            superDraftClassNames = emptyList(),
            declaredProps = emptyList(),
            producerTypeMetadata = producerTypeMetadata(bookClassName, bookDraftClassName),
            builderTypeMetadata = null,
            addFunMetadatas = emptyList(),
            newFunMetadatas = emptyList(),
            copyFunMetadata =
                ImmutableDraftCopyFunMetadata(
                    annotationClassName = bookClassName,
                    receiverTypeName = bookClassName,
                    blockMetadata = draftCallbackMetadata(bookDraftClassName),
                    returnTypeName = bookClassName,
                    draftClassName = bookDraftClassName,
                ),
        )
    }

    fun refDraftTypeMetadata(): ImmutableDraftTypeMetadata {
        val bookClassName = className("$SOURCE_PACKAGE_NAME.Book")
        val bookDraftClassName = className("$SOURCE_PACKAGE_NAME.BookDraft")
        val storeDraftClassName = className("$SOURCE_PACKAGE_NAME.StoreDraft")
        return ImmutableDraftTypeMetadata(
            simpleName = SOURCE_FILE_NAME,
            className = bookClassName,
            draftClassName = bookDraftClassName,
            superDraftClassNames = emptyList(),
            declaredProps = listOf(
                ImmutableDraftDeclaredPropMetadata(
                    name = "store",
                    typeName = className("$SOURCE_PACKAGE_NAME.Store"),
                    isMutable = true,
                    funReturnTypeName = storeDraftClassName,
                    refBlockMetadata = draftCallbackMetadata(storeDraftClassName),
                    associatedIdMetadata = null,
                )
            ),
            producerTypeMetadata = producerTypeMetadata(bookClassName, bookDraftClassName),
            builderTypeMetadata = null,
            addFunMetadatas = emptyList(),
            newFunMetadatas = emptyList(),
            copyFunMetadata = null,
        )
    }

    fun draftTypeMetadataWithBuilderAndAssociatedId(): ImmutableDraftTypeMetadata {
        val bookClassName = className("$SOURCE_PACKAGE_NAME.Book")
        val bookDraftClassName = className("$SOURCE_PACKAGE_NAME.BookDraft")
        val storeClassName = className("$SOURCE_PACKAGE_NAME.Store")
        val bookProducerClassName = className("$SOURCE_PACKAGE_NAME.BookDraft.Producer")
        return ImmutableDraftTypeMetadata(
            simpleName = SOURCE_FILE_NAME,
            className = bookClassName,
            draftClassName = bookDraftClassName,
            superDraftClassNames = emptyList(),
            declaredProps = listOf(
                ImmutableDraftDeclaredPropMetadata(
                    name = "store",
                    typeName = storeClassName,
                    isMutable = true,
                    funReturnTypeName = null,
                    refBlockMetadata = null,
                    associatedIdMetadata = ImmutableAssociatedIdMetadata(
                        name = "storeId",
                        associatedIdLsiTypeName = className("java.lang.Long"),
                        ownerPropName = "store",
                        targetIdPropName = "id",
                        isNullable = true,
                    ),
                )
            ),
            producerTypeMetadata = producerTypeMetadata(bookClassName, bookDraftClassName),
            builderTypeMetadata =
                ImmutableBuilderTypeMetadata(
                    className = bookClassName,
                    producerClassName = bookProducerClassName,
                    draftImplClassName = className("$SOURCE_PACKAGE_NAME.BookDraft.Producer.DraftImpl"),
                    visibleSlotNames = listOf("STORE"),
                    setters = listOf(
                        ImmutableBuilderSetterMetadata(
                            name = "store",
                            parameterLsiTypeName = storeClassName,
                            returnTypeName = className("$SOURCE_PACKAGE_NAME.BookDraft.Builder"),
                            ownerProducerClassName = bookProducerClassName,
                            slotName = "STORE",
                            isNullable = true,
                            lsiAnnotations = emptyList(),
                        )
                    ),
                ),
            addFunMetadatas = emptyList(),
            newFunMetadatas = emptyList(),
            copyFunMetadata = null,
        )
    }

    fun bookPropsMetadata(): ImmutablePropsTypeMetadata {
        val longType = typeRef("kotlin.Long")
        return ImmutablePropsTypeMetadata(
            simpleName = SOURCE_FILE_NAME,
            className = className("$SOURCE_PACKAGE_NAME.Book"),
            propsClassName = className("$SOURCE_PACKAGE_NAME.BookProps"),
            fetcherDslClassName = className("$SOURCE_PACKAGE_NAME.BookFetcherDsl"),
            fetchByBlockMetadata = callbackMetadata(className("$SOURCE_PACKAGE_NAME.BookFetcherDsl")),
            propExpressionClassName = className("$SOURCE_PACKAGE_NAME.BookPropExpression"),
            tableClassName = className("$SOURCE_PACKAGE_NAME.BookTable"),
            tableExClassName = className("$SOURCE_PACKAGE_NAME.BookTableEx"),
            remoteTableClassName = className("$SOURCE_PACKAGE_NAME.BookTable.Remote"),
            isEmbeddable = false,
            isEntity = true,
            idProp = ImmutablePropsIdMetadata(
                name = "id",
                type = longType,
                targetType = longType,
            ),
            properties = listOf(
                propsPropMetadata(
                    name = "id",
                    constantName = "ID",
                    type = longType,
                    targetType = longType,
                ),
                propsPropMetadata(
                    name = "stores",
                    constantName = "STORES",
                    isAssociation = true,
                    isList = true,
                    isReferenceList = true,
                    isDslTable = true,
                    isDslTableEx = true,
                    type = listType("$SOURCE_PACKAGE_NAME.Store"),
                    targetType = typeRef("$SOURCE_PACKAGE_NAME.Store"),
                    associationTargetClassName = className("$SOURCE_PACKAGE_NAME.Store"),
                    predicateBlockMetadata = callbackMetadata(className("$SOURCE_PACKAGE_NAME.StoreTableEx")),
                ),
            ),
        )
    }

    fun bookFetcherListMetadata(): ImmutableFetcherTypeMetadata =
        ImmutableFetcherTypeMetadata(
            simpleName = SOURCE_FILE_NAME,
            className = className("$SOURCE_PACKAGE_NAME.Book"),
            fetcherDslClassName = className("$SOURCE_PACKAGE_NAME.BookFetcherDsl"),
            byBlockMetadata = callbackMetadata(className("$SOURCE_PACKAGE_NAME.BookFetcherDsl")),
            properties = listOf(
                ImmutableFetcherPropMetadata(
                    name = "stores",
                    isId = false,
                    isList = true,
                    supportsIdOnlyFetchType = false,
                    supportsReferenceFetchType = false,
                    supportsRecursive = false,
                    targetClassName = className("$SOURCE_PACKAGE_NAME.Store"),
                    targetTableClassName = className("$SOURCE_PACKAGE_NAME.StoreTable"),
                    childBlockMetadata = callbackMetadata(className("$SOURCE_PACKAGE_NAME.StoreFetcherDsl")),
                    fieldConfigBlockMetadata = null,
                    recursiveConfigBlockMetadata = null,
                    targetIsEntity = true,
                    targetIsEmbeddable = false,
                    configurable = false,
                    fieldKind = ImmutableFetcherFieldKind.LIST,
                )
            ),
        )

    fun bookFetcherReferenceMetadata(): ImmutableFetcherTypeMetadata =
        ImmutableFetcherTypeMetadata(
            simpleName = SOURCE_FILE_NAME,
            className = className("$SOURCE_PACKAGE_NAME.Book"),
            fetcherDslClassName = className("$SOURCE_PACKAGE_NAME.BookFetcherDsl"),
            byBlockMetadata = callbackMetadata(className("$SOURCE_PACKAGE_NAME.BookFetcherDsl")),
            properties = listOf(
                ImmutableFetcherPropMetadata(
                    name = "store",
                    isId = false,
                    isList = false,
                    supportsIdOnlyFetchType = true,
                    supportsReferenceFetchType = true,
                    supportsRecursive = true,
                    targetClassName = className("$SOURCE_PACKAGE_NAME.Store"),
                    targetTableClassName = className("$SOURCE_PACKAGE_NAME.StoreTable"),
                    childBlockMetadata = callbackMetadata(className("$SOURCE_PACKAGE_NAME.StoreFetcherDsl")),
                    fieldConfigBlockMetadata = callbackMetadata(className("$SOURCE_PACKAGE_NAME.StoreFieldConfigDsl")),
                    recursiveConfigBlockMetadata = callbackMetadata(className("$SOURCE_PACKAGE_NAME.StoreRecursiveDsl")),
                    targetIsEntity = true,
                    targetIsEmbeddable = false,
                    configurable = true,
                    fieldKind = ImmutableFetcherFieldKind.REFERENCE,
                )
            ),
        )

    fun jacksonTypes(): JacksonTypes =
        JacksonTypes(
            jsonIgnore = className("com.fasterxml.jackson.annotation.JsonIgnore"),
            jsonValue = className("com.fasterxml.jackson.annotation.JsonValue"),
            jsonFormat = className("com.fasterxml.jackson.annotation.JsonFormat"),
            jsonProperty = className("com.fasterxml.jackson.annotation.JsonProperty"),
            jsonPropertyOrder = className("com.fasterxml.jackson.annotation.JsonPropertyOrder"),
            jsonCreator = className("com.fasterxml.jackson.annotation.JsonCreator"),
            jsonSerializer = className("com.fasterxml.jackson.databind.JsonSerializer"),
            jsonSerialize = className("com.fasterxml.jackson.databind.annotation.JsonSerialize"),
            jsonDeserialize = className("com.fasterxml.jackson.databind.annotation.JsonDeserialize"),
            jsonPojoBuilder = className("com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder"),
            jsonNaming = className("com.fasterxml.jackson.databind.annotation.JsonNaming"),
            jsonGenerator = className("com.fasterxml.jackson.core.JsonGenerator"),
            serializeProvider = className("com.fasterxml.jackson.databind.SerializerProvider"),
        )

    fun testMetaJacksonTypes(): JacksonTypes =
        JacksonTypes(
            jsonIgnore = className("test.meta.JsonIgnore"),
            jsonValue = className("test.meta.JsonValue"),
            jsonFormat = className("test.meta.JsonFormat"),
            jsonProperty = className("test.meta.JsonProperty"),
            jsonPropertyOrder = className("test.meta.JsonPropertyOrder"),
            jsonCreator = className("test.meta.JsonCreator"),
            jsonSerializer = className("test.meta.JsonSerializer"),
            jsonSerialize = className("test.meta.JsonSerialize"),
            jsonDeserialize = className("test.meta.JsonDeserialize"),
            jsonPojoBuilder = className("test.meta.JsonPojoBuilder"),
            jsonNaming = className("test.meta.JsonNaming"),
            jsonGenerator = className("test.meta.JsonGenerator"),
            serializeProvider = className("test.meta.SerializeProvider"),
        )

    fun bookPropsMetadataWithAssociatedId(): ImmutablePropsTypeMetadata {
        val longType = typeRef("kotlin.Long")
        return ImmutablePropsTypeMetadata(
            simpleName = SOURCE_FILE_NAME,
            className = className("$SOURCE_PACKAGE_NAME.Book"),
            propsClassName = className("$SOURCE_PACKAGE_NAME.BookProps"),
            fetcherDslClassName = className("$SOURCE_PACKAGE_NAME.BookFetcherDsl"),
            fetchByBlockMetadata = callbackMetadata(className("$SOURCE_PACKAGE_NAME.BookFetcherDsl")),
            propExpressionClassName = className("$SOURCE_PACKAGE_NAME.BookPropExpression"),
            tableClassName = className("$SOURCE_PACKAGE_NAME.BookTable"),
            tableExClassName = className("$SOURCE_PACKAGE_NAME.BookTableEx"),
            remoteTableClassName = className("$SOURCE_PACKAGE_NAME.BookTable.Remote"),
            isEmbeddable = false,
            isEntity = true,
            idProp = ImmutablePropsIdMetadata(
                name = "id",
                type = longType,
                targetType = longType,
            ),
            properties = listOf(
                propsPropMetadata(
                    name = "id",
                    constantName = "ID",
                    type = longType,
                    targetType = longType,
                ),
                propsPropMetadata(
                    name = "store",
                    constantName = "STORE",
                    generatedIdPropName = "storeId",
                    isAssociation = true,
                    isReference = true,
                    isDslTable = true,
                    isDslTableEx = true,
                    type = typeRef("$SOURCE_PACKAGE_NAME.Store"),
                    targetType = typeRef("$SOURCE_PACKAGE_NAME.Store"),
                    associationTargetClassName = className("$SOURCE_PACKAGE_NAME.Store"),
                    targetIdType = longType,
                    targetIdTargetType = typeRef("$SOURCE_PACKAGE_NAME.Store"),
                ),
                propsPropMetadata(
                    name = "stores",
                    constantName = "STORES",
                    isAssociation = true,
                    isList = true,
                    isReferenceList = true,
                    isDslTable = true,
                    isDslTableEx = true,
                    type = listType("$SOURCE_PACKAGE_NAME.Store"),
                    targetType = typeRef("$SOURCE_PACKAGE_NAME.Store"),
                    associationTargetClassName = className("$SOURCE_PACKAGE_NAME.Store"),
                    predicateBlockMetadata = callbackMetadata(className("$SOURCE_PACKAGE_NAME.StoreTableEx")),
                ),
            ),
        )
    }

    fun addressPropsMetadata(): ImmutablePropsTypeMetadata =
        ImmutablePropsTypeMetadata(
            simpleName = "Address",
            className = className("$SOURCE_PACKAGE_NAME.Address"),
            propsClassName = className("$SOURCE_PACKAGE_NAME.AddressProps"),
            fetcherDslClassName = className("$SOURCE_PACKAGE_NAME.AddressFetcherDsl"),
            fetchByBlockMetadata = callbackMetadata(className("$SOURCE_PACKAGE_NAME.AddressFetcherDsl")),
            propExpressionClassName = className("$SOURCE_PACKAGE_NAME.AddressPropExpression"),
            tableClassName = className("$SOURCE_PACKAGE_NAME.AddressTable"),
            tableExClassName = className("$SOURCE_PACKAGE_NAME.AddressTableEx"),
            remoteTableClassName = className("$SOURCE_PACKAGE_NAME.AddressTable.Remote"),
            isEmbeddable = true,
            isEntity = false,
            idProp = null,
            properties = listOf(
                propsPropMetadata(
                    name = "city",
                    constantName = "CITY",
                    type = typeRef("kotlin.String"),
                    targetType = typeRef("kotlin.String"),
                ),
                propsPropMetadata(
                    name = "geo",
                    constantName = "GEO",
                    isEmbedded = true,
                    type = typeRef("$SOURCE_PACKAGE_NAME.Geo"),
                    targetType = typeRef("$SOURCE_PACKAGE_NAME.Geo"),
                ),
            ),
        )

    fun bookTablePropsMetadata(): ImmutablePropsTypeMetadata {
        val longType = typeRef("kotlin.Long")
        return ImmutablePropsTypeMetadata(
            simpleName = SOURCE_FILE_NAME,
            className = className("$SOURCE_PACKAGE_NAME.Book"),
            propsClassName = className("$SOURCE_PACKAGE_NAME.BookProps"),
            fetcherDslClassName = className("$SOURCE_PACKAGE_NAME.BookFetcherDsl"),
            fetchByBlockMetadata = callbackMetadata(className("$SOURCE_PACKAGE_NAME.BookFetcherDsl")),
            propExpressionClassName = className("$SOURCE_PACKAGE_NAME.BookPropExpression"),
            tableClassName = className("$SOURCE_PACKAGE_NAME.BookTable"),
            tableExClassName = className("$SOURCE_PACKAGE_NAME.BookTableEx"),
            remoteTableClassName = className("$SOURCE_PACKAGE_NAME.BookTable.Remote"),
            isEmbeddable = false,
            isEntity = true,
            idProp = ImmutablePropsIdMetadata(
                name = "id",
                type = longType,
                targetType = longType,
            ),
            properties = listOf(
                propsPropMetadata(
                    name = "id",
                    constantName = "ID",
                    type = longType,
                    targetType = longType,
                ),
                propsPropMetadata(
                    name = "name",
                    constantName = "NAME",
                    type = typeRef("kotlin.String"),
                    targetType = typeRef("kotlin.String"),
                ),
                propsPropMetadata(
                    name = "address",
                    constantName = "ADDRESS",
                    isEmbedded = true,
                    type = typeRef("$SOURCE_PACKAGE_NAME.Address"),
                    targetType = typeRef("$SOURCE_PACKAGE_NAME.Address"),
                ),
                propsPropMetadata(
                    name = "author",
                    constantName = "AUTHOR",
                    generatedIdPropName = "authorId",
                    isAssociation = true,
                    isReference = true,
                    isDslTable = true,
                    isDslTableEx = true,
                    type = typeRef("$SOURCE_PACKAGE_NAME.Author"),
                    targetType = typeRef("$SOURCE_PACKAGE_NAME.Author"),
                    associationTargetClassName = className("$SOURCE_PACKAGE_NAME.Author"),
                    targetIdType = longType,
                    targetIdTargetType = longType,
                ),
                propsPropMetadata(
                    name = "stores",
                    constantName = "STORES",
                    isAssociation = true,
                    isList = true,
                    isReferenceList = true,
                    isDslTable = true,
                    isDslTableEx = true,
                    type = listType("$SOURCE_PACKAGE_NAME.Store"),
                    targetType = typeRef("$SOURCE_PACKAGE_NAME.Store"),
                    associationTargetClassName = className("$SOURCE_PACKAGE_NAME.Store"),
                ),
                propsPropMetadata(
                    name = "customer",
                    constantName = "CUSTOMER",
                    isAssociation = true,
                    isReference = true,
                    isRemote = true,
                    isDslTable = true,
                    isDslTableEx = true,
                    type = typeRef("$SOURCE_PACKAGE_NAME.Customer"),
                    targetType = typeRef("$SOURCE_PACKAGE_NAME.Customer"),
                    associationTargetClassName = className("$SOURCE_PACKAGE_NAME.Customer"),
                ),
            ),
        )
    }

    fun sourceQualifiedNamesOfFileSpecs(fileSpecs: List<LsiFileSpec>): List<String> =
        fileSpecs.map(LsiFileSpec::qualifiedName)

    fun className(qualifiedName: String): LsiClassName =
        LsiClassName.bestGuess(qualifiedName)

    private fun producerTypeMetadata(
        bookClassName: LsiClassName,
        bookDraftClassName: LsiClassName,
    ): ImmutableProducerTypeMetadata =
        ImmutableProducerTypeMetadata(
            className = bookClassName,
            draftClassName = bookDraftClassName,
            draftImplClassName = className("$SOURCE_PACKAGE_NAME.BookDraft.Producer.DraftImpl"),
            draftCallbackMetadata = draftCallbackMetadata(bookDraftClassName),
            isMappedSuperclass = true,
            superProducerClassNames = emptyList(),
            redefinedProps = emptyList(),
            declaredProps = emptyList(),
            slots = emptyList(),
            implementorTypeMetadata = null,
            implTypeMetadata = null,
            draftImplTypeMetadata = null,
        )

    private fun propsPropMetadata(
        name: String,
        constantName: String,
        generatedIdPropName: String? = null,
        isNullable: Boolean = false,
        isList: Boolean = false,
        isTransient: Boolean = false,
        isRemote: Boolean = false,
        isEmbedded: Boolean = false,
        isAssociation: Boolean = false,
        isReferenceList: Boolean = false,
        isReference: Boolean = false,
        isScalarList: Boolean = false,
        isDslTable: Boolean = true,
        isDslTableEx: Boolean = true,
        type: ImmutablePropsTypeRefMetadata,
        targetType: ImmutablePropsTypeRefMetadata,
        associationTargetClassName: LsiClassName? = null,
        predicateBlockMetadata: ImmutableCallbackMetadata? = null,
        targetIdType: ImmutablePropsTypeRefMetadata? = null,
        targetIdTargetType: ImmutablePropsTypeRefMetadata? = null,
        targetIdIsEmbedded: Boolean = false,
    ): ImmutablePropsPropMetadata =
        ImmutablePropsPropMetadata(
            name = name,
            constantName = constantName,
            generatedIdPropName = generatedIdPropName,
            isNullable = isNullable,
            isList = isList,
            isTransient = isTransient,
            isRemote = isRemote,
            isEmbedded = isEmbedded,
            isAssociation = isAssociation,
            isReferenceList = isReferenceList,
            isReference = isReference,
            isScalarList = isScalarList,
            isDslTable = isDslTable,
            isDslTableEx = isDslTableEx,
            type = type,
            targetType = targetType,
            associationTargetClassName = associationTargetClassName,
            predicateBlockMetadata = predicateBlockMetadata,
            targetIdType = targetIdType,
            targetIdTargetType = targetIdTargetType,
            targetIdIsEmbedded = targetIdIsEmbedded,
        )

    private fun callbackMetadata(receiverTypeName: LsiTypeName): ImmutableCallbackMetadata =
        ImmutableCallbackMetadata(
            receiverTypeName = receiverTypeName,
            returnTypeName = KOTLIN_UNIT_LSI_CLASS_NAME,
        )

    private fun listType(elementQualifiedName: String): ImmutablePropsTypeRefMetadata =
        typeRef(
            qualifiedName = "kotlin.collections.List<$elementQualifiedName>",
            simpleName = "List",
            typeArguments = listOf(typeRef(elementQualifiedName)),
        )

    private fun typeRef(
        qualifiedName: String?,
        simpleName: String? = qualifiedName?.substringAfterLast('.'),
        nullable: Boolean = false,
        primitive: Boolean = false,
        array: Boolean = false,
        typeArguments: List<ImmutablePropsTypeRefMetadata> = emptyList(),
        componentType: ImmutablePropsTypeRefMetadata? = null,
        subtypeOfNumber: Boolean = false,
        subtypeOfDate: Boolean = false,
        subtypeOfTemporal: Boolean = false,
        subtypeOfComparable: Boolean = false,
    ): ImmutablePropsTypeRefMetadata =
        ImmutablePropsTypeRefMetadata(
            qualifiedName = qualifiedName,
            simpleName = simpleName,
            presentableText = qualifiedName ?: simpleName,
            nullable = nullable,
            primitive = primitive,
            array = array,
            typeArguments = typeArguments,
            componentType = componentType,
            subtypeOfNumber = subtypeOfNumber,
            subtypeOfDate = subtypeOfDate,
            subtypeOfTemporal = subtypeOfTemporal,
            subtypeOfComparable = subtypeOfComparable,
        )
}
