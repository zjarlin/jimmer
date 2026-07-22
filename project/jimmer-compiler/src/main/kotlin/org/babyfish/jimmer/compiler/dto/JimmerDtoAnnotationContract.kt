package org.babyfish.jimmer.compiler.dto

import java.security.MessageDigest
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.annotationTargetPolicy
import site.addzero.lsi.model.stableSignature

internal data class JimmerDtoAnnotationContract(
    val declarations: List<JimmerDtoAnnotationDeclaration>,
    val typePlans: List<JimmerDtoTypeAnnotationPlan>,
    val propPlans: List<JimmerDtoPropAnnotationPlan>,
    val diagnostics: List<LsiDiagnostic>,
) {
    val declarationsByTypeId: Map<LsiSymbolId, JimmerDtoAnnotationDeclaration> =
        declarations.associateBy(JimmerDtoAnnotationDeclaration::typeId)

    val typePlansByTypeId: Map<JimmerDtoTypeId, JimmerDtoTypeAnnotationPlan> =
        typePlans.associateBy(JimmerDtoTypeAnnotationPlan::typeId)

    val propPlansByPropId: Map<JimmerDtoPropId, JimmerDtoPropAnnotationPlan> =
        propPlans.associateBy(JimmerDtoPropAnnotationPlan::propId)

    init {
        require(declarations == declarations.sortedBy(JimmerDtoAnnotationDeclaration::typeId)) {
            "DTO annotation declarations must use stable type id order"
        }
        require(declarationsByTypeId.size == declarations.size) {
            "DTO annotation contract cannot contain duplicate declarations"
        }
        require(typePlans == typePlans.sortedBy(JimmerDtoTypeAnnotationPlan::typeId)) {
            "DTO type annotation plans must use stable type id order"
        }
        require(typePlansByTypeId.size == typePlans.size) {
            "DTO annotation contract cannot contain duplicate type plans"
        }
        require(propPlans == propPlans.sortedBy(JimmerDtoPropAnnotationPlan::propId)) {
            "DTO property annotation plans must use stable property id order"
        }
        require(propPlansByPropId.size == propPlans.size) {
            "DTO annotation contract cannot contain duplicate property plans"
        }
        require(diagnostics == diagnostics.sortedBy(LsiDiagnostic::stableOrderKey)) {
            "DTO annotation diagnostics must use stable order"
        }
        val appliedAnnotations = buildList {
            typePlans.flatMapTo(this) { plan -> plan.applications.map(JimmerDtoAnnotationApplication::annotation) }
            propPlans.flatMapTo(this) { plan ->
                plan.propertyApplications.map(JimmerDtoAnnotationApplication::annotation)
            }
            propPlans.flatMapTo(this) { plan ->
                plan.builderSetterApplications.map(JimmerDtoBuilderSetterAnnotationApplication::annotation)
            }
        }
        appliedAnnotations.forEach { annotation ->
            require(annotation.typeId in declarationsByTypeId) {
                "DTO annotation application must reference a frozen declaration: " +
                    annotation.typeId.value
            }
        }
    }
}

internal data class JimmerDtoAnnotationDeclaration(
    val typeId: LsiSymbolId,
    val kind: JimmerDtoAnnotationDeclarationKind,
    val targetDeclared: Boolean,
    val allowedPlacements: List<JimmerDtoAnnotationPlacement>,
    val argumentNames: List<String>,
    val kotlinValueVararg: Boolean,
) {
    init {
        typeId.requireTypeQualifiedName()
        require(allowedPlacements == allowedPlacements.distinct().sorted()) {
            "DTO annotation declaration placements must be distinct and sorted: ${typeId.value}"
        }
        require(targetDeclared || allowedPlacements.isEmpty()) {
            "DTO annotation declaration without target cannot expose declared placements: ${typeId.value}"
        }
        require(argumentNames == argumentNames.distinct().sorted()) {
            "DTO annotation declaration argument names must be distinct and sorted: ${typeId.value}"
        }
        require(!kotlinValueVararg || kind == JimmerDtoAnnotationDeclarationKind.KOTLIN) {
            "Only Kotlin annotation declarations can expose a value vararg: ${typeId.value}"
        }
        require(!kotlinValueVararg || "value" in argumentNames) {
            "Kotlin annotation value vararg requires a value argument: ${typeId.value}"
        }
    }
}

internal enum class JimmerDtoAnnotationDeclarationKind {
    JAVA,
    KOTLIN,
}

