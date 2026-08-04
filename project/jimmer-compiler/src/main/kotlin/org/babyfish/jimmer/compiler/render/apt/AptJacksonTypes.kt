package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.ClassName

internal class AptJacksonTypes(
    @JvmField val jsonIgnore: ClassName,
    @JvmField val jsonSerialize: ClassName,
    @JvmField val jsonDeserialize: ClassName,
)
