package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationMember
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiEnumEntry
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace

class DtoAnnotationStructuredValueContractTest {

    @Test
    fun `freezes valid enum and rejects wrong enum type or missing entry`() {
        val valid = resolve(
            dtoAnnotation(
                ENUM_HOLDER,
                "value" to DtoAnnotationValue.EnumValue(MODE, "READ"),
            )
        )

        assertTrue(valid.diagnostics.isEmpty())
        val enumValue = assertIs<LsiAnnotationValue.EnumValue>(
            valid.application().annotation.arguments.getValue("value").value
        )
        assertEquals(MODE, enumValue.enumType)
        assertEquals("READ", enumValue.entryName)

        val wrongType = resolve(
            dtoAnnotation(
                ENUM_HOLDER,
                "value" to DtoAnnotationValue.EnumValue(OTHER_MODE, "READ"),
            )
        )
        assertTrue(wrongType.typeApplications().isEmpty())
        assertTrue(wrongType.hasArgumentTypeDiagnostic("value"))

        val missingEntry = resolve(
            dtoAnnotation(
                ENUM_HOLDER,
                "value" to DtoAnnotationValue.EnumValue(MODE, "MISSING"),
            )
        )
        assertTrue(missingEntry.typeApplications().isEmpty())
        assertTrue(missingEntry.hasArgumentTypeDiagnostic("value"))
    }

    @Test
    fun `freezes nested annotation values recursively into typed lsi values`() {
        val contract = resolve(
            dtoAnnotation(
                NESTED_HOLDER,
                "value" to DtoAnnotationValue.AnnotationValue(
                    dtoAnnotation(
                        NESTED,
                        "inner" to DtoAnnotationValue.AnnotationValue(
                            dtoAnnotation(
                                INNER,
                                "count" to DtoAnnotationValue.LiteralValue("7"),
                            )
                        ),
                        "mode" to DtoAnnotationValue.EnumValue(MODE, "WRITE"),
                    )
                ),
            )
        )

        assertTrue(contract.diagnostics.isEmpty())
        val nestedValue = assertIs<LsiAnnotationValue.NestedAnnotationValue>(
            contract.application().annotation.arguments.getValue("value").value
        )
        assertEquals(NESTED, nestedValue.annotation.type)
        val modeValue = assertIs<LsiAnnotationValue.EnumValue>(
            nestedValue.annotation.arguments.getValue("mode").value
        )
        assertEquals(LsiAnnotationValue.EnumValue(MODE, "WRITE"), modeValue)

        val innerValue = assertIs<LsiAnnotationValue.NestedAnnotationValue>(
            nestedValue.annotation.arguments.getValue("inner").value
        )
        assertEquals(INNER, innerValue.annotation.type)
        val countValue = assertIs<LsiAnnotationValue.IntValue>(
            innerValue.annotation.arguments.getValue("count").value
        )
        assertEquals(7, countValue.value)
    }

    @Test
    fun `accepts class and kclass literals with star or matching out bound`() {
        val valid = resolve(classLiteralAnnotation(javaBounded = CHILD, kotlinBounded = CHILD))

        assertTrue(valid.diagnostics.isEmpty())
        val annotation = valid.application().annotation
        assertEquals(OTHER, annotation.classValue("javaAny").declaredTypeId())
        assertEquals(OTHER, annotation.classValue("kotlinAny").declaredTypeId())
        assertEquals(CHILD, annotation.classValue("javaBounded").declaredTypeId())
        assertEquals(CHILD, annotation.classValue("kotlinBounded").declaredTypeId())

        val declaration = valid.declarationsByTypeId.getValue(CLASS_HOLDER)
        assertEquals(
            JAVA_CLASS,
            assertIs<LsiDeclaredType>(declaration.argumentTypes.getValue("kotlinAny")).declarationId,
        )
        assertEquals(
            JAVA_CLASS,
            assertIs<LsiDeclaredType>(declaration.argumentTypes.getValue("kotlinBounded")).declarationId,
        )

        val invalidJava = resolve(classLiteralAnnotation(javaBounded = OTHER, kotlinBounded = CHILD))
        assertTrue(invalidJava.typeApplications().isEmpty())
        assertTrue(invalidJava.hasArgumentTypeDiagnostic("javaBounded"))

        val invalidKotlin = resolve(classLiteralAnnotation(javaBounded = CHILD, kotlinBounded = OTHER))
        assertTrue(invalidKotlin.typeApplications().isEmpty())
        assertTrue(invalidKotlin.hasArgumentTypeDiagnostic("kotlinBounded"))
    }

