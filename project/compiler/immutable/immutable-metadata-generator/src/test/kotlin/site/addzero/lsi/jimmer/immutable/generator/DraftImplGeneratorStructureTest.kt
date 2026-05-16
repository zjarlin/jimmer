package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.codegen.JacksonTypes
import site.addzero.lsi.poet.LsiBinaryOperator
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiCollectionElementExpression
import site.addzero.lsi.poet.LsiCollectionSizeExpression
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiForRangeStatement
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiAssignmentStatement
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiMakeIdOnlyExpression
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiParameterizedTypeName
import site.addzero.lsi.poet.LsiPropertySetStatement
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.poet.LsiWhenStatement
import site.addzero.lsi.poet.LsiVariableDeclarationStatement

class DraftImplGeneratorStructureTest {

    @Test
    fun `id-view transform setter is emitted as structured lsi statements`() {
        val property =
            ImmutableDraftImplPropertyMetadata(
                name = "storeIds",
                typeName = listType("java.lang.Long", nullable = true),
                isMutable = true,
                getterKind = ImmutableDraftImplPropertyGetterKind.PASSTHROUGH,
                setterKind = ImmutableDraftImplPropertySetterKind.ID_VIEW_TRANSFORM,
                idViewBaseName = "stores",
                idViewBaseTypeName = listType("test.model.Store"),
                idViewBaseNullable = true,
                idViewBaseList = true,
            )

        val generatedProperty = generateDraftImplProperty(property)

        assertEquals(2, generatedProperty.setterStatements.size)
        assertTrue(generatedProperty.setterStatements[0] is LsiIfStatement)

        val idViewSetter = generatedProperty.setterStatements[1] as LsiIfStatement
        val nullCheck = idViewSetter.condition as site.addzero.lsi.poet.LsiBinaryExpression
        assertEquals(LsiBinaryOperator.EQUALS, nullCheck.operator)
        assertEquals(LsiNameExpression("value"), nullCheck.left)
        assertEquals(LsiNullExpression, nullCheck.right)

        val thenSetter = idViewSetter.thenStatements.single() as LsiPropertySetStatement
        assertEquals("stores", thenSetter.name)
        assertEquals(LsiNullExpression, thenSetter.expression)

        val targetsDeclaration = idViewSetter.elseStatements[0] as LsiVariableDeclarationStatement
        val targetsConstructor = targetsDeclaration.initializer as LsiNewExpression
        val sizeExpression = targetsConstructor.arguments.single() as LsiCollectionSizeExpression
        assertEquals(LsiNameExpression("value"), sizeExpression.receiver)

        val loop = idViewSetter.elseStatements[1] as LsiForRangeStatement
        assertEquals("__idViewIndex", loop.variableName)
        val addCall = (loop.statements.single() as LsiExpressionStatement).expression as LsiCallExpression
        assertEquals("add", addCall.name)
        val makeIdOnly = addCall.arguments.single() as LsiMakeIdOnlyExpression
        assertEquals(LsiClassName.bestGuess("test.model.Store"), makeIdOnly.targetType)
        assertEquals(
            LsiCollectionElementExpression(
                receiver = LsiNameExpression("value"),
                index = LsiNameExpression("__idViewIndex"),
            ),
            makeIdOnly.idExpression,
        )

        val elseSetter = idViewSetter.elseStatements[2] as LsiPropertySetStatement
        assertEquals("stores", elseSetter.name)
        assertEquals(LsiNameExpression("__idViewTargets"), elseSetter.expression)
    }

    @Test
    fun `id-view direct setter is emitted as structured property set`() {
        val property =
            ImmutableDraftImplPropertyMetadata(
                name = "storeId",
                typeName = ImmutableGeneratorTestFixtures.className("java.lang.Long"),
                isMutable = true,
                getterKind = ImmutableDraftImplPropertyGetterKind.PASSTHROUGH,
                setterKind = ImmutableDraftImplPropertySetterKind.ID_VIEW_DIRECT,
                idViewBaseName = "store",
                idViewBaseTypeName = ImmutableGeneratorTestFixtures.className("test.model.Store"),
            )

        val generatedProperty = generateDraftImplProperty(property)

        assertEquals(2, generatedProperty.setterStatements.size)
        val directSetter = generatedProperty.setterStatements[1] as LsiPropertySetStatement
        assertEquals("store", directSetter.name)
        val makeIdOnly = directSetter.expression as LsiMakeIdOnlyExpression
        assertEquals(LsiClassName.bestGuess("test.model.Store"), makeIdOnly.targetType)
        assertEquals(LsiNameExpression("value"), makeIdOnly.idExpression)
    }

