package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import org.babyfish.jimmer.impl.util.DtoPropAccessor
import org.babyfish.jimmer.internal.FixedInputField
import org.babyfish.jimmer.internal.GeneratedBy

internal val JVM_FIELD_CLASS_NAME = JvmField::class.asClassName()

internal val JVM_STATIC_CLASS_NAME = JvmStatic::class.asClassName()

internal val FIXED_INPUT_FIELD_CLASS_NAME = FixedInputField::class.asClassName()

internal val DTO_METADATA_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.fetcher",
    "DtoMetadata",
)

internal val DTO_PROP_ACCESSOR = DtoPropAccessor::class.asClassName()

private val GENERATED_BY_CLASS_NAME = GeneratedBy::class.asClassName()

internal fun generatedAnnotation(): AnnotationSpec =
    AnnotationSpec.builder(GENERATED_BY_CLASS_NAME)
        .build()

internal fun generatedAnnotation(className: ClassName): AnnotationSpec =
    AnnotationSpec.builder(GENERATED_BY_CLASS_NAME)
        .addMember("type = %T::class", className)
        .build()

internal fun generatedAnnotation(sourcePath: String, mutable: Boolean): AnnotationSpec =
    AnnotationSpec
        .builder(GENERATED_BY_CLASS_NAME)
        .addMember(
            "file = %S, prompt = %S",
            sourcePath,
            if (mutable) {
                "The current DTO type is mutable. If you need to make it immutable, " +
                    "please remove the ksp argument `jimmer.dto.mutable`"
            } else {
                "The current DTO type is immutable. If you need to make it mutable, " +
                    "please set the ksp argument `jimmer.dto.mutable` to the string \"text\""
            },
        )
        .build()
