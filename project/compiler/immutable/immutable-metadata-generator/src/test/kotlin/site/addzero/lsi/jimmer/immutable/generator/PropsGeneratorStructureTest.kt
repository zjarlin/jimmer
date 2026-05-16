package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.codegen.K_NON_NULL_PROPS_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_NON_NULL_TABLE_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_NULLABLE_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
import site.addzero.lsi.codegen.K_PROPS_LSI_CLASS_NAME
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsTypeMetadata
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCastExpression
import site.addzero.lsi.poet.LsiClassLiteralExpression
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiCodeExpression
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiTypeExpression

class PropsGeneratorStructureTest {

    @Test
    fun `scalar and associated id props use structured unwrap calls`() {
        val dslArtifact = entityGenerator().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)[1]

        val scalarId = dslArtifact.topLevelProperties.single {
            it.name == "id" &&
                it.receiverType == K_NON_NULL_PROPS_LSI_CLASS_NAME.parameterizedBy(ImmutableGeneratorTestFixtures.className("test.model.Book"))
        }
        val scalarGetter = scalarId.getterStatements.single() as LsiReturnStatement
        val scalarCast = scalarGetter.expression as LsiCastExpression
        assertEquals(
            LsiCallExpression(
                name = "get",
                typeArguments = listOf(ImmutableGeneratorTestFixtures.className("kotlin.Long")),
                arguments = listOf(propUnwrapExpression("test.model.BookProps", "ID")),
            ),
            scalarCast.expression,
        )
        assertFalse(scalarCast.expression is LsiCodeExpression)

        val associatedId = dslArtifact.topLevelProperties.single {
            it.name == "storeId" &&
                it.receiverType == K_NON_NULL_TABLE_LSI_CLASS_NAME.parameterizedBy(ImmutableGeneratorTestFixtures.className("test.model.Book"))
        }
        val associatedGetter = associatedId.getterStatements.single() as LsiReturnStatement
        val associatedCast = associatedGetter.expression as LsiCastExpression
        assertEquals(
            LsiCallExpression(
                name = "getAssociatedId",
                typeArguments = listOf(ImmutableGeneratorTestFixtures.className("test.model.Store")),
                arguments = listOf(propUnwrapExpression("test.model.BookProps", "STORE")),
            ),
            associatedCast.expression,
        )
        assertFalse(associatedCast.expression is LsiCodeExpression)
    }

    @Test
    fun `lambda and fetchBy helpers use structured lsi call chain`() {
        val dslArtifact = entityGenerator().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)[1]

        val lambdaCallable = dslArtifact.topLevelCallables.single {
            it.name == "stores" &&
                it.receiverType == K_PROPS_LSI_CLASS_NAME.parameterizedBy(ImmutableGeneratorTestFixtures.className("test.model.Book"))
        }
        val existsReturn = lambdaCallable.statements.single() as LsiReturnStatement
        assertEquals(
            LsiCallExpression(
                name = "exists",
                arguments = listOf(
                    propUnwrapExpression("test.model.BookProps", "STORES"),
                    LsiNameExpression("block"),
                ),
            ),
            existsReturn.expression,
        )

        val fetchBy = dslArtifact.topLevelCallables.single {
            it.name == "fetchBy" &&
                it.receiverType == K_NON_NULL_TABLE_LSI_CLASS_NAME.parameterizedBy(ImmutableGeneratorTestFixtures.className("test.model.Book"))
        }
        val fetchReturn = fetchBy.statements.single() as LsiReturnStatement
        val fetchCall = fetchReturn.expression as LsiCallExpression
        assertEquals("fetch", fetchCall.name)
        assertEquals(
            LsiCallExpression(
                receiver = LsiCallExpression(
                    receiver = LsiNameExpression("org.babyfish.jimmer.sql.kt.fetcher"),
                    name = "newFetcher",
                    arguments = listOf(LsiClassLiteralExpression(ImmutableGeneratorTestFixtures.className("test.model.Book"))),
                ),
                name = "by",
                arguments = listOf(LsiNameExpression("block")),
            ),
            fetchCall.arguments.single(),
        )
        assertTrue(dslArtifact.memberImports.isEmpty())
    }

    @Test
    fun `embeddable nullable getter keeps plain structured get call`() {
        val dslArtifact = embeddableGenerator().generate(mode = ImmutableGenerationMode.KOTLIN_FULL)[1]

        val nullableStreet = dslArtifact.topLevelProperties.single {
            it.name == "street" &&
                it.receiverType ==
                K_NULLABLE_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME.parameterizedBy(ImmutableGeneratorTestFixtures.className("test.model.Address"))
        }
        val getter = nullableStreet.getterStatements.single() as LsiReturnStatement
        assertEquals(
            LsiCallExpression(
                name = "get",
                arguments = listOf(propUnwrapExpression("test.model.AddressProps", "STREET")),
            ),
            getter.expression,
        )
        assertFalse(getter.expression is LsiCodeExpression)
    }

    private fun entityGenerator(): PropsGenerator =
        PropsGenerator(
            sourcePackageName = ImmutableGeneratorTestFixtures.SOURCE_PACKAGE_NAME,
            sourceFileName = ImmutableGeneratorTestFixtures.SOURCE_FILE_NAME,
            type = entityMetadata(),
        )

    private fun embeddableGenerator(): PropsGenerator =
        PropsGenerator(
            sourcePackageName = ImmutableGeneratorTestFixtures.SOURCE_PACKAGE_NAME,
            sourceFileName = "Address",
            type = embeddableMetadata(),
        )

    private fun entityMetadata(): ImmutablePropsTypeMetadata =
        ImmutableGeneratorTestFixtures.bookPropsMetadataWithAssociatedId()

    private fun embeddableMetadata(): ImmutablePropsTypeMetadata =
        ImmutableGeneratorTestFixtures.addressPropsMetadata().copy(
            properties = listOf(
                ImmutableGeneratorTestFixtures.addressPropsMetadata().properties.first().copy(
                    name = "street",
                    constantName = "STREET",
                )
            )
        )

    private fun propUnwrapExpression(
        propsClassQualifiedName: String,
        constantName: String,
    ): LsiCallExpression =
        LsiCallExpression(
            receiver = LsiPropertyAccessExpression(
                receiver = LsiTypeExpression(ImmutableGeneratorTestFixtures.className(propsClassQualifiedName)),
                name = constantName,
            ),
            name = "unwrap",
        )
}
