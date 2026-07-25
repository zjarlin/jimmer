package site.addzero.lsi.jimmer.dto

import java.security.MessageDigest
import site.addzero.lsi.core.LsiLocation

/** 返回仅包含 DTO 图语义的规范化快照。 */
fun DtoGraph.normalizedSnapshot(): String {
    return buildString {
        appendRecord(
            "graph",
            source.path,
            source.language.name,
            source.kind.name,
        )
        rootTypeIds.forEachIndexed { index, typeId ->
            appendRecord(
                "root",
                index.toString(),
                typeId.value,
            )
        }
        types.sortedBy(DtoType::id).forEach { type -> appendType(type) }
        props.sortedBy(DtoProp::id).forEach { prop -> appendProp(prop) }
    }
}

/** 根据规范化 DTO 图语义计算稳定指纹。 */
fun DtoGraph.fingerprint(): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest(normalizedSnapshot().toByteArray(Charsets.UTF_8))
    return buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(DTO_GRAPH_HEX_DIGITS[value ushr 4])
            append(DTO_GRAPH_HEX_DIGITS[value and 0x0f])
        }
    }
}

private fun StringBuilder.appendType(type: DtoType) {
    appendRecord(
        "type",
        type.id.value,
        type.baseTypeId?.value.orEmpty(),
        type.packageName,
        type.name.orEmpty(),
        type.modifiers
            .sortedWith(compareBy(DtoModifier::order, DtoModifier::name))
            .joinToString(",", transform = DtoModifier::name),
        type.annotations.annotationListCanonicalText(),
        type.superInterfaces.typeRefListCanonicalText(),
        type.documentation.orEmpty(),
        type.location.canonicalText(),
        type.focusedRecursion.toString(),
        type.propIds.joinToString(",") { propId -> propId.value },
        type.hiddenFlatPropIds.joinToString(",") { propId -> propId.value },
        type.polymorphism?.exhaustive?.toString().orEmpty(),
    )
    type.polymorphism?.branches.orEmpty().forEachIndexed { index, branch ->
        appendRecord(
            "branch",
            type.id.value,
            index.toString(),
            branch.kind.name,
            branch.targetBaseTypeId?.value.orEmpty(),
            branch.declaredClassName.orEmpty(),
            branch.className,
            branch.bodyTypeId.value,
            branch.mergedTypeId.value,
            branch.implicit.toString(),
            branch.location.canonicalText(),
        )
    }
}

