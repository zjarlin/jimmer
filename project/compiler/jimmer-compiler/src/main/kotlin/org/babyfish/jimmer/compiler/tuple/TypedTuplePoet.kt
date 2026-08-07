package org.babyfish.jimmer.compiler.tuple

import site.addzero.lsi.codegen.classifyArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.tuple.TypedTupleJavaConstructorConstruction
import site.addzero.lsi.jimmer.tuple.TypedTupleJavaSetterConstruction
import site.addzero.lsi.jimmer.tuple.TypedTupleKotlinConstructorConstruction
import site.addzero.lsi.jimmer.tuple.TypedTupleBaseTableSelection
import site.addzero.lsi.jimmer.tuple.TypedTupleBaseTableSelectionKind
import site.addzero.lsi.jimmer.tuple.TypedTupleProperty
import site.addzero.lsi.jimmer.tuple.TypedTupleScalarCategory
import site.addzero.lsi.jimmer.tuple.TypedTupleSchema
import site.addzero.lsi.jimmer.tuple.TypedTupleType
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetAccessor
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
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.referencedTypeIds
import site.addzero.lsi.poet.toLsiPoetTypeNames

internal fun TypedTupleSchema.toLsiPoetArtifacts(
    workspace: LsiWorkspace,
): List<LsiPoetArtifact> {
    return tuples.flatMap { tuple -> tuple.toLsiPoet(workspace) }
}

private fun TypedTupleType.toLsiPoet(workspace: LsiWorkspace): List<LsiPoetArtifact> {
    val originatingSymbols = setOf(id)
    val originatingSources = workspace.originatingSources(originatingSymbols)
    val dependencySymbols = dependencies.symbolIds.toSet()
    val dependencySources = workspace.originatingSources(dependencySymbols)
    val files = when (sourceLanguage) {
        LsiLanguage.JAVA -> buildList {
            add(
                LsiPoetFile(
                    language = sourceLanguage,
                    packageName = packageName,
                    fileName = mapperSimpleName,
                    members = listOf(mapperType()),
                )
            )
            if (baseTableProjection != null) {
                add(
                    LsiPoetFile(
                        language = sourceLanguage,
                        packageName = packageName,
                        fileName = tableSimpleName,
                        members = listOf(javaBaseTableType()),
                    )
                )
            }
        }
        LsiLanguage.KOTLIN -> listOf(
            LsiPoetFile(
                language = sourceLanguage,
                packageName = packageName,
                fileName = mapperSimpleName,
                members = buildList {
                    add(mapperType())
                    if (baseTableProjection != null) {
                        add(kotlinBaseTableType())
                    }
                },
            )
        )
        LsiLanguage.UNKNOWN -> error("Typed tuple '${id.value}' has unknown source language")
    }
    val aggregationMode = classifyArtifactAggregationMode(
        originatingSymbols = originatingSymbols,
        originatingSources = originatingSources,
        dependencySources = dependencySources,
    )
    return files.map { file ->
        LsiPoetArtifact(
            file = file,
            typeNames = workspace.toLsiPoetTypeNames(
                typeIds = file.referencedTypeIds,
                additional = generatedTypeNames(),
            ),
            aggregationMode = aggregationMode,
            originatingSymbols = originatingSymbols,
            originatingSources = originatingSources,
            dependencySymbols = dependencySymbols,
            dependencySources = dependencySources,
        )
    }
}

private fun TypedTupleType.generatedTypeNames(): List<LsiPoetTypeName> {
    return buildList {
        addAll(BUILT_IN_TYPE_NAMES)
        add(
            LsiPoetTypeName(
                typeId = LsiSymbolId.type(mapperQualifiedName),
                packageName = packageName,
                simpleNames = listOf(mapperSimpleName),
            )
        )
        val projection = baseTableProjection
        if (projection != null) {
            add(topLevelGeneratedTypeName(tableQualifiedName))
            add(
                LsiPoetTypeName(
                    typeId = nullableTableType.declarationId,
                    packageName = packageName,
                    simpleNames = listOf(tableSimpleName, "Nullable"),
                )
            )
            projection.selections.mapNotNull(TypedTupleBaseTableSelection::entityTableTypeId)
                .distinct()
                .forEach { entityTableTypeId ->
                    add(topLevelGeneratedTypeName(entityTableTypeId.requireTypeQualifiedName()))
                }
        }
        properties.drop(1).forEach { property ->
            add(
                LsiPoetTypeName(
                    typeId = LsiSymbolId.type("$mapperQualifiedName.${property.builderSimpleName}"),
                    packageName = packageName,
                    simpleNames = listOf(mapperSimpleName, property.builderSimpleName),
                )
            )
        }
    }
}