internal enum class JimmerDtoAnnotationPlacement {
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

internal data class JimmerDtoTypeAnnotationPlan(
    val typeId: JimmerDtoTypeId,
    val applications: List<JimmerDtoAnnotationApplication>,
)

internal data class JimmerDtoPropAnnotationPlan(
    val propId: JimmerDtoPropId,
    val propertyApplications: List<JimmerDtoAnnotationApplication>,
    val builderSetterApplications: List<JimmerDtoBuilderSetterAnnotationApplication>,
) {
    init {
        require(
            builderSetterApplications.map { application -> application.annotation.typeId }.distinct().size ==
                builderSetterApplications.size
        ) {
            "DTO builder setter annotations must be unique by exact type id: ${propId.value}"
        }
    }
}

internal data class JimmerDtoBuilderSetterAnnotationApplication(
    val annotation: JimmerDtoAppliedAnnotation,
    val origin: JimmerDtoAnnotationOrigin,
    val sourceSymbolId: LsiSymbolId?,
) {
    init {
        sourceSymbolId?.let { symbolId ->
            require(symbolId.value.isNotBlank()) { "DTO annotation source symbol id cannot be blank" }
        }
        require(origin == JimmerDtoAnnotationOrigin.IMMUTABLE || sourceSymbolId == null) {
            "DTO-authored builder setter annotation cannot reference an immutable source symbol"
        }
    }
}

internal data class JimmerDtoAnnotationApplication(
    val annotation: JimmerDtoAppliedAnnotation,
    val origin: JimmerDtoAnnotationOrigin,
    val sourceSymbolId: LsiSymbolId?,
    val placements: List<JimmerDtoAnnotationPlacement>,
) {
    init {
        sourceSymbolId?.let { symbolId ->
            require(symbolId.value.isNotBlank()) { "DTO annotation source symbol id cannot be blank" }
        }
        require(placements.isNotEmpty()) {
            "DTO annotation application must have at least one placement: ${annotation.typeId.value}"
        }
        require(placements == placements.distinct().sorted()) {
            "DTO annotation application placements must be distinct and sorted: ${annotation.typeId.value}"
        }
        require(origin == JimmerDtoAnnotationOrigin.IMMUTABLE || sourceSymbolId == null) {
            "DTO-authored annotation application cannot reference an immutable source symbol"
        }
    }

}

internal enum class JimmerDtoAnnotationOrigin {
    IMMUTABLE,
    DTO,
}

internal data class JimmerDtoAppliedAnnotation(
    val typeId: LsiSymbolId,
    val arguments: List<JimmerDtoAppliedAnnotationArgument>,
) {
    init {
        typeId.requireTypeQualifiedName()
        require(arguments == arguments.sortedBy(JimmerDtoAppliedAnnotationArgument::name)) {
            "DTO applied annotation arguments must use stable name order: ${typeId.value}"
        }
        require(arguments.map(JimmerDtoAppliedAnnotationArgument::name).distinct().size == arguments.size) {
            "DTO applied annotation cannot contain duplicate arguments: ${typeId.value}"
        }
    }

    fun canonicalText(): String = canonicalValue(
        typeId.value,
        arguments.canonicalList(JimmerDtoAppliedAnnotationArgument::canonicalText),
    )
}

internal data class JimmerDtoAppliedAnnotationArgument(
    val name: String,
    val value: JimmerDtoAppliedAnnotationValue,
) {
    init {
        require(name.isNotBlank()) { "DTO applied annotation argument name cannot be blank" }
    }

    fun canonicalText(): String = canonicalValue(name, value.canonicalText())
}

internal sealed interface JimmerDtoAppliedAnnotationValue {
    data class ScalarValue(
        val kind: JimmerDtoAnnotationScalarKind,
        val value: String,
    ) : JimmerDtoAppliedAnnotationValue

    data class EnumValue(
        val enumTypeId: LsiSymbolId,
        val constant: String,
    ) : JimmerDtoAppliedAnnotationValue {
        init {
            enumTypeId.requireTypeQualifiedName()
            require(constant.isNotBlank()) { "DTO applied annotation enum constant cannot be blank" }
        }
    }

    data class TypeValue(
        val type: LsiTypeRef,
    ) : JimmerDtoAppliedAnnotationValue

    data class AnnotationValue(
        val annotation: JimmerDtoAppliedAnnotation,
    ) : JimmerDtoAppliedAnnotationValue

    data class ArrayValue(
        val elements: List<JimmerDtoAppliedAnnotationValue>,
    ) : JimmerDtoAppliedAnnotationValue

