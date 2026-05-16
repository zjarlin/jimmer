package site.addzero.lsi.jimmer.client

import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.anno.get
import site.addzero.lsi.anno.getClassListArgument
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.annotation
import site.addzero.lsi.jimmer.CODE_BASED_EXCEPTION
import site.addzero.lsi.jimmer.CODE_BASED_RUNTIME_EXCEPTION
import site.addzero.lsi.jimmer.CLIENT_EXCEPTION
import site.addzero.lsi.jimmer.DEFAULT_ERROR_FAMILY

class ClientExceptionContext {

    private val metadataMap = mutableMapOf<LsiClass, ClientExceptionMetadata>()

    // 覆盖来源：ClientExceptionContext.create 中错误族/错误码去重表，声明类型从 KSDeclaration 切到 LsiClass
    private val nonAbstractDeclarationMap = mutableMapOf<Key, LsiClass>()

    operator fun get(declaration: LsiClass): ClientExceptionMetadata =
        metadataMap[declaration] ?:
            create(declaration).also {
                metadataMap[declaration] = it
                try {
                    initSubMetadatas(it)
                } catch (ex: Throwable) {
                    metadataMap.remove(declaration)
                    throw ex
                }
            }

    private fun create(declaration: LsiClass): ClientExceptionMetadata {
        // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientExceptionContext.create @ClientException 读取
        // 迁移说明：ClientException 判定改为 LSI 注解 FQ 常量 + attributes 访问，移除对 `ClientException::class` 的编译期依赖
        val annotation = declaration.annotation(CLIENT_EXCEPTION)
            ?: throw MetaException(
                declaration,
                // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientExceptionContext.create CodeBased* 装饰报错文案
                // 迁移说明：异常基类名称改为复用 lsi-jimmer 常量，避免消息构造继续依赖 runtime class literal
                "the exception type extends \"" +
                    CODE_BASED_EXCEPTION +
                    "\" or \"" +
                    CODE_BASED_RUNTIME_EXCEPTION +
                    "\" must be decorated by \"@" +
                    CLIENT_EXCEPTION +
                    "\""
            )
        val code = annotation.get<String>(CODE_ATTRIBUTE)?.takeIf { it.isNotEmpty() }
        val subTypes = annotation.getClassListArgument(SUB_TYPES_ATTRIBUTE)
        if (code === null && subTypes.isEmpty()) {
            throw MetaException(
                declaration,
                "it is decorated by @\"" +
                    CLIENT_EXCEPTION +
                    "\" but neither \"code\" nor \"subTypes\" of the annotation is specified"
            )
        }
        if (code !== null && subTypes.isNotEmpty()) {
            throw MetaException(
                declaration,
                ("it is decorated by @\"" +
                    CLIENT_EXCEPTION +
                    "\" but both \"code\" and \"subTypes\" of the annotation are specified")
            )
        }
        if (code !== null && declaration.isAbstract) {
            throw MetaException(
                declaration,
                "it is decorated by @\"" +
                    CLIENT_EXCEPTION +
                    "\" and the \"code\" of the annotation is specified so that " +
                    "it cannot be abstract"
            )
        }
        if (subTypes.isNotEmpty() && !declaration.isAbstract) {
            throw MetaException(
                declaration,
                ("it is decorated by @\"" +
                    CLIENT_EXCEPTION +
                    "\" and the \"subTypes\" of the annotation is specified so that " +
                    "it must be abstract")
            )
        }
        // 覆盖来源：ClientExceptionContext.create 原 declaration.superTypes + ClassKind.CLASS 路径
        val superDeclaration = declaration.firstClassSuperType()
        var superMetadata: ClientExceptionMetadata? = null
        if (superDeclaration != null &&
            // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientExceptionContext.create CodeBased* 父类判定
            // 迁移说明：异常基类识别改为复用 lsi-jimmer 常量，移除该语义路径对 `CodeBasedException::*` class literal 的依赖
            superDeclaration.qualifiedName != CODE_BASED_EXCEPTION &&
            superDeclaration.qualifiedName != CODE_BASED_RUNTIME_EXCEPTION) {
            val superAnnotation = superDeclaration.annotation(CLIENT_EXCEPTION)
            if (superAnnotation !== null) {
                if (!superAnnotation.getClassListArgument(SUB_TYPES_ATTRIBUTE).any { it.qualifiedName == declaration.qualifiedName }) {
                    throw MetaException(
                        declaration,
                        "its super type \"" +
                            superDeclaration.displayName() +
                            "\" is decorated by " +
                            CLIENT_EXCEPTION +
                            "\" but the \"subTypes\" of the annotation does not contain current type"
                    )
                }
                superMetadata = get(superDeclaration)
            }
        }
        val family: String = annotation.get<String>(FAMILY_ATTRIBUTE)?.takeIf { it.isNotEmpty() }
            ?: superMetadata?.family
            // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientExceptionContext.create 默认 family
            // 迁移说明：默认错误族名称收敛为 lsi-jimmer 常量，避免散落魔法字符串
            ?: DEFAULT_ERROR_FAMILY
        if (superMetadata != null && superMetadata.family != family) {
            throw MetaException(
                declaration,
                "Its family is \"" +
                    family +
                    "\" but the family of super exception is \"" +
                    superMetadata.family +
                    "\""
            )
        }
        code?.let {
            nonAbstractDeclarationMap.put(Key(family, it), declaration)
                ?.takeIf { conflictDeclaration -> conflictDeclaration.qualifiedName != declaration.qualifiedName }
                ?.let { conflictDeclaration ->
                throw MetaException(
                    declaration,
                    "Duplicated error family \"" +
                        family +
                        "\" and code \"" +
                        code +
                        "\", it is used by another exception type \"" +
                        conflictDeclaration.displayName() +
                        "\""
                )
            }
        }
        return ClientExceptionMetadata(
            declaration,
            family,
            code,
            superMetadata
        )
    }

