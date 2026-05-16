package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.codegen.DRAFT_CONSUMER_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JacksonTypes
import site.addzero.lsi.codegen.KOTLIN_UNIT_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PROP_ID_LSI_CLASS_NAME
import site.addzero.lsi.codegen.PRODUCER
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableBuilderSetterMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableBuilderTypeMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableCallbackMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableImplementorDeepPropIdMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableImplementorPropCaseMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableImplementorTypeMetadata
import site.addzero.lsi.poet.LsiBinaryExpression
import site.addzero.lsi.poet.LsiBinaryOperator
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCastExpression
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiCodeExpression
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiSafeCastExpression
import site.addzero.lsi.poet.LsiSuperExpression
import site.addzero.lsi.poet.LsiThrowStatement
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiWhenStatement

class ImmutableGeneratorStructureCleanupTest {

    @Test
    fun `draft block metadata reuses shared callback metadata`() {
        val bookDraftClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft")

        val blockMetadata = draftCallbackMetadata(bookDraftClassName)

        assertEquals(
            ImmutableCallbackMetadata(
                receiverTypeName = bookDraftClassName,
                returnTypeName = KOTLIN_UNIT_LSI_CLASS_NAME,
            ),
            blockMetadata,
        )
        assertEquals(
            DRAFT_CONSUMER_LSI_CLASS_NAME.parameterizedBy(bookDraftClassName),
            blockMetadata.toLsiDraftConsumerTypeName(),
        )
    }

    @Test
    fun `draft dsl helpers use structured producer calls`() {
        val dslArtifact = draftGeneratorWithDslHelpers().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)[1]
        val callables = dslArtifact.topLevelCallables.associateBy { it.name }