    data class SourceLiteralValue(
        val code: String,
    ) : JimmerDtoAppliedAnnotationValue {
        init {
            require(code.isNotBlank()) { "DTO annotation source literal cannot be blank" }
        }
    }
}

internal enum class JimmerDtoAnnotationScalarKind {
    BOOLEAN,
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    CHAR,
    STRING,
}

internal class JimmerDtoAnnotationContractFreezer(
    private val workspace: LsiWorkspace,
    private val immutableSchema: ImmutableSchema,
) {
    fun freeze(graph: JimmerDtoRenderGraph): JimmerDtoAnnotationContract {
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
        val declarationsByTypeId = declarations.associateBy(JimmerDtoAnnotationDeclaration::typeId)
        val typePlans = typeCandidates.map { target ->
            JimmerDtoTypeAnnotationPlan(
                typeId = JimmerDtoTypeId(target.targetId),
                applications = freezeApplications(
                    target = target,
                    targetKind = AnnotationPlanTargetKind.TYPE,
                    supportedPlacements = TYPE_APPLICATION_PLACEMENTS,
                    declarationsByTypeId = declarationsByTypeId,
                    diagnostics = diagnostics,
                ),
            )
        }.sortedBy(JimmerDtoTypeAnnotationPlan::typeId)
        val propPlans = propCandidates.map { candidates ->
            JimmerDtoPropAnnotationPlan(
                propId = JimmerDtoPropId(candidates.property.targetId),
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
        }.sortedBy(JimmerDtoPropAnnotationPlan::propId)
        return JimmerDtoAnnotationContract(
            declarations = declarations.sortedBy(JimmerDtoAnnotationDeclaration::typeId),
            typePlans = typePlans,
            propPlans = propPlans,
            diagnostics = diagnostics
                .distinctBy(LsiDiagnostic::canonicalText)
                .sortedBy(LsiDiagnostic::stableOrderKey),
        )
    }

    private fun typeCandidates(
        type: JimmerDtoType,
        diagnostics: MutableList<LsiDiagnostic>,
    ): AnnotationTargetCandidates {
        val baseType = type.baseTypeId?.let(immutableSchema.typesById::get)
        if (type.baseTypeId != null && baseType == null) {
            diagnostics += missingImmutableTargetDiagnostic(
                code = "jimmer.dto.annotation.base-type-missing",
                message = "DTO 注解冻结无法找到不可变基础类型 ${type.baseTypeId.value}",
                targetId = type.id.value,
                symbolId = type.baseTypeId,
                location = type.location,
            )
        }
        val baseAnnotations = baseType?.let { immutableType ->
            immutableType.annotations.map { annotation ->
                AnnotationCandidate(
                    annotation = annotation.toAppliedAnnotation(),
                    origin = JimmerDtoAnnotationOrigin.IMMUTABLE,
                    sourceSymbolId = immutableType.id,
                    location = type.location,
                )
            }
        }.orEmpty()
        val dtoAnnotations = type.annotations.map { annotation ->
            AnnotationCandidate(
                annotation = annotation.toAppliedAnnotation(),
                origin = JimmerDtoAnnotationOrigin.DTO,
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
        graph: JimmerDtoRenderGraph,
        prop: JimmerDtoProp,
        diagnostics: MutableList<LsiDiagnostic>,
    ): AnnotationTargetCandidates {
        val baseProp = if (prop is JimmerDtoBaseProp) {
            val tailProp = graph.propsById.getValue(prop.tailPropId) as JimmerDtoBaseProp
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
                    annotation = annotation.toAppliedAnnotation(),
                    origin = JimmerDtoAnnotationOrigin.IMMUTABLE,
                    sourceSymbolId = immutableProp.id,
                    location = prop.aliasLocation,
                )
            }
        }.orEmpty()
        val dtoAnnotations = prop.annotations.map { annotation ->
            AnnotationCandidate(
                annotation = annotation.toAppliedAnnotation(),
                origin = JimmerDtoAnnotationOrigin.DTO,
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
        graph: JimmerDtoRenderGraph,
        prop: JimmerDtoProp,
        diagnostics: MutableList<LsiDiagnostic>,
    ): AnnotationTargetCandidates {
        val ownerType = graph.typesById.getValue(prop.ownerTypeId)
        if (prop.id !in ownerType.propIds || !ownerType.requiresInputBuilder(graph)) {
            return AnnotationTargetCandidates(prop.id.value, prop.aliasLocation, emptyList())
        }
        val dtoCandidates = prop.annotations.map { annotation ->
            AnnotationCandidate(
                annotation = annotation.toAppliedAnnotation(),
                origin = JimmerDtoAnnotationOrigin.DTO,
                sourceSymbolId = null,
                location = prop.aliasLocation,
            )
        }
        val baseProp = if (prop is JimmerDtoBaseProp) {
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
                    annotation = annotation.toAppliedAnnotation(),
                    origin = JimmerDtoAnnotationOrigin.IMMUTABLE,
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
    ): JimmerDtoAnnotationDeclaration? {
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
        val argumentNames = declaration.annotationMembers.map { member -> member.name }.sorted()
        val kotlinValueVararg = kind == JimmerDtoAnnotationDeclarationKind.KOTLIN &&
            declaration.annotationMembers.any { member ->
                member.name == "value" && member.vararg
            }
        val targetPolicy = declaration.dtoAnnotationTargetPolicy()
        return JimmerDtoAnnotationDeclaration(
            typeId = typeId,
            kind = kind,
            targetDeclared = targetPolicy.declared,
            allowedPlacements = targetPolicy.allowedPlacements,
            argumentNames = argumentNames,
            kotlinValueVararg = kotlinValueVararg,
        )
    }

    private fun freezeApplications(
        target: AnnotationTargetCandidates,
        targetKind: AnnotationPlanTargetKind,
        supportedPlacements: Set<JimmerDtoAnnotationPlacement>,
        declarationsByTypeId: Map<LsiSymbolId, JimmerDtoAnnotationDeclaration>,
        diagnostics: MutableList<LsiDiagnostic>,
    ): List<JimmerDtoAnnotationApplication> {
        return target.candidates.mapNotNull { candidate ->
            val declaration = declarationsByTypeId[candidate.annotation.typeId] ?: return@mapNotNull null
            val supportedCandidatePlacements = when {
                declaration.targetDeclared -> declaration.allowedPlacements
                    .filter(supportedPlacements::contains)
                    .sorted()
                targetKind == AnnotationPlanTargetKind.TYPE -> listOf(JimmerDtoAnnotationPlacement.TYPE)
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
            validateAnnotationArguments(candidate, declarationsByTypeId, diagnostics)
            if (!candidate.annotation.hasValidArgumentSchema(declarationsByTypeId)) {
                return@mapNotNull null
            }
            JimmerDtoAnnotationApplication(
                annotation = candidate.annotation,
                origin = candidate.origin,
                sourceSymbolId = candidate.sourceSymbolId,
                placements = supportedCandidatePlacements,
            )
        }
    }

    private fun freezeBuilderSetterApplications(
        target: AnnotationTargetCandidates,
        declarationsByTypeId: Map<LsiSymbolId, JimmerDtoAnnotationDeclaration>,
        diagnostics: MutableList<LsiDiagnostic>,
    ): List<JimmerDtoBuilderSetterAnnotationApplication> {
        return target.candidates.mapNotNull { candidate ->
            if (candidate.annotation.typeId !in declarationsByTypeId) {
                return@mapNotNull null
            }
            validateAnnotationArguments(candidate, declarationsByTypeId, diagnostics)
            if (!candidate.annotation.hasValidArgumentSchema(declarationsByTypeId)) {
                return@mapNotNull null
            }
            JimmerDtoBuilderSetterAnnotationApplication(
                annotation = candidate.annotation,
                origin = candidate.origin,
                sourceSymbolId = candidate.sourceSymbolId,
            )
        }
    }

    private fun validateAnnotationArguments(
        candidate: AnnotationCandidate,
        declarationsByTypeId: Map<LsiSymbolId, JimmerDtoAnnotationDeclaration>,
        diagnostics: MutableList<LsiDiagnostic>,
    ) {
        candidate.annotation.visit { annotation ->
            val declaration = declarationsByTypeId[annotation.typeId] ?: return@visit
            annotation.arguments.forEach { argument ->
                if (argument.name in declaration.argumentNames) {
                    return@forEach
                }
                diagnostics += LsiDiagnostic(
                    code = "jimmer.dto.annotation.argument",
                    severity = LsiDiagnosticSeverity.ERROR,
                    message = "注解 ${annotation.typeId.value} 不存在参数 ${argument.name}",
                    symbolId = candidate.sourceSymbolId,
                    location = candidate.location,
                    details = sortedMapOf(
                        "annotationType" to annotation.typeId.value,
                        "argument" to argument.name,
                    ),
                )
            }
        }
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

internal fun JimmerDtoAnnotationContract.normalizedSnapshot(): String {
    return buildList {
        declarations.forEach { declaration ->
            add(
                canonicalValue(
                    "declaration",
                    declaration.typeId.value,
                    declaration.kind.name,
                    declaration.targetDeclared.toString(),
                    declaration.allowedPlacements.joinToString(",", transform = JimmerDtoAnnotationPlacement::name),
                    declaration.argumentNames.joinToString(","),
                    declaration.kotlinValueVararg.toString(),
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

internal fun JimmerDtoAnnotationContract.fingerprint(): String {
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
    val annotation: JimmerDtoAppliedAnnotation,
    val origin: JimmerDtoAnnotationOrigin,
    val sourceSymbolId: LsiSymbolId?,
    val location: LsiLocation?,
) {
    fun isCopyable(): Boolean {
        val qualifiedName = annotation.typeId.requireTypeQualifiedName()
        if (qualifiedName == KOTLIN_DTO_ANNOTATION) {
            return false
        }
        if (
            origin == JimmerDtoAnnotationOrigin.IMMUTABLE &&
            (qualifiedName == T_NULLABLE_ANNOTATION || qualifiedName.substringAfterLast('.') in NULLITY_SIMPLE_NAMES)
        ) {
            return false
        }
        if (qualifiedName == IMMUTABLE_ANNOTATION || qualifiedName.startsWith(JIMMER_SQL_PREFIX)) {
            return false
        }
        if (
            origin == JimmerDtoAnnotationOrigin.IMMUTABLE &&
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

private fun JimmerDtoType.requiresInputBuilder(graph: JimmerDtoRenderGraph): Boolean {
    if (polymorphism != null || JimmerDtoModifier.INPUT !in modifiers) {
        return false
    }
    return propIds.asSequence()
        .map(graph.propsById::getValue)
        .filterIsInstance<JimmerDtoBaseProp>()
        .any { prop ->
            prop.inputModifier == JimmerDtoModifier.FIXED ||
                prop.inputModifier == JimmerDtoModifier.DYNAMIC
        }
}

private fun LsiTypeDeclaration.annotationDeclarationKind(): JimmerDtoAnnotationDeclarationKind {
    if (
        annotations.any { annotation ->
            annotation.type == KOTLIN_METADATA || annotation.type == KOTLIN_TARGET
        } || annotationMembers.any { member -> member.vararg } || origin.language == LsiLanguage.KOTLIN
    ) {
        return JimmerDtoAnnotationDeclarationKind.KOTLIN
    }
    return JimmerDtoAnnotationDeclarationKind.JAVA
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
    val allowedPlacements: List<JimmerDtoAnnotationPlacement>,
)

private fun LsiAnnotation.toAppliedAnnotation(): JimmerDtoAppliedAnnotation {
    return JimmerDtoAppliedAnnotation(
        typeId = type,
        arguments = arguments.entries
            .asSequence()
            .filter { (_, argument) -> argument.origin == LsiAnnotationArgumentOrigin.EXPLICIT }
            .map { (name, argument) ->
                JimmerDtoAppliedAnnotationArgument(name, argument.value.toAppliedAnnotationValue())
            }
            .sortedBy(JimmerDtoAppliedAnnotationArgument::name)
            .toList(),
    )
}

private fun JimmerDtoAnnotation.toAppliedAnnotation(): JimmerDtoAppliedAnnotation {
    return JimmerDtoAppliedAnnotation(
        typeId = typeId,
        arguments = arguments.map { argument ->
            JimmerDtoAppliedAnnotationArgument(argument.name, argument.value.toAppliedAnnotationValue())
        }.sortedBy(JimmerDtoAppliedAnnotationArgument::name),
    )
}

private fun LsiAnnotationValue.toAppliedAnnotationValue(): JimmerDtoAppliedAnnotationValue {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> scalar(JimmerDtoAnnotationScalarKind.BOOLEAN, value)
        is LsiAnnotationValue.ByteValue -> scalar(JimmerDtoAnnotationScalarKind.BYTE, value)
        is LsiAnnotationValue.ShortValue -> scalar(JimmerDtoAnnotationScalarKind.SHORT, value)
        is LsiAnnotationValue.IntValue -> scalar(JimmerDtoAnnotationScalarKind.INT, value)
        is LsiAnnotationValue.LongValue -> scalar(JimmerDtoAnnotationScalarKind.LONG, value)
        is LsiAnnotationValue.FloatValue -> scalar(JimmerDtoAnnotationScalarKind.FLOAT, value)
        is LsiAnnotationValue.DoubleValue -> scalar(JimmerDtoAnnotationScalarKind.DOUBLE, value)
        is LsiAnnotationValue.CharValue -> scalar(JimmerDtoAnnotationScalarKind.CHAR, value)
        is LsiAnnotationValue.StringValue -> scalar(JimmerDtoAnnotationScalarKind.STRING, value)
        is LsiAnnotationValue.EnumValue -> JimmerDtoAppliedAnnotationValue.EnumValue(enumType, entryName)
        is LsiAnnotationValue.ClassValue -> JimmerDtoAppliedAnnotationValue.TypeValue(type)
        is LsiAnnotationValue.NestedAnnotationValue -> {
            JimmerDtoAppliedAnnotationValue.AnnotationValue(annotation.toAppliedAnnotation())
        }
        is LsiAnnotationValue.ArrayValue -> JimmerDtoAppliedAnnotationValue.ArrayValue(
            elements.map(LsiAnnotationValue::toAppliedAnnotationValue)
        )
    }
}

private fun JimmerDtoAnnotationValue.toAppliedAnnotationValue(): JimmerDtoAppliedAnnotationValue {
    return when (this) {
        is JimmerDtoAnnotationValue.ArrayValue -> JimmerDtoAppliedAnnotationValue.ArrayValue(
            elements.map(JimmerDtoAnnotationValue::toAppliedAnnotationValue)
        )
        is JimmerDtoAnnotationValue.AnnotationValue -> {
            JimmerDtoAppliedAnnotationValue.AnnotationValue(annotation.toAppliedAnnotation())
        }
        is JimmerDtoAnnotationValue.EnumValue -> JimmerDtoAppliedAnnotationValue.EnumValue(enumTypeId, constant)
        is JimmerDtoAnnotationValue.TypeValue -> JimmerDtoAppliedAnnotationValue.TypeValue(type.toLsiType())
        is JimmerDtoAnnotationValue.LiteralValue -> JimmerDtoAppliedAnnotationValue.SourceLiteralValue(code)
    }
}

private fun scalar(
    kind: JimmerDtoAnnotationScalarKind,
    value: Any,
): JimmerDtoAppliedAnnotationValue.ScalarValue {
    return JimmerDtoAppliedAnnotationValue.ScalarValue(kind, value.toString())
}

private fun JimmerDtoTypeRef.toLsiType(): LsiTypeRef {
    val nullability = if (nullable) LsiNullability.NULLABLE else LsiNullability.NON_NULL
    val primitiveKind = DTO_PRIMITIVE_KINDS[typeName]
    if (primitiveKind != null && arguments.isEmpty()) {
        return LsiPrimitiveType(primitiveKind, nullability)
    }
    if (typeName in DTO_ARRAY_TYPE_NAMES) {
        require(arguments.size == 1 && arguments.single().type != null) {
            "DTO Array type must have exactly one non-star element type: $typeName"
        }
        return LsiArrayType(
            elementType = requireNotNull(arguments.single().type).toLsiType(),
            nullability = nullability,
        )
    }
    val canonicalTypeName = DTO_STANDARD_DECLARED_TYPES[typeName] ?: typeName
    return LsiDeclaredType(
        declarationId = LsiSymbolId.type(canonicalTypeName),
        arguments = arguments.map(JimmerDtoTypeArgument::toLsiTypeArgument),
        nullability = nullability,
    )
}

private fun JimmerDtoTypeArgument.toLsiTypeArgument(): LsiTypeArgument {
    return when (variance) {
        JimmerDtoVariance.INVARIANT -> LsiTypeArgument.invariant(requireNotNull(type).toLsiType())
        JimmerDtoVariance.IN -> LsiTypeArgument.input(requireNotNull(type).toLsiType())
        JimmerDtoVariance.OUT -> LsiTypeArgument.output(requireNotNull(type).toLsiType())
        JimmerDtoVariance.STAR -> LsiTypeArgument.STAR
    }
}

private fun JimmerDtoAppliedAnnotation.collectAnnotationTypeIds(
    destination: MutableSet<LsiSymbolId>,
) {
    destination.add(typeId)
    arguments.forEach { argument -> argument.value.collectAnnotationTypeIds(destination) }
}

private fun JimmerDtoAppliedAnnotationValue.collectAnnotationTypeIds(
    destination: MutableSet<LsiSymbolId>,
) {
    when (this) {
        is JimmerDtoAppliedAnnotationValue.AnnotationValue -> annotation.collectAnnotationTypeIds(destination)
        is JimmerDtoAppliedAnnotationValue.ArrayValue -> elements.forEach { element ->
            element.collectAnnotationTypeIds(destination)
        }
        is JimmerDtoAppliedAnnotationValue.EnumValue,
        is JimmerDtoAppliedAnnotationValue.ScalarValue,
        is JimmerDtoAppliedAnnotationValue.SourceLiteralValue,
        is JimmerDtoAppliedAnnotationValue.TypeValue,
        -> Unit
    }
}

private fun JimmerDtoAppliedAnnotation.annotationTypeIds(): Set<LsiSymbolId> = buildSet {
    collectAnnotationTypeIds(this)
}

private fun JimmerDtoAppliedAnnotation.visit(
    block: (JimmerDtoAppliedAnnotation) -> Unit,
) {
    block(this)
    arguments.forEach { argument -> argument.value.visitAnnotations(block) }
}

private fun JimmerDtoAppliedAnnotation.hasValidArgumentSchema(
    declarationsByTypeId: Map<LsiSymbolId, JimmerDtoAnnotationDeclaration>,
): Boolean {
    val declaration = declarationsByTypeId[typeId] ?: return false
    if (arguments.any { argument -> argument.name !in declaration.argumentNames }) {
        return false
    }
    return arguments.all { argument ->
        argument.value.hasValidAnnotationSchema(declarationsByTypeId)
    }
}

private fun JimmerDtoAppliedAnnotationValue.hasValidAnnotationSchema(
    declarationsByTypeId: Map<LsiSymbolId, JimmerDtoAnnotationDeclaration>,
): Boolean {
    return when (this) {
        is JimmerDtoAppliedAnnotationValue.AnnotationValue -> {
            annotation.hasValidArgumentSchema(declarationsByTypeId)
        }
        is JimmerDtoAppliedAnnotationValue.ArrayValue -> elements.all { element ->
            element.hasValidAnnotationSchema(declarationsByTypeId)
        }
        is JimmerDtoAppliedAnnotationValue.EnumValue,
        is JimmerDtoAppliedAnnotationValue.ScalarValue,
        is JimmerDtoAppliedAnnotationValue.SourceLiteralValue,
        is JimmerDtoAppliedAnnotationValue.TypeValue,
        -> true
    }
}

private fun JimmerDtoAppliedAnnotationValue.visitAnnotations(
    block: (JimmerDtoAppliedAnnotation) -> Unit,
) {
    when (this) {
        is JimmerDtoAppliedAnnotationValue.AnnotationValue -> annotation.visit(block)
        is JimmerDtoAppliedAnnotationValue.ArrayValue -> elements.forEach { element ->
            element.visitAnnotations(block)
        }
        is JimmerDtoAppliedAnnotationValue.EnumValue,
        is JimmerDtoAppliedAnnotationValue.ScalarValue,
        is JimmerDtoAppliedAnnotationValue.SourceLiteralValue,
        is JimmerDtoAppliedAnnotationValue.TypeValue,
        -> Unit
    }
}

private fun JimmerDtoAnnotationApplication.canonicalText(): String = canonicalValue(
    annotation.canonicalText(),
    origin.name,
    sourceSymbolId?.value.orEmpty(),
    placements.joinToString(",", transform = JimmerDtoAnnotationPlacement::name),
)

private fun JimmerDtoBuilderSetterAnnotationApplication.canonicalText(): String = canonicalValue(
    annotation.canonicalText(),
    origin.name,
    sourceSymbolId?.value.orEmpty(),
)

private fun JimmerDtoAppliedAnnotationValue.canonicalText(): String {
    return when (this) {
        is JimmerDtoAppliedAnnotationValue.ScalarValue -> canonicalValue("scalar", kind.name, value)
        is JimmerDtoAppliedAnnotationValue.EnumValue -> {
            canonicalValue("enum", enumTypeId.value, constant)
        }
        is JimmerDtoAppliedAnnotationValue.TypeValue -> canonicalValue("type", type.stableSignature())
        is JimmerDtoAppliedAnnotationValue.AnnotationValue -> {
            canonicalValue("annotation", annotation.canonicalText())
        }
        is JimmerDtoAppliedAnnotationValue.ArrayValue -> {
            canonicalValue("array", elements.canonicalList(JimmerDtoAppliedAnnotationValue::canonicalText))
        }
        is JimmerDtoAppliedAnnotationValue.SourceLiteralValue -> canonicalValue("source", code)
    }
}

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

private val TYPE_APPLICATION_PLACEMENTS = setOf(JimmerDtoAnnotationPlacement.TYPE)

private val PROP_APPLICATION_PLACEMENTS = setOf(
    JimmerDtoAnnotationPlacement.FIELD,
    JimmerDtoAnnotationPlacement.GETTER,
    JimmerDtoAnnotationPlacement.SETTER,
    JimmerDtoAnnotationPlacement.PROPERTY,
)

private val LSI_TARGET_PLACEMENTS = mapOf(
    LsiAnnotationTarget.TYPE to JimmerDtoAnnotationPlacement.TYPE,
    LsiAnnotationTarget.ANNOTATION_TYPE to JimmerDtoAnnotationPlacement.ANNOTATION_TYPE,
    LsiAnnotationTarget.CONSTRUCTOR to JimmerDtoAnnotationPlacement.CONSTRUCTOR,
    LsiAnnotationTarget.FIELD to JimmerDtoAnnotationPlacement.FIELD,
    LsiAnnotationTarget.METHOD to JimmerDtoAnnotationPlacement.GETTER,
    LsiAnnotationTarget.PARAMETER to JimmerDtoAnnotationPlacement.PARAMETER,
    LsiAnnotationTarget.TYPE_USE to JimmerDtoAnnotationPlacement.TYPE_USE,
    LsiAnnotationTarget.TYPE_PARAMETER to JimmerDtoAnnotationPlacement.TYPE_PARAMETER,
    LsiAnnotationTarget.LOCAL_VARIABLE to JimmerDtoAnnotationPlacement.LOCAL_VARIABLE,
    LsiAnnotationTarget.PROPERTY to JimmerDtoAnnotationPlacement.PROPERTY,
    LsiAnnotationTarget.GETTER to JimmerDtoAnnotationPlacement.GETTER,
    LsiAnnotationTarget.SETTER to JimmerDtoAnnotationPlacement.SETTER,
    LsiAnnotationTarget.EXPRESSION to JimmerDtoAnnotationPlacement.EXPRESSION,
    LsiAnnotationTarget.FILE to JimmerDtoAnnotationPlacement.FILE,
    LsiAnnotationTarget.TYPE_ALIAS to JimmerDtoAnnotationPlacement.TYPE_ALIAS,
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

private val DTO_STANDARD_DECLARED_TYPES = mapOf(
    "Any" to "kotlin.Any",
    "String" to "kotlin.String",
    "Iterable" to "kotlin.collections.Iterable",
    "MutableIterable" to "kotlin.collections.MutableIterable",
    "Collection" to "kotlin.collections.Collection",
    "MutableCollection" to "kotlin.collections.MutableCollection",
    "List" to "kotlin.collections.List",
    "MutableList" to "kotlin.collections.MutableList",
    "Set" to "kotlin.collections.Set",
    "MutableSet" to "kotlin.collections.MutableSet",
    "Map" to "kotlin.collections.Map",
    "MutableMap" to "kotlin.collections.MutableMap",
    "java.lang.Object" to "kotlin.Any",
    "java.lang.String" to "kotlin.String",
    "kotlin.Any" to "kotlin.Any",
    "kotlin.String" to "kotlin.String",
    "kotlin.collections.Iterable" to "kotlin.collections.Iterable",
    "kotlin.collections.MutableIterable" to "kotlin.collections.MutableIterable",
    "kotlin.collections.Collection" to "kotlin.collections.Collection",
    "kotlin.collections.MutableCollection" to "kotlin.collections.MutableCollection",
    "kotlin.collections.List" to "kotlin.collections.List",
    "kotlin.collections.MutableList" to "kotlin.collections.MutableList",
    "kotlin.collections.Set" to "kotlin.collections.Set",
    "kotlin.collections.MutableSet" to "kotlin.collections.MutableSet",
    "kotlin.collections.Map" to "kotlin.collections.Map",
    "kotlin.collections.MutableMap" to "kotlin.collections.MutableMap",
)

private val LSI_LOCATION_COMPARATOR = compareBy<LsiLocation>(
    { location -> location.source },
    { location -> location.start },
    { location -> location.end },
)

private val KOTLIN_TARGET = LsiSymbolId.type("kotlin.annotation.Target")
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