    @Test
    fun `freezes nullable primitive class literal as boxed primitive`() {
        val contract = resolve(
            dtoAnnotation(
                BOXED_HOLDER,
                "integer" to DtoAnnotationValue.TypeValue(typeRef("Int")),
                "number" to DtoAnnotationValue.TypeValue(typeRef("Int", nullable = true)),
                "serializable" to DtoAnnotationValue.TypeValue(typeRef("Int")),
                "comparable" to DtoAnnotationValue.TypeValue(typeRef("Int")),
                "value" to DtoAnnotationValue.TypeValue(typeRef("Int", nullable = true)),
            )
        )

        assertTrue(contract.diagnostics.isEmpty())
        val classValue = contract.application().annotation.classValue("value")
        val primitiveType = assertIs<LsiPrimitiveType>(classValue.type)
        assertEquals(LsiPrimitiveKind.INT, primitiveType.kind)
        assertEquals(LsiNullability.NON_NULL, primitiveType.nullability)
        assertTrue(primitiveType.boxed)
        assertEquals(
            LsiPrimitiveKind.INT,
            assertIs<LsiPrimitiveType>(contract.application().annotation.classValue("integer").type).kind,
        )
        assertEquals(
            LsiPrimitiveKind.INT,
            assertIs<LsiPrimitiveType>(contract.application().annotation.classValue("number").type).kind,
        )
        assertEquals(
            LsiPrimitiveKind.INT,
            assertIs<LsiPrimitiveType>(
                contract.application().annotation.classValue("serializable").type,
            ).kind,
        )
        assertEquals(
            LsiPrimitiveKind.INT,
            assertIs<LsiPrimitiveType>(
                contract.application().annotation.classValue("comparable").type,
            ).kind,
        )
    }

    @Test
    fun `resolves generic class literal bounds with payload arguments`() {
        val valid = resolve(
            dtoAnnotation(
                GENERIC_HOLDER,
                "value" to DtoAnnotationValue.TypeValue(
                    genericTypeRef(GENERIC_CHILD, "String")
                ),
            )
        )

        assertTrue(valid.diagnostics.isEmpty(), valid.diagnostics.joinToString { it.message })
        val payload = assertIs<LsiDeclaredType>(valid.application().annotation.classValue("value").type)
        assertEquals(GENERIC_CHILD, payload.declarationId)
        assertEquals(
            LsiSymbolId.type("java.lang.String"),
            assertIs<LsiDeclaredType>(payload.arguments.single().type).declarationId,
        )

        val invalid = resolve(
            dtoAnnotation(
                GENERIC_HOLDER,
                "value" to DtoAnnotationValue.TypeValue(
                    genericTypeRef(GENERIC_CHILD, OTHER.requireTypeQualifiedName())
                ),
            )
        )
        assertTrue(invalid.typeApplications().isEmpty())
        assertTrue(invalid.hasArgumentTypeDiagnostic("value"))
    }

