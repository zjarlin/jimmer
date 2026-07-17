package org.babyfish.jimmer.compiler.tuple

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.stableSignature

fun TypedTuplePrecompiledSchema.normalizedSnapshot(): String {
    return buildString {
        tuples.sortedBy(TypedTupleType::id).forEach { tuple ->
            appendRecord(
                "tuple",
                tuple.id.value,
                tuple.qualifiedName,
                tuple.packageName,
                tuple.simpleName,
                tuple.mapperSimpleName,
                tuple.mapperQualifiedName,
            )
            tuple.dependencies.typeIds.forEach { typeId ->
                appendRecord("type-dependency", tuple.id.value, typeId.value)
            }
            tuple.properties.map(TypedTupleProperty::id).distinct().sorted().forEach { propertyId ->
                appendRecord("property-dependency", tuple.id.value, propertyId.value)
            }
            tuple.properties.sortedBy(TypedTupleProperty::index).forEach { property ->
                appendRecord(
                    "property",
                    tuple.id.value,
                    property.id.value,
                    property.name,
                    property.index.toString(),
                    property.type.normalizedTupleTypeSignature(),
                    property.nullable.toString(),
                    property.builderSimpleName.orEmpty(),
                    property.nextStepTypeName,
                    property.typeDependencyIds.joinToString(",") { typeId -> typeId.value },
                )
            }
        }
    }
}

fun TypedTuplePrecompiledSchema.fingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(renderSnapshot().toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte) }
}

private fun TypedTuplePrecompiledSchema.renderSnapshot(): String {
    return buildString {
        tuples.forEach { tuple ->
            appendRecord(
                "render-tuple",
                tuple.id.value,
                tuple.qualifiedName,
                tuple.packageName,
                tuple.simpleName,
                tuple.mapperSimpleName,
                tuple.mapperQualifiedName,
                tuple.platform.name,
            )
            tuple.dependencies.typeIds.forEach { typeId ->
                appendRecord("render-type-dependency", tuple.id.value, typeId.value)
            }
            tuple.dependencies.memberIds.forEach { memberId ->
                appendRecord("render-member-dependency", tuple.id.value, memberId.value)
            }
            tuple.properties.forEach { property ->
                appendRecord(
                    "render-property",
                    tuple.id.value,
                    property.id.value,
                    property.sourceMemberId.value,
                    property.name,
                    property.index.toString(),
                    property.type.stableSignature(),
                    property.nullable.toString(),
                    property.builderSimpleName.orEmpty(),
                    property.nextStepTypeName,
                    property.typeDependencyIds.joinToString(",") { typeId -> typeId.value },
                )
            }
            appendConstruction(tuple)
        }
    }
}

private fun StringBuilder.appendConstruction(tuple: TypedTupleType) {
    when (val construction = tuple.construction) {
        is TypedTupleJavaSetterPlan -> {
            appendRecord(
                "render-construction",
                tuple.id.value,
                "java-setter",
                construction.constructorId?.value.orEmpty(),
            )
            construction.assignments.forEach { assignment ->
                appendRecord(
                    "render-setter",
                    tuple.id.value,
                    assignment.sourceMemberId.value,
                    assignment.propertyIndex.toString(),
                    assignment.setterName,
                )
            }
        }
        is TypedTupleJavaPositionalPlan -> {
            appendRecord(
                "render-construction",
                tuple.id.value,
                "java-positional",
                construction.constructorId?.value.orEmpty(),
            )
            construction.arguments.forEach { argument ->
                appendConstructorArgument(tuple.id, argument)
            }
        }
        is TypedTupleKotlinNamedPlan -> {
            appendRecord(
                "render-construction",
                tuple.id.value,
                "kotlin-named",
                construction.constructorId.value,
            )
            construction.arguments.forEach { argument ->
                appendConstructorArgument(tuple.id, argument)
            }
        }
    }
}

private fun StringBuilder.appendConstructorArgument(
    tupleId: site.addzero.lsi.core.LsiSymbolId,
    argument: TypedTupleConstructorArgument,
) {
    appendRecord(
        "render-constructor-argument",
        tupleId.value,
        argument.sourceMemberId.value,
        argument.propertyIndex.toString(),
        argument.parameterId?.value.orEmpty(),
        argument.parameterIndex.toString(),
        argument.parameterName,
    )
}

private fun StringBuilder.appendRecord(
    kind: String,
    vararg fields: String,
) {
    append(kind)
    fields.forEach { field ->
        append('|')
        append(field.escapeSnapshotField())
    }
    append('\n')
}

private fun String.escapeSnapshotField(): String {
    return buildString {
        for (character in this@escapeSnapshotField) {
            when (character) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '|' -> append("\\|")
                ',' -> append("\\,")
                else -> append(character)
            }
        }
    }
}

private fun LsiTypeRef.normalizedTupleTypeSignature(): String {
    return stableSignature().replace("!platform", "!non-null")
}
