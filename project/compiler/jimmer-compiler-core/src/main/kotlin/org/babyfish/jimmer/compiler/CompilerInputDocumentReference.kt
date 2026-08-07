package org.babyfish.jimmer.compiler

import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiTypeSeedMode
import site.addzero.lsi.model.mergeLsiTypeSeeds

enum class CompilerInputDocumentReferenceKind {
    SUBJECT_TYPE,
    TARGET_TYPE,
    ANNOTATION_TYPE,
    SUPER_TYPE,
    MODEL_TYPE,
    REUSABLE_DTO_TYPE,
    TYPE_USAGE,
    CONFIG_IMPLEMENTATION,
}

/**
 * 输入文档类型引用的有序候选，首项是在没有候选存在时的回退类型。
 */
class CompilerInputDocumentTypeSelector(
    val sourceName: String,
    val fallbackTypeId: LsiSymbolId,
    wildcardTypeIds: Collection<LsiSymbolId> = emptyList(),
    val checksFallbackExistence: Boolean = wildcardTypeIds.isNotEmpty(),
) : Comparable<CompilerInputDocumentTypeSelector> {

    val wildcardTypeIds: List<LsiSymbolId> = wildcardTypeIds
        .asSequence()
        .filter { typeId -> typeId != fallbackTypeId }
        .distinct()
        .toList()

    val candidateTypeIds: List<LsiSymbolId> = listOf(fallbackTypeId) + this.wildcardTypeIds

    init {
        require(sourceName.isNotBlank()) { "Compiler input type selector source name cannot be blank" }
        fallbackTypeId.requireTypeQualifiedName()
        this.wildcardTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
    }

    /**
     * 按 DTO 导入优先级在当前 LSI 工作区中选择类型。
     */
    fun select(typeExists: (LsiSymbolId) -> Boolean): CompilerInputDocumentTypeSelection {
        if (!checksFallbackExistence || typeExists(fallbackTypeId)) {
            return CompilerInputDocumentTypeSelection.selected(fallbackTypeId)
        }
        val matches = wildcardTypeIds.filter(typeExists)
        return when (matches.size) {
            0 -> CompilerInputDocumentTypeSelection.selected(fallbackTypeId)
            1 -> CompilerInputDocumentTypeSelection.selected(matches.single())
            else -> CompilerInputDocumentTypeSelection.ambiguous(matches)
        }
    }

    override fun compareTo(other: CompilerInputDocumentTypeSelector): Int {
        val sourceComparison = sourceName.compareTo(other.sourceName)
        if (sourceComparison != 0) {
            return sourceComparison
        }
        val fallbackComparison = fallbackTypeId.compareTo(other.fallbackTypeId)
        if (fallbackComparison != 0) {
            return fallbackComparison
        }
        val fallbackCheckComparison = checksFallbackExistence.compareTo(other.checksFallbackExistence)
        if (fallbackCheckComparison != 0) {
            return fallbackCheckComparison
        }
        val commonSize = minOf(wildcardTypeIds.size, other.wildcardTypeIds.size)
        for (index in 0 until commonSize) {
            val candidateComparison = wildcardTypeIds[index].compareTo(other.wildcardTypeIds[index])
            if (candidateComparison != 0) {
                return candidateComparison
            }
        }
        return wildcardTypeIds.size.compareTo(other.wildcardTypeIds.size)
    }

    override fun equals(other: Any?): Boolean {
        return this === other ||
            other is CompilerInputDocumentTypeSelector &&
            sourceName == other.sourceName &&
            fallbackTypeId == other.fallbackTypeId &&
            checksFallbackExistence == other.checksFallbackExistence &&
            wildcardTypeIds == other.wildcardTypeIds
    }

    override fun hashCode(): Int =
        31 * (
            31 * (31 * sourceName.hashCode() + fallbackTypeId.hashCode()) +
                checksFallbackExistence.hashCode()
            ) + wildcardTypeIds.hashCode()

    override fun toString(): String =
        "$sourceName -> ${candidateTypeIds.joinToString(prefix = "[", postfix = "]")}"
}

/**
 * 输入文档类型选择器在一个 LSI 工作区中的选择结果。
 */
