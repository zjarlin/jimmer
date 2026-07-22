package site.addzero.lsi.poet

import site.addzero.lsi.model.LsiTypeRef

/**
 * 构成代码块的语言无关占位片段。
 */
sealed interface LsiPoetCodePart {
    data class Text(val value: String) : LsiPoetCodePart

    data class Type(
        val value: LsiTypeRef,
        val referenceStyle: LsiPoetTypeReferenceStyle = LsiPoetTypeReferenceStyle.IMPORTED,
    ) : LsiPoetCodePart

    /**
     * 指向 Kotlin 包级声明，由 KotlinPoet 负责导入和关键字转义。
     */
    data class TopLevelMember(
        val packageName: String,
        val simpleName: String,
        val extension: Boolean,
    ) : LsiPoetCodePart {
        init {
            require(packageName == packageName.trim()) {
                "LSI Poet top-level member package name cannot have surrounding whitespace: '$packageName'"
            }
            require(packageName.isEmpty() || packageName.isQualifiedJvmName()) {
                "LSI Poet top-level member package name must be a qualified JVM name: '$packageName'"
            }
            require(simpleName.isJvmIdentifier()) {
                "LSI Poet top-level member simple name must be a JVM identifier: '$simpleName'"
            }
        }
    }

    data class Name(val value: String) : LsiPoetCodePart {
        init {
            require(value.isNotBlank()) { "LSI Poet name cannot be blank" }
        }
    }

    data class Literal(val value: String) : LsiPoetCodePart {
        init {
            require(value.isNotBlank()) { "LSI Poet literal cannot be blank" }
        }
    }

    data class StringLiteral(val value: String) : LsiPoetCodePart

    data class CharacterLiteral(val value: Char) : LsiPoetCodePart

    /**
     * 由具体 Poet 实现补齐语句终止符和换行。
     */
    data class Statement(val value: LsiPoetCodeBlock) : LsiPoetCodePart

    /**
     * 由具体 Poet 实现选择块状返回或表达式函数。
     */
    data class Return(val value: LsiPoetCodeBlock?) : LsiPoetCodePart

    /**
     * 表达带花括号主体、可选尾部调用以及语句完成方式的结构。
     */
    data class BracedExpression(
        val completion: LsiPoetBracedExpressionCompletion,
        val prefix: LsiPoetCodeBlock,
        val body: LsiPoetCodeBlock,
        val suffix: LsiPoetCodeBlock,
    ) : LsiPoetCodePart

    /**
     * 开始一个由具体 Poet 实现负责排版的控制流。
     */
    data class BeginControlFlow(val header: LsiPoetCodeBlock) : LsiPoetCodePart

    /**
     * 切换到同一控制流的下一分支。
     */
    data class NextControlFlow(val header: LsiPoetCodeBlock) : LsiPoetCodePart

    data object EndControlFlow : LsiPoetCodePart

    data object NewLine : LsiPoetCodePart

    data object Indent : LsiPoetCodePart

    data object Unindent : LsiPoetCodePart
}

enum class LsiPoetBracedExpressionCompletion {
    RETURN,
    STATEMENT,
}

/**
 * 控制代码块中的类型引用是否参与文件导入缩写。
 */
enum class LsiPoetTypeReferenceStyle {
    IMPORTED,
    FULLY_QUALIFIED,
    /**
     * 省略当前包名但保留外部类型名，例如 `BookDraft.Producer`。
     */
    SAME_PACKAGE_OUTER_QUALIFIED,
}

data class LsiPoetCodeBlock(
    val parts: List<LsiPoetCodePart>,
    val indentation: LsiPoetCodeBlockIndentation = LsiPoetCodeBlockIndentation.PLATFORM_DEFAULT,
) {
    val isEmpty: Boolean
        get() = parts.isEmpty()

    init {
        var indentation = 0
        var controlFlowDepth = 0
        parts.forEach { part ->
            when (part) {
                is LsiPoetCodePart.BeginControlFlow -> controlFlowDepth++
                LsiPoetCodePart.EndControlFlow -> {
                    controlFlowDepth--
                    require(controlFlowDepth >= 0) {
                        "LSI Poet code block cannot end a control flow before it begins"
                    }
                }
                LsiPoetCodePart.Indent -> indentation++
                is LsiPoetCodePart.NextControlFlow -> require(controlFlowDepth > 0) {
                    "LSI Poet code block cannot continue a control flow before it begins"
                }
                LsiPoetCodePart.Unindent -> {
                    indentation--
                    require(indentation >= 0) {
                        "LSI Poet code block cannot unindent before an indent"
                    }
                }
                else -> Unit
            }
        }
        require(indentation == 0) {
            "LSI Poet code block must close every indent"
        }
        require(controlFlowDepth == 0) {
            "LSI Poet code block must close every control flow"
        }
    }

    companion object {
        val EMPTY: LsiPoetCodeBlock = LsiPoetCodeBlock(emptyList())

        fun build(block: LsiPoetCodeBuilder.() -> Unit): LsiPoetCodeBlock {
            return LsiPoetCodeBuilder().apply(block).build()
        }
    }
}

