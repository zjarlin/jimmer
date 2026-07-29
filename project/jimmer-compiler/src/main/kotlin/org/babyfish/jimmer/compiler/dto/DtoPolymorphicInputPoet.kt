package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranchKind
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.mergedType
import site.addzero.lsi.jimmer.dto.selectedPolymorphicInputDiscriminatorPropOrNull
import site.addzero.lsi.jimmer.dto.serializerValueAccessorName
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodeBuilder
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.referencedTypeIds
import site.addzero.lsi.poet.toLsiPoetTypeNames

/** 将类型多态输入分支的判别值校验降低为可由两端 Poet 渲染的代码块。 */
internal fun DtoType.toTypedPolymorphicInputDiscriminatorValidationPoetCodeBlock(
    targetLanguage: LsiLanguage,
    branch: DtoPolymorphicBranch,
    discriminatorProp: DtoBaseProp,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    generatedDtoTypeName: LsiPoetTypeName,
): LsiPoetCodeBlock {
    require(graph.typesById[id] === this) {
        "DTO polymorphic input branch does not belong to this graph: ${id.value}"
    }
    require(branch.kind == DtoPolymorphicBranchKind.TYPE && branch.mergedType(graph) === this) {
        "DTO typed polymorphic input branch does not match generated type: ${id.value}"
    }
    require(graph.propsById[discriminatorProp.id] === discriminatorProp) {
        "DTO polymorphic input discriminator does not belong to this graph: ${discriminatorProp.id.value}"
    }
    require(selectedPolymorphicInputDiscriminatorPropOrNull(graph, immutableSchema) === discriminatorProp) {
        "DTO polymorphic input discriminator does not match selected property: ${discriminatorProp.id.value}"
    }
    val baseTypeId = requireNotNull(baseTypeId) {
        "DTO polymorphic input branch requires an immutable base type: ${id.value}"
    }
    require(branch.targetBaseTypeId == baseTypeId) {
        "DTO polymorphic input branch target does not match its immutable base type: ${id.value}"
    }
    val baseType = requireNotNull(immutableSchema.typesById[baseTypeId]) {
        "No immutable base type '${baseTypeId.value}' for DTO polymorphic input branch: ${id.value}"
    }
    val discriminatorValue = baseType.discriminatorValue ?: return LsiPoetCodeBlock.EMPTY
    val rootTypeId = baseType.inheritanceRootTypeId ?: baseType.id
    require(immutableSchema.typesById.containsKey(rootTypeId)) {
        "No immutable inheritance root '${rootTypeId.value}' for DTO polymorphic input branch: ${id.value}"
    }
    val accessorName = discriminatorProp.serializerValueAccessorName(
        targetLanguage = targetLanguage,
        graph = graph,
        immutableSchema = immutableSchema,
    )
    return when (targetLanguage) {
        LsiLanguage.JAVA -> javaTypedDiscriminatorValidation(
            accessorName = accessorName,
            discriminatorValue = discriminatorValue,
            rootTypeId = rootTypeId,
            entityTypeName = baseType.qualifiedName,
            generatedDtoTypeName = generatedDtoTypeName,
        )
        LsiLanguage.KOTLIN -> kotlinTypedDiscriminatorValidation(
            accessorName = accessorName,
            discriminatorValue = discriminatorValue,
            rootTypeId = rootTypeId,
            entityTypeName = baseType.qualifiedName,
            generatedDtoTypeName = generatedDtoTypeName,
        )
        else -> error("DTO polymorphic input validation requires Java or Kotlin: $targetLanguage")
    }
}

/** 为多态输入代码块解析完整源码类型名。 */
internal fun LsiWorkspace.dtoPolymorphicInputPoetTypeNames(
    codeBlock: LsiPoetCodeBlock,
): List<LsiPoetTypeName> {
    return toLsiPoetTypeNames(
        typeIds = codeBlock.referencedTypeIds,
        additional = POLYMORPHIC_INPUT_RUNTIME_TYPE_NAMES,
    )
}

