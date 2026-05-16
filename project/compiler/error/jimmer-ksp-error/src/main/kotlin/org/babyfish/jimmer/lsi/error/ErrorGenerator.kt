package site.addzero.lsi.jimmer.error

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.THROWABLE
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import org.babyfish.jimmer.impl.util.StringUtil
import site.addzero.context.Context
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.codegen.CLIENT_EXCEPTION_CLASS_NAME
import site.addzero.lsi.codegen.JVM_STATIC_CLASS_NAME
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.get
import site.addzero.lsi.anno.getClassArgument
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.LsiEnumConstant
import site.addzero.lsi.clazz.annotation
import site.addzero.lsi.clazz.toClassName
import site.addzero.lsi.codegen.generatedAnnotation
import site.addzero.lsi.jimmer.CODE_BASED_EXCEPTION
import site.addzero.lsi.jimmer.CODE_BASED_RUNTIME_EXCEPTION
import site.addzero.lsi.jimmer.ERROR_FAMILY
import site.addzero.lsi.jimmer.ERROR_FIELD
import site.addzero.lsi.jimmer.ERROR_FIELDS

class ErrorGenerator(
    private val ctx: Context,
    private val declaration: LsiClass,
    private val checkedException: Boolean
) {

    private val declarationSimpleName =
        declaration.simpleName
            ?: throw MetaException(declaration, "Error type must have simple name")

    private val declarationQualifiedName =
        declaration.qualifiedName
            ?: throw MetaException(declaration, "Error type must have qualified name")

    private val packageName = declarationQualifiedName.substringBeforeLast('.', "")

    private val enumClassName: ClassName =
        // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator enumClassName
        // 迁移说明：错误枚举类型名直接复用 LSI core `LsiClass.toClassName()`，移除对 `org.babyfish.jimmer.ksp.className` 旧桥接的依赖
        declaration.toClassName()

    private val declaredFieldsCache = mutableMapOf<String, Map<String, TypeName>>()

    private val fieldsCache = mutableMapOf<String, Map<String, TypeName>>()

    private val enumConstants: List<LsiEnumConstant> = declaration.enumConstants

    private val family: String =
        // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.family 注解读取
        // 迁移说明：ErrorFamily 读取改为 LSI 注解 FQ + attributes 访问，避免生成层直接消费 KSAnnotation 或 `ErrorFamily::class`
        declaration.annotation(ERROR_FAMILY)
            ?.get<String>(VALUE_ATTRIBUTE)
            ?.takeIf { it.isNotEmpty() }
            ?: declarationSimpleName
                .let {
                    when {
                        it.endsWith("_ErrorCode") -> it.substring(0, it.length - 10)
                        it.endsWith("ErrorCode") -> it.substring(0, it.length - 9)
                        it.endsWith("_Error") -> it.substring(0, it.length - 6)
                        it.endsWith("Error") -> it.substring(0, it.length - 5)
                        else -> it
                    }
                }
                .let {
                    StringUtil.snake(it, StringUtil.SnakeCase.UPPER)
                }

    private val exceptionSimpleName: String =
        declarationSimpleName
            .let {
                when {
                    it.endsWith("_ErrorCode") -> it.substring(0, it.length - 10)
                    it.endsWith("ErrorCode") -> it.substring(0, it.length - 9)
                    it.endsWith("_Error") -> it.substring(0, it.length - 6)
                    it.endsWith("Error") -> it.substring(0, it.length - 5)
                    else -> it
                }
            } + "Exception"

    private val exceptionClassName = ClassName(packageName, exceptionSimpleName)

    private val generatedQualifiedName =
        if (packageName.isNotEmpty()) {
            "$packageName.$exceptionSimpleName"
        } else {
            exceptionSimpleName
        }

    fun generate() {
        val superType = if (checkedException) {
            // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.generate checkedException 超类选择
            // 迁移说明：异常超类改为由 lsi-jimmer FQ 常量推导 ClassName，移除生成器对 `CodeBasedException::class` 的直接依赖
            ClassName.bestGuess(CODE_BASED_EXCEPTION)
        } else {
            // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.generate uncheckedException 超类选择
            // 迁移说明：运行时异常超类同样改为共享 FQ 常量，避免该生成器再直接 import runtime class literal
            ClassName.bestGuess(CODE_BASED_RUNTIME_EXCEPTION)
        }
        val fileSpec = FileSpec
            .builder(packageName, exceptionSimpleName)
            .apply {
                indent("    ")
                addType(
                    TypeSpec
                        .classBuilder(exceptionSimpleName)
                        .superclass(superType)
                        .addModifiers(KModifier.ABSTRACT)
                        .addSuperclassConstructorParameter("message, cause")
                        .addAnnotation(generatedAnnotation(enumClassName))
                        .addAnnotation(
                            AnnotationSpec
                                .builder(CLIENT_EXCEPTION_CLASS_NAME)
                                .addMember("family = %S", family)
                                .apply {
                                    // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.generate subTypes 收集
                                    // 迁移说明：枚举常量遍历由 KS enum entry 声明切换为 LsiClass.enumConstants
                                    addMember(
                                        "subTypes = [${this@ErrorGenerator.enumConstants.joinToString { "%T::class" }}]",
                                        *this@ErrorGenerator.enumConstants.map { enumConstant ->
                                            exceptionClassName.nestedClass(
                                                ktName(requireEnumConstantName(enumConstant), true)
                                            )
                                        }.toTypedArray()
                                    )
                                }
                                .build()
                        )
                        .apply {
                            addMembers()
                        }
                        .build()
                )
            }
            .build()
        // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.generate 输出文件创建
        // 迁移说明：生成输出由 CodeGenerator.createNewFile(...) 迁移为 LSI filer，Error 生成层不再直接依赖 KSP 文件 API
        ctx.lsiFiler.createSourceFile(generatedQualifiedName, fileSpec.toString())
    }

    private fun TypeSpec.Builder.addMembers() {
        addInit(declaration)
        addGetEnum()
        addFields(declaration)

        addType(
            TypeSpec
                .companionObjectBuilder()
                .apply {
                    for (item in this@ErrorGenerator.enumConstants) {
                        addItem(item)
                    }
                }
                .build()
        )

        // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.addMembers enum entry nested type 生成
        // 迁移说明：枚举项子类型生成由 KS enum entry 声明遍历切换为 LsiEnumConstant 列表
        for (item in this@ErrorGenerator.enumConstants) {
            val itemName = requireEnumConstantName(item)
            addType(
                TypeSpec
                    .classBuilder(ktName(itemName, true))
                    .superclass(exceptionClassName)
                    .addAnnotation(
                        AnnotationSpec
                            .builder(CLIENT_EXCEPTION_CLASS_NAME)
                            .addMember("family = %S", family)
                            .addMember(
                                "code = %S",
                                StringUtil.snake(itemName, StringUtil.SnakeCase.UPPER)
                            )
                            .build()
                    )
                    .apply {
                        val shared = fieldsOf(requireDeclaringEnum(item))
                        if (shared.isEmpty()) {
                            addSuperclassConstructorParameter("message, cause")
                        } else {
                            addSuperclassConstructorParameter(
                                "message, cause, " + shared.keys.joinToString()
                            )
                        }
                        addInit(item)
                        addGetEnum(item)
                        addFields(item)
                    }
                    .build()
            )
        }
    }

    private fun TypeSpec.Builder.addItem(item: LsiEnumConstant) {
        val itemName = requireEnumConstantName(item)
        val fields = fieldsOf(item)
        addFunction(
            FunSpec
                .builder(ktName(itemName, false))
                .addAnnotation(JVM_STATIC_CLASS_NAME)
                .addParameter(
                    ParameterSpec
                        .builder("message", STRING.copy(nullable = true))
                        .defaultValue("null")
                        .build()
                )
                .addParameter(
                    ParameterSpec
                        .builder("cause", THROWABLE.copy(nullable = true))
                        .defaultValue("null")
                        .build()
                )
                .apply {
                    for ((name, type) in fields) {
                        if (type.isNullable) {
                            addParameter(
                                ParameterSpec
                                    .builder(name, type)
                                    .defaultValue("null")
                                    .build()
                            )
                        } else {
                            addParameter(name, type)
                        }
                    }
                }
                .returns(exceptionClassName.nestedClass(ktName(itemName, true)))
                .apply {
                    addCode(
                        CodeBlock
                            .builder()
                            .apply {
                                add("return %L(\n", ktName(itemName, true))
                                indent()
                                add("message,\ncause")
                                for ((name, _) in fields) {
                                    add(",\n").add(name)
                                }
                                unindent()
                                add("\n)\n")
                            }
                            .build()
                    )
                }
                .build()
        )
    }

    private fun TypeSpec.Builder.addInit(declaration: LsiClass) {
        for ((name, typeName) in declaredFieldsOf(declaration)) {
            addProperty(
                PropertySpec
                    .builder(name, typeName)
                    .initializer(name)
                    .build()
            )
        }
        primaryConstructor(
            FunSpec
                .constructorBuilder()
                .addParameter(
                    ParameterSpec
                        .builder("message", STRING.copy(nullable = true))
                        .defaultValue("null")
                        .build()
                )
                .addParameter(
                    ParameterSpec
                        .builder("cause", THROWABLE.copy(nullable = true))
                        .defaultValue("null")
                        .build()
                )
                .apply {
                    for ((name, typeName) in fieldsOf(declaration)) {
                        addParameter(
                            ParameterSpec
                                .builder(name, typeName)
                                .apply {
                                    if (typeName.isNullable) {
                                        defaultValue("null")
                                    }
                                }
                                .build()
                        )
                    }
                }
                .build()
        )
    }

    private fun TypeSpec.Builder.addInit(declaration: LsiEnumConstant) {
        for ((name, typeName) in declaredFieldsOf(declaration)) {
            addProperty(
                PropertySpec
                    .builder(name, typeName)
                    .initializer(name)
                    .build()
            )
        }
        primaryConstructor(
            FunSpec
                .constructorBuilder()
                .addParameter(
                    ParameterSpec
                        .builder("message", STRING.copy(nullable = true))
                        .defaultValue("null")
                        .build()
                )
                .addParameter(
                    ParameterSpec
                        .builder("cause", THROWABLE.copy(nullable = true))
                        .defaultValue("null")
                        .build()
                )
                .apply {
                    for ((name, typeName) in fieldsOf(declaration)) {
                        addParameter(
                            ParameterSpec
                                .builder(name, typeName)
                                .apply {
                                    if (typeName.isNullable) {
                                        defaultValue("null")
                                    }
                                }
                                .build()
                        )
                    }
                }
                .build()
        )
    }

    private fun TypeSpec.Builder.addGetEnum() {
        addProperty(
            PropertySpec
                .builder(StringUtil.identifier(declarationSimpleName), enumClassName)
                .addModifiers(KModifier.ABSTRACT)
                .addAnnotation(
                    AnnotationSpec
                        .builder(ctx.jacksonTypes.jsonIgnore)
                        .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                        .build()
                )
                .build()
        )
    }

    private fun TypeSpec.Builder.addGetEnum(declaration: LsiEnumConstant) {
        val itemName = requireEnumConstantName(declaration)
        addProperty(
            PropertySpec
                .builder(StringUtil.identifier(declarationSimpleName), enumClassName)
                .addModifiers(KModifier.OVERRIDE)
                .addAnnotation(
                    AnnotationSpec
                        .builder(ctx.jacksonTypes.jsonIgnore)
                        .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                        .build()
                )
                .getter(
                    FunSpec
                        .getterBuilder()
                        .addStatement("return %T.%N", enumClassName, itemName)
                        .build()
                )
                .build()
        )
    }

    private fun TypeSpec.Builder.addFields(declaration: LsiClass) {
        val fields = fieldsOf(declaration)
        addProperty(
            PropertySpec
                .builder("fields", MAP.parameterizedBy(STRING, ANY.copy(nullable = true)), KModifier.OVERRIDE)
                .getter(
                    FunSpec
                        .getterBuilder()
                        .addCode(fieldsCode(fields))
                        .build()
                )
                .build()
        )
    }

    private fun TypeSpec.Builder.addFields(declaration: LsiEnumConstant) {
        val fields = fieldsOf(declaration)
        addProperty(
            PropertySpec
                .builder("fields", MAP.parameterizedBy(STRING, ANY.copy(nullable = true)), KModifier.OVERRIDE)
                .getter(
                    FunSpec
                        .getterBuilder()
                        .addCode(fieldsCode(fields))
                        .build()
                )
                .build()
        )
    }

    private fun fieldsCode(fields: Map<String, TypeName>): CodeBlock =
        CodeBlock
            .builder()
            .apply {
                if (fields.isEmpty()) {
                    add("return emptyMap()")
                } else {
                    add("return mapOf(\n")
                    indent()
                    var addComma = false
                    for (name in fields.keys) {
                        if (addComma) {
                            add(",\n")
                        } else {
                            addComma = true
                        }
                        add("%S to %N", name, name)
                    }
                    unindent()
                    add("\n)\n")
                }
            }
            .build()

    private fun fieldsOf(declaration: LsiClass): Map<String, TypeName> {
        val cacheKey = declarationCacheKey(declaration)
        val cached = fieldsCache[cacheKey]
        if (cached != null) {
            return cached
        }
        return declaredFieldsOf(declaration).also {
            fieldsCache[cacheKey] = it
        }
    }

    private fun fieldsOf(declaration: LsiEnumConstant): Map<String, TypeName> {
        val cacheKey = enumConstantCacheKey(declaration)
        val cached = fieldsCache[cacheKey]
        if (cached != null) {
            return cached
        }
        val owner = requireDeclaringEnum(declaration)
        val shared = declaredFieldsOf(owner)
        val local = declaredFieldsOf(declaration)
        val merged = if (shared.isEmpty()) {
            local
        } else {
            val map = shared.toMutableMap()
            for ((name, typeName) in local) {
                map.put(name, typeName)?.let {
                    throw MetaException(
                        declaration,
                        "The field \"$name\" has already been defined in enum \"" +
                            (owner.qualifiedName ?: owner.simpleName ?: "<unknown>") +
                            "\""
                    )
                }
            }
            map
        }
        fieldsCache[cacheKey] = merged
        return merged
    }

    private fun declaredFieldsOf(declaration: LsiClass): Map<String, TypeName> {
        val cacheKey = declarationCacheKey(declaration)
        val cached = declaredFieldsCache[cacheKey]
        if (cached != null) {
            return cached
        }
        val map = toFieldMap(
            // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.declaredFieldsOf enum 级 @ErrorField 提取
            // 迁移说明：ErrorField 提取改为 LSI 注解 FQ 读取，并兼容 repeatable 容器 `@ErrorFields`
            targetAnnotations = declaration.errorFieldAnnotations(),
            onReservedName = { name ->
                throw MetaException(
                    declaration,
                    "The enum \"" +
                        (declaration.qualifiedName ?: declaration.simpleName ?: "<unknown>") +
                        "\" is illegal, it cannot be decorated by \"@" +
                        ERROR_FAMILY +
                        "\" with the name \"$name\""
                )
            },
            onDuplicateName = { name ->
                throw MetaException(declaration, "Duplicate field \"$name\"")
            }
        )
        declaredFieldsCache[cacheKey] = map
        return map
    }

    private fun declaredFieldsOf(declaration: LsiEnumConstant): Map<String, TypeName> {
        val cacheKey = enumConstantCacheKey(declaration)
        val cached = declaredFieldsCache[cacheKey]
        if (cached != null) {
            return cached
        }
        val constantName = requireEnumConstantName(declaration)
        val owner = requireDeclaringEnum(declaration)
        val map = toFieldMap(
            targetAnnotations = declaration.errorFieldAnnotations(),
            onReservedName = { name ->
                throw MetaException(
                    declaration,
                    "The enum constant \"" +
                        (owner.qualifiedName ?: owner.simpleName ?: "<unknown>") +
                        '.' +
                        constantName +
                        "\" is illegal, it cannot be decorated by \"@" +
                        ERROR_FAMILY +
                        "\" with the name \"$name\""
                )
            },
            onDuplicateName = { name ->
                throw MetaException(declaration, "Duplicate field \"$name\"")
            }
        )
        declaredFieldsCache[cacheKey] = map
        return map
    }

    private fun toFieldMap(
        targetAnnotations: List<LsiAnnotation>,
        onReservedName: (String) -> Nothing,
        onDuplicateName: (String) -> Nothing
    ): Map<String, TypeName> {
        val map = mutableMapOf<String, TypeName>()
        for (anno in targetAnnotations) {
            // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.toFieldMap
            // 迁移说明：ErrorField 参数读取改为 LSI attributes 访问，移除 `ErrorField::name/type/list/nullable` 直接依赖
            val name = anno.get<String>(NAME_ATTRIBUTE)
                ?: throw IllegalStateException("@$ERROR_FIELD.$NAME_ATTRIBUTE must exist")
            if (name == "family" || name == "code") {
                onReservedName(name)
            }
            val fieldType = anno.getClassArgument(TYPE_ATTRIBUTE)
                ?.toClassName()
                ?: throw IllegalStateException("@$ERROR_FIELD.$TYPE_ATTRIBUTE must exist")
            val typeName = fieldType
                .let {
                    if (anno.get<Boolean>(LIST_ATTRIBUTE) == true) {
                        LIST.parameterizedBy(it)
                    } else {
                        it
                    }
                }
                .copy(nullable = anno.get<Boolean>(NULLABLE_ATTRIBUTE) == true)
            map.put(name, typeName)?.let {
                onDuplicateName(name)
            }
        }
        return map
    }

    private fun LsiEnumConstant.errorFieldAnnotations(): List<LsiAnnotation> =
        // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.errorFieldAnnotations
        // 迁移说明：repeatable `@ErrorField` 收集改为纯 LSI 注解展开，兼容容器注解 `@ErrorFields`
        expandErrorFieldAnnotations(annotations)

    private fun LsiClass.errorFieldAnnotations(): List<LsiAnnotation> =
        // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.declaredFieldsOf enum 级 @ErrorField 提取
        // 迁移说明：类型级 ErrorField 收集改为纯 LSI 注解展开，兼容容器注解 `@ErrorFields`
        expandErrorFieldAnnotations(annotations)

    private fun expandErrorFieldAnnotations(annotations: List<LsiAnnotation>): List<LsiAnnotation> =
        buildList {
            for (annotation in annotations) {
                when (annotation.qualifiedName) {
                    ERROR_FIELD -> add(annotation)
                    ERROR_FIELDS -> addAll(
                        ((annotation[VALUE_ATTRIBUTE] as? List<*>)
                            ?.filterIsInstance<LsiAnnotation>())
                            .orEmpty()
                    )
                }
            }
        }

    private fun requireEnumConstantName(declaration: LsiEnumConstant): String =
        declaration.name
            ?: throw MetaException(
                declaration,
                "Cannot resolve enum constant name"
            )

    private fun requireDeclaringEnum(declaration: LsiEnumConstant): LsiClass =
        declaration.declaringClass
            ?: throw MetaException(
                declaration,
                "Cannot resolve declaring enum type"
            )

    private fun declarationCacheKey(declaration: LsiClass): String =
        declaration.qualifiedName ?: declaration.simpleName ?: "<unknown>"

    private fun enumConstantCacheKey(declaration: LsiEnumConstant): String =
        "${declaration.declaringClass?.qualifiedName ?: "<unknown>"}#${declaration.name ?: "<unknown>"}"

    companion object {

        private const val VALUE_ATTRIBUTE = "value"
        private const val NAME_ATTRIBUTE = "name"
        private const val TYPE_ATTRIBUTE = "type"
        private const val LIST_ATTRIBUTE = "list"
        private const val NULLABLE_ATTRIBUTE = "nullable"

        private fun ktName(simpleName: String, upperHead: Boolean): String {
            val size = simpleName.length
            var toUpper = upperHead
            val builder = StringBuilder()
            for (i in 0 until size) {
                val c = simpleName[i]
                toUpper = if (c == '_') {
                    true
                } else {
                    if (toUpper) {
                        builder.append(c.uppercaseChar())
                    } else {
                        builder.append(c.lowercaseChar())
                    }
                    false
                }
            }
            return builder.toString()
        }
    }
}
