package org.babyfish.jimmer.dto.compiler;

import org.babyfish.jimmer.dto.compiler.spi.BaseProp;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class DtoTypeRef<T, P extends BaseProp> implements DtoPropTarget<T, P> {

    private final String qualifiedName;

    private final T targetBaseType;

    private final String targetBaseTypeQualifiedName;

    private final int line;

    private final int col;

    @Nullable
    private DtoType<T, P> sourceType;

    @Nullable
    private DtoTypeInfo typeInfo;

    DtoTypeRef(
            String qualifiedName,
            T targetBaseType,
            String targetBaseTypeQualifiedName,
            int line,
            int col
    ) {
        this.qualifiedName = Objects.requireNonNull(qualifiedName, "qualifiedName cannot be null");
        this.targetBaseType = Objects.requireNonNull(targetBaseType, "targetBaseType cannot be null");
        this.targetBaseTypeQualifiedName = Objects.requireNonNull(
                targetBaseTypeQualifiedName,
                "targetBaseTypeQualifiedName cannot be null"
        );
        this.line = line;
        this.col = col;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public T getTargetBaseType() {
        return targetBaseType;
    }

    public String getTargetBaseTypeQualifiedName() {
        return targetBaseTypeQualifiedName;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return col;
    }

    @Nullable
    public DtoType<T, P> getSourceType() {
        return sourceType;
    }

    @Nullable
    public DtoTypeInfo getTypeInfo() {
        return typeInfo;
    }

    void resolve(DtoTypeInfo typeInfo, @Nullable DtoType<T, P> sourceType) {
        this.typeInfo = typeInfo;
        this.sourceType = sourceType;
    }

    @Override
    public String toString() {
        return qualifiedName;
    }
}
