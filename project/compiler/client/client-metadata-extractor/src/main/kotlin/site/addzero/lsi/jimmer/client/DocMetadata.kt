package site.addzero.lsi.jimmer.client

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.get
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.doc.LsiDoc
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.jimmer.DESCRIPTION
import site.addzero.lsi.method.LsiMethod

class DocMetadata(
    private val draftImplDocMapProvider: (LsiClass, String, String) -> Map<String, String> =
        { _, _, _ -> emptyMap() }
) {
    private val docMap = mutableMapOf<String, String>()

    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.handleService/fillDefinition/fillEnumDefinition
    // 迁移说明：文档元数据出口先返回 LSI 文档对象，最终 runtime `Doc` 转换延后到 client schema 装配边界
    fun getDoc(type: LsiClass): LsiDoc? =
        getString(type)?.let { LsiDoc.parse(it) }

    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.determineFetchBy/fillDefinition
    // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.baseDocString(prop)
    // 迁移说明：字段文档同样先保留为 LSI 文档对象，DTO/client 两侧按需再做目标模型转换
    fun getDoc(field: LsiField): LsiDoc? =
        getString(field)?.let { LsiDoc.parse(it) }

    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.handleOperation
    // 迁移说明：方法文档解析改为复用 LSI 文档 parse，避免 compiler 直接依赖 jimmer runtime `Doc.parse`
    fun getDoc(method: LsiMethod): LsiDoc? =
        getString(method)?.let { LsiDoc.parse(it) }

    fun getString(type: LsiClass): String? =
        getStringImpl(type).takeIf { it.isNotBlank() }

    fun getString(field: LsiField): String? =
        getStringImpl(field).takeIf { it.isNotBlank() }

    fun getString(method: LsiMethod): String? =
        getStringImpl(method).takeIf { it.isNotBlank() }

    private fun getStringImpl(type: LsiClass): String {
        val key = classKey(type)
        val existing = docMap[key]
        if (existing !== null) {
            return existing
        }
        val docString = type.comment?.takeIf { it.isNotBlank() }
        if (docString != null) {
            return docString.also {
                docMap[key] = it
            }
        }
        val descriptionString = descriptionValue(type.annotations)
        if (descriptionString != null) {
            return descriptionString.also {
                docMap[key] = it
            }
        }
        val map = implDocStringMap(type)
        if (map.isNotEmpty()) {
            map[""]?.let {
                docMap[key] = it
            }
            addPropDocs(type, map)
        }
        return docMap[key] ?: "".also {
            docMap[key] = it
        }
    }

    private fun getStringImpl(field: LsiField): String {
        val key = fieldKey(field)
        val existing = docMap[key]
        if (existing !== null) {
            return existing
        }
        val docString = field.comment?.takeIf { it.isNotBlank() }
        if (docString != null) {
            return docString.also { docMap[key] = it }
        }
        val descriptionString = descriptionValue(field.annotations)
        if (descriptionString != null) {
            return descriptionString.also { docMap[key] = it }
        }
        val owner = field.declaringClass
        if (owner != null) {
            val map = implDocStringMap(owner)
            if (map.isNotEmpty()) {
                map[""]?.let { docMap[classKey(owner)] = it }
                addPropDocs(owner, map)
            }
        }
        return docMap[key] ?: "".also { docMap[key] = it }
    }

    private fun getStringImpl(method: LsiMethod): String {
        val key = methodKey(method)
        val existing = docMap[key]
        if (existing != null) {
            return existing
        }
        val docString = method.comment?.takeIf { it.isNotBlank() }
        if (docString != null) {
            return docString.also {
                docMap[key] = it
            }
        }
        val descriptionString = descriptionValue(method.annotations)
        if (descriptionString != null) {
            return descriptionString.also {
                docMap[key] = it
            }
        }
        return "".also {
            docMap[key] = it
        }
    }

    private fun addPropDocs(type: LsiClass, map: Map<String, String>) {
        for (field in type.fields) {
            field.name?.let { fieldName ->
                map[fieldName]?.let { docMap[fieldKey(field)] = it }
            }
        }
        for (superType in type.superClasses) {
            addPropDocs(superType, map)
        }
    }

    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../client/DocMetadata.implDocStringMap（原 KSP 逻辑）
    // - getClassDeclarationByName("${qualifiedName}Draft")
    // - "$"/"Impl" 内部类扫描
    // - Impl public 且非 internal 属性的 Description 提取
    private fun implDocStringMap(type: LsiClass): Map<String, String> =
        // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../DocMetadata.implDocStringMap 的 `type.findKspDraftImplDocMap(...)`
        // 覆盖来源：project/jimmer-apt/.../client/DocMetadata.implDocStringMap
        // 迁移说明：draft Impl 文档扫描改为由 processor 显式注入 callback，
        // 从而把 client metadata extractor 从 KSP 专属 Context 脱钩，允许 APT/KSP 共用同一提取器
        draftImplDocMapProvider(type, DESCRIPTION, VALUE_ATTRIBUTE)

    private fun classKey(type: LsiClass): String =
        "class:${type.qualifiedName ?: type.simpleName ?: "<unknown>"}"

    private fun fieldKey(field: LsiField): String =
        "field:${field.declaringClass?.qualifiedName ?: "<unknown>"}.${field.name ?: "<unknown>"}"

    private fun methodKey(method: LsiMethod): String =
        "method:${method.declaringClass?.qualifiedName ?: "<unknown>"}.${method.name ?: "<unknown>"}"

    private fun descriptionValue(annotations: List<LsiAnnotation>): String? =
        // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../DocMetadata Description 注解读取
        // 迁移说明：Description 读取改为 LSI 注解 FQ + attributes 访问，移除对 `Description::class`/`Description::value` 的直接依赖
        annotations
            .firstOrNull { it.qualifiedName == DESCRIPTION }
            ?.get<String>(VALUE_ATTRIBUTE)
            ?.takeIf { it.isNotBlank() }

    companion object {
        private const val VALUE_ATTRIBUTE = "value"
    }
}
