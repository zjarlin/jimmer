package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.*
import org.babyfish.jimmer.client.ApiIgnore
import org.babyfish.jimmer.compiler.JacksonFamily
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames.create
import org.babyfish.jimmer.compiler.dto.JimmerDtoRendererOptions
import org.babyfish.jimmer.compiler.render.apt.*
import org.babyfish.jimmer.compiler.render.apt.AptDtoBaseContractRenderer.render
import org.babyfish.jimmer.compiler.render.apt.AptDtoDescriptionRenderer.render
import org.babyfish.jimmer.compiler.render.apt.AptDtoDraftWriteRenderer.render
import org.babyfish.jimmer.compiler.render.apt.AptDtoEqualityRenderer.renderEquals
import org.babyfish.jimmer.compiler.render.apt.AptDtoEqualityRenderer.renderHashCode
import org.babyfish.jimmer.compiler.render.apt.AptDtoFoldDraftApplyRenderer.render
import org.babyfish.jimmer.compiler.render.apt.AptDtoHibernateValidatorRenderer.renderEnhancedBeanType
import org.babyfish.jimmer.compiler.render.apt.AptDtoHibernateValidatorRenderer.renderFunctions
import org.babyfish.jimmer.compiler.render.apt.AptDtoJacksonPolymorphismRenderer.renderBranchTypeName
import org.babyfish.jimmer.compiler.render.apt.AptDtoJacksonPolymorphismRenderer.renderRootAnnotations
import org.babyfish.jimmer.compiler.render.apt.AptDtoLoadedStateRenderer.renderBaseInitializer
import org.babyfish.jimmer.compiler.render.apt.AptDtoLoadedStateRenderer.renderStorageField
import org.babyfish.jimmer.compiler.render.apt.AptDtoPoetSupport.generatedAnnotation
import org.babyfish.jimmer.compiler.render.apt.AptDtoPolymorphicBranchRenderer.render
import org.babyfish.jimmer.compiler.render.apt.AptDtoPolymorphicInputRenderer.renderDefaultBranchBody
import org.babyfish.jimmer.compiler.render.apt.AptDtoPolymorphicInputRenderer.renderTypedDiscriminatorValidation
import org.babyfish.jimmer.compiler.render.apt.AptDtoPolymorphicMetadataConverterRenderer.render
import org.babyfish.jimmer.compiler.render.apt.AptDtoPropAnnotationRenderer.renderField
import org.babyfish.jimmer.compiler.render.apt.AptDtoPropAnnotationRenderer.renderGetter
import org.babyfish.jimmer.compiler.render.apt.AptDtoSerializerRenderer.render
import org.babyfish.jimmer.compiler.render.apt.AptDtoSpecificationRenderer.renderApplyTo
import org.babyfish.jimmer.compiler.render.apt.AptDtoSpecificationRenderer.renderConverterOrNull
import org.babyfish.jimmer.compiler.render.apt.AptDtoSpecificationRenderer.renderEntityType
import org.babyfish.jimmer.compiler.render.apt.AptDtoTypeAnnotationRenderer.render
import org.babyfish.jimmer.compiler.render.apt.AptDtoTypeRefRenderer.render
import org.babyfish.jimmer.compiler.render.apt.AptImmutableTypeNameRenderer.renderDraft
import org.babyfish.jimmer.compiler.render.apt.AptImmutableTypeNameRenderer.renderSource
import org.babyfish.jimmer.dto.compiler.DtoPolymorphicBranchKind
import org.babyfish.jimmer.impl.util.StringUtil
import org.jspecify.annotations.NonNull
import org.jspecify.annotations.Nullable
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.*
import site.addzero.lsi.jimmer.dto.*
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiTypeName
import java.util.*
import javax.lang.model.element.Modifier

