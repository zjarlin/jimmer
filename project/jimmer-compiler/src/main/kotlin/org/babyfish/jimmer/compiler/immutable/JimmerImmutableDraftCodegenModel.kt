package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.ImmutableValidation
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiVisibility

internal data class JimmerImmutableDraftCodegenSchema(
    val jacksonFamily: JimmerImmutableJacksonFamily,
    val types: List<JimmerImmutableDraftTypePlan>,
) {

    val typesById: Map<LsiSymbolId, JimmerImmutableDraftTypePlan> =
        types.associateBy(JimmerImmutableDraftTypePlan::typeId)

    init {
        require(typesById.size == types.size) {
            "Immutable draft codegen schema cannot contain duplicate type ids"
        }
    }
}

internal data class JimmerImmutableDraftTypePlan(
    val typeId: LsiSymbolId,
    val qualifiedName: String,
    val kind: ImmutableTypeKind,
    val sourceLanguage: LsiLanguage,
    val sourcePath: String?,
    val sourceBaseName: String?,
    val visibility: LsiVisibility,
    val typeParameters: List<LsiTypeParameter>,
    val selfType: LsiDeclaredType,
    val directSuperTypes: List<LsiDeclaredType>,
    val primarySuperTypeId: LsiSymbolId?,
    val propsBySlot: List<JimmerImmutableDraftPropPlan>,
    val runtimeDeclaredPropIds: List<LsiSymbolId>,
    val runtimeRedefinedPropIds: List<LsiSymbolId>,
    val kotlinDraftPropIds: List<LsiSymbolId>,
    val idPropId: LsiSymbolId?,
    val versionPropId: LsiSymbolId?,
    val logicalDeletedPropId: LsiSymbolId?,
    val customValidations: List<ImmutableValidation>,
    val artifactOriginatingSymbols: Set<LsiSymbolId>,
    val artifactOriginatingSources: List<LsiSource>,
    val dependencySymbols: Set<LsiSymbolId>,
    val dependencySources: List<LsiSource>,
) {

    val propsById: Map<LsiSymbolId, JimmerImmutableDraftPropPlan> =
        propsBySlot.associateBy(JimmerImmutableDraftPropPlan::propId)

    val requiresVisibilityState: Boolean = propsBySlot.any { prop -> !prop.valueState.hasValue }

    init {
        require(propsById.size == propsBySlot.size) {
            "Immutable draft type cannot contain duplicate property ids: ${typeId.value}"
        }
        require(propsBySlot.map(JimmerImmutableDraftPropPlan::slotIndex) == propsBySlot.indices.toList()) {
            "Immutable draft slots must be contiguous and zero-based: ${typeId.value}"
        }
        require(runtimeDeclaredPropIds.distinct().size == runtimeDeclaredPropIds.size) {
            "Immutable draft declared properties must be unique: ${typeId.value}"
        }
        require(runtimeRedefinedPropIds.distinct().size == runtimeRedefinedPropIds.size) {
            "Immutable draft redefined properties must be unique: ${typeId.value}"
        }
        require(runtimeDeclaredPropIds.all(propsById::containsKey)) {
            "Immutable draft declared properties must belong to their type: ${typeId.value}"
        }
        require(runtimeRedefinedPropIds.all(propsById::containsKey)) {
            "Immutable draft redefined properties must belong to their type: ${typeId.value}"
        }
        require(runtimeDeclaredPropIds.none(runtimeRedefinedPropIds::contains)) {
            "Immutable draft property cannot be declared and redefined together: ${typeId.value}"
        }
        require(kotlinDraftPropIds.distinct().size == kotlinDraftPropIds.size) {
            "Immutable Kotlin draft declarations must be unique: ${typeId.value}"
        }
        require(kotlinDraftPropIds.all(propsById::containsKey)) {
            "Immutable Kotlin draft declarations must belong to their type: ${typeId.value}"
        }
        require(primarySuperTypeId == null || directSuperTypes.any { superType ->
            superType.declarationId == primarySuperTypeId
        }) {
            "Immutable draft primary super type must be direct: ${typeId.value}"
        }
        require(artifactOriginatingSymbols == setOf(typeId)) {
            "Immutable draft artifact must originate from exactly its owner type: ${typeId.value}"
        }
        require(artifactOriginatingSources.size <= 1) {
            "Immutable draft isolating artifact cannot have multiple originating sources: ${typeId.value}"
        }
        require(typeId in dependencySymbols) {
            "Immutable draft dependencies must contain their owner type: ${typeId.value}"
        }
    }
}

