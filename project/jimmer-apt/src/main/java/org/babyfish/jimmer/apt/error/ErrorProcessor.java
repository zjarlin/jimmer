package org.babyfish.jimmer.apt.error;

import site.addzero.context.Context;
import site.addzero.context.LsiSourceFilterKt;
import site.addzero.lsi.jimmer.error.metadata.extractor.ErrorMetadataExtraction;
import site.addzero.lsi.jimmer.error.metadata.generator.ErrorProcessorSupport;
import site.addzero.lsi.poet.LsiFileSpec;

public class ErrorProcessor {

    private final boolean checkedException;

    public ErrorProcessor(boolean checkedException) {
        this.checkedException = checkedException;
    }

    public boolean process() {
        ErrorMetadataExtraction extraction = ErrorProcessorSupport.collectNewTypes(
                Context.INSTANCE.getLsiResolver(),
                LsiSourceFilterKt::matchesConfiguredSourceFilters
        );
        for (LsiFileSpec fileSpec : ErrorProcessorSupport.generateFileSpecs(extraction.getTypes(), checkedException)) {
            writeFileSpec(fileSpec);
        }
        return !extraction.getTypes().isEmpty();
    }

    private void writeFileSpec(LsiFileSpec fileSpec) {
        Context.INSTANCE.getLsiFiler().createSourceFile(fileSpec);
    }
}
