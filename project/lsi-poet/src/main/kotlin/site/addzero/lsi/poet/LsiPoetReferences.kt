package site.addzero.lsi.poet

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.collectTypeRefDependencies

/** 收集单个源码注解中以结构化形式出现的全部稳定符号引用。 */
fun LsiPoetAnnotation.referencedSymbolIds(): Set<LsiSymbolId> {
    return sortedSetOf<LsiSymbolId>().apply {
        collectPoetAnnotationDependencies(this@referencedSymbolIds)
    }
}

/** 收集渲染单个源码注解所需的直接类型身份。 */
val LsiPoetAnnotation.referencedTypeIds: Set<LsiSymbolId>
    get() = referencedSymbolIds().filterTo(sortedSetOf(), LsiSymbolId::isTypeId)

/**
 * 收集源码成员中以结构化形式出现的全部稳定符号引用。
 *
 * 纯文本和名称片段不承载符号身份，因此调用方必须用 [LsiPoetCodePart.Type]
 * 表达会影响增量依赖的类型引用。
 */
fun LsiPoetMember.referencedSymbolIds(): Set<LsiSymbolId> {
    return sortedSetOf<LsiSymbolId>().apply {
        collectPoetMemberDependencies(this@referencedSymbolIds)
    }
}

/**
 * 收集渲染源码成员所需的直接类型身份，不包含类型参数和成员身份。
 */
val LsiPoetMember.referencedTypeIds: Set<LsiSymbolId>
    get() = referencedSymbolIds().filterTo(sortedSetOf(), LsiSymbolId::isTypeId)

/** 收集独立代码块中以结构化形式出现的全部稳定符号引用。 */
fun LsiPoetCodeBlock.referencedSymbolIds(): Set<LsiSymbolId> {
    return sortedSetOf<LsiSymbolId>().apply {
        collectPoetCodeDependencies(this@referencedSymbolIds)
    }
}

/** 收集渲染独立代码块所需的直接类型身份。 */
val LsiPoetCodeBlock.referencedTypeIds: Set<LsiSymbolId>
    get() = referencedSymbolIds().filterTo(sortedSetOf(), LsiSymbolId::isTypeId)

/** 收集源码文件中以结构化形式出现的全部稳定符号引用。 */
fun LsiPoetFile.referencedSymbolIds(): Set<LsiSymbolId> {
    return sortedSetOf<LsiSymbolId>().apply {
        annotations.forEach(::collectPoetAnnotationDependencies)
        members.forEach { member -> addAll(member.referencedSymbolIds()) }
    }
}

/** 收集渲染源码文件所需的直接类型身份，不包含类型参数和成员身份。 */
val LsiPoetFile.referencedTypeIds: Set<LsiSymbolId>
    get() = referencedSymbolIds().filterTo(sortedSetOf(), LsiSymbolId::isTypeId)

private fun MutableSet<LsiSymbolId>.collectPoetMemberDependencies(member: LsiPoetMember) {
    member.annotations.forEach(::collectPoetAnnotationDependencies)
    when (member) {
        is LsiPoetConstructor -> {
            member.typeParameters.forEach(::collectPoetTypeParameterDependencies)
            member.parameters.forEach(::collectPoetParameterDependencies)
            member.thrownTypes.forEach(::collectTypeRefDependencies)
            collectPoetCodeDependencies(member.body)
            member.delegationCall?.arguments?.forEach(::collectPoetCodeDependencies)
        }
        is LsiPoetField -> {
            collectTypeRefDependencies(member.type)
            member.initializer?.let(::collectPoetCodeDependencies)
        }
        is LsiPoetFunction -> {
            member.typeParameters.forEach(::collectPoetTypeParameterDependencies)
            member.receiverType?.let(::collectTypeRefDependencies)
            member.parameters.forEach(::collectPoetParameterDependencies)
            member.returnType?.let(::collectTypeRefDependencies)
            member.thrownTypes.forEach(::collectTypeRefDependencies)
            collectPoetCodeDependencies(member.body)
        }
        is LsiPoetInitializerBlock -> collectPoetCodeDependencies(member.body)
        is LsiPoetProperty -> {
            collectTypeRefDependencies(member.type)
            member.receiverType?.let(::collectTypeRefDependencies)
            member.initializer?.let(::collectPoetCodeDependencies)
            member.getter?.let(::collectPoetAccessorDependencies)
            member.setter?.let(::collectPoetAccessorDependencies)
        }
        is LsiPoetType -> {
            member.typeParameters.forEach(::collectPoetTypeParameterDependencies)
            member.superClass?.let(::collectTypeRefDependencies)
            member.superClassConstructorArguments.forEach(::collectPoetCodeDependencies)
            member.superInterfaces.forEach(::collectTypeRefDependencies)
            member.primaryConstructor?.let(::collectPoetMemberDependencies)
            member.enumConstants.forEach { constant ->
                constant.constructorArguments.forEach(::collectPoetCodeDependencies)
                constant.anonymousType?.let(::collectPoetMemberDependencies)
            }
            member.members.forEach(::collectPoetMemberDependencies)
        }
    }
}

