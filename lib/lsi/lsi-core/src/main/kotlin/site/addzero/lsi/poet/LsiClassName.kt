package site.addzero.lsi.poet

import site.addzero.lsi.assist.TypeChecker.toPkg
import site.addzero.lsi.assist.TypeChecker.toSimpleName

/**
 * 语言无关的类名模型，用于跨 JavaPoet/KotlinPoet 传递类型名语义。
 */
data class LsiClassName(
    val packageName: String,
    val simpleNames: List<String>,
    override val nullable: Boolean = false,
) : LsiTypeName {

    init {
        require(simpleNames.isNotEmpty()) { "simpleNames must not be empty" }
    }

    val simpleName: String
        get() = simpleNames.last()

    val canonicalName: String
        get() = when {
            packageName.isEmpty() -> simpleNames.joinToString(".")
            else -> "$packageName.${simpleNames.joinToString(".")}"
        }

    override fun copyNullable(nullable: Boolean): LsiClassName =
        if (this.nullable == nullable) this else copy(nullable = nullable)

    fun nested(vararg nestedNames: String): LsiClassName =
        copy(simpleNames = simpleNames + nestedNames)

    override fun toString(): String =
        canonicalName + if (nullable) "?" else ""

    companion object {
        fun bestGuess(qualifiedName: String, nullable: Boolean = false): LsiClassName {
            val packageName = qualifiedName.toPkg()
            val simpleName = qualifiedName.toSimpleName()
            return LsiClassName(
                packageName = packageName,
                simpleNames = listOf(simpleName),
                nullable = nullable
            )
        }
    }
}
