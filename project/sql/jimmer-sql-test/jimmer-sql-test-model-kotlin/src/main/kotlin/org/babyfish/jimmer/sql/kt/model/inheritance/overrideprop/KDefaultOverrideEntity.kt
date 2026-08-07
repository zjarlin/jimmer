package org.babyfish.jimmer.sql.kt.model.inheritance.overrideprop

import com.fasterxml.jackson.annotation.JsonFormat
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import javax.validation.constraints.Min

@Entity
interface KDefaultOverrideEntity : KDefaultOverrideBase {

    @Id
    val id: Long

    @Default("1")
    @get:JsonFormat(pattern = "child")
    override val status: @Min(1) Int
}
