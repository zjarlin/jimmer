package site.addzero.lsi.jimmer.dto

import java.security.MessageDigest
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationMember
import site.addzero.lsi.model.LsiAnnotationTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeHierarchyEntry
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiTypeSystem
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVariance
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.annotationTargetPolicy
import site.addzero.lsi.model.stableSignature

data class DtoAnnotationContract(
    val declarations: List<DtoAnnotationDeclaration>,
    val typePlans: List<DtoTypeAnnotationPlan>,
    val propPlans: List<DtoPropAnnotationPlan>,
    val diagnostics: List<LsiDiagnostic>,
) {
    val declarationsByTypeId: Map<LsiSymbolId, DtoAnnotationDeclaration> =
        declarations.associateBy(DtoAnnotationDeclaration::typeId)

    val typePlansByTypeId: Map<DtoTypeId, DtoTypeAnnotationPlan> =
        typePlans.associateBy(DtoTypeAnnotationPlan::typeId)

    val propPlansByPropId: Map<DtoPropId, DtoPropAnnotationPlan> =
        propPlans.associateBy(DtoPropAnnotationPlan::propId)

    init {
        require(declarations == declarations.sortedBy(DtoAnnotationDeclaration::typeId)) {
            "DTO annotation declarations must use stable type id order"
        }
        require(declarationsByTypeId.size == declarations.size) {
            "DTO annotation contract cannot contain duplicate declarations"
        }
        require(typePlans == typePlans.sortedBy(DtoTypeAnnotationPlan::typeId)) {
            "DTO type annotation plans must use stable type id order"
        }
        require(typePlansByTypeId.size == typePlans.size) {
            "DTO annotation contract cannot contain duplicate type plans"
        }
        require(propPlans == propPlans.sortedBy(DtoPropAnnotationPlan::propId)) {
            "DTO property annotation plans must use stable property id order"
        }
        require(propPlansByPropId.size == propPlans.size) {
            "DTO annotation contract cannot contain duplicate property plans"
        }
        require(diagnostics == diagnostics.sortedBy(LsiDiagnostic::stableOrderKey)) {
            "DTO annotation diagnostics must use stable order"
        }
        val appliedAnnotations = buildList {
            typePlans.flatMapTo(this) { plan -> plan.applications.map(DtoAnnotationApplication::annotation) }
            propPlans.flatMapTo(this) { plan ->
                plan.propertyApplications.map(DtoAnnotationApplication::annotation)
            }
            propPlans.flatMapTo(this) { plan ->
                plan.builderSetterApplications.map(DtoBuilderSetterAnnotationApplication::annotation)
            }
        }
        appliedAnnotations.forEach { annotation ->
            require(annotation.type in declarationsByTypeId) {
                "DTO annotation application must reference a frozen declaration: " +
                    annotation.type.value
            }
        }
    }
}

data class DtoAnnotationDeclaration(
    val typeId: LsiSymbolId,
    val kind: DtoAnnotationDeclarationKind,
    val targetDeclared: Boolean,
    val allowedPlacements: List<DtoAnnotationPlacement>,
    val argumentTypes: Map<String, LsiTypeRef>,
    val kotlinValueVararg: Boolean,
    val argumentNamesInDeclarationOrder: List<String> = argumentTypes.keys.toList(),
) {
    val argumentNames: List<String> = argumentTypes.keys.toList()

    init {
        typeId.requireTypeQualifiedName()
        require(allowedPlacements == allowedPlacements.distinct().sorted()) {
            "DTO annotation declaration placements must be distinct and sorted: ${typeId.value}"
        }
        require(targetDeclared || allowedPlacements.isEmpty()) {
            "DTO annotation declaration without target cannot expose declared placements: ${typeId.value}"
        }
        require(argumentNames == argumentNames.sorted()) {
            "DTO annotation declaration argument names must be distinct and sorted: ${typeId.value}"
        }
        require(argumentNamesInDeclarationOrder.toSet() == argumentNames.toSet()) {
            "DTO annotation declaration order must contain every argument exactly once: ${typeId.value}"
        }
        require(argumentNamesInDeclarationOrder.distinct().size == argumentNamesInDeclarationOrder.size) {
            "DTO annotation declaration order cannot contain duplicate arguments: ${typeId.value}"
        }
        require(argumentTypes.values.all { type -> type == type.toDtoAnnotationMemberType() }) {
            "DTO annotation declaration argument types must use canonical annotation member semantics: ${typeId.value}"
        }
        require(!kotlinValueVararg || kind == DtoAnnotationDeclarationKind.KOTLIN) {
            "Only Kotlin annotation declarations can expose a value vararg: ${typeId.value}"
        }
        require(!kotlinValueVararg || "value" in argumentTypes) {
            "Kotlin annotation value vararg requires a value argument: ${typeId.value}"
        }
    }
}

enum class DtoAnnotationDeclarationKind {
    JAVA,
    KOTLIN,
}

enum class DtoAnnotationPlacement {
    TYPE,
    ANNOTATION_TYPE,
    CONSTRUCTOR,
    FIELD,
    GETTER,
    SETTER,
    PROPERTY,
    PARAMETER,
    SET_PARAMETER,
    RECEIVER,
    DELEGATE,
    TYPE_USE,
    TYPE_PARAMETER,
    LOCAL_VARIABLE,
    EXPRESSION,
    FILE,
    TYPE_ALIAS,
}

data class DtoTypeAnnotationPlan(
    val typeId: DtoTypeId,
    val applications: List<DtoAnnotationApplication>,
)

data class DtoPropAnnotationPlan(
    val propId: DtoPropId,
    val propertyApplications: List<DtoAnnotationApplication>,
    val builderSetterApplications: List<DtoBuilderSetterAnnotationApplication>,
) {
    init {
        require(
            builderSetterApplications.map { application -> application.annotation.type }.distinct().size ==
                builderSetterApplications.size
        ) {
            "DTO builder setter annotations must be unique by exact type id: ${propId.value}"
        }
    }
}

data class DtoBuilderSetterAnnotationApplication(
    val annotation: LsiAnnotation,
    val origin: DtoAnnotationOrigin,
    val sourceSymbolId: LsiSymbolId?,
) {
    init {
        sourceSymbolId?.let { symbolId ->
            require(symbolId.value.isNotBlank()) { "DTO annotation source symbol id cannot be blank" }
        }
        require(origin == DtoAnnotationOrigin.IMMUTABLE || sourceSymbolId == null) {
            "DTO-authored builder setter annotation cannot reference an immutable source symbol"
        }
    }
}

data class DtoAnnotationApplication(
    val annotation: LsiAnnotation,
    val origin: DtoAnnotationOrigin,
    val sourceSymbolId: LsiSymbolId?,
    val placements: List<DtoAnnotationPlacement>,
) {
    init {
        sourceSymbolId?.let { symbolId ->
            require(symbolId.value.isNotBlank()) { "DTO annotation source symbol id cannot be blank" }
        }
        require(placements.isNotEmpty()) {
            "DTO annotation application must have at least one placement: ${annotation.type.value}"
        }
        require(placements == placements.distinct().sorted()) {
            "DTO annotation application placements must be distinct and sorted: ${annotation.type.value}"
        }
        require(origin == DtoAnnotationOrigin.IMMUTABLE || sourceSymbolId == null) {
            "DTO-authored annotation application cannot reference an immutable source symbol"
        }
    }

}

enum class DtoAnnotationOrigin {
    IMMUTABLE,
    DTO,
}

/** 解析 DTO 图的有效注解契约。 */
fun LsiWorkspace.resolveDtoAnnotationContract(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): DtoAnnotationContract {
    return DtoAnnotationContractResolver(this, immutableSchema).resolve(graph)
}

/** 返回当前 DTO 类型的已冻结注解计划。 */
fun DtoType.typeAnnotationPlan(
    annotationContract: DtoAnnotationContract,
): DtoTypeAnnotationPlan {
    return requireNotNull(annotationContract.typePlansByTypeId[id]) {
        "DTO annotation contract has no type plan: ${id.value}"
    }
}

/** 返回当前 DTO 类型的已冻结有效注解应用。 */
fun DtoType.typeAnnotationApplications(
    annotationContract: DtoAnnotationContract,
): List<DtoAnnotationApplication> {
    return typeAnnotationPlan(annotationContract).applications
}

/** 判断当前 DTO 类型是否具有指定类型的已冻结有效注解。 */
fun DtoType.hasTypeAnnotation(
    annotationContract: DtoAnnotationContract,
    annotationTypeId: LsiSymbolId,
): Boolean {
    annotationTypeId.requireTypeQualifiedName()
    return typeAnnotationApplications(annotationContract).any { application ->
        application.annotation.type == annotationTypeId
    }
}

