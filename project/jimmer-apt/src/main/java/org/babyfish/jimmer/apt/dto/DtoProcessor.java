package org.babyfish.jimmer.apt.dto;

import site.addzero.context.Context;
import site.addzero.context.LsiSourceFilterKt;
import site.addzero.lsi.jimmer.dto.LsiDtoModifier;
import site.addzero.lsi.jimmer.dto.DtoProcessorSupport;
import site.addzero.lsi.poet.LsiFileSpec;

import java.util.*;

public class DtoProcessor {

    private final Collection<String> dtoDirs;

    private final LsiDtoModifier defaultNullableInputModifier;

    public DtoProcessor(
            Collection<String> dtoDirs,
            LsiDtoModifier defaultNullableInputModifier
    ) {
        this.dtoDirs = dtoDirs;
        this.defaultNullableInputModifier = defaultNullableInputModifier;
    }

    public boolean process() {
        Collection<LsiFileSpec> fileSpecs = DtoProcessorSupport.generateFileSpecs(
                DtoProcessorSupport.collectDtoFiles(
                        Context.INSTANCE.getSourceAnchorFilePath(),
                        dtoDirs
                ),
                defaultNullableInputModifier,
                Context.INSTANCE.getLsiResolver(),
                LsiSourceFilterKt::matchesConfiguredSourceFilters,
                Context.INSTANCE::typeOf,
                Context.INSTANCE::resolve,
                Context.INSTANCE::findDraftImplDocMap,
                "true".equals(Context.INSTANCE.option("jimmer.dto.mutable"))
        );
        for (LsiFileSpec fileSpec : fileSpecs) {
            Context.INSTANCE.getLsiFiler().createSourceFile(fileSpec);
        }
        return !fileSpecs.isEmpty();
    }
}