private fun MutableSet<LsiSymbolId>.collectPoetTypeParameterDependencies(parameter: LsiTypeParameter) {
    add(parameter.id)
    parameter.upperBounds.forEach(::collectTypeRefDependencies)
}

private fun MutableSet<LsiSymbolId>.collectPoetParameterDependencies(parameter: LsiPoetParameter) {
    parameter.annotations.forEach(::collectPoetAnnotationDependencies)
    collectTypeRefDependencies(parameter.type)
    parameter.defaultValue?.let(::collectPoetCodeDependencies)
}

private fun MutableSet<LsiSymbolId>.collectPoetAccessorDependencies(accessor: LsiPoetAccessor) {
    accessor.annotations.forEach(::collectPoetAnnotationDependencies)
    accessor.parameterAnnotations.forEach(::collectPoetAnnotationDependencies)
    collectPoetCodeDependencies(accessor.body)
}

private fun MutableSet<LsiSymbolId>.collectPoetCodeDependencies(code: LsiPoetCodeBlock) {
    code.parts.forEach { part ->
        when (part) {
            is LsiPoetCodePart.BeginControlFlow -> collectPoetCodeDependencies(part.header)
            is LsiPoetCodePart.BracedExpression -> {
                collectPoetCodeDependencies(part.prefix)
                collectPoetCodeDependencies(part.body)
                collectPoetCodeDependencies(part.suffix)
            }
            is LsiPoetCodePart.NextControlFlow -> collectPoetCodeDependencies(part.header)
            is LsiPoetCodePart.Return -> part.value?.let(::collectPoetCodeDependencies)
            is LsiPoetCodePart.Statement -> collectPoetCodeDependencies(part.value)
            is LsiPoetCodePart.Type -> collectTypeRefDependencies(part.value)
            is LsiPoetCodePart.CharacterLiteral,
            LsiPoetCodePart.EndControlFlow,
            LsiPoetCodePart.Indent,
            is LsiPoetCodePart.Literal,
            is LsiPoetCodePart.Name,
            LsiPoetCodePart.NewLine,
            is LsiPoetCodePart.StringLiteral,
            is LsiPoetCodePart.Text,
            is LsiPoetCodePart.TopLevelMember,
            LsiPoetCodePart.Unindent,
            -> Unit
        }
    }
}

private fun MutableSet<LsiSymbolId>.collectPoetAnnotationDependencies(annotation: LsiPoetAnnotation) {
    add(annotation.type)
    annotation.arguments.forEach { argument -> collectPoetAnnotationValueDependencies(argument.value) }
}

private fun MutableSet<LsiSymbolId>.collectPoetAnnotationValueDependencies(value: LsiPoetAnnotationValue) {
    when (value) {
        is LsiPoetAnnotationValue.ArrayValue -> value.elements.forEach(::collectPoetAnnotationValueDependencies)
        is LsiPoetAnnotationValue.ClassValue -> collectTypeRefDependencies(value.type)
        is LsiPoetAnnotationValue.EnumValue -> add(value.enumType)
        is LsiPoetAnnotationValue.NestedAnnotationValue -> collectPoetAnnotationDependencies(value.annotation)
        is LsiPoetAnnotationValue.BooleanValue,
        is LsiPoetAnnotationValue.ByteValue,
        is LsiPoetAnnotationValue.ShortValue,
        is LsiPoetAnnotationValue.IntValue,
        is LsiPoetAnnotationValue.LongValue,
        is LsiPoetAnnotationValue.FloatValue,
        is LsiPoetAnnotationValue.DoubleValue,
        is LsiPoetAnnotationValue.CharValue,
        is LsiPoetAnnotationValue.StringValue,
        -> Unit
    }
}
