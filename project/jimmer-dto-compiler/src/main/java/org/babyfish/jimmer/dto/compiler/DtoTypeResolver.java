package org.babyfish.jimmer.dto.compiler;

import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface DtoTypeResolver {

    @Nullable
    DtoTypeInfo resolve(String qualifiedName);
}
