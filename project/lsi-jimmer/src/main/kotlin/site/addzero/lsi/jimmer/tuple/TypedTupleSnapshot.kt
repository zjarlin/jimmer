package site.addzero.lsi.jimmer.tuple

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.stableSignature

/** 生成跨前端比较使用的 TypedTuple 规范化快照。 */
fun TypedTupleSchema.normalizedSnapshot(): String {
    return buildString {
        tuples.sortedBy(TypedTupleType::id).forEach { tuple ->
            appendRecord(
                "tuple",
                tuple.id.value,
                tuple.qualifiedName,
                tuple.packageName,
                tuple.simpleName,
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
                    property.typeDependencyIds.joinToString(",") { typeId -> typeId.value },
                )
            }
            tuple.baseTableProjection?.selections.orEmpty().forEach { selection ->
                appendRecord(
                    "base-table-selection",
                    tuple.id.value,
                    selection.propertyIndex.toString(),
                    selection.kind.name,
                    selection.entityTableTypeId?.value.orEmpty(),
                    selection.scalarCategory?.name.orEmpty(),
                )
            }
        }
    }
}

/** 计算包含源语言与构造契约的 TypedTuple SHA-256 指纹。 */
fun TypedTupleSchema.fingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(renderSnapshot().toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte) }
}

private fun TypedTupleSchema.renderSnapshot(): String {
    return buildString {
        tuples.sortedBy(TypedTupleType::id).forEach { tuple ->
            appendRecord(
                "render-tuple",
                tuple.id.value,
                tuple.qualifiedName,
                tuple.packageName,
                tuple.simpleName,
                tuple.sourceLanguage.name,
            )
            tuple.dependencies.typeIds.forEach { typeId ->
                appendRecord("render-type-dependency", tuple.id.value, typeId.value)
            }
            tuple.dependencies.memberIds.forEach { memberId ->
                appendRecord("render-member-dependency", tuple.id.value, memberId.value)
            }
            tuple.properties.sortedBy(TypedTupleProperty::index).forEach { property ->
                appendRecord(
                    "render-property",
                    tuple.id.value,
                    property.id.value,
                    property.sourceMemberId.value,
                    property.name,
                    property.index.toString(),
                    property.type.stableSignature(),
                    property.nullable.toString(),
                    property.typeDependencyIds.joinToString(",") { typeId -> typeId.value },
                )
            }
            tuple.baseTableProjection?.selections.orEmpty().forEach { selection ->
                appendRecord(
                    "render-base-table-selection",
                    tuple.id.value,
                    selection.propertyIndex.toString(),
                    selection.kind.name,
                    selection.entityTableTypeId?.value.orEmpty(),
                    selection.scalarCategory?.name.orEmpty(),
                )
            }
            appendConstruction(tuple)
        }
    }
}

private fun StringBuilder.appendConstruction(tuple: TypedTupleType) {
    when (val construction = tuple.construction) {
        is TypedTupleJavaSetterConstruction -> {
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
        is TypedTupleJavaConstructorConstruction -> {
            appendRecord(
                "render-construction",
                tuple.id.value,
                "java-constructor",
                construction.constructorId?.value.orEmpty(),
            )
            construction.arguments.forEach { argument ->
                appendConstructorArgument(tuple.id, argument)
            }
        }
        is TypedTupleKotlinConstructorConstruction -> {
            appendRecord(
                "render-construction",
                tuple.id.value,
                "kotlin-constructor",
                construction.constructorId.value,
            )
            construction.arguments.forEach { argument ->
                appendConstructorArgument(tuple.id, argument)
            }
        }
    }
}

private fun StringBuilder.appendConstructorArgument(
    tupleId: LsiSymbolId,
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
