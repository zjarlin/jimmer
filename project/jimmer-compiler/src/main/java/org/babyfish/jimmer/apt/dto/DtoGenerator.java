package org.babyfish.jimmer.apt.dto;

import com.squareup.javapoet.*;
import org.babyfish.jimmer.apt.Context;
import org.babyfish.jimmer.apt.GeneratorException;
import org.babyfish.jimmer.apt.immutable.generator.Constants;
import org.babyfish.jimmer.apt.util.GeneratedAnnotation;
import org.babyfish.jimmer.client.ApiIgnore;
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames;
import org.babyfish.jimmer.compiler.dto.JimmerDtoJacksonVersion;
import org.babyfish.jimmer.compiler.render.apt.AptDtoAccessorRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoBaseContractRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoBaseValueRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoDescriptionRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoDraftWriteRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoEqualityRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoFoldDraftApplyRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoFoldValueRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoHibernateValidatorRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoInputBuilderRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptImmutableTypeNameRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoJacksonPolymorphismRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoLoadedStateRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoMetadataFetcherRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoPolymorphicBranchRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoPolymorphicInputRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoPolymorphicMetadataConverterRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoPropAnnotationRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoSerializerRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoSpecificationRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoToStringRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoTypeAnnotationRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoTypeRefRenderer;
import org.babyfish.jimmer.impl.util.StringUtil;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import site.addzero.lsi.core.LsiLanguage;
import site.addzero.lsi.jimmer.ImmutableDraftNamingExtensionsKt;
import site.addzero.lsi.jimmer.ImmutableSchema;
import site.addzero.lsi.jimmer.ImmutableSchemaExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoAccessorExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract;
import site.addzero.lsi.jimmer.dto.DtoBaseProp;
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution;
import site.addzero.lsi.jimmer.dto.DtoConverterExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoDraftWriteExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoGenerationExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoGeneratedBaseContractKind;
import site.addzero.lsi.jimmer.dto.DtoGeneratedValueTypeExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoGraph;
import site.addzero.lsi.jimmer.dto.DtoInterfaceContract;
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution;
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranchAnnotationExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoTypeId;
import site.addzero.lsi.model.LsiDeclaredType;
import site.addzero.lsi.model.LsiTypeRef;
import site.addzero.lsi.model.LsiWorkspace;
import site.addzero.lsi.poet.LsiPoetTypeName;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.*;

public class DtoGenerator {

    private static final String[] EMPTY_STR_ARR = new String[0];

    public final Context ctx;

    private final DtoGraph lsiGraph;

    private final site.addzero.lsi.jimmer.dto.DtoType lsiDtoType;

    private final DtoAnnotationContract annotationContract;

    private final DtoInterfaceContractResolution interfaceContractResolution;

    private final DtoConfigContractResolution configContractResolution;

    private final ImmutableSchema immutableSchema;

    private final LsiWorkspace lsiWorkspace;

    private final Map<DtoTypeId, LsiPoetTypeName> batchRootDtoTypeNames;

    private final String generatedDtoPackageName;

    private final List<String> generatedDtoSimpleNames;

    private final JimmerDtoJacksonVersion jacksonVersion;

    private final boolean hibernateValidatorEnhancement;

    private final DtoGenerator parent;

    private final String innerClassName;

    private final Map<LsiPoetTypeName, DtoTypeId> generatedDtoTypeIdsByTypeName;

    private final Map<DtoTypeId, LsiPoetTypeName> generatedDtoTypeNames;

    private final Set<DtoTypeId> locallyGeneratedDtoTypeIds = new HashSet<>();

    private final Map<DtoTypeId, LsiPoetTypeName> readOnlyGeneratedDtoTypeNames;

    @Nullable
    private final TypeName polymorphicSuperInterfaceName;

    private final boolean polymorphicBranch;

    private final site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch lsiPolymorphicBranch;

    private final Set<String> interfaceMethodNames;

    private TypeSpec.Builder typeBuilder;

    public DtoGenerator(
            Context ctx,
            DtoGraph lsiGraph,
            site.addzero.lsi.jimmer.dto.DtoType lsiDtoType,
            DtoAnnotationContract annotationContract,
            DtoInterfaceContractResolution interfaceContractResolution,
            DtoConfigContractResolution configContractResolution,
            ImmutableSchema immutableSchema,
            LsiWorkspace lsiWorkspace,
            Map<DtoTypeId, LsiPoetTypeName> batchRootDtoTypeNames,
            JimmerDtoJacksonVersion jacksonVersion,
            boolean hibernateValidatorEnhancement
    ) {
        this(
                ctx,
                lsiGraph,
                lsiDtoType,
                annotationContract,
                interfaceContractResolution,
                configContractResolution,
                immutableSchema,
                lsiWorkspace,
                batchRootDtoTypeNames,
                jacksonVersion,
                hibernateValidatorEnhancement,
                null,
                null,
                null,
                null
        );
    }

    private DtoGenerator(
            Context ctx,
            site.addzero.lsi.jimmer.dto.DtoType lsiDtoType,
            DtoGenerator parent,
            String innerClassName
    ) {
        this(
                ctx,
                parent.lsiGraph,
                lsiDtoType,
                parent.annotationContract,
                parent.interfaceContractResolution,
                parent.configContractResolution,
                parent.immutableSchema,
                parent.lsiWorkspace,
                parent.batchRootDtoTypeNames,
                parent.jacksonVersion,
                parent.hibernateValidatorEnhancement,
                parent,
                innerClassName,
                null,
                null
        );
    }