private fun TypedTupleType.mapperType(): LsiPoetType {
    return when (sourceLanguage) {
        LsiLanguage.JAVA -> javaMapperType()
        LsiLanguage.KOTLIN -> kotlinMapperType()
        LsiLanguage.UNKNOWN -> error("Typed tuple '${id.value}' has unknown source language")
    }
}

private fun TypedTupleType.javaMapperType(): LsiPoetType {
    val tupleType = declaredType(qualifiedName)
    val mapperType = declaredType(mapperQualifiedName)
    return LsiPoetType(
        name = mapperSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        modifiers = setOf(LsiPoetModifier.PUBLIC),
        superInterfaces = buildList {
            add(tupleMapperType(tupleType))
            if (baseTableProjection != null) {
                add(declaredType(BASE_TABLE_PROJECTION_ID, tableType))
            }
        },
        members = buildList {
            add(javaSelectionsField())
            add(javaSelectionsConstructor())
            add(javaGetSelectionsFunction())
            if (baseTableProjection != null) {
                add(javaGetBaseTableFactoryFunction())
            }
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
        kind = LsiTypeDeclarationKind.CLASS,
        primaryConstructor = LsiPoetConstructor(
            modifiers = setOf(LsiPoetModifier.PRIVATE),
            parameters = listOf(LsiPoetParameter("selections", KOTLIN_SELECTION_ARRAY_TYPE)),
        ),
        superInterfaces = buildList {
            add(tupleMapperType(tupleType))
            if (baseTableProjection != null) {
                add(declaredType(K_BASE_TABLE_PROJECTION_ID, tableType, nullableTableType))
            }
        },
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
            if (baseTableProjection != null) {
                add(kotlinGetBaseTableFactoryFunction())
                add(kotlinGetSelectionLayoutFunction())
            }
            add(kotlinCreateTupleFunction(tupleType))
            properties.drop(1).forEach { property ->
                add(kotlinBuilderType(property, mapperType))
            }
            add(kotlinCompanionType(mapperType))
        },
    )
}

private fun TypedTupleType.javaBaseTableType(): LsiPoetType {
    val projection = requireNotNull(baseTableProjection)
    return LsiPoetType(
        name = tableSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        modifiers = setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.FINAL),
        superClass = declaredType(ABSTRACT_TYPED_BASE_TABLE_ID, tableType),
        primaryConstructor = LsiPoetConstructor(
            parameters = listOf(LsiPoetParameter("baseTable", BASE_TABLE_TYPE)),
            body = code {
                statement { text("super(baseTable)") }
            },
        ),
        members = buildList {
            add(
                LsiPoetField(
                    name = "FACTORY",
                    type = declaredType(BASE_TABLE_FACTORY_ID, tableType, tableType),
                    modifiers = setOf(LsiPoetModifier.STATIC, LsiPoetModifier.FINAL),
                    initializer = code {
                        type(BASE_TABLE_FACTORY_TYPE)
                        text(".of(")
                        type(tableType)
                        text("::new)")
                    },
                )
            )
            projection.selections.forEach { selection ->
                val property = properties[selection.propertyIndex]
                add(
                    LsiPoetFunction(
                        name = property.javaGetterName,
                        modifiers = setOf(LsiPoetModifier.PUBLIC),
                        returnType = javaBaseTableSelectionType(property, selection),
                        body = code {
                            returnValue {
                                text("selection(")
                                literal(selection.propertyIndex.toString())
                                text(")")
                            }
                        },
                    )
                )
            }
        },
    )
}