private fun StringBuilder.appendProp(prop: DtoProp) {
    val commonFields = arrayOf(
        prop.id.value,
        prop.ownerTypeId.value,
        prop.name,
        prop.alias.orEmpty(),
        prop.nullable.toString(),
        prop.annotations.annotationListCanonicalText(),
        prop.documentation.orEmpty(),
        prop.aliasLocation.canonicalText(),
    )
    when (prop) {
        is DtoBaseProp -> appendRecord(
            "base-prop",
            *commonFields,
            prop.baseLocation.canonicalText(),
            prop.baseProps.baseBindingListCanonicalText(),
            prop.basePath,
            prop.nextPropId?.value.orEmpty(),
            prop.tailPropId.value,
            prop.baseNullable.toString(),
            prop.inputModifier.name,
            prop.functionName.orEmpty(),
            prop.targetTypeId?.value.orEmpty(),
            prop.targetTypeReference?.canonicalText().orEmpty(),
            prop.enumType?.canonicalText().orEmpty(),
            prop.config?.canonicalText().orEmpty(),
            prop.recursive.toString(),
            prop.likeOptions.sortedBy(DtoLikeOption::name).joinToString(",", transform = DtoLikeOption::name),
            prop.dtoDocumentation.orEmpty(),
        )
        is DtoUserProp -> appendRecord(
            "user-prop",
            *commonFields,
            prop.type.canonicalText(),
            prop.defaultValueText.orEmpty(),
        )
        is DtoFoldProp -> appendRecord(
            "fold-prop",
            *commonFields,
            prop.nullGuardPropId?.value.orEmpty(),
            prop.targetTypeId.value,
        )
    }
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

private fun List<DtoBasePropBinding>.baseBindingListCanonicalText(): String = canonicalList { binding ->
    canonicalValue("binding", binding.name, binding.propId.value)
}

private fun List<DtoAnnotation>.annotationListCanonicalText(): String =
    canonicalList(DtoAnnotation::canonicalText)

private fun DtoAnnotation.canonicalText(): String = canonicalValue(
    "annotation",
    typeId.value,
    arguments.canonicalList { argument ->
        canonicalValue("argument", argument.name, argument.value.canonicalText())
    },
)

private fun DtoAnnotationValue.canonicalText(): String = when (this) {
    is DtoAnnotationValue.ArrayValue -> canonicalValue(
        "array",
        elements.canonicalList(DtoAnnotationValue::canonicalText),
    )
    is DtoAnnotationValue.AnnotationValue -> canonicalValue("annotation", annotation.canonicalText())
    is DtoAnnotationValue.EnumValue -> canonicalValue("enum", enumTypeId.value, constant)
    is DtoAnnotationValue.TypeValue -> canonicalValue("type", type.canonicalText())
    is DtoAnnotationValue.LiteralValue -> canonicalValue("literal", code)
}

private fun List<DtoTypeRef>.typeRefListCanonicalText(): String = canonicalList(DtoTypeRef::canonicalText)

private fun DtoTypeRef.canonicalText(): String = canonicalValue(
    "type",
    typeName,
    nullable.toString(),
    location.canonicalText(),
    arguments.canonicalList { argument ->
        canonicalValue(
            "argument",
            argument.variance.name,
            argument.type?.canonicalText().orEmpty(),
        )
    },
)

private fun DtoEnumType.canonicalText(): String = canonicalValue(
    "enum",
    numeric.toString(),
    mappings.canonicalList { mapping -> canonicalValue("mapping", mapping.constant, mapping.value) },
)

private fun DtoReusableTypeReference.canonicalText(): String = canonicalValue(
    "reusable-type",
    qualifiedName,
    targetBaseTypeId.value,
    kind.name,
    location.canonicalText(),
)

private fun DtoPropConfig.canonicalText(): String = canonicalValue(
    "config",
    predicate?.canonicalText().orEmpty(),
    orderItems.canonicalList { orderItem ->
        canonicalValue(
            "order",
            orderItem.path.propPathCanonicalText(),
            orderItem.descending.toString(),
        )
    },
    filter?.canonicalText().orEmpty(),
    recursion?.canonicalText().orEmpty(),
    fetchType.name,
    limit?.canonicalText().orEmpty(),
    batch?.toString().orEmpty(),
    depth?.toString().orEmpty(),
)

private fun DtoLimit.canonicalText(): String = canonicalValue(
    "limit",
    value.toString(),
    offset.toString(),
)

private fun DtoConfigTypeRef.canonicalText(): String = canonicalValue(
    "config-type",
    typeId.value,
    location.canonicalText(),
)

private fun DtoPredicate.canonicalText(): String = when (this) {
    is DtoPredicate.And -> canonicalValue(
        "and",
        predicates.canonicalList(DtoPredicate::canonicalText),
    )
    is DtoPredicate.Or -> canonicalValue(
        "or",
        predicates.canonicalList(DtoPredicate::canonicalText),
    )
    is DtoPredicate.Comparison -> canonicalValue(
        "comparison",
        path.propPathCanonicalText(),
        operator.token,
        value.canonicalText(),
    )
    is DtoPredicate.Nullity -> canonicalValue(
        "nullity",
        path.propPathCanonicalText(),
        negative.toString(),
    )
}

private fun DtoConfigValue.canonicalText(): String = when (this) {
    is DtoConfigValue.BooleanValue -> canonicalValue("boolean", value.toString())
    is DtoConfigValue.LongValue -> canonicalValue("long", value.toString())
    is DtoConfigValue.BigIntegerValue -> canonicalValue("big-integer", value)
    is DtoConfigValue.DecimalValue -> canonicalValue("decimal", value)
    is DtoConfigValue.StringValue -> canonicalValue("string", value)
}

private fun List<DtoPropPathNode>.propPathCanonicalText(): String = canonicalList { node ->
    canonicalValue("path", node.propId.value, node.associatedId.toString())
}

private fun LsiLocation.canonicalText(): String = canonicalValue(
    "location",
    source.path,
    source.language.name,
    source.kind.name,
    start.line.toString(),
    start.column.toString(),
    end.line.toString(),
    end.column.toString(),
)

private fun canonicalValue(
    kind: String,
    vararg fields: String,
): String = buildString {
    append(kind.length)
    append(':')
    append(kind)
    fields.forEach { field ->
        append(field.length)
        append(':')
        append(field)
    }
}

private inline fun <T> Iterable<T>.canonicalList(
    transform: (T) -> String,
): String = buildString {
    for (element in this@canonicalList) {
        val value = transform(element)
        append(value.length)
        append(':')
        append(value)
    }
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

private const val DTO_GRAPH_HEX_DIGITS = "0123456789abcdef"