internal data class JimmerImmutableDraftPropPlan(
    val propId: LsiSymbolId,
    val declarationId: LsiSymbolId,
    val lineageRootId: LsiSymbolId,
    val sourceDeclaringTypeId: LsiSymbolId,
    val runtimeOwnerTypeId: LsiSymbolId,
    val slotIndex: Int,
    val metadataSlotIndex: Int?,
    val role: JimmerImmutableDraftPropRole,
    val name: String,
    val codegenName: String,
    val sourceGetterName: String,
    val documentation: String?,
    val sourceDocumentation: String?,
    val accessorStyle: JimmerImmutableDraftAccessorStyle,
    val slotName: String,
    val javaSetterName: String,
    val javaBeanGetterName: String,
    val javaApplierName: String,
    val javaAdderByName: String,
    val annotationPlan: JimmerImmutableDraftAnnotationPlan,
    val valueFieldName: String?,
    val loadedStateFieldName: String?,
    val javaDeeperPropIdName: String?,
    val kotlinDeeperPropIdName: String?,
    val type: LsiTypeRef,
    val elementType: LsiTypeRef,
    val runtimeProp: JimmerImmutableDraftRuntimeProp,
    val targetTypeId: LsiSymbolId?,
    val targetIdPropId: LsiSymbolId?,
    val primitive: Boolean,
    val nullable: Boolean,
    val list: Boolean,
    val association: Boolean,
    val immutableReference: Boolean,
    val genericTarget: Boolean,
    val genericSourceTarget: Boolean,
    val languageFormula: Boolean,
    val valueState: JimmerImmutableDraftValueState,
    val visibilityControllable: Boolean,
    val writable: Boolean,
    val autoCreateSupported: Boolean,
    val referenceMutationSupported: Boolean,
    val idViewBasePropId: LsiSymbolId?,
    val manyToManyBasePropId: LsiSymbolId?,
    val manyToManyDeeperPropId: LsiSymbolId?,
    val formulaDependencyPaths: List<List<LsiSymbolId>>,
    val associatedId: JimmerImmutableAssociatedIdContract?,
    val validationPlan: JimmerImmutableDraftValidationPlan,
) {

    fun javaPatternFieldName(index: Int): String {
        require(index >= 0) { "Immutable Java pattern index cannot be negative: $index" }
        return "__${codegenName.legacyUpper()}_PATTER${index.patternFieldSuffix()}"
    }

    fun kotlinPatternFieldName(index: Int): String {
        require(index >= 0) { "Immutable Kotlin pattern index cannot be negative: $index" }
        return "__${name.legacyUpper()}_PATTERN${index.patternFieldSuffix()}"
    }

    init {
        require(slotIndex >= 0) { "Immutable draft slot cannot be negative: ${propId.value}" }
        require(metadataSlotIndex == null || metadataSlotIndex == slotIndex) {
            "Immutable draft metadata slot must reuse its runtime slot: ${propId.value}"
        }
        require(valueState.hasValue || !valueState.hasLoadedState) {
            "Immutable draft loaded state requires value storage: ${propId.value}"
        }
        require(primitive == (type is site.addzero.lsi.model.LsiPrimitiveType && !type.boxed)) {
            "Immutable draft primitive flag must match its LSI type: ${propId.value}"
        }
        require(runtimeProp.valueCategory == when {
            list && immutableReference -> JimmerImmutableDraftRuntimeValueCategory.REFERENCE_LIST
            list -> JimmerImmutableDraftRuntimeValueCategory.SCALAR_LIST
            immutableReference -> JimmerImmutableDraftRuntimeValueCategory.REFERENCE
            else -> JimmerImmutableDraftRuntimeValueCategory.SCALAR
        }) {
            "Immutable draft runtime value category must match property shape: ${propId.value}"
        }
        require(validationPlan.propId == propId) {
            "Immutable draft validation plan must belong to its property: ${propId.value}"
        }
        require((validationPlan.requiredNullCheck != null) == (!nullable && !primitive)) {
            "Immutable draft required-null validation must match property nullability: ${propId.value}"
        }
        require(!genericTarget || targetTypeId == null) {
            "Immutable draft generic target cannot have concrete target type: ${propId.value}"
        }
        require(associatedId == null || association && !list && targetIdPropId != null) {
            "Immutable associated-id contract requires concrete to-one association: ${propId.value}"
        }
        require(associatedId == null || associatedId.targetIdPropId == targetIdPropId) {
            "Immutable associated-id target must match property target id: ${propId.value}"
        }
        require(idViewBasePropId == null || manyToManyBasePropId == null) {
            "Immutable draft property cannot be id-view and many-to-many-view together: ${propId.value}"
        }
        require((manyToManyBasePropId == null) == (manyToManyDeeperPropId == null)) {
            "Immutable many-to-many draft view must preserve both dependency properties: ${propId.value}"
        }
        require(valueFieldName == if (valueState.hasValue) "__${codegenName}Value" else null) {
            "Immutable draft value field name must match value state: ${propId.value}"
        }
        require(loadedStateFieldName == if (valueState.hasLoadedState) "__${codegenName}Loaded" else null) {
            "Immutable draft loaded-state field name must match loaded state: ${propId.value}"
        }
        val expectedJavaDeeperPropIdName = manyToManyBasePropId?.let {
            "DEEPER_PROP_ID_${slotName.removePrefix("SLOT_")}"
        }
        val expectedKotlinDeeperPropIdName = manyToManyBasePropId?.let {
            "DEEP_PROP_ID_${name.legacyUpper()}"
        }
        require(javaDeeperPropIdName == expectedJavaDeeperPropIdName) {
            "Immutable Java deeper property id name must match many-to-many view: ${propId.value}"
        }
        require(kotlinDeeperPropIdName == expectedKotlinDeeperPropIdName) {
            "Immutable Kotlin deeper property id name must match many-to-many view: ${propId.value}"
        }
    }
}

