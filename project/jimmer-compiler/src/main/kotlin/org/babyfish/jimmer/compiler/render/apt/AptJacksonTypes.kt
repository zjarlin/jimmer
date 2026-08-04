package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.ClassName

internal class AptJacksonTypes(
    @JvmField val jsonIgnore: ClassName,
    @JvmField val jsonValue: ClassName,
    @JvmField val jsonPropertyOrder: ClassName,
    @JvmField val jsonFormat: ClassName,
    @JvmField val jsonSerializer: ClassName,
    @JvmField val jsonSerialize: ClassName,
    @JvmField val jsonDeserialize: ClassName,
    @JvmField val jsonPojoBuilder: ClassName,
    @JvmField val jsonNaming: ClassName,
    @JvmField val jsonGenerator: ClassName,
    @JvmField val serializerProvider: ClassName,
)
