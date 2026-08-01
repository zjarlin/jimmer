package org.babyfish.jimmer.sql.kt.model.pg

import org.babyfish.jimmer.json.codec.JsonCodec.defaultCodec
import org.babyfish.jimmer.json.codec.JsonType
import org.babyfish.jimmer.sql.runtime.ScalarProvider

class PointProvider : ScalarProvider<Point, String> {

    override fun toScalar(sqlValue: String): Point =
        CODEC.decode(sqlValue, TYPE)

    override fun toSql(scalarValue: Point): String =
        CODEC.encode(scalarValue, TYPE)

    override fun isJsonScalar(): Boolean = true

    companion object {
        @JvmStatic
        private val CODEC = defaultCodec()

        @JvmStatic
        private val TYPE = JsonType.of(Point::class.java)
    }
}
