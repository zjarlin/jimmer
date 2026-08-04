package site.addzero.lsi.model

import site.addzero.lsi.core.LsiSymbolId

/**
 * 编译器前端冻结平台符号时使用的通用约定。
 *
 * 该模型只描述 LSI 前端能力，不包含任何具体框架的注解或编译参数名称。
 */
data class LsiFrontendOptions(
    val keepJavaBooleanGetterIsPrefix: Boolean = false,
    val nullableAnnotationTypeIds: Set<LsiSymbolId> = emptySet(),
    val fullExternalDeclarationAnnotationTypeIds: Set<LsiSymbolId> = emptySet(),
    val documentationConvention: LsiFrontendDocumentationConvention? = null,
)

/**
 * 从注解及生成的同伴类型恢复文档时使用的平台中立约定。
 */
data class LsiFrontendDocumentationConvention(
    val annotationTypeId: LsiSymbolId,
    val valueMemberName: String = "value",
    val generatedPeer: LsiGeneratedPeerDocumentationConvention? = null,
) {
    init {
        require(valueMemberName.isNotBlank()) {
            "LSI documentation annotation value member name cannot be blank"
        }
    }
}

/**
 * 从生成的同伴类型恢复文档时使用的平台中立约定。
 */
data class LsiGeneratedPeerDocumentationConvention(
    val ownerAnnotationTypeIds: Set<LsiSymbolId>,
    val typeSuffix: String,
    val propertySetterPrefix: String = "set",
) {
    init {
        require(ownerAnnotationTypeIds.isNotEmpty()) {
            "LSI documentation generated peer owner annotations cannot be empty"
        }
        require(typeSuffix.isNotBlank()) {
            "LSI documentation generated peer type suffix cannot be blank"
        }
        require(propertySetterPrefix.isNotBlank()) {
            "LSI documentation generated peer property setter prefix cannot be blank"
        }
    }
}
