package site.addzero.lsi.jimmer.dto

import java.security.MessageDigest
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.model.LsiDeclaredType

data class DtoConfigContractResolution(
    val contracts: List<DtoConfigContract>,
    val diagnostics: List<LsiDiagnostic>,
    val unresolvedTypeIds: List<LsiSymbolId> = emptyList(),
) {
    val contractsByPropId: Map<DtoPropId, List<DtoConfigContract>> =
        contracts.groupBy(DtoConfigContract::propId)

    val successful: Boolean = diagnostics.isEmpty() && unresolvedTypeIds.isEmpty()

    init {
        require(contracts == contracts.sortedWith(DTO_CONFIG_CONTRACT_COMPARATOR)) {
            "DTO config contracts must use stable property and kind order"
        }
        require(contracts.distinctBy { contract -> contract.propId to contract.kind }.size == contracts.size) {
            "DTO config contracts cannot contain duplicate property kinds"
        }
        require(diagnostics == diagnostics.sortedWith(DTO_CONFIG_DIAGNOSTIC_COMPARATOR)) {
            "DTO config diagnostics must use stable order"
        }
        unresolvedTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        require(unresolvedTypeIds == unresolvedTypeIds.distinct().sorted()) {
            "Unresolved DTO config type ids must be distinct and sorted"
        }
    }
}

data class DtoConfigContract(
    val propId: DtoPropId,
    val kind: DtoConfigContractKind,
    val implementationTypeId: LsiSymbolId,
    val targetEntityTypeId: LsiSymbolId,
    val construction: DtoConfigConstructionKind,
    val dependencyTypeIds: List<LsiSymbolId>,
) {
    init {
        implementationTypeId.requireTypeQualifiedName()
        targetEntityTypeId.requireTypeQualifiedName()
        dependencyTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        require(dependencyTypeIds == dependencyTypeIds.distinct().sorted()) {
            "DTO config dependency type ids must be distinct and sorted: ${propId.value}"
        }
        require(implementationTypeId in dependencyTypeIds) {
            "DTO config dependencies must include the implementation type: ${propId.value}"
        }
        require(targetEntityTypeId in dependencyTypeIds) {
            "DTO config dependencies must include the target entity type: ${propId.value}"
        }
    }
}

/** 返回属性指定配置类别的冻结实现类型；没有该配置时返回空。 */
fun DtoBaseProp.configImplementationTypeOrNull(
    graph: DtoGraph,
    resolution: DtoConfigContractResolution,
    kind: DtoConfigContractKind,
): LsiDeclaredType? {
    require(graph.propsById[id] == this) {
        "DTO config property does not belong to this graph: ${id.value}"
    }
    require(resolution.successful) {
        "DTO config implementation type requires a successful contract resolution"
    }
    val expectedTypeId = when (kind) {
        DtoConfigContractKind.FILTER -> config?.filter?.typeId
        DtoConfigContractKind.RECURSION -> config?.recursion?.typeId
    }
    val contract = resolution.contractsByPropId[id]
        .orEmpty()
        .singleOrNull { contract -> contract.kind == kind }
    require((expectedTypeId == null) == (contract == null)) {
        "DTO config contract presence must match the frozen property config: ${id.value} ($kind)"
    }
    if (contract == null) {
        return null
    }
    require(contract.implementationTypeId == expectedTypeId) {
        "DTO config contract implementation must match the frozen property config: ${id.value} ($kind)"
    }
    require(contract.construction == DtoConfigConstructionKind.ZERO_ARGUMENT_CONSTRUCTOR) {
        "Unsupported DTO config construction '${contract.construction}': ${id.value}"
    }
    return LsiDeclaredType(declarationId = contract.implementationTypeId)
}

enum class DtoConfigContractKind {
    FILTER,
    RECURSION,
}

enum class DtoConfigConstructionKind {
    ZERO_ARGUMENT_CONSTRUCTOR,
}

/**
 * 返回平台无关的 DTO config 语义快照；原始诊断仍保留完整 message 与 details 供前端输出。
 */
