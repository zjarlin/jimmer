package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType

internal class JimmerDtoArtifactMetadata(
    schema: JimmerDtoPrecompiledSchema,
) {

    val generatedTypes: List<JimmerDtoGeneratedType> = schema.documents
        .flatMap { document ->
            document.renderGraph.rootTypeIds.map { typeId ->
                JimmerDtoGeneratedType(
                    document = document,
                    type = document.renderGraph.typesById.getValue(typeId),
                )
            }
        }
        .sortedWith(compareBy(JimmerDtoGeneratedType::qualifiedName, { generatedType -> generatedType.type.id }))

    val fingerprint: String = buildString {
        generatedTypes.forEach { generatedType ->
            appendField(generatedType.qualifiedName)
            appendField(generatedType.type.id.value)
            appendField(generatedType.aggregationMode.name)
            appendField(generatedType.originatingSymbols.canonicalText(LsiSymbolId::value))
            appendField(generatedType.originatingSources.canonicalSourceText())
            appendField(generatedType.dependencySymbols.canonicalText(LsiSymbolId::value))
            appendField(generatedType.dependencySources.canonicalSourceText())
        }
    }

    init {
        val duplicateQualifiedNames = generatedTypes
            .groupBy(JimmerDtoGeneratedType::qualifiedName)
            .filterValues { generatedTypes -> generatedTypes.size > 1 }
            .keys
            .sorted()
        require(duplicateQualifiedNames.isEmpty()) {
            "DTO generated types cannot have duplicate qualified names: " +
                duplicateQualifiedNames.joinToString()
        }
    }
}

internal data class JimmerDtoGeneratedType(
    val document: JimmerDtoPrecompiledDocument,
    val type: JimmerDtoType,
) {

    val qualifiedName: String = type.qualifiedName()

    val aggregationMode: ArtifactAggregationMode = ArtifactAggregationMode.AGGREGATING

    val originatingSymbols: Set<LsiSymbolId> = setOf(
        requireNotNull(type.baseTypeId) {
            "Generated DTO root type requires an immutable base type: ${type.id.value}"
        }
    )

    val originatingSources: Set<LsiSource> = setOf(document.inputSnapshot.document.source)

    val dependencySymbols: Set<LsiSymbolId> = document.dependencySymbols()

    val dependencySources: Set<LsiSource> = document.renderGraph.originatingSources
        .filterTo(sortedSetOf()) { source -> source.kind != LsiSourceKind.BINARY }

    init {
        require(type.id in document.renderGraph.rootTypeIds) {
            "DTO generated type must be a document root type: ${type.id.value}"
        }
        require(dependencySymbols.containsAll(originatingSymbols)) {
            "DTO artifact dependencies must include its originating symbols: $qualifiedName"
        }
        require(dependencySources.containsAll(originatingSources)) {
            "DTO artifact dependencies must include its originating sources: $qualifiedName"
        }
    }
}

private fun JimmerDtoType.qualifiedName(): String {
    val simpleName = requireNotNull(name) {
        "Generated DTO root type requires a name: ${id.value}"
    }
    return if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"
}

