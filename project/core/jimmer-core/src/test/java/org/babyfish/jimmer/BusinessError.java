package org.babyfish.jimmer;

import org.babyfish.jimmer.error.ErrorFamily;
import org.babyfish.jimmer.error.ErrorField;

@ErrorFamily
public enum BusinessError {

    GLOBAL_TENANT_REQUIRED,

    @ErrorField(name = "pathNodes", type = String.class, list = true, nullable = true)
    ILLEGAL_PATHS_NODES,

    @ErrorField(name = "min", type = int.class)
    @ErrorField(name = "max", type = int.class)
    OUT_OF_RANGE
}
