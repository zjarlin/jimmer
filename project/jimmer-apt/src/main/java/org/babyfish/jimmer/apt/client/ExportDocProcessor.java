package org.babyfish.jimmer.apt.client;

import site.addzero.context.Context;
import site.addzero.lsi.apt.diagnostic.AptLsiDiagnostics;
import site.addzero.lsi.codegen.GeneratedResourceArtifact;
import site.addzero.lsi.jimmer.client.metadata.generator.ClientProcessorSupport;
import site.addzero.lsi.resolver.LsiResolver;

import java.util.Set;

public class ExportDocProcessor {

    public ExportDocProcessor() {}

    public void process() {
        LsiResolver resolver = Context.INSTANCE.getLsiResolver();
        Set<String> typeNames = ClientProcessorSupport.collectExportDocTypeNames(resolver);
        if (typeNames.isEmpty()) {
            return;
        }
        GeneratedResourceArtifact artifact = ClientProcessorSupport.generateExportDocArtifact(resolver, typeNames);
        if (artifact != null) {
            writeArtifact(artifact);
        }
    }

    private void writeArtifact(GeneratedResourceArtifact artifact) {
        try {
            Context.INSTANCE.getLsiFiler().mergePropertiesResourceFile(
                    artifact.getPath(),
                    artifact.getContent(),
                    ClientProcessorSupport.EXPORT_DOC_RESOURCE_COMMENT
            );
        } catch (Exception ex) {
            throw AptLsiDiagnostics.generatorException(
                    "Cannot merge generated file \"" + artifact.getPath() + "\"",
                    ex
            );
        }
    }
}
