package site.addzero.lsi.poet

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeRef

enum class LsiPoetModifier {
    PUBLIC,
    PROTECTED,
    INTERNAL,
    PRIVATE,
    ABSTRACT,
    OPEN,
    FINAL,
    SEALED,
    STATIC,
    CONST,
    OVERRIDE,
    DEFAULT,
    SYNCHRONIZED,
    NATIVE,
    TRANSIENT,
    VOLATILE,
    INLINE,
    NOINLINE,
    CROSSINLINE,
    TAILREC,
    SUSPEND,
    OPERATOR,
    INFIX,
    EXTERNAL,
    LATEINIT,
    DATA,
    VALUE,
    INNER,
    COMPANION,
    VARARG,
}

enum class LsiPoetTypeKind {
    CLASS,
    INTERFACE,
    ENUM,
    OBJECT,
    ANNOTATION,
    RECORD,
}

/**
 * 声明名的源码表示方式。
 */
enum class LsiPoetNameStyle {
    IDENTIFIER,
    KOTLIN_ESCAPED,
}

/**
 * 生成文件名的源码约束。
 */
enum class LsiPoetFileNameStyle {
    JVM_IDENTIFIER,
    KOTLIN_SOURCE_STEM,
}

/**
 * 描述必须保留在生成文件中的显式导入。
 */
data class LsiPoetImport(
    val packageName: String,
    val simpleName: String,
) {
    init {
        require(packageName.isQualifiedName()) {
            "LSI Poet import package name must be a qualified JVM name: '$packageName'"
        }
        require(simpleName.isJvmIdentifier()) {
            "LSI Poet import simple name must be a JVM identifier: '$simpleName'"
        }
    }
}

sealed interface LsiPoetMember {
    val annotations: List<LsiPoetAnnotation>
    val modifiers: Set<LsiPoetModifier>
    val documentation: String?
}

data class LsiPoetFile(
    val language: LsiLanguage,
    val packageName: String,
    val fileName: String,
    val fileNameStyle: LsiPoetFileNameStyle = LsiPoetFileNameStyle.JVM_IDENTIFIER,
    val annotations: List<LsiPoetAnnotation> = emptyList(),
    val imports: List<LsiPoetImport> = emptyList(),
    val members: List<LsiPoetMember>,
    val headerComment: String? = null,
) {
    init {
        require(language == LsiLanguage.JAVA || language == LsiLanguage.KOTLIN) {
            "LSI Poet file language must be Java or Kotlin: $language"
        }
        require(packageName == packageName.trim()) {
            "LSI Poet package name cannot have surrounding whitespace: '$packageName'"
        }
        require(packageName.isEmpty() || packageName.isQualifiedName()) {
            "LSI Poet package name must be a qualified JVM name: '$packageName'"
        }
        when (fileNameStyle) {
            LsiPoetFileNameStyle.JVM_IDENTIFIER -> require(fileName.isJvmIdentifier()) {
                "LSI Poet file name must be a JVM identifier without an extension: '$fileName'"
            }
            LsiPoetFileNameStyle.KOTLIN_SOURCE_STEM -> {
                require(language == LsiLanguage.KOTLIN) {
                    "Kotlin source stem can only be used by a Kotlin LSI Poet file: '$fileName'"
                }
                require(fileName.isKotlinSourceStem()) {
                    "LSI Poet Kotlin source stem is invalid: '$fileName'"
                }
            }
        }
        require(imports.distinct() == imports) {
            "LSI Poet file cannot contain duplicate explicit imports: $fileName"
        }
        require(members.isNotEmpty()) { "LSI Poet file must contain at least one member: $fileName" }
    }
}

data class LsiPoetType(
    val name: String,
    val kind: LsiPoetTypeKind,
    val nameStyle: LsiPoetNameStyle = LsiPoetNameStyle.IDENTIFIER,
    override val annotations: List<LsiPoetAnnotation> = emptyList(),
    override val modifiers: Set<LsiPoetModifier> = emptySet(),
    override val documentation: String? = null,
    val typeParameters: List<LsiTypeParameter> = emptyList(),
    val superClass: LsiTypeRef? = null,
    val superClassConstructorArguments: List<LsiPoetCodeBlock> = emptyList(),
    val superInterfaces: List<LsiTypeRef> = emptyList(),
    val primaryConstructor: LsiPoetConstructor? = null,
    val enumConstants: List<LsiPoetEnumConstant> = emptyList(),
    val members: List<LsiPoetMember> = emptyList(),
) : LsiPoetMember {
    init {
        requirePoetDeclarationName(name, nameStyle, "type")
        require(typeParameters.map(LsiTypeParameter::id).distinct().size == typeParameters.size) {
            "LSI Poet type parameters cannot have duplicate ids: $name"
        }
        require(kind == LsiPoetTypeKind.ENUM || enumConstants.isEmpty()) {
            "Only LSI Poet enum type can declare enum constants: $name"
        }
        require(kind != LsiPoetTypeKind.INTERFACE || superClass == null) {
            "LSI Poet interface cannot declare a superclass: $name"
        }
        require(superClass != null || superClassConstructorArguments.isEmpty()) {
            "LSI Poet superclass constructor arguments require a superclass: $name"
        }
        require(kind != LsiPoetTypeKind.INTERFACE || primaryConstructor == null) {
            "LSI Poet interface cannot declare a primary constructor: $name"
        }
        require(LsiPoetModifier.COMPANION !in modifiers || kind == LsiPoetTypeKind.OBJECT) {
            "Only LSI Poet object can be a companion: $name"
        }
    }
}

