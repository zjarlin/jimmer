package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import site.addzero.lsi.codegen.LsiClassName
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsTypeMetadata
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCodeExpression
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNewExpression
import site.addzero.lsi.poet.LsiPropertyAccessExpression
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiTypeExpression

class EmbeddedPropExpressionGeneratorStructureTest {

    @Test
    fun `embedded prop expression uses structured get and unwrap calls`() {
        val generatedType = EmbeddedPropExpressionGenerator(addressMetadata()).generate().types.single()

        val city = generatedType.callables.single { it.name == "city" }
        val cityReturn = city.statements.single() as LsiReturnStatement
        assertEquals(
            LsiCallExpression(
                name = "__get",
                arguments = listOf(propUnwrapExpression("test.model.AddressProps", "CITY")),
            ),
            cityReturn.expression,
        )
        assertFalse(cityReturn.expression is LsiCodeExpression)

        val geo = generatedType.callables.single { it.name == "geo" }
        val geoReturn = geo.statements.single() as LsiReturnStatement
        val geoNew = geoReturn.expression as LsiNewExpression
        assertEquals(ImmutableGeneratorTestFixtures.className("test.model.GeoPropExpression"), geoNew.type)
        assertEquals(
            LsiCallExpression(
                name = "__get",
                arguments = listOf(propUnwrapExpression("test.model.AddressProps", "GEO")),
            ),
            geoNew.arguments.single(),
        )
    }

    private fun addressMetadata(): ImmutablePropsTypeMetadata =
        ImmutableGeneratorTestFixtures.addressPropsMetadata()

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