    @Test
    fun `modified state is initialized in primary constructor statements`() {
        val generatedType = generateDraftImplType(
            property = ImmutableDraftImplPropertyMetadata(
                name = "name",
                typeName = ImmutableGeneratorTestFixtures.className("kotlin.String"),
                isMutable = false,
                getterKind = ImmutableDraftImplPropertyGetterKind.PASSTHROUGH,
                setterKind = ImmutableDraftImplPropertySetterKind.NONE,
            )
        )

        val modifiedProperty = generatedType.properties.single { it.name == "__modified" }
        assertEquals(LsiNullExpression, modifiedProperty.initializer)

        val constructor = generatedType.callables.single { it.kind == site.addzero.lsi.poet.LsiCallableSpecKind.CONSTRUCTOR && it.primary }
        val initIf = constructor.statements.single() as LsiIfStatement
        val initCondition = initIf.condition as site.addzero.lsi.poet.LsiBinaryExpression
        assertEquals(LsiBinaryOperator.EQUALS, initCondition.operator)
        assertEquals(LsiNameExpression("base"), initCondition.left)
        assertEquals(LsiNullExpression, initCondition.right)

        val thenAssignment = initIf.thenStatements.single() as LsiAssignmentStatement
        assertEquals(LsiNameExpression("__modified"), thenAssignment.target)
        assertEquals(
            LsiNewExpression(ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Impl")),
            thenAssignment.expression,
        )

        val elseAssignment = initIf.elseStatements.single() as LsiAssignmentStatement
        assertEquals(LsiNameExpression("__modified"), elseAssignment.target)
        assertEquals(LsiNullExpression, elseAssignment.expression)
    }

    @Test
    fun `reset loaded unload uses structured default expression from metadata`() {
        val generatedType =
            DraftImplGenerator(
                jacksonTypes = ImmutableGeneratorTestFixtures.testMetaJacksonTypes(),
                type =
                    ImmutableDraftImplTypeMetadata(
                        className = ImmutableGeneratorTestFixtures.className("test.model.Book"),
                        draftProducerImplementorClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Implementor"),
                        draftClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft"),
                        draftProducerImplClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Impl"),
                        members = emptyList(),
                        dispatchType = ImmutableDraftImplDispatchTypeMetadata(
                            propsSize = 1,
                            typeDescription = "test.model.Book",
                            props = listOf(
                                ImmutableDraftImplDispatchPropMetadata(
                                    name = "edition",
                                    slotName = "EDITION",
                                    unloadKind = ImmutableDraftImplUnloadKind.RESET_LOADED,
                                    valueFieldName = "__editionValue",
                                    loadedFieldName = "__editionLoaded",
                                    unloadValueKind = ImmutableDraftImplUnloadValueKind.PRIMITIVE_DEFAULT,
                                    unloadValueTypeName = ImmutableGeneratorTestFixtures.className("kotlin.Int"),
                                    setKind = ImmutableDraftImplSetKind.ASSIGN,
                                    setTypeName = ImmutableGeneratorTestFixtures.className("kotlin.Int"),
                                )
                            ),
                        ),
                        resolveProps = emptyList(),
                        typeValidators = emptyList(),
                        validationProps = emptyList(),
                    ),
            ).generate()

        val unloadByName =
            generatedType.callables.single {
                it.name == "__unload" && it.parameters.single().type == ImmutableGeneratorTestFixtures.className("kotlin.String")
            }
        val whenStatement = unloadByName.statements[1] as LsiWhenStatement
        val editionCase = whenStatement.cases.single { it.conditions.single() == LsiLiteralExpression("edition") }
        val valueReset =
            editionCase.statements
                .filterIsInstance<LsiAssignmentStatement>()
                .single {
                    (it.target as? LsiPropertyAccessExpression)?.name == "__editionValue"
                }
        assertEquals(LsiLiteralExpression(0), valueReset.expression)
    }

    private fun generateDraftImplProperty(
        property: ImmutableDraftImplPropertyMetadata,
    ) = generateDraftImplType(property)
        .properties
        .single { it.name == property.name }

    private fun generateDraftImplType(
        property: ImmutableDraftImplPropertyMetadata?,
    ) = DraftImplGenerator(
        jacksonTypes = ImmutableGeneratorTestFixtures.testMetaJacksonTypes(),
        type =
            ImmutableDraftImplTypeMetadata(
                className = ImmutableGeneratorTestFixtures.className("test.model.Book"),
                draftProducerImplementorClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Implementor"),
                draftClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft"),
                draftProducerImplClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Impl"),
                members = property?.let {
                    listOf(
                        ImmutableDraftImplMemberMetadata(
                            property = it,
                            propFun = null,
                            propRefFun = null,
                            associatedId = null,
                        )
                    )
                } ?: emptyList(),
                dispatchType = ImmutableDraftImplDispatchTypeMetadata(
                    propsSize = if (property == null) 0 else 1,
                    typeDescription = "test.model.Book",
                    props = property?.let {
                        listOf(
                            ImmutableDraftImplDispatchPropMetadata(
                                name = it.name,
                                slotName = "STORE_ID",
                                unloadKind = ImmutableDraftImplUnloadKind.NO_OP,
                                setKind = ImmutableDraftImplSetKind.READ_ONLY,
                            )
                        )
                    } ?: emptyList(),
                ),
                resolveProps = emptyList(),
                typeValidators = emptyList(),
                validationProps = emptyList(),
            ),
    ).generate()

    private fun listType(
        elementQualifiedName: String,
        nullable: Boolean = false,
    ): LsiTypeName =
        LsiParameterizedTypeName(
            rawType = ImmutableGeneratorTestFixtures.className("kotlin.collections.List"),
            typeArguments = listOf(ImmutableGeneratorTestFixtures.className(elementQualifiedName)),
            nullable = nullable,
        )
}