data class LsiPoetEnumConstant(
    val name: String,
    val constructorArguments: List<LsiPoetCodeBlock> = emptyList(),
    val anonymousType: LsiPoetType? = null,
) {
    init {
        require(name.isJvmIdentifier()) { "LSI Poet enum constant name must be a JVM identifier: '$name'" }
        require(anonymousType == null || anonymousType.kind == LsiPoetTypeKind.CLASS) {
            "LSI Poet enum constant anonymous type must be a class: $name"
        }
    }
}

data class LsiPoetConstructor(
    override val annotations: List<LsiPoetAnnotation> = emptyList(),
    override val modifiers: Set<LsiPoetModifier> = emptySet(),
    override val documentation: String? = null,
    val typeParameters: List<LsiTypeParameter> = emptyList(),
    val parameters: List<LsiPoetParameter> = emptyList(),
    val thrownTypes: List<LsiTypeRef> = emptyList(),
    val body: LsiPoetCodeBlock = LsiPoetCodeBlock.EMPTY,
    val delegationCall: LsiPoetDelegationCall? = null,
) : LsiPoetMember {
    init {
        require(typeParameters.map(LsiTypeParameter::id).distinct().size == typeParameters.size) {
            "LSI Poet constructor type parameters cannot have duplicate ids"
        }
        require(parameters.map(LsiPoetParameter::name).distinct().size == parameters.size) {
            "LSI Poet constructor parameters cannot have duplicate names"
        }
        require(parameters.dropLast(1).none { parameter -> LsiPoetModifier.VARARG in parameter.modifiers }) {
            "LSI Poet vararg constructor parameter must be last"
        }
    }
}

data class LsiPoetDelegationCall(
    val target: LsiPoetDelegationTarget,
    val arguments: List<LsiPoetCodeBlock>,
)

enum class LsiPoetDelegationTarget {
    THIS,
    SUPER,
}

data class LsiPoetFunction(
    val name: String,
    val nameStyle: LsiPoetNameStyle = LsiPoetNameStyle.IDENTIFIER,
    override val annotations: List<LsiPoetAnnotation> = emptyList(),
    override val modifiers: Set<LsiPoetModifier> = emptySet(),
    override val documentation: String? = null,
    val typeParameters: List<LsiTypeParameter> = emptyList(),
    /**
     * 仅对 Kotlin 源码生效的具体化类型参数。
     */
    val reifiedTypeParameterIds: Set<LsiSymbolId> = emptySet(),
    val receiverType: LsiTypeRef? = null,
    val parameters: List<LsiPoetParameter> = emptyList(),
    val returnType: LsiTypeRef? = null,
    val thrownTypes: List<LsiTypeRef> = emptyList(),
    val body: LsiPoetCodeBlock = LsiPoetCodeBlock.EMPTY,
    val bodyStyle: LsiPoetBodyStyle = LsiPoetBodyStyle.BLOCK,
) : LsiPoetMember {
    init {
        requirePoetDeclarationName(name, nameStyle, "function")
        require(typeParameters.map(LsiTypeParameter::id).distinct().size == typeParameters.size) {
            "LSI Poet function type parameters cannot have duplicate ids: $name"
        }
        require(typeParameters.map(LsiTypeParameter::id).containsAll(reifiedTypeParameterIds)) {
            "LSI Poet reified type parameters must be declared by the function: $name"
        }
        require(reifiedTypeParameterIds.isEmpty() || LsiPoetModifier.INLINE in modifiers) {
            "LSI Poet reified type parameters require an inline function: $name"
        }
        require(parameters.map(LsiPoetParameter::name).distinct().size == parameters.size) {
            "LSI Poet function parameters cannot have duplicate names: $name"
        }
        require(parameters.dropLast(1).none { parameter -> LsiPoetModifier.VARARG in parameter.modifiers }) {
            "LSI Poet vararg function parameter must be last: $name"
        }
        require(bodyStyle != LsiPoetBodyStyle.EXPRESSION || !body.isEmpty) {
            "LSI Poet expression function body cannot be empty: $name"
        }
    }
}

/**
 * 描述函数或访问器主体的源码结构，不把具体 Poet 的格式对象泄露到共享模型。
 */
