package site.addzero.lsi.jimmer.client

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import org.babyfish.jimmer.client.meta.TypeDefinition
import org.babyfish.jimmer.client.meta.TypeName
import org.babyfish.jimmer.client.meta.impl.ApiOperationImpl
import org.babyfish.jimmer.client.meta.impl.ApiParameterImpl
import org.babyfish.jimmer.client.meta.impl.ApiServiceImpl
import org.babyfish.jimmer.client.meta.impl.PropImpl
import org.babyfish.jimmer.client.meta.impl.TypeDefinitionImpl
import org.babyfish.jimmer.client.meta.impl.TypeRefImpl
import org.babyfish.jimmer.impl.util.StringUtil
import site.addzero.lsi.anno.get
import site.addzero.lsi.anno.getClassArgument
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.annotation
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.field.annotation as fieldAnnotation
import site.addzero.lsi.jimmer.API_IGNORE
import site.addzero.lsi.jimmer.CLIENT_EXCEPTION
import site.addzero.lsi.jimmer.CODE_BASED_EXCEPTION
import site.addzero.lsi.jimmer.CODE_BASED_RUNTIME_EXCEPTION
import site.addzero.lsi.jimmer.DEFAULT_FETCHER_OWNER
import site.addzero.lsi.jimmer.FETCH_BY
import site.addzero.lsi.jimmer.isJimmerEntity
import site.addzero.lsi.jimmer.toJimmerDoc
import site.addzero.lsi.method.annotation as methodAnnotation
import site.addzero.lsi.poet.isLsiBooleanLikeQualifiedName
import site.addzero.lsi.poet.isLsiNoValueLikeQualifiedName
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.resolver.LsiResolver
import site.addzero.lsi.type.LsiType
import org.babyfish.jimmer.client.meta.impl.SchemaBuilder

internal data class LsiClientSchemaMaterializationInput(
    val docMetadata: DocMetadata,
    val clientExceptionContext: ClientExceptionContext,
    val resolver: LsiResolver,
    val convertedLsiTypeNameOf: (LsiClass, String) -> LsiTypeName?,
    val jsonValueTypeNameStack: MutableSet<TypeName> = mutableSetOf()
)

internal fun SchemaBuilder<LsiClass>.fillClientType(
    type: LsiType,
    input: LsiClientSchemaMaterializationInput
) {
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.fillType
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.determineNullity
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.determineFetchBy
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.determineTypeNameAndArguments
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.jsonValueTypeRef
    // 迁移说明：client type materialization 下沉到共享 helper，处理器入口只负责注入 resolver/doc/converter metadata 等上下文
    val typeRef = current<TypeRefImpl<LsiClass>>()
    try {
        determineClientNullity(type)
        determineClientFetchBy(type, input)
        determineClientTypeNameAndArguments(type, input)
        typeRef.removeClientOptional()
    } catch (ex: JsonValueTypeChangeException) {
        typeRef.replaceBy(
            ex.typeRef,
            typeRef.isNullable || ex.typeRef.isNullable
        )
    }
}

