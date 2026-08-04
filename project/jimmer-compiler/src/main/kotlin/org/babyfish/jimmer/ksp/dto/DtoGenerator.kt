package org.babyfish.jimmer.ksp.dto

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.babyfish.jimmer.compiler.dto.JimmerDtoJacksonVersion
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.compiler.render.ksp.KspDtoAccessorRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoBaseValueRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoDescriptionRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoMetadataFetcherRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoDraftWriteRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoEqualityRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoFoldDraftApplyRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoFoldValueRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoHibernateValidatorRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoInputBuilderRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoJacksonPolymorphismRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoLoadedStateRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoPolymorphicBranchRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoPolymorphicInputRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoPolymorphicMetadataConverterRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoPropAnnotationRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoSerializerRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoSpecificationRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoTypeAnnotationRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoTypeRefRenderer
import org.babyfish.jimmer.dto.compiler.*
import org.babyfish.jimmer.impl.util.StringUtil
import org.babyfish.jimmer.impl.util.StringUtil.SnakeCase
import org.babyfish.jimmer.ksp.Context
import org.babyfish.jimmer.ksp.immutable.generator.*
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableProp
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableType
import org.babyfish.jimmer.ksp.util.generatedAnnotation
import org.babyfish.jimmer.compiler.render.ksp.KspDtoToStringRenderer
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoFoldProp
import site.addzero.lsi.jimmer.dto.DtoGeneratedBaseContractKind
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch as LsiDtoPolymorphicBranch
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranchKind
import site.addzero.lsi.jimmer.dto.DtoProp as LsiDtoProp
import site.addzero.lsi.jimmer.dto.DtoType as LsiDtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoUserProp
import site.addzero.lsi.jimmer.dto.acceptsNullInAccessor
import site.addzero.lsi.jimmer.dto.baseProp
import site.addzero.lsi.jimmer.dto.basePropsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.contractFor
import site.addzero.lsi.jimmer.dto.foldProp
import site.addzero.lsi.jimmer.dto.foldPropsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.dtoLoadedStateStorageNameOrNull
import site.addzero.lsi.jimmer.dto.generatedBaseContractKind
import site.addzero.lsi.jimmer.dto.generatedPolymorphicBranch
import site.addzero.lsi.jimmer.dto.generatedTargetType
import site.addzero.lsi.jimmer.dto.generatedValueType
import site.addzero.lsi.jimmer.dto.generatedPolymorphicDtoBranchOrder
import site.addzero.lsi.jimmer.dto.hasDtoPropAccessorFields
import site.addzero.lsi.jimmer.dto.hasEntityBase
import site.addzero.lsi.jimmer.dto.isDraftWriteSkipped
import site.addzero.lsi.jimmer.dto.isPolymorphicRoot
import site.addzero.lsi.jimmer.dto.isSealed
import site.addzero.lsi.jimmer.dto.isSpecification
import site.addzero.lsi.jimmer.dto.kotlinDefaultValueTextOrNull
import site.addzero.lsi.jimmer.dto.kotlinByImportPackages
import site.addzero.lsi.jimmer.dto.mergedType
import site.addzero.lsi.jimmer.dto.nullGuardProp
import site.addzero.lsi.jimmer.dto.prop
import site.addzero.lsi.jimmer.dto.promotedPolymorphicRootPropOrNull
import site.addzero.lsi.jimmer.dto.propsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.requiresDynamicInputSerialization
import site.addzero.lsi.jimmer.dto.requiresFixedInputField
import site.addzero.lsi.jimmer.dto.requiresHibernateValidatorEnhancement
import site.addzero.lsi.jimmer.dto.requiresInputBuilder
import site.addzero.lsi.jimmer.dto.requiresNonNullDraftWriteGuard
import site.addzero.lsi.jimmer.dto.requiresDtoPropAccessor
import site.addzero.lsi.jimmer.dto.requireGeneratedMergedType
import site.addzero.lsi.jimmer.dto.requiredPropNames
import site.addzero.lsi.jimmer.dto.selectedPolymorphicInputDiscriminatorPropOrNull
import site.addzero.lsi.jimmer.dto.userPropsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.usesDirectBaseAccess
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetImport
import site.addzero.lsi.poet.LsiPoetTypeName
import java.io.OutputStreamWriter
import java.util.*

