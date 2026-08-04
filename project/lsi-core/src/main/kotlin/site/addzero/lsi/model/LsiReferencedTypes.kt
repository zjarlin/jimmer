package site.addzero.lsi.model

import site.addzero.lsi.core.LsiSymbolId

fun Iterable<LsiDeclaration>.referencedTypeIds(): Set<LsiSymbolId> {
    return buildSet {
        this@referencedTypeIds.forEach { declaration -> collect(declaration) }
    }
}

private fun MutableSet<LsiSymbolId>.collect(declaration: LsiDeclaration) {
    declaration.annotations.forEach(::collect)
    when (declaration) {
        is LsiTypeDeclaration -> {
            declaration.enclosingTypeId?.let(::add)
            declaration.typeParameters.forEach(::collect)
            declaration.superTypes.forEach(::collect)
            declaration.annotationMembers.forEach { member -> collect(member.type) }
        }
        is LsiField -> collect(declaration.type)
        is LsiProperty -> collect(declaration.type)
        is LsiFunction -> {
            collect(declaration.returnType)
            declaration.receiverType?.let(::collect)
            declaration.parameters.forEach(::collect)
            declaration.typeParameters.forEach(::collect)
            declaration.thrownTypes.forEach(::collect)
        }
        is LsiConstructor -> {
            declaration.parameters.forEach(::collect)
            declaration.typeParameters.forEach(::collect)
            declaration.thrownTypes.forEach(::collect)
        }
        is LsiParameter -> collect(declaration.type)
        is LsiEnumEntry -> Unit
    }
}

private fun MutableSet<LsiSymbolId>.collect(parameter: LsiParameter) {
    collect(parameter.type)
    parameter.annotations.forEach(::collect)
}

private fun MutableSet<LsiSymbolId>.collect(parameter: LsiTypeParameter) {
    parameter.upperBounds.forEach(::collect)
}

private fun MutableSet<LsiSymbolId>.collect(type: LsiTypeRef) {
    type.annotations.forEach(::collect)
    when (type) {
        is LsiDeclaredType -> {
            add(type.declarationId)
            type.arguments.mapNotNull { argument -> argument.type }.forEach(::collect)
        }
        is LsiArrayType -> collect(type.elementType)
        is LsiFunctionType -> {
            type.receiverType?.let(::collect)
            type.parameterTypes.forEach(::collect)
            collect(type.returnType)
        }
        is LsiPrimitiveType,
        is LsiTypeParameterRef,
        is LsiUnresolvedType,
        -> Unit
    }
}

private fun MutableSet<LsiSymbolId>.collect(annotation: LsiAnnotation) {
    add(annotation.type)
    annotation.arguments.values.forEach { argument -> collect(argument.value) }
}

private fun MutableSet<LsiSymbolId>.collect(value: LsiAnnotationValue) {
    when (value) {
        is LsiAnnotationValue.EnumValue -> add(value.enumType)
        is LsiAnnotationValue.ClassValue -> collect(value.type)
        is LsiAnnotationValue.NestedAnnotationValue -> collect(value.annotation)
        is LsiAnnotationValue.ArrayValue -> value.elements.forEach(::collect)
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