    private fun initSubMetadatas(metadata: ClientExceptionMetadata) {
        val annotation = metadata.declaration.annotation(CLIENT_EXCEPTION)!!
        val subTypes = annotation.getClassListArgument(SUB_TYPES_ATTRIBUTE)
        for (subType in subTypes) {
            // 覆盖来源：ClientExceptionContext.initSubMetadatas 原 subType.superTypes + ClassKind.CLASS 回溯路径
            val backRefDeclaration = subType.firstClassSuperType()
            if (backRefDeclaration?.qualifiedName != metadata.declaration.qualifiedName) {
                throw MetaException(
                    metadata.declaration,
                    "it is decorated by \"@$CLIENT_EXCEPTION\" " +
                        "which specifies the sub type \"${subType.qualifiedName ?: ""}\", " +
                        "but the super type of that sub type is not current type"
                )
            }
            if (subType.annotation(CLIENT_EXCEPTION) == null) {
                throw MetaException(
                    metadata.declaration,
                    "it is decorated by \"@$CLIENT_EXCEPTION\" " +
                        "which specifies the sub type \"${subType.qualifiedName ?: ""}\", " +
                        "but that sub type is not decorated by \"@$CLIENT_EXCEPTION\""
                )
            }
        }
        metadata.subMetadatas = subTypes
            .map { get(it) }
            .distinctBy { it.declaration.qualifiedName ?: it.declaration.simpleName }
            .toList()
    }

    private data class Key(
        val family: String,
        val code: String
    )

    // 覆盖来源：ClientExceptionContext.create/initSubMetadatas 的“首个父类（非接口）”判定
    private fun LsiClass.firstClassSuperType(): LsiClass? =
        superClasses.firstOrNull()

    private fun LsiClass.displayName(): String =
        qualifiedName ?: simpleName ?: "<unknown>"

    companion object {
        private const val FAMILY_ATTRIBUTE = "family"
        private const val CODE_ATTRIBUTE = "code"
        private const val SUB_TYPES_ATTRIBUTE = "subTypes"
    }
}
