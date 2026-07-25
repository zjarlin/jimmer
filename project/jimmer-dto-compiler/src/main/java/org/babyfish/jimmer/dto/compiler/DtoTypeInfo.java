package org.babyfish.jimmer.dto.compiler;

import java.util.Objects;

public final class DtoTypeInfo {

    private final String baseTypeQualifiedName;

    private final DtoTypeKind kind;

    public DtoTypeInfo(String baseTypeQualifiedName, DtoTypeKind kind) {
        this.baseTypeQualifiedName = Objects.requireNonNull(
                baseTypeQualifiedName,
                "baseTypeQualifiedName cannot be null"
        );
        this.kind = Objects.requireNonNull(kind, "kind cannot be null");
    }

    public String getBaseTypeQualifiedName() {
        return baseTypeQualifiedName;
    }

    public DtoTypeKind getKind() {
        return kind;
    }
}
