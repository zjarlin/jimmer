package org.babyfish.jimmer.sql.model.inheritance.overrideprop;

import org.babyfish.jimmer.sql.Column;
import org.babyfish.jimmer.sql.Default;
import org.babyfish.jimmer.sql.ExcludeFromAllScalars;
import org.babyfish.jimmer.sql.MappedSuperclass;

@MappedSuperclass
public interface DefaultOverrideBase {

    @Default("0")
    @Column(name = "OVERRIDDEN_STATUS")
    @ExcludeFromAllScalars
    int getStatus();
}