/** 返回当前 DTO 属性的已冻结注解计划。 */
fun DtoProp.propAnnotationPlan(
    annotationContract: DtoAnnotationContract,
): DtoPropAnnotationPlan {
    return requireNotNull(annotationContract.propPlansByPropId[id]) {
        "DTO annotation contract has no property plan: ${id.value}"
    }
}

/** 返回当前 DTO 属性的全部已冻结有效注解应用。 */
fun DtoProp.propertyAnnotationApplications(
    annotationContract: DtoAnnotationContract,
): List<DtoAnnotationApplication> {
    return propAnnotationPlan(annotationContract).propertyApplications
}

/** 返回用于源码保真的属性注解应用，并从不可变模型快照恢复默认参数。 */
fun DtoProp.propertySourceAnnotationApplications(
    annotationContract: DtoAnnotationContract,
    immutableSchema: ImmutableSchema,
): List<DtoAnnotationApplication> {
    val nextOccurrenceBySourceAndType = mutableMapOf<Pair<LsiSymbolId, LsiSymbolId>, Int>()
    return propertyAnnotationApplications(annotationContract).map { application ->
        if (application.origin != DtoAnnotationOrigin.IMMUTABLE) {
            return@map application
        }
        val sourceSymbolId = requireNotNull(application.sourceSymbolId) {
            "Frozen immutable DTO property annotation has no source symbol: ${application.annotation.type.value}"
        }
        val occurrenceKey = sourceSymbolId to application.annotation.type
        val occurrence = nextOccurrenceBySourceAndType.getOrDefault(occurrenceKey, 0)
        nextOccurrenceBySourceAndType[occurrenceKey] = occurrence + 1
        val sourceAnnotation = immutableSchema.propsById[sourceSymbolId]
            ?.annotations
            ?.filter { annotation -> annotation.type == application.annotation.type }
            ?.getOrNull(occurrence)
            ?: error(
                "Frozen DTO property annotation application has no immutable source occurrence: " +
                    "${sourceSymbolId.value}:${application.annotation.type.value}#$occurrence"
            )
        application.copy(annotation = sourceAnnotation.copy(useSiteTarget = null))
    }
}

/** 按属性落点返回已冻结有效注解应用，并保留契约中的精确顺序。 */
fun DtoProp.propertyAnnotationApplications(
    annotationContract: DtoAnnotationContract,
    placement: DtoAnnotationPlacement,
): List<DtoAnnotationApplication> {
    require(placement in PROP_APPLICATION_PLACEMENTS) {
        "DTO property annotation placement is not supported: ${placement.name}"
    }
    return propertyAnnotationApplications(annotationContract).filter { application ->
        placement in application.placements
    }
}

