package org.babyfish.jimmer.compiler.tuple

import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.toJvmReferenceType
import site.addzero.lsi.poet.LsiPoetArtifact
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationValue
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetCodeBuilder
import site.addzero.lsi.poet.LsiPoetConstructor
import site.addzero.lsi.poet.LsiPoetField
import site.addzero.lsi.poet.LsiPoetFile
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetParameter
import site.addzero.lsi.poet.LsiPoetProperty
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.poet.LsiPoetTypeKind

internal fun TypedTuplePrecompiledSchema.toLsiPoetArtifacts(
    workspace: LsiWorkspace,
): List<LsiPoetArtifact> {
    return tuples.map { tuple -> tuple.toLsiPoet(workspace) }
}

private fun TypedTupleType.toLsiPoet(workspace: LsiWorkspace): LsiPoetArtifact {
    val language = when (platform) {
        TypedTuplePlatform.JAVA -> LsiLanguage.JAVA
        TypedTuplePlatform.KOTLIN -> LsiLanguage.KOTLIN
    }
    val originatingSymbols = setOf(id)
    val dependencySymbols = dependencies.symbolIds.toSet()
    return LsiPoetArtifact(
        file = LsiPoetFile(
            language = language,
            packageName = packageName,
            fileName = mapperSimpleName,
            members = listOf(mapperType()),
        ),
        aggregationMode = ArtifactAggregationMode.ISOLATING,
        originatingSymbols = originatingSymbols,
        originatingSources = workspace.originatingSources(originatingSymbols),
        dependencySymbols = dependencySymbols,
        dependencySources = workspace.originatingSources(dependencySymbols),
    )
}

private fun TypedTupleType.mapperType(): LsiPoetType {
    return when (platform) {
        TypedTuplePlatform.JAVA -> javaMapperType()
        TypedTuplePlatform.KOTLIN -> kotlinMapperType()
    }
}

private fun TypedTupleType.javaMapperType(): LsiPoetType {
    val tupleType = declaredType(qualifiedName)
    val mapperType = declaredType(mapperQualifiedName)
    return LsiPoetType(
        name = mapperSimpleName,
        kind = LsiPoetTypeKind.CLASS,
        modifiers = setOf(LsiPoetModifier.PUBLIC),
        superInterfaces = listOf(tupleMapperType(tupleType)),
        members = buildList {
            add(javaSelectionsField())
            add(javaSelectionsConstructor())
            add(javaGetSelectionsFunction())
            add(javaCreateTupleFunction(tupleType))
            add(javaFirstPropertyFunction(mapperType))
            properties.drop(1).forEach { property ->
                add(javaBuilderType(property, mapperType))
            }
        },
    )
}

private fun TypedTupleType.kotlinMapperType(): LsiPoetType {
    val tupleType = declaredType(qualifiedName)
    val mapperType = declaredType(mapperQualifiedName)
    return LsiPoetType(
        name = mapperSimpleName,
        kind = LsiPoetTypeKind.CLASS,
        primaryConstructor = LsiPoetConstructor(
            modifiers = setOf(LsiPoetModifier.PRIVATE),
            parameters = listOf(LsiPoetParameter("selections", KOTLIN_SELECTION_ARRAY_TYPE)),
        ),
        superInterfaces = listOf(tupleMapperType(tupleType)),
        members = buildList {
            add(
                LsiPoetProperty(
                    name = "selections",
                    type = KOTLIN_SELECTION_ARRAY_TYPE,
                    mutable = false,
                    modifiers = setOf(LsiPoetModifier.PRIVATE),
                    initializer = code { name("selections") },
                )
            )
            add(kotlinGetSelectionsFunction())
            add(kotlinCreateTupleFunction(tupleType))
            properties.drop(1).forEach { property ->
                add(kotlinBuilderType(property, mapperType))
            }
            add(kotlinCompanionType(mapperType))
        },
    )
}

private fun TypedTupleType.javaSelectionsField(): LsiPoetField {
    return LsiPoetField(
        name = "selections",
        type = JAVA_SELECTION_ARRAY_TYPE,
        modifiers = setOf(
            LsiPoetModifier.PRIVATE,
            LsiPoetModifier.FINAL,
        ),
    )
}

