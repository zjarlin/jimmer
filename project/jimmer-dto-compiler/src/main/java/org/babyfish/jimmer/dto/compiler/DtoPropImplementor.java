package org.babyfish.jimmer.dto.compiler;

import site.addzero.lsi.dto.LsiDtoBaseProp;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

interface DtoPropImplementor extends AbstractProp {

    LsiDtoBaseProp getBaseProp();

    Map<String, ?> getBasePropMap();

    int getBaseLine();

    int getBaseColumn();

    int getAliasLine();

    int getAliasColumn();

    @Nullable
    String getFuncName();

    Mandatory getMandatory();

    DtoModifier getInputModifier();
}