private class DtoAnnotationContractResolver(
    private val workspace: LsiWorkspace,
    private val immutableSchema: ImmutableSchema,
) {
    private val annotationTypeSystem = LsiTypeSystem(
        workspace = workspace,
        fallbackTypeHierarchy = JVM_PRIMITIVE_WRAPPER_TYPE_HIERARCHY,
    )

    fun resolve(graph: DtoGraph): DtoAnnotationContract {
        val diagnostics = mutableListOf<LsiDiagnostic>()
        val typeCandidates = graph.types.map { type ->
            typeCandidates(type, diagnostics)
        }
        val propCandidates = graph.props.map { prop ->
            PropAnnotationCandidates(
                property = propertyCandidates(graph, prop, diagnostics),
                builderSetter = builderSetterCandidates(graph, prop, diagnostics),
            )
        }
        val allCandidates = typeCandidates.flatMap(AnnotationTargetCandidates::candidates) +
            propCandidates.flatMap { candidates ->
                candidates.property.candidates + candidates.builderSetter.candidates
            }
        val annotationTypeIds = buildSet {
            allCandidates.forEach { candidate -> candidate.annotation.collectAnnotationTypeIds(this) }
        }
        val declarations = annotationTypeIds.sorted().mapNotNull { typeId ->
            freezeDeclaration(typeId, allCandidates, diagnostics)
        }
        val declarationsByTypeId = declarations.associateBy(DtoAnnotationDeclaration::typeId)
        val typePlans = typeCandidates.map { target ->
            DtoTypeAnnotationPlan(
                typeId = DtoTypeId(target.targetId),
                applications = freezeApplications(
                    target = target,
                    targetKind = AnnotationPlanTargetKind.TYPE,
                    supportedPlacements = TYPE_APPLICATION_PLACEMENTS,
                    declarationsByTypeId = declarationsByTypeId,
                    diagnostics = diagnostics,
                ),
            )
        }.sortedBy(DtoTypeAnnotationPlan::typeId)
        val propPlans = propCandidates.map { candidates ->
            DtoPropAnnotationPlan(
                propId = DtoPropId(candidates.property.targetId),
                propertyApplications = freezeApplications(
                    target = candidates.property,
                    targetKind = AnnotationPlanTargetKind.PROP,
                    supportedPlacements = PROP_APPLICATION_PLACEMENTS,
                    declarationsByTypeId = declarationsByTypeId,
                    diagnostics = diagnostics,
                ),
                builderSetterApplications = freezeBuilderSetterApplications(
                    target = candidates.builderSetter,
                    declarationsByTypeId = declarationsByTypeId,
                    diagnostics = diagnostics,
                ),
            )
        }.sortedBy(DtoPropAnnotationPlan::propId)
        return DtoAnnotationContract(
            declarations = declarations.sortedBy(DtoAnnotationDeclaration::typeId),
            typePlans = typePlans,
            propPlans = propPlans,
            diagnostics = diagnostics
                .distinctBy(LsiDiagnostic::canonicalText)
                .sortedBy(LsiDiagnostic::stableOrderKey),
        )
    }

    private fun typeCandidates(
        type: DtoType,
        diagnostics: MutableList<LsiDiagnostic>,
    ): AnnotationTargetCandidates {
        val baseTypeId = type.baseTypeId
        val baseType = baseTypeId?.let(immutableSchema.typesById::get)
        if (baseTypeId != null && baseType == null) {
            diagnostics += missingImmutableTargetDiagnostic(
                code = "jimmer.dto.annotation.base-type-missing",
                message = "DTO 注解冻结无法找到不可变基础类型 ${baseTypeId.value}",
                targetId = type.id.value,
                symbolId = baseTypeId,
                location = type.location,
            )
        }
        val baseAnnotations = baseType?.let { immutableType ->
            immutableType.annotations.map { annotation ->
                AnnotationCandidate(
                    annotation = CandidateAnnotation.Lsi(annotation),
                    origin = DtoAnnotationOrigin.IMMUTABLE,
                    sourceSymbolId = immutableType.id,
                    location = type.location,
                )
            }
        }.orEmpty()
        val dtoAnnotations = type.annotations.map { annotation ->
            AnnotationCandidate(
                annotation = CandidateAnnotation.Dto(annotation),
                origin = DtoAnnotationOrigin.DTO,
                sourceSymbolId = null,
                location = type.location,
            )
        }
        return AnnotationTargetCandidates(
            targetId = type.id.value,
            location = type.location,
            candidates = mergeCandidates(baseAnnotations, dtoAnnotations),
        )
    }

    private fun propertyCandidates(
        graph: DtoGraph,
        prop: DtoProp,
        diagnostics: MutableList<LsiDiagnostic>,
    ): AnnotationTargetCandidates {
        val baseProp = if (prop is DtoBaseProp) {
            val tailProp = graph.propsById.getValue(prop.tailPropId) as DtoBaseProp
            val basePropId = tailProp.baseProps.first().propId
            immutableSchema.propsById[basePropId].also { immutableProp ->
                if (immutableProp == null) {
                    diagnostics += missingImmutableTargetDiagnostic(
                        code = "jimmer.dto.annotation.base-prop-missing",
                        message = "DTO 注解冻结无法找到不可变基础属性 ${basePropId.value}",
                        targetId = prop.id.value,
                        symbolId = basePropId,
                        location = prop.aliasLocation,
                    )
                }
            }
        } else {
            null
        }
        val baseAnnotations = baseProp?.let { immutableProp ->
            immutableProp.annotations.map { annotation ->
                AnnotationCandidate(
                    annotation = CandidateAnnotation.Lsi(annotation),
                    origin = DtoAnnotationOrigin.IMMUTABLE,
                    sourceSymbolId = immutableProp.id,
                    location = prop.aliasLocation,
                )
            }
        }.orEmpty()
        val dtoAnnotations = prop.annotations.map { annotation ->
            AnnotationCandidate(
                annotation = CandidateAnnotation.Dto(annotation),
                origin = DtoAnnotationOrigin.DTO,
                sourceSymbolId = null,
                location = prop.aliasLocation,
            )
        }
        return AnnotationTargetCandidates(
            targetId = prop.id.value,
            location = prop.aliasLocation,
            candidates = mergeCandidates(baseAnnotations, dtoAnnotations),
        )
    }

    private fun builderSetterCandidates(
        graph: DtoGraph,
        prop: DtoProp,
        diagnostics: MutableList<LsiDiagnostic>,
    ): AnnotationTargetCandidates {
        val ownerType = graph.typesById.getValue(prop.ownerTypeId)
        if (prop.id !in ownerType.propIds || !ownerType.requiresInputBuilder(graph)) {
            return AnnotationTargetCandidates(prop.id.value, prop.aliasLocation, emptyList())
        }
        val dtoCandidates = prop.annotations.map { annotation ->
            AnnotationCandidate(
                annotation = CandidateAnnotation.Dto(annotation),
                origin = DtoAnnotationOrigin.DTO,
                sourceSymbolId = null,
                location = prop.aliasLocation,
            )
        }
        val baseProp = if (prop is DtoBaseProp) {
            val basePropId = prop.baseProps.first().propId
            immutableSchema.propsById[basePropId].also { immutableProp ->
                if (immutableProp == null) {
                    diagnostics += missingImmutableTargetDiagnostic(
                        code = "jimmer.dto.annotation.base-prop-missing",
                        message = "DTO builder 注解冻结无法找到不可变基础属性 ${basePropId.value}",
                        targetId = prop.id.value,
                        symbolId = basePropId,
                        location = prop.aliasLocation,
                    )
                }
            }
        } else {
            null
        }
        val baseCandidates = baseProp?.let { immutableProp ->
            immutableProp.annotations.map { annotation ->
                AnnotationCandidate(
                    annotation = CandidateAnnotation.Lsi(annotation),
                    origin = DtoAnnotationOrigin.IMMUTABLE,
                    sourceSymbolId = immutableProp.id,
                    location = prop.aliasLocation,
                )
            }
        }.orEmpty()
        return AnnotationTargetCandidates(
            targetId = prop.id.value,
            location = prop.aliasLocation,
            candidates = mergeBuilderSetterCandidates(dtoCandidates, baseCandidates),
        )
    }

    private fun mergeCandidates(
        baseCandidates: List<AnnotationCandidate>,
        dtoCandidates: List<AnnotationCandidate>,
    ): List<AnnotationCandidate> {
        val copyableDtoCandidates = dtoCandidates.filter(AnnotationCandidate::isCopyable)
        val overriddenTypeIds = copyableDtoCandidates.mapTo(hashSetOf()) { candidate ->
            candidate.annotation.typeId
        }
        return baseCandidates.filter(AnnotationCandidate::isCopyable).filter { candidate ->
            candidate.annotation.typeId !in overriddenTypeIds
        } + copyableDtoCandidates
    }

    private fun mergeBuilderSetterCandidates(
        dtoCandidates: List<AnnotationCandidate>,
        baseCandidates: List<AnnotationCandidate>,
    ): List<AnnotationCandidate> {
        val typeIds = hashSetOf<LsiSymbolId>()
        return buildList {
            (dtoCandidates + baseCandidates).forEach { candidate ->
                if (candidate.isJacksonAnnotation() && typeIds.add(candidate.annotation.typeId)) {
                    add(candidate)
                }
            }
        }
    }

    private fun freezeDeclaration(
        typeId: LsiSymbolId,
        candidates: List<AnnotationCandidate>,
        diagnostics: MutableList<LsiDiagnostic>,
    ): DtoAnnotationDeclaration? {
        val declaration = workspace[typeId]
        val location = candidates
            .asSequence()
            .filter { candidate -> typeId in candidate.annotation.annotationTypeIds() }
            .mapNotNull(AnnotationCandidate::location)
            .minWithOrNull(LSI_LOCATION_COMPARATOR)
        if (declaration == null) {
            diagnostics += declarationDiagnostic(
                code = "jimmer.dto.annotation.declaration-missing",
                message = "无法在 LSI workspace 中找到 DTO 注解声明 ${typeId.value}",
                typeId = typeId,
                location = location,
            )
            return null
        }
        if (declaration !is LsiTypeDeclaration || declaration.kind != LsiTypeDeclarationKind.ANNOTATION) {
            diagnostics += declarationDiagnostic(
                code = "jimmer.dto.annotation.declaration-kind",
                message = "DTO 注解类型 ${typeId.value} 的 LSI 声明不是 annotation",
                typeId = typeId,
                location = location,
            )
            return null
        }
        val kind = declaration.annotationDeclarationKind()
        val kotlinValueVararg = kind == DtoAnnotationDeclarationKind.KOTLIN &&
            declaration.annotationMembers.any { member -> member.name == "value" && member.vararg }
        val targetPolicy = declaration.dtoAnnotationTargetPolicy()
        return DtoAnnotationDeclaration(
            typeId = typeId,
            kind = kind,
            targetDeclared = targetPolicy.declared,
            allowedPlacements = targetPolicy.allowedPlacements,
            argumentTypes = declaration.annotationMembers.associate { member ->
                member.name to member.type.toDtoAnnotationMemberType()
            }.toSortedMap(),
            kotlinValueVararg = kotlinValueVararg,
            argumentNamesInDeclarationOrder = declaration.annotationMembers
                .sortedWith(
                    compareBy<LsiAnnotationMember>(
                        { member -> member.declarationIndex ?: Int.MAX_VALUE },
                        LsiAnnotationMember::name,
                    ),
                )
                .map(LsiAnnotationMember::name),
        )
    }

    private fun freezeApplications(
        target: AnnotationTargetCandidates,
        targetKind: AnnotationPlanTargetKind,
        supportedPlacements: Set<DtoAnnotationPlacement>,
        declarationsByTypeId: Map<LsiSymbolId, DtoAnnotationDeclaration>,
        diagnostics: MutableList<LsiDiagnostic>,
    ): List<DtoAnnotationApplication> {
        return target.candidates.mapNotNull { candidate ->
            val declaration = declarationsByTypeId[candidate.annotation.typeId] ?: return@mapNotNull null
            val supportedCandidatePlacements = when {
                declaration.targetDeclared -> declaration.allowedPlacements
                    .filter(supportedPlacements::contains)
                    .sorted()
                targetKind == AnnotationPlanTargetKind.TYPE -> listOf(DtoAnnotationPlacement.TYPE)
                else -> emptyList()
            }
            if (supportedCandidatePlacements.isEmpty()) {
                if (targetKind == AnnotationPlanTargetKind.TYPE) {
                    diagnostics += LsiDiagnostic(
                        code = "jimmer.dto.annotation.placement",
                        severity = LsiDiagnosticSeverity.ERROR,
                        message = "注解 ${candidate.annotation.typeId.value} 不能应用到 DTO 目标 ${target.targetId}",
                        symbolId = candidate.sourceSymbolId,
                        location = target.location,
                        details = sortedMapOf(
                            "annotationType" to candidate.annotation.typeId.value,
                            "targetId" to target.targetId,
                        ),
                    )
                }
                return@mapNotNull null
            }
            val annotation = freezeAnnotation(candidate, declarationsByTypeId, diagnostics)
                ?: return@mapNotNull null
            DtoAnnotationApplication(
                annotation = annotation,
                origin = candidate.origin,
                sourceSymbolId = candidate.sourceSymbolId,
                placements = supportedCandidatePlacements,
            )
        }
    }

    private fun freezeBuilderSetterApplications(
        target: AnnotationTargetCandidates,
        declarationsByTypeId: Map<LsiSymbolId, DtoAnnotationDeclaration>,
        diagnostics: MutableList<LsiDiagnostic>,
    ): List<DtoBuilderSetterAnnotationApplication> {
        return target.candidates.mapNotNull { candidate ->
            if (candidate.annotation.typeId !in declarationsByTypeId) {
                return@mapNotNull null
            }
            val annotation = freezeAnnotation(candidate, declarationsByTypeId, diagnostics)
                ?: return@mapNotNull null
            DtoBuilderSetterAnnotationApplication(
                annotation = annotation,
                origin = candidate.origin,
                sourceSymbolId = candidate.sourceSymbolId,
            )
        }
    }

    private fun freezeAnnotation(
        candidate: AnnotationCandidate,
        declarationsByTypeId: Map<LsiSymbolId, DtoAnnotationDeclaration>,
        diagnostics: MutableList<LsiDiagnostic>,
    ): LsiAnnotation? {
        return when (val annotation = candidate.annotation) {
            is CandidateAnnotation.Lsi -> freezeLsiAnnotation(
                annotation = annotation.value,
                candidate = candidate,
                declarationsByTypeId = declarationsByTypeId,
                diagnostics = diagnostics,
            )
            is CandidateAnnotation.Dto -> freezeDtoAnnotation(
                annotation = annotation.value,
                candidate = candidate,
                declarationsByTypeId = declarationsByTypeId,
                diagnostics = diagnostics,
            )
        }
    }

    private fun freezeLsiAnnotation(
        annotation: LsiAnnotation,
        candidate: AnnotationCandidate,
        declarationsByTypeId: Map<LsiSymbolId, DtoAnnotationDeclaration>,
        diagnostics: MutableList<LsiDiagnostic>,
    ): LsiAnnotation? {
        val declaration = declarationsByTypeId[annotation.type] ?: return null
        var valid = true
        val arguments = sortedMapOf<String, LsiAnnotationArgument>()
        annotation.arguments.toSortedMap().forEach { (name, argument) ->
            if (argument.origin != LsiAnnotationArgumentOrigin.EXPLICIT) {
                return@forEach
            }
            val expectedType = declaration.argumentTypes[name]
            if (expectedType == null) {
                diagnostics += unknownArgumentDiagnostic(annotation.type, name, candidate)
                valid = false
                return@forEach
            }
            val value = freezeLsiAnnotationValue(
                value = argument.value,
                expectedType = expectedType,
                annotationTypeId = annotation.type,
                argumentName = name,
                candidate = candidate,
                declarationsByTypeId = declarationsByTypeId,
                diagnostics = diagnostics,
            )
            if (value == null) {
                valid = false
            } else {
                arguments[name] = LsiAnnotationArgument(value, LsiAnnotationArgumentOrigin.EXPLICIT)
            }
        }
        return if (valid) {
            LsiAnnotation(
                type = annotation.type,
                arguments = arguments,
                explicitArgumentNamesInSourceOrder = annotation.explicitArgumentNamesInSourceOrder,
            )
        } else {
            null
        }
    }

    private fun freezeDtoAnnotation(
        annotation: DtoAnnotation,
        candidate: AnnotationCandidate,
        declarationsByTypeId: Map<LsiSymbolId, DtoAnnotationDeclaration>,
        diagnostics: MutableList<LsiDiagnostic>,
    ): LsiAnnotation? {
        val declaration = declarationsByTypeId[annotation.typeId] ?: return null
        var valid = true
        val arguments = sortedMapOf<String, LsiAnnotationArgument>()
        annotation.arguments.sortedBy(DtoAnnotationArgument::name).forEach { argument ->
            val expectedType = declaration.argumentTypes[argument.name]
            if (expectedType == null) {
                diagnostics += unknownArgumentDiagnostic(annotation.typeId, argument.name, candidate)
                valid = false
                return@forEach
            }
            val value = freezeDtoAnnotationValue(
                value = argument.value,
                expectedType = expectedType,
                annotationTypeId = annotation.typeId,
                argumentName = argument.name,
                candidate = candidate,
                declarationsByTypeId = declarationsByTypeId,
                diagnostics = diagnostics,
            )
            if (value == null) {
                valid = false
            } else {
                arguments[argument.name] = LsiAnnotationArgument(value, LsiAnnotationArgumentOrigin.EXPLICIT)
            }
        }
        return if (valid) {
            LsiAnnotation(
                type = annotation.typeId,
                arguments = arguments,
                explicitArgumentNamesInSourceOrder = annotation.arguments.map(DtoAnnotationArgument::name),
            )
        } else {
            null
        }
    }

    private fun freezeLsiAnnotationValue(
        value: LsiAnnotationValue,
        expectedType: LsiTypeRef,
        annotationTypeId: LsiSymbolId,
        argumentName: String,
        candidate: AnnotationCandidate,
        declarationsByTypeId: Map<LsiSymbolId, DtoAnnotationDeclaration>,
        diagnostics: MutableList<LsiDiagnostic>,
    ): LsiAnnotationValue? {
        val diagnosticCount = diagnostics.size
        val frozenValue = when {
            expectedType is LsiArrayType && value is LsiAnnotationValue.ArrayValue -> {
                val elements = value.elements.map { element ->
                    freezeLsiAnnotationValue(
                        value = element,
                        expectedType = expectedType.elementType,
                        annotationTypeId = annotationTypeId,
                        argumentName = argumentName,
                        candidate = candidate,
                        declarationsByTypeId = declarationsByTypeId,
                        diagnostics = diagnostics,
                    )
                }
                if (elements.any { element -> element == null }) null else {
                    LsiAnnotationValue.ArrayValue(elements.filterNotNull())
                }
            }
            value is LsiAnnotationValue.NestedAnnotationValue &&
                expectedType.acceptsNestedAnnotation(value.annotation.type, workspace) -> {
                freezeLsiAnnotation(
                    annotation = value.annotation,
                    candidate = candidate,
                    declarationsByTypeId = declarationsByTypeId,
                    diagnostics = diagnostics,
                )?.let(LsiAnnotationValue::NestedAnnotationValue)
            }
            value.matchesAnnotationMemberType(expectedType, workspace, annotationTypeSystem) -> value
            else -> null
        }
        if (frozenValue == null && diagnostics.size == diagnosticCount) {
            diagnostics += argumentTypeDiagnostic(
                annotationTypeId = annotationTypeId,
                argumentName = argumentName,
                expectedType = expectedType,
                actual = value.stableSignature(),
                candidate = candidate,
            )
        }
        return frozenValue
    }

    private fun freezeDtoAnnotationValue(
        value: DtoAnnotationValue,
        expectedType: LsiTypeRef,
        annotationTypeId: LsiSymbolId,
        argumentName: String,
        candidate: AnnotationCandidate,
        declarationsByTypeId: Map<LsiSymbolId, DtoAnnotationDeclaration>,
        diagnostics: MutableList<LsiDiagnostic>,
    ): LsiAnnotationValue? {
        if (expectedType is LsiArrayType) {
            val sourceElements = if (value is DtoAnnotationValue.ArrayValue) value.elements else listOf(value)
            val elements = sourceElements.map { element ->
                freezeDtoAnnotationValue(
                    value = element,
                    expectedType = expectedType.elementType,
                    annotationTypeId = annotationTypeId,
                    argumentName = argumentName,
                    candidate = candidate,
                    declarationsByTypeId = declarationsByTypeId,
                    diagnostics = diagnostics,
                )
            }
            return if (elements.any { element -> element == null }) null else {
                LsiAnnotationValue.ArrayValue(elements.filterNotNull())
            }
        }
        val diagnosticCount = diagnostics.size
        val frozenValue = when (value) {
            is DtoAnnotationValue.ArrayValue -> null
            is DtoAnnotationValue.AnnotationValue -> {
                if (!expectedType.acceptsNestedAnnotation(value.annotation.typeId, workspace)) {
                    null
                } else {
                    freezeDtoAnnotation(
                        annotation = value.annotation,
                        candidate = candidate,
                        declarationsByTypeId = declarationsByTypeId,
                        diagnostics = diagnostics,
                    )?.let(LsiAnnotationValue::NestedAnnotationValue)
                }
            }
            is DtoAnnotationValue.EnumValue -> {
                if (expectedType.acceptsEnumValue(value.enumTypeId, value.constant, workspace)) {
                    LsiAnnotationValue.EnumValue(value.enumTypeId, value.constant)
                } else {
                    null
                }
            }
            is DtoAnnotationValue.TypeValue -> {
                value.type.toLsiTypeOrNull()?.takeIf { payloadType ->
                    expectedType.acceptsClassLiteral(payloadType, annotationTypeSystem)
                }?.let(LsiAnnotationValue::ClassValue)
            }
            is DtoAnnotationValue.LiteralValue -> parseAnnotationLiteral(value.code, expectedType)
        }
        if (frozenValue == null && diagnostics.size == diagnosticCount) {
            diagnostics += argumentTypeDiagnostic(
                annotationTypeId = annotationTypeId,
                argumentName = argumentName,
                expectedType = expectedType,
                actual = value.stableDescription(),
                candidate = candidate,
            )
        }
        return frozenValue
    }

    private fun unknownArgumentDiagnostic(
        annotationTypeId: LsiSymbolId,
        argumentName: String,
        candidate: AnnotationCandidate,
    ): LsiDiagnostic {
        return LsiDiagnostic(
            code = "jimmer.dto.annotation.argument",
            severity = LsiDiagnosticSeverity.ERROR,
            message = "注解 ${annotationTypeId.value} 不存在参数 $argumentName",
            symbolId = candidate.sourceSymbolId,
            location = candidate.location,
            details = sortedMapOf(
                "annotationType" to annotationTypeId.value,
                "argument" to argumentName,
            ),
        )
    }

    private fun argumentTypeDiagnostic(
        annotationTypeId: LsiSymbolId,
        argumentName: String,
        expectedType: LsiTypeRef,
        actual: String,
        candidate: AnnotationCandidate,
    ): LsiDiagnostic {
        return LsiDiagnostic(
            code = "jimmer.dto.annotation.argument-type",
            severity = LsiDiagnosticSeverity.ERROR,
            message = "注解 ${annotationTypeId.value} 的参数 $argumentName 与声明类型不匹配",
            symbolId = candidate.sourceSymbolId,
            location = candidate.location,
            details = sortedMapOf(
                "annotationType" to annotationTypeId.value,
                "argument" to argumentName,
                "expectedType" to expectedType.stableSignature(),
                "actualValue" to actual,
            ),
        )
    }

    private fun missingImmutableTargetDiagnostic(
        code: String,
        message: String,
        targetId: String,
        symbolId: LsiSymbolId,
        location: LsiLocation,
    ): LsiDiagnostic {
        return LsiDiagnostic(
            code = code,
            severity = LsiDiagnosticSeverity.ERROR,
            message = message,
            symbolId = symbolId,
            location = location,
            details = sortedMapOf("targetId" to targetId),
        )
    }

    private fun declarationDiagnostic(
        code: String,
        message: String,
        typeId: LsiSymbolId,
        location: LsiLocation?,
    ): LsiDiagnostic {
        return LsiDiagnostic(
            code = code,
            severity = LsiDiagnosticSeverity.ERROR,
            message = message,
            symbolId = typeId,
            location = location,
            details = sortedMapOf("annotationType" to typeId.value),
        )
    }
}

