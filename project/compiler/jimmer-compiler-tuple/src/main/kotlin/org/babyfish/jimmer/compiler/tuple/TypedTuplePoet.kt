package org.babyfish.jimmer.compiler.tuple

import site.addzero.lsi.model.sourceLsiAnnotation

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
import site.addzero.lsi.type.LsiArrayType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiNullability
import site.addzero.lsi.type.LsiFunctionType
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.type.LsiTypeParameter
import site.addzero.lsi.type.LsiTypeParameterRef
import site.addzero.lsi.type.LsiTypeArgument
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.type.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiAccessor
import site.addzero.lsi.model.toJvmReferenceType
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiSourceAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBuilder
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFile
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.clazz.toLsiClasses

internal fun TypedTupleSchema.toLsiSourceArtifacts(
    workspace: LsiWorkspace,
): List<LsiSourceArtifact> {
    return tuples.flatMap { tuple -> tuple.toLsiPoet(workspace) }
}

private fun TypedTupleType.toLsiPoet(workspace: LsiWorkspace): List<LsiSourceArtifact> {
    val originatingSymbols = setOf(id)
    val originatingSources = workspace.originatingSources(originatingSymbols)
    val dependencySymbols = dependencies.symbolIds.toSet()
    val dependencySources = workspace.originatingSources(dependencySymbols)
    val files = when (sourceLanguage) {
        LsiLanguage.JAVA -> buildList {
            add(
                LsiFile(
                    language = sourceLanguage,
                    packageName = packageName,
                    fileName = mapperSimpleName,
                    members = listOf(mapperType()),
                )
            )
            if (baseTableProjection != null) {
                add(
                    LsiFile(
                        language = sourceLanguage,
                        packageName = packageName,
                        fileName = tableSimpleName,
                        members = listOf(javaBaseTableType()),
                    )
                )
            }
        }
        LsiLanguage.KOTLIN -> listOf(
            LsiFile(
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
        LsiSourceArtifact(
            file = file,
            typeNames = workspace.toLsiClasses(
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

private fun TypedTupleType.generatedTypeNames(): List<LsiClass> {
    return buildList {
        addAll(BUILT_IN_TYPE_NAMES)
        add(
            LsiClass(
                typeId = LsiSymbolId.type(mapperQualifiedName),
                packageName = packageName,
                simpleNames = listOf(mapperSimpleName),
            )
        )
        val projection = baseTableProjection
        if (projection != null) {
            add(topLevelGeneratedTypeName(tableQualifiedName))
            add(
                LsiClass(
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
                LsiClass(
                    typeId = LsiSymbolId.type("$mapperQualifiedName.${property.builderSimpleName}"),
                    packageName = packageName,
                    simpleNames = listOf(mapperSimpleName, property.builderSimpleName),
                )
            )
        }
    }
}

private fun TypedTupleType.mapperType(): LsiClass {
    return when (sourceLanguage) {
        LsiLanguage.JAVA -> javaMapperType()
        LsiLanguage.KOTLIN -> kotlinMapperType()
        LsiLanguage.UNKNOWN -> error("Typed tuple '${id.value}' has unknown source language")
    }
}

private fun TypedTupleType.javaMapperType(): LsiClass {
    val tupleType = declaredType(qualifiedName)
    val mapperType = declaredType(mapperQualifiedName)
    return LsiClass(
        name = mapperSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        modifiers = setOf(LsiModifier.PUBLIC),
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

private fun TypedTupleType.kotlinMapperType(): LsiClass {
    val tupleType = declaredType(qualifiedName)
    val mapperType = declaredType(mapperQualifiedName)
    return LsiClass(
        name = mapperSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        primaryConstructor = LsiConstructor(
            modifiers = setOf(LsiModifier.PRIVATE),
            parameters = listOf(LsiParameter("selections", KOTLIN_SELECTION_ARRAY_TYPE)),
        ),
        superInterfaces = buildList {
            add(tupleMapperType(tupleType))
            if (baseTableProjection != null) {
                add(declaredType(K_BASE_TABLE_PROJECTION_ID, tableType, nullableTableType))
            }
        },
        members = buildList {
            add(
                LsiProperty(
                    name = "selections",
                    type = KOTLIN_SELECTION_ARRAY_TYPE,
                    mutable = false,
                    modifiers = setOf(LsiModifier.PRIVATE),
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

private fun TypedTupleType.javaBaseTableType(): LsiClass {
    val projection = requireNotNull(baseTableProjection)
    return LsiClass(
        name = tableSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.FINAL),
        superClass = declaredType(ABSTRACT_TYPED_BASE_TABLE_ID, tableType),
        primaryConstructor = LsiConstructor(
            parameters = listOf(LsiParameter("baseTable", BASE_TABLE_TYPE)),
            body = code {
                statement { text("super(baseTable)") }
            },
        ),
        members = buildList {
            add(
                LsiField(
                    name = "FACTORY",
                    type = declaredType(BASE_TABLE_FACTORY_ID, tableType, tableType),
                    modifiers = setOf(LsiModifier.STATIC, LsiModifier.FINAL),
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
                    LsiFunction(
                        name = property.javaGetterName,
                        modifiers = setOf(LsiModifier.PUBLIC),
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

private fun TypedTupleType.kotlinBaseTableType(): LsiClass {
    val projection = requireNotNull(baseTableProjection)
    return LsiClass(
        name = tableSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        superClass = ABSTRACT_K_BASE_TABLE_TYPE,
        superClassConstructorArguments = listOf(code { name("baseTable") }),
        superInterfaces = listOf(declaredType(K_NON_NULL_BASE_TABLE_ID, nullableTableType)),
        primaryConstructor = LsiConstructor(
            modifiers = setOf(LsiModifier.INTERNAL),
            parameters = listOf(LsiParameter("baseTable", BASE_TABLE_TYPE)),
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
): LsiClass {
    return LsiClass(
        name = "Nullable",
        kind = LsiTypeDeclarationKind.CLASS,
        superClass = ABSTRACT_K_BASE_TABLE_TYPE,
        superClassConstructorArguments = listOf(code { name("baseTable") }),
        superInterfaces = listOf(K_NULLABLE_BASE_TABLE_TYPE),
        primaryConstructor = LsiConstructor(
            modifiers = setOf(LsiModifier.INTERNAL),
            parameters = listOf(LsiParameter("baseTable", BASE_TABLE_TYPE)),
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
): LsiProperty {
    val property = properties[selection.propertyIndex]
    return LsiProperty(
        name = property.name,
        type = kotlinBaseTableSelectionType(property, selection, outerNullable),
        mutable = false,
        getter = LsiAccessor(
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
): LsiClass {
    return LsiClass(
        name = "Companion",
        kind = LsiTypeDeclarationKind.OBJECT,
        modifiers = setOf(LsiModifier.COMPANION),
        members = listOf(
            LsiProperty(
                name = "FACTORY",
                type = declaredType(BASE_TABLE_FACTORY_ID, tableType, nullableTableType),
                mutable = false,
                modifiers = setOf(LsiModifier.INTERNAL),
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
            LsiProperty(
                name = "SELECTION_LAYOUT",
                type = BASE_TABLE_SELECTION_LAYOUT_TYPE,
                mutable = false,
                modifiers = setOf(LsiModifier.INTERNAL),
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

private fun TypedTupleType.javaGetBaseTableFactoryFunction(): LsiFunction {
    return LsiFunction(
        name = "getBaseTableFactory",
        modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
        returnType = declaredType(BASE_TABLE_FACTORY_ID, tableType, tableType),
        body = code {
            returnValue {
                type(tableType)
                text(".FACTORY")
            }
        },
    )
}

private fun TypedTupleType.kotlinGetBaseTableFactoryFunction(): LsiFunction {
    return LsiFunction(
        name = "getBaseTableFactory",
        modifiers = setOf(LsiModifier.OVERRIDE),
        returnType = declaredType(BASE_TABLE_FACTORY_ID, tableType, nullableTableType),
        body = code {
            returnValue {
                type(tableType)
                text(".FACTORY")
            }
        },
    )
}

private fun TypedTupleType.kotlinGetSelectionLayoutFunction(): LsiFunction {
    return LsiFunction(
        name = "getSelectionLayout",
        modifiers = setOf(LsiModifier.OVERRIDE),
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
    sourceType: LsiType,
): List<LsiFunction> {
    return listOf(
        kotlinWeakJoinFunction(sourceType, byType = false, outer = false),
        kotlinWeakJoinFunction(sourceType, byType = true, outer = false),
        kotlinWeakJoinFunction(sourceType, byType = false, outer = true),
        kotlinWeakJoinFunction(sourceType, byType = true, outer = true),
    )
}

private fun TypedTupleType.kotlinWeakJoinFunction(
    sourceType: LsiType,
    byType: Boolean,
    outer: Boolean,
): LsiFunction {
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
    return LsiFunction(
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
            LsiParameter(
                name = "targetSymbol",
                type = declaredType(K_BASE_TABLE_SYMBOL_ID, targetType),
            ),
            LsiParameter(
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
): LsiType {
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
): LsiType {
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

private fun TypedTupleType.javaSelectionsField(): LsiField {
    return LsiField(
        name = "selections",
        type = JAVA_SELECTION_ARRAY_TYPE,
        modifiers = setOf(
            LsiModifier.PRIVATE,
            LsiModifier.FINAL,
        ),
    )
}

private fun TypedTupleType.javaSelectionsConstructor(): LsiConstructor {
    return LsiConstructor(
        parameters = listOf(LsiParameter("selections", JAVA_SELECTION_ARRAY_TYPE)),
        body = code {
            statement {
                text("this.selections = ")
                name("selections")
            }
        },
    )
}

private fun TypedTupleType.javaGetSelectionsFunction(): LsiFunction {
    return LsiFunction(
        name = "getSelections",
        modifiers = setOf(
            LsiModifier.PUBLIC,
            LsiModifier.OVERRIDE,
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

private fun TypedTupleType.javaCreateTupleFunction(tupleType: LsiType): LsiFunction {
    return LsiFunction(
        name = "createTuple",
        modifiers = setOf(
            LsiModifier.PUBLIC,
            LsiModifier.OVERRIDE,
        ),
        parameters = listOf(LsiParameter("args", JAVA_ARGUMENT_ARRAY_TYPE)),
        returnType = tupleType,
        body = javaCreateTupleBody(tupleType),
    )
}

private fun TypedTupleType.javaCreateTupleBody(tupleType: LsiType): LsiCodeBlock {
    return when (val plan = construction) {
        is TypedTupleJavaConstructorConstruction -> javaPositionalTupleBody(plan, tupleType)
        is TypedTupleJavaSetterConstruction -> javaSetterTupleBody(plan, tupleType)
        else -> error("Java typed tuple '${id.value}' has unsupported construction plan '$plan'")
    }
}

private fun TypedTupleType.javaPositionalTupleBody(
    plan: TypedTupleJavaConstructorConstruction,
    tupleType: LsiType,
): LsiCodeBlock {
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
    tupleType: LsiType,
): LsiCodeBlock {
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

private fun TypedTupleType.javaFirstPropertyFunction(mapperType: LsiType): LsiFunction {
    val property = properties.first()
    val returnType = stepType(property, mapperType)
    return LsiFunction(
        name = property.name,
        modifiers = setOf(
            LsiModifier.PUBLIC,
            LsiModifier.STATIC,
        ),
        parameters = listOf(
            LsiParameter("selection", javaSelectionType(property)),
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
    mapperType: LsiType,
): LsiClass {
    val builderSimpleName = property.builderSimpleName
    val returnType = stepType(property, mapperType)
    return LsiClass(
        name = builderSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        modifiers = setOf(
            LsiModifier.PUBLIC,
            LsiModifier.STATIC,
        ),
        members = listOf(
            javaSelectionsField(),
            javaSelectionsConstructor(),
            LsiFunction(
                name = property.name,
                modifiers = setOf(LsiModifier.PUBLIC),
                parameters = listOf(
                    LsiParameter("selection", javaSelectionType(property)),
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

private fun TypedTupleType.kotlinGetSelectionsFunction(): LsiFunction {
    return LsiFunction(
        name = "getSelections",
        annotations = listOf(UNCHECKED_CAST_SUPPRESSION),
        modifiers = setOf(LsiModifier.OVERRIDE),
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

private fun TypedTupleType.kotlinCreateTupleFunction(tupleType: LsiType): LsiFunction {
    return LsiFunction(
        name = "createTuple",
        modifiers = setOf(LsiModifier.OVERRIDE),
        parameters = listOf(LsiParameter("args", KOTLIN_ARGUMENT_ARRAY_TYPE)),
        returnType = tupleType,
        body = kotlinCreateTupleBody(tupleType),
    )
}

private fun TypedTupleType.kotlinCreateTupleBody(tupleType: LsiType): LsiCodeBlock {
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
    mapperType: LsiType,
): LsiClass {
    val builderSimpleName = property.builderSimpleName
    val returnType = stepType(property, mapperType)
    return LsiClass(
        name = builderSimpleName,
        kind = LsiTypeDeclarationKind.CLASS,
        primaryConstructor = LsiConstructor(
            modifiers = setOf(LsiModifier.INTERNAL),
            parameters = listOf(LsiParameter("selections", KOTLIN_SELECTION_ARRAY_TYPE)),
        ),
        members = listOf(
            LsiProperty(
                name = "selections",
                type = KOTLIN_SELECTION_ARRAY_TYPE,
                mutable = false,
                modifiers = setOf(LsiModifier.PRIVATE),
                initializer = code { name("selections") },
            ),
            LsiFunction(
                name = property.name,
                parameters = listOf(
                    LsiParameter("selection", kotlinSelectionType(property)),
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

private fun TypedTupleType.kotlinCompanionType(mapperType: LsiType): LsiClass {
    val property = properties.first()
    val returnType = stepType(property, mapperType)
    return LsiClass(
        name = "Companion",
        kind = LsiTypeDeclarationKind.OBJECT,
        modifiers = setOf(LsiModifier.COMPANION),
        members = listOf(
            LsiFunction(
                name = property.name,
                parameters = listOf(
                    LsiParameter("selection", kotlinSelectionType(property)),
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
    mapperType: LsiType,
): LsiType {
    val nextStepTypeName = nextStepTypeName(property)
    return if (nextStepTypeName == mapperSimpleName) {
        mapperType
    } else {
        declaredType("$mapperQualifiedName.$nextStepTypeName")
    }
}

private fun javaSelectionType(property: TypedTupleProperty): LsiType {
    return selectionType(property.type.toJvmReferenceType())
}

private fun kotlinSelectionType(property: TypedTupleProperty): LsiType {
    return selectionType(property.type)
}

private fun selectionType(valueType: LsiType): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = SELECTION_ID,
        arguments = listOf(LsiTypeArgument.invariant(valueType)),
    )
}

private fun tupleMapperType(tupleType: LsiType): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = TUPLE_MAPPER_ID,
        arguments = listOf(LsiTypeArgument.invariant(tupleType)),
    )
}

private fun listType(elementType: LsiType): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = LIST_ID,
        arguments = listOf(LsiTypeArgument.invariant(elementType)),
    )
}

private fun LsiCodeBuilder.selectionAssignment(property: TypedTupleProperty) {
    statement {
        name("selections")
        text("[")
        literal(property.index.toString())
        text("] = ")
        name("selection")
    }
}

private fun code(
    block: LsiCodeBuilder.() -> Unit,
): LsiCodeBlock {
    return LsiCodeBlock.build(block)
}

private fun declaredType(qualifiedName: String): LsiDeclaredType {
    return LsiDeclaredType(LsiSymbolId.type(qualifiedName))
}

private fun declaredType(
    typeId: LsiSymbolId,
    vararg argumentTypes: LsiType?,
): LsiDeclaredType {
    return LsiDeclaredType(
        declarationId = typeId,
        arguments = argumentTypes.map { argumentType ->
            argumentType?.let(LsiTypeArgument::invariant) ?: LsiTypeArgument.STAR
        },
    )
}

private fun LsiType.asNonNullType(): LsiType {
    return when (this) {
        is LsiDeclaredType -> copy(nullability = LsiNullability.NON_NULL)
        is LsiTypeParameterRef -> copy(nullability = LsiNullability.NON_NULL)
        is LsiPrimitiveType -> copy(nullability = LsiNullability.NON_NULL)
        is LsiArrayType -> copy(nullability = LsiNullability.NON_NULL)
        is LsiFunctionType -> copy(nullability = LsiNullability.NON_NULL)
        is LsiUnresolvedType -> copy(nullability = LsiNullability.NON_NULL)
    }
}

private fun topLevelGeneratedTypeName(qualifiedName: String): LsiClass {
    val packageName = qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")
    return LsiClass(
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
    LsiClass(SELECTION_ID, "org.babyfish.jimmer.sql.ast", listOf("Selection")),
    LsiClass(TUPLE_MAPPER_ID, "org.babyfish.jimmer.sql.runtime", listOf("TupleMapper")),
    LsiClass(BASE_TABLE_PROJECTION_ID, "org.babyfish.jimmer.sql.ast.query", listOf("BaseTableProjection")),
    LsiClass(BASE_TABLE_ID, "org.babyfish.jimmer.sql.ast.table", listOf("BaseTable")),
    LsiClass(BASE_TABLE_FACTORY_ID, "org.babyfish.jimmer.sql.ast.table.spi", listOf("BaseTableFactory")),
    LsiClass(
        BASE_TABLE_SELECTION_KIND_ID,
        "org.babyfish.jimmer.sql.ast.table.spi",
        listOf("BaseTableSelectionKind"),
    ),
    LsiClass(
        BASE_TABLE_SELECTION_LAYOUT_ID,
        "org.babyfish.jimmer.sql.ast.table.spi",
        listOf("BaseTableSelectionLayout"),
    ),
    LsiClass(
        ABSTRACT_TYPED_BASE_TABLE_ID,
        "org.babyfish.jimmer.sql.ast.table.spi",
        listOf("AbstractTypedBaseTable"),
    ),
    LsiClass(EXPRESSION_ID, "org.babyfish.jimmer.sql.ast", listOf("Expression")),
    LsiClass(STRING_EXPRESSION_ID, "org.babyfish.jimmer.sql.ast", listOf("StringExpression")),
    LsiClass(NUMERIC_EXPRESSION_ID, "org.babyfish.jimmer.sql.ast", listOf("NumericExpression")),
    LsiClass(DATE_EXPRESSION_ID, "org.babyfish.jimmer.sql.ast", listOf("DateExpression")),
    LsiClass(TEMPORAL_EXPRESSION_ID, "org.babyfish.jimmer.sql.ast", listOf("TemporalExpression")),
    LsiClass(COMPARABLE_EXPRESSION_ID, "org.babyfish.jimmer.sql.ast", listOf("ComparableExpression")),
    LsiClass(
        K_BASE_TABLE_PROJECTION_ID,
        "org.babyfish.jimmer.sql.kt.ast.query",
        listOf("KBaseTableProjection"),
    ),
    LsiClass(
        ABSTRACT_K_BASE_TABLE_ID,
        "org.babyfish.jimmer.sql.kt.ast.table.impl",
        listOf("AbstractKBaseTable"),
    ),
    LsiClass(K_NON_NULL_BASE_TABLE_ID, "org.babyfish.jimmer.sql.kt.ast.table", listOf("KNonNullBaseTable")),
    LsiClass(K_NULLABLE_BASE_TABLE_ID, "org.babyfish.jimmer.sql.kt.ast.table", listOf("KNullableBaseTable")),
    LsiClass(K_BASE_TABLE_SYMBOL_ID, "org.babyfish.jimmer.sql.kt.ast.table", listOf("KBaseTableSymbol")),
    LsiClass(K_PROPS_WEAK_JOIN_ID, "org.babyfish.jimmer.sql.kt.ast.table", listOf("KPropsWeakJoin")),
    LsiClass(K_PROPS_WEAK_JOIN_FUN_ID, "org.babyfish.jimmer.sql.kt.ast.table", listOf("KPropsWeakJoinFun")),
    LsiClass(K_NON_NULL_TABLE_ID, "org.babyfish.jimmer.sql.kt.ast.table", listOf("KNonNullTable")),
    LsiClass(K_NULLABLE_TABLE_ID, "org.babyfish.jimmer.sql.kt.ast.table", listOf("KNullableTable")),
    LsiClass(
        K_NON_NULL_EXPRESSION_ID,
        "org.babyfish.jimmer.sql.kt.ast.expression",
        listOf("KNonNullExpression"),
    ),
    LsiClass(
        K_NULLABLE_EXPRESSION_ID,
        "org.babyfish.jimmer.sql.kt.ast.expression",
        listOf("KNullableExpression"),
    ),
    LsiClass(K_CLASS_ID, "kotlin.reflect", listOf("KClass")),
    LsiClass(LIST_ID, "java.util", listOf("List")),
    LsiClass(COLLECTIONS_TYPE.declarationId, "java.util", listOf("Collections")),
    LsiClass(ARRAYS_TYPE.declarationId, "java.util", listOf("Arrays")),
    LsiClass(OBJECT_TYPE.declarationId, "java.lang", listOf("Object")),
    LsiClass(SUPPRESS_ID, "kotlin", listOf("Suppress")),
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
private val UNCHECKED_CAST_SUPPRESSION = sourceLsiAnnotation(
    type = SUPPRESS_ID,
    arguments = listOf(
        LsiSourceAnnotationArgument.Positional(
            LsiAnnotationValue.StringValue("UNCHECKED_CAST")
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