private fun TypedTupleType.javaSelectionsConstructor(): LsiPoetConstructor {
    return LsiPoetConstructor(
        parameters = listOf(LsiPoetParameter("selections", JAVA_SELECTION_ARRAY_TYPE)),
        body = code {
            statement {
                text("this.selections = ")
                name("selections")
            }
        },
    )
}

private fun TypedTupleType.javaGetSelectionsFunction(): LsiPoetFunction {
    return LsiPoetFunction(
        name = "getSelections",
        modifiers = setOf(
            LsiPoetModifier.PUBLIC,
            LsiPoetModifier.OVERRIDE,
        ),
        returnType = listType(SELECTION_STAR_TYPE),
        body = code {
            returnValue {
                type(COLLECTIONS_TYPE)
                text(".unmodifiableList(")
                type(ARRAYS_TYPE)
                text(".asList(")
                name("selections")
                text("))")
            }
        },
    )
}

private fun TypedTupleType.javaCreateTupleFunction(tupleType: LsiTypeRef): LsiPoetFunction {
    return LsiPoetFunction(
        name = "createTuple",
        modifiers = setOf(
            LsiPoetModifier.PUBLIC,
            LsiPoetModifier.OVERRIDE,
        ),
        parameters = listOf(LsiPoetParameter("args", JAVA_ARGUMENT_ARRAY_TYPE)),
        returnType = tupleType,
        body = javaCreateTupleBody(tupleType),
    )
}

private fun TypedTupleType.javaCreateTupleBody(tupleType: LsiTypeRef): LsiPoetCodeBlock {
    return when (val plan = construction) {
        is TypedTupleJavaPositionalPlan -> javaPositionalTupleBody(plan, tupleType)
        is TypedTupleJavaSetterPlan -> javaSetterTupleBody(plan, tupleType)
        else -> error("Java typed tuple '${id.value}' has unsupported construction plan '$plan'")
    }
}

private fun TypedTupleType.javaPositionalTupleBody(
    plan: TypedTupleJavaPositionalPlan,
    tupleType: LsiTypeRef,
): LsiPoetCodeBlock {
    return code {
        returnValue {
            text("new ")
            type(tupleType)
            text("(")
            line()
            indent {
                plan.arguments.forEachIndexed { index, argument ->
                    if (index != 0) {
                        text(",")
                        line()
                    }
                    val property = properties[argument.propertyIndex]
                    text("(")
                    type(property.type.toJvmReferenceType())
                    text(")")
                    name("args")
                    text("[")
                    literal(property.index.toString())
                    text("]")
                }
            }
            line()
            text(")")
        }
    }
}

private fun TypedTupleType.javaSetterTupleBody(
    plan: TypedTupleJavaSetterPlan,
    tupleType: LsiTypeRef,
): LsiPoetCodeBlock {
    return code {
        statement {
            type(tupleType)
            text(" __tuple = new ")
            type(tupleType)
            text("()")
        }
        plan.assignments.forEach { assignment ->
            val property = properties[assignment.propertyIndex]
            statement {
                name("__tuple")
                text(".")
                name(assignment.setterName)
                text("((")
                type(property.type.toJvmReferenceType())
                text(")")
                name("args")
                text("[")
                literal(property.index.toString())
                text("])")
            }
        }
        returnValue {
            name("__tuple")
        }
    }
}

private fun TypedTupleType.javaFirstPropertyFunction(mapperType: LsiTypeRef): LsiPoetFunction {
    val property = properties.first()
    val returnType = stepType(property, mapperType)
    return LsiPoetFunction(
        name = property.name,
        modifiers = setOf(
            LsiPoetModifier.PUBLIC,
            LsiPoetModifier.STATIC,
        ),
        parameters = listOf(
            LsiPoetParameter("selection", javaSelectionType(property)),
        ),
        returnType = returnType,
        body = code {
            statement {
                type(JAVA_SELECTION_ARRAY_TYPE)
                text(" selections = new ")
                type(SELECTION_STAR_TYPE)
                text("[")
                literal(properties.size.toString())
                text("]")
            }
            selectionAssignment(property)
            returnValue {
                text("new ")
                type(returnType)
                text("(selections)")
            }
        },
    )
}

