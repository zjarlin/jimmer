package org.babyfish.jimmer.apt.dto;

import com.squareup.javapoet.*;
import org.babyfish.jimmer.apt.Context;
import org.babyfish.jimmer.apt.GeneratorException;
import org.babyfish.jimmer.apt.immutable.generator.Constants;
import org.babyfish.jimmer.apt.immutable.meta.ImmutableProp;
import org.babyfish.jimmer.apt.immutable.meta.ImmutableType;
import org.babyfish.jimmer.apt.util.ConverterMetadata;
import org.babyfish.jimmer.apt.util.GeneratedAnnotation;
import org.babyfish.jimmer.client.ApiIgnore;
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames;
import org.babyfish.jimmer.compiler.dto.JimmerDtoJacksonVersion;
import org.babyfish.jimmer.compiler.render.apt.AptDtoDescriptionRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoConfigRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoEnumRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoEqualityRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoHibernateValidatorRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoInputBuilderRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoJacksonPolymorphismRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoPolymorphicBranchRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoPropAnnotationRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoSerializerRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoSpecificationRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoToStringRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoTypeAnnotationRenderer;
import org.babyfish.jimmer.compiler.render.apt.AptDtoTypeRefRenderer;
import org.babyfish.jimmer.dto.compiler.*;
import org.babyfish.jimmer.impl.util.StringUtil;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.Id;
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
import site.addzero.lsi.jimmer.dto.DtoGraph;
import site.addzero.lsi.jimmer.dto.DtoInterfaceContract;
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution;
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranchAnnotationExtensionsKt;
import site.addzero.lsi.jimmer.dto.DtoTypeId;
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

    private final Map<DtoTypeId, LsiPoetTypeName> generatedDtoTypeNames;

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
        this.generatedDtoTypeNames = new LinkedHashMap<>(
                parent != null ? parent.generatedDtoTypeNames : this.batchRootDtoTypeNames
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
        if (dtoType.getPolymorphism() != null) {
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
        if (dtoType.getModifiers().contains(DtoModifier.SEALED)) {
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

        addPolymorphicMetadata(polymorphism);
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
        site.addzero.lsi.jimmer.dto.DtoPolymorphism polymorphism = lsiDtoType.getPolymorphism();
        if (polymorphism == null) {
            throw new DtoException("Frozen DTO type is not polymorphic");
        }
        site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch matchedBranch = null;
        for (site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch candidate : polymorphism.getBranches()) {
            if (candidate.getClassName().equals(branch.getClassName()) &&
                    candidate.getKind().name().equals(branch.getKind().name())) {
                if (matchedBranch != null) {
                    throw new DtoException(
                            "Frozen DTO polymorphism contains duplicate generated branch \"" +
                                    branch.getClassName() + "\""
                    );
                }
                matchedBranch = candidate;
            }
        }
        if (matchedBranch == null) {
            throw new DtoException(
                    "Frozen DTO polymorphism does not contain generated branch \"" +
                            branch.getClassName() + "\""
            );
        }
        return matchedBranch;
    }

    private site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch currentLsiPolymorphicBranchOrNull() {
        if (!polymorphicBranch) {
            return null;
        }
        if (lsiPolymorphicBranch == null ||
                !DtoGenerationExtensionsKt.mergedType(lsiPolymorphicBranch, lsiGraph).equals(lsiDtoType)) {
            throw new DtoException("Frozen DTO polymorphic branch does not match generated branch");
        }
        return lsiPolymorphicBranch;
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
        JimmerDtoPoetTypeNames.register(lsiGraph, type, generatedDtoTypeNames, typeName);
    }

    private void addMembers() {

        boolean isSpecification = dtoType.getModifiers().contains(DtoModifier.SPECIFICATION);
        if (!isSpecification && !polymorphicBranch) {
            addMetadata();
        }

        if (!dtoType.getModifiers().contains(DtoModifier.SPECIFICATION)) {
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

        if (dtoType.getModifiers().contains(DtoModifier.SPECIFICATION)) {
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

        for (DtoProp<ImmutableType, ImmutableProp> prop : dtoType.getDtoProps()) {
            addSpecificationConverter(prop);
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
            DtoType<ImmutableType, ImmutableProp> targetType = prop.getTargetType();
            if (targetType == null) {
                continue;
            }
            if (!prop.isRecursive() || targetType.isFocusedRecursion()) {
                DtoBaseProp lsiProp = DtoGenerationExtensionsKt.baseProp(lsiDtoType, lsiGraph, prop.getName());
                site.addzero.lsi.jimmer.dto.DtoType lsiTargetType =
                        DtoGenerationExtensionsKt.generatedTargetType(lsiProp, lsiGraph);
                if (lsiTargetType == null) {
                    throw new DtoException(
                            "Frozen DTO property \"" + prop.getName() + "\" has no generated target"
                    );
                }
                String childSimpleName = targetSimpleName(prop);
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
        }
        for (FoldProp<ImmutableType, ImmutableProp> prop : dtoType.getFoldProps()) {
            if (polymorphicRootPropOrNull(prop) != null) {
                continue;
            }
            site.addzero.lsi.jimmer.dto.DtoFoldProp lsiProp =
                    DtoGenerationExtensionsKt.foldProp(lsiDtoType, lsiGraph, prop.getName());
            site.addzero.lsi.jimmer.dto.DtoType lsiTargetType =
                    DtoGenerationExtensionsKt.generatedTargetType(lsiProp, lsiGraph);
            String childSimpleName = targetSimpleName(prop);
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
                .add("$T.$L", dtoType.getBaseType().getFetcherClassName(), "$")
                .indent();
        addFetcherFields(dtoType, lsiDtoType, cb);
        cb
                .add(",\n")
                .unindent()
                .add("$T::new\n", getDtoClassName())
                .unindent()
                .unindent()
                .add(")");
        builder.initializer(cb.build());
        typeBuilder.addField(builder.build());
    }

    private void addPolymorphicMetadata(DtoPolymorphism<ImmutableType, ImmutableProp> polymorphism) {
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
                .add("$T.$L", dtoType.getBaseType().getFetcherClassName(), "$")
                .indent();
        addFetcherFields(dtoType, lsiDtoType, cb);
        for (DtoPolymorphicBranch<ImmutableType, ImmutableProp> branch : polymorphism.getTypeBranches()) {
            addPolymorphicTypeFetcherBranch(branch, cb);
        }
        cb.add(",\n");
        addPolymorphicConverter(polymorphism, cb);
        cb
                .unindent()
                .unindent()
                .add(")");
        builder.initializer(cb.build());
        typeBuilder.addField(builder.build());
    }

    private void addPolymorphicTypeFetcherBranch(
            DtoPolymorphicBranch<ImmutableType, ImmutableProp> branch,
            CodeBlock.Builder cb
    ) {
        ImmutableType targetType = branch.getTargetType();
        assert targetType != null;
        site.addzero.lsi.jimmer.dto.DtoType branchLsiType = DtoGenerationExtensionsKt.bodyType(
                lsiPolymorphicBranch(branch),
                lsiGraph
        );
        if (targetType.equals(dtoType.getBaseType())) {
            addFetcherFields(branch.getDtoType(), branchLsiType, cb);
            return;
        }
        cb.add("\n.forType($T.$L", targetType.getFetcherClassName(), "$").indent();
        addFetcherFields(branch.getDtoType(), branchLsiType, cb);
        cb.unindent().add("\n)");
    }

    private void addPolymorphicConverter(
            DtoPolymorphism<ImmutableType, ImmutableProp> polymorphism,
            CodeBlock.Builder cb
    ) {
        cb.beginControlFlow("base ->");
        cb.addStatement("$T<?> actualType = (($T)base).__type().getJavaClass()", Class.class, ImmutableSpi.class);
        for (DtoPolymorphicBranch<ImmutableType, ImmutableProp> branch : polymorphism.getTypeBranches()) {
            ImmutableType targetType = branch.getTargetType();
            assert targetType != null;
            cb.beginControlFlow("if (actualType == $T.class)", targetType.getClassName());
            cb.addStatement(
                    "return new $T(($T)base)",
                    getDtoClassName(branch.getClassName()),
                    targetType.getClassName()
            );
            cb.endControlFlow();
        }
        DtoPolymorphicBranch<ImmutableType, ImmutableProp> defaultBranch = polymorphism.getDefaultBranch();
        if (defaultBranch != null) {
            cb.addStatement("return new $T(base)", getDtoClassName(defaultBranch.getClassName()));
        } else {
            cb.addStatement(
                    "throw new $T($S + actualType.getName() + $S)",
                    IllegalArgumentException.class,
                    "Cannot convert entity object to polymorphic DTO \"" +
                            getDtoClassName().canonicalName() +
                            "\" because there is no branch for actual entity type \"",
                    "\""
            );
        }
        cb.endControlFlow();
    }

    private void addFetcherFields(
            DtoType<ImmutableType, ImmutableProp> dtoType,
            site.addzero.lsi.jimmer.dto.DtoType lsiType,
            CodeBlock.Builder cb
    ) {
        for (DtoProp<ImmutableType, ImmutableProp> prop : dtoType.getDtoProps()) {
            if (prop.getNextProp() == null) {
                addFetcherField(
                        prop,
                        DtoGenerationExtensionsKt.baseProp(lsiType, lsiGraph, prop.getName()),
                        cb
                );
            }
        }
        List<DtoBaseProp> hiddenLsiProps = DtoGenerationExtensionsKt.hiddenFlatPropsInDeclarationOrder(
                lsiType,
                lsiGraph
        );
        for (DtoProp<ImmutableType, ImmutableProp> hiddenProp : dtoType.getHiddenFlatProps()) {
            if (!hiddenProp.getBaseProp().isId()) {
                addHiddenFetcherField(
                        hiddenProp,
                        hiddenLsiProps.stream()
                                .filter(prop -> prop.getName().equals(hiddenProp.getName()))
                                .findFirst()
                                .orElseThrow(() -> new DtoException(
                                        "No frozen hidden flat property \"" + hiddenProp.getName() + "\""
                                )),
                        cb
                );
            }
        }
        for (FoldProp<ImmutableType, ImmutableProp> foldProp : dtoType.getFoldProps()) {
            site.addzero.lsi.jimmer.dto.DtoFoldProp lsiFoldProp = DtoGenerationExtensionsKt.foldProp(
                    lsiType,
                    lsiGraph,
                    foldProp.getName()
            );
            addFetcherFields(
                    foldProp.getTargetType(),
                    DtoGenerationExtensionsKt.generatedTargetType(lsiFoldProp, lsiGraph),
                    cb
            );
        }
    }

    private void addFetcherField(
            DtoProp<ImmutableType, ImmutableProp> prop,
            DtoBaseProp lsiProp,
            CodeBlock.Builder cb
    ) {
        if (prop.getBaseProp().getAnnotation(Id.class) == null) {
            boolean configured = lsiProp.getConfig() != null;
            if (prop.getTarget() != null) {
                if (prop.isRecursive()) {
                    cb.add("\n.$N(", StringUtil.identifier("recursive", prop.getBaseProp().getName()));
                } else {
                    cb.add("\n.$N(", prop.getBaseProp().getName());
                }
                if (configured) {
                    cb.add("\n$>");
                }
                if (!prop.isRecursive()) {
                    cb.add("$T.METADATA.getFetcher()", getPropElementName(prop));
                    if (configured) {
                        cb.add(", \n");
                    }
                }
            } else {
                cb.add("\n.$N(", prop.getBaseProp().getName());
            }
            if (configured) {
                addConfigLambda(cb, lsiProp);
                cb.add("$<\n");
            }
            cb.add(")");
        }
    }

    private void addConfigLambda(
            CodeBlock.Builder cb,
            DtoBaseProp prop
    ) {
        cb.add(
                "$L",
                AptDtoConfigRenderer.render(
                        prop,
                        lsiGraph,
                        immutableSchema,
                        lsiWorkspace,
                        configContractResolution
                )
        );
    }

    private void addAccessorField(DtoProp<ImmutableType, ImmutableProp> prop) {
        if (isSimpleProp(prop)) {
            return;
        }
        DtoBaseProp lsiProp = DtoGenerationExtensionsKt.baseProp(
                lsiDtoType,
                lsiGraph,
                prop.getName()
        );
        addAccessorField(
                prop,
                accessorFieldName(prop.getName()),
                DtoAccessorExtensionsKt.acceptsNullInAccessor(lsiProp, lsiGraph),
                true,
                lsiProp
        );
    }

    private void addFoldNullGuardAccessorField(FoldProp<ImmutableType, ImmutableProp> prop) {
        DtoProp<ImmutableType, ImmutableProp> nullGuardProp = prop.getNullGuardProp();
        if (nullGuardProp != null) {
            addAccessorField(
                    nullGuardProp,
                    foldNullGuardAccessorFieldName(prop),
                    true,
                    false,
                    null
            );
        }
    }

    private void addAccessorField(
            DtoProp<ImmutableType, ImmutableProp> prop,
            String fieldName,
            boolean acceptNull,
            boolean withConverters,
            @Nullable DtoBaseProp lsiProp
    ) {
        FieldSpec.Builder builder = FieldSpec.builder(
                org.babyfish.jimmer.apt.immutable.generator.Constants.DTO_PROP_ACCESSOR_CLASS_NAME,
                fieldName,
                Modifier.PRIVATE,
                Modifier.STATIC,
                Modifier.FINAL
        );
        CodeBlock.Builder cb = CodeBlock.builder();
        cb.add("new $T(", org.babyfish.jimmer.apt.immutable.generator.Constants.DTO_PROP_ACCESSOR_CLASS_NAME);
        cb.indent();

        DtoProp<ImmutableType, ImmutableProp> tailProp = prop.toTailProp();
        if (withConverters) {
            Objects.requireNonNull(lsiProp, "Frozen DTO property is required for converter accessors");
        }
        cb.add("\n$L", acceptNull);

        if (prop.getNextProp() == null) {
            cb.add(",\nnew int[] { $T.$L }", dtoType.getBaseType().getProducerClassName(), prop.getBaseProp().getSlotName());
        } else {
            cb.add(",\nnew int[] {");
            cb.indent();
            boolean addComma = false;
            for (DtoProp<ImmutableType, ImmutableProp> p = prop; p != null; p = p.getNextProp()) {
                if (addComma) {
                    cb.add(",");
                } else {
                    addComma = true;
                }
                cb.add("\n$T.$L", p.getBaseProp().getDeclaringType().getProducerClassName(), p.getBaseProp().getSlotName());
            }
            cb.unindent();
            cb.add("\n}");
        }

        if (withConverters && prop.isIdOnly()) {
            if (dtoType.getModifiers().contains(DtoModifier.SPECIFICATION)) {
                cb.add(",\nnull");
            } else {
                cb.add(
                        ",\n$T.$L($T.class, ",
                        org.babyfish.jimmer.apt.immutable.generator.Constants.DTO_PROP_ACCESSOR_CLASS_NAME,
                        tailProp.getBaseProp().isList() ? "idListGetter" : "idReferenceGetter",
                        tailProp.getBaseProp().getTargetType().getClassName()
                );
                addConverterLoading(cb, prop, false);
                cb.add(")");

                cb.add(
                        ",\n$T.$L($T.class, ",
                        org.babyfish.jimmer.apt.immutable.generator.Constants.DTO_PROP_ACCESSOR_CLASS_NAME,
                        tailProp.getBaseProp().isList() ? "idListSetter" : "idReferenceSetter",
                        tailProp.getBaseProp().getTargetType().getClassName()
                );
                addConverterLoading(cb, prop, false);
                cb.add(")");
            }
        } else if (withConverters && tailProp.getTarget() != null) {
            if (dtoType.getModifiers().contains(DtoModifier.SPECIFICATION)) {
                cb.add(",\nnull");
            } else {
                boolean reusableTargetType = lsiTailProp(prop).getTargetTypeReference() != null;
                if (reusableTargetType || tailProp.getTargetType().getPolymorphism() != null) {
                    cb.add(
                            ",\n$T.<$T, $T>$L($T.METADATA.getConverter())",
                            org.babyfish.jimmer.apt.immutable.generator.Constants.DTO_PROP_ACCESSOR_CLASS_NAME,
                            tailProp.getBaseProp().getTargetType().getClassName(),
                            getPropElementName(prop),
                            tailProp.getBaseProp().isList() ? "objectListGetter" : "objectReferenceGetter",
                            getPropElementName(prop)
                    );
                } else {
                    cb.add(
                            ",\n$T.<$T, $T>$L($T::new)",
                            org.babyfish.jimmer.apt.immutable.generator.Constants.DTO_PROP_ACCESSOR_CLASS_NAME,
                            tailProp.getBaseProp().getTargetType().getClassName(),
                            getPropElementName(prop),
                            tailProp.getBaseProp().isList() ? "objectListGetter" : "objectReferenceGetter",
                            getPropElementName(prop)
                    );
                }
                cb.add(
                        ",\n$T.$L($T::$L)",
                        org.babyfish.jimmer.apt.immutable.generator.Constants.DTO_PROP_ACCESSOR_CLASS_NAME,
                        tailProp.getBaseProp().isList() ? "objectListSetter" : "objectReferenceSetter",
                        getPropElementName(prop),
                        reusableTargetType ?
                                "toImmutable" :
                                tailProp.getTargetType().getBaseType().isEntity() ? "toEntity" : "toImmutable"
                );
            }
        } else if (withConverters && lsiProp.getEnumType() != null) {
            if (dtoType.getModifiers().contains(DtoModifier.SPECIFICATION)) {
                cb.add(",\nnull");
            } else {
                cb.add(
                        ",\n$L",
                        AptDtoEnumRenderer.renderEnumToScalarLambda(
                                lsiProp,
                                lsiGraph,
                                immutableSchema,
                                lsiWorkspace
                        )
                );
            }
            cb.add(
                    ",\n$L",
                    AptDtoEnumRenderer.renderScalarToEnumLambda(
                            lsiProp,
                            lsiGraph,
                            immutableSchema,
                            lsiWorkspace
                    )
            );
        } else if (withConverters && converterMetadataOf(prop) != null) {
            cb.add(",\narg -> ");
            addConverterLoading(cb, prop, true);
            cb.add(".output(arg)");
            cb.add(",\narg -> ");
            addConverterLoading(cb, prop, true);
            cb.add(".input(arg)");
        }

        cb.unindent();
        cb.add("\n)");
        builder.initializer(cb.build());
        typeBuilder.addField(builder.build());
    }

    private void addConverterLoading(CodeBlock.Builder cb, DtoProp<ImmutableType, ImmutableProp> prop, boolean forList) {
        ImmutableProp baseProp = prop.toTailProp().getBaseProp();
        cb.add(
                "$T.$L.unwrap().$L",
                baseProp.getDeclaringType().getPropsClassName(),
                StringUtil.snake(baseProp.getName(), StringUtil.SnakeCase.UPPER),
                prop.toTailProp().getBaseProp().isAssociation(true) ?
                        "getAssociatedIdConverter(" + forList + ")" :
                        "getConverter()"
        );
    }

    private boolean isSimpleProp(DtoProp<ImmutableType, ImmutableProp> prop) {
        if (prop.getNextProp() != null) {
            return false;
        }
        if (prop.getBaseProp().isDiscriminator()) {
            return false;
        }
        if ((prop.isNullable() && (!prop.isBaseNullable() || dtoType.getModifiers().contains(DtoModifier.SPECIFICATION))) ||
                (prop.getBaseProp().getConverterMetadata() != null &&
                        !dtoType.getModifiers().contains(DtoModifier.INPUT) &&
                        !dtoType.getModifiers().contains(DtoModifier.SPECIFICATION))
        ) {
            return false;
        }
        return getPropTypeName(prop).equals(prop.getBaseProp().getTypeName());
    }

    private void addHiddenFetcherField(
            DtoProp<ImmutableType, ImmutableProp> prop,
            DtoBaseProp lsiProp,
            CodeBlock.Builder cb
    ) {
        if (!"flat".equals(prop.getFuncName())) {
            addFetcherField(prop, lsiProp, cb);
            return;
        }
        DtoType<ImmutableType, ImmutableProp> targetDtoType = prop.getTargetType();
        assert targetDtoType != null;
        site.addzero.lsi.jimmer.dto.DtoType targetLsiType = DtoGenerationExtensionsKt.generatedTargetType(
                lsiProp,
                lsiGraph
        );
        if (targetLsiType == null) {
            throw new DtoException(
                    "Frozen flat DTO property \"" + lsiProp.getName() +
                            "\" has no generated target type"
            );
        }
        cb.add("\n.$N($>", prop.getBaseProp().getName());
        cb.add("$T.$L$>", prop.getBaseProp().getTargetType().getFetcherClassName(), "$");
        for (DtoProp<ImmutableType, ImmutableProp> childProp : targetDtoType.getDtoProps()) {
            addHiddenFetcherField(
                    childProp,
                    DtoGenerationExtensionsKt.baseProp(targetLsiType, lsiGraph, childProp.getName()),
                    cb
            );
        }
        cb.add("$<$<\n)");
    }

    private void addField(AbstractProp prop) {
        if (prop instanceof UserProp) {
            addField((UserProp) prop);
            return;
        }
        TypeName typeName = getPropTypeName(prop);
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
        if (prop instanceof DtoProp<?, ?>) {
            DtoProp<ImmutableType, ImmutableProp> dtoProp = asDtoProp(prop);
            if (dtoType.getModifiers().contains(DtoModifier.INPUT) &&
                    dtoProp.getInputModifier() == DtoModifier.FIXED) {
                builder.addAnnotation(org.babyfish.jimmer.apt.immutable.generator.Constants.FIXED_INPUT_FIELD_CLASS_NAME);
            }
        }
        builder.addAnnotations(
                AptDtoPropAnnotationRenderer.renderField(
                        DtoGenerationExtensionsKt.prop(lsiDtoType, lsiGraph, prop.getName()),
                        annotationContract,
                        immutableSchema,
                        lsiWorkspace,
                        isBuilderRequired ? ctx.getJacksonTypes().jsonDeserialize.reflectionName() : null
                )
        );
        typeBuilder.addField(builder.build());
    }

    private void addField(UserProp prop) {
        TypeName typeName = getPropTypeName(prop);
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
        String stateFieldName = DtoAccessorExtensionsKt.dtoLoadedStateStorageNameOrNull(
                DtoGenerationExtensionsKt.prop(lsiDtoType, lsiGraph, prop.getName()),
                lsiGraph,
                LsiLanguage.JAVA
        );
        if (stateFieldName == null) {
            return;
        }
        typeBuilder.addField(
                TypeName.BOOLEAN,
                stateFieldName,
                ctx.getDtoFieldModifier()
        );
    }

    private void addAccessorDeclaration(AbstractProp prop) {
        TypeName typeName = getPropTypeName(prop);
        site.addzero.lsi.jimmer.dto.DtoProp lsiProp =
                DtoGenerationExtensionsKt.prop(lsiDtoType, lsiGraph, prop.getName());
        MethodSpec.Builder getterBuilder = MethodSpec
                .methodBuilder(getterName(prop))
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
        TypeName typeName = getPropTypeName(prop);
        site.addzero.lsi.jimmer.dto.DtoProp lsiProp =
                DtoGenerationExtensionsKt.prop(lsiDtoType, lsiGraph, prop.getName());
        String getterName = getterName(prop);
        String setterName = setterName(prop);
        String stateFieldName = DtoAccessorExtensionsKt.dtoLoadedStateStorageNameOrNull(
                DtoGenerationExtensionsKt.prop(lsiDtoType, lsiGraph, prop.getName()),
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
                if (foldProp.getNullGuardProp() != null) {
                    builder.addStatement(
                            "this.$L = $L.get(base) != null ? new $T(base) : null",
                            foldProp.getName(),
                            foldNullGuardAccessorFieldName(foldProp),
                            getPropTypeName(foldProp)
                    );
                } else {
                    builder.addStatement(
                            "this.$L = new $T(base)",
                            foldProp.getName(),
                            getPropTypeName(foldProp)
                    );
                }
                continue;
            }
            if (!(abstractProp instanceof DtoProp<?, ?>)) {
                continue;
            }
            DtoProp<ImmutableType, ImmutableProp> prop = asDtoProp(abstractProp);
            if (isSimpleProp(prop)) {
                if (prop.isNullable()) {
                    builder.addStatement(
                            "this.$L = (($T)base).__isLoaded($T.byIndex($T.$L)) ? base.$L() : null",
                            prop.getName(),
                            ImmutableSpi.class,
                            org.babyfish.jimmer.apt.immutable.generator.Constants.PROP_ID_CLASS_NAME,
                            dtoType.getBaseType().getProducerClassName(),
                            prop.getBaseProp().getSlotName(),
                            prop.getBaseProp().getGetterName()
                    );
                } else {
                    builder.addStatement(
                            "this.$L = base.$L()",
                            prop.getName(),
                            prop.getBaseProp().getGetterName()
                    );
                }
            } else {
                if (!prop.isNullable() && prop.isBaseNullable()) {
                    builder.addStatement(
                            "this.$L = $L.get($>\n" +
                                    "base,\n" +
                                    "$S\n" +
                                    "$<)",
                            prop.getName(),
                            accessorFieldName(prop.getName()),
                            "Cannot convert \"" +
                                    dtoType.getBaseType().getClassName() +
                                    "\" to " +
                                    "\"" +
                                    getDtoClassName() +
                                    "\" because the cannot get non-null " +
                                    "value for \"" +
                                    prop.getName() +
                                    "\""
                    );
                } else {
                    builder.addStatement(
                            "this.$L = $L.get(base)",
                            prop.getName(),
                            accessorFieldName(prop.getName())
                    );
                }
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
                if (foldProp.isNullable()) {
                    builder.beginControlFlow("if (this.$L != null)", foldProp.getName());
                }
                builder.addStatement("this.$L.__applyTo(__draft)", foldProp.getName());
                if (foldProp.isNullable()) {
                    builder.endControlFlow();
                }
                continue;
            }
            if (!(abstractProp instanceof DtoProp<?, ?>)) {
                continue;
            }
            DtoProp<ImmutableType, ImmutableProp> prop = asDtoProp(abstractProp);
            if (prop.getBaseProp().isJavaFormula()) {
                continue;
            }
            if (prop.getNextProp() == null && prop.getBaseProp().isDiscriminator()) {
                continue;
            }
            String stateFieldName = DtoAccessorExtensionsKt.dtoLoadedStateStorageNameOrNull(
                    DtoGenerationExtensionsKt.prop(lsiDtoType, lsiGraph, prop.getName()),
                    lsiGraph,
                    LsiLanguage.JAVA
            );
            boolean fuzzy = prop.getInputModifier() == DtoModifier.FUZZY && prop.isNullable();
            if (stateFieldName != null) {
                builder.beginControlFlow("if (this.$L)", stateFieldName);
            } else if (fuzzy) {
                builder.beginControlFlow("if (this.$L != null)", prop.getName());
            }
            if (isSimpleProp(prop)) {
                builder.addStatement("__draft.$L(this.$L)", prop.getBaseProp().getSetterName(), prop.getName());
            } else {
                ImmutableProp tailBaseProp = prop.toTailProp().getBaseProp();
                if (tailBaseProp.isList() && tailBaseProp.isAssociation(true)) {
                    builder.addStatement(
                            "$L.set(__draft, this.$L != null ? this.$L : $T.emptyList())",
                            accessorFieldName(prop.getName()),
                            prop.getName(),
                            prop.getName(),
                            org.babyfish.jimmer.apt.immutable.generator.Constants.COLLECTIONS_CLASS_NAME
                    );
                } else {
                    builder.addStatement(
                            "$L.set(__draft, this.$L)",
                            accessorFieldName(prop.getName()),
                            prop.getName()
                    );
                }
            }
            if (stateFieldName != null || fuzzy) {
                builder.endControlFlow();
            }
        }
        typeBuilder.addMethod(builder.build());
    }

    private void addToEntity(boolean withId) {
        boolean idOverridable =
                dtoType.getModifiers().contains(DtoModifier.INPUT) &&
                        dtoType.getBaseType().isEntity();
        if (withId && !idOverridable) {
            return;
        }
        DtoBaseProp discriminatorProp = polymorphicInputDiscriminatorProp();
        ImmutableProp baseIdProp = withId ? dtoType.getBaseType().getIdProp() : null;
        MethodSpec.Builder builder = MethodSpec
                .methodBuilder(dtoType.getBaseType().isEntity() ?
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
            addDefaultPolymorphicInputToEntityBody(builder, baseIdProp, discriminatorProp);
        } else {
            if (discriminatorProp != null && isTypedPolymorphicInputBranch()) {
                addTypedPolymorphicInputDiscriminatorValidation(builder, discriminatorProp);
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

    private void addDefaultPolymorphicInputToEntityBody(
            MethodSpec.Builder builder,
            @Nullable ImmutableProp baseIdProp,
            DtoBaseProp discriminatorProp
    ) {
        String discriminatorGetter = "this." + DtoAccessorExtensionsKt.serializerValueAccessorName(
                discriminatorProp,
                LsiLanguage.JAVA,
                lsiGraph,
                immutableSchema
        ) + "()";
        List<ImmutableType> concreteTypes = knownConcreteTypes(dtoType.getBaseType());
        for (ImmutableType concreteType : concreteTypes) {
            String value = concreteType.getDiscriminatorValue();
            if (value == null) {
                continue;
            }
            builder.beginControlFlow(
                    "if ($T.equals($L, $T.get($T.class).getInheritanceInfo().discriminatorValue($S)))",
                    Constants.OBJECTS_CLASS_NAME,
                    discriminatorGetter,
                    Constants.RUNTIME_TYPE_CLASS_NAME,
                    polymorphicRootType().getClassName(),
                    value
            );
            builder.addCode(
                    "return $T.$L.produce(__draft -> {$>\n",
                    concreteType.getDraftClassName(),
                    "$"
            );
            builder.addStatement("this.__applyTo(__draft)");
            if (baseIdProp != null) {
                builder.beginControlFlow("if (id != null)");
                builder.addStatement("__draft.$L($L)", baseIdProp.getSetterName(), "id");
                builder.endControlFlow();
            }
            builder.addCode("$<});\n");
            builder.endControlFlow();
        }
        builder.addStatement(
                "throw new $T($S + $L + $S)",
                IllegalArgumentException.class,
                "Illegal discriminator value \"",
                discriminatorGetter,
                "\" for polymorphic input DTO branch \"" + getDtoClassName().canonicalName() + "\""
        );
    }

    private void addTypedPolymorphicInputDiscriminatorValidation(
            MethodSpec.Builder builder,
            DtoBaseProp discriminatorProp
    ) {
        String value = dtoType.getBaseType().getDiscriminatorValue();
        if (value == null) {
            return;
        }
        String discriminatorGetter = "this." + DtoAccessorExtensionsKt.serializerValueAccessorName(
                discriminatorProp,
                LsiLanguage.JAVA,
                lsiGraph,
                immutableSchema
        ) + "()";
        builder.beginControlFlow(
                "if (!$T.equals($L, $T.get($T.class).getInheritanceInfo().discriminatorValue($S)))",
                Constants.OBJECTS_CLASS_NAME,
                discriminatorGetter,
                Constants.RUNTIME_TYPE_CLASS_NAME,
                polymorphicRootType().getClassName(),
                value
        );
        builder.addStatement(
                "throw new $T($S + $L + $S)",
                IllegalArgumentException.class,
                "Discriminator value \"",
                discriminatorGetter,
                "\" does not match polymorphic input DTO branch \"" +
                        getDtoClassName().canonicalName() +
                        "\" whose entity type is \"" +
                        dtoType.getBaseType().getQualifiedName() +
                        "\""
        );
        builder.endControlFlow();
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

    private ImmutableType polymorphicRootType() {
        ImmutableType rootType = dtoType.getBaseType().getInheritanceRoot();
        return rootType != null ? rootType : dtoType.getBaseType();
    }

    private List<ImmutableType> knownConcreteTypes(ImmutableType baseType) {
        List<ImmutableType> types = new ArrayList<>();
        if (baseType.isInstantiable()) {
            types.add(baseType);
        }
        for (ImmutableType type : ctx.getImmutableTypes()) {
            if (type != baseType &&
                    type.isEntity() &&
                    type.isInstantiable() &&
                    isAssignableFrom(baseType, type)) {
                types.add(type);
            }
        }
        types.sort(Comparator.comparing(ImmutableType::getQualifiedName));
        return types;
    }

    private static boolean isAssignableFrom(ImmutableType base, ImmutableType type) {
        for (ImmutableType current = type; current != null; current = current.getPrimarySuperType()) {
            if (current == base) {
                return true;
            }
        }
        return false;
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
        MethodSpec.Builder builder = MethodSpec
                .methodBuilder("applyTo")
                .addModifiers(Modifier.PUBLIC);
        if (!isNestedSpecificationFragment()) {
            builder.addAnnotation(Override.class)
                    .addParameter(
                            ParameterSpec.builder(
                                    ParameterizedTypeName.get(
                                            org.babyfish.jimmer.apt.immutable.generator.Constants.SPECIFICATION_ARGS_CLASS_NAME,
                                            dtoType.getBaseType().getClassName(),
                                            dtoType.getBaseType().getTableClassName()
                                    ),
                                    "args"
                            ).build()
                    );
        } else {
            builder.addParameter(
                    ParameterSpec
                            .builder(
                                    org.babyfish.jimmer.apt.immutable.generator.Constants.PREDICATE_APPLIER_CLASS_NAME,
                                    "__applier"
                            )
                            .build()
            );
        }

        List<ImmutableProp> stack = Collections.emptyList();
        if (!isNestedSpecificationFragment()) {
            builder.addStatement(
                    "$T __applier = args.getApplier()",
                    org.babyfish.jimmer.apt.immutable.generator.Constants.PREDICATE_APPLIER_CLASS_NAME
            );
        }
        for (AbstractProp abstractProp : dtoType.getProps()) {
            if (abstractProp instanceof FoldProp<?, ?>) {
                FoldProp<ImmutableType, ImmutableProp> foldProp = asFoldProp(abstractProp);
                stack = addStackOperations(builder, stack, Collections.emptyList());
                builder.beginControlFlow("if (this.$L != null)", foldProp.getName());
                if (dtoType.getBaseType().isEntity()) {
                    builder.addStatement("this.$L.applyTo(args)", foldProp.getName());
                } else {
                    builder.addStatement("this.$L.applyTo(__applier)", foldProp.getName());
                }
                builder.endControlFlow();
                continue;
            }
            if (!(abstractProp instanceof DtoProp<?, ?>)) {
                continue;
            }
            DtoProp<ImmutableType, ImmutableProp> prop = asDtoProp(abstractProp);
            List<ImmutableProp> newStack = new ArrayList<>(stack.size() + 2);
            DtoProp<ImmutableType, ImmutableProp> tailProp = prop.toTailProp();
            for (DtoProp<ImmutableType, ImmutableProp> p = prop; p != null; p = p.getNextProp()) {
                if (p != tailProp || p.getTarget() != null) {
                    newStack.add(p.getBaseProp());
                }
            }
            stack = addStackOperations(builder, stack, newStack);
            addPredicateOperation(builder, prop);
        }
        addStackOperations(builder, stack, Collections.emptyList());
        typeBuilder.addMethod(builder.build());
    }

    private List<ImmutableProp> addStackOperations(
            MethodSpec.Builder builder,
            List<ImmutableProp> stack,
            List<ImmutableProp> newStack
    ) {
        int size = Math.min(stack.size(), newStack.size());
        int sameCount = size;
        for (int i = 0; i < size; i++) {
            if (stack.get(i) != newStack.get(i)) {
                sameCount = i;
                break;
            }
        }
        for (int i = stack.size() - sameCount; i > 0; --i) {
            builder.addStatement("__applier.pop()");
        }
        for (ImmutableProp prop : newStack.subList(sameCount, newStack.size())) {
            builder.addStatement(
                    "__applier.push($T.$L.unwrap())",
                    prop.getDeclaringType().getPropsClassName(),
                    StringUtil.snake(prop.getName(), StringUtil.SnakeCase.UPPER)
            );
        }
        return newStack;
    }

    private void addPredicateOperation(MethodSpec.Builder builder, DtoProp<ImmutableType, ImmutableProp> prop) {
        String propName = prop.getName();
        String propGetter = getterName(prop);
        DtoProp<ImmutableType, ImmutableProp> tailProp = prop.toTailProp();
        if (tailProp.getTarget() != null) {
            builder.beginControlFlow("if (this.$L != null)", propName);
            if (tailProp.getBaseProp().isAssociation(true)) {
                builder.addStatement("this.$L.applyTo(args.child())", propName);
            } else {
                builder.addStatement("this.$L.applyTo(args.getApplier())", propName);
            }
            builder.endControlFlow();
            return;
        }

        String funcName = tailProp.getFuncName();
        String javaMethodName = funcName;
        if (funcName == null) {
            funcName = "eq";
            javaMethodName = "eq";
        } else if ("null".equals(funcName)) {
            javaMethodName = "isNull";
        } else if ("notNull".equals(funcName)) {
            javaMethodName = "isNotNull";
        } else if ("id".equals(funcName)) {
            funcName = "associatedIdEq";
            javaMethodName = "associatedIdEq";
        }

        CodeBlock.Builder cb = CodeBlock.builder();
        if (org.babyfish.jimmer.dto.compiler.Constants.MULTI_ARGS_FUNC_NAMES.contains(funcName)) {
            cb.add("__applier.$L(new $T[] { ", javaMethodName, org.babyfish.jimmer.apt.immutable.generator.Constants.IMMUTABLE_PROP_CLASS_NAME);
            boolean addComma = false;
            for (ImmutableProp baseProp : tailProp.getBasePropMap().values()) {
                if (addComma) {
                    cb.add(", ");
                } else {
                    addComma = true;
                }
                cb.add(
                        "$T.$L.unwrap()",
                        baseProp.getDeclaringType().getPropsClassName(),
                        StringUtil.snake(baseProp.getName(), StringUtil.SnakeCase.UPPER)
                );
            }
            cb.add(" }, ");
        } else {
            cb.add(
                    "__applier.$L($T.$L.unwrap(), ",
                    funcName,
                    tailProp.getBaseProp().getDeclaringType().getPropsClassName(),
                    StringUtil.snake(tailProp.getBaseProp().getName(), StringUtil.SnakeCase.UPPER)
            );
        }
        if (isSpecificationConverterRequired(prop)) {
            cb.add(
                    "$L(this.$L())",
                    StringUtil.identifier("__convert", propName),
                    propGetter
            );
        } else {
            cb.add("this.$L()", propGetter);
        }
        if ("like".equals(funcName) || "notLike".equals(funcName)) {
            cb.add(", ");
            cb.add(tailProp.getLikeOptions().contains(LikeOption.INSENSITIVE) ? "true" : "false");
            cb.add(", ");
            cb.add(tailProp.getLikeOptions().contains(LikeOption.MATCH_START) ? "true" : "false");
            cb.add(", ");
            cb.add(tailProp.getLikeOptions().contains(LikeOption.MATCH_END) ? "true" : "false");
        }
        cb.addStatement(")");
        builder.addCode(cb.build());
    }

    private void addSpecificationConverter(DtoProp<ImmutableType, ImmutableProp> prop) {
        if (!isSpecificationConverterRequired(prop)) {
            return;
        }
        ImmutableProp baseProp = prop.toTailProp().getBaseProp();
        TypeName baseTypeName = null;
        String funcName = prop.getFuncName();
        if (funcName != null) {
            switch (funcName) {
                case "id":
                    baseTypeName = baseProp.getTargetType().getIdProp().getTypeName();
                    if (baseProp.isList() && !dtoType.getModifiers().contains(DtoModifier.SPECIFICATION)) {
                        baseTypeName = ParameterizedTypeName.get(
                                org.babyfish.jimmer.apt.immutable.generator.Constants.LIST_CLASS_NAME,
                                baseTypeName.box()
                        );
                    }
                    break;
                case "null":
                case "notNull":
                    baseTypeName = TypeName.BOOLEAN;
                    break;
                case "valueIn":
                case "valueNotIn":
                    baseTypeName = ParameterizedTypeName.get(
                            org.babyfish.jimmer.apt.immutable.generator.Constants.LIST_CLASS_NAME,
                            baseProp.getTypeName().box()
                    );
                    break;
                case "associatedIdEq":
                case "associatedIdNe":
                    baseTypeName = baseProp.getTargetType().getIdProp().getTypeName();
                    break;
                case "associatedIdIn":
                case "associatedIdNotIn":
                    baseTypeName = ParameterizedTypeName.get(
                            org.babyfish.jimmer.apt.immutable.generator.Constants.LIST_CLASS_NAME,
                            baseProp.getTargetType().getIdProp().getTypeName().box()
                    );
            }
        }
        if (baseTypeName == null) {
            baseTypeName = baseProp.getTypeName();
        }
        baseTypeName = baseTypeName.box();
        TypeName dtoPropTypeName = getPropTypeName(prop);
        MethodSpec.Builder builder = MethodSpec
                .methodBuilder(StringUtil.identifier("__convert", prop.getName()))
                .addModifiers(Modifier.PRIVATE)
                .addParameter(dtoPropTypeName, "value")
                .returns(baseTypeName);
        CodeBlock.Builder cb = CodeBlock.builder();
        cb.beginControlFlow("if ($L == null)", prop.getName());
        cb.addStatement("return null");
        cb.endControlFlow();
        DtoBaseProp lsiEnumProp = lsiEnumPropOrNull(prop);
        if (lsiEnumProp != null) {
            cb.add(
                    "$L",
                    AptDtoEnumRenderer.renderScalarToEnumConversion(
                            lsiEnumProp,
                            lsiGraph,
                            immutableSchema,
                            lsiWorkspace,
                            "value"
                    )
            );
        } else {
            cb.addStatement(
                    "return $T.$L.unwrap().<$T, $T>$L.input(value)",
                    baseProp.getDeclaringType().getPropsClassName(),
                    StringUtil.snake(baseProp.getName(), StringUtil.SnakeCase.UPPER),
                    baseTypeName,
                    getPropTypeName(prop).box(),
                    baseProp.isAssociation(true) ?
                            "getAssociatedIdConverter(" + (prop.isFunc("associatedIdIn", "associatedIdNotIn") ? "true" : "false") + ")" :
                            "getConverter(" + (prop.isFunc("valueIn", "valueNotIn") ? "true" : "") + ")"
            );
        }
        builder.addCode(cb.build());
        typeBuilder.addMethod(builder.build());
    }

    public TypeName getPropTypeName(AbstractProp prop) {
        if (prop instanceof DtoProp<?, ?>) {
            return getPropTypeName(asDtoProp(prop));
        }
        if (prop instanceof FoldProp<?, ?>) {
            FoldProp<ImmutableType, ImmutableProp> foldProp = asFoldProp(prop);
            site.addzero.lsi.jimmer.dto.DtoProp polymorphicRootProp =
                    polymorphicRootPropOrNull(foldProp);
            if (polymorphicRootProp != null) {
                return generatedTargetTypeName(polymorphicRootProp);
            }
            return getDtoClassName(targetSimpleName(foldProp));
        }
        return AptDtoTypeRefRenderer.render(
                DtoGenerationExtensionsKt.userProp(lsiDtoType, lsiGraph, prop.getName()).getType(),
                lsiWorkspace
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

    private TypeName getPropTypeName(DtoProp<ImmutableType, ImmutableProp> prop) {

        ImmutableProp baseProp = prop.toTailProp().getBaseProp();

        DtoBaseProp lsiEnumProp = lsiEnumPropOrNull(prop);
        if (lsiEnumProp != null) {
            return AptDtoEnumRenderer.renderScalarType(lsiEnumProp, lsiWorkspace);
        }
        ConverterMetadata metadata = converterMetadataOf(prop);
        final TypeName propElementName = getPropElementName(prop);
        if (dtoType.getModifiers().contains(DtoModifier.SPECIFICATION)) {
            String funcName = prop.toTailProp().getFuncName();
            if (funcName != null) {
                switch (funcName) {
                    case "null":
                    case "notNull":
                        return TypeName.BOOLEAN;
                    case "valueIn":
                    case "valueNotIn":
                        return ParameterizedTypeName.get(
                                org.babyfish.jimmer.apt.immutable.generator.Constants.COLLECTION_CLASS_NAME,
                                metadata != null ?
                                        metadata.getTargetTypeName() :
                                        toListType(
                                                propElementName,
                                                baseProp.isList()
                                        )
                        );
                    case "id":
                    case "associatedIdEq":
                    case "associatedIdNe":
                        final TypeName clientTypeName = baseProp.getTargetType().getIdProp().getClientTypeName();
                        if (prop.isNullable()) {
                            return clientTypeName.box();
                        }
                        return clientTypeName;
                    case "associatedIdIn":
                    case "associatedIdNotIn":
                        return ParameterizedTypeName.get(
                                org.babyfish.jimmer.apt.immutable.generator.Constants.COLLECTION_CLASS_NAME,
                                baseProp.getTargetType().getIdProp().getClientTypeName().box()
                        );
                }
            }
            if (baseProp.isAssociation(true)) {
                return propElementName;
            }
        }
        if (metadata != null) {
            return metadata.getTargetTypeName();
        }

        return toListType(propElementName, baseProp.isList()
                && !(propElementName instanceof ParameterizedTypeName && ((ParameterizedTypeName) propElementName).rawType.equals(Constants.LIST_CLASS_NAME)));
    }

    private static TypeName toListType(TypeName typeName, boolean isList) {
        return isList ? ParameterizedTypeName.get(Constants.LIST_CLASS_NAME, typeName.box()) : typeName;
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

    public TypeName getPropElementName(DtoProp<ImmutableType, ImmutableProp> prop) {
        site.addzero.lsi.jimmer.dto.DtoProp polymorphicRootProp = polymorphicRootPropOrNull(prop);
        if (polymorphicRootProp != null) {
            return generatedTargetTypeName(polymorphicRootProp);
        }
        DtoProp<ImmutableType, ImmutableProp> tailProp = prop.toTailProp();
        DtoBaseProp lsiTailProp = lsiTailProp(prop);
        if (lsiTailProp.getTargetTypeReference() != null) {
            site.addzero.lsi.jimmer.dto.DtoReusableTypeReference targetTypeReference =
                    lsiTailProp.getTargetTypeReference();
            return AptDtoTypeRefRenderer.render(
                    targetTypeReference,
                    lsiWorkspace,
                    JimmerDtoPoetTypeNames.reusableTarget(targetTypeReference, batchRootDtoTypeNames)
            );
        }
        DtoType<ImmutableType, ImmutableProp> targetType = tailProp.getTargetType();
        if (targetType != null) {
            if (tailProp.isRecursive() && !targetType.isFocusedRecursion()) {
                return getDtoClassName();
            }
            if (targetType.getName() == null) {
                List<String> list = new ArrayList<>();
                collectNames(list);
                if (!tailProp.isRecursive() || targetType.isFocusedRecursion()) {
                    list.add(targetSimpleName(tailProp));
                }
                return ClassName.get(
                        root.dtoType.getPackageName(),
                        list.get(0),
                        list.subList(1, list.size()).toArray(EMPTY_STR_ARR)
                );
            }
            return ClassName.get(
                    root.dtoType.getPackageName(),
                    targetType.getName()
            );
        }
        ImmutableProp baseProp = tailProp.getBaseProp();
        TypeName typeName;
        if (tailProp.isIdOnly()) {
            typeName = tailProp.getBaseProp().getTargetType().getIdProp().getTypeName();
        } else if (baseProp.getIdViewBaseProp() != null) {
            typeName = baseProp.getIdViewBaseProp().getTargetType().getIdProp().getClientTypeName();
        } else {
            typeName = tailProp.getBaseProp().getClientTypeName();
        }
        if (typeName.isPrimitive() && prop.isNullable()) {
            return typeName.box();
        }
        return typeName;
    }

    private DtoBaseProp lsiProp(DtoProp<ImmutableType, ImmutableProp> prop) {
        return (DtoBaseProp) DtoGenerationExtensionsKt.prop(lsiDtoType, lsiGraph, prop.getName());
    }

    @Nullable
    private DtoBaseProp lsiEnumPropOrNull(DtoProp<ImmutableType, ImmutableProp> prop) {
        DtoBaseProp lsiProp = lsiProp(prop);
        return lsiProp.getEnumType() != null ? lsiProp : null;
    }

    private DtoBaseProp lsiTailProp(DtoProp<ImmutableType, ImmutableProp> prop) {
        return DtoGenerationExtensionsKt.tailProp(
                lsiProp(prop),
                lsiGraph
        );
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

    private TypeName generatedTargetTypeName(site.addzero.lsi.jimmer.dto.DtoProp prop) {
        site.addzero.lsi.jimmer.dto.DtoType targetType =
                DtoGenerationExtensionsKt.generatedTargetTypeOrNull(prop, lsiGraph);
        if (targetType == null) {
            throw new DtoException(
                    "Promoted DTO root property has no generated target: \"" + prop.getName() + "\""
            );
        }
        LsiPoetTypeName typeName = JimmerDtoPoetTypeNames.requireRegistered(
                targetType,
                generatedDtoTypeNames
        );
        return AptDtoTypeRefRenderer.render(typeName, lsiWorkspace);
    }

    private void collectNames(List<String> list) {
        if (parent == null) {
            list.add(dtoType.getName());
        } else {
            parent.collectNames(list);
            list.add(innerClassName);
        }
    }

    private String targetSimpleName(DtoProp<ImmutableType, ImmutableProp> prop) {
        DtoType<ImmutableType, ImmutableProp> targetType = prop.getTargetType();
        if (targetType == null) {
            throw new IllegalArgumentException("prop is not association");
        }
        if (targetType.getName() != null) {
            return targetType.getName();
        }
        if (prop.isRecursive() && !targetType.isFocusedRecursion()) {
            return innerClassName != null ? innerClassName : dtoType.getName();
        }
        return standardTargetSimpleName("TargetOf_" + prop.getName());
    }

    private String targetSimpleName(FoldProp<ImmutableType, ImmutableProp> prop) {
        return standardTargetSimpleName("TargetOf_" + prop.getName());
    }

    private String accessorFieldName(String propName) {
        return StringUtil.snake(propName + "Accessor", StringUtil.SnakeCase.UPPER);
    }

    private String foldNullGuardAccessorFieldName(FoldProp<ImmutableType, ImmutableProp> prop) {
        return StringUtil.snake(prop.getName() + "NullGuardAccessor", StringUtil.SnakeCase.UPPER);
    }

    private String standardTargetSimpleName(String targetSimpleName) {
        boolean conflict = false;
        for (DtoGenerator generator = this; generator != null; generator = generator.parent) {
            if (generator.getSimpleName().equals(targetSimpleName)) {
                conflict = true;
                break;
            }
        }
        if (!conflict) {
            return targetSimpleName;
        }
        for (int i = 2; i < 100; i++) {
            conflict = false;
            String newTargetSimpleName = targetSimpleName + '_' + i;
            for (DtoGenerator generator = this; generator != null; generator = generator.parent) {
                if (generator.getSimpleName().equals(newTargetSimpleName)) {
                    conflict = true;
                    break;
                }
            }
            if (!conflict) {
                return newTargetSimpleName;
            }
        }
        throw new AssertionError("Dto is too deep");
    }

    private boolean isSpecificationConverterRequired(DtoProp<ImmutableType, ImmutableProp> prop) {
        if (!dtoType.getModifiers().contains(DtoModifier.SPECIFICATION)) {
            return false;
        }
        return lsiEnumPropOrNull(prop) != null || converterMetadataOf(prop) != null;
    }

    private ConverterMetadata converterMetadataOf(DtoProp<ImmutableType, ImmutableProp> prop) {
        String funcName = prop.getFuncName();
        if ("null".equals(funcName) || "notNull".equals(funcName)) {
            return null;
        }
        ImmutableProp baseProp = prop.toTailProp().getBaseProp();
        ConverterMetadata metadata = baseProp.getConverterMetadata();
        if (metadata != null) {
            return metadata;
        }
        if ("id".equals(funcName)) {
            metadata = baseProp.getTargetType().getIdProp().getConverterMetadata();
            if (metadata != null && baseProp.isList() && !dtoType.getModifiers().contains(DtoModifier.SPECIFICATION)) {
                metadata = metadata.toListMetadata(baseProp.context());
            }
            return metadata;
        }
        if ("associatedInEq".equals(funcName) || "associatedInNe".equals(funcName)) {
            return baseProp.getTargetType().getIdProp().getConverterMetadata();
        }
        if ("associatedIdIn".equals(funcName) || "associatedIdNotIn".equals(funcName)) {
            metadata = baseProp.getTargetType().getIdProp().getConverterMetadata();
            if (metadata != null) {
                return metadata.toListMetadata(baseProp.context());
            }
        }
        if (baseProp.getIdViewBaseProp() != null) {
            metadata = baseProp.getIdViewBaseProp().getTargetType().getIdProp().getConverterMetadata();
            if (metadata != null) {
                return baseProp.isList() ? metadata.toListMetadata(baseProp.context()) : metadata;
            }
        }
        return null;
    }

    String getterName(AbstractProp prop) {
        TypeName typeName = getPropTypeName(prop);
        String suffix = prop.getAlias();
        if (suffix.startsWith("is") &&
                suffix.length() > 2 &&
                Character.isUpperCase(suffix.charAt(2)) &&
                typeName.equals(TypeName.BOOLEAN)) {
            suffix = suffix.substring(2);
        }
        return StringUtil.identifier(
                typeName.equals(TypeName.BOOLEAN) ? "is" : "get",
                suffix
        );
    }

    private String setterName(AbstractProp prop) {
        TypeName typeName = getPropTypeName(prop);
        String suffix = prop.getAlias();
        if (suffix.startsWith("is") &&
                suffix.length() > 2 &&
                Character.isUpperCase(suffix.charAt(2)) &&
                typeName.equals(TypeName.BOOLEAN)) {
            suffix = suffix.substring(2);
        }
        return StringUtil.identifier("set", suffix);
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

    private boolean isNestedSpecificationFragment() {
        return DtoAccessorExtensionsKt.isNestedSpecificationFragment(
                lsiDtoType,
                immutableSchema
        );
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
