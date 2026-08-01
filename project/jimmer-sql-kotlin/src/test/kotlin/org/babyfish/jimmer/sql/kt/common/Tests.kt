package org.babyfish.jimmer.sql.kt.common

import org.babyfish.jimmer.json.codec.JsonCodec.defaultCodec
import org.babyfish.jimmer.json.codec.Node
import kotlin.test.expect

fun assertContent(expected: String, actual: Any) {
    val normalizedExpected = expected.replace("--->", "").replace("\r", "").replace("\n", "")
    val actualString = actual.toString()

    // Try to parse as JSON and compare semantically to handle property ordering issues
    try {
        val expectedJson = defaultCodec().decode(normalizedExpected, Node::class.java)
        val actualJson = defaultCodec().decode(actualString, Node::class.java)
        expect(expectedJson) { actualJson }
    } catch (e: Exception) {
        // Fall back to string comparison if JSON parsing fails
        expect(normalizedExpected) { actualString }
    }
}