private fun TypedTupleType.javaBuilderType(
    property: TypedTupleProperty,
    mapperType: LsiTypeRef,
): LsiPoetType {
    val builderSimpleName = requireNotNull(property.builderSimpleName)
    val returnType = stepType(property, mapperType)
    return LsiPoetType(
        name = builderSimpleName,
        kind = LsiPoetTypeKind.CLASS,
        modifiers = setOf(
            LsiPoetModifier.PUBLIC,
            LsiPoetModifier.STATIC,
        ),
        members = listOf(
            javaSelectionsField(),
            javaSelectionsConstructor(),
            LsiPoetFunction(
                name = property.name,
                modifiers = setOf(LsiPoetModifier.PUBLIC),
                parameters = listOf(
                    LsiPoetParameter("selection", javaSelectionType(property)),
                ),
                returnType = returnType,
                body = code {
                    selectionAssignment(property)
                    returnValue {
                        text("new ")
                        type(returnType)
                        text("(selections)")
                    }
                },
            ),
        ),
    )
}

private fun TypedTupleType.kotlinGetSelectionsFunction(): LsiPoetFunction {
    return LsiPoetFunction(
        name = "getSelections",
        annotations = listOf(UNCHECKED_CAST_SUPPRESSION),
        modifiers = setOf(LsiPoetModifier.OVERRIDE),
        returnType = listType(SELECTION_STAR_TYPE),
        body = code {
            returnValue {
                type(COLLECTIONS_TYPE)
                text(".unmodifiableList(listOf(*")
                name("selections")
                text(" as ")
                type(KOTLIN_NON_NULL_SELECTION_ARRAY_TYPE)
                text("))")
            }
        },
    )
}

private fun TypedTupleType.kotlinCreateTupleFunction(tupleType: LsiTypeRef): LsiPoetFunction {
    return LsiPoetFunction(
        name = "createTuple",
        modifiers = setOf(LsiPoetModifier.OVERRIDE),
        parameters = listOf(LsiPoetParameter("args", KOTLIN_ARGUMENT_ARRAY_TYPE)),
        returnType = tupleType,
        body = kotlinCreateTupleBody(tupleType),
    )
}

private fun TypedTupleType.kotlinCreateTupleBody(tupleType: LsiTypeRef): LsiPoetCodeBlock {
    val plan = construction as? TypedTupleKotlinNamedPlan
        ?: error("Kotlin typed tuple '${id.value}' has unsupported construction plan '$construction'")
    return code {
        returnValue {
            type(tupleType)
            text("(")
            line()
            indent {
                plan.arguments.forEachIndexed { index, argument ->
                    if (index != 0) {
                        text(",")
                        line()
                    }
                    val property = properties[argument.propertyIndex]
                    name(argument.parameterName)
                    text(" = ")
                    name("args")
                    text("[")
                    literal(property.index.toString())
                    text("] as ")
                    type(property.type)
                }
            }
            line()
            text(")")
        }
    }
}

private fun TypedTupleType.kotlinBuilderType(
    property: TypedTupleProperty,
    mapperType: LsiTypeRef,
): LsiPoetType {
    val builderSimpleName = requireNotNull(property.builderSimpleName)
    val returnType = stepType(property, mapperType)
    return LsiPoetType(
        name = builderSimpleName,
        kind = LsiPoetTypeKind.CLASS,
        primaryConstructor = LsiPoetConstructor(
            modifiers = setOf(LsiPoetModifier.INTERNAL),
            parameters = listOf(LsiPoetParameter("selections", KOTLIN_SELECTION_ARRAY_TYPE)),
        ),
        members = listOf(
            LsiPoetProperty(
                name = "selections",
                type = KOTLIN_SELECTION_ARRAY_TYPE,
                mutable = false,
                modifiers = setOf(LsiPoetModifier.PRIVATE),
                initializer = code { name("selections") },
            ),
            LsiPoetFunction(
                name = property.name,
                parameters = listOf(
                    LsiPoetParameter("selection", kotlinSelectionType(property)),
                ),
                returnType = returnType,
                body = code {
                    selectionAssignment(property)
                    returnValue {
                        type(returnType)
                        text("(selections)")
                    }
                },
            ),
        ),
    )
}