private fun TypedTupleType.kotlinBaseTableType(): LsiPoetType {
    val projection = requireNotNull(baseTableProjection)
    return LsiPoetType(
        name = tableSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        superClass = ABSTRACT_K_BASE_TABLE_TYPE,
        superClassConstructorArguments = listOf(code { name("baseTable") }),
        superInterfaces = listOf(declaredType(K_NON_NULL_BASE_TABLE_ID, nullableTableType)),
        primaryConstructor = LsiPoetConstructor(
            modifiers = setOf(LsiPoetModifier.INTERNAL),
            parameters = listOf(LsiPoetParameter("baseTable", BASE_TABLE_TYPE)),
        ),
        members = buildList {
            projection.selections.forEach { selection ->
                add(kotlinBaseTableProperty(selection, outerNullable = false))
            }
            addAll(kotlinWeakJoinFunctions(tableType))
            add(kotlinNullableBaseTableType(projection.selections))
            add(kotlinBaseTableCompanionType(projection.selections))
        },
    )
}

private fun TypedTupleType.kotlinNullableBaseTableType(
    selections: List<TypedTupleBaseTableSelection>,
): LsiPoetType {
    return LsiPoetType(
        name = "Nullable",
        kind = LsiTypeDeclarationKind.CLASS,
        superClass = ABSTRACT_K_BASE_TABLE_TYPE,
        superClassConstructorArguments = listOf(code { name("baseTable") }),
        superInterfaces = listOf(K_NULLABLE_BASE_TABLE_TYPE),
        primaryConstructor = LsiPoetConstructor(
            modifiers = setOf(LsiPoetModifier.INTERNAL),
            parameters = listOf(LsiPoetParameter("baseTable", BASE_TABLE_TYPE)),
        ),
        members = buildList {
            selections.forEach { selection ->
                add(kotlinBaseTableProperty(selection, outerNullable = true))
            }
            addAll(kotlinWeakJoinFunctions(nullableTableType))
        },
    )
}

private fun TypedTupleType.kotlinBaseTableProperty(
    selection: TypedTupleBaseTableSelection,
    outerNullable: Boolean,
): LsiPoetProperty {
    val property = properties[selection.propertyIndex]
    return LsiPoetProperty(
        name = property.name,
        type = kotlinBaseTableSelectionType(property, selection, outerNullable),
        mutable = false,
        getter = LsiPoetAccessor(
            body = code {
                returnValue {
                    text("selection(")
                    literal(selection.propertyIndex.toString())
                    text(", ")
                    literal(outerNullable.toString())
                    text(")")
                }
            },
        ),
    )
}

private fun TypedTupleType.kotlinBaseTableCompanionType(
    selections: List<TypedTupleBaseTableSelection>,
): LsiPoetType {
    return LsiPoetType(
        name = "Companion",
        kind = LsiTypeDeclarationKind.OBJECT,
        modifiers = setOf(LsiPoetModifier.COMPANION),
        members = listOf(
            LsiPoetProperty(
                name = "FACTORY",
                type = declaredType(BASE_TABLE_FACTORY_ID, tableType, nullableTableType),
                mutable = false,
                modifiers = setOf(LsiPoetModifier.INTERNAL),
                initializer = code {
                    type(BASE_TABLE_FACTORY_TYPE)
                    text(".of(")
                    line()
                    indent {
                        text("{ ")
                        type(tableType)
                        text("(it) },")
                        line()
                        text("{ ")
                        type(nullableTableType)
                        text("(it) }")
                    }
                    line()
                    text(")")
                },
            ),
            LsiPoetProperty(
                name = "SELECTION_LAYOUT",
                type = BASE_TABLE_SELECTION_LAYOUT_TYPE,
                mutable = false,
                modifiers = setOf(LsiPoetModifier.INTERNAL),
                initializer = code {
                    type(BASE_TABLE_SELECTION_LAYOUT_TYPE)
                    text(".of(")
                    line()
                    indent {
                        selections.forEachIndexed { index, selection ->
                            if (index != 0) {
                                text(",")
                                line()
                            }
                            type(BASE_TABLE_SELECTION_KIND_TYPE)
                            text(".")
                            name(selection.kind.name)
                        }
                    }
                    line()
                    text(")")
                },
            ),
        ),
    )
}

