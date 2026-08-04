package site.addzero.lsi.jimmer

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiWorkspace

/** 返回不可变属性在源码中冻结的 getter 名称。 */
fun ImmutableProp.sourceGetterName(workspace: LsiWorkspace): String {
    return (workspace[declarationId] as? LsiProperty)?.getterName ?: name
}

/** 返回不可变属性在 Draft 源码中的实际成员名。 */
fun ImmutableProp.generatedDraftCodegenName(workspace: LsiWorkspace): String {
    val declaration = workspace[declarationId] as? LsiProperty ?: return name
    if (declaration.origin.language != LsiLanguage.JAVA) {
        return name
    }
    val getterName = declaration.getterName
    if (getterName.startsWith("get") && getterName.length > 3 && getterName[3].isUpperCase()) {
        return getterName.substring(3).replaceFirstChar(Char::lowercaseChar)
    }
    val primitiveBoolean = type is LsiPrimitiveType &&
        !type.boxed &&
        type.kind == LsiPrimitiveKind.BOOLEAN
    if (
        primitiveBoolean &&
        getterName != name &&
        getterName.startsWith("is") &&
        getterName.length > 2 &&
        getterName[2].isUpperCase()
    ) {
        return getterName.substring(2).replaceFirstChar(Char::lowercaseChar)
    }
    return getterName
}

/** 返回不可变属性在 Java Draft 中生成的写入方法名。 */
fun ImmutableProp.generatedJavaDraftSetterName(workspace: LsiWorkspace): String {
    val codegenName = generatedDraftCodegenName(workspace)
    return "set" + codegenName.replaceFirstChar(Char::uppercaseChar)
}

/** 返回不可变属性在 Draft Producer 中生成的 slot 常量名。 */
fun ImmutableProp.generatedDraftSlotName(workspace: LsiWorkspace): String {
    return "SLOT_${generatedDraftCodegenName(workspace).toDraftConstantName()}"
}

private fun String.toDraftConstantName(): String {
    var previousUpper = true
    return buildString(length + 8) {
        for (character in this@toDraftConstantName) {
            val upper = character.isUpperCase()
            if (upper) {
                if (!previousUpper) {
                    append('_')
                }
                append(character)
            } else {
                append(character.uppercaseChar())
            }
            previousUpper = upper
        }
    }
}
