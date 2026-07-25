package org.babyfish.jimmer.dto.compiler;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

interface DtoPropImplementor<P> extends AbstractProp {

    P getBaseProp();

    Map<String, P> getBasePropMap();

    default String getBasePropName() {
        return getBasePropMap().keySet().iterator().next();
    }

    int getBaseLine();

    int getBaseColumn();

    int getAliasLine();

    int getAliasColumn();

    @Nullable
    String getFuncName();

    Mandatory getMandatory();

    DtoModifier getInputModifier();
}
