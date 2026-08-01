package org.babyfish.jimmer.sql.kt.dto

import org.babyfish.jimmer.Input
import org.babyfish.jimmer.json.codec.JsonCodec.defaultCodec
import org.babyfish.jimmer.sql.kt.common.assertContent
import org.babyfish.jimmer.sql.kt.model.classic.book.dto.*
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.expect

class InputModifierTest {

    @Test
    fun testAllProperties() {
        val json = """{
            |"id": 100,
            |"name": "SQL in Action",
            |"edition": 1,
            |"price": 49.9
            |}""".trimMargin()
        val input = defaultCodec().decode(json, MixedBookInput::class.java)
        assertContent(
            """MixedBookInput(
                |--->id=100, 
                |--->name=SQL in Action, 
                |--->edition=1, 
                |--->price=49.9
                |)""".trimMargin(),
            input
        )
        assertContent(
            """{
                |--->"id":100,
                |--->"name":"SQL in Action",
                |--->"edition":1,
                |--->"price":49.9
                |}""".trimMargin(),
            input.toEntity()
        )
    }

    @Test
    fun testNullId() {
        val json = """{
            |"id": null,
            |"name": "SQL in Action",
            |"edition": 1,
            |"price": 49.9
            |}""".trimMargin()
        val input = defaultCodec().decode(json, MixedBookInput::class.java)
        assertContent(
            """MixedBookInput(
                |--->id=null, 
                |--->name=SQL in Action, 
                |--->edition=1, 
                |--->price=49.9
                |)""".trimMargin(),
            input
        )
        assertContent(
            """{
                |--->"name":"SQL in Action",
                |--->"edition":1,
                |--->"price":49.9
                |}""".trimMargin(),
            input.toEntity()
        )
    }

    @Test
    fun testAllMissedId() {
        val json = """{
            |"name": "SQL in Action",
            |"edition": 1,
            |"price": 49.9
            |}""".trimMargin()

        val ex = assertFails {
            defaultCodec().decode(json, MixedBookInput::class.java)
        }
        expect(
            "An object whose type is " +
                "\"org.babyfish.jimmer.sql.kt.model.classic.book.dto.MixedBookInput\" " +
                "cannot be deserialized by JSON codec. The current input has the fixed " +
                "nullable property \"id\", it is not specified by JSON explicitly. " +
                "Please either explicitly specify the property as null in the JSON, " +
                "or specify the current input property as static, dynamic or fuzzy " +
                "in the DTO language"
        ) {
            ex.cause!!.message
        }
    }

    @Test
    fun testNullName() {
        val json = """{
            |"id": 100,
            |"name": null,
            |"edition": 1,
            |"price": 49.9
            |}""".trimMargin()
        val input = defaultCodec().decode(json, MixedBookInput::class.java)
        assertContent(
            """MixedBookInput(
                |--->id=100, 
                |--->name=null, 
                |--->edition=1, 
                |--->price=49.9
                |)""".trimMargin(),
            input
        )
        assertContent(
            """{
                |--->"id":100,
                |--->"edition":1,
                |--->"price":49.9
                |}""".trimMargin(),
            input.toEntity()
        )
    }

    @Test
    fun testMissedName() {
        val json = """{
            |"id": 100,
            |"edition": 1,
            |"price": 49.9
            |}""".trimMargin()
        val input = defaultCodec().decode(json, MixedBookInput::class.java)
        assertContent(
            """MixedBookInput(
                |--->id=100, 
                |--->name=null, 
                |--->edition=1, 
                |--->price=49.9
                |)""".trimMargin(),
            input
        )
        assertContent(
            """{
                |--->"id":100,
                |--->"edition":1,
                |--->"price":49.9
                |}""".trimMargin(),
            input.toEntity()
        )
    }

    @Test
    fun testNullEdition() {
        val json = """{
            |"id": 100,
            |"name": "SQL in Action",
            |"edition": null,
            |"price": 49.9
            |}""".trimMargin()
        val input = defaultCodec().decode(json, MixedBookInput::class.java)
        assertContent(
            """MixedBookInput(
                |--->id=100, 
                |--->name=SQL in Action, 
                |--->edition=null, 
                |--->price=49.9
                |)""".trimMargin(),
            input
        )
        assertContent(
            """{
                |--->"id":100,
                |--->"name":"SQL in Action",
                |--->"price":49.9
                |}""".trimMargin(),
            input.toEntity()
        )
    }

