package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema

/** 校验 DTO 属性配置中的谓词和排序路径是否与冻结不可变模型一致。 */
fun DtoGraph.validateDtoConfigPaths(
    immutableSchema: ImmutableSchema,
): List<LsiDiagnostic> {
    val diagnostics = mutableListOf<LsiDiagnostic>()
    props.filterIsInstance<DtoBaseProp>().forEach { dtoProp ->
        val paths = dtoProp.config?.paths().orEmpty()
        if (paths.isEmpty()) {
            return@forEach
        }
        when (val rootTarget = resolveConfigPathRootTarget(dtoProp, immutableSchema)) {
            is ConfigPathRootTarget.Resolved -> paths.mapNotNullTo(diagnostics) { path ->
                path.validate(dtoProp, rootTarget.typeId, immutableSchema)
            }
            is ConfigPathRootTarget.Unresolved -> paths.forEach { path ->
                diagnostics += dtoProp.pathDiagnostic(
                    code = ROOT_TARGET_UNRESOLVED_CODE,
                    message = rootTarget.message,
                    path = path,
                    extraDetails = buildMap {
                        put("reason", rootTarget.reason)
                        putAll(rootTarget.details)
                    },
                )
            }
        }
    }
    return diagnostics.sortedWith(DTO_CONFIG_DIAGNOSTIC_COMPARATOR)
}

private fun DtoPropConfig.paths(): List<ConfigPath> {
    return buildList {
        predicate
            ?.paths()
            .orEmpty()
            .forEachIndexed { index, path ->
                add(ConfigPath(ConfigPathKind.PREDICATE, index, path))
            }
        orderItems.forEachIndexed { index, orderItem ->
            add(ConfigPath(ConfigPathKind.ORDER, index, orderItem.path))
        }
    }
}

private fun DtoPredicate.paths(): List<List<DtoPropPathNode>> {
    return when (this) {
        is DtoPredicate.And -> predicates.flatMap(DtoPredicate::paths)
        is DtoPredicate.Or -> predicates.flatMap(DtoPredicate::paths)
        is DtoPredicate.Comparison -> listOf(path)
        is DtoPredicate.Nullity -> listOf(path)
    }
}

private fun DtoGraph.resolveConfigPathRootTarget(
    dtoProp: DtoBaseProp,
    immutableSchema: ImmutableSchema,
): ConfigPathRootTarget {
    val tailProp = propsById[dtoProp.tailPropId] as? DtoBaseProp
        ?: return ConfigPathRootTarget.Unresolved(
            reason = "tail-prop-missing",
            message = "Cannot resolve the tail property of DTO config property '${dtoProp.id.value}'",
            details = mapOf("tailDtoPropId" to dtoProp.tailPropId.value),
        )
    val missingBasePropIds = tailProp.baseProps
        .map(DtoBasePropBinding::propId)
        .filterNot(immutableSchema.propsById::containsKey)
        .distinct()
        .sorted()
    if (missingBasePropIds.isNotEmpty()) {
        return ConfigPathRootTarget.Unresolved(
            reason = "base-prop-missing",
            message = "Cannot resolve immutable bindings of DTO config property '${dtoProp.id.value}'",
            details = mapOf(
                "missingBasePropIds" to missingBasePropIds.joinToString(",", transform = LsiSymbolId::value),
                "tailDtoPropId" to tailProp.id.value,
            ),
        )
    }
    val baseProps = tailProp.baseProps.mapNotNull { binding ->
        immutableSchema.propsById[binding.propId]
    }
    val unresolvedTargetPropIds = baseProps
        .filter { prop -> prop.targetTypeId == null }
        .map(ImmutableProp::id)
        .distinct()
        .sorted()
    if (unresolvedTargetPropIds.isNotEmpty()) {
        return ConfigPathRootTarget.Unresolved(
            reason = "base-target-missing",
            message = "Cannot resolve the target type of DTO config property '${dtoProp.id.value}'",
            details = mapOf(
                "basePropIdsWithoutTarget" to unresolvedTargetPropIds.joinToString(",", transform = LsiSymbolId::value),
                "tailDtoPropId" to tailProp.id.value,
            ),
        )
    }
    val targetTypeIds = baseProps.mapNotNull(ImmutableProp::targetTypeId).distinct().sorted()
    if (targetTypeIds.size != 1) {
        return ConfigPathRootTarget.Unresolved(
            reason = "base-target-ambiguous",
            message = "DTO config property '${dtoProp.id.value}' does not have one stable target type",
            details = mapOf(
                "targetTypeIds" to targetTypeIds.joinToString(",", transform = LsiSymbolId::value),
                "tailDtoPropId" to tailProp.id.value,
            ),
        )
    }
    val targetTypeId = targetTypeIds.single()
    if (targetTypeId !in immutableSchema.typesById) {
        return ConfigPathRootTarget.Unresolved(
            reason = "target-type-missing",
            message = "DTO config property '${dtoProp.id.value}' targets missing immutable type '${targetTypeId.value}'",
            details = mapOf(
                "targetTypeId" to targetTypeId.value,
                "tailDtoPropId" to tailProp.id.value,
            ),
        )
    }
    return ConfigPathRootTarget.Resolved(targetTypeId)
}

