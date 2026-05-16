package site.addzero.lsi.codegen

import site.addzero.lsi.poet.LsiClassName

object JavaCodegenConstants {

    @JvmField
    val DRAFT_CONSUMER_CLASS_NAME = DRAFT_CONSUMER_LSI_CLASS_NAME

    @JvmField
    val LIST_CLASS_NAME = LsiClassName.bestGuess("java.util.List")
}
