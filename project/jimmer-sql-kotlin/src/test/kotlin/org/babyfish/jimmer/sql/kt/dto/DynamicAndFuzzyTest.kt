package org.babyfish.jimmer.sql.kt.dto

import org.babyfish.jimmer.json.codec.JsonCodec.defaultCodec
import org.babyfish.jimmer.sql.kt.common.assertContent
import org.babyfish.jimmer.sql.kt.model.classic.book.dto.DynamicBookInput
import org.babyfish.jimmer.sql.kt.model.classic.book.dto.DynamicBookInput2
import org.babyfish.jimmer.sql.kt.model.classic.book.dto.FuzzyBookInput
import org.babyfish.jimmer.sql.kt.model.classic.store.dto.DynamicBookStoreInput
import org.junit.Test
import java.math.BigDecimal


class DynamicAndFuzzyTest {

    @Test
    fun testByDynamicBookStoreInput() {
        val input = DynamicBookStoreInput(name = "MANNING", isWebsiteLoaded = false)
        assertContent(
            "{\"name\":\"MANNING\"}",
            input.toEntity()
        )
        val store = defaultCodec()
            .decode("{\"name\":\"MANNING\"}", DynamicBookStoreInput::class.java)
            .toEntity()
        assertContent(
            "{\"name\":\"MANNING\"}",
            store
        )
    }

    @Test
    fun testNullByDynamicInput() {
        val input = DynamicBookInput()
        assertContent(
            "{}",
            input.toEntity()
        )
        val book = defaultCodec()
            .decode("{}", DynamicBookInput::class.java)
            .toEntity()
        assertContent("{}", book)
    }

    @Test
    fun testNonNullByDynamicInput() {
        val input: DynamicBookInput =
            DynamicBookInput(
                name = "Book",
                edition = 7,
                price = BigDecimal("59.99"),
                storeId = 3L
            )
        assertContent(
            "{" +
                    "--->\"name\":\"Book\"," +
                    "--->\"edition\":7," +
                    "--->\"price\":59.99," +
                    "--->\"store\":{" +
                    "--->--->\"id\":3" +
                    "--->}" +
                    "}",
            input.toEntity().toString()
        )
        val book = defaultCodec()
            .decode(
                "{" +
                        "\"name\":\"Book\"," +
                        "\"edition\":7," +
                        "\"price\":59.99," +
                        "\"storeId\":3" +
                        "}",
                DynamicBookInput::class.java
            ).toEntity()
        assertContent(
            "{" +
                    "--->\"name\":\"Book\"," +
                    "--->\"edition\":7," +
                    "--->\"price\":59.99," +
                    "--->\"store\":{" +
                    "--->--->\"id\":3" +
                    "--->}" +
                    "}",
            book
        )
    }

    @Test
    fun testNullByDynamicInput2() {
        val input = DynamicBookInput2(
            parentName = "MANNING",
            parentWebsite = null,
            isParentWebsiteLoaded = true
        )
        assertContent(
            "{\"store\":{\"name\":\"MANNING\",\"website\":null}}",
            input.toEntity()
        )
        val book = defaultCodec()
            .decode(
                "{\"parentName\":\"MANNING\",\"parentWebsite\":null}",
                DynamicBookInput2::class.java
            )
            .toEntity()
        assertContent(
            "{\"store\":{\"name\":\"MANNING\",\"website\":null}}",
            book
        )
    }

    @Test
    fun testNonNullByDynamicInput2() {
        val input = DynamicBookInput2(
            name = "Book",
            edition = 7,
            price = BigDecimal("59.99"),
            parentName = "Store",
            parentWebsite = "https://www.store.com",
        )
        assertContent(
            "{" +
                    "--->\"name\":\"Book\"," +
                    "--->\"edition\":7," +
                    "--->\"price\":59.99," +
                    "--->\"store\":{" +
                    "--->--->\"name\":\"Store\"," +
                    "--->--->\"website\":\"https://www.store.com\"" +
                    "--->}" +
                    "}",
            input.toEntity().toString()
        )
        val book = defaultCodec()
            .decode(
                "{" +
                        "\"name\":\"Book\"," +
                        "\"edition\":7," +
                        "\"price\":59.99," +
                        "\"parentName\":\"Store\"," +
                        "\"parentWebsite\":\"https://www.store.com\"" +
                        "}",
                DynamicBookInput2::class.java
            ).toEntity()
        assertContent(
            "{" +
                    "--->\"name\":\"Book\"," +
                    "--->\"edition\":7," +
                    "--->\"price\":59.99," +
                    "--->\"store\":{" +
                    "--->--->\"name\":\"Store\"," +
                    "--->--->\"website\":\"https://www.store.com\"" +
                    "--->}" +
                    "}",
            book
        )
    }

    @Test
    fun testIssue994() {
        val input = DynamicBookInput(name = "MANNING")
        assertContent(
            "{\"name\":\"MANNING\"}",
            defaultCodec().encode(input)
        )
        val book = defaultCodec()
            .decode("{\"name\":\"MANNING\"}", DynamicBookInput::class.java)
            .toEntity()
        assertContent(
            "{\"name\":\"MANNING\"}",
            book
        )
    }

    @Test
    fun testFuzzyInput() {
        val input = FuzzyBookInput(name = "SQL in Action")
        assertContent(
            "{\"name\":\"SQL in Action\"," +
                    "\"edition\":null," +
                    "\"price\":null," +
                    "\"storeId\":null," +
                    "\"authorIds\":null}",
            defaultCodec().encode(input)
        )
        val book = defaultCodec()
            .decode(
                "{\"name\":\"SQL in Action\"," +
                        "\"edition\":null," +
                        "\"price\":null," +
                        "\"storeId\":null," +
                        "\"authorIds\":null}",
                FuzzyBookInput::class.java
            ).toEntity()
        assertContent("{\"name\":\"SQL in Action\"}", book)
    }
}
