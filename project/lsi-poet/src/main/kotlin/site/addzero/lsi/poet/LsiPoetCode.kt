package site.addzero.lsi.poet

import site.addzero.lsi.model.LsiTypeRef

/**
 * 构成代码块的语言无关占位片段。
 */
sealed interface LsiPoetCodePart {
    data class Text(val value: String) : LsiPoetCodePart

    data class Type(val value: LsiTypeRef) : LsiPoetCodePart

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

data class LsiPoetCodeBlock(
    val parts: List<LsiPoetCodePart>,
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

class LsiPoetCodeBuilder internal constructor() {
    private val parts = mutableListOf<LsiPoetCodePart>()

    fun text(value: String) {
        if (value.isNotEmpty()) {
            parts += LsiPoetCodePart.Text(value)
        }
    }

    fun type(value: LsiTypeRef) {
        parts += LsiPoetCodePart.Type(value)
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
        parts += block.parts
    }

    fun build(): LsiPoetCodeBlock = LsiPoetCodeBlock(parts.toList())
}
