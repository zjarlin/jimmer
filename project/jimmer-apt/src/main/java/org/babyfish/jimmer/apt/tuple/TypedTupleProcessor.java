package org.babyfish.jimmer.apt.tuple;

import site.addzero.context.Context;
import site.addzero.lsi.jimmer.tuple.metadata.extractor.TypedTupleMetadataExtraction;
import site.addzero.lsi.jimmer.tuple.metadata.generator.TypedTupleProcessorSupport;
import site.addzero.lsi.poet.LsiFileSpec;

public class TypedTupleProcessor {

    public void process() {
        TypedTupleMetadataExtraction extraction = TypedTupleProcessorSupport.collectRoundTypes(
                Context.INSTANCE.getLsiResolver(),
                Context.INSTANCE.getDelayedTupleTypeNames()
        );
        for (LsiFileSpec fileSpec : TypedTupleProcessorSupport.generateFileSpecs(extraction.getTypes())) {
            write(fileSpec);
        }
    }

    private void write(LsiFileSpec fileSpec) {
        Context.INSTANCE.getLsiFiler().createSourceFile(fileSpec);
    }
}