    @Test
    fun testAllMissedEdition() {
        val json = """{
            |"id": 100,
            |"name": "SQL in Action",
            |"price": 49.9
            |}""".trimMargin()
        val input = defaultCodec().decode(json, MixedBookInput::class.java)
        assertContent(
            """MixedBookInput(
                |--->id=100, 
                |--->name=SQL in Action, 
                |--->price=49.9
                |)""".trimMargin(),
            input
        )
        assertContent(
            """{
                |--->"id":100,
                |--->"name":"SQL in Action",
                |--->"price":49.9
                |}""".trimMargin(),
            input.toEntity()
        )
    }

    @Test
    fun testNullPrice() {
        val json = """{
            |"id": 100,
            |"name": "SQL in Action",
            |"edition": 1,
            |"price": null
            |}""".trimMargin()
        val input = defaultCodec().decode(json, MixedBookInput::class.java)
        assertContent(
            """MixedBookInput(
                |--->id=100, 
                |--->name=SQL in Action, 
                |--->edition=1
                |)""".trimMargin(),
            input
        )
        assertContent(
            """{
                |--->"id":100,
                |--->"name":"SQL in Action",
                |--->"edition":1
                |}""".trimMargin(),
            input.toEntity()
        )
    }

    @Test
    fun testMissedPrice() {
        val json = """{
            |"id": 100,
            |"name": "SQL in Action",
            |"edition": 1
            |}""".trimMargin()
        val input = defaultCodec().decode(json, MixedBookInput::class.java)
        assertContent(
            """MixedBookInput(
                |--->id=100, 
                |--->name=SQL in Action, 
                |--->edition=1
                |)""".trimMargin(),
            input
        )
        assertContent(
            """{
                |--->"id":100,
                |--->"name":"SQL in Action",
                |--->"edition":1
                |}""".trimMargin(),
            input.toEntity()
        )
    }

    @Test
    fun testNullableParent() {
        val nonNullParent = "{\"storeId\": 3}"
        val nullParent = "{\"storeId\": null}"
        val undefinedParent = "{}"

        assertNullParent(
            nonNullParent,
            BookInputWithFixedParent::class,
            "BookInputWithFixedParent(storeId=3)",
            "{\"store\":{\"id\":3}}"
        )
        assertNullParent(
            nullParent,
            BookInputWithFixedParent::class,
            "BookInputWithFixedParent(storeId=null)",
            "{\"store\":null}"
        )

        assertNullParent(
            nonNullParent,
            BookInputWithStaticParent::class,
            "BookInputWithStaticParent(storeId=3)",
            "{\"store\":{\"id\":3}}"
        )
        assertNullParent(
            nullParent,
            BookInputWithStaticParent::class,
            "BookInputWithStaticParent(storeId=null)",
            "{\"store\":null}"
        )
        assertNullParent(
            undefinedParent,
            BookInputWithStaticParent::class,
            "BookInputWithStaticParent(storeId=null)",
            "{\"store\":null}"
        )

        assertNullParent(
            nonNullParent,
            BookInputWithDynamicParent::class,
            "BookInputWithDynamicParent(storeId=3)",
            "{\"store\":{\"id\":3}}"
        )
        assertNullParent(
            nullParent,
            BookInputWithDynamicParent::class,
            "BookInputWithDynamicParent(storeId=null)",
            "{\"store\":null}"
        )
        assertNullParent(
            undefinedParent,
            BookInputWithDynamicParent::class,
            "BookInputWithDynamicParent()",
            "{}"
        )

        assertNullParent(
            nonNullParent,
            BookInputWithFuzzyParent::class,
            "BookInputWithFuzzyParent(storeId=3)",
            "{\"store\":{\"id\":3}}"
        )
        assertNullParent(
            nullParent,
            BookInputWithFuzzyParent::class,
            "BookInputWithFuzzyParent()",
            "{}"
        )
        assertNullParent(
            undefinedParent,
            BookInputWithFuzzyParent::class,
            "BookInputWithFuzzyParent()",
            "{}"
        )
    }

    companion object {
        private fun <T : Input<*>> assertNullParent(
            json: String,
            type: KClass<T>,
            dtoJson: String,
            entityJson: String
        ) {
            val input = defaultCodec().decode(json, type.java)
            assertContent(dtoJson, input)
            assertContent(entityJson, input.toEntity())
        }
    }
}