private fun ConfigPath.validate(
    dtoProp: DtoBaseProp,
    rootOwnerTypeId: LsiSymbolId,
    immutableSchema: ImmutableSchema,
): LsiDiagnostic? {
    if (nodes.isEmpty()) {
        return dtoProp.pathDiagnostic(
            code = EMPTY_PATH_CODE,
            message = "DTO config ${kind.detailValue} path cannot be empty",
            path = this,
        )
    }
    var expectedOwnerTypeId = rootOwnerTypeId
    nodes.forEachIndexed { index, node ->
        val prop = immutableSchema.propsById[node.propId]
            ?: return dtoProp.pathDiagnostic(
                code = PROP_MISSING_CODE,
                message = "DTO config ${kind.detailValue} path references missing immutable property " +
                    "'${node.propId.value}'",
                path = this,
                nodeIndex = index,
                node = node,
                extraDetails = mapOf("expectedOwnerTypeId" to expectedOwnerTypeId.value),
            )
        if (prop.ownerTypeId != expectedOwnerTypeId) {
            return dtoProp.pathDiagnostic(
                code = OWNER_MISMATCH_CODE,
                message = "DTO config ${kind.detailValue} path property '${prop.id.value}' belongs to " +
                    "'${prop.ownerTypeId.value}', expected '${expectedOwnerTypeId.value}'",
                path = this,
                nodeIndex = index,
                node = node,
                extraDetails = mapOf(
                    "actualOwnerTypeId" to prop.ownerTypeId.value,
                    "expectedOwnerTypeId" to expectedOwnerTypeId.value,
                ),
            )
        }
        val terminal = index == nodes.lastIndex
        if (node.associatedId) {
            return validateAssociatedId(dtoProp, prop, index, terminal, immutableSchema)
        }
        if (terminal) {
            if (prop.association) {
                return dtoProp.pathDiagnostic(
                    code = TERMINAL_ASSOCIATION_CODE,
                    message = "Terminal DTO config ${kind.detailValue} path property '${prop.id.value}' " +
                        "must use its associated-id form",
                    path = this,
                    nodeIndex = index,
                    node = node,
                )
            }
            return null
        }
        if (!prop.embedded && (!prop.association || prop.list)) {
            return dtoProp.pathDiagnostic(
                code = NON_TERMINAL_CATEGORY_CODE,
                message = "Non-terminal DTO config ${kind.detailValue} path property '${prop.id.value}' " +
                    "must be an embedded property or reference association",
                path = this,
                nodeIndex = index,
                node = node,
                extraDetails = mapOf(
                    "association" to prop.association.toString(),
                    "embedded" to prop.embedded.toString(),
                    "list" to prop.list.toString(),
                ),
            )
        }
        val targetTypeId = prop.targetTypeId
        if (targetTypeId == null || targetTypeId !in immutableSchema.typesById) {
            return dtoProp.pathDiagnostic(
                code = TARGET_UNRESOLVED_CODE,
                message = "Non-terminal DTO config ${kind.detailValue} path property '${prop.id.value}' " +
                    "has no resolvable target type",
                path = this,
                nodeIndex = index,
                node = node,
                extraDetails = mapOf("targetTypeId" to targetTypeId?.value.orEmpty()),
            )
        }
        expectedOwnerTypeId = targetTypeId
    }
    return null
}

