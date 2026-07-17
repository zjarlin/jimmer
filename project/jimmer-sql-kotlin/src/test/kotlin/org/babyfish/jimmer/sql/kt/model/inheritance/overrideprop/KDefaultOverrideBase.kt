package org.babyfish.jimmer.sql.kt.model.inheritance.overrideprop

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.ExcludeFromAllScalars
import org.babyfish.jimmer.sql.MappedSuperclass
import javax.validation.constraints.Min

@MappedSuperclass
interface KDefaultOverrideBase {

    @Default("0")
    @Column(name = "OVERRIDDEN_STATUS")
    @ExcludeFromAllScalars
    @get:JsonFormat(pattern = "base")
    @get:JsonIgnore
    val status: @Min(10) Int
}
