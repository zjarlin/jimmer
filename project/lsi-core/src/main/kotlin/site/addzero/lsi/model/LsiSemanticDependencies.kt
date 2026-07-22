package site.addzero.lsi.model

import site.addzero.lsi.core.LsiSymbolId

/**
 * 收集类型引用中的声明、类型参数和类型使用注解依赖。
 */
fun MutableSet<LsiSymbolId>.collectTypeRefDependencies(type: LsiTypeRef) {
    type.annotations.forEach(::collectAnnotationDependencies)
    when (type) {
        is LsiArrayType -> collectTypeRefDependencies(type.elementType)
        is LsiDeclaredType -> {
            add(type.declarationId)
            type.arguments.forEach { argument -> argument.type?.let(::collectTypeRefDependencies) }
        }
        is LsiFunctionType -> {
            type.receiverType?.let(::collectTypeRefDependencies)
            type.parameterTypes.forEach(::collectTypeRefDependencies)
            collectTypeRefDependencies(type.returnType)
        }
        is LsiTypeParameterRef -> add(type.parameterId)
        is LsiPrimitiveType,
        is LsiUnresolvedType,
        -> Unit
    }
}

/**
 * 收集结构化注解及其全部嵌套值引用的稳定符号。
 */
fun MutableSet<LsiSymbolId>.collectAnnotationDependencies(annotation: LsiAnnotation) {
    add(annotation.type)
    annotation.arguments.values.forEach { argument ->
        collectAnnotationValueDependencies(argument.value)
    }
}

private fun MutableSet<LsiSymbolId>.collectAnnotationValueDependencies(value: LsiAnnotationValue) {
    when (value) {
        is LsiAnnotationValue.ArrayValue -> value.elements.forEach(::collectAnnotationValueDependencies)
        is LsiAnnotationValue.ClassValue -> collectTypeRefDependencies(value.type)
        is LsiAnnotationValue.EnumValue -> add(value.enumType)
        is LsiAnnotationValue.NestedAnnotationValue -> collectAnnotationDependencies(value.annotation)
        is LsiAnnotationValue.BooleanValue,
        is LsiAnnotationValue.ByteValue,
        is LsiAnnotationValue.ShortValue,
        is LsiAnnotationValue.IntValue,
        is LsiAnnotationValue.LongValue,
        is LsiAnnotationValue.FloatValue,
        is LsiAnnotationValue.DoubleValue,
        is LsiAnnotationValue.CharValue,
        is LsiAnnotationValue.StringValue,
        -> Unit
    }
}