private fun JimmerDtoPrecompiledDocument.dependencySymbols(): Set<LsiSymbolId> = buildSet {
    addAll(targetTypeIds)
    addAll(inputSnapshot.referencedTypeIds)
    renderGraph.types.forEach { type ->
        type.baseTypeId?.let(::add)
        type.polymorphism?.branches.orEmpty().mapNotNullTo(this) { branch -> branch.targetBaseTypeId }
    }
    renderGraph.props.forEach { prop ->
        when (prop) {
            is JimmerDtoBaseProp -> {
                prop.baseProps.mapTo(this) { binding -> binding.propId }
                prop.config?.filter?.let { filter -> add(filter.typeId) }
                prop.config?.recursion?.let { recursion -> add(recursion.typeId) }
                prop.config?.predicate?.collectDependencySymbols(this)
                prop.config?.orderItems.orEmpty().forEach { item ->
                    item.path.mapTo(this) { node -> node.propId }
                }
            }
            is JimmerDtoFoldProp,
            is JimmerDtoUserProp,
            -> Unit
        }
    }
    annotationContract.declarations.mapTo(this) { declaration -> declaration.typeId }
    annotationContract.typePlans.forEach { plan ->
        plan.applications.forEach { application -> application.collectDependencySymbols(this) }
    }
    annotationContract.propPlans.forEach { plan ->
        plan.propertyApplications.forEach { application -> application.collectDependencySymbols(this) }
        plan.builderSetterApplications.forEach { application ->
            application.sourceSymbolId?.let(::add)
            application.annotation.collectDependencySymbols(this)
        }
    }
    interfaceContractResolution.contracts.forEach { contract ->
        addAll(contract.superInterfaceTypeIds)
        contract.props.forEach { prop ->
            add(prop.declaringTypeId)
            prop.type.collectDependencySymbols(this)
            prop.getter?.let { getter -> add(getter.declarationId) }
            prop.setter?.let { setter -> add(setter.declarationId) }
        }
    }
    configContractResolution.contracts.forEach { contract -> addAll(contract.dependencyTypeIds) }
}

private fun JimmerDtoPredicate.collectDependencySymbols(
    destination: MutableSet<LsiSymbolId>,
) {
    when (this) {
        is JimmerDtoPredicate.And -> predicates.forEach { predicate ->
            predicate.collectDependencySymbols(destination)
        }
        is JimmerDtoPredicate.Or -> predicates.forEach { predicate ->
            predicate.collectDependencySymbols(destination)
        }
        is JimmerDtoPredicate.Comparison -> path.mapTo(destination) { node -> node.propId }
        is JimmerDtoPredicate.Nullity -> path.mapTo(destination) { node -> node.propId }
    }
}

private fun JimmerDtoAnnotationApplication.collectDependencySymbols(
    destination: MutableSet<LsiSymbolId>,
) {
    sourceSymbolId?.let(destination::add)
    annotation.collectDependencySymbols(destination)
}

private fun JimmerDtoAppliedAnnotation.collectDependencySymbols(
    destination: MutableSet<LsiSymbolId>,
) {
    destination += typeId
    arguments.forEach { argument -> argument.value.collectDependencySymbols(destination) }
}

private fun JimmerDtoAppliedAnnotationValue.collectDependencySymbols(
    destination: MutableSet<LsiSymbolId>,
) {
    when (this) {
        is JimmerDtoAppliedAnnotationValue.AnnotationValue -> annotation.collectDependencySymbols(destination)
        is JimmerDtoAppliedAnnotationValue.ArrayValue -> elements.forEach { element ->
            element.collectDependencySymbols(destination)
        }
        is JimmerDtoAppliedAnnotationValue.EnumValue -> destination += enumTypeId
        is JimmerDtoAppliedAnnotationValue.TypeValue -> type.collectDependencySymbols(destination)
        is JimmerDtoAppliedAnnotationValue.ScalarValue,
        is JimmerDtoAppliedAnnotationValue.SourceLiteralValue,
        -> Unit
    }
}

private fun LsiTypeRef.collectDependencySymbols(
    destination: MutableSet<LsiSymbolId>,
) {
    when (this) {
        is LsiArrayType -> elementType.collectDependencySymbols(destination)
        is LsiDeclaredType -> {
            destination += declarationId
            arguments.mapNotNull { argument -> argument.type }.forEach { argument ->
                argument.collectDependencySymbols(destination)
            }
        }
        is LsiTypeParameterRef -> destination += parameterId
        is LsiPrimitiveType,
        is LsiUnresolvedType,
        -> Unit
    }
}

private fun <T : Comparable<T>> Set<T>.canonicalText(
    transform: (T) -> String,
): String = sorted().joinToString(",", transform = transform)

private fun Set<LsiSource>.canonicalSourceText(): String = sorted().joinToString(",") { source ->
    listOf(source.path, source.language.name, source.kind.name)
        .joinToString(":") { field -> "${field.length}:$field" }
}

private fun StringBuilder.appendField(value: String) {
    append(value.length)
    append(':')
    append(value)
    append('|')
}
