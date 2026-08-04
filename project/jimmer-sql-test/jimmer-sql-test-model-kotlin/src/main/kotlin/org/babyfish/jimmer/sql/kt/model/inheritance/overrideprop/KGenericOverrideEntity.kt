package org.babyfish.jimmer.sql.kt.model.inheritance.overrideprop

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.JoinColumn

@Entity
interface KGenericOverrideEntity : KGenericOverrideBase<KGenericOverrideEntity> {

    @Id
    val id: Long

    @JoinColumn(name = "PARENT_ID")
    override val parent: KGenericOverrideEntity?
}