internal fun SchemaBuilder<LsiClass>.fillClientDefinition(
    declaration: LsiClass,
    immutable: Boolean,
    input: LsiClientSchemaMaterializationInput
) {
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.fillDefinition
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.fillEnumDefinition
    // 迁移说明：client schema definition materialization 下沉到共享 helper，处理器入口只保留 builder override 装配
    val definition = current<TypeDefinitionImpl<LsiClass>>()
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.fillDefinition @ApiIgnore 判定
    // 迁移说明：ApiIgnore 判定继续复用 LSI 注解 FQ 常量，不在 helper 中引入平台符号
    definition.isApiIgnore = declaration.annotation(API_IGNORE) != null
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.fillDefinition definition.doc 赋值
    // 迁移说明：类型文档在 materialization helper 内部继续保持 LsiDoc -> Doc 的边界转换
    definition.doc = input.docMetadata.getDoc(declaration)?.toJimmerDoc()

    if (declaration.isEnum) {
        fillClientEnumDefinition(declaration, input)
        return
    }

    definition.kind = if (immutable) TypeDefinition.Kind.IMMUTABLE else TypeDefinition.Kind.OBJECT

    if (!immutable || declaration.isInterface) {
        // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.fillDefinition client exception 判定
        // 迁移说明：ClientException 判定在共享 helper 中继续使用 LSI 注解语义，避免入口类保留重复分支
        val isClientException = declaration.annotation(CLIENT_EXCEPTION) != null
        for (propDeclaration in declaredClientFields(declaration)) {
            val propName = propDeclaration.name ?: continue
            if (!propDeclaration.isPublic ||
                propDeclaration.fieldAnnotation(API_IGNORE) != null ||
                propDeclaration.fieldAnnotation(JsonIgnore::class) != null
            ) {
                continue
            }
            if (isClientException && (propName == "code" || propName == "fields")) {
                continue
            }
            val convertedType = declaration
                .takeIf { immutable }
                ?.let { resolveConvertedClientType(it, propName, input) }
            val propType = convertedType ?: propDeclaration.type ?: continue

            prop(declaration, propName) { prop: PropImpl<LsiClass> ->
                try {
                    typeRef { type: TypeRefImpl<LsiClass> ->
                        fillClientType(propType, input)
                        prop.setType(type)
                    }
                    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.fillDefinition prop.doc 赋值
                    // 迁移说明：属性文档边界转换收拢到共享 materialization helper，移除入口类中的重复赋值分支
                    prop.doc = input.docMetadata.getDoc(propDeclaration)?.toJimmerDoc()
                    definition.addProp(prop)
                } catch (_: UnambiguousTypeException) {
                    // Ignore unsupported shape for property extraction.
                }
            }
        }

        for (funcDeclaration in declaredClientMethods(declaration)) {
            if (funcDeclaration.isConstructor ||
                !funcDeclaration.isPublic ||
                funcDeclaration.parameters.isNotEmpty() ||
                funcDeclaration.methodAnnotation(JsonIgnore::class) != null ||
                funcDeclaration.methodAnnotation(API_IGNORE) != null
            ) {
                continue
            }
            val returnType = funcDeclaration.returnType ?: continue
            val returnTypeName = returnType.qualifiedName ?: continue
            if (returnTypeName.isLsiNoValueLikeQualifiedName()) {
                continue
            }
            val methodName = funcDeclaration.name ?: continue
            val name = StringUtil.propName(methodName, returnTypeName.isLsiBooleanLikeQualifiedName()) ?: continue
            try {
                prop(declaration, name) { prop: PropImpl<LsiClass> ->
                    typeRef { type: TypeRefImpl<LsiClass> ->
                        fillClientType(returnType, input)
                        prop.setType(type)
                    }
                    definition.addProp(prop)
                }
            } catch (_: UnambiguousTypeException) {
                // Ignore unsupported shape for property extraction.
            }
        }
    }

    if (declaration.annotation(CLIENT_EXCEPTION) != null) {
        val metadata = input.clientExceptionContext[declaration]
        if (metadata.code != null) {
            definition.error = TypeDefinition.Error(metadata.family, metadata.code)
        }
    }

    if (declaration.isClass || declaration.isInterface) {
        for (superDeclaration in declaration.superClasses + declaration.interfaces) {
            if (superDeclaration.annotation(API_IGNORE) == null) {
                val superName = superDeclaration.toClientTypeName()
                if (superName.isGenerationRequired &&
                    superName != CODE_BASED_EXCEPTION_NAME &&
                    superName != CODE_BASED_RUNTIME_EXCEPTION_NAME
                ) {
                    typeRef { superType: TypeRefImpl<LsiClass> ->
                        fillClientType(superDeclaration.asClientLsiType(nullable = false), input)
                        definition.addSuperType(superType)
                    }
                }
            }
        }
    }
}

