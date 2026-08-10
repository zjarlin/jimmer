package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import org.babyfish.jimmer.compiler.render.apt.AptDtoBaseValueRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoBaseValueRenderer
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
import site.addzero.lsi.jimmer.dto.DtoGraph
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class DtoBaseValuePoetTest {

    @Test
    fun `renders direct nullable base value for both target languages`() {
        val fixture = fixture(dtoNullable = true, baseNullable = true, immutableNullable = true)

        assertEquals(
            "((org.babyfish.jimmer.runtime.ImmutableSpi)base).__isLoaded(" +
                "org.babyfish.jimmer.meta.PropId.byIndex(demo.BookDraft.Producer.SLOT_VALUE)) ? " +
                "base.getValue() : null",
            fixture.renderJava(),
        )
        assertEquals("base.`value`", fixture.renderKotlin())
    }

    @Test
    fun `renders accessor and non-null base guard for both target languages`() {
        val fixture = fixture(
            dtoNullable = false,
            baseNullable = true,
            immutableNullable = true,
            accessor = true,
        )

        assertEquals(
            """
                VALUE_ACCESSOR.get(
                  base,
                  "Cannot convert \"demo.Book\" to \"demo.dto.BookView\" because the cannot get non-null value for \"value\""
                )
            """.trimIndent(),
            fixture.renderJava(),
        )
        assertEquals(
            """
                VALUE_ACCESSOR.get<kotlin.String>(
                  base,
                  "Cannot convert \"demo.Book\" to \"demo.dto.BookView\" because the cannot get non-null value for \"value\""
                )
            """.trimIndent(),
            fixture.renderKotlin(),
        )
    }

    private data class Fixture(
        val prop: DtoBaseProp,
        val graph: DtoGraph,
        val schema: ImmutableSchema,
    ) {
        fun renderJava(): String {
            return AptDtoBaseValueRenderer.render(
                prop = prop,
                graph = graph,
                immutableSchema = schema,
                workspace = WORKSPACE,
                accessorName = "VALUE_ACCESSOR",
                baseParameterName = "base",
                baseValueAccessorName = "getValue",
                baseType = schema.typesById.getValue(IMMUTABLE_TYPE_ID),
                baseSlotName = "SLOT_VALUE",
                conversionErrorMessage = ERROR_MESSAGE,
                generatedTargetType = { LsiDeclaredType(STRING_TYPE_ID) },
                generatedTypeNames = emptyList(),
            ).toString()
        }

        fun renderKotlin(): String {
            return KspDtoBaseValueRenderer.render(
                prop = prop,
                graph = graph,
                immutableSchema = schema,
                workspace = LsiWorkspace.EMPTY,
                accessorName = "VALUE_ACCESSOR",
                baseParameterName = "base",
                conversionErrorMessage = ERROR_MESSAGE,
                generatedTargetType = { LsiDeclaredType(STRING_TYPE_ID) },
                generatedTypeNames = emptyList(),
            ).toString()
        }
    }

    private companion object {
        val SOURCE = LsiSource.of("src/main/dto/demo/BookView.dto", LsiLanguage.UNKNOWN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val IMMUTABLE_TYPE_ID = LsiSymbolId.type("demo.Book")
        val IMMUTABLE_PROP_ID = LsiSymbolId.property(IMMUTABLE_TYPE_ID, "value")
        val DTO_TYPE_ID = DtoTypeId("demo.dto.BookView#root")
        val DTO_PROP_ID = DtoPropId("demo.dto.BookView#prop:value")
        val STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")
        val WORKSPACE = LsiWorkspace(
            declarations = listOf(
                LsiTypeDeclaration(
                    id = IMMUTABLE_TYPE_ID,
                    name = "Book",
                    qualifiedName = "demo.Book",
                    kind = LsiTypeDeclarationKind.INTERFACE,
                    origin = LsiOrigin(
                        kind = LsiOriginKind.SYNTHETIC,
                        language = LsiLanguage.JAVA,
                    ),
                ),
            ),
        )
        const val ERROR_MESSAGE =
            "Cannot convert \"demo.Book\" to \"demo.dto.BookView\" because the cannot get non-null value for \"value\""

        fun fixture(
            dtoNullable: Boolean,
            baseNullable: Boolean,
            immutableNullable: Boolean,
            accessor: Boolean = false,
        ): Fixture {
            val immutableProp = ImmutableProp(
                id = IMMUTABLE_PROP_ID,
                declarationId = IMMUTABLE_PROP_ID,
                ownerTypeId = IMMUTABLE_TYPE_ID,
                declaringTypeId = IMMUTABLE_TYPE_ID,
                name = "value",
                documentation = null,
                type = LsiDeclaredType(STRING_TYPE_ID),
                annotations = emptyList(),
                overrideChain = emptyList(),
                inherited = false,
                overridden = false,
                nullable = immutableNullable,
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
                qualifiedName = "demo.Book",
                kind = ImmutableTypeKind.IMMUTABLE,
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
            val tailPropId = DtoPropId("demo.dto.BookView#hidden:valueTail")
            val prop = DtoBaseProp(
                id = DTO_PROP_ID,
                ownerTypeId = DTO_TYPE_ID,
                name = "value",
                alias = null,
                nullable = dtoNullable,
                annotations = emptyList(),
                documentation = null,
                aliasLocation = LOCATION,
                baseLocation = LOCATION,
                baseProps = listOf(DtoBasePropBinding("value", IMMUTABLE_PROP_ID)),
                basePath = "value",
                nextPropId = tailPropId.takeIf { accessor },
                tailPropId = if (accessor) tailPropId else DTO_PROP_ID,
                baseNullable = baseNullable,
                inputModifier = DtoModifier.STATIC,
                functionName = null,
                targetTypeId = null,
                enumType = null,
                config = null,
                recursive = false,
                likeOptions = emptySet(),
            )
            val tailProp = if (accessor) {
                prop.copy(
                    id = tailPropId,
                    name = "valueTail",
                    nextPropId = null,
                    tailPropId = tailPropId,
                )
            } else {
                null
            }
            val type = DtoType(
                id = DTO_TYPE_ID,
                baseTypeId = IMMUTABLE_TYPE_ID,
                packageName = "demo.dto",
                name = "BookView",
                modifiers = setOf(DtoModifier.INPUT),
                annotations = emptyList(),
                superInterfaces = emptyList(),
                documentation = null,
                location = LOCATION,
                focusedRecursion = false,
                propIds = listOf(DTO_PROP_ID),
                hiddenFlatPropIds = listOfNotNull(tailProp?.id),
                polymorphism = null,
            )
            return Fixture(
                prop = prop,
                graph = DtoGraph(
                    source = SOURCE,
                    rootTypeIds = listOf(DTO_TYPE_ID),
                    types = listOf(type),
                    props = listOfNotNull(tailProp, prop).sortedBy { it.id },
                ),
                schema = ImmutableSchema(listOf(immutableType)),
            )
        }
    }
}
