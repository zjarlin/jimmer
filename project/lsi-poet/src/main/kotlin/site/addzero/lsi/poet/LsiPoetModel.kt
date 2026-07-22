package site.addzero.lsi.poet

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.model.LsiAnnotation
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
    REIFIED,
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

sealed interface LsiPoetMember {
    val annotations: List<LsiAnnotation>
    val modifiers: Set<LsiPoetModifier>
    val documentation: String?
}

data class LsiPoetFile(
    val language: LsiLanguage,
    val packageName: String,
    val fileName: String,
    val annotations: List<LsiAnnotation> = emptyList(),
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
        require(fileName.isJvmIdentifier()) {
            "LSI Poet file name must be a JVM identifier without an extension: '$fileName'"
        }
        require(members.isNotEmpty()) { "LSI Poet file must contain at least one member: $fileName" }
    }
}

data class LsiPoetType(
    val name: String,
    val kind: LsiPoetTypeKind,
    override val annotations: List<LsiAnnotation> = emptyList(),
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
        require(name.isJvmIdentifier()) { "LSI Poet type name must be a JVM identifier: '$name'" }
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
    override val annotations: List<LsiAnnotation> = emptyList(),
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
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val modifiers: Set<LsiPoetModifier> = emptySet(),
    override val documentation: String? = null,
    val typeParameters: List<LsiTypeParameter> = emptyList(),
    val receiverType: LsiTypeRef? = null,
    val parameters: List<LsiPoetParameter> = emptyList(),
    val returnType: LsiTypeRef? = null,
    val thrownTypes: List<LsiTypeRef> = emptyList(),
    val body: LsiPoetCodeBlock = LsiPoetCodeBlock.EMPTY,
) : LsiPoetMember {
    init {
        require(name.isJvmIdentifier()) { "LSI Poet function name must be a JVM identifier: '$name'" }
        require(typeParameters.map(LsiTypeParameter::id).distinct().size == typeParameters.size) {
            "LSI Poet function type parameters cannot have duplicate ids: $name"
        }
        require(parameters.map(LsiPoetParameter::name).distinct().size == parameters.size) {
            "LSI Poet function parameters cannot have duplicate names: $name"
        }
        require(parameters.dropLast(1).none { parameter -> LsiPoetModifier.VARARG in parameter.modifiers }) {
            "LSI Poet vararg function parameter must be last: $name"
        }
    }
}

data class LsiPoetParameter(
    val name: String,
    val type: LsiTypeRef,
    val annotations: List<LsiAnnotation> = emptyList(),
    val modifiers: Set<LsiPoetModifier> = emptySet(),
    val defaultValue: LsiPoetCodeBlock? = null,
) {
    init {
        require(name.isJvmIdentifier()) { "LSI Poet parameter name must be a JVM identifier: '$name'" }
        require(LsiPoetModifier.VARARG !in modifiers || defaultValue == null) {
            "LSI Poet vararg parameter cannot declare a default value: $name"
        }
    }
}

data class LsiPoetField(
    val name: String,
    val type: LsiTypeRef,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val modifiers: Set<LsiPoetModifier> = emptySet(),
    override val documentation: String? = null,
    val initializer: LsiPoetCodeBlock? = null,
) : LsiPoetMember {
    init {
        require(name.isJvmIdentifier()) { "LSI Poet field name must be a JVM identifier: '$name'" }
    }
}

data class LsiPoetProperty(
    val name: String,
    val type: LsiTypeRef,
    val mutable: Boolean,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val modifiers: Set<LsiPoetModifier> = emptySet(),
    override val documentation: String? = null,
    val initializer: LsiPoetCodeBlock? = null,
    val receiverType: LsiTypeRef? = null,
    val getter: LsiPoetAccessor? = null,
    val setter: LsiPoetAccessor? = null,
) : LsiPoetMember {
    init {
        require(name.isJvmIdentifier()) { "LSI Poet property name must be a JVM identifier: '$name'" }
        require(mutable || setter == null) {
            "Immutable LSI Poet property cannot declare a setter: $name"
        }
    }
}

data class LsiPoetAccessor(
    val annotations: List<LsiAnnotation> = emptyList(),
    val modifiers: Set<LsiPoetModifier> = emptySet(),
    val parameterAnnotations: List<LsiAnnotation> = emptyList(),
    val body: LsiPoetCodeBlock = LsiPoetCodeBlock.EMPTY,
)

data class LsiPoetInitializerBlock(
    val static: Boolean,
    val body: LsiPoetCodeBlock,
    override val annotations: List<LsiAnnotation> = emptyList(),
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