private fun SchemaBuilder<LsiClass>.determineClientNullity(type: LsiType) {
    current<TypeRefImpl<LsiClass>>().isNullable = type.isNullable
}

private fun SchemaBuilder<LsiClass>.determineClientFetchBy(
    type: LsiType,
    input: LsiClientSchemaMaterializationInput
) {
    val typeRef = current<TypeRefImpl<LsiClass>>()
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.determineFetchBy @FetchBy 读取
    // 迁移说明：FetchBy 解析留在共享 helper，并继续只消费 LSI annotation/type 语义
    val fetchBy = type.annotations.firstOrNull { it.qualifiedName == FETCH_BY } ?: return

    val entityType = type.lsiClass
    if (entityType?.isJimmerEntity != true) {
        throw MetaException(
            clientErrorSource(),
            "Illegal type because \"${type.qualifiedName ?: type.presentableText ?: "<unknown>"}\" " +
                "which is decorated by `@FetchBy` is not entity type"
        )
    }

    val constant = fetchBy.get<String>(VALUE_ATTRIBUTE)?.takeIf { it.isNotBlank() } ?: throw MetaException(
        clientErrorSource(),
        "The `value` of `@FetchBy` is required"
    )

    val owner = fetchBy
        .getClassArgument(OWNER_TYPE_ATTRIBUTE)
        ?.takeIf { !it.qualifiedName.isLsiNoValueLikeQualifiedName() }
        ?: ancestorSource(ApiServiceImpl::class.java, TypeDefinitionImpl::class.java)
            // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.determineFetchBy DefaultFetcherOwner fallback
            // 迁移说明：DefaultFetcherOwner fallback 逻辑搬到共享 helper，保留原有注解常量与属性读取语义
            ?.annotation(DEFAULT_FETCHER_OWNER)
            ?.getClassArgument(VALUE_ATTRIBUTE)
        ?: ancestorSource(ApiServiceImpl::class.java, TypeDefinitionImpl::class.java)
        ?: entityType

    val field = owner.fields.firstOrNull { it.name == constant }
        ?: throw MetaException(
            clientErrorSource(),
            "Illegal `@FetcherBy`, the owner type \"${owner.clientFullName()}\" does any field named \"$constant\""
        )

    val fieldType = field.type
    if (fieldType?.qualifiedName != FETCHER_QUALIFIED_NAME) {
        throw MetaException(
            clientErrorSource(),
            "Illegal `@FetcherBy`, there is static field \"$constant\" in owner type \"${owner.clientFullName()}\" " +
                "but it is not \"$FETCHER_QUALIFIED_NAME\""
        )
    }

    val argType = fieldType.typeParameters.firstOrNull() ?: throw MetaException(
        clientErrorSource(),
        "Illegal `@FetcherBy`, there is static field \"$constant\" in owner type \"${owner.clientFullName()}\" " +
            "but it has no generic argument"
    )

    if (!sameClientTypeName(argType.qualifiedName, entityType.qualifiedName)) {
        throw MetaException(
            clientErrorSource(),
            "Illegal `@FetcherBy`, there is property \"$constant\" in owner type \"${owner.clientFullName()}\" " +
                "but it is not fetcher for \"${entityType.clientFullName()}\""
        )
    }

    typeRef.fetchBy = constant
    typeRef.fetcherOwner = owner.toClientTypeName()
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.determineFetchBy fetcherDoc 赋值
    // 迁移说明：fetcherDoc 赋值边界也下沉到共享 helper，入口类不再保留文档 materialization 细节
    typeRef.fetcherDoc = input.docMetadata.getDoc(field)?.toJimmerDoc()
}

