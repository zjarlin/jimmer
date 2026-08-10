package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.AssociationKind
import site.addzero.lsi.jimmer.AssociationStorageKind
import site.addzero.lsi.jimmer.FormulaKind
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.jimmer.PrimaryMapping
import site.addzero.lsi.jimmer.dto.DtoAnnotation
import site.addzero.lsi.jimmer.dto.DtoAnnotationArgument
import site.addzero.lsi.jimmer.dto.DtoAnnotationApplication
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoAnnotationDeclaration
import site.addzero.lsi.jimmer.dto.DtoAnnotationOrigin
import site.addzero.lsi.jimmer.dto.DtoAnnotationPlacement
import site.addzero.lsi.jimmer.dto.DtoAnnotationValue
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoBuilderSetterAnnotationApplication
import site.addzero.lsi.jimmer.dto.DtoGraph
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoPropAnnotationPlan
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeAnnotationPlan
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.LsiAnnotationArgument
import site.addzero.lsi.anno.LsiAnnotationArgumentOrigin
import site.addzero.lsi.anno.LsiAnnotationValue
import site.addzero.lsi.type.LsiArrayType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.anno.LsiSourceAnnotationArgument
import site.addzero.lsi.anno.LsiAnnotationArgumentLayout
import site.addzero.lsi.field.LsiProperty
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class DtoInputBuilderPoetTest {

    @Test
    fun `renders Java and Kotlin builders with stable member order and language behavior`() {
        val graph = graph()
        val dtoType = graph.types.single()
        val schema = immutableSchema()
        val annotationContract = annotationContract(graph)
        val currentDtoType = LsiDeclaredType(DTO_DECLARATION_TYPE_ID)

        val javaType = dtoType.toInputBuilderPoetType(
            graph = graph,
            immutableSchema = schema,
            annotationContract = annotationContract,
            targetLanguage = LsiLanguage.JAVA,
            currentDtoType = currentDtoType,
            generatedDtoTypes = emptyMap(),
            jsonPojoBuilderAnnotationTypeId = JSON_POJO_BUILDER_TYPE_ID,
            jsonNamingAnnotationTypeId = JSON_NAMING_TYPE_ID,
        )
        val kotlinType = dtoType.toInputBuilderPoetType(
            graph = graph,
            immutableSchema = schema,
            annotationContract = annotationContract,
            targetLanguage = LsiLanguage.KOTLIN,
            currentDtoType = currentDtoType,
            generatedDtoTypes = emptyMap(),
            jsonPojoBuilderAnnotationTypeId = JSON_POJO_BUILDER_TYPE_ID,
            jsonNamingAnnotationTypeId = JSON_NAMING_TYPE_ID,
        )

        assertEquals(
            listOf(
                "dynamicName",
                "_isDynamicNameLoaded",
                "requiredName",
                "fixedName",
                "_isFixedNameLoaded",
                "fuzzyName",
                "dynamicName",
                "requiredName",
                "fixedName",
                "fuzzyName",
                "build",
            ),
            javaType.members.map { member ->
                when (member) {
                    is LsiField -> member.name
                    is LsiMethod -> member.name
                    else -> error("Unexpected Java InputBuilder member: $member")
                }
            },
        )
        assertEquals(
            listOf(
                "dynamicName",
                "isDynamicNameLoaded",
                "requiredName",
                "fixedName",
                "isFixedNameLoaded",
                "fuzzyName",
                "dynamicName",
                "requiredName",
                "fixedName",
                "fuzzyName",
                "build",
            ),
            kotlinType.members.map { member ->
                when (member) {
                    is LsiProperty -> member.name
                    is LsiMethod -> member.name
                    else -> error("Unexpected Kotlin InputBuilder member: $member")
                }
            },
        )
        assertIs<LsiField>(javaType.members.first())
        assertIs<LsiProperty>(kotlinType.members.first())

        val javaSource = LsiJavaPoetRenderer().renderType(javaType, TYPE_NAMES).toString()
        val kotlinSource = LsiKotlinPoetRenderer().renderType(kotlinType, TYPE_NAMES).toString()
        assertEquals(EXPECTED_JAVA, javaSource)
        assertEquals(EXPECTED_KOTLIN, kotlinSource)
    }

    @Test
    fun `lowers immutable annotations by declaration order and target vararg rules`() {
        val graph = graph(dynamicAnnotations = emptyList())
        val contract = annotationContract(
            graph = graph,
            declarations = listOf(
                annotationDeclaration(
                    typeId = ORDERED_ANNOTATION_TYPE_ID,
                    argumentTypes = mapOf(
                        "alpha" to LsiDeclaredType(JAVA_STRING_TYPE_ID),
                        "zeta" to LsiDeclaredType(JAVA_STRING_TYPE_ID),
                    ),
                    argumentNamesInDeclarationOrder = listOf("zeta", "alpha"),
                ),
                annotationDeclaration(
                    typeId = TAGS_ANNOTATION_TYPE_ID,
                    language = LsiLanguage.KOTLIN,
                    argumentTypes = mapOf(
                        "value" to LsiArrayType(LsiDeclaredType(JAVA_STRING_TYPE_ID)),
                    ),
                    kotlinValueVararg = true,
                ),
            ),
            applications = listOf(
                DtoBuilderSetterAnnotationApplication(
                    annotation = LsiAnnotation(
                        type = ORDERED_ANNOTATION_TYPE_ID,
                        arguments = mapOf(
                            "alpha" to explicit(LsiAnnotationValue.StringValue("a")),
                            "zeta" to explicit(LsiAnnotationValue.StringValue("z")),
                        ),
                    ),
                    origin = DtoAnnotationOrigin.IMMUTABLE,
                    sourceSymbolId = LsiSymbolId.property(IMMUTABLE_TYPE_ID, "dynamicName"),
                ),
                DtoBuilderSetterAnnotationApplication(
                    annotation = LsiAnnotation(
                        type = TAGS_ANNOTATION_TYPE_ID,
                        arguments = mapOf(
                            "value" to explicit(
                                LsiAnnotationValue.ArrayValue(
                                    listOf(
                                        LsiAnnotationValue.StringValue("first"),
                                        LsiAnnotationValue.StringValue("second"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    origin = DtoAnnotationOrigin.IMMUTABLE,
                    sourceSymbolId = LsiSymbolId.property(IMMUTABLE_TYPE_ID, "dynamicName"),
                ),
            ),
        )

        val javaSetter = inputBuilderSetter(graph, contract, LsiLanguage.JAVA)
        val kotlinSetter = inputBuilderSetter(graph, contract, LsiLanguage.KOTLIN)
        val javaOrdered = javaSetter.annotations.single { it.type == ORDERED_ANNOTATION_TYPE_ID }
        val kotlinOrdered = kotlinSetter.annotations.single { it.type == ORDERED_ANNOTATION_TYPE_ID }
        val javaTags = javaSetter.annotations.single { it.type == TAGS_ANNOTATION_TYPE_ID }
        val kotlinTags = kotlinSetter.annotations.single { it.type == TAGS_ANNOTATION_TYPE_ID }

        assertEquals(
            listOf("zeta", "alpha"),
            javaOrdered.sourceArguments.filterIsInstance<LsiSourceAnnotationArgument.Named>().map { it.name },
        )
        assertEquals(javaOrdered.sourceArguments, kotlinOrdered.sourceArguments)
        assertIs<LsiAnnotationValue.ArrayValue>(
            assertIs<LsiSourceAnnotationArgument.Named>(javaTags.sourceArguments.single()).value,
        )
        assertEquals(2, kotlinTags.sourceArguments.filterIsInstance<LsiSourceAnnotationArgument.Positional>().size)
        assertEquals(LsiAnnotationArgumentLayout.PLATFORM_DEFAULT, kotlinTags.argumentLayout)
    }

    @Test
    fun `lowers dto value vararg and nested sole value without platform metadata`() {
        val nestedSource = DtoAnnotation(
            typeId = NESTED_ANNOTATION_TYPE_ID,
            arguments = listOf(
                DtoAnnotationArgument("value", DtoAnnotationValue.LiteralValue("\"nested\"")),
            ),
        )
        val tagsSource = DtoAnnotation(
            typeId = TAGS_ANNOTATION_TYPE_ID,
            arguments = listOf(
                DtoAnnotationArgument(
                    "value",
                    DtoAnnotationValue.ArrayValue(
                        listOf(
                            DtoAnnotationValue.LiteralValue("\"first\""),
                            DtoAnnotationValue.LiteralValue("\"second\""),
                        ),
                    ),
                ),
            ),
        )
        val wrapperSource = DtoAnnotation(
            typeId = WRAPPER_ANNOTATION_TYPE_ID,
            arguments = listOf(
                DtoAnnotationArgument("nested", DtoAnnotationValue.AnnotationValue(nestedSource)),
            ),
        )
        val graph = graph(dynamicAnnotations = listOf(tagsSource, wrapperSource))
        val frozenNested = LsiAnnotation(
            type = NESTED_ANNOTATION_TYPE_ID,
            arguments = mapOf(
                "value" to explicit(LsiAnnotationValue.StringValue("nested")),
            ),
        )
        val contract = annotationContract(
            graph = graph,
            declarations = listOf(
                annotationDeclaration(
                    typeId = TAGS_ANNOTATION_TYPE_ID,
                    language = LsiLanguage.KOTLIN,
                    argumentTypes = mapOf(
                        "value" to LsiArrayType(LsiDeclaredType(JAVA_STRING_TYPE_ID)),
                    ),
                    kotlinValueVararg = true,
                ),
                annotationDeclaration(
                    typeId = NESTED_ANNOTATION_TYPE_ID,
                    argumentTypes = mapOf("value" to LsiDeclaredType(JAVA_STRING_TYPE_ID)),
                ),
                annotationDeclaration(
                    typeId = WRAPPER_ANNOTATION_TYPE_ID,
                    argumentTypes = mapOf(
                        "nested" to LsiDeclaredType(NESTED_ANNOTATION_TYPE_ID),
                    ),
                ),
            ),
            applications = listOf(
                DtoBuilderSetterAnnotationApplication(
                    annotation = LsiAnnotation(
                        type = TAGS_ANNOTATION_TYPE_ID,
                        arguments = mapOf(
                            "value" to explicit(
                                LsiAnnotationValue.ArrayValue(
                                    listOf(
                                        LsiAnnotationValue.StringValue("first"),
                                        LsiAnnotationValue.StringValue("second"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    origin = DtoAnnotationOrigin.DTO,
                    sourceSymbolId = null,
                ),
                DtoBuilderSetterAnnotationApplication(
                    annotation = LsiAnnotation(
                        type = WRAPPER_ANNOTATION_TYPE_ID,
                        arguments = mapOf(
                            "nested" to explicit(LsiAnnotationValue.NestedAnnotationValue(frozenNested)),
                        ),
                    ),
                    origin = DtoAnnotationOrigin.DTO,
                    sourceSymbolId = null,
                ),
            ),
        )

        val javaSetter = inputBuilderSetter(graph, contract, LsiLanguage.JAVA)
        val kotlinSetter = inputBuilderSetter(graph, contract, LsiLanguage.KOTLIN)
        val kotlinTags = kotlinSetter.annotations.single { it.type == TAGS_ANNOTATION_TYPE_ID }
        assertEquals(LsiAnnotationArgumentLayout.SINGLE_LINE, kotlinTags.argumentLayout)
        assertEquals(2, kotlinTags.sourceArguments.filterIsInstance<LsiSourceAnnotationArgument.Positional>().size)

        val javaNested = javaSetter.nestedAnnotation()
        val kotlinNested = kotlinSetter.nestedAnnotation()
        assertIs<LsiSourceAnnotationArgument.Positional>(javaNested.sourceArguments.single())
        assertEquals(LsiAnnotationArgumentLayout.PLATFORM_DEFAULT, javaNested.argumentLayout)
        assertIs<LsiSourceAnnotationArgument.Positional>(kotlinNested.sourceArguments.single())
        assertEquals(LsiAnnotationArgumentLayout.SINGLE_LINE, kotlinNested.argumentLayout)
    }

    private fun graph(
        dynamicAnnotations: List<DtoAnnotation> = listOf(DtoAnnotation(JSON_ALIAS_TYPE_ID, emptyList())),
    ): DtoGraph {
        val dynamic = prop(
            DYNAMIC_PROP_ID,
            "dynamicName",
            "dynamicName",
            true,
            DtoModifier.DYNAMIC,
            annotations = dynamicAnnotations,
        )
        val required = prop(REQUIRED_PROP_ID, "requiredName", "requiredName", false, DtoModifier.STATIC)
        val fixed = prop(FIXED_PROP_ID, "fixedName", "fixedName", true, DtoModifier.FIXED)
        val fuzzy = prop(FUZZY_PROP_ID, "fuzzyName", "fuzzyName", true, DtoModifier.FUZZY)
        val declarationProps = listOf(dynamic, required, fixed, fuzzy)
        val type = DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = IMMUTABLE_TYPE_ID,
            packageName = "demo",
            name = "BookInput",
            modifiers = setOf(DtoModifier.INPUT),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = declarationProps.map(DtoProp::id),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        return DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(DTO_TYPE_ID),
            types = listOf(type),
            props = declarationProps.sortedBy(DtoProp::id),
        )
    }

    private fun prop(
        id: DtoPropId,
        name: String,
        baseName: String,
        nullable: Boolean,
        modifier: DtoModifier,
        annotations: List<DtoAnnotation> = emptyList(),
    ): DtoBaseProp {
        return DtoBaseProp(
            id = id,
            ownerTypeId = DTO_TYPE_ID,
            name = name,
            alias = name,
            nullable = nullable,
            annotations = annotations,
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(
                DtoBasePropBinding(baseName, LsiSymbolId.property(IMMUTABLE_TYPE_ID, baseName)),
            ),
            basePath = baseName,
            nextPropId = null,
            tailPropId = id,
            baseNullable = nullable,
            inputModifier = modifier,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private fun immutableSchema(): ImmutableSchema {
        val props = listOf("dynamicName", "requiredName", "fixedName", "fuzzyName").map(::immutableProp)
        return ImmutableSchema(
            listOf(
                ImmutableType(
                    id = IMMUTABLE_TYPE_ID,
                    qualifiedName = IMMUTABLE_TYPE_ID.requireTypeQualifiedName(),
                    kind = ImmutableTypeKind.IMMUTABLE,
                    documentation = null,
                    annotations = emptyList(),
                    typeParameterIds = emptyList(),
                    superTypeIds = emptyList(),
                    props = props,
                    primarySuperTypeId = null,
                    inheritanceRootTypeId = null,
                    inheritanceStrategy = null,
                    joinedTableDissociateAction = null,
                    instantiable = false,
                    discriminatorValue = null,
                    discriminatorPropId = null,
                    idPropId = null,
                    versionPropId = null,
                    logicalDeletedPropId = null,
                    acrossMicroServices = false,
                    microServiceName = "",
                ),
            ),
        )
    }

    private fun immutableProp(name: String): ImmutableProp {
        val id = LsiSymbolId.property(IMMUTABLE_TYPE_ID, name)
        return ImmutableProp(
            id = id,
            declarationId = id,
            ownerTypeId = IMMUTABLE_TYPE_ID,
            declaringTypeId = IMMUTABLE_TYPE_ID,
            name = name,
            documentation = null,
            type = LsiDeclaredType(JAVA_STRING_TYPE_ID),
            annotations = emptyList(),
            overrideChain = emptyList(),
            inherited = false,
            overridden = false,
            nullable = false,
            list = false,
            association = false,
            embedded = false,
            targetTypeId = null,
            primaryMapping = PrimaryMapping.SCALAR,
            primaryAnnotationTypeId = null,
            defaultContract = null,
            associationKind = AssociationKind.NONE,
            formulaKind = FormulaKind.NONE,
            mappedBy = null,
            associationStorage = AssociationStorageKind.NONE,
            transientResolver = null,
            view = null,
            genericTarget = false,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = null,
        )
    }

    private fun annotationContract(graph: DtoGraph): DtoAnnotationContract {
        val naming = LsiAnnotation(
            type = JSON_NAMING_TYPE_ID,
            arguments = mapOf(
                "value" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.ClassValue(LsiDeclaredType(NAMING_STRATEGY_TYPE_ID)),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                ),
            ),
        )
        return DtoAnnotationContract(
            declarations = listOf(
                annotationDeclaration(JSON_ALIAS_TYPE_ID),
                annotationDeclaration(JSON_NAMING_TYPE_ID),
            ).sortedBy(DtoAnnotationDeclaration::typeId),
            typePlans = listOf(
                DtoTypeAnnotationPlan(
                    typeId = DTO_TYPE_ID,
                    applications = listOf(
                        DtoAnnotationApplication(
                            annotation = naming,
                            origin = DtoAnnotationOrigin.DTO,
                            sourceSymbolId = null,
                            placements = listOf(DtoAnnotationPlacement.TYPE),
                        ),
                    ),
                ),
            ),
            propPlans = graph.props.map { prop ->
                DtoPropAnnotationPlan(
                    propId = prop.id,
                    propertyApplications = emptyList(),
                    builderSetterApplications = if (prop.id == DYNAMIC_PROP_ID) {
                        listOf(
                            DtoBuilderSetterAnnotationApplication(
                                annotation = LsiAnnotation(JSON_ALIAS_TYPE_ID),
                                origin = DtoAnnotationOrigin.DTO,
                                sourceSymbolId = null,
                            ),
                        )
                    } else {
                        emptyList()
                    },
                )
            },
            diagnostics = emptyList(),
        )
    }

    private fun annotationDeclaration(typeId: LsiSymbolId): DtoAnnotationDeclaration {
        return annotationDeclaration(typeId = typeId, argumentTypes = emptyMap())
    }

    private fun annotationDeclaration(
        typeId: LsiSymbolId,
        language: LsiLanguage = LsiLanguage.JAVA,
        argumentTypes: Map<String, LsiType>,
        kotlinValueVararg: Boolean = false,
        argumentNamesInDeclarationOrder: List<String> = argumentTypes.keys.toList(),
    ): DtoAnnotationDeclaration {
        return DtoAnnotationDeclaration(
            typeId = typeId,
            language = language,
            targetDeclared = true,
            allowedPlacements = listOf(DtoAnnotationPlacement.TYPE),
            argumentTypes = argumentTypes.toSortedMap(),
            kotlinValueVararg = kotlinValueVararg,
            argumentNamesInDeclarationOrder = argumentNamesInDeclarationOrder,
        )
    }

    private fun annotationContract(
        graph: DtoGraph,
        declarations: List<DtoAnnotationDeclaration>,
        applications: List<DtoBuilderSetterAnnotationApplication>,
    ): DtoAnnotationContract {
        return DtoAnnotationContract(
            declarations = declarations.sortedBy(DtoAnnotationDeclaration::typeId),
            typePlans = listOf(DtoTypeAnnotationPlan(DTO_TYPE_ID, emptyList())),
            propPlans = graph.props.map { prop ->
                DtoPropAnnotationPlan(
                    propId = prop.id,
                    propertyApplications = emptyList(),
                    builderSetterApplications = if (prop.id == DYNAMIC_PROP_ID) applications else emptyList(),
                )
            },
            diagnostics = emptyList(),
        )
    }

    private fun inputBuilderSetter(
        graph: DtoGraph,
        contract: DtoAnnotationContract,
        language: LsiLanguage,
    ): LsiMethod {
        val type = graph.types.single().toInputBuilderPoetType(
            graph = graph,
            immutableSchema = immutableSchema(),
            annotationContract = contract,
            targetLanguage = language,
            currentDtoType = LsiDeclaredType(DTO_DECLARATION_TYPE_ID),
            generatedDtoTypes = emptyMap(),
            jsonPojoBuilderAnnotationTypeId = JSON_POJO_BUILDER_TYPE_ID,
            jsonNamingAnnotationTypeId = JSON_NAMING_TYPE_ID,
        )
        return type.members.filterIsInstance<LsiMethod>().single { function ->
            function.name == "dynamicName"
        }
    }

    private fun LsiMethod.nestedAnnotation(): LsiAnnotation {
        val wrapper = annotations.single { annotation -> annotation.type == WRAPPER_ANNOTATION_TYPE_ID }
        val wrapperArgument = assertIs<LsiSourceAnnotationArgument.Named>(wrapper.sourceArguments.single())
        return assertIs<LsiAnnotationValue.NestedAnnotationValue>(wrapperArgument.value).annotation
    }

    private fun explicit(value: LsiAnnotationValue): LsiAnnotationArgument {
        return LsiAnnotationArgument(value, LsiAnnotationArgumentOrigin.EXPLICIT)
    }

    private companion object {
        val SOURCE = LsiSource.of("demo/Book.dto")
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val DTO_TYPE_ID = DtoTypeId("dto#book-input")
        val DYNAMIC_PROP_ID = DtoPropId("dto#a-dynamic")
        val REQUIRED_PROP_ID = DtoPropId("dto#b-required")
        val FIXED_PROP_ID = DtoPropId("dto#c-fixed")
        val FUZZY_PROP_ID = DtoPropId("dto#d-fuzzy")
        val IMMUTABLE_TYPE_ID = LsiSymbolId.type("demo.Book")
        val DTO_DECLARATION_TYPE_ID = LsiSymbolId.type("demo.BookInput")
        val BUILDER_TYPE_ID = LsiSymbolId.type("demo.BookInput.Builder")
        val JAVA_STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")
        val KOTLIN_STRING_TYPE_ID = LsiSymbolId.type("kotlin.String")
        val INPUT_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.Input")
        val OBJECTS_TYPE_ID = LsiSymbolId.type("java.util.Objects")
        val GENERATED_BY_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.internal.GeneratedBy")
        val JSON_POJO_BUILDER_TYPE_ID =
            LsiSymbolId.type("com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder")
        val JSON_NAMING_TYPE_ID =
            LsiSymbolId.type("com.fasterxml.jackson.databind.annotation.JsonNaming")
        val JSON_ALIAS_TYPE_ID = LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonAlias")
        val ORDERED_ANNOTATION_TYPE_ID = LsiSymbolId.type("demo.Ordered")
        val TAGS_ANNOTATION_TYPE_ID = LsiSymbolId.type("demo.Tags")
        val NESTED_ANNOTATION_TYPE_ID = LsiSymbolId.type("demo.Nested")
        val WRAPPER_ANNOTATION_TYPE_ID = LsiSymbolId.type("demo.Wrapper")
        val NAMING_STRATEGY_TYPE_ID = LsiSymbolId.type("demo.SnakeCaseStrategy")
        val TYPE_NAMES = listOf(
            LsiClass(DTO_DECLARATION_TYPE_ID, "demo", listOf("BookInput")),
            LsiClass(BUILDER_TYPE_ID, "demo", listOf("BookInput", "Builder")),
            LsiClass(JAVA_STRING_TYPE_ID, "java.lang", listOf("String")),
            LsiClass(KOTLIN_STRING_TYPE_ID, "kotlin", listOf("String")),
            LsiClass(INPUT_TYPE_ID, "org.babyfish.jimmer", listOf("Input")),
            LsiClass(OBJECTS_TYPE_ID, "java.util", listOf("Objects")),
            LsiClass(GENERATED_BY_TYPE_ID, "org.babyfish.jimmer.internal", listOf("GeneratedBy")),
            LsiClass(
                JSON_POJO_BUILDER_TYPE_ID,
                "com.fasterxml.jackson.databind.annotation",
                listOf("JsonPOJOBuilder"),
            ),
            LsiClass(
                JSON_NAMING_TYPE_ID,
                "com.fasterxml.jackson.databind.annotation",
                listOf("JsonNaming"),
            ),
            LsiClass(JSON_ALIAS_TYPE_ID, "com.fasterxml.jackson.annotation", listOf("JsonAlias")),
            LsiClass(NAMING_STRATEGY_TYPE_ID, "demo", listOf("SnakeCaseStrategy")),
        )

        val EXPECTED_JAVA =
            """
                @com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder(
                    withPrefix = ""
                )
                @com.fasterxml.jackson.databind.annotation.JsonNaming(demo.SnakeCaseStrategy.class)
                public static class Builder {
                  private java.lang.String dynamicName;

                  private boolean _isDynamicNameLoaded;

                  private java.lang.String requiredName;

                  private java.lang.String fixedName;

                  private boolean _isFixedNameLoaded;

                  private java.lang.String fuzzyName;

                  @com.fasterxml.jackson.annotation.JsonAlias
                  public demo.BookInput.Builder dynamicName(java.lang.String dynamicName) {
                    this.dynamicName = dynamicName;
                    this._isDynamicNameLoaded = true;
                    return this;
                  }

                  public demo.BookInput.Builder requiredName(java.lang.String requiredName) {
                    this.requiredName = java.util.Objects.requireNonNull(requiredName, "The property \"requiredName\" cannot be null");
                    return this;
                  }

                  public demo.BookInput.Builder fixedName(java.lang.String fixedName) {
                    this.fixedName = fixedName;
                    this._isFixedNameLoaded = true;
                    return this;
                  }

                  public demo.BookInput.Builder fuzzyName(java.lang.String fuzzyName) {
                    this.fuzzyName = fuzzyName;
                    return this;
                  }

                  public demo.BookInput build() {
                    demo.BookInput _input = new demo.BookInput();
                    if (_isDynamicNameLoaded) {
                      _input.setDynamicName(dynamicName);
                    }
                    if (requiredName == null) {
                      throw org.babyfish.jimmer.Input.unknownNonNullProperty(demo.BookInput.class, "requiredName");
                    }
                    _input.setRequiredName(requiredName);
                    if (!_isFixedNameLoaded) {
                      throw org.babyfish.jimmer.Input.unknownNullableProperty(demo.BookInput.class, "fixedName");
                    }
                    _input.setFixedName(fixedName);
                    if (fuzzyName != null) {
                      _input.setFuzzyName(fuzzyName);
                    }
                    return _input;
                  }
                }
            """.trimIndent() + "\n"

        val EXPECTED_KOTLIN =
            """
                @org.babyfish.jimmer.`internal`.GeneratedBy
                @com.fasterxml.jackson.databind.`annotation`.JsonPOJOBuilder(withPrefix = "")
                @com.fasterxml.jackson.databind.`annotation`.JsonNaming(value = demo.SnakeCaseStrategy::class)
                public class Builder {
                  private var dynamicName: kotlin.String? = null

                  private var isDynamicNameLoaded: kotlin.Boolean = false

                  private var requiredName: kotlin.String? = null

                  private var fixedName: kotlin.String? = null

                  private var isFixedNameLoaded: kotlin.Boolean = false

                  private var fuzzyName: kotlin.String? = null

                  @com.fasterxml.jackson.`annotation`.JsonAlias
                  public fun dynamicName(dynamicName: kotlin.String?): demo.BookInput.Builder {
                    this.dynamicName = dynamicName
                    this.isDynamicNameLoaded = true
                    return this
                  }

                  public fun requiredName(requiredName: kotlin.String): demo.BookInput.Builder {
                    this.requiredName = requiredName
                    return this
                  }

                  public fun fixedName(fixedName: kotlin.String?): demo.BookInput.Builder {
                    this.fixedName = fixedName
                    this.isFixedNameLoaded = true
                    return this
                  }

                  public fun fuzzyName(fuzzyName: kotlin.String?): demo.BookInput.Builder {
                    this.fuzzyName = fuzzyName
                    return this
                  }

                  public fun build(): demo.BookInput = demo.BookInput(
                    // DYNAMIC
                    dynamicName,
                    isDynamicNameLoaded,
                    requiredName ?: throw org.babyfish.jimmer.Input.unknownNonNullProperty(demo.BookInput::class.java, "requiredName"),
                    // FIXED
                    if (!isFixedNameLoaded) {
                      throw org.babyfish.jimmer.Input.unknownNullableProperty(demo.BookInput::class.java, "fixedName")} else {
                      fixedName}
                    ,
                    fuzzyName,
                  )
                }
            """.trimIndent() + "\n"
    }
}
