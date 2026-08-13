package org.babyfish.jimmer.compiler.tuple

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.tuple.TypedTupleJavaSetterConstruction
import site.addzero.lsi.jimmer.tuple.TypedTupleProperty
import site.addzero.lsi.jimmer.tuple.TypedTupleSchema
import site.addzero.lsi.jimmer.tuple.TypedTupleSetterAssignment
import site.addzero.lsi.jimmer.tuple.TypedTupleType
import site.addzero.lsi.jimmer.tuple.TypedTupleValidationException
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType

class TypedTupleCodegenNamesTest {

    @Test
    fun `rejects duplicate builder names after codegen normalization`() {
        val exception = assertFailsWith<TypedTupleValidationException> {
            duplicateBuilderSchema().validateCodegenNames()
        }

        assertEquals(TUPLE_ID, exception.declarationId)
        assertEquals(
            "Typed tuple 'demo.CollisionTuple' produces duplicate builder 'FooBuilder'",
            exception.message,
        )
    }

    private fun duplicateBuilderSchema(): TypedTupleSchema {
        val names = listOf("first", "foo", "Foo")
        val sourceMemberIds = names.map { name -> LsiSymbolId.field(TUPLE_ID, name) }
        val properties = names.mapIndexed { index, name ->
            TypedTupleProperty(
                id = LsiSymbolId.property(TUPLE_ID, name),
                sourceMemberId = sourceMemberIds[index],
                name = name,
                index = index,
                type = LsiPrimitiveType(LsiPrimitiveKind.INT),
            )
        }
        return TypedTupleSchema(
            tuples = listOf(
                TypedTupleType(
                    id = TUPLE_ID,
                    qualifiedName = "demo.CollisionTuple",
                    packageName = "demo",
                    simpleName = "CollisionTuple",
                    sourceLanguage = LsiLanguage.JAVA,
                    properties = properties,
                    construction = TypedTupleJavaSetterConstruction(
                        constructorId = null,
                        assignments = sourceMemberIds.mapIndexed { index, sourceMemberId ->
                            TypedTupleSetterAssignment(
                                sourceMemberId = sourceMemberId,
                                propertyIndex = index,
                                setterName = "set${names[index]}",
                            )
                        },
                    ),
                )
            ),
        )
    }

    private companion object {
        val TUPLE_ID = LsiSymbolId.type("demo.CollisionTuple")
    }
}
