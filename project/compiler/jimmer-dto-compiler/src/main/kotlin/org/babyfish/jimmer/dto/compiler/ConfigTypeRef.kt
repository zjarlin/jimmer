package org.babyfish.jimmer.dto.compiler

data class ConfigTypeRef(
    val qualifiedName: String,
    val line: Int,
    val column: Int,
) {
    init {
        require(qualifiedName.isNotBlank()) { "DTO config type name cannot be blank" }
        require(line >= 1) { "DTO config type line must be positive: $line" }
        require(column >= 1) { "DTO config type column must be positive: $column" }
    }
}