    @Test
    fun `freezes immutable origin structured lsi annotation values recursively`() {
        val structuredAnnotation = LsiAnnotation(
            type = LSI_HOLDER,
            arguments = mapOf(
                "array" to explicit(
                    LsiAnnotationValue.ArrayValue(
                        listOf(
                            LsiAnnotationValue.IntValue(2),
                            LsiAnnotationValue.IntValue(3),
                        )
                    )
                ),
                "clazz" to explicit(LsiAnnotationValue.ClassValue(LsiDeclaredType(CHILD))),
                "enum" to explicit(LsiAnnotationValue.EnumValue(MODE, "READ")),
                "nested" to explicit(
                    LsiAnnotationValue.NestedAnnotationValue(
                        LsiAnnotation(
                            type = NESTED,
                            arguments = mapOf(
                                "inner" to explicit(
                                    LsiAnnotationValue.NestedAnnotationValue(
                                        LsiAnnotation(
                                            type = INNER,
                                            arguments = mapOf(
                                                "count" to explicit(LsiAnnotationValue.IntValue(7))
                                            ),
                                        )
                                    )
                                ),
                                "mode" to explicit(LsiAnnotationValue.EnumValue(MODE, "WRITE")),
                            ),
                        )
                    )
                ),
            ),
        )
        val contract = WORKSPACE.resolveDtoAnnotationContract(
            graph = immutableGraph(),
            immutableSchema = immutableSchema(structuredAnnotation),
        )

        assertTrue(
            contract.diagnostics.isEmpty(),
            contract.diagnostics.joinToString { diagnostic -> diagnostic.message },
        )
        val application = contract.application()
        assertEquals(DtoAnnotationOrigin.IMMUTABLE, application.origin)
        assertEquals(IMMUTABLE_BASE, application.sourceSymbolId)
        assertEquals(
            structuredAnnotation.arguments.mapValues { (_, argument) -> argument.value },
            application.annotation.arguments.mapValues { (_, argument) -> argument.value },
        )
    }

    private fun resolve(annotation: DtoAnnotation): DtoAnnotationContract {
        return WORKSPACE.resolveDtoAnnotationContract(graph(annotation), ImmutableSchema(emptyList()))
    }

    private fun immutableGraph(): DtoGraph {
        return DtoGraph(
            source = DTO_SOURCE,
            rootTypeIds = listOf(DTO_TYPE),
            types = listOf(
                DtoType(
                    id = DTO_TYPE,
                    baseTypeId = IMMUTABLE_BASE,
                    packageName = "demo.dto",
                    name = "StructuredView",
                    modifiers = emptySet(),
                    annotations = emptyList(),
                    superInterfaces = emptyList(),
                    documentation = null,
                    location = DTO_LOCATION,
                    focusedRecursion = false,
                    propIds = emptyList(),
                    hiddenFlatPropIds = emptyList(),
                    polymorphism = null,
                )
            ),
            props = emptyList(),
        )
    }

    private fun immutableSchema(annotation: LsiAnnotation): ImmutableSchema {
        return ImmutableSchema(
            listOf(
                ImmutableType(
                    id = IMMUTABLE_BASE,
                    qualifiedName = IMMUTABLE_BASE.requireTypeQualifiedName(),
                    kind = ImmutableTypeKind.IMMUTABLE,
                    documentation = null,
                    annotations = listOf(annotation),
                    typeParameterIds = emptyList(),
                    superTypeIds = emptyList(),
                    props = emptyList(),
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
                )
            )
        )
    }

    private fun explicit(value: LsiAnnotationValue): LsiAnnotationArgument {
        return LsiAnnotationArgument(value, LsiAnnotationArgumentOrigin.EXPLICIT)
    }

    private fun DtoAnnotationContract.application(): DtoAnnotationApplication {
        return typeApplications().single()
    }

    private fun DtoAnnotationContract.typeApplications(): List<DtoAnnotationApplication> {
        return typePlansByTypeId.getValue(DTO_TYPE).applications
    }

    private fun DtoAnnotationContract.hasArgumentTypeDiagnostic(argumentName: String): Boolean {
        return diagnostics.any { diagnostic ->
            diagnostic.code == "jimmer.dto.annotation.argument-type" &&
                diagnostic.details["argument"] == argumentName
        }
    }

    private fun LsiAnnotation.classValue(
        argumentName: String,
    ): LsiAnnotationValue.ClassValue {
        return assertIs(arguments.getValue(argumentName).value)
    }

    private fun LsiAnnotationValue.ClassValue.declaredTypeId(): LsiSymbolId {
        return assertIs<LsiDeclaredType>(type).declarationId
    }