fun DtoConfigContractResolution.normalizedSnapshot(): String {
    return buildList {
        add(
            configCanonicalValue(
                "resolution",
                successful.toString(),
                unresolvedTypeIds.joinToString(",") { typeId -> typeId.value },
            )
        )
        contracts.forEach { contract ->
            add(
                configCanonicalValue(
                    "contract",
                    contract.propId.value,
                    contract.kind.name,
                    contract.implementationTypeId.value,
                    contract.targetEntityTypeId.value,
                    contract.construction.name,
                    contract.dependencyTypeIds.joinToString(",") { typeId -> typeId.value },
                )
            )
        }
        diagnostics
            .map(LsiDiagnostic::configSemanticCanonicalText)
            .sorted()
            .forEach { diagnostic -> add(configCanonicalValue("diagnostic", diagnostic)) }
    }.joinToString("\n", postfix = "\n")
}

/** 根据平台无关语义快照计算稳定指纹。 */
fun DtoConfigContractResolution.fingerprint(): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest(normalizedSnapshot().toByteArray(Charsets.UTF_8))
    return buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(CONFIG_HEX_DIGITS[value ushr 4])
            append(CONFIG_HEX_DIGITS[value and 0x0f])
        }
    }
}

private val DTO_CONFIG_CONTRACT_COMPARATOR: Comparator<DtoConfigContract> =
    compareBy(DtoConfigContract::propId, DtoConfigContract::kind)

internal val DTO_CONFIG_DIAGNOSTIC_COMPARATOR: Comparator<LsiDiagnostic> =
    compareBy(LsiDiagnostic::configRawCanonicalText)

private fun LsiDiagnostic.configRawCanonicalText(): String = configCanonicalValue(
    code,
    severity.name,
    symbolId?.value.orEmpty(),
    location?.configCanonicalText().orEmpty(),
    message,
    details.toSortedMap().entries.joinToString("\u0000") { (name, value) ->
        configCanonicalValue(name, value)
    },
)

private fun LsiDiagnostic.configSemanticCanonicalText(): String = configCanonicalValue(
    code,
    severity.name,
    symbolId?.value.orEmpty(),
    location?.configCanonicalText().orEmpty(),
    details.configSemanticDetails().entries.joinToString("\u0000") { (name, value) ->
        configCanonicalValue(name, value)
    },
)

private fun Map<String, String>.configSemanticDetails(): Map<String, String> {
    return buildMap {
        CONFIG_SEMANTIC_DETAIL_NAMES.forEach { name ->
            this@configSemanticDetails[name]?.let { value -> put(name, value) }
        }
        this@configSemanticDetails["reason"]?.let { reason ->
            put("reason", reason.configSemanticReason())
        }
    }.toSortedMap()
}

private fun String.configSemanticReason(): String {
    val category = substringBefore(':')
    return when (category) {
        "table-target-ambiguous" -> {
            val targetTypeIds = substringAfter(':', "")
                .split(',')
                .filter(String::isNotEmpty)
                .sorted()
                .joinToString(",")
            if (targetTypeIds.isEmpty()) category else "$category:$targetTypeIds"
        }
        in CONFIG_TYPE_DETAIL_REASON_CATEGORIES -> category
        else -> this
    }
}

private fun LsiLocation.configCanonicalText(): String = configCanonicalValue(
    source.path,
    source.language.name,
    source.kind.name,
    start.line.toString(),
    start.column.toString(),
    end.line.toString(),
    end.column.toString(),
)

private fun configCanonicalValue(vararg fields: String): String {
    return fields.joinToString("\u0001") { field ->
        buildString(field.length) {
            field.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\u0000' -> append("\\0")
                    '\u0001' -> append("\\1")
                    else -> append(char)
                }
            }
        }
    }
}

private const val CONFIG_HEX_DIGITS = "0123456789abcdef"

private val CONFIG_SEMANTIC_DETAIL_NAMES = sortedSetOf(
    "actualTargetTypeId",
    "actualTargetTypeIds",
    "dtoPropId",
    "expectedTargetTypeId",
    "implementationTypeId",
    "kind",
)

private val CONFIG_TYPE_DETAIL_REASON_CATEGORIES = setOf(
    "array-target",
    "filter-table-contract-missing",
    "function-target",
    "nullable-target",
    "parameterized-target",
    "primitive-target",
    "residual-type-parameter",
    "table-contract-arity",
    "unresolved-type",
    "unsupported-target",
)
