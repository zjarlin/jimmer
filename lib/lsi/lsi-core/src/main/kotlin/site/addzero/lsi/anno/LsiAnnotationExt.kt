package site.addzero.lsi.anno

import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.method.LsiMethod
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

val LsiAnnotation.fullName: String
    get() = qualifiedName ?: ""

@Suppress("UNCHECKED_CAST")
operator fun <T> LsiAnnotation.get(annoProp: KProperty1<out Annotation, T>): T? =
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../LsiClientSchemaTraversal @Api groups 读取
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../DocMetadata Description value 读取
    // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator family/field 属性读取
    // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator Document/allowedTargets 属性读取
    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableProp / ValidationGenerator / DraftImplGenerator 注解属性读取
    // 迁移说明：LsiAnnotation 标量属性读取上收至 lsi-core，compiler 不再依赖旧 `org.babyfish.jimmer.ksp.get`
    attributes[annoProp.name] as T?

@Suppress("UNCHECKED_CAST")
operator fun <T> LsiAnnotation.get(name: String): T? =
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../LsiClientSchemaTraversal / DocMetadata
    // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator
    // 迁移说明：支持按属性名读取 LSI 注解值，替代旧 KSP 工具包中的同名扩展
    attributes[name] as T?

fun LsiAnnotation.getClassArgument(annoProp: KProperty1<out Annotation, KClass<*>>): LsiClass? =
    getClassArgument(annoProp.name)

fun LsiAnnotation.getClassArgument(name: String): LsiClass? =
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.determineFetchBy ownerType/defaultFetcherOwner
    // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator error field type
    // 覆盖来源：project/compiler/transactional/jimmer-ksp-transactional/.../TxGenerator TargetAnnotation.value
    // 迁移说明：类参数读取语义直接下沉到 lsi-core，只接受 `KSP/APT -> LSI` 适配后的 `LsiClass`
    attributes[name] as? LsiClass

@Suppress("UNCHECKED_CAST")
fun <T> LsiAnnotation.getListArgument(annoProp: KProperty1<out Annotation, Array<out T>>): List<T>? =
    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType formula dependencies 读取
    // 迁移说明：数组参数读取迁移到 lsi-core，避免 compiler 继续从旧 ksp utils 引入该语义
    attributes[annoProp.name] as? List<T>

@Suppress("UNCHECKED_CAST")
fun <T> LsiAnnotation.getListArgument(name: String): List<T>? =
    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/meta/ImmutableType formula dependencies 读取
    // 迁移说明：支持按名称读取数组参数，供不直接引用注解类字面量的路径复用
    attributes[name] as? List<T>

fun LsiAnnotation.getClassListArgument(annoProp: KProperty1<out Annotation, Array<out KClass<*>>>): List<LsiClass> =
    getClassListArgument(annoProp.name)

fun LsiAnnotation.getClassListArgument(name: String): List<LsiClass> =
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.getExceptionTypeNames
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientExceptionContext.create/initSubMetadatas
    // 迁移说明：类数组参数读取下沉到 lsi-core，移除 compiler 对旧 `org.babyfish.jimmer.ksp.getClassListArgument` 的依赖
    (attributes[name] as? List<*>)
        ?.mapNotNull { it as? LsiClass }
        ?: emptyList()

inline fun <reified E : Enum<E>> LsiAnnotation.getEnumListArgument(
    annoProp: KProperty1<out Annotation, Array<out E>>
): List<E> {
    // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoGenerator.allowedTargets Kotlin/Java @Target 读取
    // 迁移说明：枚举数组参数读取改由 lsi-core 提供，避免 DTO/后续 APT 继续借用旧 KSP utils
    val list = attributes[annoProp.name] as? List<Any> ?: return emptyList()
    return list.map { value ->
        val name = value.toString()
        val lastIndex = name.lastIndexOf('.')
        enumValueOf(if (lastIndex == -1) name else name.substring(lastIndex + 1))
    }
}

fun List<LsiAnnotation>.recursiveAnnotation(
    annotationTypeName: String,
    sourceDeclaration: Any? = null,
): LsiAnnotation? {
    val stack = ArrayDeque<String>()
    var foundPath: List<String> = emptyList()
    var foundAnnotation: LsiAnnotation? = null

    fun declared(path: List<String>): String =
        if (path.isEmpty()) {
            "is declared directly"
        } else {
            "is declared as nest annotation of $path"
        }

    fun fail(reason: String): Nothing =
        when (sourceDeclaration) {
            is LsiClass -> throw MetaException(sourceDeclaration, reason)
            is LsiField -> throw MetaException(sourceDeclaration, reason)
            is LsiMethod -> throw MetaException(sourceDeclaration, reason)
            null -> error(reason)
            else -> error("recursiveAnnotation sourceDeclaration must be null/LsiClass/LsiField/LsiMethod")
        }

    fun visit(annotation: LsiAnnotation) {
        val qualifiedName = annotation.fullName
        if (qualifiedName.isEmpty()) {
            return
        }
        if (qualifiedName == annotationTypeName) {
            if (foundAnnotation != null && foundAnnotation !== annotation) {
                fail(
                    "Conflict annotation \"@$annotationTypeName\" one " +
                        declared(foundPath) +
                        " and the other one " +
                        declared(stack.toList())
                )
            }
            foundAnnotation = annotation
            foundPath = stack.toList()
            return
        }
        if (stack.contains(qualifiedName)) {
            return
        }
        stack.addLast(qualifiedName)
        for (subAnnotation in annotation.annotations) {
            visit(subAnnotation)
        }
        stack.removeLast()
    }

    for (annotation in this) {
        visit(annotation)
    }
    return foundAnnotation
}
