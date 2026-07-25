package org.babyfish.jimmer.dto.compiler;

import org.babyfish.jimmer.dto.compiler.spi.BaseProp;

class DtoFragmentUse<T, P extends BaseProp> {

    final DtoFragment<T, P> fragment;

    final DtoParser.IncludeContext ast;

    DtoFragmentUse(DtoFragment<T, P> fragment, DtoParser.IncludeContext ast) {
        this.fragment = fragment;
        this.ast = ast;
    }
}