fun DtoAnnotationContract.normalizedSnapshot(): String {
    return buildList {
        declarations.forEach { declaration ->
            add(
                canonicalValue(
                    "declaration",
                    declaration.typeId.value,
                    declaration.kind.name,
                    declaration.targetDeclared.toString(),
                    declaration.allowedPlacements.joinToString(",", transform = DtoAnnotationPlacement::name),
                    declaration.argumentTypes.entries.toList().canonicalList { (name, type) ->
                        canonicalValue(
                            name,
                            type.stableSignature(),
                        )
                    },
                    declaration.kotlinValueVararg.toString(),
                    declaration.argumentNamesInDeclarationOrder.joinToString(","),
                )
            )
        }
        typePlans.forEach { plan ->
            add(canonicalValue("type", plan.typeId.value))
            plan.applications.forEach { application ->
                add(canonicalValue("type-annotation", plan.typeId.value, application.canonicalText()))
            }
        }
        propPlans.forEach { plan ->
            add(canonicalValue("prop", plan.propId.value))
            plan.propertyApplications.forEach { application ->
                add(canonicalValue("property-annotation", plan.propId.value, application.canonicalText()))
            }
            plan.builderSetterApplications.forEach { application ->
                add(canonicalValue("builder-setter-annotation", plan.propId.value, application.canonicalText()))
            }
        }
        diagnostics.forEach { diagnostic ->
            add(canonicalValue("diagnostic", diagnostic.canonicalText()))
        }
    }.joinToString("\n", postfix = "\n")
}

