package org.babyfish.jimmer.sql.kt.model.pg

import org.babyfish.jimmer.json.codec.JsonCodec.defaultCodec
import org.babyfish.jimmer.json.codec.JsonType
import org.babyfish.jimmer.kt.toImmutableProp
import org.babyfish.jimmer.meta.ImmutableProp
import org.babyfish.jimmer.sql.runtime.ScalarProvider

class TagsProvider : ScalarProvider<List<String>, String> {

    override fun toScalar(sqlValue: String): List<String> =
        CODEC.decode(sqlValue, TYPE)

    override fun toSql(scalarValue: List<String>): String =
        CODEC.encode(scalarValue, TYPE)

    override fun isJsonScalar(): Boolean = true

    override fun getHandledProps(): Collection<ImmutableProp> =
        listOf(JsonWrapper::tags.toImmutableProp())

    companion object {
        @JvmStatic
        private val CODEC = defaultCodec()

        @JvmStatic
        private val TYPE = JsonType.listOf(String::class.java)
    }
}