enum class LsiPoetBodyStyle {
    BLOCK,
    EXPRESSION,
}

data class LsiPoetParameter(
    val name: String,
    val type: LsiTypeRef,
    val nameStyle: LsiPoetNameStyle = LsiPoetNameStyle.IDENTIFIER,
    val annotations: List<LsiPoetAnnotation> = emptyList(),
    val modifiers: Set<LsiPoetModifier> = emptySet(),
    val defaultValue: LsiPoetCodeBlock? = null,
) {
    init {
        requirePoetDeclarationName(name, nameStyle, "parameter")
        require(LsiPoetModifier.VARARG !in modifiers || defaultValue == null) {
            "LSI Poet vararg parameter cannot declare a default value: $name"
        }
    }
}

data class LsiPoetField(
    val name: String,
    val type: LsiTypeRef,
    override val annotations: List<LsiPoetAnnotation> = emptyList(),
    override val modifiers: Set<LsiPoetModifier> = emptySet(),
    override val documentation: String? = null,
    val initializer: LsiPoetCodeBlock? = null,
    val typeReferenceStyle: LsiPoetTypeReferenceStyle = LsiPoetTypeReferenceStyle.IMPORTED,
) : LsiPoetMember {
    init {
        require(name.isJvmIdentifier()) { "LSI Poet field name must be a JVM identifier: '$name'" }
    }
}

data class LsiPoetProperty(
    val name: String,
    val type: LsiTypeRef,
    val mutable: Boolean,
    val nameStyle: LsiPoetNameStyle = LsiPoetNameStyle.IDENTIFIER,
    override val annotations: List<LsiPoetAnnotation> = emptyList(),
    override val modifiers: Set<LsiPoetModifier> = emptySet(),
    override val documentation: String? = null,
    val initializer: LsiPoetCodeBlock? = null,
    val receiverType: LsiTypeRef? = null,
    val getter: LsiPoetAccessor? = null,
    val setter: LsiPoetAccessor? = null,
) : LsiPoetMember {
    init {
        requirePoetDeclarationName(name, nameStyle, "property")
        require(mutable || setter == null) {
            "Immutable LSI Poet property cannot declare a setter: $name"
        }
    }
}

data class LsiPoetAccessor(
    val annotations: List<LsiPoetAnnotation> = emptyList(),
    val modifiers: Set<LsiPoetModifier> = emptySet(),
    val setterParameterName: String = "value",
    val setterParameterNameStyle: LsiPoetNameStyle = LsiPoetNameStyle.IDENTIFIER,
    val parameterAnnotations: List<LsiPoetAnnotation> = emptyList(),
    val body: LsiPoetCodeBlock = LsiPoetCodeBlock.EMPTY,
    val bodyStyle: LsiPoetBodyStyle = LsiPoetBodyStyle.BLOCK,
) {
    init {
        requirePoetDeclarationName(setterParameterName, setterParameterNameStyle, "setter parameter")
        require(bodyStyle != LsiPoetBodyStyle.EXPRESSION || !body.isEmpty) {
            "LSI Poet expression accessor body cannot be empty"
        }
    }
}

data class LsiPoetInitializerBlock(
    val static: Boolean,
    val body: LsiPoetCodeBlock,
    override val annotations: List<LsiPoetAnnotation> = emptyList(),
    override val documentation: String? = null,
) : LsiPoetMember {
    override val modifiers: Set<LsiPoetModifier> = if (static) {
        setOf(LsiPoetModifier.STATIC)
    } else {
        emptySet()
    }
}

private fun String.isQualifiedName(): Boolean {
    return split('.').all(String::isJvmIdentifier)
}

private fun String.isJvmIdentifier(): Boolean {
    if (isEmpty() || !Character.isJavaIdentifierStart(first())) {
        return false
    }
    return drop(1).all(Character::isJavaIdentifierPart)
}

private fun requirePoetDeclarationName(
    name: String,
    nameStyle: LsiPoetNameStyle,
    declarationKind: String,
) {
    when (nameStyle) {
        LsiPoetNameStyle.IDENTIFIER -> require(name.isJvmIdentifier()) {
            "LSI Poet $declarationKind name must be a JVM identifier: '$name'"
        }
        LsiPoetNameStyle.KOTLIN_ESCAPED -> require(name.isKotlinEscapedIdentifier()) {
            "LSI Poet escaped Kotlin $declarationKind name is invalid: '$name'"
        }
    }
}

private fun String.isKotlinEscapedIdentifier(): Boolean {
    return isNotBlank() && none { character ->
        character == '`' || character == '\n' || character == '\r'
    }
}

private fun String.isKotlinSourceStem(): Boolean {
    return isNotBlank() && this == trim() && this != "." && this != ".." && none { character ->
        character == '/' || character == '\\' || character == '\n' || character == '\r' || character == '\u0000'
    }
}