fun DtoAnnotationContract.fingerprint(): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest(normalizedSnapshot().toByteArray(Charsets.UTF_8))
    return buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }
}

private data class AnnotationTargetCandidates(
    val targetId: String,
    val location: LsiLocation,
    val candidates: List<AnnotationCandidate>,
)

private data class PropAnnotationCandidates(
    val property: AnnotationTargetCandidates,
    val builderSetter: AnnotationTargetCandidates,
) {
    init {
        require(property.targetId == builderSetter.targetId) {
            "DTO property and builder annotation candidates must use the same target id"
        }
    }
}

private enum class AnnotationPlanTargetKind {
    TYPE,
    PROP,
}

private data class AnnotationCandidate(
    val annotation: CandidateAnnotation,
    val origin: DtoAnnotationOrigin,
    val sourceSymbolId: LsiSymbolId?,
    val location: LsiLocation?,
) {
    fun isCopyable(): Boolean {
        val qualifiedName = annotation.typeId.requireTypeQualifiedName()
        if (qualifiedName == KOTLIN_DTO_ANNOTATION) {
            return false
        }
        if (
            origin == DtoAnnotationOrigin.IMMUTABLE &&
            (qualifiedName == T_NULLABLE_ANNOTATION || qualifiedName.substringAfterLast('.') in NULLITY_SIMPLE_NAMES)
        ) {
            return false
        }
        if (qualifiedName == IMMUTABLE_ANNOTATION || qualifiedName.startsWith(JIMMER_SQL_PREFIX)) {
            return false
        }
        if (
            origin == DtoAnnotationOrigin.IMMUTABLE &&
            qualifiedName.startsWith(JIMMER_PREFIX) &&
            !qualifiedName.startsWith(JIMMER_CLIENT_PREFIX)
        ) {
            return false
        }
        return true
    }

    fun isJacksonAnnotation(): Boolean {
        val qualifiedName = annotation.typeId.requireTypeQualifiedName()
        return JACKSON_ANNOTATION_PREFIXES.any(qualifiedName::startsWith)
    }

}

private sealed interface CandidateAnnotation {
    val typeId: LsiSymbolId

    data class Lsi(
        val value: LsiAnnotation,
    ) : CandidateAnnotation {
        override val typeId: LsiSymbolId
            get() = value.type
    }

    data class Dto(
        val value: DtoAnnotation,
    ) : CandidateAnnotation {
        override val typeId: LsiSymbolId
            get() = value.typeId
    }
}

private fun LsiTypeDeclaration.annotationDeclarationKind(): DtoAnnotationDeclarationKind {
    if (
        annotations.any { annotation -> annotation.type == KOTLIN_METADATA } ||
            annotationMembers.any { member -> member.vararg } ||
            origin.language == LsiLanguage.KOTLIN
    ) {
        return DtoAnnotationDeclarationKind.KOTLIN
    }
    return DtoAnnotationDeclarationKind.JAVA
}

private fun LsiTypeDeclaration.dtoAnnotationTargetPolicy(): DtoAnnotationTargetPolicy {
    val policy = annotationTargetPolicy()
    return DtoAnnotationTargetPolicy(
        declared = policy.declared,
        allowedPlacements = policy.targets.mapNotNull(LSI_TARGET_PLACEMENTS::get).distinct().sorted(),
    )
}

private data class DtoAnnotationTargetPolicy(
    val declared: Boolean,
    val allowedPlacements: List<DtoAnnotationPlacement>,
)

private fun DtoTypeRef.toLsiTypeOrNull(): LsiTypeRef? {
    val primitiveKind = DTO_PRIMITIVE_KINDS[typeName]
    if (primitiveKind != null) {
        if (arguments.isNotEmpty()) {
            return null
        }
        return LsiPrimitiveType(
            kind = primitiveKind,
            nullability = LsiNullability.NON_NULL,
            boxed = nullable,
        )
    }
    if (typeName in DTO_ARRAY_TYPE_NAMES) {
        if (arguments.size != 1 || arguments.single().type == null) {
            return null
        }
        return LsiArrayType(
            elementType = requireNotNull(arguments.single().type).toLsiTypeOrNull() ?: return null,
        )
    }
    val canonicalTypeName = DTO_STANDARD_DECLARED_TYPES[typeName] ?: typeName
    val frozenArguments = buildList {
        arguments.forEach { argument ->
            add(argument.toLsiTypeArgumentOrNull() ?: return null)
        }
    }
    return LsiDeclaredType(
        declarationId = LsiSymbolId.type(canonicalTypeName),
        arguments = frozenArguments,
    )
}

private fun DtoTypeArgument.toLsiTypeArgumentOrNull(): LsiTypeArgument? {
    return when (variance) {
        DtoVariance.INVARIANT -> LsiTypeArgument.invariant(requireNotNull(type).toLsiTypeOrNull() ?: return null)
        DtoVariance.IN -> LsiTypeArgument.input(requireNotNull(type).toLsiTypeOrNull() ?: return null)
        DtoVariance.OUT -> LsiTypeArgument.output(requireNotNull(type).toLsiTypeOrNull() ?: return null)
        DtoVariance.STAR -> LsiTypeArgument.STAR
    }
}