internal class AptDtoGenerator private constructor(
    lsiGraph: DtoGraph,
    lsiDtoType: DtoType,
    annotationContract: DtoAnnotationContract?,
    interfaceContractResolution: DtoInterfaceContractResolution?,
    configContractResolution: DtoConfigContractResolution?,
    immutableSchema: ImmutableSchema?,
    lsiWorkspace: LsiWorkspace?,
    batchRootDtoTypeNames: Map<DtoTypeId, LsiTypeName>?,
    rendererOptions: JimmerDtoRendererOptions?,
    parent: AptDtoGenerator?,
    innerClassName: String?,
    polymorphicSuperInterfaceName: TypeName?,
    lsiPolymorphicBranch: DtoPolymorphicBranch?
) {
    private val lsiGraph: DtoGraph

    private val lsiDtoType: DtoType

    private val annotationContract: DtoAnnotationContract

    private val interfaceContractResolution: DtoInterfaceContractResolution

    private val configContractResolution: DtoConfigContractResolution

    private val immutableSchema: ImmutableSchema

    private val lsiWorkspace: LsiWorkspace

    private val batchRootDtoTypeNames: Map<DtoTypeId, LsiTypeName>

    private val generatedDtoPackageName: String

    private val generatedDtoSimpleNames: List<String>

    private val jacksonVersion: JacksonFamily

    private val hibernateValidatorEnhancement: Boolean

    private val jacksonTypes: AptJacksonTypes

    private val dtoFieldModifier: Modifier

    private val parent: AptDtoGenerator?

    private val innerClassName: String?

    private val generatedDtoTypeIdsByTypeName: Map<LsiTypeName, DtoTypeId>

    private val generatedDtoTypeNames: MutableMap<DtoTypeId, LsiTypeName>

    private val locallyGeneratedDtoTypeIds: MutableSet<DtoTypeId> = HashSet()

    private val readOnlyGeneratedDtoTypeNames: Map<DtoTypeId, LsiTypeName>

    private val polymorphicSuperInterfaceName: TypeName?

    private val polymorphicBranch: Boolean

    private val lsiPolymorphicBranch: DtoPolymorphicBranch?

    private val interfaceMethodNames: Set<String>

    private var typeBuilder: TypeSpec.Builder? = null

    constructor(
        lsiGraph: DtoGraph,
        lsiDtoType: DtoType,
        annotationContract: DtoAnnotationContract,
        interfaceContractResolution: DtoInterfaceContractResolution,
        configContractResolution: DtoConfigContractResolution,
        immutableSchema: ImmutableSchema,
        lsiWorkspace: LsiWorkspace,
        batchRootDtoTypeNames: Map<DtoTypeId, LsiTypeName>,
        rendererOptions: JimmerDtoRendererOptions,
    ) : this(
        lsiGraph,
        lsiDtoType,
        annotationContract,
        interfaceContractResolution,
        configContractResolution,
        immutableSchema,
        lsiWorkspace,
        batchRootDtoTypeNames,
        rendererOptions,
        null,
        null,
        null,
        null
    )

    private constructor(
        lsiDtoType: DtoType,
        parent: AptDtoGenerator,
        innerClassName: String,
    ) : this(
        parent.lsiGraph,
        lsiDtoType,
        parent.annotationContract,
        parent.interfaceContractResolution,
        parent.configContractResolution,
        parent.immutableSchema,
        parent.lsiWorkspace,
        parent.batchRootDtoTypeNames,
        null,
        parent,
        innerClassName,
        null,
        null
    )

    init {
        require((parent == null) == (innerClassName == null)) { "The nullity values of `parent` and `innerClassName` must be same" }
        this.lsiGraph = lsiGraph
        this.lsiDtoType = lsiDtoType
        this.parent = parent
        this.innerClassName = innerClassName
        this.annotationContract = (if (parent != null) parent.annotationContract else annotationContract)!!
        this.interfaceContractResolution =
            (if (parent != null) parent.interfaceContractResolution else interfaceContractResolution)!!
        this.configContractResolution =
            (if (parent != null) parent.configContractResolution else configContractResolution)!!
        this.immutableSchema = (if (parent != null) parent.immutableSchema else immutableSchema)!!
        this.lsiWorkspace = (if (parent != null) parent.lsiWorkspace else lsiWorkspace)!!
        this.batchRootDtoTypeNames =
            if (parent != null) parent.batchRootDtoTypeNames else Collections.unmodifiableMap(
                LinkedHashMap(requireNotNull(batchRootDtoTypeNames))
            )
        if (parent != null) {
            this.generatedDtoPackageName = parent.generatedDtoPackageName
            val simpleNames = ArrayList(parent.generatedDtoSimpleNames)
            simpleNames.add(requireNotNull(innerClassName))
            this.generatedDtoSimpleNames = Collections.unmodifiableList(simpleNames)
        } else {
            val generatedDtoTypeName = JimmerDtoPoetTypeNames.rootTypeName(
                lsiDtoType,
                this.batchRootDtoTypeNames
            )
            this.generatedDtoPackageName = generatedDtoTypeName.packageName
            this.generatedDtoSimpleNames = Collections.unmodifiableList(
                ArrayList(generatedDtoTypeName.simpleNames)
            )
        }
        this.generatedDtoTypeIdsByTypeName =
            if (parent != null) parent.generatedDtoTypeIdsByTypeName else Collections.unmodifiableMap(
                LinkedHashMap(
                    JimmerDtoPoetTypeNames.forRoot(
                        lsiGraph,
                        lsiDtoType,
                        this.batchRootDtoTypeNames
                    )
                )
            )
        this.generatedDtoTypeNames = LinkedHashMap(
            if (parent != null) parent.generatedDtoTypeNames else this.batchRootDtoTypeNames
        )
        this.readOnlyGeneratedDtoTypeNames =
            Collections.unmodifiableMap(generatedDtoTypeNames)
        this.jacksonVersion =
            if (parent != null) parent.jacksonVersion else Objects.requireNonNull<JimmerDtoRendererOptions?>(
                rendererOptions,
                "rendererOptions"
            ).jacksonVersion
        this.hibernateValidatorEnhancement =
            if (parent != null) parent.hibernateValidatorEnhancement else rendererOptions!!.hibernateValidatorEnhancement
        this.jacksonTypes = if (parent != null) parent.jacksonTypes else jacksonTypes(rendererOptions!!.jacksonVersion)
        this.dtoFieldModifier =
            if (parent != null) parent.dtoFieldModifier else dtoFieldModifier(rendererOptions!!.aptFieldVisibility)
        this.polymorphicSuperInterfaceName = polymorphicSuperInterfaceName
        this.polymorphicBranch = lsiPolymorphicBranch != null
        this.lsiPolymorphicBranch = lsiPolymorphicBranch
        if (lsiPolymorphicBranch != null) {
            requireNotNull(parent) { "Frozen DTO polymorphic branch has no direct parent" }
            lsiPolymorphicBranch
                .generatedPolymorphicDtoBranchOrder(
                    parent.lsiDtoType
                )
        }
        val interfaceContract = this.interfaceContractResolution
            .contractFor(
                lsiDtoType
            )
        this.interfaceMethodNames = interfaceContract.requiredAccessorNames()
        registerGeneratedDtoTypeName()
    }

    fun generate(): String {
        check(parent == null) { "Only root DTO types can produce generated artifacts" }
        generateType()
        val javaFile = JavaFile
            .builder(generatedDtoPackageName, typeBuilder!!.build())
            .indent("    ")
            .build()
        return javaFile.toString()
    }

    private fun generateType() {
        if (lsiDtoType.isPolymorphicRoot()) {
            generatePolymorphic()
            return
        }
        val simpleName = this.simpleName
        typeBuilder = TypeSpec
            .classBuilder(simpleName)
            .addModifiers(Modifier.PUBLIC)
        if (polymorphicBranch) {
            checkNotNull(parent)
            checkNotNull(lsiPolymorphicBranch)
            typeBuilder!!.addModifiers(Modifier.FINAL)
            typeBuilder!!.addAnnotation(
                render(
                    parent.lsiDtoType,
                    lsiPolymorphicBranch,
                    this.generatedDtoPackageName,
                    parent.generatedDtoSimpleNames
                )
            )
        }
        if (polymorphicSuperInterfaceName != null) {
            typeBuilder!!.addSuperinterface(polymorphicSuperInterfaceName)
        } else {
            val baseContractKind = generatedBaseContractKind()
            if (baseContractKind != null) {
                typeBuilder!!.addSuperinterface(generatedBaseContractTypeName(baseContractKind))
            }
        }
        for (typeRef in lsiDtoType.superInterfaces) {
            typeBuilder!!.addSuperinterface(render(typeRef, lsiWorkspace))
        }
        if (this.isHibernateValidatorEnhancementRequired) {
            typeBuilder!!.addSuperinterface(
                renderEnhancedBeanType(lsiWorkspace)
            )
        }
        if (parent == null) {
            typeBuilder!!.addAnnotation(
                generatedAnnotation(lsiGraph.source.path)
            )
        } else {
            typeBuilder!!.addAnnotation(generatedAnnotation())
        }
        if (this.isSerializerRequired) {
            typeBuilder!!.addAnnotation(
                AnnotationSpec
                    .builder(jacksonTypes.jsonSerialize)
                    .addMember(
                        "using",
                        "\$T.class",
                        getDtoClassName("Serializer")
                    )
                    .build()
            )
        }
        if (this.isBuildRequired) {
            typeBuilder!!.addAnnotation(
                AnnotationSpec
                    .builder(jacksonTypes.jsonDeserialize)
                    .addMember(
                        "builder",
                        "\$T.class",
                        getDtoClassName("Builder")
                    )
                    .build()
            )
        }
        val description = render(lsiDtoType)
        if (description != null) {
            typeBuilder!!.addAnnotation(description)
        }
        typeBuilder!!.addAnnotations(
            render(lsiDtoType, annotationContract, lsiWorkspace)
        )
        val lsiPolymorphicBranch =
            currentLsiPolymorphicBranchOrNull()
        if (lsiPolymorphicBranch != null) {
            checkNotNull(parent)
            val polymorphicTypeName = renderBranchTypeName(
                parent.lsiDtoType,
                lsiPolymorphicBranch,
                lsiGraph,
                immutableSchema,
                annotationContract,
                this.generatedDtoPackageName,
                parent.generatedDtoSimpleNames
            )
            if (polymorphicTypeName != null) {
                typeBuilder!!.addAnnotation(polymorphicTypeName)
            }
        }
        if (innerClassName != null) {
            typeBuilder!!.addModifiers(Modifier.STATIC)
            addMembers()
        } else {
            addMembers()
        }
        if (innerClassName != null) {
            checkNotNull(parent)
            parent.typeBuilder!!.addType(typeBuilder!!.build())
        }
    }

    private fun generatePolymorphic() {
        val baseContractKind = generatedBaseContractKind()
        if (baseContractKind != DtoGeneratedBaseContractKind.ENTITY_INPUT &&
            baseContractKind != DtoGeneratedBaseContractKind.ENTITY_VIEW
        ) {
            throw AptDtoException(
                "Polymorphic DTO generation is only supported for entity types",
                null
            )
        }
        val simpleName = this.simpleName
        typeBuilder = TypeSpec
            .interfaceBuilder(simpleName)
            .addModifiers(Modifier.PUBLIC)
        if (lsiDtoType.isSealed()) {
            typeBuilder!!.addModifiers(sealedModifier())
        }
        typeBuilder!!.addSuperinterface(
            generatedBaseContractTypeName(baseContractKind)
        )
        for (typeRef in lsiDtoType.superInterfaces) {
            typeBuilder!!.addSuperinterface(render(typeRef, lsiWorkspace))
        }
        if (this.isHibernateValidatorEnhancementRequired) {
            typeBuilder!!.addSuperinterface(
                renderEnhancedBeanType(lsiWorkspace)
            )
        }
        if (parent == null) {
            typeBuilder!!.addAnnotation(
                generatedAnnotation(lsiGraph.source.path)
            )
        } else {
            typeBuilder!!.addAnnotation(generatedAnnotation())
            typeBuilder!!.addModifiers(Modifier.STATIC)
        }
        val description = render(lsiDtoType)
        if (description != null) {
            typeBuilder!!.addAnnotation(description)
        }
        typeBuilder!!.addAnnotations(
            render(lsiDtoType, annotationContract, lsiWorkspace)
        )
        val polymorphism = Objects.requireNonNull<DtoPolymorphism?>(
            lsiDtoType.polymorphism,
            "Frozen DTO polymorphic root has no polymorphism: " + this.dtoClassName
        )
        typeBuilder!!.addAnnotations(
            renderRootAnnotations(
                lsiDtoType,
                lsiGraph,
                immutableSchema,
                annotationContract,
                this.generatedDtoPackageName,
                this.generatedDtoSimpleNames
            )
        )
        for (prop in lsiDtoType.propsInDeclarationOrder(lsiGraph)) {
            addAccessorDeclaration(prop)
        }
        generateNestedDtoTypes()

        addPolymorphicMetadata()
        val superInterfaceName = this.dtoClassName
        val defaultBranch =
            polymorphism!!.defaultBranch()
        if (defaultBranch != null) {
            generatePolymorphicBranch(defaultBranch, superInterfaceName)
        }
        for (branch in polymorphism.typeBranchesInDeclarationOrder()) {
            generatePolymorphicBranch(branch, superInterfaceName)
        }

        if (innerClassName != null) {
            checkNotNull(parent)
            parent.typeBuilder!!.addType(typeBuilder!!.build())
        }
    }

    private fun generatedBaseContractKind(): DtoGeneratedBaseContractKind? {
        return lsiDtoType.generatedBaseContractKind(immutableSchema)
    }

    private fun generatedBaseContractTypeName(kind: DtoGeneratedBaseContractKind?): TypeName {
        if (generatedBaseContractKind() != kind) {
            throw AssertionError("Unexpected DTO base contract kind: " + kind)
        }
        return render(lsiDtoType, immutableSchema, lsiWorkspace)
    }

    private fun generatePolymorphicBranch(
        branch: DtoPolymorphicBranch,
        superInterfaceName: TypeName?
    ) {
        AptDtoGenerator(
            lsiGraph,
            branch.mergedType(lsiGraph),
            annotationContract,
            interfaceContractResolution,
            configContractResolution,
            immutableSchema,
            lsiWorkspace,
            batchRootDtoTypeNames,
            null,
            this,
            branch.className,
            superInterfaceName,
            branch
        ).generateType()
    }

    private fun currentLsiPolymorphicBranchOrNull(): DtoPolymorphicBranch? {
        if (!polymorphicBranch) {
            return null
        }
        if (lsiPolymorphicBranch == null) {
            throw AptDtoException("Frozen DTO polymorphic branch does not match generated branch")
        }
        try {
            return lsiPolymorphicBranch
                .requireGeneratedMergedType(
                    lsiGraph,
                    lsiDtoType
                )
        } catch (ex: IllegalArgumentException) {
            throw AptDtoException(
                "Frozen DTO polymorphic branch does not match generated branch",
                ex
            )
        }
    }

    val simpleName: String
        get() = generatedDtoSimpleNames.get(generatedDtoSimpleNames.size - 1)

    private fun sealedModifier(): Modifier {
        try {
            return Modifier.valueOf("SEALED")
        } catch (ex: IllegalArgumentException) {
            throw AptDtoException(
                "The modifier 'sealed' requires the annotation processor to run on Java 17 or later",
                ex
            )
        }
    }

    private val dtoClassName: ClassName
        get() = getDtoClassName(null)

    private fun getDtoClassName(nestedClassName: String?): ClassName {
        var simpleNames = generatedDtoSimpleNames
        if (nestedClassName != null) {
            simpleNames = ArrayList<String>(simpleNames)
            simpleNames.add(nestedClassName)
        }
        return ClassName.get(
            generatedDtoPackageName,
            simpleNames.get(0),
            *simpleNames.subList(1, simpleNames.size).toTypedArray()
        )
    }

    private fun getGeneratedDtoTypeNames(): Map<DtoTypeId, LsiTypeName> {
        return readOnlyGeneratedDtoTypeNames
    }

    private fun registerGeneratedDtoTypeName(
        type: DtoType = lsiDtoType,
        simpleNames: List<String> = this.generatedDtoSimpleNames
    ) {
        val typeName = create(
            this.generatedDtoPackageName,
            simpleNames
        )
        JimmerDtoPoetTypeNames.requirePlanned(
            lsiGraph,
            type,
            generatedDtoTypeIdsByTypeName,
            typeName
        )
        JimmerDtoPoetTypeNames.register(
            lsiGraph,
            type,
            generatedDtoTypeNames,
            locallyGeneratedDtoTypeIds,
            typeName
        )
    }

    private fun addMembers() {
        val isSpecification = lsiDtoType.isSpecification()
        if (!isSpecification && !polymorphicBranch) {
            addMetadata()
        }

        if (!isSpecification) {
            for (prop in lsiDtoType.basePropsInDeclarationOrder(lsiGraph)) {
                addAccessorField(prop)
            }
            for (prop in lsiDtoType.foldPropsInDeclarationOrder(lsiGraph)) {
                addFoldNullGuardAccessorField(prop)
            }
        }
        for (prop in lsiDtoType.propsInDeclarationOrder(lsiGraph)) {
            addField(prop)
            addStateField(prop)
        }

        addDefaultConstructor()
        if (!isSpecification) {
            addConverterConstructor()
        }

        for (prop in lsiDtoType.propsInDeclarationOrder(lsiGraph)) {
            addAccessors(prop)
        }

        if (isSpecification) {
            addEntityType()
            addApplyTo()
        } else {
            addApplyToDraft()
            addToEntity(false)
            addToEntity(true)
        }

        addHashCode()
        addEquals()
        addToString()

        if (isSpecification) {
            for (prop in lsiDtoType.basePropsInDeclarationOrder(lsiGraph)) {
                val converter = renderConverterOrNull(
                    prop,
                    lsiGraph,
                    immutableSchema,
                    lsiWorkspace
                )
                if (converter != null) {
                    typeBuilder!!.addMethod(converter)
                }
            }
        }

        generateNestedDtoTypes()

        if (this.isSerializerRequired) {
            typeBuilder!!.addType(
                render(
                    lsiDtoType,
                    lsiGraph,
                    immutableSchema,
                    jacksonVersion,
                    this.dtoClassName.packageName(),
                    this.dtoClassName.simpleNames()
                )
            )
        }
        if (this.isBuildRequired) {
            typeBuilder!!.addType(
                AptDtoInputBuilderRenderer.render(
                    lsiDtoType,
                    lsiGraph,
                    immutableSchema,
                    annotationContract,
                    lsiWorkspace,
                    jacksonVersion,
                    this.generatedDtoPackageName,
                    this.generatedDtoSimpleNames,
                    getGeneratedDtoTypeNames(),
                    batchRootDtoTypeNames.values
                )
            )
        }

        if (this.isHibernateValidatorEnhancementRequired) {
            for (method in renderFunctions(
                lsiDtoType,
                lsiGraph,
                immutableSchema,
                lsiWorkspace
            )) {
                typeBuilder!!.addMethod(method)
            }
        }
    }

    private fun generateNestedDtoTypes() {
        for (prop in lsiDtoType.basePropsInDeclarationOrder(lsiGraph)) {
            if (polymorphicRootPropOrNull(prop) != null) {
                continue
            }
            val lsiTargetType =
                prop.generatedTargetType(lsiGraph)
            if (lsiTargetType == null) {
                continue
            }
            val childSimpleName = JimmerDtoPoetTypeNames.requireDirectChildSimpleName(
                create(
                    this.generatedDtoPackageName,
                    this.generatedDtoSimpleNames
                ),
                lsiTargetType,
                generatedDtoTypeIdsByTypeName
            )
            val childSimpleNames: MutableList<String> = ArrayList<String>(
                this.generatedDtoSimpleNames
            )
            childSimpleNames.add(childSimpleName)
            registerGeneratedDtoTypeName(lsiTargetType, childSimpleNames)
            AptDtoGenerator(
                lsiTargetType,
                this,
                childSimpleName
            ).generateType()
        }
        for (prop in lsiDtoType.foldPropsInDeclarationOrder(lsiGraph)) {
            if (polymorphicRootPropOrNull(prop) != null) {
                continue
            }
            val lsiTargetType =
                prop.generatedTargetType(lsiGraph)
            val childSimpleName = JimmerDtoPoetTypeNames.requireDirectChildSimpleName(
                create(
                    this.generatedDtoPackageName,
                    this.generatedDtoSimpleNames
                ),
                lsiTargetType,
                generatedDtoTypeIdsByTypeName
            )
            val childSimpleNames: MutableList<String> = ArrayList<String>(
                this.generatedDtoSimpleNames
            )
            childSimpleNames.add(childSimpleName)
            registerGeneratedDtoTypeName(lsiTargetType, childSimpleNames)
            AptDtoGenerator(
                lsiTargetType,
                this,
                childSimpleName
            ).generateType()
        }
    }

    private fun addMetadata() {
        val builder = FieldSpec
            .builder(
                ParameterizedTypeName.get(
                    AptDtoPoetSupport.DTO_METADATA_CLASS_NAME,
                    immutableBaseTypeName(),
                    this.dtoClassName
                ),
                "METADATA"
            )
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        val cb = CodeBlock
            .builder()
            .indent()
            .add("\n")
            .add(
                "new \$T<\$T, \$T>(\n",
                AptDtoPoetSupport.DTO_METADATA_CLASS_NAME,
                immutableBaseTypeName(),
                this.dtoClassName
            )
            .indent()
            .add("\$T.class,\n", this.dtoClassName)
            .add(
                "\$L",
                AptDtoMetadataFetcherRenderer.render(
                    lsiDtoType,
                    lsiGraph,
                    immutableSchema,
                    lsiWorkspace,
                    configContractResolution,
                    this.generatedDtoPackageName,
                    this.generatedDtoSimpleNames,
                    generatedDtoTypeIdsByTypeName,
                    batchRootDtoTypeNames
                )
            )
        cb
            .add(",\n")
            .add("\$T::new\n", this.dtoClassName)
            .unindent()
            .unindent()
            .add(")")
        builder.initializer(cb.build())
        typeBuilder!!.addField(builder.build())
    }

    private fun addPolymorphicMetadata() {
        val builder = FieldSpec
            .builder(
                ParameterizedTypeName.get(
                    AptDtoPoetSupport.DTO_METADATA_CLASS_NAME,
                    immutableBaseTypeName(),
                    this.dtoClassName
                ),
                "METADATA"
            )
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        val cb = CodeBlock
            .builder()
            .indent()
            .add("\n")
            .add(
                "new \$T<\$T, \$T>(\n",
                AptDtoPoetSupport.DTO_METADATA_CLASS_NAME,
                immutableBaseTypeName(),
                this.dtoClassName
            )
            .indent()
            .add("\$T.class,\n", this.dtoClassName)
            .add(
                "\$L",
                AptDtoMetadataFetcherRenderer.render(
                    lsiDtoType,
                    lsiGraph,
                    immutableSchema,
                    lsiWorkspace,
                    configContractResolution,
                    this.generatedDtoPackageName,
                    this.generatedDtoSimpleNames,
                    generatedDtoTypeIdsByTypeName,
                    batchRootDtoTypeNames
                )
            )
        cb.add(",\n").indent()
        cb.add(
            "\$L",
            render(
                lsiDtoType,
                lsiGraph,
                lsiWorkspace,
                this.generatedDtoPackageName,
                this.generatedDtoSimpleNames
            )
        )
        cb
            .unindent()
            .unindent()
            .add(")")
        builder.initializer(cb.build())
        typeBuilder!!.addField(builder.build())
    }

    private fun addAccessorField(prop: DtoBaseProp) {
        if (!prop
                .requiresDtoPropAccessor(
                    lsiGraph,
                    immutableSchema,
                    LsiLanguage.JAVA
                ) { prop: DtoProp -> this.generatedTargetType(prop) }
        ) {
            return
        }
        addAccessorField(
            prop,
            accessorFieldName(prop.name),
            prop.acceptsNullInAccessor(lsiGraph),
            true
        )
    }

    private fun addFoldNullGuardAccessorField(prop: DtoFoldProp) {
        val nullGuardProp = prop.nullGuardProp(lsiGraph)
        if (nullGuardProp != null) {
            addAccessorField(
                nullGuardProp,
                foldNullGuardAccessorFieldName(prop.name),
                true,
                false
            )
        }
    }

    private fun addAccessorField(
        prop: DtoBaseProp,
        fieldName: String,
        acceptNull: Boolean,
        withConverters: Boolean
    ) {
        val builder = FieldSpec.builder(
            AptDtoPoetSupport.DTO_PROP_ACCESSOR_CLASS_NAME,
            fieldName,
            Modifier.PRIVATE,
            Modifier.STATIC,
            Modifier.FINAL
        )
        builder.initializer(
            AptDtoAccessorRenderer.render(
                prop,
                lsiGraph,
                immutableSchema,
                lsiWorkspace,
                acceptNull,
                withConverters,
                { prop: DtoProp -> this.generatedTargetType(prop) },
                generatedDtoTypeIdsByTypeName.keys
            )
        )
        typeBuilder!!.addField(builder.build())
    }

    private fun addField(prop: DtoProp) {
        var typeName = renderGeneratedValueType(prop)
        if (prop.hasNullableJavaBackingField()) {
            typeName = typeName.box()
        }
        val userProp =
            if (prop is DtoUserProp) prop else null
        val builder = FieldSpec
            .builder(typeName, prop.name)
            .addModifiers(dtoFieldModifier)
        if (userProp != null) {
            val defaultValueText = userProp.defaultValueText
            if (defaultValueText != null) {
                builder.initializer(defaultValueText)
            }
        }
        val doc = doc(prop, true)
        if (doc != null) {
            builder.addJavadoc(doc)
        }
        val isBuilderRequired = this.isBuildRequired
        if (prop.requiresFixedInputField(lsiGraph)) {
            builder.addAnnotation(AptDtoPoetSupport.FIXED_INPUT_FIELD_CLASS_NAME)
        }
        builder.addAnnotations(
            renderField(
                prop,
                annotationContract,
                immutableSchema,
                lsiWorkspace,
                if (isBuilderRequired) jacksonTypes.jsonDeserialize.reflectionName() else null
            )
        )
        typeBuilder!!.addField(builder.build())
    }

    private fun addStateField(prop: DtoProp) {
        val stateField = renderStorageField(
            prop,
            lsiGraph,
            dtoFieldModifier
        )
        if (stateField != null) {
            typeBuilder!!.addField(stateField)
        }
    }

    private fun addAccessorDeclaration(prop: DtoProp) {
        val typeName = renderGeneratedValueType(prop)
        val getterBuilder = MethodSpec
            .methodBuilder(
                prop
                    .dtoValueAccessorName(
                        LsiLanguage.JAVA,
                        lsiGraph,
                        immutableSchema
                    )
            )
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(typeName)
        val description = render(prop, lsiGraph)
        if (description != null) {
            getterBuilder.addAnnotation(description)
        }
        if (!typeName.isPrimitive()) {
            if (prop.nullable) {
                getterBuilder.addAnnotation(Nullable::class.java)
            } else {
                getterBuilder.addAnnotation(NonNull::class.java)
            }
        }
        getterBuilder.addAnnotations(
            renderGetter(
                prop,
                annotationContract,
                immutableSchema,
                lsiWorkspace,
                null
            )
        )
        typeBuilder!!.addMethod(getterBuilder.build())
    }

    private fun addAccessors(prop: DtoProp) {
        val typeName = renderGeneratedValueType(prop)
        val getterName = prop
            .dtoValueAccessorName(
                LsiLanguage.JAVA,
                lsiGraph,
                immutableSchema
            )
        val setterName = prop
            .javaValueSetterName(
                lsiGraph,
                immutableSchema
            )
        val stateFieldName = prop
            .dtoLoadedStateStorageNameOrNull(
                lsiGraph,
                LsiLanguage.JAVA
            )

        val getterBuilder = MethodSpec
            .methodBuilder(getterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(typeName)
        if (interfaceMethodNames.contains(getterName)) {
            getterBuilder.addAnnotation(Override::class.java)
        }
        val description = render(prop, lsiGraph)
        if (description != null) {
            getterBuilder.addAnnotation(description)
        }
        if (!typeName.isPrimitive()) {
            if (prop.nullable) {
                getterBuilder.addAnnotation(Nullable::class.java)
            } else {
                getterBuilder.addAnnotation(NonNull::class.java)
            }
        }
        val isBuilderRequired = this.isBuildRequired
        getterBuilder.addAnnotations(
            renderGetter(
                prop,
                annotationContract,
                immutableSchema,
                lsiWorkspace,
                if (isBuilderRequired) jacksonTypes.jsonDeserialize.reflectionName() else null
            )
        )
        if (stateFieldName != null) {
            getterBuilder.beginControlFlow(
                "if (\$L)",
                '!'.toString() + stateFieldName
            )
            getterBuilder.addStatement(
                "throw new IllegalStateException(\$S)",
                "The property \"" + prop.name + "\" is not specified"
            )
            getterBuilder.endControlFlow()
        }
        if (!prop.nullable && prop.hasNullableJavaBackingField()) {
            getterBuilder.beginControlFlow(
                "if (\$L == null)",
                prop.name
            )
            if (lsiDtoType.isInput() &&
                typeName is ParameterizedTypeName &&
                AptDtoPoetSupport.LIST_CLASS_NAME == typeName.rawType
            ) {
                getterBuilder.addComment(
                    "GraphQLInput requires `obj." +
                            getterName +
                            "().add(...)`"
                )
                getterBuilder.addStatement(
                    "return this.\$L = new \$T<>()",
                    prop.name,
                    AptDtoPoetSupport.ARRAY_LIST_CLASS_NAME
                )
            } else {
                getterBuilder.addStatement(
                    "throw new IllegalStateException(\$S)",
                    "The property \"" + prop.name + "\" is not specified"
                )
            }
            getterBuilder.endControlFlow()
        }
        getterBuilder.addStatement("return \$L", prop.name)
        typeBuilder!!.addMethod(getterBuilder.build())

        val parameterBuilder = ParameterSpec.builder(typeName, prop.name)
        if (!typeName.isPrimitive()) {
            if (prop.nullable) {
                parameterBuilder.addAnnotation(Nullable::class.java)
            } else {
                parameterBuilder.addAnnotation(NonNull::class.java)
            }
        }
        val setterBuilder = MethodSpec
            .methodBuilder(setterName)
            .addParameter(parameterBuilder.build())
            .addModifiers(Modifier.PUBLIC)
        if (interfaceMethodNames.contains(setterName)) {
            setterBuilder.addAnnotation(Override::class.java)
        }
        setterBuilder.addStatement("this.\$L = \$L", prop.name, prop.name)
        if (stateFieldName != null) {
            setterBuilder.addStatement("this.\$L = true", stateFieldName)
        }
        typeBuilder!!.addMethod(setterBuilder.build())

        if (stateFieldName != null) {
            val isLoadedBuilder = MethodSpec
                .methodBuilder(StringUtil.identifier("is", prop.name, "Loaded"))
                .returns(TypeName.BOOLEAN)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ApiIgnore::class.java)
                .addAnnotation(jacksonTypes.jsonIgnore)
                .addStatement("return this.\$L", stateFieldName)
            typeBuilder!!.addMethod(isLoadedBuilder.build())
            val setLoadedBuilder = MethodSpec
                .methodBuilder(StringUtil.identifier("set", prop.name, "Loaded"))
                .addParameter(TypeName.BOOLEAN, "loaded")
                .addStatement("this.\$L = loaded", stateFieldName)
            typeBuilder!!.addMethod(setLoadedBuilder.build())
        }
    }

    private fun doc(prop: DtoProp, contentOnly: Boolean): String? {
        var doc: String? = escapedDocumentation(prop.documentation)
        if (doc == null) {
            return null
        }
        if (contentOnly) {
            var index = -1
            index = docKeyIndex(index, doc, "@param")
            index = docKeyIndex(index, doc, "@return")
            index = docKeyIndex(index, doc, "@exception")
            index = docKeyIndex(index, doc, "@throws")
            index = docKeyIndex(index, doc, "@see")
            if (index != -1) {
                doc = doc.substring(0, index)
            }
        }
        return doc
    }

    private fun addDefaultConstructor() {
        val builder = MethodSpec
            .constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
        typeBuilder!!.addMethod(builder.build())
    }

    private fun addConverterConstructor() {
        val immutableBaseType = immutableBaseType()
        val parameterBuilder =
            ParameterSpec.builder(
                immutableBaseTypeName().annotated(
                    AnnotationSpec.builder(NonNull::class.java).build()
                ),
                "base"
            )
        val builder = MethodSpec
            .constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(parameterBuilder.build())
        for (prop in lsiDtoType.propsInDeclarationOrder(lsiGraph)) {
            if (prop is DtoFoldProp) {
                builder.addStatement(
                    "this.\$L = \$L",
                    prop.name,
                    AptDtoFoldValueRenderer.render(
                        prop,
                        lsiGraph,
                        lsiWorkspace,
                        "base",
                        foldNullGuardAccessorFieldName(prop.name),
                        { prop: DtoProp -> this.generatedTargetType(prop) },
                        generatedDtoTypeIdsByTypeName.keys
                    )
                )
                continue
            }
            if (prop !is DtoBaseProp) {
                continue
            }
            val lsiProp = prop
            val immutableProp =
                lsiProp
                    .boundImmutableProp(
                        lsiGraph,
                        immutableSchema
                    )
            val stateFieldName = lsiProp
                .dtoLoadedStateStorageNameOrNull(
                    lsiGraph,
                    LsiLanguage.JAVA
                )
            builder.addStatement(
                "this.\$L = \$L",
                prop.name,
                AptDtoBaseValueRenderer.render(
                    lsiProp,
                    lsiGraph,
                    immutableSchema,
                    lsiWorkspace,
                    accessorFieldName(prop.name),
                    "base",
                    immutableProp
                        .sourceGetterName(
                            lsiWorkspace
                        ),
                    immutableBaseType,
                    immutableProp
                        .generatedDraftSlotName(
                            lsiWorkspace
                        ),
                    "Cannot convert \"" +
                            immutableBaseTypeName() +
                            "\" to \"" +
                            this.dtoClassName +
                            "\" because the cannot get non-null value for \"" +
                            prop.name +
                            "\"",
                    { prop: DtoProp -> this.generatedTargetType(prop) },
                    generatedDtoTypeIdsByTypeName.keys
                )
            )
            if (stateFieldName != null) {
                val stateInitializer = Objects.requireNonNull<CodeBlock?>(
                    renderBaseInitializer(
                        lsiProp,
                        lsiGraph,
                        accessorFieldName(prop.name),
                        "base"
                    ),
                    "Dynamic DTO property must have a base loaded-state initializer"
                )
                builder.addStatement("this.\$L = \$L", stateFieldName, stateInitializer)
            }
        }
        typeBuilder!!.addMethod(builder.build())
    }

    private fun addApplyToDraft() {
        val builder = MethodSpec
            .methodBuilder("__applyTo")
            .addModifiers(Modifier.PRIVATE)
            .addParameter(immutableBaseDraftTypeName(), "__draft")
        for (prop in lsiDtoType.propsInDeclarationOrder(lsiGraph)) {
            if (prop is DtoFoldProp) {
                builder.addCode(
                    render(
                        prop,
                        "__draft"
                    )
                )
                continue
            }
            if (prop !is DtoBaseProp) {
                continue
            }
            val lsiProp = prop
            if (lsiProp
                    .isDraftWriteSkipped(
                        lsiGraph,
                        immutableSchema,
                        LsiLanguage.JAVA
                    )
            ) {
                continue
            }
            val stateFieldName = lsiProp
                .dtoLoadedStateStorageNameOrNull(
                    lsiGraph,
                    LsiLanguage.JAVA
                )
            val fuzzy = lsiProp.requiresNonNullDraftWriteGuard(lsiGraph)
            if (stateFieldName != null) {
                builder.beginControlFlow("if (this.\$L)", stateFieldName)
            } else if (fuzzy) {
                builder.beginControlFlow("if (this.\$L != null)", prop.name)
            }
            builder.addCode(
                render(
                    lsiProp,
                    lsiGraph,
                    immutableSchema,
                    lsiWorkspace,
                    accessorFieldName(prop.name),
                    "__draft",
                    prop.name,
                    lsiProp
                        .boundImmutableProp(
                            lsiGraph,
                            immutableSchema
                        )
                        .generatedJavaDraftSetterName(
                            lsiWorkspace
                        )
                ) { prop: DtoProp -> this.generatedTargetType(prop) }
            )
            if (stateFieldName != null || fuzzy) {
                builder.endControlFlow()
            }
        }
        typeBuilder!!.addMethod(builder.build())
    }

    private fun addToEntity(withId: Boolean) {
        val entityBase = lsiDtoType.hasEntityBase(immutableSchema)
        val idOverridable =
            lsiDtoType.isInput() &&
                    entityBase
        if (withId && !idOverridable) {
            return
        }
        val discriminatorProp = polymorphicInputDiscriminatorProp()
        val baseIdProp = if (withId) immutableSchema
            .idPropOf(
                lsiDtoType.immutableBaseType(immutableSchema)
            ) else null
        val builder = MethodSpec
            .methodBuilder(if (entityBase) (if (withId) "toEntityById" else "toEntity") else "toImmutable")
        if (baseIdProp != null) {
            builder.addParameter(
                ParameterSpec.builder(
                    render(baseIdProp.type, lsiWorkspace).box(),
                    "id"
                ).addAnnotation(Nullable::class.java).build()
            )
        } else {
            builder.addAnnotation(Override::class.java)
        }
        builder.addModifiers(Modifier.PUBLIC)
            .returns(immutableBaseTypeName())
        if (!withId && idOverridable) {
            builder.addStatement("return toEntityById(null)")
        } else if (discriminatorProp != null && this.isDefaultPolymorphicInputBranch) {
            builder.addCode(
                renderDefaultBranchBody(
                    lsiDtoType,
                    Objects.requireNonNull<DtoPolymorphicBranch?>(
                        currentLsiPolymorphicBranchOrNull(),
                        "Frozen DTO default polymorphic branch is required"
                    ),
                    discriminatorProp,
                    lsiGraph,
                    immutableSchema,
                    lsiWorkspace,
                    this.generatedDtoPackageName,
                    this.generatedDtoSimpleNames,
                    if (baseIdProp != null) "id" else null
                )
            )
        } else {
            if (discriminatorProp != null && this.isTypedPolymorphicInputBranch) {
                builder.addCode(
                    renderTypedDiscriminatorValidation(
                        lsiDtoType,
                        Objects.requireNonNull<DtoPolymorphicBranch?>(
                            currentLsiPolymorphicBranchOrNull(),
                            "Frozen DTO typed polymorphic branch is required"
                        ),
                        discriminatorProp,
                        lsiGraph,
                        immutableSchema,
                        lsiWorkspace,
                        this.generatedDtoPackageName,
                        this.generatedDtoSimpleNames
                    )
                )
            }
            builder.addCode(
                "return \$T.\$L.produce(__draft -> {$>\n",
                immutableBaseDraftTypeName(),
                "$"
            )
            builder.addStatement("this.__applyTo(__draft)")
            if (baseIdProp != null) {
                builder.beginControlFlow("if (id != null)")
                builder.addStatement(
                    "__draft.\$L(\$L)",
                    baseIdProp
                        .generatedJavaDraftSetterName(
                            lsiWorkspace
                        ),
                    "id"
                )
                builder.endControlFlow()
            }
            builder.addCode("$<});\n")
        }
        typeBuilder!!.addMethod(builder.build())
    }

    private fun polymorphicInputDiscriminatorProp(): DtoBaseProp? {
        if (!polymorphicBranch) {
            return null
        }
        return lsiDtoType
            .selectedPolymorphicInputDiscriminatorPropOrNull(
                lsiGraph,
                immutableSchema
            )
    }

    private val isDefaultPolymorphicInputBranch: Boolean
        get() = lsiPolymorphicBranch != null &&
                lsiPolymorphicBranch.kind == DtoPolymorphicBranchKind.DEFAULT

    private val isTypedPolymorphicInputBranch: Boolean
        get() = lsiPolymorphicBranch != null &&
                lsiPolymorphicBranch.kind == DtoPolymorphicBranchKind.TYPE


    private fun addEntityType() {
        typeBuilder!!.addMethod(
            renderEntityType(
                lsiDtoType,
                immutableSchema,
                lsiWorkspace
            )
        )
    }

    private fun addApplyTo() {
        typeBuilder!!.addMethod(
            renderApplyTo(
                lsiDtoType,
                lsiGraph,
                immutableSchema,
                lsiWorkspace
            )
        )
    }

    private fun renderGeneratedValueType(prop: DtoProp): TypeName {
        val type = prop
            .generatedValueType(
                lsiGraph,
                immutableSchema,
                LsiLanguage.JAVA
            ) { prop: DtoProp -> this.generatedTargetType(prop) }
        return AptDtoTypeRefRenderer.render(
            type,
            lsiWorkspace,
            generatedDtoTypeIdsByTypeName.keys
        )
    }

    private fun immutableBaseType(): ImmutableType {
        return lsiDtoType.immutableBaseType(immutableSchema)
    }

    private fun immutableBaseTypeName(): ClassName {
        return renderSource(immutableBaseType(), lsiWorkspace)
    }

    private fun immutableBaseDraftTypeName(): ClassName {
        return renderDraft(immutableBaseType(), lsiWorkspace)
    }

    private fun addHashCode() {
        typeBuilder!!.addMethod(
            renderHashCode(lsiDtoType, lsiGraph, immutableSchema)
        )
    }

    private fun addEquals() {
        typeBuilder!!.addMethod(
            renderEquals(
                lsiDtoType,
                lsiGraph,
                immutableSchema,
                this.generatedDtoPackageName,
                this.generatedDtoSimpleNames
            )
        )
    }

    private fun addToString() {
        typeBuilder!!.addMethod(
            AptDtoToStringRenderer.render(lsiDtoType, lsiGraph, simpleNamePath()!!)
        )
    }

    private fun simpleNamePath(): String? {
        val name = this.simpleName
        if (parent != null) {
            return parent.simpleNamePath() + '.' + name
        }
        return name
    }

    private fun polymorphicRootPropOrNull(
        prop: DtoProp
    ): DtoProp? {
        if (!polymorphicBranch || parent == null) {
            return null
        }
        return parent.lsiDtoType
            .promotedPolymorphicRootPropOrNull(
                lsiGraph,
                prop
            )
    }

    private fun generatedTargetType(prop: DtoProp): LsiDeclaredType {
        val ownerTypeName = create(
            this.generatedDtoPackageName,
            this.generatedDtoSimpleNames
        )
        JimmerDtoPoetTypeNames.requirePlanned(
            lsiGraph,
            lsiDtoType,
            generatedDtoTypeIdsByTypeName,
            ownerTypeName
        )
        return JimmerDtoPoetTypeNames.toLsiGeneratedTargetType(
            lsiGraph,
            prop,
            ownerTypeName,
            generatedDtoTypeIdsByTypeName,
            batchRootDtoTypeNames
        )
    }

    private fun accessorFieldName(propName: String?): String {
        return StringUtil.snake(propName + "Accessor", StringUtil.SnakeCase.UPPER)
    }

    private fun foldNullGuardAccessorFieldName(propName: String?): String {
        return StringUtil.snake(propName + "NullGuardAccessor", StringUtil.SnakeCase.UPPER)
    }

    fun getTypeBuilder(): TypeSpec.Builder {
        return typeBuilder!!
    }

    private val isSerializerRequired: Boolean
        get() = lsiDtoType.requiresDynamicInputSerialization(lsiGraph)

    private val isBuildRequired: Boolean
        get() = lsiDtoType.requiresInputBuilder(lsiGraph)

    private val isHibernateValidatorEnhancementRequired: Boolean
        get() = lsiDtoType
            .requiresHibernateValidatorEnhancement(
                lsiGraph,
                hibernateValidatorEnhancement
            )

    companion object {
        private fun jacksonTypes(jacksonVersion: JacksonFamily): AptJacksonTypes {
            if (jacksonVersion == JacksonFamily.JACKSON_3) {
                return AptJacksonTypes(
                    ClassName.get("com.fasterxml.jackson.annotation", "JsonIgnore"),
                    ClassName.get("tools.jackson.databind.annotation", "JsonSerialize"),
                    ClassName.get("tools.jackson.databind.annotation", "JsonDeserialize")
                )
            }
            return AptJacksonTypes(
                ClassName.get("com.fasterxml.jackson.annotation", "JsonIgnore"),
                ClassName.get("com.fasterxml.jackson.databind.annotation", "JsonSerialize"),
                ClassName.get("com.fasterxml.jackson.databind.annotation", "JsonDeserialize")
            )
        }

        private fun dtoFieldModifier(visibility: LsiVisibility): Modifier {
            when (visibility) {
                LsiVisibility.PRIVATE -> return Modifier.PRIVATE
                LsiVisibility.PROTECTED -> return Modifier.PROTECTED
                LsiVisibility.PUBLIC -> return Modifier.PUBLIC
                LsiVisibility.INTERNAL,
                LsiVisibility.PACKAGE_PRIVATE,
                LsiVisibility.LOCAL,
                LsiVisibility.UNKNOWN,
                -> error("Unsupported APT DTO field visibility: $visibility")
            }
        }

        private fun docKeyIndex(originalIndex: Int, doc: String, key: String): Int {
            val index = doc.indexOf(key)
            if (index == -1 || (originalIndex != -1 && originalIndex < index)) {
                return originalIndex
            }
            if (doc.length == index + key.length) {
                return index
            }
            if (Character.isWhitespace(doc.get(index + key.length))) {
                return index
            }
            return originalIndex
        }

        private fun escapedDocumentation(documentation: String?): String? {
            return if (documentation != null && !documentation.isEmpty()) documentation.replace("$", "$$") else null
        }
    }
}
