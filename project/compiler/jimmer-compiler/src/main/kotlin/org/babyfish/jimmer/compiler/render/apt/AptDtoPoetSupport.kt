package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import org.babyfish.jimmer.impl.util.DtoPropAccessor
import org.babyfish.jimmer.internal.FixedInputField
import org.babyfish.jimmer.internal.GeneratedBy
import java.util.ArrayList

internal object AptDtoPoetSupport {
    @JvmField
    val LIST_CLASS_NAME: ClassName = ClassName.get(List::class.java)

    @JvmField
    val ARRAY_LIST_CLASS_NAME: ClassName = ClassName.get(ArrayList::class.java)

    @JvmField
    val GENERATED_BY_CLASS_NAME: ClassName = ClassName.get(GeneratedBy::class.java)

    @JvmField
    val FIXED_INPUT_FIELD_CLASS_NAME: ClassName = ClassName.get(FixedInputField::class.java)

    @JvmField
    val DTO_PROP_ACCESSOR_CLASS_NAME: ClassName = ClassName.get(DtoPropAccessor::class.java)

    @JvmField
    val DTO_METADATA_CLASS_NAME: ClassName = ClassName.get(
        "org.babyfish.jimmer.sql.fetcher",
        "DtoMetadata",
    )

    @JvmStatic
    fun generatedAnnotation(): AnnotationSpec = AnnotationSpec
        .builder(GENERATED_BY_CLASS_NAME)
        .build()

    @JvmStatic
    fun generatedAnnotation(sourcePath: String): AnnotationSpec = AnnotationSpec
        .builder(GENERATED_BY_CLASS_NAME)
        .addMember("file", "\$S", sourcePath)
        .build()
}
