package site.addzero.lsi.jimmer.client

import org.babyfish.jimmer.client.meta.TypeName
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.poet.LsiArrayTypeName
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiLambdaTypeName
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiStarTypeName
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiTypeVariableName
import site.addzero.lsi.poet.LsiWildcardTypeName
import site.addzero.lsi.poet.isLsiCollectionLikeQualifiedName
import site.addzero.lsi.resolver.LsiResolver
import site.addzero.lsi.type.LsiType

internal fun LsiClass.toClientTypeName(): TypeName {
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../LsiClientSchemaTraversal.toClientTypeName
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.toClientTypeName
    // 迁移说明：client TypeName 推导规则收敛为单一 LSI helper，消除 traversal/processor 间的重复实现
    val names = simpleNames.ifEmpty {
        listOf(simpleName ?: qualifiedName?.substringAfterLast('.') ?: error("Cannot resolve type name for class"))
    }
    return TypeName.of(packageName ?: "", names)
}

internal fun LsiType.toClientTypeName(): TypeName {
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.toClientTypeName
    // 迁移说明：LSI 类型到 client TypeName 的转换收敛到共享 helper，避免处理器内重复保留命名推导规则
    val classValue = lsiClass
    if (classValue != null) {
        return classValue.toClientTypeName()
    }
    val qualified = qualifiedName
    if (qualified != null) {
        val simple = simpleName ?: qualified.substringAfterLast('.')
        val pkg = qualified.substringBeforeLast('.', "")
        return TypeName.of(pkg, listOf(simple))
    }
    val simple = simpleName ?: "Object"
    return TypeName.of("java.lang", listOf(simple))
}

internal fun declaredClientFields(owner: LsiClass): List<LsiField> =
    owner.fields.filter { field ->
        val declaringClass = field.declaringClass
        declaringClass != null && sameClientTypeName(declaringClass.qualifiedName, owner.qualifiedName)
    }

internal fun declaredClientMethods(owner: LsiClass): List<LsiMethod> =
    owner.methods.filter { method ->
        val declaringClass = method.declaringClass
        declaringClass != null && sameClientTypeName(declaringClass.qualifiedName, owner.qualifiedName)
    }

internal fun sameClientTypeName(left: String?, right: String?): Boolean =
    left?.removeSuffix("?") == right?.removeSuffix("?")

internal fun LsiClass.clientFullName(): String =
    qualifiedName ?: simpleName ?: "<unknown>"

internal fun LsiClass.asClientLsiType(nullable: Boolean): LsiType =
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.asType
    // 迁移说明：最小 LSI 类型包装继续保留在 client 语义层，避免 materialization helper 反向依赖处理器私有实现
    ClientSimpleLsiType(
        simpleName = simpleName,
        qualifiedName = qualifiedName,
        presentableText = qualifiedName,
        isNullableValue = nullable,
        isPrimitiveValue = false,
        lsiClassValue = this,
        typeParametersValue = emptyList(),
        componentTypeValue = null,
        isArrayValue = false
    )

internal fun simpleClientLsiType(
    qualifiedName: String,
    nullable: Boolean
): LsiType =
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.SimpleLsiType
    // 迁移说明：converter 目标类型的最小 LSI 包装下沉到共享 helper，避免处理器入口继续持有匿名/私有 LSI 类型实现
    ClientSimpleLsiType(
        simpleName = qualifiedName.substringAfterLast('.'),
        qualifiedName = qualifiedName,
        presentableText = qualifiedName,
        isNullableValue = nullable,
        isPrimitiveValue = TypeName
            .of(qualifiedName.substringBeforeLast('.', ""), listOf(qualifiedName.substringAfterLast('.')))
            .isPrimitive,
        lsiClassValue = null,
        typeParametersValue = emptyList(),
        componentTypeValue = null,
        isArrayValue = false
    )

internal fun LsiTypeName.toClientLsiType(resolver: LsiResolver): LsiType =
    when (this) {
        is LsiClassName ->
            resolver.findClassByQualifiedName(canonicalName)?.asClientLsiType(nullable)
                ?: simpleClientLsiType(canonicalName, nullable)
        is LsiParameterizedTypeName -> {
            val rawClass = resolver.findClassByQualifiedName(rawType.canonicalName)
            ClientSimpleLsiType(
                simpleName = rawType.simpleName,
                qualifiedName = rawType.canonicalName,
                presentableText = toString(),
                isNullableValue = nullable,
                isPrimitiveValue = false,
                lsiClassValue = rawClass,
                typeParametersValue = typeArguments.map { it.toClientLsiType(resolver) },
                componentTypeValue = null,
                isArrayValue = false
            )
        }
        is LsiArrayTypeName ->
            ClientSimpleLsiType(
                simpleName = "Array",
                qualifiedName = "kotlin.Array",
                presentableText = toString(),
                isNullableValue = nullable,
                isPrimitiveValue = false,
                lsiClassValue = null,
                typeParametersValue = emptyList(),
                componentTypeValue = componentType.toClientLsiType(resolver),
                isArrayValue = true
            )
        is LsiLambdaTypeName -> simpleClientLsiType("kotlin.Function", nullable)
        is LsiTypeVariableName -> simpleClientLsiType(name, nullable)
        is LsiWildcardTypeName -> simpleClientLsiType("kotlin.Any", nullable)
        LsiStarTypeName -> simpleClientLsiType("kotlin.Any", false)
    }

private data class ClientSimpleLsiType(
    override val simpleName: String?,
    override val qualifiedName: String?,
    override val presentableText: String?,
    val isNullableValue: Boolean,
    val isPrimitiveValue: Boolean,
    val lsiClassValue: LsiClass?,
    val typeParametersValue: List<LsiType>,
    val componentTypeValue: LsiType?,
    val isArrayValue: Boolean,
) : LsiType {
    override val annotations: List<LsiAnnotation> = emptyList()
    override val isCollectionType: Boolean = qualifiedName.isLsiCollectionLikeQualifiedName()
    override val isNullable: Boolean = isNullableValue
    override val typeParameters: List<LsiType> = typeParametersValue
    override val isPrimitive: Boolean = isPrimitiveValue
    override val componentType: LsiType? = componentTypeValue
    override val isArray: Boolean = isArrayValue
    override val lsiClass: LsiClass? = lsiClassValue
}