        val addBy = callables.getValue("addBy")
        val addCall = (addBy.statements[0] as LsiExpressionStatement).expression as LsiCallExpression
        val addDraft = addCall.arguments.single() as LsiCastExpression
        assertFalse(addDraft.expression is LsiCodeExpression)
        val addProduce = addDraft.expression as LsiCallExpression
        assertEquals(LsiTypeExpression(ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer")), addProduce.receiver)
        assertEquals("produce", addProduce.name)
        assertEquals(
            listOf(
                LsiNameExpression("base"),
                LsiNameExpression("resolveImmediately"),
                LsiNameExpression("block"),
            ),
            addProduce.arguments,
        )

        val newBy = callables.getValue("newBy")
        val newExpression = (newBy.statements.single() as LsiReturnStatement).expression
        assertFalse(newExpression is LsiCodeExpression)
        val newProduce = newExpression as LsiCallExpression
        assertEquals(LsiTypeExpression(ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer")), newProduce.receiver)
        assertEquals("produce", newProduce.name)
        assertEquals(
            listOf(
                LsiNameExpression("base"),
                LsiNameExpression("resolveImmediately"),
                LsiNameExpression("block"),
            ),
            newProduce.arguments,
        )

        val copy = callables.getValue("copy")
        val copyExpression = (copy.statements.single() as LsiReturnStatement).expression
        assertFalse(copyExpression is LsiCodeExpression)
        val copyProduce = copyExpression as LsiCallExpression
        assertEquals(
            LsiPropertyAccessExpression(
                receiver = LsiTypeExpression(ImmutableGeneratorTestFixtures.className("test.model.BookDraft")),
                name = PRODUCER,
            ),
            copyProduce.receiver,
        )
        assertEquals(
            listOf(
                site.addzero.lsi.poet.LsiThisExpression,
                LsiNameExpression("resolveImmediately"),
                LsiNameExpression("block"),
            ),
            copyProduce.arguments,
        )
    }

    @Test
    fun `builder non-null setter uses binary guard instead of raw code expression`() {
        val builder = BuilderGenerator(
            ImmutableBuilderTypeMetadata(
                className = ImmutableGeneratorTestFixtures.className("test.model.Book"),
                producerClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer"),
                draftImplClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.DraftImpl"),
                visibleSlotNames = listOf("STORE"),
                setters = listOf(
                    ImmutableBuilderSetterMetadata(
                        name = "store",
                        parameterLsiTypeName = ImmutableGeneratorTestFixtures.className("test.model.Store"),
                        returnTypeName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Builder"),
                        ownerProducerClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer"),
                        slotName = "STORE",
                        isNullable = false,
                        lsiAnnotations = emptyList(),
                    )
                ),
            )
        ).generate()

        val setter = builder.callables.single { it.name == "store" }
        val guard = setter.statements[0] as LsiIfStatement
        assertEquals(
            LsiBinaryExpression(
                left = LsiNameExpression("store"),
                operator = LsiBinaryOperator.NOT_EQUALS,
                right = LsiNullExpression,
            ),
            guard.condition,
        )
        assertFalse(guard.condition is LsiCodeExpression)
    }

    @Test
    fun `implementor generator uses structured property access chain`() {
        val implementor = ImplementorGenerator(
            jacksonTypes = ImmutableGeneratorTestFixtures.jacksonTypes(),
            type = ImmutableImplementorTypeMetadata(
                className = ImmutableGeneratorTestFixtures.className("test.model.Book"),
                producerClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer"),
                typeDescription = "test.model.Book",
                propertyOrderNames = listOf("id", "name"),
                getCases = listOf(
                    ImmutableImplementorPropCaseMetadata(name = "id", slotName = "ID"),
                    ImmutableImplementorPropCaseMetadata(name = "name", slotName = "NAME"),
                ),
                deeperPropIds = listOf(
                    ImmutableImplementorDeepPropIdMetadata(
                        constantName = "STORE_ID",
                        propName = "store",
                    )
                ),
            ),
        ).generate()

        val typeCallable = implementor.callables.single { it.name == "__type" }
        assertEquals(
            LsiPropertyAccessExpression(
                receiver = LsiTypeExpression(ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer")),
                name = "type",
            ),
            (typeCallable.statements.single() as LsiReturnStatement).expression,
        )

        val deeperProp = implementor.properties.single { it.name == "STORE_ID" }
        val getIdCall = deeperProp.initializer as LsiCallExpression
        assertEquals("getId", getIdCall.name)
        val deeperBaseCall = getIdCall.receiver as LsiCallExpression
        assertEquals("getManyToManyViewBaseDeeperProp", deeperBaseCall.name)
        val getPropCall = deeperBaseCall.receiver as LsiCallExpression
        assertEquals("getProp", getPropCall.name)
        assertEquals(
            LsiPropertyAccessExpression(
                receiver = LsiTypeExpression(ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer")),
                name = "type",
            ),
            getPropCall.receiver,
        )
        assertEquals(listOf(LsiLiteralExpression("store")), getPropCall.arguments)

        val indexedGet = implementor.callables.single {
            it.name == "__get" && it.parameters.single().type == PROP_ID_LSI_CLASS_NAME
        }
        val whenStatement = indexedGet.statements.single() as LsiWhenStatement
        val defaultThrow = whenStatement.elseStatements.single() as LsiThrowStatement
        val message = (defaultThrow.expression as site.addzero.lsi.poet.LsiNewExpression).arguments.single()
        assertTrue(message is LsiBinaryExpression)
        assertFalse(message is LsiCodeExpression)
    }

    @Test
    fun `impl generator uses structured defaults and receiver expressions`() {
        val impl = ImplGenerator(
            jacksonTypes = ImmutableGeneratorTestFixtures.jacksonTypes(),
            type = ImmutableImplTypeMetadata(
                className = ImmutableGeneratorTestFixtures.className("test.model.Book"),
                implementorClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Implementor"),
                draftProducerImplClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Impl"),
                draftProducerImplementorClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Implementor"),
                propsSize = 0,
                typeDescription = "test.model.Book",
                fieldProps = listOf(
                    ImmutableImplFieldMetadata(
                        valueFieldName = "__editionValue",
                        valueFieldTypeName = ImmutableGeneratorTestFixtures.className("kotlin.Int"),
                        valueFieldDefaultValueKind = ImmutableImplDefaultValueKind.PRIMITIVE_DEFAULT,
                        valueFieldDefaultValueTypeName = ImmutableGeneratorTestFixtures.className("kotlin.Int"),
                        loadedFieldName = null,
                    )
                ),
                getterProps = emptyList(),
                stateProps = listOf(
                    ImmutableImplStatePropMetadata(
                        name = "edition",
                        typeName = ImmutableGeneratorTestFixtures.className("kotlin.Int"),
                        slotName = "EDITION",
                        valueFieldName = "__editionValue",
                        loadedFieldName = null,
                        isNullable = false,
                        isAssociation = false,
                        isId = false,
                        loadKind = ImmutableImplLoadKind.STANDARD,
                    )
                ),
                hiddenSlotNames = emptyList(),
            ),
        ).generate()

        val editionField = impl.properties.single { it.name == "__editionValue" }
        assertEquals(LsiLiteralExpression(0), editionField.initializer)

        val cloneCallable = impl.callables.single { it.name == "clone" }
        val cloneInit =
            (cloneCallable.statements[0] as site.addzero.lsi.poet.LsiVariableDeclarationStatement).initializer
                as LsiCastExpression
        assertEquals(
            LsiCallExpression(
                receiver = LsiSuperExpression,
                name = "clone",
            ),
            cloneInit.expression,
        )

        val isVisibleByName =
            impl.callables.single {
                it.name == "__isVisible" && it.parameters.single().type == ImmutableGeneratorTestFixtures.className("kotlin.String")
            }
        val visibilityGuard = isVisibleByName.statements[1] as site.addzero.lsi.poet.LsiIfStatement
        assertEquals(
            LsiBinaryExpression(
                left = LsiNameExpression("__visibility"),
                operator = LsiBinaryOperator.EQUALS,
                right = LsiNullExpression,
            ),
            visibilityGuard.condition,
        )

        val equalsCallable = impl.callables.single { it.name == "equals" }
        val otherInit =
            (equalsCallable.statements[0] as site.addzero.lsi.poet.LsiVariableDeclarationStatement).initializer
                as LsiSafeCastExpression
        assertEquals(LsiNameExpression("other"), otherInit.expression)
    }

    @Test
    fun `impl generator resolves null default from semantic metadata`() {
        val impl = ImplGenerator(
            jacksonTypes = ImmutableGeneratorTestFixtures.jacksonTypes(),
            type = ImmutableImplTypeMetadata(
                className = ImmutableGeneratorTestFixtures.className("test.model.Book"),
                implementorClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Implementor"),
                draftProducerImplClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Impl"),
                draftProducerImplementorClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.Implementor"),
                propsSize = 0,
                typeDescription = "test.model.Book",
                fieldProps = listOf(
                    ImmutableImplFieldMetadata(
                        valueFieldName = "__storeValue",
                        valueFieldTypeName = ImmutableGeneratorTestFixtures.className("test.model.Store").copyNullable(true),
                        valueFieldDefaultValueKind = ImmutableImplDefaultValueKind.NULL,
                        loadedFieldName = null,
                    )
                ),
                getterProps = emptyList(),
                stateProps = listOf(
                    ImmutableImplStatePropMetadata(
                        name = "store",
                        typeName = ImmutableGeneratorTestFixtures.className("test.model.Store").copyNullable(true),
                        slotName = "STORE",
                        valueFieldName = "__storeValue",
                        loadedFieldName = null,
                        isNullable = true,
                        isAssociation = true,
                        isId = false,
                        loadKind = ImmutableImplLoadKind.STANDARD,
                    )
                ),
                hiddenSlotNames = emptyList(),
            ),
        ).generate()

        val storeField = impl.properties.single { it.name == "__storeValue" }
        assertEquals(LsiNullExpression, storeField.initializer)
    }

    private fun draftGeneratorWithDslHelpers(): DraftGenerator {
        val bookClassName = ImmutableGeneratorTestFixtures.className("test.model.Book")
        val bookDraftClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft")
        val producerClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer")
        val blockMetadata = draftCallbackMetadata(bookDraftClassName)
        return DraftGenerator(
            jacksonTypes = ImmutableGeneratorTestFixtures.jacksonTypes(),
            sourcePackageName = "test.model",
            sourceFileName = "Book",
            modelTypes = listOf(
                ImmutableDraftTypeMetadata(
                    simpleName = "Book",
                    className = bookClassName,
                    draftClassName = bookDraftClassName,
                    superDraftClassNames = emptyList(),
                    declaredProps = emptyList(),
                    producerTypeMetadata =
                        ImmutableProducerTypeMetadata(
                            className = bookClassName,
                            draftClassName = bookDraftClassName,
                            draftImplClassName = ImmutableGeneratorTestFixtures.className("test.model.BookDraft.Producer.DraftImpl"),
                            draftCallbackMetadata = blockMetadata,
                            isMappedSuperclass = true,
                            superProducerClassNames = emptyList(),
                            redefinedProps = emptyList(),
                            declaredProps = emptyList(),
                            slots = emptyList(),
                            implementorTypeMetadata = null,
                            implTypeMetadata = null,
                            draftImplTypeMetadata = null,
                        ),
                    builderTypeMetadata = null,
                    addFunMetadatas = listOf(
                        ImmutableDraftAddFunMetadata(
                            annotationClassName = bookClassName,
                            receiverTypeName = ImmutableGeneratorTestFixtures.className("test.model.MutableBooks"),
                            baseParameterTypeName = bookClassName,
                            blockMetadata = blockMetadata,
                            returnTypeName = ImmutableGeneratorTestFixtures.className("test.model.MutableBooks"),
                            producerClassName = producerClassName,
                            draftClassName = bookDraftClassName,
                        )
                    ),
                    newFunMetadatas = listOf(
                        ImmutableDraftNewFunMetadata(
                            name = "newBy",
                            annotationClassName = bookClassName,
                            receiverTypeName = null,
                            baseParameterTypeName = bookClassName,
                            blockMetadata = blockMetadata,
                            returnTypeName = bookClassName,
                            producerClassName = producerClassName,
                        )
                    ),
                    copyFunMetadata =
                        ImmutableDraftCopyFunMetadata(
                            annotationClassName = bookClassName,
                            receiverTypeName = bookClassName,
                            blockMetadata = blockMetadata,
                            returnTypeName = bookClassName,
                            draftClassName = bookDraftClassName,
                        ),
                )
            ),
            currentVersionValue = "test-version",
        )
    }
}