internal class DtoGenerator private constructor(
    val ctx: Context,
    private val mutable: Boolean,
    val dtoType: DtoType<ImmutableType, ImmutableProp>,
    private val codeGenerator: CodeGenerator?,
    private val lsiGraph: DtoGraph,
    private val lsiDtoType: LsiDtoType,
    private val immutableSchema: ImmutableSchema,
    private val jacksonVersion: JimmerDtoJacksonVersion,
    private val hibernateValidatorEnhancement: Boolean,
    private val workspace: LsiWorkspace,
    private val annotationContract: DtoAnnotationContract,
    private val interfaceContractResolution: DtoInterfaceContractResolution,
    private val configContractResolution: DtoConfigContractResolution,
    private val rootDtoTypeNamesByTypeId: Map<DtoTypeId, LsiPoetTypeName>,
    private val generatedDtoPackageName: String,
    private val generatedDtoSimpleNames: List<String>,
    private val parent: DtoGenerator?,
    private val innerClassName: String?,
    private val polymorphicSuperInterfaceName: TypeName? = null,
    private val lsiPolymorphicBranch: LsiDtoPolymorphicBranch? = null,
) {
    private val root: DtoGenerator = parent?.root ?: this

    private val baseType: ImmutableType = requireNotNull(dtoType.baseType) {
        "Generated DTO '${dtoType.qualifiedName ?: dtoType.name ?: "<anonymous>"}' has no immutable base type"
    }

    private val entityBase: Boolean = lsiDtoType.hasEntityBase(immutableSchema)

    private val polymorphicBranch: Boolean
        get() = lsiPolymorphicBranch != null

    private val generatedDtoTypeIdsByTypeName: Map<LsiPoetTypeName, DtoTypeId> =
        parent?.generatedDtoTypeIdsByTypeName
            ?: JimmerDtoPoetTypeNames.forRoot(lsiGraph, lsiDtoType, rootDtoTypeNamesByTypeId)

    private val generatedDtoTypeNamesByTypeId: MutableMap<DtoTypeId, LsiPoetTypeName> =
        (parent?.generatedDtoTypeNamesByTypeId ?: rootDtoTypeNamesByTypeId).toMutableMap()

    private val locallyGeneratedDtoTypeIds = mutableSetOf<DtoTypeId>()

    private val metadataFetcherPoetImports: MutableSet<LsiPoetImport> =
        parent?.metadataFetcherPoetImports ?: linkedSetOf()

    private val interfacePropNames = interfaceContractResolution
        .contractFor(lsiDtoType)
        .requiredPropNames()
        .let {
            if (polymorphicBranch) {
                it + root.lsiDtoType
                    .propsInDeclarationOrder(root.lsiGraph)
                    .map { prop -> prop.name }
            } else {
                it
            }
        }

    init {
        if ((codeGenerator === null) == (parent === null)) {
            throw IllegalArgumentException("The nullity values of `codeGenerator` and `parent` cannot be same")
        }
        if ((parent === null) != (innerClassName === null)) {
            throw IllegalArgumentException("The nullity values of `parent` and `innerClassName` must be same")
        }
        registerGeneratedDtoTypeName(lsiDtoType, generatedDtoSimpleNames)
        lsiPolymorphicBranch?.generatedPolymorphicDtoBranchOrder(
            requireNotNull(parent) {
                "Frozen DTO polymorphic branch has no direct parent"
            }.lsiDtoType
        )
    }

    private var _typeBuilder: TypeSpec.Builder? = null

    constructor(
        ctx: Context,
        mutable: Boolean,
        dtoType: DtoType<ImmutableType, ImmutableProp>,
        codeGenerator: CodeGenerator?,
        lsiGraph: DtoGraph,
        lsiDtoType: LsiDtoType,
        immutableSchema: ImmutableSchema,
        jacksonVersion: JimmerDtoJacksonVersion,
        hibernateValidatorEnhancement: Boolean,
        workspace: LsiWorkspace,
        annotationContract: DtoAnnotationContract,
        interfaceContractResolution: DtoInterfaceContractResolution,
        configContractResolution: DtoConfigContractResolution,
        rootDtoTypeNamesByTypeId: Map<DtoTypeId, LsiPoetTypeName>,
    ) : this(
        ctx,
        mutable,
        dtoType,
        codeGenerator,
        lsiGraph,
        lsiDtoType,
        immutableSchema,
        jacksonVersion,
        hibernateValidatorEnhancement,
        workspace,
        annotationContract,
        interfaceContractResolution,
        configContractResolution,
        rootDtoTypeNamesByTypeId,
        rootDtoTypeNamesByTypeId.getValue(lsiDtoType.id).packageName,
        rootDtoTypeNamesByTypeId.getValue(lsiDtoType.id).simpleNames,
        null,
        null,
    )

    private constructor(
        ctx: Context,
        mutable: Boolean,
        dtoType: DtoType<ImmutableType, ImmutableProp>,
        lsiDtoType: LsiDtoType,
        parent: DtoGenerator,
        innerClassName: String,
        polymorphicSuperInterfaceName: TypeName? = null,
        lsiPolymorphicBranch: LsiDtoPolymorphicBranch? = null,
    ) : this(
        ctx = ctx,
        mutable = mutable,
        dtoType = dtoType,
        codeGenerator = null,
        lsiGraph = parent.lsiGraph,
        lsiDtoType = lsiDtoType,
        immutableSchema = parent.immutableSchema,
        jacksonVersion = parent.jacksonVersion,
        hibernateValidatorEnhancement = parent.hibernateValidatorEnhancement,
        workspace = parent.workspace,
        annotationContract = parent.annotationContract,
        interfaceContractResolution = parent.interfaceContractResolution,
        configContractResolution = parent.configContractResolution,
        rootDtoTypeNamesByTypeId = parent.rootDtoTypeNamesByTypeId,
        generatedDtoPackageName = parent.generatedDtoPackageName,
        generatedDtoSimpleNames = parent.generatedDtoSimpleNames + innerClassName,
        parent = parent,
        innerClassName = innerClassName,
        polymorphicSuperInterfaceName = polymorphicSuperInterfaceName,
        lsiPolymorphicBranch = lsiPolymorphicBranch,
    )

    val typeBuilder: TypeSpec.Builder
        get() = _typeBuilder ?: error("Type builder is not ready")

    fun getDtoClassName(nestedSimpleName: String? = null): ClassName {
        if (innerClassName !== null) {
            val list: MutableList<String> = ArrayList()
            collectNames(list)
            return ClassName(
                root.dtoType.packageName,
                list[0],
                *list.subList(1, list.size).let {
                    if (nestedSimpleName == null) {
                        it
                    } else {
                        it.toMutableList() + nestedSimpleName
                    }
                }.toTypedArray()
            )
        }
        if (nestedSimpleName == null) {
            return ClassName(
                root.dtoType.packageName,
                dtoType.name!!
            )
        }
        return ClassName(
            root.dtoType.packageName,
            dtoType.name!!,
            nestedSimpleName
        )
    }

    fun generate(allFiles: List<KSFile>) {
        if (lsiDtoType.isPolymorphicRoot()) {
            generatePolymorphic(allFiles)
            return
        }
        if (codeGenerator != null) {
            codeGenerator.createNewFile(
                Dependencies(true, *allFiles.toTypedArray()),
                root.dtoType.packageName,
                dtoType.name!!
            ).use {
                val fileSpec = FileSpec
                    .builder(
                        root.dtoType.packageName,
                        dtoType.name!!
                    ).apply {
                        indent("    ")
                        val builder = TypeSpec
                            .classBuilder(dtoType.name!!)
                            .apply {
                                if (!polymorphicBranch) {
                                    addModifiers(KModifier.OPEN)
                                }
                            }
                        if (parent == null) {
                            builder.addAnnotation(generatedAnnotation(dtoType.dtoFile, mutable))
                        }
                        builder.addTypeAnnotations()
                        builder.addJacksonPolymorphicTypeNameIfNecessary()
                        _typeBuilder = builder
                        try {
                            addDoc()
                            addMembers()
                            addType(builder.build())
                            addExtensions()
                        } finally {
                            _typeBuilder = null
                        }
                        addImports()
                    }.build()
                val writer = OutputStreamWriter(it, Charsets.UTF_8)
                fileSpec.writeTo(writer)
                writer.flush()
            }
        } else if (innerClassName !== null && parent !== null) {
            val builder = TypeSpec
                .classBuilder(innerClassName)
                .apply {
                    if (!polymorphicBranch) {
                        addModifiers(KModifier.OPEN)
                    } else {
                        val polymorphicRootGenerator = requireNotNull(parent) {
                            "Generated polymorphic branch has no parent generator"
                        }
                        addAnnotation(
                            KspDtoPolymorphicBranchRenderer.render(
                                rootType = polymorphicRootGenerator.lsiDtoType,
                                branch = requireNotNull(lsiPolymorphicBranch),
                                generatedPackageName = generatedDtoPackageName,
                                generatedRootSimpleNames = polymorphicRootGenerator.generatedDtoSimpleNames,
                            )
                        )
                    }
                }
                .addAnnotation(generatedAnnotation())
            builder.addTypeAnnotations()
            builder.addJacksonPolymorphicTypeNameIfNecessary()
            _typeBuilder = builder
            try {
                addDoc()
                addMembers()
                parent.typeBuilder.addType(builder.build())
            } finally {
                _typeBuilder = null
            }
        }
    }

    private fun generatePolymorphic(allFiles: List<KSFile>) {
        val baseContractKind = lsiDtoType.generatedBaseContractKind(immutableSchema)
        if (baseContractKind != DtoGeneratedBaseContractKind.ENTITY_INPUT &&
            baseContractKind != DtoGeneratedBaseContractKind.ENTITY_VIEW
        ) {
            throw DtoException("Polymorphic DTO generation is only supported for entity types")
        }
        if (codeGenerator != null) {
            codeGenerator.createNewFile(
                Dependencies(true, *allFiles.toTypedArray()),
                root.dtoType.packageName,
                dtoType.name!!
            ).use {
                val fileSpec = FileSpec
                    .builder(
                        root.dtoType.packageName,
                        dtoType.name!!
                    ).apply {
                        indent("    ")
                        addType(buildPolymorphicType(baseContractKind))
                        addExtensions(includeBlockConverter = false)
                        addImports()
                    }.build()
                val writer = OutputStreamWriter(it, Charsets.UTF_8)
                fileSpec.writeTo(writer)
                writer.flush()
            }
        } else if (innerClassName !== null && parent !== null) {
            parent.typeBuilder.addType(buildPolymorphicType(baseContractKind))
        }
    }

    private fun buildPolymorphicType(
        baseContractKind: DtoGeneratedBaseContractKind,
    ): TypeSpec {
        val builder = TypeSpec
            .interfaceBuilder(innerClassName ?: dtoType.name!!)
            .apply {
                if (lsiDtoType.isSealed()) {
                    addModifiers(KModifier.SEALED)
                }
            }
            .addAnnotation(
                if (parent == null) {
                    generatedAnnotation(dtoType.dtoFile, mutable)
                } else {
                    generatedAnnotation()
                }
            )
        builder.addTypeAnnotations()
        builder.addAnnotations(
            KspDtoJacksonPolymorphismRenderer.renderRootAnnotations(
                dtoType = lsiDtoType,
                graph = lsiGraph,
                immutableSchema = immutableSchema,
                annotationContract = annotationContract,
                generatedPackageName = generatedDtoPackageName,
                generatedSimpleNames = generatedDtoSimpleNames,
            )
        )
        _typeBuilder = builder
        try {
            addDoc()
            addPolymorphicMembers(baseContractKind)
            return builder.build()
        } finally {
            _typeBuilder = null
        }
    }

    private fun FileSpec.Builder.addImports() {
        val packages = lsiDtoType.kotlinByImportPackages(lsiGraph, immutableSchema)
        val imports = sortedSetOf(
            compareBy<LsiPoetImport>({ it.packageName }, { it.simpleName })
        )
        packages.mapTo(imports) { packageName -> LsiPoetImport(packageName, "by") }
        imports += metadataFetcherPoetImports
        for (sourceImport in imports) {
            addImport(sourceImport.packageName, sourceImport.simpleName)
        }
    }

    private fun TypeSpec.Builder.addTypeAnnotations() {
        addAnnotations(
            KspDtoTypeAnnotationRenderer.render(
                dtoType = lsiDtoType,
                annotationContract = annotationContract,
                workspace = workspace,
            )
        )
    }

    private fun TypeSpec.Builder.addJacksonPolymorphicTypeNameIfNecessary() {
        val branch = currentLsiPolymorphicBranchOrNull ?: return
        val polymorphicRootGenerator = requireNotNull(parent) {
            "Generated polymorphic branch has no parent generator"
        }
        KspDtoJacksonPolymorphismRenderer.renderBranchTypeName(
            rootType = polymorphicRootGenerator.lsiDtoType,
            branch = branch,
            graph = lsiGraph,
            immutableSchema = immutableSchema,
            annotationContract = annotationContract,
            generatedPackageName = generatedDtoPackageName,
            generatedRootSimpleNames = polymorphicRootGenerator.generatedDtoSimpleNames,
        )?.let(::addAnnotation)
    }

    private fun addDoc() {
        KspDtoDescriptionRenderer.render(lsiDtoType)?.let(typeBuilder::addAnnotation)
    }

    private fun addMembers() {
        if (isSerializerRequired) {
            typeBuilder.addAnnotation(
                AnnotationSpec
                    .builder(ctx.jacksonTypes.jsonSerialize)
                    .addMember("using = %T::class", getDtoClassName("Serializer"))
                    .build()
            )
        }
        if (isBuilderRequired) {
            typeBuilder.addAnnotation(
                AnnotationSpec
                    .builder(ctx.jacksonTypes.jsonDeserialize)
                    .addMember("builder = %T::class", getDtoClassName("Builder"))
                    .build()
            )
        }
        val isSpecification = lsiDtoType.isSpecification()
        if (polymorphicSuperInterfaceName != null) {
            typeBuilder.addSuperinterface(polymorphicSuperInterfaceName)
        } else {
            lsiDtoType.generatedBaseContractKind(immutableSchema)?.let { baseContractKind ->
                typeBuilder.addSuperinterface(generatedBaseContractTypeName(baseContractKind))
            }
        }
        for (typeRef in lsiDtoType.superInterfaces) {
            typeBuilder.addSuperinterface(KspDtoTypeRefRenderer.render(typeRef, workspace))
        }
        if (isHibernateValidatorEnhancementRequired) {
            typeBuilder.addSuperinterface(
                KspDtoHibernateValidatorRenderer.renderEnhancedBeanType(workspace),
            )
        }

        addPrimaryConstructor()
        if (!isSpecification) {
            addConverterConstructor()
        }

        for (prop in lsiDtoType.propsInDeclarationOrder(lsiGraph)) {
            addProp(prop)
            if (prop is DtoBaseProp) {
                addStateProp(prop)
            }
        }
        if (isSpecification) {
            addEntityType()
            addApplyTo()
        } else {
            addToEntity()
            addToEntityEx()
            addToEntityImpl()
        }

        if (isSpecification) {
            for (prop in lsiDtoType.basePropsInDeclarationOrder(lsiGraph)) {
                val converter = KspDtoSpecificationRenderer.renderConverterOrNull(
                    prop = prop,
                    graph = lsiGraph,
                    immutableSchema = immutableSchema,
                    workspace = workspace,
                )
                if (converter != null) {
                    typeBuilder.addFunction(converter)
                }
            }
        }

        typeBuilder.addCopy()
        typeBuilder.addHashCode()
        typeBuilder.addEquals()
        typeBuilder.addToString()

        if (
            !isSpecification &&
            (
                !polymorphicBranch || lsiDtoType.hasDtoPropAccessorFields(
                    graph = lsiGraph,
                    immutableSchema = immutableSchema,
                    targetLanguage = LsiLanguage.KOTLIN,
                    generatedTargetType = ::generatedTargetType,
                )
            )
        ) {
            typeBuilder.addType(
                TypeSpec
                    .companionObjectBuilder()
                    .addAnnotation(generatedAnnotation())
                    .apply {
                        if (!polymorphicBranch) {
                            addMetadata()
                        }
                        for (prop in lsiDtoType.basePropsInDeclarationOrder(lsiGraph)) {
                            addAccessorField(prop)
                        }
                        for (prop in lsiDtoType.foldPropsInDeclarationOrder(lsiGraph)) {
                            addFoldNullGuardAccessorField(prop)
                        }
                    }
                    .build()
            )
        }

        generateNestedDtoTypes()

        if (isHibernateValidatorEnhancementRequired) {
            KspDtoHibernateValidatorRenderer.renderFunctions(
                dtoType = lsiDtoType,
                graph = lsiGraph,
                immutableSchema = immutableSchema,
                workspace = workspace,
            ).forEach(typeBuilder::addFunction)
        }
        if (isSerializerRequired) {
            typeBuilder.addType(
                KspDtoSerializerRenderer.render(
                    dtoType = lsiDtoType,
                    graph = lsiGraph,
                    immutableSchema = immutableSchema,
                    jacksonVersion = jacksonVersion,
                    generatedDtoPackageName = generatedDtoPackageName,
                    generatedDtoSimpleNames = generatedDtoSimpleNames,
                )
            )
        }
        if (isBuilderRequired) {
            typeBuilder.addType(
                KspDtoInputBuilderRenderer.render(
                    dtoType = lsiDtoType,
                    graph = lsiGraph,
                    immutableSchema = immutableSchema,
                    annotationContract = annotationContract,
                    workspace = workspace,
                    jacksonVersion = jacksonVersion,
                    generatedDtoPackageName = generatedDtoPackageName,
                    generatedDtoSimpleNames = generatedDtoSimpleNames,
                    generatedDtoTypeNamesByTypeId = generatedDtoTypeNamesByTypeId,
                )
            )
        }
    }

    private fun generatedBaseContractTypeName(kind: DtoGeneratedBaseContractKind): TypeName {
        val rawType = when (kind) {
            DtoGeneratedBaseContractKind.ENTITY_INPUT -> INPUT_CLASS_NAME
            DtoGeneratedBaseContractKind.ENTITY_VIEW -> VIEW_CLASS_NAME
            DtoGeneratedBaseContractKind.ENTITY_SPECIFICATION -> K_SPECIFICATION_CLASS_NAME
            DtoGeneratedBaseContractKind.EMBEDDABLE -> EMBEDDED_DTO_CLASS_NAME
        }
        return rawType.parameterizedBy(baseType.className)
    }

    private fun generateNestedDtoTypes() {
        for (prop in dtoType.dtoProps) {
            if (polymorphicRootPropOrNull(prop) != null) {
                continue
            }
            val lsiTargetType = lsiDtoType
                .baseProp(lsiGraph, prop.name)
                .generatedTargetType(lsiGraph)
                ?: continue
            val targetType = prop.targetType ?: throw DtoException(
                "Compiled DTO property \"${prop.name}\" has no target required by the frozen DTO graph"
            )
            val childSimpleName = JimmerDtoPoetTypeNames.requireDirectChildSimpleName(
                ownerTypeName = JimmerDtoPoetTypeNames.create(
                    generatedDtoPackageName,
                    generatedDtoSimpleNames,
                ),
                targetType = lsiTargetType,
                typeIdsByTypeName = generatedDtoTypeIdsByTypeName,
            )
            registerGeneratedDtoTypeName(lsiTargetType, generatedDtoSimpleNames + childSimpleName)
            DtoGenerator(
                ctx = ctx,
                mutable = mutable,
                dtoType = targetType,
                lsiDtoType = lsiTargetType,
                parent = this,
                innerClassName = childSimpleName,
            ).generate(emptyList())
        }
        for (foldProp in dtoType.foldProps) {
            if (polymorphicRootPropOrNull(foldProp) != null) {
                continue
            }
            val lsiTargetType = lsiDtoType
                .foldProp(lsiGraph, foldProp.name)
                .generatedTargetType(lsiGraph)
            val childSimpleName = JimmerDtoPoetTypeNames.requireDirectChildSimpleName(
                ownerTypeName = JimmerDtoPoetTypeNames.create(
                    generatedDtoPackageName,
                    generatedDtoSimpleNames,
                ),
                targetType = lsiTargetType,
                typeIdsByTypeName = generatedDtoTypeIdsByTypeName,
            )
            registerGeneratedDtoTypeName(lsiTargetType, generatedDtoSimpleNames + childSimpleName)
            DtoGenerator(
                ctx = ctx,
                mutable = mutable,
                dtoType = foldProp.targetType,
                lsiDtoType = lsiTargetType,
                parent = this,
                innerClassName = childSimpleName,
            ).generate(emptyList())
        }
    }

    private fun registerGeneratedDtoTypeName(
        type: LsiDtoType,
        simpleNames: List<String>,
    ) {
        val typeName = JimmerDtoPoetTypeNames.create(generatedDtoPackageName, simpleNames)
        JimmerDtoPoetTypeNames.requirePlanned(
            graph = lsiGraph,
            type = type,
            typeIdsByTypeName = generatedDtoTypeIdsByTypeName,
            typeName = typeName,
        )
        JimmerDtoPoetTypeNames.register(
            graph = lsiGraph,
            type = type,
            typeNamesByTypeId = generatedDtoTypeNamesByTypeId,
            locallyRegisteredTypeIds = locallyGeneratedDtoTypeIds,
            typeName = typeName,
        )
    }

    private fun addPolymorphicMembers(baseContractKind: DtoGeneratedBaseContractKind) {
        typeBuilder.addSuperinterface(generatedBaseContractTypeName(baseContractKind))
        for (typeRef in lsiDtoType.superInterfaces) {
            typeBuilder.addSuperinterface(KspDtoTypeRefRenderer.render(typeRef, workspace))
        }
        if (isHibernateValidatorEnhancementRequired) {
            typeBuilder.addSuperinterface(
                KspDtoHibernateValidatorRenderer.renderEnhancedBeanType(workspace),
            )
        }
        for (prop in lsiDtoType.propsInDeclarationOrder(lsiGraph)) {
            typeBuilder.addAccessorDeclaration(prop)
        }
        generateNestedDtoTypes()
        val polymorphism = dtoType.polymorphism ?: error("Internal bug: no DTO polymorphism")
        typeBuilder.addType(
            TypeSpec
                .companionObjectBuilder()
                .addAnnotation(generatedAnnotation())
                .apply {
                    addPolymorphicMetadata()
                }
                .build()
        )
        polymorphism.defaultBranch?.let { branch ->
            generatePolymorphicBranch(branch, getDtoClassName())
        }
        for (branch in polymorphism.typeBranches) {
            generatePolymorphicBranch(branch, getDtoClassName())
        }
    }

    private fun generatePolymorphicBranch(
        branch: DtoPolymorphicBranch<ImmutableType, ImmutableProp>,
        superInterfaceName: TypeName,
    ) {
        val lsiBranch = lsiPolymorphicBranch(branch)
        DtoGenerator(
            ctx = ctx,
            mutable = mutable,
            dtoType = dtoType.mergedWith(branch.dtoType),
            lsiDtoType = lsiBranch.mergedType(lsiGraph),
            parent = this,
            innerClassName = branch.className,
            polymorphicSuperInterfaceName = superInterfaceName,
            lsiPolymorphicBranch = lsiBranch,
        ).generate(emptyList())
    }

    private fun lsiPolymorphicBranch(
        branch: DtoPolymorphicBranch<ImmutableType, ImmutableProp>,
    ): site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch {
        return try {
            lsiDtoType.generatedPolymorphicBranch(
                branch.className,
                DtoPolymorphicBranchKind.valueOf(branch.kind.name),
            )
        } catch (ex: IllegalArgumentException) {
            throw DtoException(
                ex.message ?: "Cannot resolve frozen DTO polymorphic branch \"${branch.className}\"",
                ex,
            )
        }
    }

    private fun FileSpec.Builder.addExtensions(includeBlockConverter: Boolean = true) {
        if (!lsiDtoType.isSpecification()) {
            addToEntities()
            if (includeBlockConverter) {
                addToEntitiesEx()
            }
        }
    }

    private fun TypeSpec.Builder.addMetadata() {
        addProperty(
            PropertySpec
                .builder(
                    "METADATA",
                    DTO_METADATA_CLASS_NAME.parameterizedBy(
                        baseType.className,
                        getDtoClassName()
                    )
                )
                .addAnnotation(JVM_STATIC_CLASS_NAME)
                .initializer(
                    CodeBlock
                        .builder()
                        .apply {
                            add("\n")
                            indent()
                            add(
                                "%T<%T, %T>(\n",
                                DTO_METADATA_CLASS_NAME,
                                baseType.className, getDtoClassName()
                            )
                            indent()
                            add("%T::class.java,\n", getDtoClassName())
                            add(
                                "%L",
                                KspDtoMetadataFetcherRenderer.render(
                                    dtoType = lsiDtoType,
                                    graph = lsiGraph,
                                    immutableSchema = immutableSchema,
                                    workspace = workspace,
                                    configContractResolution = configContractResolution,
                                    generatedPackageName = generatedDtoPackageName,
                                    generatedSimpleNames = generatedDtoSimpleNames,
                                    generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
                                    batchRootDtoTypeNames = rootDtoTypeNamesByTypeId,
                                    registerImport = { sourceImport ->
                                        metadataFetcherPoetImports += sourceImport
                                    },
                                )
                            )
                            add(",\n::%T\n", getDtoClassName())
                            unindent()
                            add(")")
                            unindent()
                        }
                        .build()
                )
                .build()
        )
    }

    private fun TypeSpec.Builder.addPolymorphicMetadata() {
        addProperty(
            PropertySpec
                .builder(
                    "METADATA",
                    DTO_METADATA_CLASS_NAME.parameterizedBy(
                        baseType.className,
                        getDtoClassName()
                    )
                )
                .addAnnotation(JVM_FIELD_CLASS_NAME)
                .initializer(
                    CodeBlock
                        .builder()
                        .apply {
                            add("\n")
                            indent()
                            add(
                                "%T<%T, %T>(\n",
                                DTO_METADATA_CLASS_NAME,
                                baseType.className,
                                getDtoClassName()
                            )
                            indent()
                            add("%T::class.java,\n", getDtoClassName())
                            add(
                                "%L",
                                KspDtoMetadataFetcherRenderer.render(
                                    dtoType = lsiDtoType,
                                    graph = lsiGraph,
                                    immutableSchema = immutableSchema,
                                    workspace = workspace,
                                    configContractResolution = configContractResolution,
                                    generatedPackageName = generatedDtoPackageName,
                                    generatedSimpleNames = generatedDtoSimpleNames,
                                    generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
                                    batchRootDtoTypeNames = rootDtoTypeNamesByTypeId,
                                    registerImport = { sourceImport ->
                                        metadataFetcherPoetImports += sourceImport
                                    },
                                )
                            )
                            add(",\n")
                            KspDtoPolymorphicMetadataConverterRenderer.appendTo(
                                builder = this,
                                dtoType = lsiDtoType,
                                graph = lsiGraph,
                                workspace = workspace,
                                generatedPackageName = generatedDtoPackageName,
                                generatedRootSimpleNames = generatedDtoSimpleNames,
                            )
                            add("\n")
                            unindent()
                            add(")")
                            unindent()
                        }
                        .build()
                )
                .build()
        )
    }

    private fun addStateProp(prop: DtoBaseProp) {
        KspDtoLoadedStateRenderer.renderStorageProperty(
            prop = prop,
            graph = lsiGraph,
            mutable = mutable,
        )?.let(typeBuilder::addProperty)
    }

    private fun addProp(prop: LsiDtoProp) {
        val typeName = propTypeName(prop)
        typeBuilder.addProperty(
            PropertySpec
                .builder(prop.name, typeName)
                .mutable(mutable)
                .apply {
                    if (interfacePropNames.contains(prop.name)) {
                        addModifiers(KModifier.OVERRIDE)
                    }
                    KspDtoDescriptionRenderer.render(prop, lsiGraph)?.let { annotation ->
                        addAnnotation(annotation)
                    }
                    if (
                        !isBuilderRequired &&
                        prop.annotations.none { annotation ->
                            annotation.typeId.value == ctx.jacksonTypes.jsonProperty.reflectionName()
                        }
                    ) {
                        addAnnotation(
                            AnnotationSpec
                                .builder(ctx.jacksonTypes.jsonProperty)
                                .useSiteTarget(AnnotationSpec.UseSiteTarget.PARAM)
                                .apply {
                                    addMember("%S", prop.name)
                                    if (!typeName.isNullable) {
                                        addMember("required = true")
                                    }
                                }
                                .build()
                        )
                    }
                    if (prop.requiresFixedInputField(lsiGraph)) {
                        addAnnotation(FIXED_INPUT_FIELD_CLASS_NAME)
                    }
                    addAnnotations(
                        KspDtoPropAnnotationRenderer.renderConcrete(
                            dtoProp = prop,
                            annotationContract = annotationContract,
                            immutableSchema = immutableSchema,
                            workspace = workspace,
                            excludedAnnotationQualifiedName = if (isBuilderRequired) {
                                ctx.jacksonTypes.jsonDeserialize.reflectionName()
                            } else {
                                null
                            },
                        )
                    )
                    initializer("%N", prop.name)
                    if (mutable) {
                        prop
                            .dtoLoadedStateStorageNameOrNull(lsiGraph, LsiLanguage.KOTLIN)
                            ?.let { stateProp ->
                            val name = prop.name.takeIf { it != "field" } ?: "value"
                            setter(
                                FunSpec
                                    .setterBuilder()
                                    .addParameter(name, typeName)
                                    .addStatement("field = %N", name)
                                    .addStatement("%N = true", stateProp)
                                    .build()
                            )
                        }
                    }
                }
                .build()
        )
    }

    private fun TypeSpec.Builder.addAccessorDeclaration(prop: LsiDtoProp) {
        val typeName = propTypeName(prop)
        addProperty(
            PropertySpec
                .builder(prop.name, typeName)
                .addModifiers(KModifier.ABSTRACT)
                .apply {
                    if (interfacePropNames.contains(prop.name)) {
                        addModifiers(KModifier.OVERRIDE)
                    }
                    KspDtoDescriptionRenderer.render(prop, lsiGraph)?.let { annotation ->
                        addAnnotation(annotation)
                    }
                    addAnnotations(
                        KspDtoPropAnnotationRenderer.renderAbstractAccessor(
                            dtoProp = prop,
                            annotationContract = annotationContract,
                            immutableSchema = immutableSchema,
                            workspace = workspace,
                        )
                    )
                }
                .build()
        )
    }

    private fun addPrimaryConstructor() {
        typeBuilder.primaryConstructor(
            FunSpec
                .constructorBuilder()
                .apply {
                    for (lsiProp in lsiDtoType.propsInDeclarationOrder(lsiGraph)) {
                        val typeName = propTypeName(lsiProp)
                        addParameter(
                            ParameterSpec.builder(lsiProp.name, typeName)
                                .apply {
                                    val defaultValueText = (lsiProp as? DtoUserProp)?.defaultValueText
                                    if (defaultValueText != null) {
                                        defaultValue(defaultValueText)
                                    } else if (typeName.isNullable) {
                                        defaultValue("null")
                                    } else if (typeName == BOOLEAN) {
                                        defaultValue("false")
                                    }
                                }
                                .build()
                        )
                        lsiProp
                            .dtoLoadedStateStorageNameOrNull(lsiGraph, LsiLanguage.KOTLIN)
                            ?.let { statePropName ->
                                addParameter(
                                    ParameterSpec
                                        .builder(statePropName, BOOLEAN)
                                        .apply {
                                            if (lsiProp.nullable) {
                                                defaultValue("%N !== null", lsiProp.name)
                                            } else {
                                                defaultValue("true")
                                            }
                                        }
                                        .build()
                                )
                            }
                    }
                }
                .build()
        )
    }

    private fun addConverterConstructor() {
        typeBuilder.addFunction(
            FunSpec
                .constructorBuilder()
                .addParameter("base", baseType.className)
                .apply {
                    for (userProp in lsiDtoType.userPropsInDeclarationOrder(lsiGraph)) {
                        addParameter(
                            ParameterSpec
                                .builder(
                                    userProp.alias,
                                    KspDtoTypeRefRenderer.render(
                                        userProp.type,
                                        workspace,
                                    ),
                                )
                                .apply {
                                    userProp.kotlinDefaultValueTextOrNull()?.let(::defaultValue)
                                }
                                .build()
                        )
                    }
                }
                .callThisConstructor(lsiDtoType.propsInDeclarationOrder(lsiGraph).map { prop ->
                    CodeBlock
                        .builder()
                        .indent()
                        .add("\n")
                        .apply {
                            when (prop) {
                                is DtoFoldProp -> add(
                                    "%L",
                                    KspDtoFoldValueRenderer.render(
                                        prop = prop,
                                        graph = lsiGraph,
                                        workspace = workspace,
                                        baseParameterName = "base",
                                        nullGuardAccessorName = foldNullGuardAccessorFieldName(prop.name),
                                        generatedTargetType = ::generatedTargetType,
                                        generatedTypeNames = generatedDtoTypeIdsByTypeName.keys,
                                    ),
                                )

                                is DtoBaseProp -> {
                                    val stateInitializer = KspDtoLoadedStateRenderer.renderBaseInitializer(
                                        prop = prop,
                                        graph = lsiGraph,
                                        accessorName = accessorFieldName(prop.name),
                                        baseParameterName = "base",
                                    )
                                    val simple = prop.usesDirectBaseAccess(
                                        graph = lsiGraph,
                                        immutableSchema = immutableSchema,
                                        targetLanguage = LsiLanguage.KOTLIN,
                                        generatedTargetType = ::generatedTargetType,
                                    )
                                    add(
                                        "%L",
                                        KspDtoBaseValueRenderer.render(
                                            prop = prop,
                                            graph = lsiGraph,
                                            immutableSchema = immutableSchema,
                                            workspace = workspace,
                                            accessorName = accessorFieldName(prop.name),
                                            baseParameterName = "base",
                                            conversionErrorMessage =
                                                "Cannot convert \"${baseType.className}\" to " +
                                                    "\"${getDtoClassName()}\" because the cannot get non-null " +
                                                    "value for \"${prop.name}\"",
                                            generatedTargetType = ::generatedTargetType,
                                            generatedTypeNames = generatedDtoTypeIdsByTypeName.keys,
                                        ),
                                    )
                                    stateInitializer?.let { initializer ->
                                        add(if (simple) ",\n%L" else ",\n%L\n", initializer)
                                    }
                                }

                                is DtoUserProp -> add("%N", prop.alias)
                            }
                        }
                        .unindent()
                        .build()
                })
                .build()
        )
    }

    private fun addToEntity() {
        val discriminatorProp = polymorphicInputDiscriminatorProp()
        typeBuilder.addFunction(
            FunSpec
                .builder(if (entityBase) "toEntity" else "toImmutable")
                .addModifiers(KModifier.OVERRIDE)
                .returns(baseType.className)
                .apply {
                    if (discriminatorProp !== null && isDefaultPolymorphicInputBranch) {
                        addDefaultPolymorphicInputToEntityBody(discriminatorProp, null)
                    } else {
                        addStatement(
                            "return %M(%T::class).by(null, false, this@%L::%L)",
                            NEW,
                            baseType.className,
                            innerClassName ?: dtoType.name!!,
                            if (entityBase) "toEntityImpl" else "toImmutableImpl"
                        )
                    }
                }
                .build()
        )
    }

    private fun FileSpec.Builder.addToEntities() {
        val dtoClassName = getDtoClassName()
        addFunction(
            FunSpec
                .builder(if (entityBase) "toEntities" else "toImmutables")
                .addAnnotation(generatedAnnotation(baseType.className))
                .receiver(ITERABLE.parameterizedBy(dtoClassName))
                .returns(LIST.parameterizedBy(baseType.className))
                .addStatement(
                    "return map(%T::%L)",
                    dtoClassName,
                    if (entityBase) "toEntity" else "toImmutable"
                )
                .build()
        )
    }

    private fun FileSpec.Builder.addToEntitiesEx() {
        addFunction(
            FunSpec
                .builder(if (entityBase) "toEntities" else "toImmutables")
                .addAnnotation(generatedAnnotation(baseType.className))
                .receiver(ITERABLE.parameterizedBy(getDtoClassName()))
                .returns(LIST.parameterizedBy(baseType.className))
                .addParameter(
                    "block",
                    LambdaTypeName.get(
                        baseType.draftClassName,
                        emptyList(),
                        UNIT
                    ),
                )
                .apply {
                    beginControlFlow("return map")
                    addStatement(
                        "it.%L(block)",
                        if (entityBase) "toEntity" else "toImmutable"
                    )
                    endControlFlow()
                }
                .build()
        )
    }

    private fun addToEntityEx() {
        val discriminatorProp = polymorphicInputDiscriminatorProp()
        typeBuilder.addFunction(
            FunSpec
                .builder(if (entityBase) "toEntity" else "toImmutable")
                .addParameter(
                    "block",
                    LambdaTypeName.get(
                        baseType.draftClassName,
                        emptyList(),
                        UNIT
                    ),
                )
                .returns(baseType.className)
                .apply {
                    if (discriminatorProp !== null && isDefaultPolymorphicInputBranch) {
                        addDefaultPolymorphicInputToEntityBody(discriminatorProp, "block")
                    } else {
                        beginControlFlow(
                            "return %M(%T::class).by",
                            NEW,
                            baseType.className
                        )
                        addStatement(
                            "%L(this)",
                            if (entityBase) "toEntityImpl" else "toImmutableImpl"
                        )
                        addStatement("block(this)")
                        endControlFlow()
                    }
                }
                .build()
        )
    }

    private fun FunSpec.Builder.addDefaultPolymorphicInputToEntityBody(
        discriminatorProp: DtoBaseProp,
        blockParameterName: String?,
    ) {
        addCode(
            KspDtoPolymorphicInputRenderer.renderDefaultBranchBody(
                dtoType = lsiDtoType,
                branch = requireNotNull(currentLsiPolymorphicBranchOrNull) {
                    "Frozen DTO default polymorphic branch is required"
                },
                discriminatorProp = discriminatorProp,
                graph = lsiGraph,
                immutableSchema = immutableSchema,
                workspace = workspace,
                generatedPackageName = generatedDtoPackageName,
                generatedSimpleNames = generatedDtoSimpleNames,
                blockParameterName = blockParameterName,
            ),
        )
    }

    private fun addToEntityImpl() {
        addApplyToDraft()
        typeBuilder.addFunction(
            FunSpec
                .builder(if (entityBase) "toEntityImpl" else "toImmutableImpl")
                .addKdoc(DOC_EXPLICIT_FUN)
                .addModifiers(KModifier.PRIVATE)
                .addParameter("_draft", baseType.draftClassName)
                .addStatement("this.__applyTo(_draft)")
                .build()
        )
    }

    private fun addApplyToDraft() {
        typeBuilder.addFunction(
            FunSpec
                .builder("__applyTo")
                .addModifiers(KModifier.INTERNAL)
                .addParameter("_draft", baseType.draftClassName)
                .apply {
                    polymorphicInputDiscriminatorProp()
                        ?.takeIf { isTypedPolymorphicInputBranch }
                        ?.let { discriminatorProp ->
                            addCode(
                                KspDtoPolymorphicInputRenderer.renderTypedDiscriminatorValidation(
                                    dtoType = lsiDtoType,
                                    branch = requireNotNull(currentLsiPolymorphicBranchOrNull) {
                                        "Frozen DTO typed polymorphic branch is required"
                                    },
                                    discriminatorProp = discriminatorProp,
                                    graph = lsiGraph,
                                    immutableSchema = immutableSchema,
                                    workspace = workspace,
                                    generatedPackageName = generatedDtoPackageName,
                                    generatedSimpleNames = generatedDtoSimpleNames,
                                )
                            )
                        }
                    for (prop in lsiDtoType.propsInDeclarationOrder(lsiGraph)) {
                        when (prop) {
                            is DtoFoldProp -> {
                                addCode(
                                    KspDtoFoldDraftApplyRenderer.render(
                                        prop = prop,
                                        draftParameterName = "_draft",
                                    ),
                                )
                            }

                            is DtoBaseProp -> {
                                if (prop.isDraftWriteSkipped(lsiGraph, immutableSchema, LsiLanguage.KOTLIN)) {
                                    continue
                                }
                                val statePropName = prop
                                    .dtoLoadedStateStorageNameOrNull(lsiGraph, LsiLanguage.KOTLIN)
                                val nonNullGuard = prop.requiresNonNullDraftWriteGuard(lsiGraph)
                                if (statePropName != null) {
                                    beginControlFlow("if (%N)", statePropName)
                                } else if (nonNullGuard) {
                                    beginControlFlow("if (%N != null)", prop.name)
                                }
                                addDraftAssignment(prop, prop.name)
                                if (statePropName != null || nonNullGuard) {
                                    endControlFlow()
                                }
                            }

                            is DtoUserProp -> Unit
                        }
                    }
                }
                .build()
        )
    }

    private fun FunSpec.Builder.addDraftAssignment(
        prop: DtoBaseProp,
        valueName: String,
    ) {
        addCode(
            KspDtoDraftWriteRenderer.render(
                prop = prop,
                graph = lsiGraph,
                immutableSchema = immutableSchema,
                workspace = workspace,
                accessorName = accessorFieldName(prop.name),
                draftName = "_draft",
                valueName = valueName,
                generatedTargetType = ::generatedTargetType,
            ),
        )
    }

    private fun polymorphicInputDiscriminatorProp(): DtoBaseProp? {
        if (!polymorphicBranch) {
            return null
        }
        return lsiDtoType.selectedPolymorphicInputDiscriminatorPropOrNull(
            lsiGraph,
            immutableSchema,
        )
    }

    private val isDefaultPolymorphicInputBranch: Boolean
        get() = lsiPolymorphicBranch?.kind == DtoPolymorphicBranchKind.DEFAULT

    private val isTypedPolymorphicInputBranch: Boolean
        get() = lsiPolymorphicBranch?.kind == DtoPolymorphicBranchKind.TYPE

    private val currentLsiPolymorphicBranchOrNull: site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch?
        get() {
            if (!polymorphicBranch) {
                return null
            }
            val branch = requireNotNull(lsiPolymorphicBranch)
            return try {
                branch.requireGeneratedMergedType(lsiGraph, lsiDtoType)
            } catch (ex: IllegalArgumentException) {
                throw DtoException(
                    "Frozen DTO polymorphic branch does not match generated branch",
                    ex,
                )
            }
        }

    private fun addEntityType() {
        typeBuilder.addFunction(
            KspDtoSpecificationRenderer.renderEntityType(
                dtoType = lsiDtoType,
                immutableSchema = immutableSchema,
                workspace = workspace,
            ),
        )
    }

    private fun addApplyTo() {
        typeBuilder.addFunction(
            KspDtoSpecificationRenderer.renderApplyTo(
                dtoType = lsiDtoType,
                graph = lsiGraph,
                immutableSchema = immutableSchema,
                workspace = workspace,
            ),
        )
    }

    private fun TypeSpec.Builder.addAccessorField(prop: DtoBaseProp) {
        if (
            !prop.requiresDtoPropAccessor(
                graph = lsiGraph,
                immutableSchema = immutableSchema,
                targetLanguage = LsiLanguage.KOTLIN,
                generatedTargetType = ::generatedTargetType,
            )
        ) {
            return
        }
        addAccessorField(
            prop,
            accessorFieldName(prop.name),
            prop.acceptsNullInAccessor(lsiGraph),
            true,
        )
    }

    private fun TypeSpec.Builder.addFoldNullGuardAccessorField(prop: DtoFoldProp) {
        val nullGuardProp = prop.nullGuardProp(lsiGraph) ?: return
        addAccessorField(
            nullGuardProp,
            foldNullGuardAccessorFieldName(prop.name),
            true,
            false,
        )
    }

    private fun TypeSpec.Builder.addAccessorField(
        prop: DtoBaseProp,
        fieldName: String,
        acceptNull: Boolean,
        withConverters: Boolean,
    ) {
        val builder = PropertySpec.builder(
            fieldName,
            DTO_PROP_ACCESSOR,
            KModifier.PRIVATE
        ).initializer(
            KspDtoAccessorRenderer.render(
                prop = prop,
                graph = lsiGraph,
                immutableSchema = immutableSchema,
                workspace = workspace,
                acceptNull = acceptNull,
                withConverters = withConverters,
                generatedTargetType = ::generatedTargetType,
                generatedTypeNames = generatedDtoTypeIdsByTypeName.keys,
            )
        )
        addProperty(builder.build())
    }

    private fun propTypeName(prop: AbstractProp): TypeName =
        propTypeName(lsiDtoType.prop(lsiGraph, prop.name))

    private fun propTypeName(prop: LsiDtoProp): TypeName =
        KspDtoTypeRefRenderer.render(
            type = prop.generatedValueType(
                graph = lsiGraph,
                immutableSchema = immutableSchema,
                targetLanguage = LsiLanguage.KOTLIN,
                generatedTargetType = ::generatedTargetType,
            ),
            workspace = workspace,
            generatedTypeNames = generatedDtoTypeIdsByTypeName.keys,
        )

    private fun collectNames(list: MutableList<String>) {
        if (parent == null) {
            list.add(dtoType.name!!)
        } else if (innerClassName !== null) {
            parent.collectNames(list)
            list.add(innerClassName)
        }
    }

    private fun polymorphicRootPropOrNull(prop: AbstractProp): LsiDtoProp? {
        val polymorphicOwner = parent
        if (!polymorphicBranch || polymorphicOwner == null) {
            return null
        }
        return polymorphicOwner.lsiDtoType.promotedPolymorphicRootPropOrNull(
            lsiGraph,
            lsiDtoType.prop(lsiGraph, prop.name),
        )
    }

    private fun generatedTargetType(prop: LsiDtoProp): LsiDeclaredType {
        val ownerTypeName = JimmerDtoPoetTypeNames.create(
            generatedDtoPackageName,
            generatedDtoSimpleNames,
        )
        JimmerDtoPoetTypeNames.requirePlanned(
            graph = lsiGraph,
            type = lsiDtoType,
            typeIdsByTypeName = generatedDtoTypeIdsByTypeName,
            typeName = ownerTypeName,
        )
        return JimmerDtoPoetTypeNames.toLsiGeneratedTargetType(
            graph = lsiGraph,
            prop = prop,
            generatedOwnerTypeName = ownerTypeName,
            generatedDtoTypeIdsByTypeName = generatedDtoTypeIdsByTypeName,
            batchRootDtoTypeNames = rootDtoTypeNamesByTypeId,
        )
    }

    private fun accessorFieldName(propName: String): String =
        StringUtil.snake("${propName}Accessor", SnakeCase.UPPER)

    private fun foldNullGuardAccessorFieldName(propName: String): String =
        StringUtil.snake("${propName}NullGuardAccessor", SnakeCase.UPPER)

    private fun TypeSpec.Builder.addCopy() {
        addFunction(
            FunSpec
                .builder("copy")
                .returns(getDtoClassName())
                .apply {
                    val args = mutableListOf<String>()
                    for (prop in lsiDtoType.propsInDeclarationOrder(lsiGraph)) {
                        addParameter(
                            ParameterSpec.builder(prop.name, propTypeName(prop))
                                .defaultValue("this.%N", prop.name)
                                .build()
                        )
                        args += prop.name
                        prop.dtoLoadedStateStorageNameOrNull(lsiGraph, LsiLanguage.KOTLIN)
                            ?.let {
                            addParameter(
                                ParameterSpec.builder(it, BOOLEAN)
                                    .defaultValue("this.%N", it)
                                    .build()
                            )
                            args += it
                        }
                    }
                    val argumentBlock = CodeBlock.builder().apply {
                        args.forEachIndexed { index, name ->
                            if (index != 0) {
                                add(", ")
                            }
                            add("%N", name)
                        }
                    }.build()
                    addStatement("return %T(%L)", getDtoClassName(), argumentBlock)
                }
                .build()
        )
    }

    private fun TypeSpec.Builder.addHashCode() {
        addFunction(
            KspDtoEqualityRenderer.renderHashCode(
                dtoType = lsiDtoType,
                graph = lsiGraph,
                immutableSchema = immutableSchema,
            ),
        )
    }

    private fun TypeSpec.Builder.addEquals() {
        addFunction(
            KspDtoEqualityRenderer.renderEquals(
                dtoType = lsiDtoType,
                graph = lsiGraph,
                immutableSchema = immutableSchema,
                generatedDtoPackageName = generatedDtoPackageName,
                generatedDtoSimpleNames = generatedDtoSimpleNames,
            ),
        )
    }

    private fun TypeSpec.Builder.addToString() {
        addFunction(KspDtoToStringRenderer.render(lsiDtoType, lsiGraph, simpleNamePart()))
    }

    private fun simpleNamePart(): String =
        (innerClassName ?: dtoType.name!!).let { name ->
            parent
                ?.let { "${it.simpleNamePart()}.$name" }
                ?: name
        }

    private val isSerializerRequired: Boolean by lazy {
        lsiDtoType.requiresDynamicInputSerialization(lsiGraph)
    }

    private val isBuilderRequired: Boolean by lazy {
        lsiDtoType.requiresInputBuilder(lsiGraph)
    }

    private val isHibernateValidatorEnhancementRequired: Boolean by lazy {
        lsiDtoType.requiresHibernateValidatorEnhancement(
            graph = lsiGraph,
            enhancementEnabled = hibernateValidatorEnhancement,
        )
    }

    companion object {

        @JvmStatic
        private val NEW = MemberName("org.babyfish.jimmer.kt", "new")

        private fun String.simpleName() =
            lastIndexOf('.').let {
                if (it == -1) {
                    this
                } else {
                    substring(it + 1)
                }
            }

        private fun TypeName.isArray(): Boolean =
            if (this is ClassName) {
                when (this.reflectionName()) {
                    "kotlin.BooleanArray", "kotlin.CharArray",
                    "kotlin.ByteArray", "kotlin.ShortArray", "kotlin.IntArray", "kotlin.LongArray",
                    "kotlin.FloatArray", "kotlin.DoubleArray",
                    "kotlin.Array",
                        -> true

                    else -> false
                }
            } else if (this is ParameterizedTypeName) {
                this.rawType.isArray()
            } else {
                false
            }

        val DOC_EXPLICIT_FUN = "Avoid anonymous lambda affects coverage of non-kotlin-friendly tools such as jacoco"


    }
}
