package org.babyfish.jimmer.sql.kt.model.pg

import org.babyfish.jimmer.json.codec.JsonCodec.defaultCodec
import org.babyfish.jimmer.json.codec.JsonType
import org.babyfish.jimmer.kt.toImmutableProp
import org.babyfish.jimmer.meta.ImmutableProp
import org.babyfish.jimmer.sql.runtime.ScalarProvider

class ScoresProvider : ScalarProvider<Map<Long, Int>, String> {

    override fun toScalar(sqlValue: String): Map<Long, Int> =
        CODEC.decode(sqlValue, TYPE)

    override fun toSql(scalarValue: Map<Long, Int>): String =
        CODEC.encode(scalarValue, TYPE)

    override fun isJsonScalar(): Boolean = true

    override fun getHandledProps(): Collection<ImmutableProp> =
        listOf(JsonWrapper::scores.toImmutableProp())

    companion object {
        @JvmStatic
        private val CODEC = defaultCodec()

        @JvmStatic
        private val TYPE = JsonType.mapOf(Long::class.java, Int::class.java)
    }
}
