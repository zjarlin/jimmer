package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

/** 返回 DTO 图生成物直接引用的全部稳定符号。 */
fun DtoGraph.dependencySymbols(): Set<LsiSymbolId> {
    return buildSet {
        types.forEach { type ->
            type.baseTypeId?.let(::add)
            type.annotations.forEach { annotation -> addAnnotationSymbols(annotation) }
            type.polymorphism?.branches.orEmpty().mapNotNullTo(this) { branch ->
                branch.targetBaseTypeId
            }
        }
        props.forEach { prop ->
            prop.annotations.forEach { annotation -> addAnnotationSymbols(annotation) }
            if (prop is DtoBaseProp) {
                prop.baseProps.mapTo(this, DtoBasePropBinding::propId)
                prop.targetTypeReference?.targetBaseTypeId?.let(::add)
                prop.config?.filter?.typeId?.let(::add)
                prop.config?.recursion?.typeId?.let(::add)
            }
        }
    }.toSortedSet()
}

/** 返回 DTO 图生成物所依赖的全部来源。 */
fun DtoGraph.dependencySources(): Set<LsiSource> {
    return buildSet {
        add(source)
        types.forEach { type ->
            add(type.location.source)
            type.annotations.forEach { annotation -> addAnnotationSources(annotation) }
            type.superInterfaces.forEach { typeRef -> addTypeRefSources(typeRef) }
            type.polymorphism?.branches.orEmpty().forEach { branch -> add(branch.location.source) }
        }
        props.forEach { prop ->
            add(prop.aliasLocation.source)
            prop.annotations.forEach { annotation -> addAnnotationSources(annotation) }
            when (prop) {
                is DtoBaseProp -> {
                    add(prop.baseLocation.source)
                    prop.targetTypeReference?.let { reference -> add(reference.location.source) }
                    prop.config?.filter?.let { filter -> add(filter.location.source) }
                    prop.config?.recursion?.let { recursion -> add(recursion.location.source) }
                }
                is DtoUserProp -> addTypeRefSources(prop.type)
                is DtoFoldProp -> Unit
            }
        }
    }.toSortedSet()
}

private fun MutableSet<LsiSource>.addAnnotationSources(annotation: DtoAnnotation) {
    annotation.arguments.forEach { argument -> addAnnotationValueSources(argument.value) }
}

private fun MutableSet<LsiSymbolId>.addAnnotationSymbols(annotation: DtoAnnotation) {
    add(annotation.typeId)
    annotation.arguments.forEach { argument -> addAnnotationValueSymbols(argument.value) }
}

private fun MutableSet<LsiSymbolId>.addAnnotationValueSymbols(value: DtoAnnotationValue) {
    when (value) {
        is DtoAnnotationValue.ArrayValue -> value.elements.forEach(::addAnnotationValueSymbols)
        is DtoAnnotationValue.AnnotationValue -> addAnnotationSymbols(value.annotation)
        is DtoAnnotationValue.EnumValue -> add(value.enumTypeId)
        is DtoAnnotationValue.LiteralValue,
        is DtoAnnotationValue.TypeValue,
        -> Unit
    }
}

private fun MutableSet<LsiSource>.addAnnotationValueSources(value: DtoAnnotationValue) {
    when (value) {
        is DtoAnnotationValue.ArrayValue -> value.elements.forEach { element ->
            addAnnotationValueSources(element)
        }
        is DtoAnnotationValue.AnnotationValue -> addAnnotationSources(value.annotation)
        is DtoAnnotationValue.TypeValue -> addTypeRefSources(value.type)
        is DtoAnnotationValue.EnumValue,
        is DtoAnnotationValue.LiteralValue,
        -> Unit
    }
}

private fun MutableSet<LsiSource>.addTypeRefSources(type: DtoTypeRef) {
    add(type.location.source)
    type.arguments.mapNotNull(DtoTypeArgument::type).forEach { argumentType ->
        addTypeRefSources(argumentType)
    }
}
