package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
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
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoEnumMapping
import site.addzero.lsi.jimmer.dto.DtoEnumType
import site.addzero.lsi.jimmer.dto.DtoGraph
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.scalarType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class DtoEnumPoetTest {

    @Test
    fun `renders string enum accessors byte for byte`() {
        val fixture = fixture(
            numeric = false,
            mappings = listOf(
                DtoEnumMapping("MALE", "\"Male\""),
                DtoEnumMapping("FEMALE", "\"Female\""),
            ),
        )

        assertEquals(
            """
                arg -> {
                  switch ((demo.Gender)arg) {
                    case MALE:
                      return "Male";
                    case FEMALE:
                      return "Female";
                    default:
                      throw new AssertionError("Internal bug");
                  }
                }
            """.trimIndent(),
            renderJava(
                fixture.prop.toEnumToScalarLambdaPoetCodeBlock(
                    LsiLanguage.JAVA,
                    fixture.graph,
                    fixture.schema,
                ),
            ),
        )
        assertEquals(
            """
                arg -> {
                  switch ((java.lang.String)arg) {
                    case "Male":
                      return demo.Gender.MALE;
                    case "Female":
                      return demo.Gender.FEMALE;
                    default:
                      throw new IllegalArgumentException("Illegal value `\"" + arg + "\"`for enum type: \"demo.Gender\"");
                  }
                }
            """.trimIndent(),
            renderJava(
                fixture.prop.toScalarToEnumLambdaPoetCodeBlock(
                    LsiLanguage.JAVA,
                    fixture.graph,
                    fixture.schema,
                ),
            ),
        )
        assertEquals(
            """
                {
                  when (it as demo.Gender) {
                    demo.Gender.MALE -> "Male"
                    demo.Gender.FEMALE -> "Female"
                  }
                }
            """.trimIndent(),
            renderKotlin(
                fixture.prop.toEnumToScalarLambdaPoetCodeBlock(
                    LsiLanguage.KOTLIN,
                    fixture.graph,
                    fixture.schema,
                ),
            ),
        )
        assertEquals(
            """
                {
                  when (it as kotlin.String) {
                    "Male" -> demo.Gender.MALE
                    "Female" -> demo.Gender.FEMALE
                    else -> throw IllegalArgumentException(
                      "Illegal value \"" + it + "\" for the enum type \"demo.Gender\""
                    )
                  }
                }
            """.trimIndent(),
            renderKotlin(
                fixture.prop.toScalarToEnumLambdaPoetCodeBlock(
                    LsiLanguage.KOTLIN,
                    fixture.graph,
                    fixture.schema,
                ),
            ),
        )
    }

    @Test
    fun `renders numeric enum conversion and nullable scalar types`() {
        val fixture = fixture(
            numeric = true,
            nullable = true,
            mappings = listOf(
                DtoEnumMapping("NEW", "10"),
                DtoEnumMapping("DONE", "20"),
            ),
        )

        assertEquals(
            """
                switch ((int)value) {
                  case 10:
                    return demo.Gender.NEW;
                  case 20:
                    return demo.Gender.DONE;
                  default:
                    throw new IllegalArgumentException("Illegal value `\"" + value + "\"`for enum type: \"demo.Gender\"");
                }

            """.trimIndent(),
            renderJava(
                fixture.prop.toScalarToEnumPoetCodeBlock(
                    LsiLanguage.JAVA,
                    fixture.graph,
                    fixture.schema,
                    "value",
                ),
            ),
        )
        assertEquals(
            """
                when (value as kotlin.Int) {
                  10 -> demo.Gender.NEW
                  20 -> demo.Gender.DONE
                  else -> throw IllegalArgumentException(
                    "Illegal value \"" + value + "\" for the enum type \"demo.Gender\""
                  )
                }

            """.trimIndent(),
            renderKotlin(
                fixture.prop.toScalarToEnumPoetCodeBlock(
                    LsiLanguage.KOTLIN,
                    fixture.graph,
                    fixture.schema,
                    "value",
                ),
            ),
        )
        val enumType = requireNotNull(fixture.prop.enumType)
        val javaScalarType = enumType.scalarType(LsiLanguage.JAVA)
        val renderedJavaScalarType = LsiJavaPoetRenderer().renderTypeName(
            type = javaScalarType,
            typeNames = WORKSPACE.dtoTypeRefPoetTypeNames(javaScalarType, emptyList()),
        )
        val kotlinScalarType = enumType.scalarType(LsiLanguage.KOTLIN)
        val renderedKotlinScalarType = LsiKotlinPoetRenderer().renderTypeName(
            type = kotlinScalarType,
            typeNames = WORKSPACE.dtoTypeRefPoetTypeNames(kotlinScalarType, emptyList()),
        )
        assertEquals("java.lang.Integer", renderedJavaScalarType.box().toString())
        assertEquals("kotlin.Int?", renderedKotlinScalarType.copy(nullable = fixture.prop.nullable).toString())
    }

    private fun renderJava(codeBlock: LsiCodeBlock): String {
        return LsiJavaPoetRenderer().renderCodeBlock(
            codeBlock = codeBlock,
            typeNames = WORKSPACE.dtoEnumPoetTypeNames(codeBlock),
        ).toString()
    }

    private fun renderKotlin(codeBlock: LsiCodeBlock): String {
        return LsiKotlinPoetRenderer().renderCodeBlock(
            codeBlock = codeBlock,
            typeNames = WORKSPACE.dtoEnumPoetTypeNames(codeBlock),
        ).toString()
    }

    private fun fixture(
        numeric: Boolean,
        mappings: List<DtoEnumMapping>,
        nullable: Boolean = false,
    ): Fixture {
        val prop = DtoBaseProp(
            id = DTO_PROP_ID,
            ownerTypeId = DTO_TYPE_ID,
            name = "gender",
            alias = null,
            nullable = nullable,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(DtoBasePropBinding("gender", IMMUTABLE_PROP_ID)),
            basePath = "gender",
            nextPropId = null,
            tailPropId = DTO_PROP_ID,
            baseNullable = nullable,
            inputModifier = DtoModifier.STATIC,
            functionName = null,
            targetTypeId = null,
            enumType = DtoEnumType(numeric, mappings),
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(DTO_TYPE_ID),
            types = listOf(
                DtoType(
                    id = DTO_TYPE_ID,
                    baseTypeId = IMMUTABLE_TYPE_ID,
                    packageName = "demo.dto",
                    name = "GenderView",
                    modifiers = emptySet(),
                    annotations = emptyList(),
                    superInterfaces = emptyList(),
                    documentation = null,
                    location = LOCATION,
                    focusedRecursion = false,
                    propIds = listOf(DTO_PROP_ID),
                    hiddenFlatPropIds = emptyList(),
                    polymorphism = null,
                )
            ),
            props = listOf(prop),
        )
        val immutableProp = ImmutableProp(
            id = IMMUTABLE_PROP_ID,
            declarationId = IMMUTABLE_PROP_ID,
            ownerTypeId = IMMUTABLE_TYPE_ID,
            declaringTypeId = IMMUTABLE_TYPE_ID,
            name = "gender",
            documentation = null,
            type = LsiDeclaredType(ENUM_TYPE_ID),
            annotations = emptyList(),
            overrideChain = listOf(IMMUTABLE_PROP_ID),
            inherited = false,
            overridden = false,
            nullable = nullable,
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
        val immutableType = ImmutableType(
            id = IMMUTABLE_TYPE_ID,
            qualifiedName = IMMUTABLE_TYPE_ID.requireTypeQualifiedName(),
            kind = ImmutableTypeKind.MAPPED_SUPERCLASS,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = emptyList(),
            props = listOf(immutableProp),
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
        return Fixture(prop, graph, ImmutableSchema(listOf(immutableType)))
    }

    private data class Fixture(
        val prop: DtoBaseProp,
        val graph: DtoGraph,
        val schema: ImmutableSchema,
    )

    private companion object {
        val SOURCE = LsiSource.of("src/main/dto/demo/Gender.dto", LsiLanguage.UNKNOWN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1), LsiPosition(1, 1))
        val IMMUTABLE_TYPE_ID = LsiSymbolId.type("demo.Person")
        val IMMUTABLE_PROP_ID = LsiSymbolId.property(IMMUTABLE_TYPE_ID, "gender")
        val ENUM_TYPE_ID = LsiSymbolId.type("demo.Gender")
        val DTO_TYPE_ID = DtoTypeId("demo.dto.GenderView#root")
        val DTO_PROP_ID = DtoPropId("demo.dto.GenderView#prop:gender")
        val WORKSPACE = LsiWorkspace(
            sources = listOf(SOURCE),
            declarations = listOf(
                LsiTypeDeclaration(
                    id = ENUM_TYPE_ID,
                    name = "Gender",
                    qualifiedName = "demo.Gender",
                    kind = LsiTypeDeclarationKind.ENUM,
                    origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
                )
            ),
        )
    }
}
