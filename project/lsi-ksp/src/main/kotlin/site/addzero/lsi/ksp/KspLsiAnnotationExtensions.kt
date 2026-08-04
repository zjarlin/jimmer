package site.addzero.lsi.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.AnnotationUseSiteTarget
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.NonExistLocation
import com.google.devtools.ksp.symbol.Origin
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue

fun KSAnnotation.toLsiAnnotation(
    resolver: Resolver,
    useSiteTarget: LsiAnnotationUseSiteTarget? = null,
): LsiAnnotation {
    return KspLsiAnnotationContext(resolver).toLsiAnnotation(this, useSiteTarget)
}

internal class KspLsiAnnotationContext(
    resolver: Resolver,
    private val typeContext: KspLsiTypeContext = KspLsiTypeContext(resolver),
) {

    fun toLsiAnnotations(
        annotations: Sequence<KSAnnotation>,
        useSiteTarget: LsiAnnotationUseSiteTarget?,
    ): List<LsiAnnotation> {
        return annotations.filterNot(KSAnnotation::isUnresolvedKotlinCompilerTypeAnnotation).map { annotation ->
            toLsiAnnotation(annotation, useSiteTarget)
        }.toList()
    }

    fun toLsiAnnotation(
        annotation: KSAnnotation,
        useSiteTarget: LsiAnnotationUseSiteTarget?,
    ): LsiAnnotation {
        val resolvedAnnotationType = annotation.annotationType.resolve()
        val annotationType = resolvedAnnotationType.declaration
        val qualifiedName = requireNotNull(annotationType.qualifiedName?.asString()) {
            "KSP annotation type must have a qualified name: " +
                "shortName=${annotation.shortName.asString()}, " +
                "resolvedType=$resolvedAnnotationType, " +
                "declaration=${annotationType.simpleName.asString()}, " +
                "origin=${annotation.origin}, " +
                "location=${annotation.location}"
        }
        val defaultArguments = annotation.defaultArguments.associateBy(KSValueArgument::argumentName)
        val explicitArgumentsInSourceOrder = annotation.arguments.filter { argument ->
            defaultArguments[argument.argumentName()] !== argument
        }
        val explicitArguments = explicitArgumentsInSourceOrder.associateBy(KSValueArgument::argumentName)
        val arguments = linkedMapOf<String, KSValueArgument>()
        arguments.putAll(defaultArguments)
        arguments.putAll(explicitArguments)
        return LsiAnnotation(
            type = LsiSymbolId.type(qualifiedName),
            arguments = arguments
                .toSortedMap()
                .mapValues { (name, argument) ->
                    LsiAnnotationArgument(
                        value = toLsiAnnotationValue(argument.value),
                        origin = if (defaultArguments[name] === argument) {
                            LsiAnnotationArgumentOrigin.DEFAULT
                        } else {
                            LsiAnnotationArgumentOrigin.EXPLICIT
                        },
                    )
            },
            useSiteTarget = annotation.useSiteTarget?.toLsiUseSiteTarget() ?: useSiteTarget,
            explicitArgumentNamesInSourceOrder = explicitArgumentsInSourceOrder.map(
                KSValueArgument::argumentName,
            ),
        )
    }

    private fun toLsiAnnotationValue(value: Any?): LsiAnnotationValue {
        return when (value) {
            is Boolean -> LsiAnnotationValue.BooleanValue(value)
            is Byte -> LsiAnnotationValue.ByteValue(value)
            is Short -> LsiAnnotationValue.ShortValue(value)
            is Int -> LsiAnnotationValue.IntValue(value)
            is Long -> LsiAnnotationValue.LongValue(value)
            is Float -> LsiAnnotationValue.FloatValue(value)
            is Double -> LsiAnnotationValue.DoubleValue(value)
            is Char -> LsiAnnotationValue.CharValue(value)
            is String -> LsiAnnotationValue.StringValue(value)
            is Enum<*> -> LsiAnnotationValue.EnumValue(
                enumType = LsiSymbolId.type(
                    value.declaringJavaClass.canonicalName ?: value.declaringJavaClass.name
                ),
                entryName = value.name,
            )
            is KSType -> value.toLsiTypeOrEnumValue()
            is KSClassDeclaration -> value.toLsiEnumValue()
            is KSAnnotation -> LsiAnnotationValue.NestedAnnotationValue(
                toLsiAnnotation(value, null),
            )
            is List<*> -> LsiAnnotationValue.ArrayValue(
                value.map(::toLsiAnnotationValue),
            )
            is Array<*> -> LsiAnnotationValue.ArrayValue(
                value.map(::toLsiAnnotationValue),
            )
            else -> error("Unsupported KSP annotation value: ${value?.javaClass?.name}")
        }
    }

    private fun KSType.toLsiTypeOrEnumValue(): LsiAnnotationValue {
        val classDeclaration = declaration as? KSClassDeclaration
        if (classDeclaration?.classKind == ClassKind.ENUM_ENTRY) {
            return classDeclaration.toLsiEnumValue()
        }
        return LsiAnnotationValue.ClassValue(typeContext.toLsiType(this))
    }

    private fun KSClassDeclaration.toLsiEnumValue(): LsiAnnotationValue.EnumValue {
        require(classKind == ClassKind.ENUM_ENTRY) {
            "Unsupported KSP class annotation value: ${qualifiedName?.asString()}"
        }
        val enumType = parentDeclaration as? KSClassDeclaration
            ?: error("KSP enum entry must have an enum owner: ${simpleName.asString()}")
        val enumQualifiedName = requireNotNull(enumType.qualifiedName?.asString()) {
            "KSP enum type must have a qualified name"
        }
        return LsiAnnotationValue.EnumValue(
            enumType = LsiSymbolId.type(enumQualifiedName),
            entryName = simpleName.asString(),
        )
    }
}

private fun KSAnnotation.isUnresolvedKotlinCompilerTypeAnnotation(): Boolean {
    val type = annotationType.resolve()
    return type.isError &&
        origin == Origin.KOTLIN_LIB &&
        location == NonExistLocation &&
        shortName.asString() in UNRESOLVED_KOTLIN_COMPILER_TYPE_ANNOTATIONS
}

private fun KSValueArgument.argumentName(): String {
    return requireNotNull(name?.asString()) { "KSP annotation argument must have a name" }
}

private fun AnnotationUseSiteTarget.toLsiUseSiteTarget(): LsiAnnotationUseSiteTarget {
    return kspAnnotationUseSiteTarget(name)
}

internal fun kspAnnotationUseSiteTarget(name: String): LsiAnnotationUseSiteTarget {
    return when (name) {
        "FILE" -> LsiAnnotationUseSiteTarget.FILE
        "PROPERTY" -> LsiAnnotationUseSiteTarget.PROPERTY
        "FIELD" -> LsiAnnotationUseSiteTarget.FIELD
        "GET" -> LsiAnnotationUseSiteTarget.GETTER
        "SET" -> LsiAnnotationUseSiteTarget.SETTER
        "RECEIVER" -> LsiAnnotationUseSiteTarget.RECEIVER
        "PARAM" -> LsiAnnotationUseSiteTarget.PARAMETER
        "SETPARAM" -> LsiAnnotationUseSiteTarget.SET_PARAMETER
        "DELEGATE" -> LsiAnnotationUseSiteTarget.DELEGATE
        "ALL" -> LsiAnnotationUseSiteTarget.ALL
        else -> error("Unsupported KSP annotation use-site target: $name")
    }
}

private val UNRESOLVED_KOTLIN_COMPILER_TYPE_ANNOTATIONS = setOf(
    "ExtensionFunctionType",
)