/**
 * 控制外围声明是否可以为多行代码块追加续行缩进。
 */
enum class LsiPoetCodeBlockIndentation {
    PLATFORM_DEFAULT,
    EXPLICIT,
}

class LsiPoetCodeBuilder internal constructor() {
    private val parts = mutableListOf<LsiPoetCodePart>()
    private var indentation = LsiPoetCodeBlockIndentation.PLATFORM_DEFAULT

    /**
     * 表示代码块已经完整描述每一层缩进，平台不得再追加外围续行缩进。
     */
    fun preserveExplicitIndentation() {
        indentation = LsiPoetCodeBlockIndentation.EXPLICIT
    }

    fun text(value: String) {
        if (value.isNotEmpty()) {
            parts += LsiPoetCodePart.Text(value)
        }
    }

    fun type(
        value: LsiTypeRef,
        referenceStyle: LsiPoetTypeReferenceStyle = LsiPoetTypeReferenceStyle.IMPORTED,
    ) {
        parts += LsiPoetCodePart.Type(value, referenceStyle)
    }

    fun topLevelMember(
        packageName: String,
        simpleName: String,
        extension: Boolean,
    ) {
        parts += LsiPoetCodePart.TopLevelMember(packageName, simpleName, extension)
    }

    fun name(value: String) {
        parts += LsiPoetCodePart.Name(value)
    }

    fun literal(value: String) {
        parts += LsiPoetCodePart.Literal(value)
    }

    fun string(value: String) {
        parts += LsiPoetCodePart.StringLiteral(value)
    }

    fun character(value: Char) {
        parts += LsiPoetCodePart.CharacterLiteral(value)
    }

    fun line() {
        parts += LsiPoetCodePart.NewLine
    }

    fun statement(block: LsiPoetCodeBuilder.() -> Unit) {
        parts += LsiPoetCodePart.Statement(LsiPoetCodeBlock.build(block))
    }

    fun returnValue(block: LsiPoetCodeBuilder.() -> Unit) {
        parts += LsiPoetCodePart.Return(LsiPoetCodeBlock.build(block))
    }

    fun returnVoid() {
        parts += LsiPoetCodePart.Return(null)
    }

    fun returnBracedExpression(
        prefix: LsiPoetCodeBuilder.() -> Unit,
        body: LsiPoetCodeBuilder.() -> Unit,
        suffix: LsiPoetCodeBuilder.() -> Unit = {},
    ) {
        bracedExpression(LsiPoetBracedExpressionCompletion.RETURN, prefix, body, suffix)
    }

    fun statementBracedExpression(
        prefix: LsiPoetCodeBuilder.() -> Unit,
        body: LsiPoetCodeBuilder.() -> Unit,
        suffix: LsiPoetCodeBuilder.() -> Unit = {},
    ) {
        bracedExpression(LsiPoetBracedExpressionCompletion.STATEMENT, prefix, body, suffix)
    }

    private fun bracedExpression(
        completion: LsiPoetBracedExpressionCompletion,
        prefix: LsiPoetCodeBuilder.() -> Unit,
        body: LsiPoetCodeBuilder.() -> Unit,
        suffix: LsiPoetCodeBuilder.() -> Unit,
    ) {
        parts += LsiPoetCodePart.BracedExpression(
            completion = completion,
            prefix = LsiPoetCodeBlock.build(prefix),
            body = LsiPoetCodeBlock.build(body),
            suffix = LsiPoetCodeBlock.build(suffix),
        )
    }

    fun beginControlFlow(block: LsiPoetCodeBuilder.() -> Unit) {
        parts += LsiPoetCodePart.BeginControlFlow(LsiPoetCodeBlock.build(block))
    }

    fun nextControlFlow(block: LsiPoetCodeBuilder.() -> Unit) {
        parts += LsiPoetCodePart.NextControlFlow(LsiPoetCodeBlock.build(block))
    }

    fun endControlFlow() {
        parts += LsiPoetCodePart.EndControlFlow
    }

    fun indent(block: LsiPoetCodeBuilder.() -> Unit) {
        parts += LsiPoetCodePart.Indent
        block()
        parts += LsiPoetCodePart.Unindent
    }

    fun add(block: LsiPoetCodeBlock) {
        if (block.indentation == LsiPoetCodeBlockIndentation.EXPLICIT) {
            indentation = LsiPoetCodeBlockIndentation.EXPLICIT
        }
        parts += block.parts
    }

    fun build(): LsiPoetCodeBlock = LsiPoetCodeBlock(parts.toList(), indentation)
}

private fun String.isQualifiedJvmName(): Boolean {
    return split('.').all(String::isJvmIdentifier)
}

private fun String.isJvmIdentifier(): Boolean {
    if (isEmpty() || !Character.isJavaIdentifierStart(first())) {
        return false
    }
    return drop(1).all(Character::isJavaIdentifierPart)
}
