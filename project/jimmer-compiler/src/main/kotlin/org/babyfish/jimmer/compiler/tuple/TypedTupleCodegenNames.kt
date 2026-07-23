package org.babyfish.jimmer.compiler.tuple

import site.addzero.lsi.jimmer.tuple.TypedTupleProperty
import site.addzero.lsi.jimmer.tuple.TypedTupleSchema
import site.addzero.lsi.jimmer.tuple.TypedTupleType
import site.addzero.lsi.jimmer.tuple.TypedTupleValidationException

internal val TypedTupleType.mapperSimpleName: String
    get() = simpleName + "Mapper"

internal val TypedTupleType.mapperQualifiedName: String
    get() = if (packageName.isEmpty()) {
        mapperSimpleName
    } else {
        "$packageName.$mapperSimpleName"
    }

internal val TypedTupleProperty.builderSimpleName: String
    get() = typeName(name, "Builder")

internal fun TypedTupleType.nextStepTypeName(property: TypedTupleProperty): String {
    return properties.getOrNull(property.index + 1)?.builderSimpleName ?: mapperSimpleName
}

internal fun TypedTupleSchema.validateCodegenNames() {
    tuples.forEach { tuple ->
        val duplicateBuilderName = tuple.properties
            .drop(1)
            .map(TypedTupleProperty::builderSimpleName)
            .groupingBy { builderName -> builderName }
            .eachCount()
            .entries
            .firstOrNull { (_, count) -> count > 1 }
            ?.key
        if (duplicateBuilderName != null) {
            throw TypedTupleValidationException(
                declarationId = tuple.id,
                message = "Typed tuple '${tuple.qualifiedName}' produces duplicate builder '$duplicateBuilderName'",
            )
        }
    }
}

private fun typeName(vararg parts: String): String {
    val result = StringBuilder()
    var previousPartEndsWithLowercase = true
    for (part in parts) {
        if (part.isEmpty()) {
            continue
        }
        if (previousPartEndsWithLowercase) {
            if (part.first().isUpperCase()) {
                result.append(part)
            } else {
                result.append(part.first().uppercaseChar()).append(part.drop(1))
            }
        } else if (part.first().isLowerCase()) {
            result.append(part)
        } else {
            val characters = part.toCharArray()
            for (index in characters.indices) {
                if (characters[index].isLowerCase()) {
                    break
                }
                characters[index] = characters[index].lowercaseChar()
            }
            result.append(characters)
        }
        previousPartEndsWithLowercase = part.last().isLowerCase()
    }
    return result.toString()
}
