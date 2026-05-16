package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.codegen.JacksonTypes
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableAssociatedIdMetadata
import site.addzero.lsi.poet.LsiBinaryExpression
import site.addzero.lsi.poet.LsiBinaryOperator
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiIfStatement
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiPropertyGetExpression
import site.addzero.lsi.poet.LsiPropertySetStatement
import site.addzero.lsi.poet.LsiReturnStatement
import site.addzero.lsi.poet.LsiThisExpression

class AssociatedIdGeneratorStructureTest {

    @Test
    fun `nullable associated id getter and setter are emitted as structured lsi statements`() {
        val property =
            AssociatedIdGenerator(
                jacksonTypes = ImmutableGeneratorTestFixtures.testMetaJacksonTypes(),
                withImplementation = true,
            ).generate(
                ImmutableAssociatedIdMetadata(
                    name = "storeId",
                    associatedIdLsiTypeName = ImmutableGeneratorTestFixtures.className("java.lang.Long"),
                    ownerPropName = "store",
                    targetIdPropName = "id",
                    isNullable = true,
                )
            ) ?: error("property should be generated")

        assertEquals(2, property.getterStatements.size)
        val getterGuard = property.getterStatements[0] as LsiIfStatement
        val getterCondition = getterGuard.condition as LsiBinaryExpression
        assertEquals(LsiCallExpression(name = "store"), getterCondition.left)
        assertEquals(LsiBinaryOperator.EQUALS, getterCondition.operator)
        assertEquals(LsiNullExpression, getterCondition.right)
        assertEquals(LsiReturnStatement(LsiNullExpression), getterGuard.thenStatements.single())
        assertEquals(
            LsiReturnStatement(
                LsiPropertyGetExpression(
                    receiver = LsiCallExpression(name = "store"),
                    name = "id",
                    type = ImmutableGeneratorTestFixtures.className("java.lang.Long"),
                )
            ),
            property.getterStatements[1],
        )

        assertEquals(2, property.setterStatements.size)
        val setterGuard = property.setterStatements[0] as LsiIfStatement
        val setterCondition = setterGuard.condition as LsiBinaryExpression
        assertEquals(LsiNameExpression("value"), setterCondition.left)
        assertEquals(LsiBinaryOperator.EQUALS, setterCondition.operator)
        assertEquals(LsiNullExpression, setterCondition.right)
        assertEquals(
            LsiPropertySetStatement(
                receiver = LsiThisExpression,
                name = "store",
                expression = LsiNullExpression,
            ),
            setterGuard.thenStatements[0],
        )
        assertEquals(LsiReturnStatement(null), setterGuard.thenStatements[1])
        assertEquals(
            LsiPropertySetStatement(
                receiver = LsiCallExpression(name = "store"),
                name = "id",
                expression = LsiNameExpression("value"),
            ),
            property.setterStatements[1],
        )

        assertTrue(property.annotations.single().useSiteTarget != null)
    }

}