private fun LsiTypeRef.toDtoAnnotationMemberType(): LsiTypeRef {
    return when (this) {
        is LsiDeclaredType -> {
            val canonicalArguments = arguments.map { argument ->
                argument.copy(type = argument.type?.toDtoAnnotationMemberType())
            }.let { frozenArguments ->
                if (declarationId == KOTLIN_KCLASS_TYPE_ID) {
                    frozenArguments.map { argument ->
                        if (argument.variance == LsiVariance.INVARIANT) {
                            argument.copy(variance = LsiVariance.OUT)
                        } else {
                            argument
                        }
                    }
                } else {
                    frozenArguments
                }
            }
            copy(
                declarationId = DTO_ANNOTATION_DECLARED_TYPE_ALIASES[declarationId] ?: declarationId,
                arguments = canonicalArguments,
            )
        }
        is LsiTypeParameterRef -> this
        is LsiPrimitiveType -> this
        is LsiArrayType -> copy(elementType = elementType.toDtoAnnotationMemberType())
        is LsiFunctionType -> copy(
            returnType = returnType.toDtoAnnotationMemberType(),
            receiverType = receiverType?.toDtoAnnotationMemberType(),
            parameterTypes = parameterTypes.map(LsiTypeRef::toDtoAnnotationMemberType),
        )
        is LsiUnresolvedType -> this
    }
}

private fun LsiAnnotationValue.matchesAnnotationMemberType(
    expectedType: LsiTypeRef,
    workspace: LsiWorkspace,
    typeSystem: LsiTypeSystem,
): Boolean {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> expectedType.hasPrimitiveKind(LsiPrimitiveKind.BOOLEAN)
        is LsiAnnotationValue.ByteValue -> expectedType.hasPrimitiveKind(LsiPrimitiveKind.BYTE)
        is LsiAnnotationValue.ShortValue -> expectedType.hasPrimitiveKind(LsiPrimitiveKind.SHORT)
        is LsiAnnotationValue.IntValue -> expectedType.hasPrimitiveKind(LsiPrimitiveKind.INT)
        is LsiAnnotationValue.LongValue -> expectedType.hasPrimitiveKind(LsiPrimitiveKind.LONG)
        is LsiAnnotationValue.FloatValue -> expectedType.hasPrimitiveKind(LsiPrimitiveKind.FLOAT)
        is LsiAnnotationValue.DoubleValue -> expectedType.hasPrimitiveKind(LsiPrimitiveKind.DOUBLE)
        is LsiAnnotationValue.CharValue -> expectedType.hasPrimitiveKind(LsiPrimitiveKind.CHAR)
        is LsiAnnotationValue.StringValue -> expectedType.isStringType()
        is LsiAnnotationValue.EnumValue -> expectedType.acceptsEnumValue(enumType, entryName, workspace)
        is LsiAnnotationValue.ClassValue -> expectedType.acceptsClassLiteral(type, typeSystem)
        is LsiAnnotationValue.NestedAnnotationValue -> {
            expectedType.acceptsNestedAnnotation(annotation.type, workspace)
        }
        is LsiAnnotationValue.ArrayValue -> false
    }
}

private fun LsiTypeRef.hasPrimitiveKind(kind: LsiPrimitiveKind): Boolean {
    return this is LsiPrimitiveType && this.kind == kind
}

private fun LsiTypeRef.isStringType(): Boolean {
    return this is LsiDeclaredType && declarationId in STRING_TYPE_IDS
}

private fun LsiTypeRef.acceptsClassLiteral(
    payloadType: LsiTypeRef,
    typeSystem: LsiTypeSystem,
): Boolean {
    val classType = this as? LsiDeclaredType ?: return false
    if (classType.declarationId !in CLASS_LITERAL_TYPE_IDS) {
        return false
    }
    if (classType.arguments.isEmpty()) {
        return true
    }
    if (classType.arguments.size != 1) {
        return false
    }
    val constraint = classType.arguments.single()
    if (constraint.variance == LsiVariance.STAR) {
        return true
    }
    val bound = constraint.type ?: return false
    return when (constraint.variance) {
        LsiVariance.IN -> isAnnotationTypeAssignable(bound, payloadType, typeSystem)
        LsiVariance.INVARIANT ->
            isAnnotationTypeAssignable(payloadType, bound, typeSystem) &&
                isAnnotationTypeAssignable(bound, payloadType, typeSystem)
        LsiVariance.OUT -> isAnnotationTypeAssignable(payloadType, bound, typeSystem)
        LsiVariance.STAR -> true
    }
}

private fun LsiTypeRef.toClassLiteralReferenceType(): LsiTypeRef {
    return if (this is LsiPrimitiveType) {
        LsiDeclaredType(PRIMITIVE_WRAPPER_TYPE_IDS.getValue(kind))
    } else {
        this
    }
}

private fun isAnnotationTypeAssignable(
    source: LsiTypeRef,
    target: LsiTypeRef,
    typeSystem: LsiTypeSystem,
): Boolean {
    val sourceType = source.toClassLiteralReferenceType()
    val targetType = target.toClassLiteralReferenceType()
    if (targetType is LsiDeclaredType && targetType.declarationId == JAVA_OBJECT_TYPE_ID) {
        return true
    }
    return typeSystem.isAssignable(sourceType, targetType)
}

private fun LsiTypeRef.acceptsEnumValue(
    enumTypeId: LsiSymbolId,
    entryName: String,
    workspace: LsiWorkspace,
): Boolean {
    if (this !is LsiDeclaredType || declarationId != enumTypeId) {
        return false
    }
    val declaration = workspace[enumTypeId] as? LsiTypeDeclaration ?: return false
    return declaration.kind == LsiTypeDeclarationKind.ENUM &&
        declaration.enumEntries.any { entry -> entry.name == entryName }
}

private fun LsiTypeRef.acceptsNestedAnnotation(
    annotationTypeId: LsiSymbolId,
    workspace: LsiWorkspace,
): Boolean {
    if (this !is LsiDeclaredType || declarationId != annotationTypeId) {
        return false
    }
    val declaration = workspace[annotationTypeId] as? LsiTypeDeclaration ?: return false
    return declaration.kind == LsiTypeDeclarationKind.ANNOTATION
}

private fun parseAnnotationLiteral(
    code: String,
    expectedType: LsiTypeRef,
): LsiAnnotationValue? {
    if (expectedType is LsiPrimitiveType) {
        return when (expectedType.kind) {
            LsiPrimitiveKind.BOOLEAN -> code.toBooleanStrictOrNull()?.let(LsiAnnotationValue::BooleanValue)
            LsiPrimitiveKind.BYTE -> code.toLongOrNull()
                ?.takeIf { value -> value in Byte.MIN_VALUE..Byte.MAX_VALUE }
                ?.toByte()
                ?.let(LsiAnnotationValue::ByteValue)
            LsiPrimitiveKind.SHORT -> code.toLongOrNull()
                ?.takeIf { value -> value in Short.MIN_VALUE..Short.MAX_VALUE }
                ?.toShort()
                ?.let(LsiAnnotationValue::ShortValue)
            LsiPrimitiveKind.INT -> code.toLongOrNull()
                ?.takeIf { value -> value in Int.MIN_VALUE..Int.MAX_VALUE }
                ?.toInt()
                ?.let(LsiAnnotationValue::IntValue)
            LsiPrimitiveKind.LONG -> code.toLongOrNull()?.let(LsiAnnotationValue::LongValue)
            LsiPrimitiveKind.CHAR -> code.decodeQuotedLiteral('\'')
                ?.singleOrNull()
                ?.let(LsiAnnotationValue::CharValue)
            LsiPrimitiveKind.FLOAT -> code.toFloatOrNull()
                ?.takeIf(Float::isFinite)
                ?.let(LsiAnnotationValue::FloatValue)
            LsiPrimitiveKind.DOUBLE -> code.toDoubleOrNull()
                ?.takeIf(Double::isFinite)
                ?.let(LsiAnnotationValue::DoubleValue)
            LsiPrimitiveKind.UNIT,
            LsiPrimitiveKind.VOID,
            -> null
        }
    }
    if (expectedType.isStringType()) {
        return code.decodeQuotedLiteral('"')?.let(LsiAnnotationValue::StringValue)
    }
    return null
}

private fun String.decodeQuotedLiteral(quote: Char): String? {
    if (length < 2 || first() != quote || last() != quote) {
        return null
    }
    val result = StringBuilder(length - 2)
    var index = 1
    while (index < lastIndex) {
        val character = this[index++]
        if (character != '\\') {
            result.append(character)
            continue
        }
        if (index >= lastIndex) {
            return null
        }
        when (val escaped = this[index++]) {
            'b' -> result.append('\b')
            't' -> result.append('\t')
            'n' -> result.append('\n')
            'f' -> result.append('\u000c')
            'r' -> result.append('\r')
            '"' -> result.append('"')
            '\'' -> result.append('\'')
            '\\' -> result.append('\\')
            'u' -> {
                while (index < lastIndex && this[index] == 'u') {
                    index++
                }
                if (index + 4 > lastIndex) {
                    return null
                }
                val unicode = substring(index, index + 4).toIntOrNull(16) ?: return null
                result.append(unicode.toChar())
                index += 4
            }
            else -> return null
        }
    }
    return result.toString()
}

