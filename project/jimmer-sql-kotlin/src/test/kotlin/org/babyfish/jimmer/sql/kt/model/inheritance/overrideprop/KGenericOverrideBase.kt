package org.babyfish.jimmer.sql.kt.model.inheritance.overrideprop

import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.MappedSuperclass

@MappedSuperclass
interface KGenericOverrideBase<T> where T : KGenericOverrideBase<T> {

    @ManyToOne
    val parent: T?
}