    private fun classLiteralAnnotation(
        javaBounded: LsiSymbolId,
        kotlinBounded: LsiSymbolId,
    ): DtoAnnotation {
        return dtoAnnotation(
            CLASS_HOLDER,
            "javaAny" to DtoAnnotationValue.TypeValue(typeRef(OTHER.requireTypeQualifiedName())),
            "javaBounded" to DtoAnnotationValue.TypeValue(typeRef(javaBounded.requireTypeQualifiedName())),
            "kotlinAny" to DtoAnnotationValue.TypeValue(typeRef(OTHER.requireTypeQualifiedName())),
            "kotlinBounded" to DtoAnnotationValue.TypeValue(typeRef(kotlinBounded.requireTypeQualifiedName())),
        )
    }

    private fun graph(annotation: DtoAnnotation): DtoGraph {
        return DtoGraph(
            source = DTO_SOURCE,
            rootTypeIds = listOf(DTO_TYPE),
            types = listOf(
                DtoType(
                    id = DTO_TYPE,
                    baseTypeId = null,
                    packageName = "demo.dto",
                    name = "StructuredView",
                    modifiers = emptySet(),
                    annotations = listOf(annotation),
                    superInterfaces = emptyList(),
                    documentation = null,
                    location = DTO_LOCATION,
                    focusedRecursion = false,
                    propIds = emptyList(),
                    hiddenFlatPropIds = emptyList(),
                    polymorphism = null,
                )
            ),
            props = emptyList(),
        )
    }

    private fun dtoAnnotation(
        typeId: LsiSymbolId,
        vararg arguments: Pair<String, DtoAnnotationValue>,
    ): DtoAnnotation {
        return DtoAnnotation(
            typeId = typeId,
            arguments = arguments.map { (name, value) -> DtoAnnotationArgument(name, value) },
        )
    }

    private fun typeRef(
        typeName: String,
        nullable: Boolean = false,
        arguments: List<DtoTypeArgument> = emptyList(),
    ): DtoTypeRef {
        return DtoTypeRef(
            typeName = typeName,
            arguments = arguments,
            nullable = nullable,
            location = DTO_LOCATION,
        )
    }

    private fun genericTypeRef(
        typeId: LsiSymbolId,
        argumentTypeName: String,
    ): DtoTypeRef {
        return typeRef(
            typeName = typeId.requireTypeQualifiedName(),
            arguments = listOf(
                DtoTypeArgument(
                    variance = DtoVariance.INVARIANT,
                    type = typeRef(argumentTypeName),
                )
            ),
        )
    }

    companion object {
        private val DTO_SOURCE = LsiSource.of("dto/Structured.dto", LsiLanguage.UNKNOWN)
        private val DTO_LOCATION = LsiLocation(DTO_SOURCE, LsiPosition(1, 1))
        private val ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)

        private val DTO_TYPE = DtoTypeId("dto/Structured.dto#root")
        private val MODE = LsiSymbolId.type("demo.Mode")
        private val OTHER_MODE = LsiSymbolId.type("demo.OtherMode")
        private val INNER = LsiSymbolId.type("demo.Inner")
        private val NESTED = LsiSymbolId.type("demo.Nested")
        private val ENUM_HOLDER = LsiSymbolId.type("demo.EnumHolder")
        private val NESTED_HOLDER = LsiSymbolId.type("demo.NestedHolder")
        private val CLASS_HOLDER = LsiSymbolId.type("demo.ClassHolder")
        private val BOXED_HOLDER = LsiSymbolId.type("demo.BoxedHolder")
        private val GENERIC_HOLDER = LsiSymbolId.type("demo.GenericHolder")
        private val LSI_HOLDER = LsiSymbolId.type("demo.LsiHolder")
        private val IMMUTABLE_BASE = LsiSymbolId.type("demo.ImmutableBase")