private fun javaTypedDiscriminatorValidation(
    accessorName: String,
    discriminatorValue: String,
    rootTypeId: LsiSymbolId,
    entityTypeName: String,
    generatedDtoTypeName: LsiPoetTypeName,
): LsiPoetCodeBlock {
    return LsiPoetCodeBlock.build {
        beginControlFlow {
            text("if (!")
            type(LsiDeclaredType(JAVA_OBJECTS_TYPE_ID))
            text(".equals(")
            javaDiscriminatorAccessor(accessorName)
            text(", ")
            runtimeDiscriminatorValue(rootTypeId, discriminatorValue, LsiLanguage.JAVA)
            text("))")
        }
        statement {
            text("throw new ")
            type(LsiDeclaredType(ILLEGAL_ARGUMENT_EXCEPTION_TYPE_ID))
            text("(")
            string("Discriminator value \"")
            text(" + ")
            javaDiscriminatorAccessor(accessorName)
            text(" + ")
            string(
                "\" does not match polymorphic input DTO branch \"${generatedDtoTypeName.canonicalName}\" " +
                    "whose entity type is \"$entityTypeName\""
            )
            text(")")
        }
        endControlFlow()
    }
}

private fun kotlinTypedDiscriminatorValidation(
    accessorName: String,
    discriminatorValue: String,
    rootTypeId: LsiSymbolId,
    entityTypeName: String,
    generatedDtoTypeName: LsiPoetTypeName,
): LsiPoetCodeBlock {
    return LsiPoetCodeBlock.build {
        beginControlFlow {
            text("if (")
            name(accessorName)
            text(" != ")
            runtimeDiscriminatorValue(rootTypeId, discriminatorValue, LsiLanguage.KOTLIN)
            text(")")
        }
        statement {
            text("throw ")
            type(LsiDeclaredType(ILLEGAL_ARGUMENT_EXCEPTION_TYPE_ID))
            text("(")
            string("Discriminator value \"")
            text(" + ")
            name(accessorName)
            text(" + ")
            string(
                "\" does not match polymorphic input DTO branch \"${generatedDtoTypeName.canonicalName}\" " +
                    "whose entity type is \"$entityTypeName\""
            )
            text(")")
        }
        endControlFlow()
    }
}

private fun LsiPoetCodeBuilder.javaDiscriminatorAccessor(accessorName: String) {
    text("this.")
    name(accessorName)
    text("()")
}

private fun LsiPoetCodeBuilder.runtimeDiscriminatorValue(
    rootTypeId: LsiSymbolId,
    discriminatorValue: String,
    targetLanguage: LsiLanguage,
) {
    type(LsiDeclaredType(RUNTIME_IMMUTABLE_TYPE_ID))
    text(".get(")
    type(LsiDeclaredType(rootTypeId))
    when (targetLanguage) {
        LsiLanguage.JAVA -> text(".class).getInheritanceInfo().discriminatorValue(")
        LsiLanguage.KOTLIN -> text("::class.java).inheritanceInfo!!.discriminatorValue(")
        else -> error("DTO polymorphic input validation requires Java or Kotlin: $targetLanguage")
    }
    string(discriminatorValue)
    text(")")
}

private val JAVA_OBJECTS_TYPE_ID = LsiSymbolId.type("java.util.Objects")
private val RUNTIME_IMMUTABLE_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.meta.ImmutableType")
private val ILLEGAL_ARGUMENT_EXCEPTION_TYPE_ID = LsiSymbolId.type("java.lang.IllegalArgumentException")

private val POLYMORPHIC_INPUT_RUNTIME_TYPE_NAMES = listOf(
    JimmerDtoPoetTypeNames.create("java.util", listOf("Objects")),
    JimmerDtoPoetTypeNames.create("org.babyfish.jimmer.meta", listOf("ImmutableType")),
    JimmerDtoPoetTypeNames.create("java.lang", listOf("IllegalArgumentException")),
)
