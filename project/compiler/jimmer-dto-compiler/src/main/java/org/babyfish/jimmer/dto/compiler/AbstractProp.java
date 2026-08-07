package org.babyfish.jimmer.dto.compiler;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface AbstractProp {

    DtoFile getDeclaringFile();

    String getName();

    String getAlias();

    boolean isNullable();

    int getAliasLine();

    int getAliasColumn();

    List<Anno> getAnnotations();

    String getDoc();

    @Nullable
    default String getFuncName() {
        return null;
    }

    @Nullable
    default DtoModifier getInputModifier() {
        return null;
    }
}