        private val BASE = LsiSymbolId.type("demo.Base")
        private val CHILD = LsiSymbolId.type("demo.Child")
        private val OTHER = LsiSymbolId.type("demo.Other")
        private val GENERIC_BASE = LsiSymbolId.type("demo.GenericBase")
        private val GENERIC_CHILD = LsiSymbolId.type("demo.GenericChild")
        private val GENERIC_BASE_PARAMETER = LsiSymbolId.typeParameter(GENERIC_BASE, "T")
        private val GENERIC_CHILD_PARAMETER = LsiSymbolId.typeParameter(GENERIC_CHILD, "T")
        private val JAVA_CLASS = LsiSymbolId.type("java.lang.Class")
        private val JAVA_INTEGER = LsiSymbolId.type("java.lang.Integer")
        private val JAVA_NUMBER = LsiSymbolId.type("java.lang.Number")
        private val JAVA_CHAR_SEQUENCE = LsiSymbolId.type("java.lang.CharSequence")
        private val JAVA_STRING = LsiSymbolId.type("java.lang.String")
        private val JAVA_SERIALIZABLE = LsiSymbolId.type("java.io.Serializable")
        private val JAVA_COMPARABLE = LsiSymbolId.type("java.lang.Comparable")
        private val KOTLIN_KCLASS = LsiSymbolId.type("kotlin.reflect.KClass")

        private val WORKSPACE = LsiWorkspace(
            declarations = listOf(
                enumDeclaration(MODE, "READ", "WRITE"),
                enumDeclaration(OTHER_MODE, "READ"),
                annotationDeclaration(
                    INNER,
                    LsiAnnotationMember("count", LsiPrimitiveType(LsiPrimitiveKind.INT)),
                ),
                annotationDeclaration(
                    NESTED,
                    LsiAnnotationMember("inner", LsiDeclaredType(INNER)),
                    LsiAnnotationMember("mode", LsiDeclaredType(MODE)),
                ),
                annotationDeclaration(
                    ENUM_HOLDER,
                    LsiAnnotationMember("value", LsiDeclaredType(MODE)),
                ),
                annotationDeclaration(
                    NESTED_HOLDER,
                    LsiAnnotationMember("value", LsiDeclaredType(NESTED)),
                ),
                annotationDeclaration(
                    CLASS_HOLDER,
                    LsiAnnotationMember("javaAny", classLiteralType(JAVA_CLASS, LsiTypeArgument.STAR)),
                    LsiAnnotationMember(
                        "javaBounded",
                        classLiteralType(JAVA_CLASS, LsiTypeArgument.output(LsiDeclaredType(BASE))),
                    ),
                    LsiAnnotationMember("kotlinAny", classLiteralType(KOTLIN_KCLASS, LsiTypeArgument.STAR)),
                    LsiAnnotationMember(
                        "kotlinBounded",
                        classLiteralType(KOTLIN_KCLASS, LsiTypeArgument.output(LsiDeclaredType(BASE))),
                    ),
                ),
                annotationDeclaration(
                    BOXED_HOLDER,
                    LsiAnnotationMember(
                        "integer",
                        classLiteralType(JAVA_CLASS, LsiTypeArgument.invariant(LsiDeclaredType(JAVA_INTEGER))),
                    ),
                    LsiAnnotationMember(
                        "number",
                        classLiteralType(JAVA_CLASS, LsiTypeArgument.output(LsiDeclaredType(JAVA_NUMBER))),
                    ),
                    LsiAnnotationMember(
                        "serializable",
                        classLiteralType(
                            JAVA_CLASS,
                            LsiTypeArgument.output(LsiDeclaredType(JAVA_SERIALIZABLE)),
                        ),
                    ),
                    LsiAnnotationMember(
                        "comparable",
                        classLiteralType(
                            JAVA_CLASS,
                            LsiTypeArgument.output(
                                LsiDeclaredType(
                                    JAVA_COMPARABLE,
                                    listOf(LsiTypeArgument.STAR),
                                ),
                            ),
                        ),
                    ),
                    LsiAnnotationMember("value", classLiteralType(KOTLIN_KCLASS, LsiTypeArgument.STAR)),
                ),
                annotationDeclaration(
                    GENERIC_HOLDER,
                    LsiAnnotationMember(
                        "value",
                        classLiteralType(
                            JAVA_CLASS,
                            LsiTypeArgument.output(
                                LsiDeclaredType(
                                    GENERIC_BASE,
                                    listOf(
                                        LsiTypeArgument.output(
                                            LsiDeclaredType(JAVA_CHAR_SEQUENCE)
                                        )
                                    ),
                                )
                            ),
                        ),
                    ),
                ),
                annotationDeclaration(
                    LSI_HOLDER,
                    LsiAnnotationMember(
                        "array",
                        LsiArrayType(LsiPrimitiveType(LsiPrimitiveKind.INT)),
                    ),
                    LsiAnnotationMember(
                        "clazz",
                        classLiteralType(JAVA_CLASS, LsiTypeArgument.output(LsiDeclaredType(BASE))),
                    ),
                    LsiAnnotationMember("enum", LsiDeclaredType(MODE)),
                    LsiAnnotationMember("nested", LsiDeclaredType(NESTED)),
                ),
                classDeclaration(BASE),
                classDeclaration(CHILD, BASE),
                classDeclaration(OTHER),
                classDeclaration(JAVA_CHAR_SEQUENCE),
                classDeclaration(JAVA_STRING, JAVA_CHAR_SEQUENCE),
                typeDeclaration(
                    id = GENERIC_BASE,
                    kind = LsiTypeDeclarationKind.CLASS,
                    typeParameters = listOf(LsiTypeParameter(GENERIC_BASE_PARAMETER, "T")),
                ),
                typeDeclaration(
                    id = GENERIC_CHILD,
                    kind = LsiTypeDeclarationKind.CLASS,
                    superTypes = listOf(
                        LsiDeclaredType(
                            GENERIC_BASE,
                            listOf(
                                LsiTypeArgument.invariant(
                                    LsiTypeParameterRef(GENERIC_CHILD_PARAMETER)
                                )
                            ),
                        )
                    ),
                    typeParameters = listOf(LsiTypeParameter(GENERIC_CHILD_PARAMETER, "T")),
                ),
            )
        )

