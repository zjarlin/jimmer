package site.addzero.lsi.jimmer.dto

import java.security.MessageDigest
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.model.stableSignature

/**
 * 返回 DTO 接口契约解析结果的稳定语义快照。
 *
 * 快照保留接口继承顺序、属性类型与可变性、访问器来源及完整诊断，
 * 不包含 APT、KSP 或代码生成器的实现信息。
 */
fun DtoInterfaceContractResolution.normalizedSnapshot(): String {
    return buildList {
        add(interfaceCanonicalValue("resolution", successful.toString()))
        contracts.forEach { contract ->
            add(
                interfaceCanonicalValue(
                    "contract",
                    contract.typeId.value,
                    contract.superInterfaceTypeIds.interfaceCanonicalList { typeId -> typeId.value },
                )
            )
            contract.props.forEach { prop ->
                add(
                    interfaceCanonicalValue(
                        "prop",
                        contract.typeId.value,
                        prop.declaringTypeId.value,
                        prop.name,
                        prop.type.stableSignature(),
                        prop.mutable.toString(),
                        prop.getter?.interfaceCanonicalText().orEmpty(),
                        prop.setter?.interfaceCanonicalText().orEmpty(),
                        prop.origin.interfaceCanonicalText(),
                    )
                )
            }
        }
        diagnostics
            .sortedBy(LsiDiagnostic::interfaceStableOrderKey)
            .forEach { diagnostic ->
                add(interfaceCanonicalValue("diagnostic", diagnostic.interfaceCanonicalText()))
            }
    }.joinToString("\n", postfix = "\n")
}

/** 根据 DTO 接口契约的稳定语义快照计算 SHA-256 指纹。 */
fun DtoInterfaceContractResolution.fingerprint(): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest(normalizedSnapshot().toByteArray(Charsets.UTF_8))
    return buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(INTERFACE_HEX_DIGITS[value ushr 4])
            append(INTERFACE_HEX_DIGITS[value and 0x0f])
        }
    }
}

private fun DtoInterfaceAccessorContract.interfaceCanonicalText(): String = interfaceCanonicalValue(
    "accessor",
    declarationId.value,
    name,
    origin.interfaceCanonicalText(),
)

private fun LsiOrigin.interfaceCanonicalText(): String = interfaceCanonicalValue(
    "origin",
    kind.name,
    language.name,
    source?.path.orEmpty(),
    source?.language?.name.orEmpty(),
    source?.kind?.name.orEmpty(),
    originatingSymbols.sorted().interfaceCanonicalList { symbolId -> symbolId.value },
)

private fun LsiDiagnostic.interfaceStableOrderKey(): String = interfaceCanonicalValue(
    "diagnostic-order",
    code,
    severity.name,
    symbolId?.value.orEmpty(),
    location?.interfaceCanonicalText().orEmpty(),
    message,
    details.interfaceCanonicalText(),
)

private fun LsiDiagnostic.interfaceCanonicalText(): String = interfaceCanonicalValue(
    "diagnostic",
    code,
    severity.name,
    symbolId?.value.orEmpty(),
    location?.interfaceCanonicalText().orEmpty(),
    message,
    details.interfaceCanonicalText(),
)

private fun Map<String, String>.interfaceCanonicalText(): String =
    toSortedMap().entries.interfaceCanonicalList { (name, value) ->
        interfaceCanonicalValue("detail", name, value)
    }

private fun LsiLocation.interfaceCanonicalText(): String = interfaceCanonicalValue(
    "location",
    source.path,
    source.language.name,
    source.kind.name,
    start.line.toString(),
    start.column.toString(),
    end.line.toString(),
    end.column.toString(),
)

private fun interfaceCanonicalValue(
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

private inline fun <T> Iterable<T>.interfaceCanonicalList(
    transform: (T) -> String,
): String = buildString {
    for (element in this@interfaceCanonicalList) {
        val value = transform(element)
        append(value.length)
        append(':')
        append(value)
    }
}

private const val INTERFACE_HEX_DIGITS = "0123456789abcdef"
