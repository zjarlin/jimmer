package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.codegen.ABSTRACT_TYPED_FETCHER_LSI_CLASS_NAME
import site.addzero.lsi.codegen.CONSUMER_LSI_CLASS_NAME
import site.addzero.lsi.codegen.JAVA_FIELD_CONFIG_UTILS_LSI_CLASS_NAME
import site.addzero.lsi.codegen.REFERENCE_FIELD_CONFIG_LSI_CLASS_NAME
import site.addzero.lsi.poet.LsiAssignmentStatement
import site.addzero.lsi.poet.LsiBinaryExpression
import site.addzero.lsi.poet.LsiBinaryOperator
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiExpressionStatement
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiLambdaExpression
import site.addzero.lsi.poet.LsiLambdaMode
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiParameterSpec
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiVariableDeclarationStatement
import site.addzero.lsi.poet.LsiWildcardTypeName

class FetcherGeneratorStructureTest {

    @Test
    fun `fetcher core follows old apt chain model with lsi types`() {
        val coreType = generator().generate(mode = ImmutableGenerationMode.KOTLIN_FULL).first().types.single()
        val fetcherClassName = ImmutableGeneratorTestFixtures.className("test.model.BookFetcher")

        assertEquals(
            ABSTRACT_TYPED_FETCHER_LSI_CLASS_NAME.parameterizedBy(
                ImmutableGeneratorTestFixtures.className("test.model.Book"),
                fetcherClassName,
            ),
            coreType.superClass,
        )

        val rootFetcher = coreType.properties.single { it.name == "\$" }
        assertEquals(
            LsiPropertySpec(
                name = "\$",
                type = fetcherClassName,
                modifiers = setOf(site.addzero.lsi.poet.LsiModifier.PUBLIC, site.addzero.lsi.poet.LsiModifier.STATIC),
                initializer = LsiNewExpression(
                    type = fetcherClassName,
                    arguments = listOf(LsiNullExpression),
                ),
            ),
            rootFetcher,
        )

        val fromFactory = coreType.callables.single { it.name == "\$from" }
        val typedDeclaration = fromFactory.statements[0] as LsiVariableDeclarationStatement
        assertEquals("typed", typedDeclaration.name)
        val referenceFetchType = coreType.callables.single {
            it.name == "store" && it.parameters.map(LsiParameterSpec::name) == listOf("fetchType", "childFetcher")
        }
        val delegatedCall = (referenceFetchType.statements.single() as LsiReturnStatement).expression as LsiCallExpression
        assertEquals("store", delegatedCall.name)
        val cfgLambda = assertInstanceOf(LsiLambdaExpression::class.java, delegatedCall.arguments[1])
        assertEquals(LsiLambdaMode.BLOCK, cfgLambda.mode)

        val configuredStore = coreType.callables.single {
            it.name == "store" && it.parameters.map(LsiParameterSpec::name) == listOf("childFetcher", "fieldConfig")
        }
        assertEquals(
            CONSUMER_LSI_CLASS_NAME.parameterizedBy(
                LsiWildcardTypeName(
                    producerTypes = listOf(
                        REFERENCE_FIELD_CONFIG_LSI_CLASS_NAME.parameterizedBy(
                            ImmutableGeneratorTestFixtures.className("test.model.Store"),
                            ImmutableGeneratorTestFixtures.className("test.model.StoreTable"),
                        )
                    )
                )
            ),
            configuredStore.parameters[1].type,
        )

        val recursiveStore = coreType.callables.single { it.name == "recursiveStore" && it.parameters.isEmpty() }
        val recursiveReturn = recursiveStore.statements.single() as LsiReturnStatement
        val recursiveCall = recursiveReturn.expression as LsiCallExpression
        assertEquals("addRecursion", recursiveCall.name)
        assertEquals(LsiLiteralExpression("store"), recursiveCall.arguments[0])
        assertEquals(LsiNullExpression, recursiveCall.arguments[1])
        assertTrue(typedDeclaration.initializer is site.addzero.lsi.poet.LsiSafeCastExpression)
    }

    @Test
    fun `fetcher by with base uses core fetcher root assignment flow`() {
        val dslArtifact = generator().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)[1]
        val withBase = dslArtifact.topLevelCallables.single { it.parameters.size == 2 }

        val dslDeclaration = withBase.statements[0] as LsiVariableDeclarationStatement
        assertTrue(dslDeclaration.mutable)
        assertEquals(
            LsiNewExpression(
                type = ImmutableGeneratorTestFixtures.className("test.model.BookFetcherDsl"),
                arguments = listOf(
                    LsiPropertyAccessExpression(
                        receiver = LsiTypeExpression(ImmutableGeneratorTestFixtures.className("test.model.BookFetcher")),
                        name = "\$",
                    )
                ),
            ),
            dslDeclaration.initializer,
        )