private fun SchemaBuilder<LsiClass>.determineClientTypeNameAndArguments(
    type: LsiType,
    input: LsiClientSchemaMaterializationInput
) {
    val typeRef = current<TypeRefImpl<LsiClass>>()

    if (type.qualifiedName == null && type.simpleName != null) {
        val ownerType = ancestorSource(ApiServiceImpl::class.java, TypeDefinitionImpl::class.java)
            ?.toClientTypeName()
            ?: TypeName.OBJECT
        typeRef.typeName = ownerType.typeVariable(type.simpleName)
        return
    }

    typeRef.typeName = type.toClientTypeName()
    when (typeRef.typeName.toString()) {
        "kotlin.BooleanArray" -> {
            typeRef.typeName = TypeName.LIST
            typeRef.addArgument(TypeRefImpl<LsiClass>().apply { typeName = TypeName.BOOLEAN })
            return
        }

        "kotlin.CharArray" -> {
            typeRef.typeName = TypeName.LIST
            typeRef.addArgument(TypeRefImpl<LsiClass>().apply { typeName = TypeName.CHAR })
            return
        }

        "kotlin.ByteArray" -> {
            typeRef.typeName = TypeName.LIST
            typeRef.addArgument(TypeRefImpl<LsiClass>().apply { typeName = TypeName.BYTE })
            return
        }

        "kotlin.ShortArray" -> {
            typeRef.typeName = TypeName.LIST
            typeRef.addArgument(TypeRefImpl<LsiClass>().apply { typeName = TypeName.SHORT })
            return
        }

        "kotlin.IntArray" -> {
            typeRef.typeName = TypeName.LIST
            typeRef.addArgument(TypeRefImpl<LsiClass>().apply { typeName = TypeName.INT })
            return
        }

        "kotlin.LongArray" -> {
            typeRef.typeName = TypeName.LIST
            typeRef.addArgument(TypeRefImpl<LsiClass>().apply { typeName = TypeName.LONG })
            return
        }

        "kotlin.FloatArray" -> {
            typeRef.typeName = TypeName.LIST
            typeRef.addArgument(TypeRefImpl<LsiClass>().apply { typeName = TypeName.FLOAT })
            return
        }

        "kotlin.DoubleArray" -> {
            typeRef.typeName = TypeName.LIST
            typeRef.addArgument(TypeRefImpl<LsiClass>().apply { typeName = TypeName.DOUBLE })
            return
        }
    }

    jsonValueTypeRef(typeRef.typeName, input)?.let { throw JsonValueTypeChangeException(it) }

    val declaration = type.lsiClass
    if (declaration != null && !declaration.isTopLevel && !declaration.isStatic) {
        throw UnambiguousTypeException(
            clientErrorSource(),
            "Client API only accept top-level of static nested type"
        )
    }

    val simpleName = type.simpleName ?: ""
    val jsonFlag = listOf(
        "JsonNode",
        "JSONObject",
        "JsonObject",
        "JsonElement",
        "ObjectNode",
        "ArrayNode",
    ).any { it.equals(simpleName, ignoreCase = true) }
    if (jsonFlag) {
        typeRef.typeName = TypeName.OBJECT
        return
    }

    if (typeRef.typeName == TypeName.OBJECT) {
        throw UnambiguousTypeException(
            clientErrorSource(),
            "Client API system does not accept unambiguous type `java.lang.Object`"
        )
    }

    for (argument in type.typeParameters) {
        typeRef { argType ->
            fillClientType(argument, input)
            typeRef.addArgument(argType)
        }
    }
}

private fun SchemaBuilder<LsiClass>.jsonValueTypeRef(
    typeName: TypeName,
    input: LsiClientSchemaMaterializationInput
): TypeRefImpl<LsiClass>? {
    val declaration = input.resolver.findClassByQualifiedName(typeName.toString()) ?: return null
    val jsonValueFun = declaration.methods.firstOrNull {
        it.methodAnnotation(JsonValue::class) != null &&
            it.parameters.isEmpty() &&
            !it.returnType?.qualifiedName.isLsiNoValueLikeQualifiedName()
    } ?: return null

    if (!input.jsonValueTypeNameStack.add(typeName)) {
        throw MetaException(
            clientErrorSource(),
            "Cannot resolve \"@${JsonValue::class.java.name}\" because of dead recursion: ${input.jsonValueTypeNameStack}"
        )
    }
    try {
        var result: TypeRefImpl<LsiClass>? = null
        typeRef {
            val returnType = jsonValueFun.returnType ?: return@typeRef
            fillClientType(returnType, input)
            result = it
        }
        return result
    } finally {
        input.jsonValueTypeNameStack.remove(typeName)
    }
}

