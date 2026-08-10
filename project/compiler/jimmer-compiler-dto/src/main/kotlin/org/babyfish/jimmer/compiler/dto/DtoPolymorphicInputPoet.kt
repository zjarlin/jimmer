package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.generatedJavaDraftSetterName
import site.addzero.lsi.jimmer.knownConcreteEntityTypesOf
import site.addzero.lsi.jimmer.packageName
import site.addzero.lsi.jimmer.simpleName
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import org.babyfish.jimmer.dto.compiler.DtoPolymorphicBranchKind
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.dtoValueAccessorName
import site.addzero.lsi.jimmer.dto.mergedType
import site.addzero.lsi.jimmer.dto.selectedPolymorphicInputDiscriminatorPropOrNull
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.model.LsiTypeReferenceStyle
import site.addzero.lsi.model.generatedTopLevelTypeName
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.model.toLsiTypeNames

/** 将类型多态输入分支的判别值校验降低为可由两端 Poet 渲染的代码块。 */
internal fun DtoType.toTypedPolymorphicInputDiscriminatorValidationPoetCodeBlock(
    targetLanguage: LsiLanguage,
    branch: DtoPolymorphicBranch,
    discriminatorProp: DtoBaseProp,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    generatedDtoTypeName: LsiTypeName,
): LsiCodeBlock {
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
    val discriminatorValue = baseType.discriminatorValue ?: return LsiCodeBlock.EMPTY
    val rootTypeId = baseType.inheritanceRootTypeId ?: baseType.id
    require(immutableSchema.typesById.containsKey(rootTypeId)) {
        "No immutable inheritance root '${rootTypeId.value}' for DTO polymorphic input branch: ${id.value}"
    }
    val accessorName = discriminatorProp.dtoValueAccessorName(
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

/** 将默认多态输入分支的实体判别和 Draft 创建降低为共享代码块。 */
internal fun DtoType.toDefaultPolymorphicInputBodyPoetCodeBlock(
    targetLanguage: LsiLanguage,
    branch: DtoPolymorphicBranch,
    discriminatorProp: DtoBaseProp,
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
    workspace: LsiWorkspace,
    generatedDtoTypeName: LsiTypeName,
    idParameterName: String? = null,
    blockParameterName: String? = null,
): LsiCodeBlock {
    require(graph.typesById[id] === this) {
        "DTO polymorphic input branch does not belong to this graph: ${id.value}"
    }
    require(branch.kind == DtoPolymorphicBranchKind.DEFAULT && branch.mergedType(graph) === this) {
        "DTO default polymorphic input branch does not match generated type: ${id.value}"
    }
    require(branch.targetBaseTypeId == null) {
        "DTO default polymorphic input branch cannot declare a target base type: ${id.value}"
    }
    require(graph.propsById[discriminatorProp.id] === discriminatorProp) {
        "DTO polymorphic input discriminator does not belong to this graph: ${discriminatorProp.id.value}"
    }
    require(selectedPolymorphicInputDiscriminatorPropOrNull(graph, immutableSchema) === discriminatorProp) {
        "DTO polymorphic input discriminator does not match selected property: ${discriminatorProp.id.value}"
    }
    require(targetLanguage == LsiLanguage.JAVA || targetLanguage == LsiLanguage.KOTLIN) {
        "Default polymorphic input body requires Java or Kotlin: $targetLanguage"
    }
    require(idParameterName == null || targetLanguage == LsiLanguage.JAVA) {
        "Kotlin default polymorphic input body cannot declare an id parameter"
    }
    require(blockParameterName == null || targetLanguage == LsiLanguage.KOTLIN) {
        "Java default polymorphic input body cannot declare a block parameter"
    }
    val baseTypeId = requireNotNull(baseTypeId) {
        "DTO polymorphic input branch requires an immutable base type: ${id.value}"
    }
    val baseType = requireNotNull(immutableSchema.typesById[baseTypeId]) {
        "No immutable base type '${baseTypeId.value}' for DTO polymorphic input branch: ${id.value}"
    }
    val rootTypeId = baseType.inheritanceRootTypeId ?: baseType.id
    require(immutableSchema.typesById.containsKey(rootTypeId)) {
        "No immutable inheritance root '${rootTypeId.value}' for DTO polymorphic input branch: ${id.value}"
    }
    val concreteTypes = immutableSchema.knownConcreteEntityTypesOf(baseType)
    val idProp = idParameterName?.let {
        requireNotNull(baseType.idPropId?.let(immutableSchema.propsById::get)) {
            "Immutable base type has no id property: ${baseType.id.value}"
        }
    }
    val accessorName = discriminatorProp.dtoValueAccessorName(
        targetLanguage = targetLanguage,
        graph = graph,
        immutableSchema = immutableSchema,
    )
    return LsiCodeBlock.build {
        concreteTypes.forEach { concreteType ->
            val discriminatorValue = concreteType.discriminatorValue ?: return@forEach
            beginControlFlow {
                if (targetLanguage == LsiLanguage.JAVA) {
                    text("if (")
                    type(LsiDeclaredType(JAVA_OBJECTS_TYPE_ID))
                    text(".equals(")
                    javaDiscriminatorAccessor(accessorName)
                    text(", ")
                    runtimeDiscriminatorValue(rootTypeId, discriminatorValue, targetLanguage)
                    text("))")
                } else {
                    text("if (")
                    name(accessorName)
                    text(" == ")
                    runtimeDiscriminatorValue(rootTypeId, discriminatorValue, targetLanguage)
                    text(")")
                }
            }
            if (targetLanguage == LsiLanguage.JAVA) {
                returnBracedExpression(
                    prefix = {
                        type(
                            concreteType.javaDraftType(),
                            concreteType.javaDraftTypeReferenceStyle(generatedDtoTypeName),
                        )
                        text(".$.produce(__draft ->")
                    },
                    body = {
                        statement {
                            text("this.__applyTo(__draft)")
                        }
                        if (idParameterName != null) {
                            val requiredIdProp = requireNotNull(idProp)
                            beginControlFlow { text("if ($idParameterName != null)") }
                            statement {
                                text("__draft.")
                                name(requiredIdProp.generatedJavaDraftSetterName(workspace))
                                text("($idParameterName)")
                            }
                            endControlFlow()
                        }
                    },
                    suffix = { text(")") },
                )
            } else {
                returnBracedExpression(
                    prefix = {
                        topLevelMember("org.babyfish.jimmer.kt", "new", extension = false)
                        text("(")
                        type(
                            LsiDeclaredType(concreteType.id),
                            concreteType.entityTypeReferenceStyle(generatedDtoTypeName),
                        )
                        text("::class).by")
                    },
                    body = {
                        statement { text("toEntityImpl(this)") }
                        blockParameterName?.let { parameterName ->
                            statement { text("$parameterName(this)") }
                        }
                    },
                )
            }
            endControlFlow()
        }
        statement {
            if (targetLanguage == LsiLanguage.JAVA) {
                text("throw new ")
            } else {
                text("throw ")
            }
            type(LsiDeclaredType(ILLEGAL_ARGUMENT_EXCEPTION_TYPE_ID))
            text("(")
            string("Illegal discriminator value \"")
            text(" + ")
            if (targetLanguage == LsiLanguage.JAVA) {
                javaDiscriminatorAccessor(accessorName)
            } else {
                name(accessorName)
            }
            text(" + ")
            string(
                "\" for polymorphic input DTO branch \"${generatedDtoTypeName.canonicalName}\""
            )
            text(")")
        }
    }
}

/** 为多态输入代码块解析完整源码类型名。 */
internal fun LsiWorkspace.dtoPolymorphicInputPoetTypeNames(
    codeBlock: LsiCodeBlock,
    immutableSchema: ImmutableSchema,
): List<LsiTypeName> {
    return toLsiTypeNames(
        typeIds = codeBlock.referencedTypeIds,
        additional = POLYMORPHIC_INPUT_RUNTIME_TYPE_NAMES + immutableSchema.types.flatMap { type ->
            listOf(
                generatedTopLevelTypeName(type.packageName, type.simpleName),
                generatedTopLevelTypeName(type.packageName, "${type.simpleName}Draft"),
            )
        },
    )
}

private fun ImmutableType.javaDraftType(): LsiDeclaredType {
    return LsiDeclaredType(LsiSymbolId.type("${qualifiedName}Draft"))
}

private fun ImmutableType.javaDraftTypeReferenceStyle(
    generatedDtoTypeName: LsiTypeName,
): LsiTypeReferenceStyle {
    return if (simpleName + "Draft" in generatedDtoTypeName.simpleNames) {
        LsiTypeReferenceStyle.FULLY_QUALIFIED
    } else {
        LsiTypeReferenceStyle.IMPORTED
    }
}

private fun ImmutableType.entityTypeReferenceStyle(
    generatedDtoTypeName: LsiTypeName,
): LsiTypeReferenceStyle {
    return if (simpleName in generatedDtoTypeName.simpleNames) {
        LsiTypeReferenceStyle.FULLY_QUALIFIED
    } else {
        LsiTypeReferenceStyle.IMPORTED
    }
}

private fun javaTypedDiscriminatorValidation(
    accessorName: String,
    discriminatorValue: String,
    rootTypeId: LsiSymbolId,
    entityTypeName: String,
    generatedDtoTypeName: LsiTypeName,
): LsiCodeBlock {
    return LsiCodeBlock.build {
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
    generatedDtoTypeName: LsiTypeName,
): LsiCodeBlock {
    return LsiCodeBlock.build {
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

private fun LsiCodeBuilder.javaDiscriminatorAccessor(accessorName: String) {
    text("this.")
    name(accessorName)
    text("()")
}

private fun LsiCodeBuilder.runtimeDiscriminatorValue(
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
