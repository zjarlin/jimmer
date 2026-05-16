package site.addzero.lsi.dto

interface LsiDtoBaseProp {
    val name: String
    val isNullable: Boolean
    val isList: Boolean
    val isReference: Boolean
    val isFormula: Boolean
    val isTransient: Boolean
    val idViewBaseProp: LsiDtoBaseProp?
    val manyToManyViewBaseProp: LsiDtoBaseProp?
    val isId: Boolean
    val isKey: Boolean
    val isRecursive: Boolean
    val isEmbedded: Boolean
    val isLogicalDeleted: Boolean
    val isExcludedFromAllScalars: Boolean
    fun isAssociation(entityLevel: Boolean): Boolean
    fun hasTransientResolver(): Boolean
}