private fun SchemaBuilder<LsiClass>.fillClientEnumDefinition(
    declaration: LsiClass,
    input: LsiClientSchemaMaterializationInput
) {
    val definition = current<TypeDefinitionImpl<LsiClass>>()
    definition.kind = TypeDefinition.Kind.ENUM

    val enumConstants = declaredClientFields(declaration)
        .filter { it.isEnum || sameClientTypeName(it.type?.qualifiedName, declaration.qualifiedName) }
        .distinctBy { it.name }

    for (enumConstant in enumConstants) {
        val constantName = enumConstant.name ?: continue
        constant(declaration, constantName) { constant ->
            // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.fillEnumDefinition constant.doc 赋值
            // 迁移说明：枚举文档赋值也下沉到共享 helper，保持 runtime schema 装配边界一致
            constant.doc = input.docMetadata.getDoc(enumConstant)?.toJimmerDoc()
            definition.addEnumConstant(constant)
        }
    }
}

private fun resolveConvertedClientType(
    owner: LsiClass,
    propName: String,
    input: LsiClientSchemaMaterializationInput
): LsiType? {
    val targetTypeName = input.convertedLsiTypeNameOf(owner, propName) ?: return null
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.convertedType
    // 迁移说明：converter target type 不再走字符串往返，直接把 `LsiTypeName` 物化为 client 侧最小 `LsiType`
    return targetTypeName.toClientLsiType(input.resolver)
}

private fun SchemaBuilder<LsiClass>.clientErrorSource(): LsiClass =
    ancestorSource(ApiOperationImpl::class.java, ApiParameterImpl::class.java)
        ?: ancestorSource(ApiServiceImpl::class.java, TypeDefinitionImpl::class.java)
        ?: ancestorSource()
        ?: throw IllegalStateException("No LSI source available for error reporting")

@Suppress("UNCHECKED_CAST")
private fun TypeRefImpl<LsiClass>.removeClientOptional() {
    if (typeName == TypeName.OPTIONAL) {
        val target = arguments[0] as TypeRefImpl<LsiClass>
        replaceBy(target, null)
    }
}

private class UnambiguousTypeException(
    declaration: LsiClass,
    reason: String,
    cause: Throwable? = null
) : MetaException(declaration, reason, cause)

private class JsonValueTypeChangeException(
    val typeRef: TypeRefImpl<LsiClass>
) : RuntimeException()

private const val VALUE_ATTRIBUTE = "value"
private const val OWNER_TYPE_ATTRIBUTE = "ownerType"
private const val FETCHER_QUALIFIED_NAME = "org.babyfish.jimmer.sql.fetcher.Fetcher"

// 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.CODE_BASED_EXCEPTION_NAME
// 迁移说明：异常基类 TypeName 常量跟随 schema materialization 一起下沉，入口类不再持有这组 runtime 过滤语义
private val CODE_BASED_EXCEPTION_NAME = typeNameOfQualifiedName(CODE_BASED_EXCEPTION)

// 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.CODE_BASED_RUNTIME_EXCEPTION_NAME
// 迁移说明：异常基类 TypeName 常量跟随 schema materialization 一起下沉，入口类不再持有这组 runtime 过滤语义
private val CODE_BASED_RUNTIME_EXCEPTION_NAME = typeNameOfQualifiedName(CODE_BASED_RUNTIME_EXCEPTION)

private fun typeNameOfQualifiedName(qualifiedName: String): TypeName {
    val simpleName = qualifiedName.substringAfterLast('.')
    val packageName = qualifiedName.substringBeforeLast('.', "")
    return TypeName.of(packageName, listOf(simpleName))
}