private fun TypedTupleType.javaGetBaseTableFactoryFunction(): LsiPoetFunction {
    return LsiPoetFunction(
        name = "getBaseTableFactory",
        modifiers = setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.OVERRIDE),
        returnType = declaredType(BASE_TABLE_FACTORY_ID, tableType, tableType),
        body = code {
            returnValue {
                type(tableType)
                text(".FACTORY")
            }
        },
    )
}

private fun TypedTupleType.kotlinGetBaseTableFactoryFunction(): LsiPoetFunction {
    return LsiPoetFunction(
        name = "getBaseTableFactory",
        modifiers = setOf(LsiPoetModifier.OVERRIDE),
        returnType = declaredType(BASE_TABLE_FACTORY_ID, tableType, nullableTableType),
        body = code {
            returnValue {
                type(tableType)
                text(".FACTORY")
            }
        },
    )
}

private fun TypedTupleType.kotlinGetSelectionLayoutFunction(): LsiPoetFunction {
    return LsiPoetFunction(
        name = "getSelectionLayout",
        modifiers = setOf(LsiPoetModifier.OVERRIDE),
        returnType = BASE_TABLE_SELECTION_LAYOUT_TYPE,
        body = code {
            returnValue {
                type(tableType)
                text(".SELECTION_LAYOUT")
            }
        },
    )
}

private fun TypedTupleType.kotlinWeakJoinFunctions(
    sourceType: LsiTypeRef,
): List<LsiPoetFunction> {
    return listOf(
        kotlinWeakJoinFunction(sourceType, byType = false, outer = false),
        kotlinWeakJoinFunction(sourceType, byType = true, outer = false),
        kotlinWeakJoinFunction(sourceType, byType = false, outer = true),
        kotlinWeakJoinFunction(sourceType, byType = true, outer = true),
    )
}

private fun TypedTupleType.kotlinWeakJoinFunction(
    sourceType: LsiTypeRef,
    byType: Boolean,
    outer: Boolean,
): LsiPoetFunction {
    val functionOwnerId = LsiSymbolId.function(
        LsiSymbolId.type(tableQualifiedName),
        "${if (outer) "weakOuterJoin" else "weakJoin"}:${if (byType) "type" else "lambda"}",
    )
    val nullableTargetId = LsiSymbolId.typeParameter(functionOwnerId, "TNT")
    val targetId = LsiSymbolId.typeParameter(functionOwnerId, "TT")
    val nullableTargetType = LsiTypeParameterRef(nullableTargetId)
    val targetType = LsiTypeParameterRef(targetId)
    val targetBound = declaredType(
        K_NON_NULL_BASE_TABLE_ID,
        if (outer) nullableTargetType else null,
    )
    val weakJoinType = declaredType(K_PROPS_WEAK_JOIN_ID, sourceType, targetType)
    return LsiPoetFunction(
        name = if (outer) "weakOuterJoin" else "weakJoin",
        typeParameters = buildList {
            if (outer) {
                add(
                    LsiTypeParameter(
                        id = nullableTargetId,
                        name = "TNT",
                        upperBounds = listOf(K_NULLABLE_BASE_TABLE_TYPE),
                    )
                )
            }
            add(
                LsiTypeParameter(
                    id = targetId,
                    name = "TT",
                    upperBounds = listOf(targetBound),
                )
            )
        },
        parameters = listOf(
            LsiPoetParameter(
                name = "targetSymbol",
                type = declaredType(K_BASE_TABLE_SYMBOL_ID, targetType),
            ),
            LsiPoetParameter(
                name = if (byType) "weakJoinType" else "weakJoinLambda",
                type = if (byType) {
                    LsiDeclaredType(
                        declarationId = K_CLASS_ID,
                        arguments = listOf(LsiTypeArgument.output(weakJoinType)),
                    )
                } else {
                    declaredType(K_PROPS_WEAK_JOIN_FUN_ID, sourceType, targetType)
                },
            ),
        ),
        returnType = if (outer) nullableTargetType else targetType,
        body = code {
            returnValue {
                name(if (outer) "weakOuterJoinImpl" else "weakJoinImpl")
                text("(targetSymbol, ")
                name(if (byType) "weakJoinType" else "weakJoinLambda")
                text(")")
            }
        },
    )
}