private fun TypedTupleType.kotlinCompanionType(mapperType: LsiTypeRef): LsiPoetType {
    val property = properties.first()
    val returnType = stepType(property, mapperType)
    return LsiPoetType(
        name = "Companion",
        kind = LsiPoetTypeKind.OBJECT,
        modifiers = setOf(LsiPoetModifier.COMPANION),
        members = listOf(
            LsiPoetFunction(
                name = property.name,
                parameters = listOf(
                    LsiPoetParameter("selection", kotlinSelectionType(property)),
                ),
                returnType = returnType,
                body = code {
                    statement {
                        text("val selections = arrayOfNulls<")
                        type(SELECTION_STAR_TYPE)
                        text(">(")
                        literal(properties.size.toString())
                        text(")")
                    }
                    selectionAssignment(property)
                    returnValue {
                        type(returnType)
                        text("(selections)")
                    }
                },
            ),
        ),
    )
}

private fun TypedTupleType.stepType(
    property: TypedTupleProperty,
    mapperType: LsiTypeRef,
): LsiTypeRef {
    return if (property.nextStepTypeName == mapperSimpleName) {
        mapperType
    } else {
        declaredType("$mapperQualifiedName.${property.nextStepTypeName}")
    }
}

private fun javaSelectionType(property: TypedTupleProperty): LsiTypeRef {
    return selectionType(property.type.toJvmReferenceType())
}

private fun kotlinSelectionType(property: TypedTupleProperty): LsiTypeRef {
    return selectionType(property.type)
}

private fun selectionType(valueType: LsiTypeRef): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = SELECTION_ID,
        arguments = listOf(LsiTypeArgument.invariant(valueType)),
    )
}

private fun tupleMapperType(tupleType: LsiTypeRef): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = TUPLE_MAPPER_ID,
        arguments = listOf(LsiTypeArgument.invariant(tupleType)),
    )
}

private fun listType(elementType: LsiTypeRef): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = LIST_ID,
        arguments = listOf(LsiTypeArgument.invariant(elementType)),
    )
}

private fun LsiPoetCodeBuilder.selectionAssignment(property: TypedTupleProperty) {
    statement {
        name("selections")
        text("[")
        literal(property.index.toString())
        text("] = ")
        name("selection")
    }
}

private fun code(
    block: LsiPoetCodeBuilder.() -> Unit,
): LsiPoetCodeBlock {
    return LsiPoetCodeBlock.build(block)
}

private fun declaredType(qualifiedName: String): LsiDeclaredType {
    return LsiDeclaredType(LsiSymbolId.type(qualifiedName))
}

private val SELECTION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.Selection")
private val TUPLE_MAPPER_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.runtime.TupleMapper")
private val LIST_ID = LsiSymbolId.type("java.util.List")
private val COLLECTIONS_TYPE = declaredType("java.util.Collections")
private val ARRAYS_TYPE = declaredType("java.util.Arrays")
private val OBJECT_TYPE = declaredType("java.lang.Object")
private val SUPPRESS_ID = LsiSymbolId.type("kotlin.Suppress")
private val SELECTION_STAR_TYPE = LsiDeclaredType(
    declarationId = SELECTION_ID,
    arguments = listOf(LsiTypeArgument.STAR),
)
private val NULLABLE_SELECTION_STAR_TYPE = SELECTION_STAR_TYPE.copy(
    nullability = LsiNullability.NULLABLE,
)
private val JAVA_SELECTION_ARRAY_TYPE = LsiArrayType(SELECTION_STAR_TYPE)
private val KOTLIN_SELECTION_ARRAY_TYPE = LsiArrayType(NULLABLE_SELECTION_STAR_TYPE)
private val KOTLIN_NON_NULL_SELECTION_ARRAY_TYPE = LsiArrayType(SELECTION_STAR_TYPE)
private val JAVA_ARGUMENT_ARRAY_TYPE = LsiArrayType(OBJECT_TYPE)
private val KOTLIN_ARGUMENT_ARRAY_TYPE = LsiArrayType(
    OBJECT_TYPE.copy(nullability = LsiNullability.NULLABLE),
)
private val UNCHECKED_CAST_SUPPRESSION = LsiPoetAnnotation(
    type = SUPPRESS_ID,
    arguments = listOf(
        LsiPoetAnnotationArgument.Positional(
            LsiPoetAnnotationValue.StringValue("UNCHECKED_CAST")
        ),
    ),
)
