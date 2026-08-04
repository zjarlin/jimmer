package org.babyfish.jimmer.sql.model.inheritance.overrideprop;

import org.babyfish.jimmer.sql.Default;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;

@Entity
public interface DefaultOverrideEntity extends DefaultOverrideBase {

    @Id
    long getId();

    @Override
    @Default("1")
    int getStatus();
}
