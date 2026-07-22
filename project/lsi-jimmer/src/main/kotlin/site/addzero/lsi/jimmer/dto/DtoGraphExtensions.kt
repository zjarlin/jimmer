package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiSource

/** 返回 DTO 图生成物所依赖的全部来源。 */
fun DtoGraph.originatingSources(): Set<LsiSource> {
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