private fun ConfigPath.validateAssociatedId(
    dtoProp: DtoBaseProp,
    prop: ImmutableProp,
    nodeIndex: Int,
    terminal: Boolean,
    immutableSchema: ImmutableSchema,
): LsiDiagnostic? {
    val node = nodes[nodeIndex]
    if (!terminal) {
        return dtoProp.pathDiagnostic(
            code = ASSOCIATED_ID_NON_TERMINAL_CODE,
            message = "DTO config ${kind.detailValue} associated-id path property '${prop.id.value}' must be terminal",
            path = this,
            nodeIndex = nodeIndex,
            node = node,
        )
    }
    if (!prop.association) {
        return dtoProp.pathDiagnostic(
            code = ASSOCIATED_ID_NON_ASSOCIATION_CODE,
            message = "DTO config ${kind.detailValue} associated-id path property '${prop.id.value}' " +
                "must be an association",
            path = this,
            nodeIndex = nodeIndex,
            node = node,
        )
    }
    if (prop.list) {
        return dtoProp.pathDiagnostic(
            code = ASSOCIATED_ID_LIST_CODE,
            message = "DTO config ${kind.detailValue} associated-id path property '${prop.id.value}' " +
                "cannot be a list association",
            path = this,
            nodeIndex = nodeIndex,
            node = node,
        )
    }
    val targetTypeId = prop.targetTypeId
    val targetType = targetTypeId?.let(immutableSchema.typesById::get)
    val targetIdProp = targetType?.idPropId?.let(immutableSchema.propsById::get)
    if (targetIdProp == null || targetIdProp.ownerTypeId != targetTypeId) {
        return dtoProp.pathDiagnostic(
            code = ASSOCIATED_ID_TARGET_ID_UNRESOLVED_CODE,
            message = "DTO config ${kind.detailValue} associated-id path property '${prop.id.value}' " +
                "has no resolvable target id property",
            path = this,
            nodeIndex = nodeIndex,
            node = node,
            extraDetails = mapOf(
                "targetIdPropId" to targetType?.idPropId?.value.orEmpty(),
                "targetTypeId" to targetTypeId?.value.orEmpty(),
            ),
        )
    }
    return null
}

private fun DtoBaseProp.pathDiagnostic(
    code: String,
    message: String,
    path: ConfigPath,
    nodeIndex: Int? = null,
    node: DtoPropPathNode? = null,
    extraDetails: Map<String, String> = emptyMap(),
): LsiDiagnostic {
    return LsiDiagnostic(
        code = code,
        severity = LsiDiagnosticSeverity.ERROR,
        message = message,
        symbolId = node?.propId,
        location = baseLocation,
        details = buildMap {
            put("dtoPropId", id.value)
            put("dtoPropName", name)
            put("path", path.canonicalText())
            put("pathKind", path.kind.detailValue)
            put("pathOrdinal", path.ordinal.toString())
            nodeIndex?.let { index -> put("pathNodeIndex", index.toString()) }
            node?.let { value -> put("pathPropId", value.propId.value) }
            putAll(extraDetails)
        }.toSortedMap(),
    )
}

private fun ConfigPath.canonicalText(): String {
    return nodes.joinToString(" -> ") { node ->
        if (node.associatedId) {
            "${node.propId.value}[associated-id]"
        } else {
            node.propId.value
        }
    }
}

private data class ConfigPath(
    val kind: ConfigPathKind,
    val ordinal: Int,
    val nodes: List<DtoPropPathNode>,
)

private enum class ConfigPathKind(
    val detailValue: String,
) {
    PREDICATE("predicate"),
    ORDER("order"),
}

private sealed interface ConfigPathRootTarget {
    data class Resolved(val typeId: LsiSymbolId) : ConfigPathRootTarget

    data class Unresolved(
        val reason: String,
        val message: String,
        val details: Map<String, String>,
    ) : ConfigPathRootTarget
}

private const val EMPTY_PATH_CODE = "jimmer.dto.config.path-empty"
private const val ROOT_TARGET_UNRESOLVED_CODE = "jimmer.dto.config.path-root-target-unresolved"
private const val PROP_MISSING_CODE = "jimmer.dto.config.path-prop-missing"
private const val OWNER_MISMATCH_CODE = "jimmer.dto.config.path-owner-mismatch"
private const val TERMINAL_ASSOCIATION_CODE = "jimmer.dto.config.path-terminal-association"
private const val NON_TERMINAL_CATEGORY_CODE = "jimmer.dto.config.path-non-terminal-category"
private const val TARGET_UNRESOLVED_CODE = "jimmer.dto.config.path-target-unresolved"
private const val ASSOCIATED_ID_NON_TERMINAL_CODE = "jimmer.dto.config.path-associated-id-non-terminal"
private const val ASSOCIATED_ID_NON_ASSOCIATION_CODE = "jimmer.dto.config.path-associated-id-non-association"
private const val ASSOCIATED_ID_LIST_CODE = "jimmer.dto.config.path-associated-id-list"
private const val ASSOCIATED_ID_TARGET_ID_UNRESOLVED_CODE =
    "jimmer.dto.config.path-associated-id-target-id-unresolved"
