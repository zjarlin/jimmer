package org.babyfish.jimmer.apt.dto;

import org.babyfish.jimmer.apt.Context;
import org.babyfish.jimmer.apt.immutable.meta.ImmutableProp;
import org.babyfish.jimmer.apt.immutable.meta.ImmutableType;
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames;
import org.babyfish.jimmer.compiler.dto.JimmerDtoJacksonVersion;
import org.babyfish.jimmer.dto.compiler.*;
import site.addzero.lsi.core.LsiLanguage;
import site.addzero.lsi.core.LsiSource;
import site.addzero.lsi.jimmer.ImmutableSchema;
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract;
import site.addzero.lsi.jimmer.dto.DtoCompilerExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution;
import site.addzero.lsi.jimmer.dto.DtoGenerationExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoGraph;
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution;
import site.addzero.lsi.jimmer.dto.DtoTypeInfoExtensionsKt;
import site.addzero.lsi.jimmer.dto.LsiDtoTypeRegistry;
import site.addzero.lsi.model.LsiWorkspace;
import site.addzero.lsi.poet.LsiPoetTypeName;

import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DtoProcessor {

    private final Context context;

    private final Elements elements;

    private final Collection<DtoFile> dtoFiles;

    private final DtoModifier defaultNullableInputModifier;

    private final Map<String, DtoGraph> graphBySourcePath;

    private final Map<LsiSource, DtoAnnotationContract> annotationContractsBySource;

    private final Map<LsiSource, DtoInterfaceContractResolution> interfaceContractsBySource;

    private final Map<LsiSource, DtoConfigContractResolution> configContractsBySource;

    private final ImmutableSchema immutableSchema;

    private final LsiWorkspace lsiWorkspace;

    private final LsiDtoTypeRegistry dtoTypeRegistry;

    private final Map<site.addzero.lsi.jimmer.dto.DtoTypeId, LsiPoetTypeName> batchRootDtoTypeNames;

    private final JimmerDtoJacksonVersion jacksonVersion;

    private final boolean hibernateValidatorEnhancement;

    public DtoProcessor(
            Context context,
            Elements elements,
            Collection<DtoFile> dtoFiles,
            DtoModifier defaultNullableInputModifier,
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
        this.elements = elements;
        this.dtoFiles = dtoFiles;
        this.defaultNullableInputModifier = defaultNullableInputModifier;
        this.graphBySourcePath = new LinkedHashMap<>();
        for (DtoGraph graph : graphs) {
            DtoGraph conflict = graphBySourcePath.put(graph.getSource().getPath(), graph);
            if (conflict != null) {
                throw new IllegalArgumentException(
                        "Duplicate frozen DTO graph source path: " + graph.getSource().getPath()
                );
            }
        }
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
        this.dtoTypeRegistry = DtoCompilerExtensionsKt.toLsiDtoTypeRegistry(immutableSchema, lsiWorkspace);
        this.batchRootDtoTypeNames = JimmerDtoPoetTypeNames.roots(graphs);
        this.jacksonVersion = jacksonVersion;
        this.hibernateValidatorEnhancement = hibernateValidatorEnhancement;
    }

    public boolean process() {
        return generateDtoTypes(parseDtoTypes());
    }

    private List<DtoType<ImmutableType, ImmutableProp>> parseDtoTypes() {
        List<AptDtoCompiler> compilers = new ArrayList<>();
        AptDtoCompiler compiler;

        for (DtoFile dtoFile : dtoFiles) {
            try {
                compiler = new AptDtoCompiler(
                        dtoFile,
                        context,
                        elements,
                        defaultNullableInputModifier,
                        immutableSchema
                );
            } catch (DtoAstException ex) {
                throw new DtoException(
                        "Failed to parse \"" +
                                dtoFile.getSourcePath() +
                                "\": " +
                                ex.getMessage(),
                        ex
                );
            } catch (Throwable ex) {
                throw new DtoException(
                        "Failed to read \"" +
                                dtoFile.getSourcePath() +
                                "\": " +
                                ex.getMessage(),
                        ex
                );
            }
            compilers.add(compiler);
        }
        List<DtoType<ImmutableType, ImmutableProp>> dtoTypes = DtoCompiler
                .compileAll(compilers, context::includeDtoTarget)
                .values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        DtoTypeLinker.link(dtoTypes, this::resolveDtoType);
        return dtoTypes;
    }

    private DtoTypeInfo resolveDtoType(String qualifiedName) {
        return DtoTypeInfoExtensionsKt.resolveDtoTypeInfo(
                dtoTypeRegistry,
                qualifiedName,
                LsiLanguage.JAVA
        );
    }

    private boolean generateDtoTypes(List<DtoType<ImmutableType, ImmutableProp>> dtoTypes) {
        boolean result = false;
        for (DtoType<ImmutableType, ImmutableProp> dtoType : dtoTypes) {
            DtoGraph graph = graphBySourcePath.get(dtoType.getDtoFile().getSourcePath());
            if (graph == null) {
                throw new DtoException(
                        "No frozen DTO graph for \"" + dtoType.getDtoFile().getSourcePath() + "\""
                );
            }
            String qualifiedName = dtoType.getQualifiedName();
            if (qualifiedName == null) {
                throw new DtoException("Root DTO type must have a qualified name");
            }
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
            new DtoGenerator(
                    context,
                    dtoType,
                    graph,
                    DtoGenerationExtensionsKt.rootType(graph, qualifiedName),
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
        return result;
    }
}
