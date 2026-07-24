package org.babyfish.jimmer.apt.dto;

import org.babyfish.jimmer.Input;
import org.babyfish.jimmer.View;
import org.babyfish.jimmer.apt.Context;
import org.babyfish.jimmer.apt.client.DocMetadata;
import org.babyfish.jimmer.apt.immutable.generator.Constants;
import org.babyfish.jimmer.apt.immutable.meta.ImmutableProp;
import org.babyfish.jimmer.apt.immutable.meta.ImmutableType;
import org.babyfish.jimmer.apt.util.GenericParser;
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames;
import org.babyfish.jimmer.compiler.dto.JimmerDtoJacksonVersion;
import org.babyfish.jimmer.dto.compiler.*;
import site.addzero.lsi.core.LsiSource;
import site.addzero.lsi.jimmer.ImmutableSchema;
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract;
import site.addzero.lsi.jimmer.dto.DtoGenerationExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoGraph;
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution;
import site.addzero.lsi.model.LsiWorkspace;
import site.addzero.lsi.poet.LsiPoetTypeName;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
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

    private final ImmutableSchema immutableSchema;

    private final LsiWorkspace lsiWorkspace;

    private final Map<site.addzero.lsi.jimmer.dto.DtoTypeId, LsiPoetTypeName> batchRootDtoTypeNames;

    private final JimmerDtoJacksonVersion jacksonVersion;

    public DtoProcessor(
            Context context,
            Elements elements,
            Collection<DtoFile> dtoFiles,
            DtoModifier defaultNullableInputModifier,
            Collection<DtoGraph> graphs,
            Map<LsiSource, DtoAnnotationContract> annotationContractsBySource,
            Map<LsiSource, DtoInterfaceContractResolution> interfaceContractsBySource,
            ImmutableSchema immutableSchema,
            LsiWorkspace lsiWorkspace,
            JimmerDtoJacksonVersion jacksonVersion
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
        this.immutableSchema = immutableSchema;
        this.lsiWorkspace = lsiWorkspace;
        this.batchRootDtoTypeNames = JimmerDtoPoetTypeNames.roots(graphs);
        this.jacksonVersion = jacksonVersion;
    }

    public boolean process() {
        return generateDtoTypes(parseDtoTypes());
    }

    private List<DtoType<ImmutableType, ImmutableProp>> parseDtoTypes() {
        List<AptDtoCompiler> compilers = new ArrayList<>();
        AptDtoCompiler compiler;

        for (DtoFile dtoFile : dtoFiles) {
            try {
                compiler = new AptDtoCompiler(dtoFile, context, elements, defaultNullableInputModifier);
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

    private DtoTypeInfo<ImmutableType> resolveDtoType(String qualifiedName) {
        TypeElement typeElement = elements.getTypeElement(qualifiedName);
        if (typeElement == null) {
            return null;
        }
        Types types = context.getTypes();
        TypeMirror type = types.erasure(typeElement.asType());
        DtoTypeKind kind;
        String superName;
        if (types.isSubtype(type, types.erasure(elements.getTypeElement(Input.class.getName()).asType()))) {
            kind = DtoTypeKind.INPUT;
            superName = Input.class.getName();
        } else if (types.isSubtype(type, types.erasure(elements.getTypeElement(View.class.getName()).asType()))) {
            kind = DtoTypeKind.VIEW;
            superName = View.class.getName();
        } else if (types.isSubtype(
                type,
                types.erasure(
                        elements.getTypeElement(Constants.JSPECIFICATION_CLASS_NAME.canonicalName()).asType()
                )
        )) {
            kind = DtoTypeKind.SPECIFICATION;
            superName = Constants.JSPECIFICATION_CLASS_NAME.canonicalName();
        } else {
            return null;
        }
        TypeMirror baseTypeMirror = new GenericParser(
                "reusable DTO",
                typeElement,
                superName
        ).parse().arguments.get(0);
        ImmutableType baseType = context.getImmutableType(baseTypeMirror);
        if (baseType == null) {
            throw new DtoException(
                    "The entity type argument of reusable DTO type \"" +
                            qualifiedName +
                            "\" is not an immutable type"
            );
        }
        return new DtoTypeInfo<>(baseType, kind);
    }

    private boolean generateDtoTypes(List<DtoType<ImmutableType, ImmutableProp>> dtoTypes) {
        boolean result = false;
        DocMetadata docMetadata = new DocMetadata(context);
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
            new DtoGenerator(
                    context,
                    docMetadata,
                    dtoType,
                    graph,
                    DtoGenerationExtensionsKt.rootType(graph, qualifiedName),
                    annotationContract,
                    interfaceContractResolution,
                    immutableSchema,
                    lsiWorkspace,
                    batchRootDtoTypeNames,
                    jacksonVersion
            ).generate();
            result = true;
        }
        return result;
    }
}
