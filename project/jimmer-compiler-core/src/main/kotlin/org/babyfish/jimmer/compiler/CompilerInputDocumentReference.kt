package org.babyfish.jimmer.compiler

import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiTypeSeedMode
import site.addzero.lsi.model.mergeLsiTypeSeeds

enum class CompilerInputDocumentReferenceKind {
    SUBJECT_TYPE,
    ANNOTATION_TYPE,
    SUPER_TYPE,
    MODEL_TYPE,
    TYPE_USAGE,
    CONFIG_IMPLEMENTATION,
}

/**
 * 输入文档在解析时冻结的类型引用，不携带任何 APT 或 KSP 原生符号。
 */
data class CompilerInputDocumentReference(
    val typeId: LsiSymbolId,
    val kind: CompilerInputDocumentReferenceKind,
    val location: LsiLocation,
) : Comparable<CompilerInputDocumentReference> {

    init {
        typeId.requireTypeQualifiedName()
    }

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
        return typeId.compareTo(other.typeId)
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
        .mapTo(sortedSetOf()) { reference -> reference.typeId }

    val typeSeeds: List<LsiTypeSeed> = references
        .map { reference ->
            LsiTypeSeed(
                typeId = reference.typeId,
                mode = when (reference.kind) {
                    CompilerInputDocumentReferenceKind.SUBJECT_TYPE,
                    CompilerInputDocumentReferenceKind.ANNOTATION_TYPE,
                    CompilerInputDocumentReferenceKind.SUPER_TYPE,
                    CompilerInputDocumentReferenceKind.MODEL_TYPE,
                    CompilerInputDocumentReferenceKind.CONFIG_IMPLEMENTATION,
                    -> LsiTypeSeedMode.FULL_DECLARATION
                    CompilerInputDocumentReferenceKind.TYPE_USAGE -> LsiTypeSeedMode.HEADER
                },
            )
        }
        .mergeLsiTypeSeeds()

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
