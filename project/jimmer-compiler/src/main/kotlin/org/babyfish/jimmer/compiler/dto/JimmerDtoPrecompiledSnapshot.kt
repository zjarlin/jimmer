package org.babyfish.jimmer.compiler.dto

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.model.stableSignature

internal fun JimmerDtoPrecompiledSchema.normalizedSnapshot(): String {
    return buildString {
        documents.forEach { document ->
            val inputDocument = document.inputSnapshot.document
            appendRecord(
                "document",
                inputDocument.source.path,
                inputDocument.sourceSet.name,
                inputDocument.fingerprint,
                document.baseTypeId.value,
                document.sourceTypeName,
                document.targetPackageName.orEmpty(),
            )
            document.inputSnapshot.references.forEach { reference ->
                appendRecord(
                    "reference",
                    inputDocument.source.path,
                    reference.kind.name,
                    reference.typeId.value,
                    reference.location.start.line.toString(),
                    reference.location.start.column.toString(),
                    reference.location.end.line.toString(),
                    reference.location.end.column.toString(),
                )
            }
            document.renderGraph.rootTypeIds.forEachIndexed { index, typeId ->
                appendRecord(
                    "root",
                    inputDocument.source.path,
                    index.toString(),
                    typeId.value,
                )
            }
            document.renderGraph.types.forEach { type -> appendType(type) }
            document.renderGraph.props.forEach { prop -> appendProp(prop) }
            appendAnnotationContract(inputDocument.source.path, document.annotationContract)
            appendInterfaceContractResolution(
                inputDocument.source.path,
                document.interfaceContractResolution,
            )
            appendConfigContractResolution(
                inputDocument.source.path,
                document.configContractResolution,
            )
        }
    }
}

internal fun JimmerDtoPrecompiledSchema.fingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(normalizedSnapshot().toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte) }
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

