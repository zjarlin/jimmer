package org.babyfish.jimmer.apt.dto;

import org.babyfish.jimmer.apt.Context;
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames;
import org.babyfish.jimmer.compiler.dto.JimmerDtoJacksonVersion;
import site.addzero.lsi.core.LsiSource;
import site.addzero.lsi.jimmer.ImmutableSchema;
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract;
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution;
import site.addzero.lsi.jimmer.dto.DtoGenerationExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoGraph;
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution;
import site.addzero.lsi.model.LsiWorkspace;
import site.addzero.lsi.poet.LsiPoetTypeName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DtoProcessor {

    private final Context context;

    private final List<DtoGraph> graphs;

    private final Map<LsiSource, DtoAnnotationContract> annotationContractsBySource;

    private final Map<LsiSource, DtoInterfaceContractResolution> interfaceContractsBySource;

    private final Map<LsiSource, DtoConfigContractResolution> configContractsBySource;

    private final ImmutableSchema immutableSchema;

    private final LsiWorkspace lsiWorkspace;

    private final Map<site.addzero.lsi.jimmer.dto.DtoTypeId, LsiPoetTypeName> batchRootDtoTypeNames;

    private final JimmerDtoJacksonVersion jacksonVersion;

    private final boolean hibernateValidatorEnhancement;

    public DtoProcessor(
            Context context,
            Collection<DtoGraph> graphs,
            Map<LsiSource, DtoAnnotationContract> annotationContractsBySource,
            Map<LsiSource, DtoInterfaceContractResolution> interfaceContractsBySource,
            Map<LsiSource, DtoConfigContractResolution> configContractsBySource,
            ImmutableSchema immutableSchema,
            LsiWorkspace lsiWorkspace,
            JimmerDtoJacksonVersion jacksonVersion,
            boolean hibernateValidatorEnhancement
    ) {
        this.context = context;
        this.graphs = Collections.unmodifiableList(new ArrayList<>(graphs));
        this.annotationContractsBySource = Collections.unmodifiableMap(
                new LinkedHashMap<>(annotationContractsBySource)
        );
        this.interfaceContractsBySource = Collections.unmodifiableMap(
                new LinkedHashMap<>(interfaceContractsBySource)
        );
        this.configContractsBySource = Collections.unmodifiableMap(
                new LinkedHashMap<>(configContractsBySource)
        );
        this.immutableSchema = immutableSchema;
        this.lsiWorkspace = lsiWorkspace;
        this.batchRootDtoTypeNames = JimmerDtoPoetTypeNames.roots(graphs);
        this.jacksonVersion = jacksonVersion;
        this.hibernateValidatorEnhancement = hibernateValidatorEnhancement;
    }

    public boolean process() {
        boolean result = false;
        for (DtoGraph graph : graphs) {
            DtoAnnotationContract annotationContract = annotationContractsBySource.get(graph.getSource());
            if (annotationContract == null) {
                throw new DtoException(
                        "No frozen DTO annotation contract for \"" + graph.getSource().getPath() + "\""
                );
            }
            DtoInterfaceContractResolution interfaceContractResolution =
                    interfaceContractsBySource.get(graph.getSource());
            if (interfaceContractResolution == null) {
                throw new DtoException(
                        "No frozen DTO interface contract for \"" + graph.getSource().getPath() + "\""
                );
            }
            DtoConfigContractResolution configContractResolution =
                    configContractsBySource.get(graph.getSource());
            if (configContractResolution == null) {
                throw new DtoException(
                        "No frozen DTO config contract for \"" + graph.getSource().getPath() + "\""
                );
            }
            for (site.addzero.lsi.jimmer.dto.DtoType rootType :
                    DtoGenerationExtensionsKt.rootTypesInDeclarationOrder(graph)) {
                new DtoGenerator(
                        context,
                        graph,
                        rootType,
                        annotationContract,
                        interfaceContractResolution,
                        configContractResolution,
                        immutableSchema,
                        lsiWorkspace,
                        batchRootDtoTypeNames,
                        jacksonVersion,
                        hibernateValidatorEnhancement
                ).generate();
                result = true;
            }
        }
        return result;
    }
}
