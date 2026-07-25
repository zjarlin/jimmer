package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType

/** 返回多态输入根需要自动生成的完整 Jackson 注解。 */
fun DtoType.generatedJacksonPolymorphicRootAnnotations(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    annotationContract: DtoAnnotationContract,
    generatedRootTypeId: LsiSymbolId,
): List<LsiAnnotation> {
    if (!isPolymorphicInputRoot(immutableSchema)) {
        return emptyList()
    }
    val polymorphism = requireNotNull(polymorphism) {
        "Polymorphic input DTO has no frozen polymorphism: ${id.value}"
    }
    generatedRootTypeId.requireTypeQualifiedName()
    return buildList {
        if (!hasTypeAnnotation(annotationContract, JSON_TYPE_INFO_TYPE_ID)) {
            generatedJacksonTypeInfoAnnotationOrNull(
                graph = graph,
                immutableSchema = immutableSchema,
                polymorphism = polymorphism,
                generatedRootTypeId = generatedRootTypeId,
            )?.let(::add)
        }
        if (!hasTypeAnnotation(annotationContract, JSON_SUB_TYPES_TYPE_ID)) {
            polymorphism.generatedJacksonSubTypesAnnotationOrNull(generatedRootTypeId)?.let(::add)
        }
    }
}

/** 返回类型分支需要自动生成的完整 JsonTypeName 注解。 */
fun DtoPolymorphicBranch.generatedJacksonPolymorphicTypeNameAnnotationOrNull(
    rootType: DtoType,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    annotationContract: DtoAnnotationContract,
): LsiAnnotation? {
    require(graph.typesById[rootType.id] == rootType) {
        "DTO polymorphic root does not belong to this graph: ${rootType.id.value}"
    }
    require(this in requireNotNull(rootType.polymorphism).branches) {
        "DTO polymorphic branch does not belong to root type: $className"
    }
    if (!rootType.isPolymorphicInputRoot(immutableSchema) || kind != DtoPolymorphicBranchKind.TYPE) {
        return null
    }
    if (rootType.hasTypeAnnotation(annotationContract, JSON_SUB_TYPES_TYPE_ID)) {
        return null
    }
    val mergedType = mergedType(graph)
    if (mergedType.hasTypeAnnotation(annotationContract, JSON_TYPE_NAME_TYPE_ID)) {
        return null
    }
    val targetTypeId = requireNotNull(targetBaseTypeId) {
        "DTO type branch has no target immutable type: $className"
    }
    val discriminatorValue = requireNotNull(immutableSchema.typesById[targetTypeId]) {
        "No immutable branch type '${targetTypeId.value}' for DTO branch: $className"
    }.discriminatorValue ?: return null
    return jacksonAnnotation(
        type = JSON_TYPE_NAME_TYPE_ID,
        arguments = listOf(
            "value" to LsiAnnotationValue.StringValue(discriminatorValue),
        ),
    )
}

private fun DtoType.generatedJacksonTypeInfoAnnotationOrNull(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    polymorphism: DtoPolymorphism,
    generatedRootTypeId: LsiSymbolId,
): LsiAnnotation? {
    val selectedDiscriminator = selectedPolymorphicInputDiscriminatorPropOrNull(graph, immutableSchema)
    val propertyName = selectedDiscriminator?.name
        ?: polymorphicRootDiscriminatorPropNameOrNull(immutableSchema)
        ?: return null
    val arguments = mutableListOf(
        "use" to LsiAnnotationValue.EnumValue(JSON_TYPE_INFO_ID_TYPE_ID, "NAME"),
        "include" to LsiAnnotationValue.EnumValue(
            JSON_TYPE_INFO_AS_TYPE_ID,
            if (selectedDiscriminator != null) "EXISTING_PROPERTY" else "PROPERTY",
        ),
        "property" to LsiAnnotationValue.StringValue(propertyName),
    )
    if (selectedDiscriminator != null) {
        arguments += "visible" to LsiAnnotationValue.BooleanValue(true)
    }
    polymorphism.defaultBranch()?.let { branch ->
        arguments += "defaultImpl" to LsiAnnotationValue.ClassValue(
            LsiDeclaredType(generatedRootTypeId.nestedTypeId(branch.className))
        )
    }
    return jacksonAnnotation(JSON_TYPE_INFO_TYPE_ID, arguments)
}

private fun DtoPolymorphism.generatedJacksonSubTypesAnnotationOrNull(
    generatedRootTypeId: LsiSymbolId,
): LsiAnnotation? {
    val typeBranches = typeBranchesInDeclarationOrder()
    if (typeBranches.isEmpty()) {
        return null
    }
    val branchAnnotations = typeBranches.map { branch ->
        LsiAnnotationValue.NestedAnnotationValue(
            jacksonAnnotation(
                type = JSON_SUB_TYPES_TYPE_TYPE_ID,
                arguments = listOf(
                    "value" to LsiAnnotationValue.ClassValue(
                        LsiDeclaredType(generatedRootTypeId.nestedTypeId(branch.className))
                    ),
                ),
            )
        )
    }
    return jacksonAnnotation(
        type = JSON_SUB_TYPES_TYPE_ID,
        arguments = listOf(
            "value" to LsiAnnotationValue.ArrayValue(branchAnnotations),
        ),
    )
}

private fun jacksonAnnotation(
    type: LsiSymbolId,
    arguments: List<Pair<String, LsiAnnotationValue>>,
): LsiAnnotation {
    val argumentMap = arguments.associateTo(linkedMapOf()) { (name, value) ->
        name to LsiAnnotationArgument(value, LsiAnnotationArgumentOrigin.EXPLICIT)
    }
    require(argumentMap.size == arguments.size) {
        "Generated Jackson annotation cannot contain duplicate arguments: ${type.value}"
    }
    return LsiAnnotation(
        type = type,
        arguments = argumentMap,
        explicitArgumentNamesInSourceOrder = arguments.map(Pair<String, LsiAnnotationValue>::first),
    )
}

private fun LsiSymbolId.nestedTypeId(simpleName: String): LsiSymbolId {
    return LsiSymbolId.type("${requireTypeQualifiedName()}.$simpleName")
}

private val JSON_TYPE_INFO_TYPE_ID = LsiSymbolId.type(
    "com.fasterxml.jackson.annotation.JsonTypeInfo"
)

private val JSON_TYPE_INFO_ID_TYPE_ID = LsiSymbolId.type(
    "com.fasterxml.jackson.annotation.JsonTypeInfo.Id"
)

private val JSON_TYPE_INFO_AS_TYPE_ID = LsiSymbolId.type(
    "com.fasterxml.jackson.annotation.JsonTypeInfo.As"
)

private val JSON_SUB_TYPES_TYPE_ID = LsiSymbolId.type(
    "com.fasterxml.jackson.annotation.JsonSubTypes"
)

private val JSON_SUB_TYPES_TYPE_TYPE_ID = LsiSymbolId.type(
    "com.fasterxml.jackson.annotation.JsonSubTypes.Type"
)

private val JSON_TYPE_NAME_TYPE_ID = LsiSymbolId.type(
    "com.fasterxml.jackson.annotation.JsonTypeName"
)