private fun javaBaseTableSelectionType(
    property: TypedTupleProperty,
    selection: TypedTupleBaseTableSelection,
): LsiTypeRef {
    selection.entityTableTypeId?.let(::LsiDeclaredType)?.let { return it }
    val valueType = property.type.toJvmReferenceType().asNonNullType()
    return when (requireNotNull(selection.scalarCategory)) {
        TypedTupleScalarCategory.GENERIC -> declaredType(EXPRESSION_ID, valueType)
        TypedTupleScalarCategory.STRING -> STRING_EXPRESSION_TYPE
        TypedTupleScalarCategory.NUMERIC -> declaredType(NUMERIC_EXPRESSION_ID, valueType)
        TypedTupleScalarCategory.DATE -> declaredType(DATE_EXPRESSION_ID, valueType)
        TypedTupleScalarCategory.TEMPORAL -> declaredType(TEMPORAL_EXPRESSION_ID, valueType)
        TypedTupleScalarCategory.COMPARABLE -> declaredType(COMPARABLE_EXPRESSION_ID, valueType)
    }
}

private fun kotlinBaseTableSelectionType(
    property: TypedTupleProperty,
    selection: TypedTupleBaseTableSelection,
    outerNullable: Boolean,
): LsiTypeRef {
    val valueType = property.type.asNonNullType()
    val nullable = outerNullable || selection.kind.nullable
    val typeId = when {
        selection.kind.table && nullable -> K_NULLABLE_TABLE_ID
        selection.kind.table -> K_NON_NULL_TABLE_ID
        nullable -> K_NULLABLE_EXPRESSION_ID
        else -> K_NON_NULL_EXPRESSION_ID
    }
    return declaredType(typeId, valueType)
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
        is TypedTupleJavaConstructorConstruction -> javaPositionalTupleBody(plan, tupleType)
        is TypedTupleJavaSetterConstruction -> javaSetterTupleBody(plan, tupleType)
        else -> error("Java typed tuple '${id.value}' has unsupported construction plan '$plan'")
    }
}