    private DtoGenerator(
            Context ctx,
            DtoGraph lsiGraph,
            site.addzero.lsi.jimmer.dto.DtoType lsiDtoType,
            DtoAnnotationContract annotationContract,
            DtoInterfaceContractResolution interfaceContractResolution,
            DtoConfigContractResolution configContractResolution,
            ImmutableSchema immutableSchema,
            LsiWorkspace lsiWorkspace,
            Map<DtoTypeId, LsiPoetTypeName> batchRootDtoTypeNames,
            JimmerDtoJacksonVersion jacksonVersion,
            boolean hibernateValidatorEnhancement,
            DtoGenerator parent,
            String innerClassName,
            @Nullable TypeName polymorphicSuperInterfaceName,
            site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch lsiPolymorphicBranch
    ) {
        if ((parent == null) != (innerClassName == null)) {
            throw new IllegalArgumentException("The nullity values of `parent` and `innerClassName` must be same");
        }
        this.ctx = ctx;
        this.lsiGraph = lsiGraph;
        this.lsiDtoType = lsiDtoType;
        this.parent = parent;
        this.innerClassName = innerClassName;
        this.annotationContract = parent != null ? parent.annotationContract : annotationContract;
        this.interfaceContractResolution = parent != null ?
                parent.interfaceContractResolution :
                interfaceContractResolution;
        this.configContractResolution = parent != null ?
                parent.configContractResolution :
                configContractResolution;
        this.immutableSchema = parent != null ? parent.immutableSchema : immutableSchema;
        this.lsiWorkspace = parent != null ? parent.lsiWorkspace : lsiWorkspace;
        this.batchRootDtoTypeNames = parent != null ?
                parent.batchRootDtoTypeNames :
                Collections.unmodifiableMap(new LinkedHashMap<>(batchRootDtoTypeNames));
        if (parent != null) {
            this.generatedDtoPackageName = parent.generatedDtoPackageName;
            List<String> simpleNames = new ArrayList<>(parent.generatedDtoSimpleNames);
            simpleNames.add(innerClassName);
            this.generatedDtoSimpleNames = Collections.unmodifiableList(simpleNames);
        } else {
            LsiPoetTypeName generatedDtoTypeName = JimmerDtoPoetTypeNames.rootTypeName(
                    lsiDtoType,
                    this.batchRootDtoTypeNames
            );
            this.generatedDtoPackageName = generatedDtoTypeName.getPackageName();
            this.generatedDtoSimpleNames = Collections.unmodifiableList(
                    new ArrayList<>(generatedDtoTypeName.getSimpleNames())
            );
        }
        this.generatedDtoTypeIdsByTypeName = parent != null ?
                parent.generatedDtoTypeIdsByTypeName :
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(
                                JimmerDtoPoetTypeNames.forRoot(
                                        lsiGraph,
                                        lsiDtoType,
                                        this.batchRootDtoTypeNames
                                )
                        )
                );
        this.generatedDtoTypeNames = new LinkedHashMap<>(
                parent != null ?
                        parent.generatedDtoTypeNames :
                        this.batchRootDtoTypeNames
        );
        this.readOnlyGeneratedDtoTypeNames = Collections.unmodifiableMap(generatedDtoTypeNames);
        this.jacksonVersion = parent != null ? parent.jacksonVersion : jacksonVersion;
        this.hibernateValidatorEnhancement = parent != null ?
                parent.hibernateValidatorEnhancement :
                hibernateValidatorEnhancement;
        this.polymorphicSuperInterfaceName = polymorphicSuperInterfaceName;
        this.polymorphicBranch = lsiPolymorphicBranch != null;
        this.lsiPolymorphicBranch = lsiPolymorphicBranch;
        if (lsiPolymorphicBranch != null) {
            if (parent == null) {
                throw new IllegalArgumentException("Frozen DTO polymorphic branch has no direct parent");
            }
            DtoPolymorphicBranchAnnotationExtensionsKt.generatedPolymorphicDtoBranchOrder(
                    lsiPolymorphicBranch,
                    parent.lsiDtoType
            );
        }
        DtoInterfaceContract interfaceContract = DtoInterfaceContractExtensionsKt.contractFor(
                this.interfaceContractResolution,
                lsiDtoType
        );
        this.interfaceMethodNames = DtoInterfaceContractExtensionsKt.requiredAccessorNames(interfaceContract);
        registerGeneratedDtoTypeName();
    }

    public void generate() {
        if (DtoAccessorExtensionsKt.isPolymorphicRoot(lsiDtoType)) {
            generatePolymorphic();
            return;
        }
        String simpleName = getSimpleName();
        typeBuilder = TypeSpec
                .classBuilder(simpleName)
                .addModifiers(Modifier.PUBLIC);
        if (polymorphicBranch) {
            assert parent != null;
            assert lsiPolymorphicBranch != null;
            typeBuilder.addModifiers(Modifier.FINAL);
            typeBuilder.addAnnotation(
                    AptDtoPolymorphicBranchRenderer.render(
                            parent.lsiDtoType,
                            lsiPolymorphicBranch,
                            getGeneratedDtoPackageName(),
                            parent.getGeneratedDtoSimpleNames()
                    )
            );
        }
        if (polymorphicSuperInterfaceName != null) {
            typeBuilder.addSuperinterface(polymorphicSuperInterfaceName);
        } else {
            DtoGeneratedBaseContractKind baseContractKind = generatedBaseContractKind();
            if (baseContractKind != null) {
                typeBuilder.addSuperinterface(generatedBaseContractTypeName(baseContractKind));
            }
        }
        for (site.addzero.lsi.jimmer.dto.DtoTypeRef typeRef : lsiDtoType.getSuperInterfaces()) {
            typeBuilder.addSuperinterface(AptDtoTypeRefRenderer.render(typeRef, lsiWorkspace));
        }
        if (isHibernateValidatorEnhancementRequired()) {
            typeBuilder.addSuperinterface(
                    AptDtoHibernateValidatorRenderer.renderEnhancedBeanType(lsiWorkspace)
            );
        }
        if (parent == null) {
            typeBuilder.addAnnotation(
                    GeneratedAnnotation.generatedAnnotation(lsiGraph.getSource().getPath())
            );
        } else {
            typeBuilder.addAnnotation(GeneratedAnnotation.generatedAnnotation());
        }
        if (isSerializerRequired()) {
            typeBuilder.addAnnotation(
                    AnnotationSpec
                            .builder(ctx.getJacksonTypes().jsonSerialize)
                            .addMember(
                                    "using",
                                    "$T.class",
                                    getDtoClassName("Serializer")
                            )
                            .build()
            );
        }
        if (isBuildRequired()) {
            typeBuilder.addAnnotation(
                    AnnotationSpec
                            .builder(ctx.getJacksonTypes().jsonDeserialize)
                            .addMember(
                                    "builder",
                                    "$T.class",
                                    getDtoClassName("Builder")
                            )
                            .build()
            );
        }
        AnnotationSpec description = AptDtoDescriptionRenderer.render(lsiDtoType);
        if (description != null) {
            typeBuilder.addAnnotation(description);
        }
        typeBuilder.addAnnotations(
                AptDtoTypeAnnotationRenderer.render(lsiDtoType, annotationContract, lsiWorkspace)
        );
        site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch lsiPolymorphicBranch =
                currentLsiPolymorphicBranchOrNull();
        if (lsiPolymorphicBranch != null) {
            assert parent != null;
            AnnotationSpec polymorphicTypeName = AptDtoJacksonPolymorphismRenderer.renderBranchTypeName(
                    parent.lsiDtoType,
                    lsiPolymorphicBranch,
                    lsiGraph,
                    immutableSchema,
                    annotationContract,
                    getGeneratedDtoPackageName(),
                    parent.getGeneratedDtoSimpleNames()
            );
            if (polymorphicTypeName != null) {
                typeBuilder.addAnnotation(polymorphicTypeName);
            }
        }
        if (innerClassName != null) {
            typeBuilder.addModifiers(Modifier.STATIC);
            addMembers();
        } else {
            addMembers();
        }
        if (innerClassName != null) {
            assert parent != null;
            parent.typeBuilder.addType(typeBuilder.build());
        } else {
            try {
                JavaFile
                        .builder(
                                generatedDtoPackageName,
                                typeBuilder.build()
                        )
                        .indent("    ")
                        .build()
                        .writeTo(ctx.getFiler());
            } catch (IOException ex) {
                throw new GeneratorException(
                        String.format(
                                "Cannot generate dto type '%s' for '%s'",
                                getSimpleName(),
                                immutableBaseType().getQualifiedName()
                        ),
                        ex
                );
            }
        }
    }

    private void generatePolymorphic() {
        DtoGeneratedBaseContractKind baseContractKind = generatedBaseContractKind();
        if (baseContractKind != DtoGeneratedBaseContractKind.ENTITY_INPUT &&
                baseContractKind != DtoGeneratedBaseContractKind.ENTITY_VIEW) {
            throw new GeneratorException(
                    "Polymorphic DTO generation is only supported for entity types",
                    null
            );
        }
        String simpleName = getSimpleName();
        typeBuilder = TypeSpec
                .interfaceBuilder(simpleName)
                .addModifiers(Modifier.PUBLIC);
        if (DtoAccessorExtensionsKt.isSealed(lsiDtoType)) {
            typeBuilder.addModifiers(sealedModifier());
        }
        typeBuilder.addSuperinterface(
                generatedBaseContractTypeName(baseContractKind)
        );
        for (site.addzero.lsi.jimmer.dto.DtoTypeRef typeRef : lsiDtoType.getSuperInterfaces()) {
            typeBuilder.addSuperinterface(AptDtoTypeRefRenderer.render(typeRef, lsiWorkspace));
        }
        if (isHibernateValidatorEnhancementRequired()) {
            typeBuilder.addSuperinterface(
                    AptDtoHibernateValidatorRenderer.renderEnhancedBeanType(lsiWorkspace)
            );
        }
        if (parent == null) {
            typeBuilder.addAnnotation(
                    GeneratedAnnotation.generatedAnnotation(lsiGraph.getSource().getPath())
            );
        } else {
            typeBuilder.addAnnotation(GeneratedAnnotation.generatedAnnotation());
            typeBuilder.addModifiers(Modifier.STATIC);
        }
        AnnotationSpec description = AptDtoDescriptionRenderer.render(lsiDtoType);
        if (description != null) {
            typeBuilder.addAnnotation(description);
        }
        typeBuilder.addAnnotations(
                AptDtoTypeAnnotationRenderer.render(lsiDtoType, annotationContract, lsiWorkspace)
        );
        site.addzero.lsi.jimmer.dto.DtoPolymorphism polymorphism = Objects.requireNonNull(
                lsiDtoType.getPolymorphism(),
                "Frozen DTO polymorphic root has no polymorphism: " + getDtoClassName()
        );
        typeBuilder.addAnnotations(
                AptDtoJacksonPolymorphismRenderer.renderRootAnnotations(
                        lsiDtoType,
                        lsiGraph,
                        immutableSchema,
                        annotationContract,
                        getGeneratedDtoPackageName(),
                        getGeneratedDtoSimpleNames()
                )
        );
        for (site.addzero.lsi.jimmer.dto.DtoProp prop :
                DtoAccessorExtensionsKt.propsInDeclarationOrder(lsiDtoType, lsiGraph)) {
            addAccessorDeclaration(prop);
        }
        generateNestedDtoTypes();

        addPolymorphicMetadata();
        ClassName superInterfaceName = getDtoClassName();
        site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch defaultBranch =
                DtoGenerationExtensionsKt.defaultBranch(polymorphism);
        if (defaultBranch != null) {
            generatePolymorphicBranch(defaultBranch, superInterfaceName);
        }
        for (site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch branch :
                DtoGenerationExtensionsKt.typeBranchesInDeclarationOrder(polymorphism)) {
            generatePolymorphicBranch(branch, superInterfaceName);
        }

        if (innerClassName != null) {
            assert parent != null;
            parent.typeBuilder.addType(typeBuilder.build());
        } else {
            try {
                JavaFile
                        .builder(
                                generatedDtoPackageName,
                                typeBuilder.build()
                        )
                        .indent("    ")
                        .build()
                        .writeTo(ctx.getFiler());
            } catch (IOException ex) {
                throw new GeneratorException(
                        String.format(
                                "Cannot generate dto type '%s' for '%s'",
                                getSimpleName(),
                                immutableBaseType().getQualifiedName()
                        ),
                        ex
                );
            }
        }
    }

    @Nullable
    private DtoGeneratedBaseContractKind generatedBaseContractKind() {
        return DtoAccessorExtensionsKt.generatedBaseContractKind(lsiDtoType, immutableSchema);
    }

    private TypeName generatedBaseContractTypeName(DtoGeneratedBaseContractKind kind) {
        if (generatedBaseContractKind() != kind) {
            throw new AssertionError("Unexpected DTO base contract kind: " + kind);
        }
        return AptDtoBaseContractRenderer.render(lsiDtoType, immutableSchema, lsiWorkspace);
    }

    private void generatePolymorphicBranch(
            site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch branch,
            TypeName superInterfaceName
    ) {
        new DtoGenerator(
                ctx,
                lsiGraph,
                DtoGenerationExtensionsKt.mergedType(branch, lsiGraph),
                annotationContract,
                interfaceContractResolution,
                configContractResolution,
                immutableSchema,
                lsiWorkspace,
                batchRootDtoTypeNames,
                jacksonVersion,
                hibernateValidatorEnhancement,
                this,
                branch.getClassName(),
                superInterfaceName,
                branch
        ).generate();
    }

    private site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch currentLsiPolymorphicBranchOrNull() {
        if (!polymorphicBranch) {
            return null;
        }
        if (lsiPolymorphicBranch == null) {
            throw new DtoException("Frozen DTO polymorphic branch does not match generated branch");
        }
        try {
            return DtoGenerationExtensionsKt.requireGeneratedMergedType(
                    lsiPolymorphicBranch,
                    lsiGraph,
                    lsiDtoType
            );
        } catch (IllegalArgumentException ex) {
            throw new DtoException(
                    "Frozen DTO polymorphic branch does not match generated branch",
                    ex
            );
        }
    }

    public String getSimpleName() {
        return generatedDtoSimpleNames.get(generatedDtoSimpleNames.size() - 1);
    }

    private Modifier sealedModifier() {
        try {
            return Modifier.valueOf("SEALED");
        } catch (IllegalArgumentException ex) {
            throw new GeneratorException(
                    "The modifier 'sealed' requires the annotation processor to run on Java 17 or later",
                    ex
            );
        }
    }

    private ClassName getDtoClassName() {
        return getDtoClassName(null);
    }

    ClassName getDtoClassName(String nestedClassName) {
        List<String> simpleNames = generatedDtoSimpleNames;
        if (nestedClassName != null) {
            simpleNames = new ArrayList<>(simpleNames);
            simpleNames.add(nestedClassName);
        }
        return ClassName.get(
                generatedDtoPackageName,
                simpleNames.get(0),
                simpleNames.subList(1, simpleNames.size()).toArray(EMPTY_STR_ARR)
        );
    }

    private String getGeneratedDtoPackageName() {
        return generatedDtoPackageName;
    }

    private List<String> getGeneratedDtoSimpleNames() {
        return generatedDtoSimpleNames;
    }

    private Map<DtoTypeId, LsiPoetTypeName> getGeneratedDtoTypeNames() {
        return readOnlyGeneratedDtoTypeNames;
    }

    private void registerGeneratedDtoTypeName() {
        registerGeneratedDtoTypeName(lsiDtoType, getGeneratedDtoSimpleNames());
    }

    private void registerGeneratedDtoTypeName(
            site.addzero.lsi.jimmer.dto.DtoType type,
            List<String> simpleNames
    ) {
        LsiPoetTypeName typeName = JimmerDtoPoetTypeNames.create(
                getGeneratedDtoPackageName(),
                simpleNames
        );
        JimmerDtoPoetTypeNames.requirePlanned(
                lsiGraph,
                type,
                generatedDtoTypeIdsByTypeName,
                typeName
        );
        JimmerDtoPoetTypeNames.register(
                lsiGraph,
                type,
                generatedDtoTypeNames,
                locallyGeneratedDtoTypeIds,
                typeName
        );
    }

    private void addMembers() {

        boolean isSpecification = DtoAccessorExtensionsKt.isSpecification(lsiDtoType);
        if (!isSpecification && !polymorphicBranch) {
            addMetadata();
        }

        if (!isSpecification) {
            for (DtoBaseProp prop : DtoAccessorExtensionsKt.basePropsInDeclarationOrder(lsiDtoType, lsiGraph)) {
                addAccessorField(prop);
            }
            for (site.addzero.lsi.jimmer.dto.DtoFoldProp prop :
                    DtoGenerationExtensionsKt.foldPropsInDeclarationOrder(lsiDtoType, lsiGraph)) {
                addFoldNullGuardAccessorField(prop);
            }
        }
        for (site.addzero.lsi.jimmer.dto.DtoProp prop :
                DtoAccessorExtensionsKt.propsInDeclarationOrder(lsiDtoType, lsiGraph)) {
            addField(prop);
            addStateField(prop);
        }

        addDefaultConstructor();
        if (!isSpecification) {
            addConverterConstructor();
        }

        for (site.addzero.lsi.jimmer.dto.DtoProp prop :
                DtoAccessorExtensionsKt.propsInDeclarationOrder(lsiDtoType, lsiGraph)) {
            addAccessors(prop);
        }

        if (isSpecification) {
            addEntityType();
            addApplyTo();
        } else {
            addApplyToDraft();
            addToEntity(false);
            addToEntity(true);
        }

        addHashCode();
        addEquals();
        addToString();

        if (isSpecification) {
            for (DtoBaseProp prop : DtoAccessorExtensionsKt.basePropsInDeclarationOrder(lsiDtoType, lsiGraph)) {
                MethodSpec converter = AptDtoSpecificationRenderer.renderConverterOrNull(
                        prop,
                        lsiGraph,
                        immutableSchema,
                        lsiWorkspace
                );
                if (converter != null) {
                    typeBuilder.addMethod(converter);
                }
            }
        }

        generateNestedDtoTypes();

        if (isSerializerRequired()) {
            typeBuilder.addType(
                    AptDtoSerializerRenderer.render(
                            lsiDtoType,
                            lsiGraph,
                            immutableSchema,
                            jacksonVersion,
                            getDtoClassName().packageName(),
                            getDtoClassName().simpleNames()
                    )
            );
        }
        if (isBuildRequired()) {
            typeBuilder.addType(
                    AptDtoInputBuilderRenderer.render(
                            lsiDtoType,
                            lsiGraph,
                            immutableSchema,
                            annotationContract,
                            lsiWorkspace,
                            jacksonVersion,
                            getGeneratedDtoPackageName(),
                            getGeneratedDtoSimpleNames(),
                            getGeneratedDtoTypeNames(),
                            batchRootDtoTypeNames.values()
                    )
            );
        }

        if (isHibernateValidatorEnhancementRequired()) {
            for (MethodSpec method : AptDtoHibernateValidatorRenderer.renderFunctions(
                    lsiDtoType,
                    lsiGraph,
                    immutableSchema,
                    lsiWorkspace
            )) {
                typeBuilder.addMethod(method);
            }
        }
    }

    private void generateNestedDtoTypes() {
        for (DtoBaseProp prop :
                DtoAccessorExtensionsKt.basePropsInDeclarationOrder(lsiDtoType, lsiGraph)) {
            if (polymorphicRootPropOrNull(prop) != null) {
                continue;
            }
            site.addzero.lsi.jimmer.dto.DtoType lsiTargetType =
                    DtoGenerationExtensionsKt.generatedTargetType(prop, lsiGraph);
            if (lsiTargetType == null) {
                continue;
            }
            String childSimpleName = JimmerDtoPoetTypeNames.requireDirectChildSimpleName(
                    JimmerDtoPoetTypeNames.create(
                            getGeneratedDtoPackageName(),
                            getGeneratedDtoSimpleNames()
                    ),
                    lsiTargetType,
                    generatedDtoTypeIdsByTypeName
            );
            List<String> childSimpleNames = new ArrayList<>(getGeneratedDtoSimpleNames());
            childSimpleNames.add(childSimpleName);
            registerGeneratedDtoTypeName(lsiTargetType, childSimpleNames);
            new DtoGenerator(
                    ctx,
                    lsiTargetType,
                    this,
                    childSimpleName
            ).generate();
        }
        for (site.addzero.lsi.jimmer.dto.DtoFoldProp prop :
                DtoGenerationExtensionsKt.foldPropsInDeclarationOrder(lsiDtoType, lsiGraph)) {
            if (polymorphicRootPropOrNull(prop) != null) {
                continue;
            }
            site.addzero.lsi.jimmer.dto.DtoType lsiTargetType =
                    DtoGenerationExtensionsKt.generatedTargetType(prop, lsiGraph);
            String childSimpleName = JimmerDtoPoetTypeNames.requireDirectChildSimpleName(
                    JimmerDtoPoetTypeNames.create(
                            getGeneratedDtoPackageName(),
                            getGeneratedDtoSimpleNames()
                    ),
                    lsiTargetType,
                    generatedDtoTypeIdsByTypeName
            );
            List<String> childSimpleNames = new ArrayList<>(getGeneratedDtoSimpleNames());
            childSimpleNames.add(childSimpleName);
            registerGeneratedDtoTypeName(lsiTargetType, childSimpleNames);
            new DtoGenerator(
                    ctx,
                    lsiTargetType,
                    this,
                    childSimpleName
            ).generate();
        }
    }

    private void addMetadata() {
        FieldSpec.Builder builder = FieldSpec
                .builder(
                        ParameterizedTypeName.get(
                                org.babyfish.jimmer.apt.immutable.generator.Constants.DTO_METADATA_CLASS_NAME,
                                immutableBaseTypeName(),
                                getDtoClassName()
                        ),
                        "METADATA"
                )
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
        CodeBlock.Builder cb = CodeBlock
                .builder()
                .indent()
                .add("\n")
                .add(
                        "new $T<$T, $T>(\n",
                        org.babyfish.jimmer.apt.immutable.generator.Constants.DTO_METADATA_CLASS_NAME,
                        immutableBaseTypeName(),
                        getDtoClassName()
                )
                .indent()
                .add("$T.class,\n", getDtoClassName())
                .add(
                        "$L",
                        AptDtoMetadataFetcherRenderer.render(
                                lsiDtoType,
                                lsiGraph,
                                immutableSchema,
                                lsiWorkspace,
                                configContractResolution,
                                getGeneratedDtoPackageName(),
                                getGeneratedDtoSimpleNames(),
                                generatedDtoTypeIdsByTypeName,
                                batchRootDtoTypeNames
                        )
                );
        cb
                .add(",\n")
                .add("$T::new\n", getDtoClassName())
                .unindent()
                .unindent()
                .add(")");
        builder.initializer(cb.build());
        typeBuilder.addField(builder.build());
    }

    private void addPolymorphicMetadata() {
        FieldSpec.Builder builder = FieldSpec
                .builder(
                        ParameterizedTypeName.get(
                                org.babyfish.jimmer.apt.immutable.generator.Constants.DTO_METADATA_CLASS_NAME,
                                immutableBaseTypeName(),
                                getDtoClassName()
                        ),
                        "METADATA"
                )
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
        CodeBlock.Builder cb = CodeBlock
                .builder()
                .indent()
                .add("\n")
                .add(
                        "new $T<$T, $T>(\n",
                        org.babyfish.jimmer.apt.immutable.generator.Constants.DTO_METADATA_CLASS_NAME,
                        immutableBaseTypeName(),
                        getDtoClassName()
                )
                .indent()
                .add("$T.class,\n", getDtoClassName())
                .add(
                        "$L",
                        AptDtoMetadataFetcherRenderer.render(
                                lsiDtoType,
                                lsiGraph,
                                immutableSchema,
                                lsiWorkspace,
                                configContractResolution,
                                getGeneratedDtoPackageName(),
                                getGeneratedDtoSimpleNames(),
                                generatedDtoTypeIdsByTypeName,
                                batchRootDtoTypeNames
                        )
                );
        cb.add(",\n").indent();
        cb.add(
                "$L",
                AptDtoPolymorphicMetadataConverterRenderer.render(
                        lsiDtoType,
                        lsiGraph,
                        lsiWorkspace,
                        getGeneratedDtoPackageName(),
                        getGeneratedDtoSimpleNames()
                )
        );
        cb
                .unindent()
                .unindent()
                .add(")");
        builder.initializer(cb.build());
        typeBuilder.addField(builder.build());
    }

    private void addAccessorField(DtoBaseProp prop) {
        if (!DtoAccessorExtensionsKt.requiresDtoPropAccessor(
                prop,
                lsiGraph,
                immutableSchema,
                LsiLanguage.JAVA,
                this::generatedTargetType
        )) {
            return;
        }
        addAccessorField(
                prop,
                accessorFieldName(prop.getName()),
                DtoAccessorExtensionsKt.acceptsNullInAccessor(prop, lsiGraph),
                true
        );
    }

    private void addFoldNullGuardAccessorField(site.addzero.lsi.jimmer.dto.DtoFoldProp prop) {
        DtoBaseProp nullGuardProp = DtoGenerationExtensionsKt.nullGuardProp(prop, lsiGraph);
        if (nullGuardProp != null) {
            addAccessorField(
                    nullGuardProp,
                    foldNullGuardAccessorFieldName(prop.getName()),
                    true,
                    false
            );
        }
    }

    private void addAccessorField(
            DtoBaseProp prop,
            String fieldName,
            boolean acceptNull,
            boolean withConverters
    ) {
        FieldSpec.Builder builder = FieldSpec.builder(
                org.babyfish.jimmer.apt.immutable.generator.Constants.DTO_PROP_ACCESSOR_CLASS_NAME,
                fieldName,
                Modifier.PRIVATE,
                Modifier.STATIC,
                Modifier.FINAL
        );
        builder.initializer(
                AptDtoAccessorRenderer.render(
                        prop,
                        lsiGraph,
                        immutableSchema,
                        lsiWorkspace,
                        acceptNull,
                        withConverters,
                        this::generatedTargetType,
                        generatedDtoTypeIdsByTypeName.keySet()
                )
        );
        typeBuilder.addField(builder.build());
    }

    private void addField(site.addzero.lsi.jimmer.dto.DtoProp prop) {
        TypeName typeName = renderGeneratedValueType(prop);
        if (DtoAccessorExtensionsKt.hasNullableJavaBackingField(prop)) {
            typeName = typeName.box();
        }
        site.addzero.lsi.jimmer.dto.DtoUserProp userProp =
                prop instanceof site.addzero.lsi.jimmer.dto.DtoUserProp ?
                        (site.addzero.lsi.jimmer.dto.DtoUserProp) prop :
                        null;
        FieldSpec.Builder builder = FieldSpec
                .builder(typeName, prop.getName())
                .addModifiers(ctx.getDtoFieldModifier());
        if (userProp != null) {
            String defaultValueText = userProp.getDefaultValueText();
            if (defaultValueText != null) {
                builder.initializer(defaultValueText);
            }
        }
        String doc = doc(prop, true);
        if (doc != null) {
            builder.addJavadoc(doc);
        }
        boolean isBuilderRequired = isBuildRequired();
        if (DtoAccessorExtensionsKt.requiresFixedInputField(prop, lsiGraph)) {
            builder.addAnnotation(org.babyfish.jimmer.apt.immutable.generator.Constants.FIXED_INPUT_FIELD_CLASS_NAME);
        }
        builder.addAnnotations(
                AptDtoPropAnnotationRenderer.renderField(
                        prop,
                        annotationContract,
                        immutableSchema,
                        lsiWorkspace,
                        isBuilderRequired ? ctx.getJacksonTypes().jsonDeserialize.reflectionName() : null
                )
        );
        typeBuilder.addField(builder.build());
    }

    private void addStateField(site.addzero.lsi.jimmer.dto.DtoProp prop) {
        FieldSpec stateField = AptDtoLoadedStateRenderer.renderStorageField(
                prop,
                lsiGraph,
                ctx.getDtoFieldModifier()
        );
        if (stateField != null) {
            typeBuilder.addField(stateField);
        }
    }

    private void addAccessorDeclaration(site.addzero.lsi.jimmer.dto.DtoProp prop) {
        TypeName typeName = renderGeneratedValueType(prop);
        MethodSpec.Builder getterBuilder = MethodSpec
                .methodBuilder(
                        DtoAccessorExtensionsKt.dtoValueAccessorName(
                                prop,
                                LsiLanguage.JAVA,
                                lsiGraph,
                                immutableSchema
                        )
                )
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(typeName);
        AnnotationSpec description = AptDtoDescriptionRenderer.render(prop, lsiGraph);
        if (description != null) {
            getterBuilder.addAnnotation(description);
        }
        if (!typeName.isPrimitive()) {
            if (prop.getNullable()) {
                getterBuilder.addAnnotation(Nullable.class);
            } else {
                getterBuilder.addAnnotation(NonNull.class);
            }
        }
        getterBuilder.addAnnotations(
                AptDtoPropAnnotationRenderer.renderGetter(
                        prop,
                        annotationContract,
                        immutableSchema,
                        lsiWorkspace,
                        null
                )
        );
        typeBuilder.addMethod(getterBuilder.build());
    }

    private void addAccessors(site.addzero.lsi.jimmer.dto.DtoProp prop) {
        TypeName typeName = renderGeneratedValueType(prop);
        String getterName = DtoAccessorExtensionsKt.dtoValueAccessorName(
                prop,
                LsiLanguage.JAVA,
                lsiGraph,
                immutableSchema
        );
        String setterName = DtoAccessorExtensionsKt.javaValueSetterName(
                prop,
                lsiGraph,
                immutableSchema
        );
        String stateFieldName = DtoAccessorExtensionsKt.dtoLoadedStateStorageNameOrNull(
                prop,
                lsiGraph,
                LsiLanguage.JAVA
        );

        MethodSpec.Builder getterBuilder = MethodSpec
                .methodBuilder(getterName)
                .addModifiers(Modifier.PUBLIC)
                .returns(typeName);
        if (interfaceMethodNames.contains(getterName)) {
            getterBuilder.addAnnotation(Override.class);
        }
        AnnotationSpec description = AptDtoDescriptionRenderer.render(prop, lsiGraph);
        if (description != null) {
            getterBuilder.addAnnotation(description);
        }
        if (!typeName.isPrimitive()) {
            if (prop.getNullable()) {
                getterBuilder.addAnnotation(Nullable.class);
            } else {
                getterBuilder.addAnnotation(NonNull.class);
            }
        }
        boolean isBuilderRequired = isBuildRequired();
        getterBuilder.addAnnotations(
                AptDtoPropAnnotationRenderer.renderGetter(
                        prop,
                        annotationContract,
                        immutableSchema,
                        lsiWorkspace,
                        isBuilderRequired ? ctx.getJacksonTypes().jsonDeserialize.reflectionName() : null
                )
        );
        if (stateFieldName != null) {
            getterBuilder.beginControlFlow(
                    "if ($L)",
                    '!' + stateFieldName
            );
            getterBuilder.addStatement(
                    "throw new IllegalStateException($S)",
                    "The property \"" + prop.getName() + "\" is not specified"
            );
            getterBuilder.endControlFlow();
        }
        if (!prop.getNullable() && DtoAccessorExtensionsKt.hasNullableJavaBackingField(prop)) {
            getterBuilder.beginControlFlow(
                    "if ($L == null)",
                    prop.getName()
            );
            if (DtoAccessorExtensionsKt.isInput(lsiDtoType) &&
                    typeName instanceof ParameterizedTypeName &&
                    Constants.LIST_CLASS_NAME.equals(((ParameterizedTypeName) typeName).rawType)) {
                getterBuilder.addComment(
                        "GraphQLInput requires `obj." +
                                getterName +
                                "().add(...)`"
                );
                getterBuilder.addStatement(
                        "return this.$L = new $T<>()",
                        prop.getName(),
                        Constants.ARRAY_LIST_CLASS_NAME
                );
            } else {
                getterBuilder.addStatement(
                        "throw new IllegalStateException($S)",
                        "The property \"" + prop.getName() + "\" is not specified"
                );
            }
            getterBuilder.endControlFlow();
        }
        getterBuilder.addStatement("return $L", prop.getName());
        typeBuilder.addMethod(getterBuilder.build());

        ParameterSpec.Builder parameterBuilder = ParameterSpec.builder(typeName, prop.getName());
        if (!typeName.isPrimitive()) {
            if (prop.getNullable()) {
                parameterBuilder.addAnnotation(Nullable.class);
            } else {
                parameterBuilder.addAnnotation(NonNull.class);
            }
        }
        MethodSpec.Builder setterBuilder = MethodSpec
                .methodBuilder(setterName)
                .addParameter(parameterBuilder.build())
                .addModifiers(Modifier.PUBLIC);
        if (interfaceMethodNames.contains(setterName)) {
            setterBuilder.addAnnotation(Override.class);
        }
        setterBuilder.addStatement("this.$L = $L", prop.getName(), prop.getName());
        if (stateFieldName != null) {
            setterBuilder.addStatement("this.$L = true", stateFieldName);
        }
        typeBuilder.addMethod(setterBuilder.build());

        if (stateFieldName != null) {
            MethodSpec.Builder isLoadedBuilder = MethodSpec
                    .methodBuilder(StringUtil.identifier("is", prop.getName(), "Loaded"))
                    .returns(TypeName.BOOLEAN)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(ApiIgnore.class)
                    .addAnnotation(ctx.getJacksonTypes().jsonIgnore)
                    .addStatement("return this.$L", stateFieldName);
            typeBuilder.addMethod(isLoadedBuilder.build());
            MethodSpec.Builder setLoadedBuilder = MethodSpec
                    .methodBuilder(StringUtil.identifier("set", prop.getName(), "Loaded"))
                    .addParameter(TypeName.BOOLEAN, "loaded")
                    .addStatement("this.$L = loaded", stateFieldName);
            typeBuilder.addMethod(setLoadedBuilder.build());
        }
    }

    private String doc(site.addzero.lsi.jimmer.dto.DtoProp prop, boolean contentOnly) {
        String doc = escapedDocumentation(prop.getDocumentation());
        if (doc == null) {
            return null;
        }
        if (contentOnly) {
            int index = -1;
            index = docKeyIndex(index, doc, "@param");
            index = docKeyIndex(index, doc, "@return");
            index = docKeyIndex(index, doc, "@exception");
            index = docKeyIndex(index, doc, "@throws");
            index = docKeyIndex(index, doc, "@see");
            if (index != -1) {
                doc = doc.substring(0, index);
            }
        }
        return doc;
    }

    private void addDefaultConstructor() {
        MethodSpec.Builder builder = MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC);
        typeBuilder.addMethod(builder.build());
    }

    private void addConverterConstructor() {
        site.addzero.lsi.jimmer.ImmutableType immutableBaseType = immutableBaseType();
        ParameterSpec.Builder parameterBuilder =
                ParameterSpec.builder(
                        immutableBaseTypeName().annotated(
                                AnnotationSpec.builder(NonNull.class).build()
                        ),
                        "base"
                );
        MethodSpec.Builder builder = MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(parameterBuilder.build());
        for (site.addzero.lsi.jimmer.dto.DtoProp prop :
                DtoAccessorExtensionsKt.propsInDeclarationOrder(lsiDtoType, lsiGraph)) {
            if (prop instanceof site.addzero.lsi.jimmer.dto.DtoFoldProp) {
                builder.addStatement(
                        "this.$L = $L",
                        prop.getName(),
                        AptDtoFoldValueRenderer.render(
                                (site.addzero.lsi.jimmer.dto.DtoFoldProp) prop,
                                lsiGraph,
                                lsiWorkspace,
                                "base",
                                foldNullGuardAccessorFieldName(prop.getName()),
                                this::generatedTargetType,
                                generatedDtoTypeIdsByTypeName.keySet()
                        )
                );
                continue;
            }
            if (!(prop instanceof DtoBaseProp)) {
                continue;
            }
            DtoBaseProp lsiProp = (DtoBaseProp) prop;
            site.addzero.lsi.jimmer.ImmutableProp immutableProp =
                    DtoConverterExtensionsKt.boundImmutableProp(
                            lsiProp,
                            lsiGraph,
                            immutableSchema
                    );
            String stateFieldName = DtoAccessorExtensionsKt.dtoLoadedStateStorageNameOrNull(
                    lsiProp,
                    lsiGraph,
                    LsiLanguage.JAVA
            );
            builder.addStatement(
                    "this.$L = $L",
                    prop.getName(),
                    AptDtoBaseValueRenderer.render(
                            lsiProp,
                            lsiGraph,
                            immutableSchema,
                            lsiWorkspace,
                            accessorFieldName(prop.getName()),
                            "base",
                            ImmutableDraftNamingExtensionsKt.sourceGetterName(
                                    immutableProp,
                                    lsiWorkspace
                            ),
                            immutableBaseType,
                            ImmutableDraftNamingExtensionsKt.generatedDraftSlotName(
                                    immutableProp,
                                    lsiWorkspace
                            ),
                            "Cannot convert \"" +
                                    immutableBaseTypeName() +
                                    "\" to \"" +
                                    getDtoClassName() +
                                    "\" because the cannot get non-null value for \"" +
                                    prop.getName() +
                                    "\"",
                            this::generatedTargetType,
                            generatedDtoTypeIdsByTypeName.keySet()
                    )
            );
            if (stateFieldName != null) {
                CodeBlock stateInitializer = Objects.requireNonNull(
                        AptDtoLoadedStateRenderer.renderBaseInitializer(
                                lsiProp,
                                lsiGraph,
                                accessorFieldName(prop.getName()),
                                "base"
                        ),
                        "Dynamic DTO property must have a base loaded-state initializer"
                );
                builder.addStatement("this.$L = $L", stateFieldName, stateInitializer);
            }
        }
        typeBuilder.addMethod(builder.build());
    }

    private void addApplyToDraft() {
        MethodSpec.Builder builder = MethodSpec
                .methodBuilder("__applyTo")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(immutableBaseDraftTypeName(), "__draft");
        for (site.addzero.lsi.jimmer.dto.DtoProp prop :
                DtoAccessorExtensionsKt.propsInDeclarationOrder(lsiDtoType, lsiGraph)) {
            if (prop instanceof site.addzero.lsi.jimmer.dto.DtoFoldProp) {
                builder.addCode(
                        AptDtoFoldDraftApplyRenderer.render(
                                (site.addzero.lsi.jimmer.dto.DtoFoldProp) prop,
                                "__draft"
                        )
                );
                continue;
            }
            if (!(prop instanceof DtoBaseProp)) {
                continue;
            }
            DtoBaseProp lsiProp = (DtoBaseProp) prop;
            if (DtoDraftWriteExtensionsKt.isDraftWriteSkipped(
                    lsiProp,
                    lsiGraph,
                    immutableSchema,
                    LsiLanguage.JAVA
            )) {
                continue;
            }
            String stateFieldName = DtoAccessorExtensionsKt.dtoLoadedStateStorageNameOrNull(
                    lsiProp,
                    lsiGraph,
                    LsiLanguage.JAVA
            );
            boolean fuzzy = DtoAccessorExtensionsKt.requiresNonNullDraftWriteGuard(lsiProp, lsiGraph);
            if (stateFieldName != null) {
                builder.beginControlFlow("if (this.$L)", stateFieldName);
            } else if (fuzzy) {
                builder.beginControlFlow("if (this.$L != null)", prop.getName());
            }
            builder.addCode(
                    AptDtoDraftWriteRenderer.render(
                            lsiProp,
                            lsiGraph,
                            immutableSchema,
                            lsiWorkspace,
                            accessorFieldName(prop.getName()),
                            "__draft",
                            prop.getName(),
                            ImmutableDraftNamingExtensionsKt.generatedJavaDraftSetterName(
                                    DtoConverterExtensionsKt.boundImmutableProp(
                                            lsiProp,
                                            lsiGraph,
                                            immutableSchema
                                    ),
                                    lsiWorkspace
                            ),
                            this::generatedTargetType
                    )
            );
            if (stateFieldName != null || fuzzy) {
                builder.endControlFlow();
            }
        }
        typeBuilder.addMethod(builder.build());
    }

    private void addToEntity(boolean withId) {
        boolean entityBase = DtoAccessorExtensionsKt.hasEntityBase(lsiDtoType, immutableSchema);
        boolean idOverridable =
                DtoAccessorExtensionsKt.isInput(lsiDtoType) &&
                        entityBase;
        if (withId && !idOverridable) {
            return;
        }
        DtoBaseProp discriminatorProp = polymorphicInputDiscriminatorProp();
        site.addzero.lsi.jimmer.ImmutableProp baseIdProp = withId ?
                ImmutableSchemaExtensionsKt.idPropOf(
                        immutableSchema,
                        DtoAccessorExtensionsKt.immutableBaseType(lsiDtoType, immutableSchema)
                ) :
                null;
        MethodSpec.Builder builder = MethodSpec
                .methodBuilder(entityBase ?
                        (withId ? "toEntityById" : "toEntity") :
                        "toImmutable");
        if (baseIdProp != null) {
            builder.addParameter(
                    ParameterSpec.builder(
                            AptDtoTypeRefRenderer.render(baseIdProp.getType(), lsiWorkspace).box(),
                            "id"
                    ).addAnnotation(Nullable.class).build()
            );
        } else {
            builder.addAnnotation(Override.class);
        }
        builder.addModifiers(Modifier.PUBLIC)
                .returns(immutableBaseTypeName());
        if (!withId && idOverridable) {
            builder.addStatement("return toEntityById(null)");
        } else if (discriminatorProp != null && isDefaultPolymorphicInputBranch()) {
            builder.addCode(
                    AptDtoPolymorphicInputRenderer.renderDefaultBranchBody(
                            lsiDtoType,
                            Objects.requireNonNull(
                                    currentLsiPolymorphicBranchOrNull(),
                                    "Frozen DTO default polymorphic branch is required"
                            ),
                            discriminatorProp,
                            lsiGraph,
                            immutableSchema,
                            lsiWorkspace,
                            getGeneratedDtoPackageName(),
                            getGeneratedDtoSimpleNames(),
                            baseIdProp != null ? "id" : null
                    )
            );
        } else {
            if (discriminatorProp != null && isTypedPolymorphicInputBranch()) {
                builder.addCode(
                        AptDtoPolymorphicInputRenderer.renderTypedDiscriminatorValidation(
                                lsiDtoType,
                                Objects.requireNonNull(
                                        currentLsiPolymorphicBranchOrNull(),
                                        "Frozen DTO typed polymorphic branch is required"
                                ),
                                discriminatorProp,
                                lsiGraph,
                                immutableSchema,
                                lsiWorkspace,
                                getGeneratedDtoPackageName(),
                                getGeneratedDtoSimpleNames()
                        )
                );
            }
            builder.addCode(
                    "return $T.$L.produce(__draft -> {$>\n",
                    immutableBaseDraftTypeName(),
                    "$"
            );
            builder.addStatement("this.__applyTo(__draft)");
            if (baseIdProp != null) {
                builder.beginControlFlow("if (id != null)");
                builder.addStatement(
                        "__draft.$L($L)",
                        ImmutableDraftNamingExtensionsKt.generatedJavaDraftSetterName(
                                baseIdProp,
                                lsiWorkspace
                        ),
                        "id"
                );
                builder.endControlFlow();
            }
            builder.addCode("$<});\n");
        }
        typeBuilder.addMethod(builder.build());
    }

    @Nullable
    private DtoBaseProp polymorphicInputDiscriminatorProp() {
        if (!polymorphicBranch) {
            return null;
        }
        return DtoAccessorExtensionsKt.selectedPolymorphicInputDiscriminatorPropOrNull(
                lsiDtoType,
                lsiGraph,
                immutableSchema
        );
    }

    private boolean isDefaultPolymorphicInputBranch() {
        return lsiPolymorphicBranch != null &&
                lsiPolymorphicBranch.getKind() == site.addzero.lsi.jimmer.dto.DtoPolymorphicBranchKind.DEFAULT;
    }

    private boolean isTypedPolymorphicInputBranch() {
        return lsiPolymorphicBranch != null &&
                lsiPolymorphicBranch.getKind() == site.addzero.lsi.jimmer.dto.DtoPolymorphicBranchKind.TYPE;
    }


    private void addEntityType() {
        typeBuilder.addMethod(
                AptDtoSpecificationRenderer.renderEntityType(
                        lsiDtoType,
                        immutableSchema,
                        lsiWorkspace
                )
        );
    }

    private void addApplyTo() {
        typeBuilder.addMethod(
                AptDtoSpecificationRenderer.renderApplyTo(
                        lsiDtoType,
                        lsiGraph,
                        immutableSchema,
                        lsiWorkspace
                )
        );
    }

    private TypeName renderGeneratedValueType(site.addzero.lsi.jimmer.dto.DtoProp prop) {
        LsiTypeRef type = DtoGeneratedValueTypeExtensionsKt.generatedValueType(
                prop,
                lsiGraph,
                immutableSchema,
                LsiLanguage.JAVA,
                this::generatedTargetType
        );
        return AptDtoTypeRefRenderer.render(
                type,
                lsiWorkspace,
                generatedDtoTypeIdsByTypeName.keySet()
        );
    }

    private site.addzero.lsi.jimmer.ImmutableType immutableBaseType() {
        return DtoAccessorExtensionsKt.immutableBaseType(lsiDtoType, immutableSchema);
    }

    private ClassName immutableBaseTypeName() {
        return AptImmutableTypeNameRenderer.renderSource(immutableBaseType(), lsiWorkspace);
    }

    private ClassName immutableBaseDraftTypeName() {
        return AptImmutableTypeNameRenderer.renderDraft(immutableBaseType(), lsiWorkspace);
    }

    private void addHashCode() {
        typeBuilder.addMethod(
                AptDtoEqualityRenderer.renderHashCode(lsiDtoType, lsiGraph, immutableSchema)
        );
    }

    private void addEquals() {
        typeBuilder.addMethod(
                AptDtoEqualityRenderer.renderEquals(
                        lsiDtoType,
                        lsiGraph,
                        immutableSchema,
                        getGeneratedDtoPackageName(),
                        getGeneratedDtoSimpleNames()
                )
        );
    }

    private void addToString() {
        typeBuilder.addMethod(
                AptDtoToStringRenderer.render(lsiDtoType, lsiGraph, simpleNamePath())
        );
    }

    private String simpleNamePath() {
        String name = getSimpleName();
        if (parent != null) {
            return parent.simpleNamePath() + '.' + name;
        }
        return name;
    }

    private site.addzero.lsi.jimmer.dto.DtoProp polymorphicRootPropOrNull(
            site.addzero.lsi.jimmer.dto.DtoProp prop
    ) {
        if (!polymorphicBranch || parent == null) {
            return null;
        }
        return DtoGenerationExtensionsKt.promotedPolymorphicRootPropOrNull(
                parent.lsiDtoType,
                lsiGraph,
                prop
        );
    }

    private LsiDeclaredType generatedTargetType(site.addzero.lsi.jimmer.dto.DtoProp prop) {
        LsiPoetTypeName ownerTypeName = JimmerDtoPoetTypeNames.create(
                getGeneratedDtoPackageName(),
                getGeneratedDtoSimpleNames()
        );
        JimmerDtoPoetTypeNames.requirePlanned(
                lsiGraph,
                lsiDtoType,
                generatedDtoTypeIdsByTypeName,
                ownerTypeName
        );
        return JimmerDtoPoetTypeNames.toLsiGeneratedTargetType(
                lsiGraph,
                prop,
                ownerTypeName,
                generatedDtoTypeIdsByTypeName,
                batchRootDtoTypeNames
        );
    }

    private String accessorFieldName(String propName) {
        return StringUtil.snake(propName + "Accessor", StringUtil.SnakeCase.UPPER);
    }

    private String foldNullGuardAccessorFieldName(String propName) {
        return StringUtil.snake(propName + "NullGuardAccessor", StringUtil.SnakeCase.UPPER);
    }

    private static int docKeyIndex(int originalIndex, String doc, String key) {
        int index = doc.indexOf(key);
        if (index == -1 || (originalIndex != -1 && originalIndex < index)) {
            return originalIndex;
        }
        if (doc.length() == index + key.length()) {
            return index;
        }
        if (Character.isWhitespace(doc.charAt(index + key.length()))) {
            return index;
        }
        return originalIndex;
    }

    private static String escapedDocumentation(@Nullable String documentation) {
        return documentation != null && !documentation.isEmpty() ?
                documentation.replace("$", "$$") :
                null;
    }

    TypeSpec.Builder getTypeBuilder() {
        return typeBuilder;
    }

    private boolean isSerializerRequired() {
        return DtoAccessorExtensionsKt.requiresDynamicInputSerialization(lsiDtoType, lsiGraph);
    }

    private boolean isBuildRequired() {
        return DtoAccessorExtensionsKt.requiresInputBuilder(lsiDtoType, lsiGraph);
    }

    private boolean isHibernateValidatorEnhancementRequired() {
        return DtoAccessorExtensionsKt.requiresHibernateValidatorEnhancement(
                lsiDtoType,
                lsiGraph,
                hibernateValidatorEnhancement
        );
    }

}
