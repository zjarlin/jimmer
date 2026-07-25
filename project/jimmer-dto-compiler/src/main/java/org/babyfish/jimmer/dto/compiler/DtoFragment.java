package org.babyfish.jimmer.dto.compiler;

import org.babyfish.jimmer.dto.compiler.spi.BaseProp;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

class DtoFragment<T, P extends BaseProp> {

    final CompilerContext<T, P> ctx;

    final DtoParser.DtoFragmentContext ast;

    final T baseType;

    final String qualifiedName;

    DtoFragment(
            CompilerContext<T, P> ctx,
            DtoParser.DtoFragmentContext ast,
            T baseType,
            String qualifiedName
    ) {
        this.ctx = ctx;
        this.ast = ast;
        this.baseType = baseType;
        this.qualifiedName = qualifiedName;
    }

    Map<String, AbstractProp> resolve(
            DtoType<T, P> ownerType,
            Set<DtoModifier> modifiers
    ) {
        return new DtoTypeBuilder<>(
                null,
                baseType,
                ast.body,
                null,
                null,
                modifiers,
                Collections.emptyList(),
                Collections.emptyList(),
                ctx
        ).buildFragment(ownerType);
    }
}