private fun TypedTupleType.javaPositionalTupleBody(
    plan: TypedTupleJavaConstructorConstruction,
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
    plan: TypedTupleJavaSetterConstruction,
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
    val builderSimpleName = property.builderSimpleName
    val returnType = stepType(property, mapperType)
    return LsiPoetType(
        name = builderSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
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
    val plan = construction as? TypedTupleKotlinConstructorConstruction
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
    val builderSimpleName = property.builderSimpleName
    val returnType = stepType(property, mapperType)
    return LsiPoetType(
        name = builderSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
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
        kind = LsiTypeDeclarationKind.OBJECT,
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
    val nextStepTypeName = nextStepTypeName(property)
    return if (nextStepTypeName == mapperSimpleName) {
        mapperType
    } else {
        declaredType("$mapperQualifiedName.$nextStepTypeName")
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

private fun declaredType(
    typeId: LsiSymbolId,
    vararg argumentTypes: LsiTypeRef?,
): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = typeId,
        arguments = argumentTypes.map { argumentType ->
            argumentType?.let(LsiTypeArgument::invariant) ?: LsiTypeArgument.STAR
        },
    )
}

private fun LsiTypeRef.asNonNullType(): LsiTypeRef {
    return when (this) {
        is LsiDeclaredType -> copy(nullability = LsiNullability.NON_NULL)
        is LsiTypeParameterRef -> copy(nullability = LsiNullability.NON_NULL)
        is LsiPrimitiveType -> copy(nullability = LsiNullability.NON_NULL)
        is LsiArrayType -> copy(nullability = LsiNullability.NON_NULL)
        is LsiFunctionType -> copy(nullability = LsiNullability.NON_NULL)
        is LsiUnresolvedType -> copy(nullability = LsiNullability.NON_NULL)
    }
}

private fun topLevelGeneratedTypeName(qualifiedName: String): LsiPoetTypeName {
    val packageName = qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")
    return LsiPoetTypeName(
        typeId = LsiSymbolId.type(qualifiedName),
        packageName = packageName,
        simpleNames = listOf(qualifiedName.substringAfterLast('.')),
    )
}

private val TypedTupleType.tableType: LsiDeclaredType
    get() = declaredType(tableQualifiedName)

private val TypedTupleType.nullableTableType: LsiDeclaredType
    get() = declaredType("$tableQualifiedName.Nullable")

private val SELECTION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.Selection")
private val TUPLE_MAPPER_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.runtime.TupleMapper")
private val BASE_TABLE_PROJECTION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.query.BaseTableProjection")
private val BASE_TABLE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.BaseTable")
private val BASE_TABLE_FACTORY_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.spi.BaseTableFactory")
private val BASE_TABLE_SELECTION_KIND_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.spi.BaseTableSelectionKind")
private val BASE_TABLE_SELECTION_LAYOUT_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.spi.BaseTableSelectionLayout")
private val ABSTRACT_TYPED_BASE_TABLE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.ast.table.spi.AbstractTypedBaseTable")
private val EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.Expression")
private val STRING_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.StringExpression")
private val NUMERIC_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.NumericExpression")
private val DATE_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.DateExpression")
private val TEMPORAL_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.TemporalExpression")
private val COMPARABLE_EXPRESSION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ast.ComparableExpression")
private val K_BASE_TABLE_PROJECTION_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.query.KBaseTableProjection")
private val ABSTRACT_K_BASE_TABLE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.impl.AbstractKBaseTable")
private val K_NON_NULL_BASE_TABLE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KNonNullBaseTable")
private val K_NULLABLE_BASE_TABLE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KNullableBaseTable")
private val K_BASE_TABLE_SYMBOL_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KBaseTableSymbol")
private val K_PROPS_WEAK_JOIN_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KPropsWeakJoin")
private val K_PROPS_WEAK_JOIN_FUN_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KPropsWeakJoinFun")
private val K_NON_NULL_TABLE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KNonNullTable")
private val K_NULLABLE_TABLE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.table.KNullableTable")
private val K_NON_NULL_EXPRESSION_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.expression.KNonNullExpression")
private val K_NULLABLE_EXPRESSION_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.kt.ast.expression.KNullableExpression")
private val K_CLASS_ID = LsiSymbolId.type("kotlin.reflect.KClass")
private val LIST_ID = LsiSymbolId.type("java.util.List")
private val COLLECTIONS_TYPE = declaredType("java.util.Collections")
private val ARRAYS_TYPE = declaredType("java.util.Arrays")
private val OBJECT_TYPE = declaredType("java.lang.Object")
private val SUPPRESS_ID = LsiSymbolId.type("kotlin.Suppress")
private val BUILT_IN_TYPE_NAMES = listOf(
    LsiPoetTypeName(SELECTION_ID, "org.babyfish.jimmer.sql.ast", listOf("Selection")),
    LsiPoetTypeName(TUPLE_MAPPER_ID, "org.babyfish.jimmer.sql.runtime", listOf("TupleMapper")),
    LsiPoetTypeName(BASE_TABLE_PROJECTION_ID, "org.babyfish.jimmer.sql.ast.query", listOf("BaseTableProjection")),
    LsiPoetTypeName(BASE_TABLE_ID, "org.babyfish.jimmer.sql.ast.table", listOf("BaseTable")),
    LsiPoetTypeName(BASE_TABLE_FACTORY_ID, "org.babyfish.jimmer.sql.ast.table.spi", listOf("BaseTableFactory")),
    LsiPoetTypeName(
        BASE_TABLE_SELECTION_KIND_ID,
        "org.babyfish.jimmer.sql.ast.table.spi",
        listOf("BaseTableSelectionKind"),
    ),
    LsiPoetTypeName(
        BASE_TABLE_SELECTION_LAYOUT_ID,
        "org.babyfish.jimmer.sql.ast.table.spi",
        listOf("BaseTableSelectionLayout"),
    ),
    LsiPoetTypeName(
        ABSTRACT_TYPED_BASE_TABLE_ID,
        "org.babyfish.jimmer.sql.ast.table.spi",
        listOf("AbstractTypedBaseTable"),
    ),
    LsiPoetTypeName(EXPRESSION_ID, "org.babyfish.jimmer.sql.ast", listOf("Expression")),
    LsiPoetTypeName(STRING_EXPRESSION_ID, "org.babyfish.jimmer.sql.ast", listOf("StringExpression")),
    LsiPoetTypeName(NUMERIC_EXPRESSION_ID, "org.babyfish.jimmer.sql.ast", listOf("NumericExpression")),
    LsiPoetTypeName(DATE_EXPRESSION_ID, "org.babyfish.jimmer.sql.ast", listOf("DateExpression")),
    LsiPoetTypeName(TEMPORAL_EXPRESSION_ID, "org.babyfish.jimmer.sql.ast", listOf("TemporalExpression")),
    LsiPoetTypeName(COMPARABLE_EXPRESSION_ID, "org.babyfish.jimmer.sql.ast", listOf("ComparableExpression")),
    LsiPoetTypeName(
        K_BASE_TABLE_PROJECTION_ID,
        "org.babyfish.jimmer.sql.kt.ast.query",
        listOf("KBaseTableProjection"),
    ),
    LsiPoetTypeName(
        ABSTRACT_K_BASE_TABLE_ID,
        "org.babyfish.jimmer.sql.kt.ast.table.impl",
        listOf("AbstractKBaseTable"),
    ),
    LsiPoetTypeName(K_NON_NULL_BASE_TABLE_ID, "org.babyfish.jimmer.sql.kt.ast.table", listOf("KNonNullBaseTable")),
    LsiPoetTypeName(K_NULLABLE_BASE_TABLE_ID, "org.babyfish.jimmer.sql.kt.ast.table", listOf("KNullableBaseTable")),
    LsiPoetTypeName(K_BASE_TABLE_SYMBOL_ID, "org.babyfish.jimmer.sql.kt.ast.table", listOf("KBaseTableSymbol")),
    LsiPoetTypeName(K_PROPS_WEAK_JOIN_ID, "org.babyfish.jimmer.sql.kt.ast.table", listOf("KPropsWeakJoin")),
    LsiPoetTypeName(K_PROPS_WEAK_JOIN_FUN_ID, "org.babyfish.jimmer.sql.kt.ast.table", listOf("KPropsWeakJoinFun")),
    LsiPoetTypeName(K_NON_NULL_TABLE_ID, "org.babyfish.jimmer.sql.kt.ast.table", listOf("KNonNullTable")),
    LsiPoetTypeName(K_NULLABLE_TABLE_ID, "org.babyfish.jimmer.sql.kt.ast.table", listOf("KNullableTable")),
    LsiPoetTypeName(
        K_NON_NULL_EXPRESSION_ID,
        "org.babyfish.jimmer.sql.kt.ast.expression",
        listOf("KNonNullExpression"),
    ),
    LsiPoetTypeName(
        K_NULLABLE_EXPRESSION_ID,
        "org.babyfish.jimmer.sql.kt.ast.expression",
        listOf("KNullableExpression"),
    ),
    LsiPoetTypeName(K_CLASS_ID, "kotlin.reflect", listOf("KClass")),
    LsiPoetTypeName(LIST_ID, "java.util", listOf("List")),
    LsiPoetTypeName(COLLECTIONS_TYPE.declarationId, "java.util", listOf("Collections")),
    LsiPoetTypeName(ARRAYS_TYPE.declarationId, "java.util", listOf("Arrays")),
    LsiPoetTypeName(OBJECT_TYPE.declarationId, "java.lang", listOf("Object")),
    LsiPoetTypeName(SUPPRESS_ID, "kotlin", listOf("Suppress")),
)
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
private val BASE_TABLE_TYPE = LsiDeclaredType(BASE_TABLE_ID)
private val BASE_TABLE_FACTORY_TYPE = LsiDeclaredType(BASE_TABLE_FACTORY_ID)
private val BASE_TABLE_SELECTION_KIND_TYPE = LsiDeclaredType(BASE_TABLE_SELECTION_KIND_ID)
private val BASE_TABLE_SELECTION_LAYOUT_TYPE = LsiDeclaredType(BASE_TABLE_SELECTION_LAYOUT_ID)
private val ABSTRACT_K_BASE_TABLE_TYPE = LsiDeclaredType(ABSTRACT_K_BASE_TABLE_ID)
private val K_NULLABLE_BASE_TABLE_TYPE = LsiDeclaredType(K_NULLABLE_BASE_TABLE_ID)
private val STRING_EXPRESSION_TYPE = LsiDeclaredType(STRING_EXPRESSION_ID)
