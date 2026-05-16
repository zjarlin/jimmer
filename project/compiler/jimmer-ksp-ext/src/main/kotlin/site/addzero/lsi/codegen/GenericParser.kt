package site.addzero.lsi.codegen

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.poet.LsiArrayTypeName
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.isLsiCollectionLikeQualifiedName
import site.addzero.lsi.poet.normalizedLsiCollectionCarrierQualifiedName
import site.addzero.lsi.type.LsiType
import java.lang.IllegalStateException

class GenericParser(
    private val name: String,
    private val declaration: LsiClass,
    private val superName: String
) {

    init {
        if (declaration.typeParameterCount > 0) {
            throw IllegalStateException("\"${declaration.qualifiedName ?: declaration.simpleName}\" cannot have type parameters")
        }
    }

    fun parse(): Result {
        try {
            val visited = linkedSetOf<String>()
            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../util/GenericParser.parse
            // 迁移说明：由 KSClassDeclaration.asType/superTypes 递归，改为 LSI superTypes 递归
            for (superType in declaration.superTypes) {
                parse(superType, emptyMap(), visited)
            }
        } catch (ex: Finished) {
            return ex.result
        }
        throw MetaException(
            declaration,
            "it does not specify the arguments for \"" +
                superName +
                "\""
        )
    }

    private fun parse(
        rawType: LsiType,
        inheritedBindings: Map<String, LsiType>,
        visited: MutableSet<String>
    ) {
        val type = substitute(rawType, inheritedBindings)
        if (normalizedQualifiedName(type.qualifiedName) == superName) {
            if (type.typeParameters.isEmpty()) {
                throw MetaException(
                    declaration,
                    "it does not specify type argument for \"$superName\""
                )
            }
            throw Finished(
                Result(
                    arguments = type.typeParameters,
                    argumentLsiTypeNames = type.typeParameters.map { resolve(it) }
                )
            )
        }

        val classDeclaration = type.lsiClass ?: return
        val visitKey = (classDeclaration.qualifiedName ?: classDeclaration.simpleName ?: "<unknown>") +
            "<" + type.typeParameters.joinToString(",") { it.presentableText ?: it.qualifiedName ?: it.simpleName ?: "?" } + ">"
        if (!visited.add(visitKey)) {
            return
        }

        val nextBindings = LinkedHashMap(inheritedBindings)
        val paramNames = classDeclaration.typeParameterNames
        val args = type.typeParameters
        val bindCount = minOf(paramNames.size, args.size)
        for (i in 0 until bindCount) {
            nextBindings[paramNames[i]] = args[i]
        }

        for (superType in classDeclaration.superTypes) {
            parse(superType, nextBindings, visited)
        }
    }

    private fun substitute(
        type: LsiType,
        bindings: Map<String, LsiType>
    ): LsiType {
        val typeVariableName = type.typeVariableName()
        if (typeVariableName != null) {
            return bindings[typeVariableName] ?: type
        }
        if (type.typeParameters.isEmpty()) {
            return type
        }
        return SyntheticLsiType(
            simpleName = type.simpleName,
            qualifiedName = type.qualifiedName,
            presentableText = type.presentableText,
            isNullableValue = type.isNullable,
            isPrimitiveValue = type.isPrimitive,
            isArrayValue = type.isArray,
            annotationsValue = type.annotations,
            lsiClassValue = type.lsiClass,
            typeParametersValue = type.typeParameters.map { substitute(it, bindings) },
            componentTypeValue = type.componentType?.let { substitute(it, bindings) }
        )
    }

    private fun LsiType.typeVariableName(): String? {
        if (lsiClass != null || typeParameters.isNotEmpty()) {
            return null
        }
        val candidate = simpleName ?: qualifiedName ?: presentableText ?: return null
        return candidate.takeIf { '.' !in it && '<' !in it && ' ' !in it }
    }

    private fun resolve(type: LsiType): LsiTypeName {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../util/GenericParser.resolve(KSType)
        // 迁移说明：由 KSType/KSTypeArgument 解析改为 LsiType 解析，移除 toKS 反向桥接
        val unresolvedTypeVar = type.typeVariableName()
        if (unresolvedTypeVar != null) {
            throw MetaException(
                declaration,
                "The type parameter \"$unresolvedTypeVar\" cannot be resolved"
            )
        }

        if (type.isArray && type.componentType != null) {
            return LsiArrayTypeName(
                componentType = resolve(type.componentType!!),
                nullable = type.isNullable
            )
        }

        val className = resolveClassName(type)
        val argumentLsiTypeNames = type.typeParameters.map { resolve(it) }
        return if (argumentLsiTypeNames.isEmpty()) {
            className.copy(nullable = type.isNullable)
        } else {
            LsiParameterizedTypeName(
                rawType = className,
                typeArguments = argumentLsiTypeNames,
                nullable = type.isNullable
            )
        }
    }

    private fun resolveClassName(type: LsiType): LsiClassName =
        LsiClassName.bestGuess(
            (normalizedQualifiedName(type.qualifiedName)
                ?: type.lsiClass?.qualifiedName
                ?: throw MetaException(
                    declaration,
                    "The type \"${type.presentableText ?: type.simpleName ?: "<unknown>"}\" cannot be resolved"
                )).normalizedLsiCollectionCarrierQualifiedName()
        )

    private fun normalizedQualifiedName(qualifiedName: String?): String? =
        qualifiedName
            ?.removeSuffix("?")
            ?.substringBefore('<')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    data class Result(
        val arguments: List<LsiType>,
        val argumentLsiTypeNames: List<LsiTypeName>
    )

    private class Finished(
        val result: Result
    ) : RuntimeException()

    private data class SyntheticLsiType(
        override val simpleName: String?,
        override val qualifiedName: String?,
        override val presentableText: String?,
        val isNullableValue: Boolean,
        val isPrimitiveValue: Boolean,
        val isArrayValue: Boolean,
        val annotationsValue: List<LsiAnnotation>,
        val lsiClassValue: LsiClass?,
        val typeParametersValue: List<LsiType>,
        val componentTypeValue: LsiType?
    ) : LsiType {
        override val annotations: List<LsiAnnotation>
            get() = annotationsValue
        override val isCollectionType: Boolean
            get() = qualifiedName.isLsiCollectionLikeQualifiedName()
        override val isNullable: Boolean
            get() = isNullableValue
        override val typeParameters: List<LsiType>
            get() = typeParametersValue
        override val isPrimitive: Boolean
            get() = isPrimitiveValue
        override val componentType: LsiType?
            get() = componentTypeValue
        override val isArray: Boolean
            get() = isArrayValue
        override val lsiClass: LsiClass?
            get() = lsiClassValue
    }
}