private fun DtoAnnotationValue.stableDescription(): String {
    return when (this) {
        is DtoAnnotationValue.ArrayValue -> "array[${elements.joinToString(",") { it.stableDescription() }}]"
        is DtoAnnotationValue.AnnotationValue -> "annotation:${annotation.typeId.value}"
        is DtoAnnotationValue.EnumValue -> "enum:${enumTypeId.value}.$constant"
        is DtoAnnotationValue.TypeValue -> "class:${type.stableDescription()}"
        is DtoAnnotationValue.LiteralValue -> "literal:$code"
    }
}

private fun DtoTypeRef.stableDescription(): String {
    val argumentText = arguments.joinToString(",", prefix = "<", postfix = ">") { argument ->
        when (argument.variance) {
            DtoVariance.STAR -> "*"
            else -> "${argument.variance.name.lowercase()}:${requireNotNull(argument.type).stableDescription()}"
        }
    }.takeUnless { arguments.isEmpty() }.orEmpty()
    return typeName + argumentText + if (nullable) "?" else ""
}

private fun CandidateAnnotation.collectAnnotationTypeIds(
    destination: MutableSet<LsiSymbolId>,
) {
    when (this) {
        is CandidateAnnotation.Lsi -> value.collectAnnotationTypeIds(destination)
        is CandidateAnnotation.Dto -> value.collectAnnotationTypeIds(destination)
    }
}

private fun LsiAnnotation.collectAnnotationTypeIds(
    destination: MutableSet<LsiSymbolId>,
) {
    destination.add(type)
    arguments.values.forEach { argument ->
        argument.value.collectAnnotationTypeIds(destination)
    }
}

private fun DtoAnnotation.collectAnnotationTypeIds(
    destination: MutableSet<LsiSymbolId>,
) {
    destination.add(typeId)
    arguments.forEach { argument -> argument.value.collectAnnotationTypeIds(destination) }
}

private fun LsiAnnotationValue.collectAnnotationTypeIds(
    destination: MutableSet<LsiSymbolId>,
) {
    when (this) {
        is LsiAnnotationValue.NestedAnnotationValue -> annotation.collectAnnotationTypeIds(destination)
        is LsiAnnotationValue.ArrayValue -> elements.forEach { element ->
            element.collectAnnotationTypeIds(destination)
        }
        else -> Unit
    }
}

private fun DtoAnnotationValue.collectAnnotationTypeIds(
    destination: MutableSet<LsiSymbolId>,
) {
    when (this) {
        is DtoAnnotationValue.AnnotationValue -> annotation.collectAnnotationTypeIds(destination)
        is DtoAnnotationValue.ArrayValue -> elements.forEach { element ->
            element.collectAnnotationTypeIds(destination)
        }
        else -> Unit
    }
}

private fun CandidateAnnotation.annotationTypeIds(): Set<LsiSymbolId> = buildSet {
    collectAnnotationTypeIds(this)
}

private fun DtoAnnotationApplication.canonicalText(): String = canonicalValue(
    annotation.stableSignature(),
    origin.name,
    sourceSymbolId?.value.orEmpty(),
    placements.joinToString(",", transform = DtoAnnotationPlacement::name),
)

private fun DtoBuilderSetterAnnotationApplication.canonicalText(): String = canonicalValue(
    annotation.stableSignature(),
    origin.name,
    sourceSymbolId?.value.orEmpty(),
)

private fun LsiDiagnostic.stableOrderKey(): String = listOf(
    code,
    severity.name,
    symbolId?.value.orEmpty(),
    location?.source?.path.orEmpty(),
    location?.start?.line?.toString().orEmpty(),
    location?.start?.column?.toString().orEmpty(),
    message,
    details.toSortedMap().entries.joinToString(",") { (name, value) -> canonicalValue(name, value) },
).joinToString("\u0000")

private fun LsiDiagnostic.canonicalText(): String = canonicalValue(
    code,
    severity.name,
    symbolId?.value.orEmpty(),
    location?.source?.path.orEmpty(),
    location?.start?.line?.toString().orEmpty(),
    location?.start?.column?.toString().orEmpty(),
    message,
    details.toSortedMap().entries.joinToString(",") { (name, value) -> canonicalValue(name, value) },
)

private fun <T> List<T>.canonicalList(transform: (T) -> String): String {
    return canonicalValue(*map(transform).toTypedArray())
}

private fun canonicalValue(vararg fields: String): String {
    return fields.joinToString(separator = "|") { field -> "${field.length}:$field" }
}

private val TYPE_APPLICATION_PLACEMENTS = setOf(DtoAnnotationPlacement.TYPE)

private val PROP_APPLICATION_PLACEMENTS = setOf(
    DtoAnnotationPlacement.FIELD,
    DtoAnnotationPlacement.GETTER,
    DtoAnnotationPlacement.SETTER,
    DtoAnnotationPlacement.PROPERTY,
)

private val LSI_TARGET_PLACEMENTS = mapOf(
    LsiAnnotationTarget.TYPE to DtoAnnotationPlacement.TYPE,
    LsiAnnotationTarget.ANNOTATION_TYPE to DtoAnnotationPlacement.ANNOTATION_TYPE,
    LsiAnnotationTarget.CONSTRUCTOR to DtoAnnotationPlacement.CONSTRUCTOR,
    LsiAnnotationTarget.FIELD to DtoAnnotationPlacement.FIELD,
    LsiAnnotationTarget.METHOD to DtoAnnotationPlacement.GETTER,
    LsiAnnotationTarget.PARAMETER to DtoAnnotationPlacement.PARAMETER,
    LsiAnnotationTarget.TYPE_USE to DtoAnnotationPlacement.TYPE_USE,
    LsiAnnotationTarget.TYPE_PARAMETER to DtoAnnotationPlacement.TYPE_PARAMETER,
    LsiAnnotationTarget.LOCAL_VARIABLE to DtoAnnotationPlacement.LOCAL_VARIABLE,
    LsiAnnotationTarget.PROPERTY to DtoAnnotationPlacement.PROPERTY,
    LsiAnnotationTarget.GETTER to DtoAnnotationPlacement.GETTER,
    LsiAnnotationTarget.SETTER to DtoAnnotationPlacement.SETTER,
    LsiAnnotationTarget.EXPRESSION to DtoAnnotationPlacement.EXPRESSION,
    LsiAnnotationTarget.FILE to DtoAnnotationPlacement.FILE,
    LsiAnnotationTarget.TYPE_ALIAS to DtoAnnotationPlacement.TYPE_ALIAS,
)

private val DTO_PRIMITIVE_KINDS = mapOf(
    "Boolean" to LsiPrimitiveKind.BOOLEAN,
    "Byte" to LsiPrimitiveKind.BYTE,
    "Short" to LsiPrimitiveKind.SHORT,
    "Int" to LsiPrimitiveKind.INT,
    "Long" to LsiPrimitiveKind.LONG,
    "Char" to LsiPrimitiveKind.CHAR,
    "Float" to LsiPrimitiveKind.FLOAT,
    "Double" to LsiPrimitiveKind.DOUBLE,
    "boolean" to LsiPrimitiveKind.BOOLEAN,
    "byte" to LsiPrimitiveKind.BYTE,
    "short" to LsiPrimitiveKind.SHORT,
    "int" to LsiPrimitiveKind.INT,
    "long" to LsiPrimitiveKind.LONG,
    "char" to LsiPrimitiveKind.CHAR,
    "float" to LsiPrimitiveKind.FLOAT,
    "double" to LsiPrimitiveKind.DOUBLE,
    "void" to LsiPrimitiveKind.VOID,
    "kotlin.Boolean" to LsiPrimitiveKind.BOOLEAN,
    "kotlin.Byte" to LsiPrimitiveKind.BYTE,
    "kotlin.Short" to LsiPrimitiveKind.SHORT,
    "kotlin.Int" to LsiPrimitiveKind.INT,
    "kotlin.Long" to LsiPrimitiveKind.LONG,
    "kotlin.Char" to LsiPrimitiveKind.CHAR,
    "kotlin.Float" to LsiPrimitiveKind.FLOAT,
    "kotlin.Double" to LsiPrimitiveKind.DOUBLE,
    "kotlin.Unit" to LsiPrimitiveKind.UNIT,
)