private fun StringBuilder.appendType(type: JimmerDtoType) {
    appendRecord(
        "type",
        type.id.value,
        type.baseTypeId?.value.orEmpty(),
        type.packageName,
        type.name.orEmpty(),
        type.modifiers
            .sortedWith(compareBy(JimmerDtoModifier::order, JimmerDtoModifier::name))
            .joinToString(",", transform = JimmerDtoModifier::name),
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

private fun StringBuilder.appendProp(prop: JimmerDtoProp) {
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
        is JimmerDtoBaseProp -> appendRecord(
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
            prop.enumType?.canonicalText().orEmpty(),
            prop.config?.canonicalText().orEmpty(),
            prop.recursive.toString(),
            prop.likeOptions.sortedBy(JimmerDtoLikeOption::name).joinToString(",", transform = JimmerDtoLikeOption::name),
        )
        is JimmerDtoUserProp -> appendRecord(
            "user-prop",
            *commonFields,
            prop.type.canonicalText(),
            prop.defaultValueText.orEmpty(),
        )
        is JimmerDtoFoldProp -> appendRecord(
            "fold-prop",
            *commonFields,
            prop.nullGuardPropId?.value.orEmpty(),
            prop.targetTypeId.value,
        )
    }
}

private fun StringBuilder.appendAnnotationContract(
    documentPath: String,
    contract: JimmerDtoAnnotationContract,
) {
    appendRecord(
        "annotation-contract",
        documentPath,
        contract.fingerprint(),
    )
    contract.normalizedSnapshot().lineSequence().filter(String::isNotEmpty).forEachIndexed { index, record ->
        appendRecord(
            "annotation-contract-record",
            documentPath,
            index.toString(),
            record,
        )
    }
    contract.diagnostics
        .sortedBy(LsiDiagnostic::stableOrderKey)
        .forEach { diagnostic ->
            appendRecord(
                "annotation-contract-diagnostic",
                documentPath,
                diagnostic.canonicalText(),
            )
        }
}

private fun StringBuilder.appendInterfaceContractResolution(
    documentPath: String,
    resolution: DtoInterfaceContractResolution,
) {
    appendRecord(
        "interface-contract-resolution",
        documentPath,
        resolution.successful.toString(),
    )
    resolution.contracts.forEach { contract ->
        appendRecord(
            "interface-contract",
            documentPath,
            contract.typeId.value,
            contract.superInterfaceTypeIds.joinToString(",") { typeId -> typeId.value },
        )
        contract.props.forEach { prop ->
            appendRecord(
                "interface-prop",
                documentPath,
                contract.typeId.value,
                prop.declaringTypeId.value,
                prop.name,
                prop.type.stableSignature(),
                prop.mutable.toString(),
                prop.getter?.canonicalText().orEmpty(),
                prop.setter?.canonicalText().orEmpty(),
                prop.origin.canonicalText(),
            )
        }
    }
    resolution.diagnostics
        .sortedBy(LsiDiagnostic::stableOrderKey)
        .forEach { diagnostic ->
            appendRecord(
                "interface-contract-diagnostic",
                documentPath,
                diagnostic.canonicalText(),
            )
        }
}

private fun StringBuilder.appendConfigContractResolution(
    documentPath: String,
    resolution: DtoConfigContractResolution,
) {
    appendRecord(
        "config-contract-resolution",
        documentPath,
        resolution.successful.toString(),
        resolution.unresolvedTypeIds.joinToString(",") { typeId -> typeId.value },
    )
    resolution.contracts.forEach { contract ->
        appendRecord(
            "config-contract",
            documentPath,
            contract.propId.value,
            contract.kind.name,
            contract.implementationTypeId.value,
            contract.targetEntityTypeId.value,
            contract.construction.name,
            contract.dependencyTypeIds.joinToString(",") { typeId -> typeId.value },
        )
    }
    resolution.diagnostics
        .sortedBy(LsiDiagnostic::stableOrderKey)
        .forEach { diagnostic ->
            appendRecord(
                "config-contract-diagnostic",
                documentPath,
                diagnostic.canonicalText(),
            )
        }
}

private fun DtoInterfaceAccessorContract.canonicalText(): String = canonicalValue(
    "accessor",
    declarationId.value,
    name,
    origin.canonicalText(),
)

private fun LsiOrigin.canonicalText(): String = canonicalValue(
    "origin",
    kind.name,
    language.name,
    source?.path.orEmpty(),
    source?.language?.name.orEmpty(),
    source?.kind?.name.orEmpty(),
    originatingSymbols.sorted().joinToString(",") { symbolId -> symbolId.value },
)

private fun LsiDiagnostic.stableOrderKey(): String = canonicalValue(
    "diagnostic-order",
    code,
    severity.name,
    symbolId?.value.orEmpty(),
    location?.canonicalText().orEmpty(),
    message,
    details.canonicalText(),
)

private fun LsiDiagnostic.canonicalText(): String = canonicalValue(
    "diagnostic",
    code,
    severity.name,
    symbolId?.value.orEmpty(),
    location?.canonicalText().orEmpty(),
    message,
    details.canonicalText(),
)

private fun Map<String, String>.canonicalText(): String =
    toSortedMap().entries.canonicalList { (name, value) -> canonicalValue("detail", name, value) }

private fun List<JimmerDtoBasePropBinding>.baseBindingListCanonicalText(): String = canonicalList { binding ->
    canonicalValue("binding", binding.name, binding.propId.value)
}

private fun List<JimmerDtoAnnotation>.annotationListCanonicalText(): String =
    canonicalList(JimmerDtoAnnotation::canonicalText)

private fun JimmerDtoAnnotation.canonicalText(): String = canonicalValue(
    "annotation",
    typeId.value,
    arguments.canonicalList { argument ->
        canonicalValue("argument", argument.name, argument.value.canonicalText())
    },
)

private fun JimmerDtoAnnotationValue.canonicalText(): String = when (this) {
    is JimmerDtoAnnotationValue.ArrayValue -> canonicalValue(
        "array",
        elements.canonicalList(JimmerDtoAnnotationValue::canonicalText),
    )
    is JimmerDtoAnnotationValue.AnnotationValue -> canonicalValue("annotation", annotation.canonicalText())
    is JimmerDtoAnnotationValue.EnumValue -> canonicalValue("enum", enumTypeId.value, constant)
    is JimmerDtoAnnotationValue.TypeValue -> canonicalValue("type", type.canonicalText())
    is JimmerDtoAnnotationValue.LiteralValue -> canonicalValue("literal", code)
}

private fun List<JimmerDtoTypeRef>.typeRefListCanonicalText(): String = canonicalList(JimmerDtoTypeRef::canonicalText)

private fun JimmerDtoTypeRef.canonicalText(): String = canonicalValue(
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

private fun JimmerDtoEnumType.canonicalText(): String = canonicalValue(
    "enum",
    numeric.toString(),
    mappings.canonicalList { mapping -> canonicalValue("mapping", mapping.constant, mapping.value) },
)

private fun JimmerDtoPropConfig.canonicalText(): String = canonicalValue(
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
    limit.toString(),
    offset.toString(),
    batch.toString(),
    depth.toString(),
)

private fun JimmerDtoConfigTypeRef.canonicalText(): String = canonicalValue(
    "config-type",
    typeId.value,
    location.canonicalText(),
)

private fun JimmerDtoPredicate.canonicalText(): String = when (this) {
    is JimmerDtoPredicate.And -> canonicalValue(
        "and",
        predicates.canonicalList(JimmerDtoPredicate::canonicalText),
    )
    is JimmerDtoPredicate.Or -> canonicalValue(
        "or",
        predicates.canonicalList(JimmerDtoPredicate::canonicalText),
    )
    is JimmerDtoPredicate.Comparison -> canonicalValue(
        "comparison",
        path.propPathCanonicalText(),
        operator,
        value.canonicalText(),
    )
    is JimmerDtoPredicate.Nullity -> canonicalValue(
        "nullity",
        path.propPathCanonicalText(),
        negative.toString(),
    )
}

private fun JimmerDtoConfigValue.canonicalText(): String = when (this) {
    is JimmerDtoConfigValue.BooleanValue -> canonicalValue("boolean", value.toString())
    is JimmerDtoConfigValue.LongValue -> canonicalValue("long", value.toString())
    is JimmerDtoConfigValue.BigIntegerValue -> canonicalValue("big-integer", value)
    is JimmerDtoConfigValue.DecimalValue -> canonicalValue("decimal", value)
    is JimmerDtoConfigValue.StringValue -> canonicalValue("string", value)
}

private fun List<JimmerDtoPropPathNode>.propPathCanonicalText(): String = canonicalList { node ->
    canonicalValue("path", node.propId.value, node.associatedId.toString())
}

private fun site.addzero.lsi.core.LsiLocation.canonicalText(): String = canonicalValue(
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
