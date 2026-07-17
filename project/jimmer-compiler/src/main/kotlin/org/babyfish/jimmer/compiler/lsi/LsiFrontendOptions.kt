package org.babyfish.jimmer.compiler.lsi

data class LsiFrontendOptions(
    val keepJavaBooleanGetterIsPrefix: Boolean,
) {
    companion object {
        const val KEEP_IS_PREFIX_OPTION = "jimmer.keepIsPrefix"

        fun from(options: Map<String, String>): LsiFrontendOptions {
            return LsiFrontendOptions(
                keepJavaBooleanGetterIsPrefix = options[KEEP_IS_PREFIX_OPTION] == "true",
            )
        }
    }
}