private val DTO_ARRAY_TYPE_NAMES = setOf("Array", "kotlin.Array")

private val STRING_TYPE_IDS = setOf(
    LsiSymbolId.type("java.lang.String"),
    LsiSymbolId.type("kotlin.String"),
)

private val CLASS_LITERAL_TYPE_IDS = setOf(
    LsiSymbolId.type("java.lang.Class"),
    LsiSymbolId.type("kotlin.reflect.KClass"),
)

private val KOTLIN_KCLASS_TYPE_ID = LsiSymbolId.type("kotlin.reflect.KClass")

private val DTO_STANDARD_DECLARED_TYPES = mapOf(
    "Any" to "java.lang.Object",
    "String" to "java.lang.String",
    "Iterable" to "java.lang.Iterable",
    "MutableIterable" to "java.lang.Iterable",
    "Collection" to "java.util.Collection",
    "MutableCollection" to "java.util.Collection",
    "List" to "java.util.List",
    "MutableList" to "java.util.List",
    "Set" to "java.util.Set",
    "MutableSet" to "java.util.Set",
    "Map" to "java.util.Map",
    "MutableMap" to "java.util.Map",
    "java.lang.Object" to "java.lang.Object",
    "java.lang.String" to "java.lang.String",
    "kotlin.Any" to "java.lang.Object",
    "kotlin.String" to "java.lang.String",
    "kotlin.collections.Iterable" to "java.lang.Iterable",
    "kotlin.collections.MutableIterable" to "java.lang.Iterable",
    "kotlin.collections.Collection" to "java.util.Collection",
    "kotlin.collections.MutableCollection" to "java.util.Collection",
    "kotlin.collections.List" to "java.util.List",
    "kotlin.collections.MutableList" to "java.util.List",
    "kotlin.collections.Set" to "java.util.Set",
    "kotlin.collections.MutableSet" to "java.util.Set",
    "kotlin.collections.Map" to "java.util.Map",
    "kotlin.collections.MutableMap" to "java.util.Map",
)

private val DTO_ANNOTATION_DECLARED_TYPE_ALIASES = buildMap {
    DTO_STANDARD_DECLARED_TYPES.forEach { (source, target) ->
        if ('.' in source) {
            put(LsiSymbolId.type(source), LsiSymbolId.type(target))
        }
    }
    put(KOTLIN_KCLASS_TYPE_ID, LsiSymbolId.type("java.lang.Class"))
}

private val JAVA_OBJECT_TYPE_ID = LsiSymbolId.type("java.lang.Object")
private val JAVA_NUMBER_TYPE_ID = LsiSymbolId.type("java.lang.Number")
private val JAVA_SERIALIZABLE_TYPE_ID = LsiSymbolId.type("java.io.Serializable")
private val JAVA_COMPARABLE_TYPE_ID = LsiSymbolId.type("java.lang.Comparable")
private val JAVA_COMPARABLE_PARAMETER_ID = LsiSymbolId.typeParameter(JAVA_COMPARABLE_TYPE_ID, "T")

private val NUMERIC_PRIMITIVE_KINDS = setOf(
    LsiPrimitiveKind.BYTE,
    LsiPrimitiveKind.SHORT,
    LsiPrimitiveKind.INT,
    LsiPrimitiveKind.LONG,
    LsiPrimitiveKind.FLOAT,
    LsiPrimitiveKind.DOUBLE,
)

private val COMPARABLE_PRIMITIVE_KINDS = NUMERIC_PRIMITIVE_KINDS + setOf(
    LsiPrimitiveKind.BOOLEAN,
    LsiPrimitiveKind.CHAR,
)

private val SERIALIZABLE_PRIMITIVE_KINDS = COMPARABLE_PRIMITIVE_KINDS + LsiPrimitiveKind.UNIT

private val PRIMITIVE_WRAPPER_TYPE_IDS = mapOf(
    LsiPrimitiveKind.BOOLEAN to LsiSymbolId.type("java.lang.Boolean"),
    LsiPrimitiveKind.BYTE to LsiSymbolId.type("java.lang.Byte"),
    LsiPrimitiveKind.SHORT to LsiSymbolId.type("java.lang.Short"),
    LsiPrimitiveKind.INT to LsiSymbolId.type("java.lang.Integer"),
    LsiPrimitiveKind.LONG to LsiSymbolId.type("java.lang.Long"),
    LsiPrimitiveKind.CHAR to LsiSymbolId.type("java.lang.Character"),
    LsiPrimitiveKind.FLOAT to LsiSymbolId.type("java.lang.Float"),
    LsiPrimitiveKind.DOUBLE to LsiSymbolId.type("java.lang.Double"),
    LsiPrimitiveKind.UNIT to LsiSymbolId.type("kotlin.Unit"),
    LsiPrimitiveKind.VOID to LsiSymbolId.type("java.lang.Void"),
)

private val JVM_PRIMITIVE_WRAPPER_TYPE_HIERARCHY = buildList {
    add(
        LsiTypeHierarchyEntry(
            id = JAVA_OBJECT_TYPE_ID,
            qualifiedName = JAVA_OBJECT_TYPE_ID.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.CLASS,
        )
    )
    add(
        LsiTypeHierarchyEntry(
            id = JAVA_SERIALIZABLE_TYPE_ID,
            qualifiedName = JAVA_SERIALIZABLE_TYPE_ID.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.INTERFACE,
        )
    )
    add(
        LsiTypeHierarchyEntry(
            id = JAVA_COMPARABLE_TYPE_ID,
            qualifiedName = JAVA_COMPARABLE_TYPE_ID.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.INTERFACE,
            typeParameters = listOf(
                LsiTypeParameter(JAVA_COMPARABLE_PARAMETER_ID, "T"),
            ),
        )
    )
    add(
        LsiTypeHierarchyEntry(
            id = JAVA_NUMBER_TYPE_ID,
            qualifiedName = JAVA_NUMBER_TYPE_ID.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.CLASS,
            directSuperTypes = listOf(
                LsiDeclaredType(JAVA_OBJECT_TYPE_ID),
                LsiDeclaredType(JAVA_SERIALIZABLE_TYPE_ID),
            ),
        )
    )
    PRIMITIVE_WRAPPER_TYPE_IDS.forEach { (kind, wrapperTypeId) ->
        val directSuperTypes = buildList {
            add(LsiDeclaredType(JAVA_OBJECT_TYPE_ID))
            if (kind in NUMERIC_PRIMITIVE_KINDS) {
                add(LsiDeclaredType(JAVA_NUMBER_TYPE_ID))
            }
            if (kind in SERIALIZABLE_PRIMITIVE_KINDS) {
                add(LsiDeclaredType(JAVA_SERIALIZABLE_TYPE_ID))
            }
            if (kind in COMPARABLE_PRIMITIVE_KINDS) {
                add(
                    LsiDeclaredType(
                        declarationId = JAVA_COMPARABLE_TYPE_ID,
                        arguments = listOf(
                            LsiTypeArgument.invariant(LsiDeclaredType(wrapperTypeId)),
                        ),
                    )
                )
            }
        }
        add(
            LsiTypeHierarchyEntry(
                id = wrapperTypeId,
                qualifiedName = wrapperTypeId.requireTypeQualifiedName(),
                kind = if (kind == LsiPrimitiveKind.UNIT) {
                    LsiTypeDeclarationKind.OBJECT
                } else {
                    LsiTypeDeclarationKind.CLASS
                },
                directSuperTypes = directSuperTypes,
            )
        )
    }
}

private val LSI_LOCATION_COMPARATOR = compareBy<LsiLocation>(
    { location -> location.source },
    { location -> location.start },
    { location -> location.end },
)

private val KOTLIN_METADATA = LsiSymbolId.type("kotlin.Metadata")

private const val JIMMER_PREFIX = "org.babyfish.jimmer."
private const val JIMMER_SQL_PREFIX = "org.babyfish.jimmer.sql."
private const val JIMMER_CLIENT_PREFIX = "org.babyfish.jimmer.client."
private const val IMMUTABLE_ANNOTATION = "org.babyfish.jimmer.Immutable"
private const val KOTLIN_DTO_ANNOTATION = "org.babyfish.jimmer.kt.dto.KotlinDto"
private const val T_NULLABLE_ANNOTATION = "org.babyfish.jimmer.client.TNullable"

private val NULLITY_SIMPLE_NAMES = setOf("Null", "Nullable", "NotNull", "NonNull")

private val JACKSON_ANNOTATION_PREFIXES = listOf(
    "tools.jackson.databind.annotation.",
    "com.fasterxml.jackson.databind.annotation.",
    "com.fasterxml.jackson.annotation.",
)

private const val HEX_DIGITS = "0123456789abcdef"
