package org.babyfish.jimmer.dto.compiler;

class DtoFragmentUse<T, P> {

    final DtoFragment<T, P> fragment;

    final DtoParser.IncludeContext ast;

    DtoFragmentUse(DtoFragment<T, P> fragment, DtoParser.IncludeContext ast) {
        this.fragment = fragment;
        this.ast = ast;
    }
}
