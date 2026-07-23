package org.babyfish.jimmer.compiler.immutable

import site.addzero.lsi.jimmer.ImmutableDraftPatternFlag
import site.addzero.lsi.jimmer.ImmutableDraftValidationPlan
import site.addzero.lsi.jimmer.ImmutableDraftValidationStep

/** 返回正则校验在当前属性全部正则约束中的稳定序号。 */
internal fun ImmutableDraftValidationPlan.patternIndexOf(
    pattern: ImmutableDraftValidationStep.Pattern,
): Int {
    val index = builtInSteps
        .filterIsInstance<ImmutableDraftValidationStep.Pattern>()
        .indexOf(pattern)
    check(index >= 0) { "Immutable draft pattern must belong to its validation plan" }
    return index
}

/** 将共享正则标志降低为 JVM Pattern 使用的位掩码。 */
internal fun List<ImmutableDraftPatternFlag>.toJvmPatternFlagMask(): Int {
    return fold(0) { mask, flag -> mask or flag.jvmMask }
}

private val ImmutableDraftPatternFlag.jvmMask: Int
    get() = when (this) {
        ImmutableDraftPatternFlag.UNIX_LINES -> 1
        ImmutableDraftPatternFlag.CASE_INSENSITIVE -> 2
        ImmutableDraftPatternFlag.COMMENTS -> 4
        ImmutableDraftPatternFlag.MULTILINE -> 8
        ImmutableDraftPatternFlag.LITERAL -> 16
        ImmutableDraftPatternFlag.DOTALL -> 32
        ImmutableDraftPatternFlag.UNICODE_CASE -> 64
        ImmutableDraftPatternFlag.CANON_EQ -> 128
        ImmutableDraftPatternFlag.UNICODE_CHARACTER_CLASS -> 256
    }
