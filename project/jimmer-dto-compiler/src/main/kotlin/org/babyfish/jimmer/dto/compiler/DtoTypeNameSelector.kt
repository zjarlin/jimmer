package org.babyfish.jimmer.dto.compiler

import java.util.function.Predicate

/**
 * 在类型存在性未知时冻结导入解析顺序。
 */
class DtoTypeNameSelector private constructor(
    val sourceName: String,
    val fallbackQualifiedName: String,
    wildcardQualifiedNames: Collection<String>,
    val checksFallbackExistence: Boolean,
) : Comparable<DtoTypeNameSelector> {

    val wildcardQualifiedNames: List<String> = wildcardQualifiedNames
        .asSequence()
        .filter { qualifiedName -> qualifiedName != fallbackQualifiedName }
        .distinct()
        .toList()

    val candidateQualifiedNames: List<String> =
        listOf(fallbackQualifiedName) + this.wildcardQualifiedNames

    init {
        require(sourceName.isNotBlank()) { "DTO type selector source name cannot be blank" }
        require(fallbackQualifiedName.isNotBlank()) {
            "DTO type selector fallback name cannot be blank"
        }
        require(this.wildcardQualifiedNames.all(String::isNotBlank)) {
            "DTO type selector wildcard name cannot be blank"
        }
    }

    /**
     * 按正式 DTO 导入规则选择名称，同时保留通配符歧义。
     */
    fun select(typeExists: Predicate<String>): DtoTypeNameSelection {
        if (!checksFallbackExistence || typeExists.test(fallbackQualifiedName)) {
            return DtoTypeNameSelection.selected(fallbackQualifiedName)
        }
        val matches = wildcardQualifiedNames.filter(typeExists::test)
        return when (matches.size) {
            0 -> DtoTypeNameSelection.selected(fallbackQualifiedName)
            1 -> DtoTypeNameSelection.selected(matches.single())
            else -> DtoTypeNameSelection.ambiguous(matches)
        }
    }

    override fun compareTo(other: DtoTypeNameSelector): Int {
        val sourceComparison = sourceName.compareTo(other.sourceName)
        if (sourceComparison != 0) {
            return sourceComparison
        }
        val fallbackComparison = fallbackQualifiedName.compareTo(other.fallbackQualifiedName)
        if (fallbackComparison != 0) {
            return fallbackComparison
        }
        val fallbackCheckComparison = checksFallbackExistence.compareTo(other.checksFallbackExistence)
        if (fallbackCheckComparison != 0) {
            return fallbackCheckComparison
        }
        val commonSize = minOf(wildcardQualifiedNames.size, other.wildcardQualifiedNames.size)
        for (index in 0 until commonSize) {
            val candidateComparison = wildcardQualifiedNames[index]
                .compareTo(other.wildcardQualifiedNames[index])
            if (candidateComparison != 0) {
                return candidateComparison
            }
        }
        return wildcardQualifiedNames.size.compareTo(other.wildcardQualifiedNames.size)
    }

    override fun equals(other: Any?): Boolean {
        return this === other ||
            other is DtoTypeNameSelector &&
            sourceName == other.sourceName &&
            fallbackQualifiedName == other.fallbackQualifiedName &&
            checksFallbackExistence == other.checksFallbackExistence &&
            wildcardQualifiedNames == other.wildcardQualifiedNames
    }

    override fun hashCode(): Int =
        31 * (
            31 * (31 * sourceName.hashCode() + fallbackQualifiedName.hashCode()) +
                checksFallbackExistence.hashCode()
            ) + wildcardQualifiedNames.hashCode()

    override fun toString(): String =
        "$sourceName -> ${candidateQualifiedNames.joinToString(prefix = "[", postfix = "]")}"

    companion object {

        @JvmStatic
        @JvmOverloads
        fun exact(
            qualifiedName: String,
            sourceName: String = qualifiedName,
        ): DtoTypeNameSelector =
            DtoTypeNameSelector(sourceName, qualifiedName, emptyList(), false)

        /**
         * 规划显式导入、默认包和通配符导入的共同解析顺序。
         */
        @JvmStatic
        fun plan(
            qualifiedName: String,
            defaultPackageName: String,
            importedTypes: Map<String, String>,
            wildcardPackageNames: Collection<String>,
        ): DtoTypeNameSelector {
            require(qualifiedName.isNotBlank()) { "DTO type name cannot be blank" }
            val separatorIndex = qualifiedName.indexOf('.')
            val firstPart = if (separatorIndex == -1) {
                qualifiedName
            } else {
                qualifiedName.substring(0, separatorIndex)
            }
            val importedType = importedTypes[firstPart]
            if (importedType != null) {
                return exact(
                    qualifiedName = importedType + qualifiedName.removePrefix(firstPart),
                    sourceName = qualifiedName,
                )
            }
            if (Character.isLowerCase(qualifiedName[0])) {
                return exact(qualifiedName = qualifiedName, sourceName = qualifiedName)
            }
            val fallbackQualifiedName = qualify(defaultPackageName, qualifiedName)
            return DtoTypeNameSelector(
                qualifiedName,
                fallbackQualifiedName,
                wildcardPackageNames.map { packageName -> qualify(packageName, qualifiedName) },
                true,
            )
        }

        private fun qualify(packageName: String, name: String): String {
            return if (packageName.isEmpty()) name else "$packageName.$name"
        }
    }
}

/**
 * 一次带类型存在性判断的导入选择结果。
 */
class DtoTypeNameSelection private constructor(
    val selectedQualifiedName: String?,
    conflictingQualifiedNames: Collection<String>,
) {

    val conflictingQualifiedNames: List<String> = conflictingQualifiedNames.toList()

    val isAmbiguous: Boolean
        get() = conflictingQualifiedNames.isNotEmpty()

    init {
        require((selectedQualifiedName == null) == isAmbiguous) {
            "DTO type name selection must be either selected or ambiguous"
        }
        require(conflictingQualifiedNames.size >= if (isAmbiguous) 2 else 0) {
            "Ambiguous DTO type name selection must contain at least two conflicts"
        }
    }

    companion object {

        internal fun selected(qualifiedName: String): DtoTypeNameSelection =
            DtoTypeNameSelection(qualifiedName, emptyList())

        internal fun ambiguous(qualifiedNames: Collection<String>): DtoTypeNameSelection =
            DtoTypeNameSelection(null, qualifiedNames)
    }
}
