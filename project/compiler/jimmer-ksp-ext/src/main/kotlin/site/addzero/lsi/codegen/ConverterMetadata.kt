package site.addzero.lsi.codegen

import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiTypeName
import java.lang.IllegalStateException

private const val CONVERTER_INTERFACE = "org.babyfish.jimmer.jackson.Converter"

open class ConverterMetadata(
    val sourceTypeName: LsiTypeName,
    val targetTypeName: LsiTypeName
) {
    open fun toListMetadata(): ConverterMetadata =
        ListMetadata(
            listTypeName(sourceTypeName),
            listTypeName(targetTypeName)
        )

    override fun hashCode(): Int {
        return sourceTypeName.hashCode() xor targetTypeName.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || this::class != other::class) {
            return false
        }
        val metadata = other as ConverterMetadata
        return sourceTypeName == metadata.sourceTypeName &&
            targetTypeName == metadata.targetTypeName
    }

    override fun toString(): String {
        return "ConverterMetadata(sourceType = $sourceTypeName, targetType = $targetTypeName)"
    }

    private class ListMetadata(
        sourceTypeName: LsiTypeName,
        targetTypeName: LsiTypeName
    ) : ConverterMetadata(
        sourceTypeName,
        targetTypeName
    ) {
        override fun toListMetadata(): ConverterMetadata =
            throw IllegalStateException("The current metadata is already list metadata")
    }
}

fun converterMetadataOf(declaration: LsiClass): ConverterMetadata {
    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../util/ConverterMetadata.converterMetadataOf
    // 迁移说明：Converter 泛型提取改为按接口 FQ 名驱动，减少该工具对 jimmer-core Java 类型字面量的硬依赖
    val result = GenericParser("converter", declaration, CONVERTER_INTERFACE).parse()
    return ConverterMetadata(
        result.argumentLsiTypeNames[0],
        result.argumentLsiTypeNames[1]
    )
}

private fun listTypeName(elementType: LsiTypeName): LsiParameterizedTypeName =
    LsiParameterizedTypeName(
        rawType = LsiClassName.bestGuess("kotlin.collections.List"),
        typeArguments = listOf(elementType)
    )