internal data class JimmerImmutableAssociatedIdContract(
    val name: String,
    val targetIdPropId: LsiSymbolId,
) {
    init {
        require(name.isNotBlank()) { "Immutable associated-id property name cannot be blank" }
    }
}

internal enum class JimmerImmutableDraftPropRole {
    INHERITED_PRIMARY,
    REDEFINED,
    DECLARED,
}

internal enum class JimmerImmutableDraftAccessorStyle {
    KOTLIN_PROPERTY,
    JAVA_BEAN_GET,
    JAVA_BEAN_IS,
    JAVA_BARE,
    UNKNOWN,
}

internal enum class JimmerImmutableDraftValueState(
    val hasValue: Boolean,
    val hasLoadedState: Boolean,
) {
    NONE(false, false),
    VALUE_ONLY(true, false),
    VALUE_AND_LOADED(true, true),
}

internal fun String.legacyUpper(): String {
    var previousUpper = true
    return buildString {
        for (character in this@legacyUpper) {
            val upper = character.isUpperCase()
            if (upper) {
                if (!previousUpper) {
                    append('_')
                }
                append(character)
            } else {
                append(character.uppercaseChar())
            }
            previousUpper = upper
        }
    }
}

private fun Int.patternFieldSuffix(): String = if (this == 0) "" else "_$this"
