@file:Suppress("warnings")

package demo

import kotlin.Int
import kotlin.Suppress
import org.babyfish.jimmer.Draft
import org.babyfish.jimmer.`internal`.GeneratedBy
import org.babyfish.jimmer.kt.DslScope
import org.babyfish.jimmer.meta.ImmutablePropCategory
import org.babyfish.jimmer.meta.ImmutableType

@DslScope
@GeneratedBy(type = BaseOnlyOneSwitch::class)
public interface BaseOnlyOneSwitchDraft : BaseOnlyOneSwitch, Draft {
    override var status: Int

    @GeneratedBy(type = BaseOnlyOneSwitch::class)
    public object `$` {
        public val type: ImmutableType = ImmutableType
                    .newBuilder(
                        "0.11.2",
                        BaseOnlyOneSwitch::class,
                        listOf(

                        ),
                        null
                    )
                    .add(-1, "status", ImmutablePropCategory.SCALAR, Int::class.java, false)
                    .build()
    }
}
