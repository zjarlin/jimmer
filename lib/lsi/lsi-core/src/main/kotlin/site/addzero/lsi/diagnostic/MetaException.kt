package site.addzero.lsi.diagnostic

import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.LsiEnumConstant
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.method.LsiMethod

/**
 * 覆盖来源：project/compiler/jimmer-ksp-ext/.../org.babyfish.jimmer.ksp.MetaException
 * 迁移说明：元编程诊断异常下沉到 `lsi-core`，让 APT/KSP/shared metadata extractor 统一依赖 LSI 诊断能力，
 * 避免该基础异常继续寄存在 KSP 扩展模块中
 */
open class MetaException private constructor(
    reason: String,
    cause: Throwable? = null,
    val anchor: LsiDiagnosticAnchor = UnknownLsiDiagnosticAnchor,
) : RuntimeException(reason, cause) {

    constructor(
        anchor: LsiDiagnosticAnchor,
        reason: String,
        cause: Throwable? = null
    ) : this(
        message(anchor, reason),
        cause,
        anchor
    )

    constructor(
        declaration: LsiClass,
        reason: String,
        cause: Throwable? = null
    ) : this(
        message(declaration, reason),
        cause,
        ClassLsiDiagnosticAnchor(declaration)
    )

    constructor(
        declaration: LsiField,
        reason: String,
        cause: Throwable? = null
    ) : this(
        message(declaration, reason),
        cause,
        FieldLsiDiagnosticAnchor(declaration)
    )

    constructor(
        declaration: LsiMethod,
        reason: String,
        cause: Throwable? = null
    ) : this(
        message(declaration, reason),
        cause,
        MethodLsiDiagnosticAnchor(declaration)
    )

    constructor(
        declaration: LsiEnumConstant,
        reason: String,
        cause: Throwable? = null
    ) : this(
        message(declaration, reason),
        cause,
        EnumConstantLsiDiagnosticAnchor(declaration)
    )

    companion object {

        @JvmStatic
        private fun message(declaration: LsiClass, reason: String): String =
            "Illegal type \"" +
                (declaration.qualifiedName ?: declaration.simpleName ?: "<unknown>") +
                "\", " +
                lowerFirstChar(reason)

        @JvmStatic
        private fun message(declaration: LsiField, reason: String): String =
            "Illegal property \"" +
                longName(declaration) +
                "\", " +
                lowerFirstChar(reason)

        @JvmStatic
        private fun message(declaration: LsiMethod, reason: String): String =
            "Illegal function \"" +
                longName(declaration) +
                "\", " +
                lowerFirstChar(reason)

        @JvmStatic
        private fun message(declaration: LsiEnumConstant, reason: String): String =
            "Illegal enum constant \"" +
                longName(declaration) +
                "\", " +
                lowerFirstChar(reason)

        @JvmStatic
        private fun longName(declaration: LsiField): String {
            val ownerName = declaration.declaringClass?.qualifiedName
                ?: declaration.declaringClass?.simpleName
                ?: "<unknown>"
            return "$ownerName.${declaration.name ?: "<unknown>"}"
        }

        @JvmStatic
        private fun longName(declaration: LsiMethod): String {
            val ownerName = declaration.declaringClass?.qualifiedName
                ?: declaration.declaringClass?.simpleName
                ?: "<unknown>"
            return "$ownerName.${declaration.name ?: "<unknown>"}"
        }

        @JvmStatic
        private fun longName(declaration: LsiEnumConstant): String {
            val ownerName = declaration.declaringClass?.qualifiedName
                ?: declaration.declaringClass?.simpleName
                ?: "<unknown>"
            return "$ownerName.${declaration.name ?: "<unknown>"}"
        }

        @JvmStatic
        private fun lowerFirstChar(reason: String): String =
            reason
                .trimStart()
                .let {
                    if (it.isEmpty()) {
                        it
                    } else if (it[0].isUpperCase()) {
                        it[0].lowercase() + it.substring(1)
                    } else {
                        it
                    }
                }

        @JvmStatic
        private fun message(anchor: LsiDiagnosticAnchor, reason: String): String =
            when (anchor.kind) {
                LsiDiagnosticAnchor.Kind.CLASS -> "Illegal type \"" +
                    (anchor.ownerQualifiedName ?: anchor.symbolName ?: "<unknown>") +
                    "\", " +
                    lowerFirstChar(reason)

                LsiDiagnosticAnchor.Kind.FIELD -> "Illegal property \"" +
                    longName(anchor) +
                    "\", " +
                    lowerFirstChar(reason)

                LsiDiagnosticAnchor.Kind.METHOD -> "Illegal function \"" +
                    longName(anchor) +
                    "\", " +
                    lowerFirstChar(reason)

                LsiDiagnosticAnchor.Kind.PARAMETER -> "Illegal parameter \"" +
                    longName(anchor) +
                    "\", " +
                    lowerFirstChar(reason)

                LsiDiagnosticAnchor.Kind.UNKNOWN -> lowerFirstChar(reason)
            }

        @JvmStatic
        private fun longName(anchor: LsiDiagnosticAnchor): String {
            val ownerName = anchor.ownerQualifiedName ?: "<unknown>"
            val symbolName = anchor.symbolName
            return if (symbolName.isNullOrEmpty()) {
                ownerName
            } else {
                "$ownerName.$symbolName"
            }
        }
    }

    private data class ClassLsiDiagnosticAnchor(
        private val declaration: LsiClass
    ) : LsiDiagnosticAnchor {
        override val kind: LsiDiagnosticAnchor.Kind
            get() = LsiDiagnosticAnchor.Kind.CLASS
        override val ownerQualifiedName: String?
            get() = declaration.qualifiedName
        override val symbolName: String?
            get() = declaration.simpleName
    }

    private data class FieldLsiDiagnosticAnchor(
        private val declaration: LsiField
    ) : LsiDiagnosticAnchor {
        override val kind: LsiDiagnosticAnchor.Kind
            get() = LsiDiagnosticAnchor.Kind.FIELD
        override val ownerQualifiedName: String?
            get() = declaration.declaringClass?.qualifiedName
        override val symbolName: String?
            get() = declaration.name
    }

    private data class MethodLsiDiagnosticAnchor(
        private val declaration: LsiMethod
    ) : LsiDiagnosticAnchor {
        override val kind: LsiDiagnosticAnchor.Kind
            get() = LsiDiagnosticAnchor.Kind.METHOD
        override val ownerQualifiedName: String?
            get() = declaration.declaringClass?.qualifiedName
        override val symbolName: String?
            get() = declaration.name
    }

    private data class EnumConstantLsiDiagnosticAnchor(
        private val declaration: LsiEnumConstant
    ) : LsiDiagnosticAnchor {
        override val kind: LsiDiagnosticAnchor.Kind
            get() = LsiDiagnosticAnchor.Kind.FIELD
        override val ownerQualifiedName: String?
            get() = declaration.declaringClass?.qualifiedName
        override val symbolName: String?
            get() = declaration.name
    }

    private object UnknownLsiDiagnosticAnchor : LsiDiagnosticAnchor {
        override val kind: LsiDiagnosticAnchor.Kind
            get() = LsiDiagnosticAnchor.Kind.UNKNOWN
        override val ownerQualifiedName: String?
            get() = null
        override val symbolName: String?
            get() = null
    }
}
