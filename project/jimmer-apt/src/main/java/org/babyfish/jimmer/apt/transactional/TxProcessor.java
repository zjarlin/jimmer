package org.babyfish.jimmer.apt.transactional;
import site.addzero.context.Context;
import site.addzero.lsi.jimmer.transactional.metadata.extractor.TxMetadataExtraction;
import site.addzero.lsi.jimmer.transactional.metadata.generator.TxProcessorSupport;
import site.addzero.lsi.poet.LsiFileSpec;

public class TxProcessor {

    private final boolean buddyIgnoreResourceGeneration;

    public TxProcessor(boolean buddyIgnoreResourceGeneration) {
        this.buddyIgnoreResourceGeneration = buddyIgnoreResourceGeneration;
    }

    public void process() {
        if (buddyIgnoreResourceGeneration) {
            return;
        }
        TxMetadataExtraction extraction = TxProcessorSupport.collectNewTypes(Context.INSTANCE.getLsiResolver());
        for (LsiFileSpec fileSpec : TxProcessorSupport.generateFileSpecs(extraction.getTypes())) {
            write(fileSpec);
        }
    }

    private void write(LsiFileSpec fileSpec) {
        Context.INSTANCE.getLsiFiler().createSourceFile(fileSpec);
    }
}