        private fun annotationDeclaration(
            id: LsiSymbolId,
            vararg members: LsiAnnotationMember,
        ): LsiTypeDeclaration {
            return typeDeclaration(
                id = id,
                kind = LsiTypeDeclarationKind.ANNOTATION,
                annotationMembers = members.sortedBy(LsiAnnotationMember::name),
            )
        }

        private fun enumDeclaration(
            id: LsiSymbolId,
            vararg entries: String,
        ): LsiTypeDeclaration {
            return typeDeclaration(
                id = id,
                kind = LsiTypeDeclarationKind.ENUM,
                enumEntries = entries.map { name ->
                    LsiEnumEntry(
                        id = LsiSymbolId.enumEntry(id, name),
                        name = name,
                        ownerId = id,
                        origin = ORIGIN,
                    )
                },
            )
        }

        private fun classDeclaration(
            id: LsiSymbolId,
            superTypeId: LsiSymbolId? = null,
        ): LsiTypeDeclaration {
            return typeDeclaration(
                id = id,
                kind = LsiTypeDeclarationKind.CLASS,
                superTypes = superTypeId?.let { typeId -> listOf(LsiDeclaredType(typeId)) }.orEmpty(),
            )
        }

        private fun typeDeclaration(
            id: LsiSymbolId,
            kind: LsiTypeDeclarationKind,
            superTypes: List<LsiTypeRef> = emptyList(),
            enumEntries: List<LsiEnumEntry> = emptyList(),
            annotationMembers: List<LsiAnnotationMember> = emptyList(),
            typeParameters: List<LsiTypeParameter> = emptyList(),
        ): LsiTypeDeclaration {
            val qualifiedName = id.requireTypeQualifiedName()
            return LsiTypeDeclaration(
                id = id,
                name = qualifiedName.substringAfterLast('.'),
                qualifiedName = qualifiedName,
                kind = kind,
                typeParameters = typeParameters,
                superTypes = superTypes,
                enumEntries = enumEntries,
                annotationMembers = annotationMembers,
                origin = ORIGIN,
            )
        }

        private fun classLiteralType(
            classTypeId: LsiSymbolId,
            argument: LsiTypeArgument,
        ): LsiDeclaredType {
            return LsiDeclaredType(classTypeId, listOf(argument))
        }
    }
}