class CompilerInputDocumentTypeSelection private constructor(
    val selectedTypeId: LsiSymbolId?,
    conflictingTypeIds: Collection<LsiSymbolId>,
) {

    val conflictingTypeIds: List<LsiSymbolId> = conflictingTypeIds.toList()

    val isAmbiguous: Boolean
        get() = conflictingTypeIds.isNotEmpty()

    init {
        require((selectedTypeId == null) == isAmbiguous) {
            "Compiler input type selection must be either selected or ambiguous"
        }
        require(conflictingTypeIds.size >= if (isAmbiguous) 2 else 0) {
            "Ambiguous compiler input type selection must contain at least two conflicts"
        }
    }

    companion object {

        internal fun selected(typeId: LsiSymbolId): CompilerInputDocumentTypeSelection =
            CompilerInputDocumentTypeSelection(typeId, emptyList())

        internal fun ambiguous(typeIds: Collection<LsiSymbolId>): CompilerInputDocumentTypeSelection =
            CompilerInputDocumentTypeSelection(null, typeIds)
    }
}

/**
 * 输入文档在解析时冻结的类型引用，不携带任何 APT 或 KSP 原生符号。
 */
data class CompilerInputDocumentReference(
    val typeSelector: CompilerInputDocumentTypeSelector,
    val kind: CompilerInputDocumentReferenceKind,
    val ownerTargetSelector: CompilerInputDocumentTypeSelector?,
    val location: LsiLocation,
) : Comparable<CompilerInputDocumentReference> {

    override fun compareTo(other: CompilerInputDocumentReference): Int {
        val sourceComparison = location.source.compareTo(other.location.source)
        if (sourceComparison != 0) {
            return sourceComparison
        }
        val startComparison = location.start.compareTo(other.location.start)
        if (startComparison != 0) {
            return startComparison
        }
        val kindComparison = kind.compareTo(other.kind)
        if (kindComparison != 0) {
            return kindComparison
        }
        val selectorComparison = typeSelector.compareTo(other.typeSelector)
        if (selectorComparison != 0) {
            return selectorComparison
        }
        return compareValues(ownerTargetSelector, other.ownerTargetSelector)
    }
}

/**
 * 把不可变输入内容和从该内容提取的引用绑定为同一份稳定快照。
 */
class CompilerInputDocumentSnapshot(
    val document: CompilerInputDocument,
    references: List<CompilerInputDocumentReference>,
) : Comparable<CompilerInputDocumentSnapshot> {

    val references: List<CompilerInputDocumentReference> = references.toList()

    val referencedTypeIds: Set<LsiSymbolId> = references
        .flatMapTo(sortedSetOf()) { reference ->
            reference.typeSelector.candidateTypeIds +
                reference.ownerTargetSelector?.candidateTypeIds.orEmpty()
        }

    val typeSeeds: List<LsiTypeSeed> = buildList {
        for (reference in references) {
            reference.typeSelector.candidateTypeIds.forEach { typeId ->
                add(LsiTypeSeed(typeId, reference.kind.seedMode))
            }
            reference.ownerTargetSelector?.candidateTypeIds?.forEach { typeId ->
                add(LsiTypeSeed(typeId, LsiTypeSeedMode.FULL_DECLARATION))
            }
        }
    }.mergeLsiTypeSeeds()

    init {
        require(references == references.sorted()) {
            "Compiler input document references must use stable source order"
        }
        require(references.distinct().size == references.size) {
            "Compiler input document snapshot cannot contain duplicate references"
        }
        require(references.all { reference -> reference.location.source == document.source }) {
            "Compiler input document reference location must use the document source"
        }
    }

    override fun compareTo(other: CompilerInputDocumentSnapshot): Int =
        document.compareTo(other.document)

    override fun equals(other: Any?): Boolean {
        return this === other ||
            other is CompilerInputDocumentSnapshot &&
            document == other.document &&
            references == other.references
    }

    override fun hashCode(): Int = 31 * document.hashCode() + references.hashCode()

    override fun toString(): String {
        return "CompilerInputDocumentSnapshot(document=$document, references=$references)"
    }
}

private val CompilerInputDocumentReferenceKind.seedMode: LsiTypeSeedMode
    get() = when (this) {
        CompilerInputDocumentReferenceKind.SUBJECT_TYPE,
        CompilerInputDocumentReferenceKind.TARGET_TYPE,
        CompilerInputDocumentReferenceKind.ANNOTATION_TYPE,
        CompilerInputDocumentReferenceKind.SUPER_TYPE,
        CompilerInputDocumentReferenceKind.MODEL_TYPE,
        CompilerInputDocumentReferenceKind.REUSABLE_DTO_TYPE,
        CompilerInputDocumentReferenceKind.CONFIG_IMPLEMENTATION,
        -> LsiTypeSeedMode.FULL_DECLARATION
        CompilerInputDocumentReferenceKind.TYPE_USAGE -> LsiTypeSeedMode.HEADER
    }
