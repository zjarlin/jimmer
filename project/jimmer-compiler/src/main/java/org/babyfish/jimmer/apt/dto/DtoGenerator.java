package org.babyfish.jimmer.apt.dto;

import com.squareup.javapoet.*;
import org.babyfish.jimmer.apt.Context;
import org.babyfish.jimmer.apt.GeneratorException;
import org.babyfish.jimmer.apt.immutable.generator.Constants;
import org.babyfish.jimmer.apt.immutable.meta.ImmutableProp;
import org.babyfish.jimmer.apt.immutable.meta.ImmutableType;
import org.babyfish.jimmer.apt.util.GeneratedAnnotation;
import org.babyfish.jimmer.client.ApiIgnore;
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames;
import org.babyfish.jimmer.compiler.dto.JimmerDtoJacksonVersion;
import org.babyfish.jimmer.compiler.render.apt.AptDtoAccessorRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoBaseValueRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoDescriptionRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoDraftWriteRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoEqualityRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoFoldDraftApplyRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoFoldValueRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoHibernateValidatorRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoInputBuilderRenderer;
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
import org.babyfish.jimmer.dto.compiler.*;
import org.babyfish.jimmer.impl.util.StringUtil;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import site.addzero.lsi.core.LsiLanguage;
import site.addzero.lsi.jimmer.ImmutableSchema;
import site.addzero.lsi.jimmer.dto.DtoAccessorExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract;
import site.addzero.lsi.jimmer.dto.DtoBaseProp;
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution;
import site.addzero.lsi.jimmer.dto.DtoGenerationExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoGeneratedBaseContractKind;
import site.addzero.lsi.jimmer.dto.DtoGeneratedValueTypeExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoGraph;
import site.addzero.lsi.jimmer.dto.DtoDraftWriteExtensionsKt;
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

    final DtoType<ImmutableType, ImmutableProp> dtoType;

    private final DtoGraph lsiGraph;

    private final site.addzero.lsi.jimmer.dto.DtoType lsiDtoType;

    private final DtoAnnotationContract annotationContract;

    private final DtoInterfaceContractResolution interfaceContractResolution;

    private final DtoConfigContractResolution configContractResolution;

    private final ImmutableSchema immutableSchema;

    private final LsiWorkspace lsiWorkspace;

    private final Map<DtoTypeId, LsiPoetTypeName> batchRootDtoTypeNames;

    private final JimmerDtoJacksonVersion jacksonVersion;

    private final boolean hibernateValidatorEnhancement;

    private final DtoGenerator parent;

    private final DtoGenerator root;

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
            DtoType<ImmutableType, ImmutableProp> dtoType,
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
                dtoType,
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
            DtoType<ImmutableType, ImmutableProp> dtoType,
            site.addzero.lsi.jimmer.dto.DtoType lsiDtoType,
            DtoGenerator parent,
            String innerClassName
    ) {
        this(
                ctx,
                dtoType,
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
            DtoType<ImmutableType, ImmutableProp> dtoType,
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
        this.dtoType = dtoType;
        this.lsiGraph = lsiGraph;
        this.lsiDtoType = lsiDtoType;
        this.parent = parent;
        this.root = parent != null ? parent.root : this;
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
            typeBuilder.addAnnotation(GeneratedAnnotation.generatedAnnotation(dtoType.getDtoFile()));
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
                                root.dtoType.getPackageName(),
                                typeBuilder.build()
                        )
                        .indent("    ")
                        .build()
                        .writeTo(ctx.getFiler());
            } catch (IOException ex) {
                throw new GeneratorException(
                        String.format(
                                "Cannot generate dto type '%s' for '%s'",
                                dtoType.getName(),
                                dtoType.getBaseType().getQualifiedName()
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
            typeBuilder.addAnnotation(GeneratedAnnotation.generatedAnnotation(dtoType.getDtoFile()));
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
        DtoPolymorphism<ImmutableType, ImmutableProp> polymorphism = dtoType.getPolymorphism();
        assert polymorphism != null;
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
        for (AbstractProp prop : dtoType.getProps()) {
            addAccessorDeclaration(prop);
        }
        generateNestedDtoTypes();

        addPolymorphicMetadata();
        ClassName superInterfaceName = getDtoClassName();
        DtoPolymorphicBranch<ImmutableType, ImmutableProp> defaultBranch = polymorphism.getDefaultBranch();
        if (defaultBranch != null) {
            generatePolymorphicBranch(defaultBranch, superInterfaceName);
        }
        for (DtoPolymorphicBranch<ImmutableType, ImmutableProp> branch : polymorphism.getTypeBranches()) {
            generatePolymorphicBranch(branch, superInterfaceName);
        }

        if (innerClassName != null) {
            assert parent != null;
            parent.typeBuilder.addType(typeBuilder.build());
        } else {
            try {
                JavaFile
                        .builder(
                                root.dtoType.getPackageName(),
                                typeBuilder.build()
                        )
                        .indent("    ")
                        .build()
                        .writeTo(ctx.getFiler());
            } catch (IOException ex) {
                throw new GeneratorException(
                        String.format(
                                "Cannot generate dto type '%s' for '%s'",
                                dtoType.getName(),
                                dtoType.getBaseType().getQualifiedName()
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
        switch (kind) {
            case ENTITY_INPUT:
                return ParameterizedTypeName.get(
                        org.babyfish.jimmer.apt.immutable.generator.Constants.INPUT_CLASS_NAME,
                        dtoType.getBaseType().getClassName()
                );
            case ENTITY_VIEW:
                return ParameterizedTypeName.get(
                        org.babyfish.jimmer.apt.immutable.generator.Constants.VIEW_CLASS_NAME,
                        dtoType.getBaseType().getClassName()
                );
            case ENTITY_SPECIFICATION:
                return ParameterizedTypeName.get(
                        org.babyfish.jimmer.apt.immutable.generator.Constants.JSPECIFICATION_CLASS_NAME,
                        dtoType.getBaseType().getClassName(),
                        dtoType.getBaseType().getTableClassName()
                );
            case EMBEDDABLE:
                return ParameterizedTypeName.get(
                        org.babyfish.jimmer.apt.immutable.generator.Constants.EMBEDDABLE_DTO_CLASS_NAME,
                        dtoType.getBaseType().getClassName()
                );
            default:
                throw new AssertionError("Unexpected DTO base contract kind: " + kind);
        }
    }

    private void generatePolymorphicBranch(
            DtoPolymorphicBranch<ImmutableType, ImmutableProp> branch,
            TypeName superInterfaceName
    ) {
        site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch lsiBranch = lsiPolymorphicBranch(branch);
        new DtoGenerator(
                ctx,
                dtoType.mergedWith(branch.getDtoType()),
                lsiGraph,
                DtoGenerationExtensionsKt.mergedType(lsiBranch, lsiGraph),
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
                lsiBranch
        ).generate();
    }

    private site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch lsiPolymorphicBranch(
            DtoPolymorphicBranch<ImmutableType, ImmutableProp> branch
    ) {
        try {
            return DtoGenerationExtensionsKt.generatedPolymorphicBranch(
                    lsiDtoType,
                    branch.getClassName(),
                    site.addzero.lsi.jimmer.dto.DtoPolymorphicBranchKind.valueOf(branch.getKind().name())
            );
        } catch (IllegalArgumentException ex) {
            throw new DtoException(
                    ex.getMessage() != null ? ex.getMessage() :
                            "Cannot resolve frozen DTO polymorphic branch \"" + branch.getClassName() + "\"",
                    ex
            );
        }
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
        return innerClassName != null ? innerClassName : dtoType.getName();
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
        if (innerClassName != null) {
            List<String> list = new ArrayList<>();
            collectNames(list);
            List<String> simpleNames = list.subList(1, list.size());
            if (nestedClassName != null) {
                simpleNames = new ArrayList<>(simpleNames);
                simpleNames.add(nestedClassName);
            }
            return ClassName.get(
                    root.dtoType.getPackageName(),
                    list.get(0),
                    simpleNames.toArray(EMPTY_STR_ARR)
            );
        }
        if (nestedClassName == null) {
            return ClassName.get(
                    root.dtoType.getPackageName(),
                    dtoType.getName()
            );
        }
        return ClassName.get(
                root.dtoType.getPackageName(),
                dtoType.getName(),
                nestedClassName
        );
    }

    private String getGeneratedDtoPackageName() {
        return root.dtoType.getPackageName();
    }

    private List<String> getGeneratedDtoSimpleNames() {
        List<String> simpleNames = new ArrayList<>();
        collectNames(simpleNames);
        return Collections.unmodifiableList(simpleNames);
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
            for (DtoProp<ImmutableType, ImmutableProp> prop : dtoType.getDtoProps()) {
                addAccessorField(prop);
            }
            for (FoldProp<ImmutableType, ImmutableProp> prop : dtoType.getFoldProps()) {
                addFoldNullGuardAccessorField(prop);
            }
        }
        for (AbstractProp prop : dtoType.getProps()) {
            addField(prop);
            if (prop instanceof DtoProp<?, ?>) {
                addStateField(asDtoProp(prop));
            }
        }

        addDefaultConstructor();
        if (!isSpecification) {
            addConverterConstructor();
        }

        for (AbstractProp prop : dtoType.getProps()) {
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
            for (DtoProp<ImmutableType, ImmutableProp> prop : dtoType.getDtoProps()) {
                MethodSpec converter = AptDtoSpecificationRenderer.renderConverterOrNull(
                        lsiProp(prop),
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
        for (DtoProp<ImmutableType, ImmutableProp> prop : dtoType.getDtoProps()) {
            if (polymorphicRootPropOrNull(prop) != null) {
                continue;
            }
            DtoBaseProp lsiProp = DtoGenerationExtensionsKt.baseProp(lsiDtoType, lsiGraph, prop.getName());
            site.addzero.lsi.jimmer.dto.DtoType lsiTargetType =
                    DtoGenerationExtensionsKt.generatedTargetType(lsiProp, lsiGraph);
            if (lsiTargetType == null) {
                continue;
            }
            DtoType<ImmutableType, ImmutableProp> targetType = prop.getTargetType();
            if (targetType == null) {
                throw new DtoException(
                        "Compiled DTO property \"" + prop.getName() +
                                "\" has no target required by the frozen DTO graph"
                );
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
                    targetType,
                    lsiTargetType,
                    this,
                    childSimpleName
            ).generate();
        }
        for (FoldProp<ImmutableType, ImmutableProp> prop : dtoType.getFoldProps()) {
            if (polymorphicRootPropOrNull(prop) != null) {
                continue;
            }
            site.addzero.lsi.jimmer.dto.DtoFoldProp lsiProp =
                    DtoGenerationExtensionsKt.foldProp(lsiDtoType, lsiGraph, prop.getName());
            site.addzero.lsi.jimmer.dto.DtoType lsiTargetType =
                    DtoGenerationExtensionsKt.generatedTargetType(lsiProp, lsiGraph);
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
                    prop.getTargetType(),
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
                                dtoType.getBaseType().getClassName(),
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
                        dtoType.getBaseType().getClassName(),
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
                                dtoType.getBaseType().getClassName(),
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
                        dtoType.getBaseType().getClassName(),
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

    private void addAccessorField(DtoProp<ImmutableType, ImmutableProp> prop) {
        DtoBaseProp lsiProp = DtoGenerationExtensionsKt.baseProp(
                lsiDtoType,
                lsiGraph,
                prop.getName()
        );
        if (!DtoAccessorExtensionsKt.requiresDtoPropAccessor(
                lsiProp,
                lsiGraph,
                immutableSchema,
                LsiLanguage.JAVA,
                this::generatedTargetType
        )) {
            return;
        }
        addAccessorField(
                lsiProp,
                accessorFieldName(prop.getName()),
                DtoAccessorExtensionsKt.acceptsNullInAccessor(lsiProp, lsiGraph),
                true
        );
    }

    private void addFoldNullGuardAccessorField(FoldProp<ImmutableType, ImmutableProp> prop) {
        DtoBaseProp nullGuardProp = DtoGenerationExtensionsKt.nullGuardProp(
                DtoGenerationExtensionsKt.foldProp(lsiDtoType, lsiGraph, prop.getName()),
                lsiGraph
        );
        if (nullGuardProp != null) {
            addAccessorField(
                    nullGuardProp,
                    foldNullGuardAccessorFieldName(prop),
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

    private void addField(AbstractProp prop) {
        if (prop instanceof UserProp) {
            addField((UserProp) prop);
            return;
        }
        TypeName typeName = renderGeneratedValueType(prop);
        if (isFieldNullable(prop)) {
            typeName = typeName.box();
        }
        FieldSpec.Builder builder = FieldSpec
                .builder(typeName, prop.getName())
                .addModifiers(ctx.getDtoFieldModifier());
        String doc = doc(prop, true);
        if (doc != null) {
            builder.addJavadoc(doc);
        }
        boolean isBuilderRequired = isBuildRequired();
        site.addzero.lsi.jimmer.dto.DtoProp lsiProp =
                DtoGenerationExtensionsKt.prop(lsiDtoType, lsiGraph, prop.getName());
        if (DtoAccessorExtensionsKt.requiresFixedInputField(lsiProp, lsiGraph)) {
            builder.addAnnotation(org.babyfish.jimmer.apt.immutable.generator.Constants.FIXED_INPUT_FIELD_CLASS_NAME);
        }
        builder.addAnnotations(
                AptDtoPropAnnotationRenderer.renderField(
                        lsiProp,
                        annotationContract,
                        immutableSchema,
                        lsiWorkspace,
                        isBuilderRequired ? ctx.getJacksonTypes().jsonDeserialize.reflectionName() : null
                )
        );
        typeBuilder.addField(builder.build());
    }

    private void addField(UserProp prop) {
        TypeName typeName = renderGeneratedValueType(prop);
        if (isFieldNullable(prop)) {
            typeName = typeName.box();
        }
        FieldSpec.Builder builder = FieldSpec
                .builder(typeName, prop.getAlias())
                .addModifiers(ctx.getDtoFieldModifier());
        String defaultValueText = DtoGenerationExtensionsKt
                .userProp(lsiDtoType, lsiGraph, prop.getName())
                .getDefaultValueText();
        if (defaultValueText != null) {
            builder.initializer(defaultValueText);
        }
        String doc = doc(prop, true);
        if (doc != null) {
            builder.addJavadoc(doc);
        }
        builder.addAnnotations(
                AptDtoPropAnnotationRenderer.renderField(
                        DtoGenerationExtensionsKt.prop(lsiDtoType, lsiGraph, prop.getName()),
                        annotationContract,
                        immutableSchema,
                        lsiWorkspace,
                        isBuildRequired() ? ctx.getJacksonTypes().jsonDeserialize.reflectionName() : null
                )
        );
        typeBuilder.addField(builder.build());
    }

    private void addStateField(DtoProp<ImmutableType, ImmutableProp> prop) {
        FieldSpec stateField = AptDtoLoadedStateRenderer.renderStorageField(
                DtoGenerationExtensionsKt.prop(lsiDtoType, lsiGraph, prop.getName()),
                lsiGraph,
                ctx.getDtoFieldModifier()
        );
        if (stateField != null) {
            typeBuilder.addField(stateField);
        }
    }

    private void addAccessorDeclaration(AbstractProp prop) {
        TypeName typeName = renderGeneratedValueType(prop);
        site.addzero.lsi.jimmer.dto.DtoProp lsiProp =
                DtoGenerationExtensionsKt.prop(lsiDtoType, lsiGraph, prop.getName());
        MethodSpec.Builder getterBuilder = MethodSpec
                .methodBuilder(
                        DtoAccessorExtensionsKt.dtoValueAccessorName(
                                lsiProp,
                                LsiLanguage.JAVA,
                                lsiGraph,
                                immutableSchema
                        )
                )
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(typeName);
        AnnotationSpec description = AptDtoDescriptionRenderer.render(lsiProp, lsiGraph);
        if (description != null) {
            getterBuilder.addAnnotation(description);
        }
        if (!typeName.isPrimitive()) {
            if (prop.isNullable()) {
                getterBuilder.addAnnotation(Nullable.class);
            } else {
                getterBuilder.addAnnotation(NonNull.class);
            }
        }
        getterBuilder.addAnnotations(
                AptDtoPropAnnotationRenderer.renderGetter(
                        lsiProp,
                        annotationContract,
                        immutableSchema,
                        lsiWorkspace,
                        null
                )
        );
        typeBuilder.addMethod(getterBuilder.build());
    }

    private void addAccessors(AbstractProp prop) {
        TypeName typeName = renderGeneratedValueType(prop);
        site.addzero.lsi.jimmer.dto.DtoProp lsiProp =
                DtoGenerationExtensionsKt.prop(lsiDtoType, lsiGraph, prop.getName());
        String getterName = DtoAccessorExtensionsKt.dtoValueAccessorName(
                lsiProp,
                LsiLanguage.JAVA,
                lsiGraph,
                immutableSchema
        );
        String setterName = DtoAccessorExtensionsKt.javaValueSetterName(
                lsiProp,
                lsiGraph,
                immutableSchema
        );
        String stateFieldName = DtoAccessorExtensionsKt.dtoLoadedStateStorageNameOrNull(
                lsiProp,
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
        AnnotationSpec description = AptDtoDescriptionRenderer.render(lsiProp, lsiGraph);
        if (description != null) {
            getterBuilder.addAnnotation(description);
        }
        if (!typeName.isPrimitive()) {
            if (prop.isNullable()) {
                getterBuilder.addAnnotation(Nullable.class);
            } else {
                getterBuilder.addAnnotation(NonNull.class);
            }
        }
        boolean isBuilderRequired = isBuildRequired();
        getterBuilder.addAnnotations(
                AptDtoPropAnnotationRenderer.renderGetter(
                        lsiProp,
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
        if (!prop.isNullable() && isFieldNullable(prop)) {
            getterBuilder.beginControlFlow(
                    "if ($L == null)",
                    prop.getName()
            );
            if (dtoType.getModifiers().contains(DtoModifier.INPUT) &&
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
            if (prop.isNullable()) {
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

    private String doc(AbstractProp prop, boolean contentOnly) {
        String doc = propDocumentation(prop);
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
        ParameterSpec.Builder parameterBuilder =
                ParameterSpec.builder(
                        dtoType.getBaseType().getClassName().annotated(
                                AnnotationSpec.builder(NonNull.class).build()
                        ),
                        "base"
                );
        MethodSpec.Builder builder = MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(parameterBuilder.build());
        for (AbstractProp abstractProp : dtoType.getProps()) {
            if (abstractProp instanceof FoldProp<?, ?>) {
                FoldProp<ImmutableType, ImmutableProp> foldProp = asFoldProp(abstractProp);
                builder.addStatement(
                        "this.$L = $L",
                        foldProp.getName(),
                        AptDtoFoldValueRenderer.render(
                                DtoGenerationExtensionsKt.foldProp(
                                        lsiDtoType,
                                        lsiGraph,
                                        foldProp.getName()
                                ),
                                lsiGraph,
                                lsiWorkspace,
                                "base",
                                foldNullGuardAccessorFieldName(foldProp),
                                this::generatedTargetType,
                                generatedDtoTypeIdsByTypeName.keySet()
                        )
                );
                continue;
            }
            if (!(abstractProp instanceof DtoProp<?, ?>)) {
                continue;
            }
            DtoProp<ImmutableType, ImmutableProp> prop = asDtoProp(abstractProp);
            DtoBaseProp lsiProp = DtoGenerationExtensionsKt.baseProp(
                    lsiDtoType,
                    lsiGraph,
                    prop.getName()
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
                            prop.getBaseProp().getGetterName(),
                            dtoType.getBaseType().getProducerClassName(),
                            prop.getBaseProp().getSlotName(),
                            "Cannot convert \"" +
                                    dtoType.getBaseType().getClassName() +
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
                .addParameter(dtoType.getBaseType().getDraftClassName(), "__draft");
        for (AbstractProp abstractProp : dtoType.getProps()) {
            if (abstractProp instanceof FoldProp<?, ?>) {
                FoldProp<ImmutableType, ImmutableProp> foldProp = asFoldProp(abstractProp);
                builder.addCode(
                        AptDtoFoldDraftApplyRenderer.render(
                                DtoGenerationExtensionsKt.foldProp(
                                        lsiDtoType,
                                        lsiGraph,
                                        foldProp.getName()
                                ),
                                "__draft"
                        )
                );
                continue;
            }
            if (!(abstractProp instanceof DtoProp<?, ?>)) {
                continue;
            }
            DtoProp<ImmutableType, ImmutableProp> prop = asDtoProp(abstractProp);
            DtoBaseProp lsiProp = DtoGenerationExtensionsKt.baseProp(
                    lsiDtoType,
                    lsiGraph,
                    prop.getName()
            );
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
                            prop.getBaseProp().getSetterName(),
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
                dtoType.getModifiers().contains(DtoModifier.INPUT) &&
                        entityBase;
        if (withId && !idOverridable) {
            return;
        }
        DtoBaseProp discriminatorProp = polymorphicInputDiscriminatorProp();
        ImmutableProp baseIdProp = withId ? dtoType.getBaseType().getIdProp() : null;
        MethodSpec.Builder builder = MethodSpec
                .methodBuilder(entityBase ?
                        (withId ? "toEntityById" : "toEntity") :
                        "toImmutable");
        if (baseIdProp != null) {
            builder.addParameter(
                    ParameterSpec.builder(
                            baseIdProp.getTypeName().box(),
                            "id"
                    ).addAnnotation(Nullable.class).build()
            );
        } else {
            builder.addAnnotation(Override.class);
        }
        builder.addModifiers(Modifier.PUBLIC)
                .returns(dtoType.getBaseType().getClassName());
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
                    dtoType.getBaseType().getDraftClassName(),
                    "$"
            );
            builder.addStatement("this.__applyTo(__draft)");
            if (baseIdProp != null) {
                builder.beginControlFlow("if (id != null)");
                builder.addStatement("__draft.$L($L)", baseIdProp.getSetterName(), "id");
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

    private TypeName renderGeneratedValueType(AbstractProp prop) {
        site.addzero.lsi.jimmer.dto.DtoProp lsiProp =
                DtoGenerationExtensionsKt.prop(lsiDtoType, lsiGraph, prop.getName());
        LsiTypeRef type = DtoGeneratedValueTypeExtensionsKt.generatedValueType(
                lsiProp,
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

    @SuppressWarnings("unchecked")
    private DtoProp<ImmutableType, ImmutableProp> asDtoProp(AbstractProp prop) {
        return (DtoProp<ImmutableType, ImmutableProp>) prop;
    }

    @SuppressWarnings("unchecked")
    private FoldProp<ImmutableType, ImmutableProp> asFoldProp(AbstractProp prop) {
        return (FoldProp<ImmutableType, ImmutableProp>) prop;
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

    private DtoBaseProp lsiProp(DtoProp<ImmutableType, ImmutableProp> prop) {
        return (DtoBaseProp) DtoGenerationExtensionsKt.prop(lsiDtoType, lsiGraph, prop.getName());
    }

    private site.addzero.lsi.jimmer.dto.DtoProp polymorphicRootPropOrNull(AbstractProp prop) {
        if (!polymorphicBranch || parent == null) {
            return null;
        }
        site.addzero.lsi.jimmer.dto.DtoProp mergedProp = DtoGenerationExtensionsKt.prop(
                lsiDtoType,
                lsiGraph,
                prop.getName()
        );
        return DtoGenerationExtensionsKt.promotedPolymorphicRootPropOrNull(
                parent.lsiDtoType,
                lsiGraph,
                mergedProp
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

    private void collectNames(List<String> list) {
        if (parent == null) {
            list.add(dtoType.getName());
        } else {
            parent.collectNames(list);
            list.add(innerClassName);
        }
    }

    private String accessorFieldName(String propName) {
        return StringUtil.snake(propName + "Accessor", StringUtil.SnakeCase.UPPER);
    }

    private String foldNullGuardAccessorFieldName(FoldProp<ImmutableType, ImmutableProp> prop) {
        return StringUtil.snake(prop.getName() + "NullGuardAccessor", StringUtil.SnakeCase.UPPER);
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

    private String propDocumentation(AbstractProp prop) {
        return escapedDocumentation(
                DtoGenerationExtensionsKt.prop(lsiDtoType, lsiGraph, prop.getName()).getDocumentation()
        );
    }

    private static String escapedDocumentation(@Nullable String documentation) {
        return documentation != null && !documentation.isEmpty() ?
                documentation.replace("$", "$$") :
                null;
    }

    TypeSpec.Builder getTypeBuilder() {
        return typeBuilder;
    }

    private static boolean isFieldNullable(AbstractProp prop) {
        if (prop instanceof DtoProp<?, ?>) {
            String funcName = prop.getFuncName();
            return !"null".equals(funcName) && !"notNull".equals(funcName);
        }
        return true;
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
