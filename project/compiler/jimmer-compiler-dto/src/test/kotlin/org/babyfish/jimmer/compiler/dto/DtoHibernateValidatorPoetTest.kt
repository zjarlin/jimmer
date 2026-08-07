package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import org.babyfish.jimmer.compiler.render.apt.AptDtoHibernateValidatorRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoHibernateValidatorRenderer
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
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoBasePropBinding
import site.addzero.lsi.jimmer.dto.DtoFoldProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoTypeRef
import site.addzero.lsi.jimmer.dto.DtoUserProp
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace

class DtoHibernateValidatorPoetTest {

    @Test
    fun `renders exact APT enhancement methods through shared workspace`() {
        val fixture = fixture()
        val methods = AptDtoHibernateValidatorRenderer.renderFunctions(
            dtoType = fixture.type,
            graph = fixture.graph,
            immutableSchema = fixture.schema,
            workspace = WORKSPACE,
        )

        assertEquals(
            "org.hibernate.validator.engine.HibernateValidatorEnhancedBean",
            AptDtoHibernateValidatorRenderer.renderEnhancedBeanType(WORKSPACE).toString(),
        )
        assertEquals(
            listOf(
                """
                    @java.lang.Override
                    public java.lang.Object ${'$'}${'$'}_hibernateValidator_getFieldValue(java.lang.String name) {
                      switch (name) {
                        case "name": return this.name;
                        case "active": return this.active;
                        case "isEnabled": return this.isEnabled;
                        case "is1": return this.is1;
                        case "isDisplayName": return this.isDisplayName;
                        case "foldValue": return this.foldValue;
                        default: throw new IllegalArgumentException("No field named \"" + name + "\"");
                      }
                    }
                """.trimIndent(),
                """
                    @java.lang.Override
                    public java.lang.Object ${'$'}${'$'}_hibernateValidator_getGetterValue(java.lang.String name) {
                      switch (name) {
                        case "getName": return this.name;
                        case "isActive": return this.active;
                        case "getIsEnabled": return this.isEnabled;
                        case "getIs1": return this.is1;
                        case "getIsDisplayName": return this.isDisplayName;
                        case "getFoldValue": return this.foldValue;
                        default: throw new IllegalArgumentException("No getter named \"" + name + "\"");
                      }
                    }
                """.trimIndent(),
            ),
            methods.map { method -> method.toString().trimEnd() },
        )
    }

    @Test
    fun `renders exact KSP enhancement methods through shared workspace`() {
        val fixture = fixture()
        val methods = KspDtoHibernateValidatorRenderer.renderFunctions(
            dtoType = fixture.type,
            graph = fixture.graph,
            immutableSchema = fixture.schema,
            workspace = WORKSPACE,
        )

        assertEquals(
            "org.hibernate.validator.engine.HibernateValidatorEnhancedBean",
            KspDtoHibernateValidatorRenderer.renderEnhancedBeanType(WORKSPACE).toString(),
        )
        assertEquals(
            listOf(
                """
                    override fun `${'$'}${'$'}_hibernateValidator_getFieldValue`(name: kotlin.String): kotlin.Any? = when(name) {
                      "name" -> this.name
                      "active" -> this.active
                      "isEnabled" -> this.isEnabled
                      "is1" -> this.is1
                      "isDisplayName" -> this.isDisplayName
                      "foldValue" -> this.foldValue
                      else -> throw IllegalArgumentException("No field named \"${'$'}{name}\"")
                    }
                """.trimIndent(),
                """
                    override fun `${'$'}${'$'}_hibernateValidator_getGetterValue`(name: kotlin.String): kotlin.Any? = when(name) {
                      "getName" -> this.name
                      "getActive" -> this.active
                      "isEnabled" -> this.isEnabled
                      "is1" -> this.is1
                      "isDisplayName" -> this.isDisplayName
                      "getFoldValue" -> this.foldValue
                      else -> throw IllegalArgumentException("No getter named \"${'$'}{name}\"")
                    }
                """.trimIndent(),
            ),
            methods.map { method -> method.toString().trimEnd() },
        )
    }

    private fun fixture(): Fixture {
        val props = listOf(
            baseProp(),
            userProp("active", nullable = false),
            userProp("isEnabled", nullable = true),
            userProp("is1", nullable = true),
            userProp("isDisplayName", nullable = true, typeName = "String"),
            foldProp(),
        )
        val type = DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = BASE_TYPE_ID,
            packageName = "demo.dto",
            name = "BookInput",
            modifiers = setOf(DtoModifier.INPUT),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = props.map(DtoProp::id),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        val graph = DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(DTO_TYPE_ID),
            types = listOf(type),
            props = props.sortedBy(DtoProp::id),
        )
        val immutableName = ImmutableProp(
            id = BASE_NAME_PROP_ID,
            declarationId = BASE_NAME_PROP_ID,
            ownerTypeId = BASE_TYPE_ID,
            declaringTypeId = BASE_TYPE_ID,
            name = "name",
            documentation = null,
            type = LsiDeclaredType(STRING_TYPE_ID),
            annotations = emptyList(),
            overrideChain = emptyList(),
            inherited = false,
            overridden = false,
            nullable = true,
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
            id = BASE_TYPE_ID,
            qualifiedName = "demo.Book",
            kind = ImmutableTypeKind.IMMUTABLE,
            documentation = null,
            annotations = emptyList(),
            typeParameterIds = emptyList(),
            superTypeIds = emptyList(),
            props = listOf(immutableName),
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
        return Fixture(type, graph, ImmutableSchema(listOf(immutableType)))
    }

    private fun baseProp(): DtoBaseProp {
        return DtoBaseProp(
            id = NAME_PROP_ID,
            ownerTypeId = DTO_TYPE_ID,
            name = "name",
            alias = "name",
            nullable = true,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(DtoBasePropBinding("name", BASE_NAME_PROP_ID)),
            basePath = "name",
            nextPropId = null,
            tailPropId = NAME_PROP_ID,
            baseNullable = true,
            inputModifier = DtoModifier.DYNAMIC,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
    }

    private fun userProp(
        name: String,
        nullable: Boolean,
        typeName: String = "Boolean",
    ): DtoUserProp {
        return DtoUserProp(
            id = DtoPropId("dto#$name"),
            ownerTypeId = DTO_TYPE_ID,
            name = name,
            alias = name,
            nullable = nullable,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            type = DtoTypeRef(typeName, emptyList(), nullable, LOCATION),
            defaultValueText = null,
        )
    }

    private fun foldProp(): DtoFoldProp {
        return DtoFoldProp(
            id = FOLD_PROP_ID,
            ownerTypeId = DTO_TYPE_ID,
            name = "foldValue",
            alias = "foldValue",
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            nullGuardPropId = null,
            targetTypeId = DTO_TYPE_ID,
        )
    }

    private data class Fixture(
        val type: DtoType,
        val graph: DtoGraph,
        val schema: ImmutableSchema,
    )

    private companion object {
        val SOURCE = LsiSource.of("src/main/dto/demo/Book.dto", LsiLanguage.KOTLIN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1), LsiPosition(1, 1))
        val WORKSPACE = LsiWorkspace(emptyList())
        val DTO_TYPE_ID = DtoTypeId("demo.dto.BookInput")
        val BASE_TYPE_ID = LsiSymbolId.type("demo.Book")
        val STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")
        val BASE_NAME_PROP_ID = LsiSymbolId.property(BASE_TYPE_ID, "name")
        val NAME_PROP_ID = DtoPropId("dto#name")
        val FOLD_PROP_ID = DtoPropId("dto#foldValue")
    }
}
