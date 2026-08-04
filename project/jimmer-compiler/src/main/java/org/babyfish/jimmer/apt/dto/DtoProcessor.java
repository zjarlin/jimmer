package org.babyfish.jimmer.apt.dto;

import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames;
import org.babyfish.jimmer.compiler.dto.JimmerDtoRendererOptions;
import site.addzero.lsi.codegen.ArtifactAggregationMode;
import site.addzero.lsi.codegen.ArtifactEmissionMode;
import site.addzero.lsi.codegen.ArtifactKind;
import site.addzero.lsi.codegen.GeneratedArtifact;
import site.addzero.lsi.core.LsiSource;
import site.addzero.lsi.core.LsiSymbolId;
import site.addzero.lsi.jimmer.ImmutableSchema;
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract;
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution;
import site.addzero.lsi.jimmer.dto.DtoGenerationExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoGraph;
import site.addzero.lsi.jimmer.dto.DtoGraphExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution;
import site.addzero.lsi.model.LsiWorkspace;
import site.addzero.lsi.poet.LsiPoetTypeName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DtoProcessor {

    private final List<DtoGraph> graphs;

    private final Map<LsiSource, DtoAnnotationContract> annotationContractsBySource;

    private final Map<LsiSource, DtoInterfaceContractResolution> interfaceContractsBySource;

    private final Map<LsiSource, DtoConfigContractResolution> configContractsBySource;

    private final ImmutableSchema immutableSchema;

    private final LsiWorkspace lsiWorkspace;

    private final Map<site.addzero.lsi.jimmer.dto.DtoTypeId, LsiPoetTypeName> batchRootDtoTypeNames;

    private final JimmerDtoRendererOptions rendererOptions;

    public DtoProcessor(
            Collection<DtoGraph> graphs,
            Map<LsiSource, DtoAnnotationContract> annotationContractsBySource,
            Map<LsiSource, DtoInterfaceContractResolution> interfaceContractsBySource,
            Map<LsiSource, DtoConfigContractResolution> configContractsBySource,
            ImmutableSchema immutableSchema,
            LsiWorkspace lsiWorkspace,
            JimmerDtoRendererOptions rendererOptions
    ) {
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
        this.rendererOptions = rendererOptions;
    }

    public List<GeneratedArtifact> process() {
        List<GeneratedArtifact> artifacts = new ArrayList<>();
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
            Set<LsiSymbolId> dependencySymbols = DtoGraphExtensionsKt.dependencySymbols(graph);
            for (site.addzero.lsi.jimmer.dto.DtoType rootType :
                    DtoGenerationExtensionsKt.rootTypesInDeclarationOrder(graph)) {
                LsiPoetTypeName rootTypeName = JimmerDtoPoetTypeNames.rootTypeName(
                        rootType,
                        batchRootDtoTypeNames
                );
                String content = new DtoGenerator(
                        graph,
                        rootType,
                        annotationContract,
                        interfaceContractResolution,
                        configContractResolution,
                        immutableSchema,
                        lsiWorkspace,
                        batchRootDtoTypeNames,
                        rendererOptions
                ).generate();
                artifacts.add(GeneratedArtifact.Companion.source(
                        ArtifactKind.JAVA_SOURCE,
                        rootTypeName.getCanonicalName(),
                        content,
                        ArtifactAggregationMode.AGGREGATING,
                        ArtifactEmissionMode.IMMEDIATE,
                        Collections.emptySet(),
                        Collections.singleton(graph.getSource()),
                        dependencySymbols,
                        DtoGraphExtensionsKt.dependencySources(graph)
                ));
            }
        }
        return Collections.unmodifiableList(artifacts);
    }
}
