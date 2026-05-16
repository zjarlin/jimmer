package site.addzero.lsi.poet

import kotlin.jvm.JvmOverloads

data class LsiPropertySpec @JvmOverloads constructor(
    val name: String,
    val type: LsiTypeName,
    val receiverType: LsiTypeName? = null,
    val annotations: List<LsiAnnotationSpec> = emptyList(),
    val modifiers: Set<LsiModifier> = emptySet(),
    val mutable: Boolean = false,
    val initializer: LsiExpression? = null,
    val getterStatements: List<LsiStatement> = emptyList(),
    val setterStatements: List<LsiStatement> = emptyList(),
    val backingFieldModifiers: Set<LsiModifier> = emptySet(),
) {

    init {
        require(initializer == null || (getterStatements.isEmpty() && setterStatements.isEmpty())) {
            "Property cannot define both initializer and accessor statements"
        }
        require(!setterStatements.isNotEmpty() || mutable) {
            "Property cannot define setterStatements unless mutable is true"
        }
    }
}