        val baseGuard = withBase.statements[1] as LsiIfStatement
        assertEquals(
            LsiBinaryExpression(
                left = LsiNameExpression("base"),
                operator = LsiBinaryOperator.NOT_EQUALS,
                right = LsiNullExpression,
            ),
            baseGuard.condition,
        )
        val reassign = baseGuard.thenStatements.single() as LsiAssignmentStatement
        assertEquals(LsiNameExpression("dsl"), reassign.target)
        assertEquals(
            LsiNewExpression(
                type = ImmutableGeneratorTestFixtures.className("test.model.BookFetcherDsl"),
                arguments = listOf(LsiNameExpression("base")),
            ),
            reassign.expression,
        )

        val blockInvoke = withBase.statements[2] as LsiExpressionStatement
        assertEquals(
            LsiCallExpression(
                receiver = LsiNameExpression("block"),
                name = "invoke",
                arguments = listOf(LsiNameExpression("dsl")),
            ),
            blockInvoke.expression,
        )

        val returnStatement = withBase.statements[3] as LsiReturnStatement
        assertEquals(
            LsiCallExpression(
                receiver = LsiNameExpression("dsl"),
                name = "internallyGetFetcher",
            ),
            returnStatement.expression,
        )
        assertInstanceOf(LsiPropertyAccessExpression::class.java, (dslDeclaration.initializer as LsiNewExpression).arguments.single())
    }

    @Test
    fun `fetcher dsl methods use structured fetcher updates`() {
        val dslType = generator().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)[1].types.single()

        val deleteFun = dslType.callables.single { it.name == "allScalarFields" }
        assertEquals(
            LsiAssignmentStatement(
                target = LsiNameExpression("_fetcher"),
                expression = LsiCallExpression(
                    receiver = LsiNameExpression("_fetcher"),
                    name = "allScalarFields",
                ),
            ),
            deleteFun.statements.single(),
        )

        val simpleStore = dslType.callables.single {
            it.name == "store" && it.parameters.singleOrNull()?.name == "enabled"
        }
        val simpleIf = simpleStore.statements.single() as LsiIfStatement
        assertEquals(LsiNameExpression("enabled"), simpleIf.condition)
        assertEquals(
            LsiAssignmentStatement(
                target = LsiNameExpression("_fetcher"),
                expression = LsiCallExpression(
                    receiver = LsiNameExpression("_fetcher"),
                    name = "add",
                    arguments = listOf(LsiLiteralExpression("store")),
                ),
            ),
            simpleIf.thenStatements.single(),
        )
        assertEquals(
            LsiAssignmentStatement(
                target = LsiNameExpression("_fetcher"),
                expression = LsiCallExpression(
                    receiver = LsiNameExpression("_fetcher"),
                    name = "remove",
                    arguments = listOf(LsiLiteralExpression("store")),
                ),
            ),
            simpleIf.elseStatements.single(),
        )

        val referenceLambda = dslType.callables.single {
            it.name == "store" &&
                it.parameters.size == 2 &&
                it.parameters[0].name == "fetchType" &&
                it.parameters[1].name == "childBlock"
        }
        val childFetcherDeclaration = referenceLambda.statements[2] as LsiVariableDeclarationStatement
        assertEquals("childFetcher", childFetcherDeclaration.name)
        val referenceAssign = referenceLambda.statements[3] as LsiAssignmentStatement
        val addCall = referenceAssign.expression as LsiCallExpression
        assertEquals("add", addCall.name)
        assertEquals(LsiNameExpression("_fetcher"), addCall.receiver)
        assertEquals(LsiLiteralExpression("store"), addCall.arguments[0])
        assertEquals(LsiNameExpression("childFetcher"), addCall.arguments[1])
        assertEquals(
            LsiCallExpression(
                receiver = LsiTypeExpression(JAVA_FIELD_CONFIG_UTILS_LSI_CLASS_NAME),
                name = "reference",
                typeArguments = listOf(ImmutableGeneratorTestFixtures.className("test.model.Store")),
                arguments = listOf(LsiNameExpression("fetchType")),
            ),
            addCall.arguments[2],
        )

        val recursiveNoConfig = dslType.callables.single {
            it.name == "store*" && it.parameters.isEmpty()
        }
        assertEquals(
            LsiAssignmentStatement(
                target = LsiNameExpression("_fetcher"),
                expression = LsiCallExpression(
                    receiver = LsiNameExpression("_fetcher"),
                    name = "addRecursion",
                    arguments = listOf(
                        LsiLiteralExpression("store"),
                        LsiNullExpression,
                    ),
                ),
            ),
            recursiveNoConfig.statements.single(),
        )
    }

    private fun generator(): FetcherGenerator =
        FetcherGenerator(
            sourcePackageName = ImmutableGeneratorTestFixtures.SOURCE_PACKAGE_NAME,
            sourceFileName = ImmutableGeneratorTestFixtures.SOURCE_FILE_NAME,
            type = ImmutableGeneratorTestFixtures.bookFetcherReferenceMetadata(),
        )
}
