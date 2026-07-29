package org.babyfish.jimmer.ksp.dto

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.babyfish.jimmer.client.ApiIgnore
import org.babyfish.jimmer.compiler.dto.JimmerDtoJacksonVersion
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.compiler.render.ksp.KspDtoDescriptionRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoConfigRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoDraftWriteRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoEnumRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoEqualityRenderer
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
import site.addzero.lsi.jimmer.dto.bodyType
import site.addzero.lsi.jimmer.dto.boundImmutableProp
import site.addzero.lsi.jimmer.dto.contractFor
import site.addzero.lsi.jimmer.dto.dtoAssociatedIdClientType
import site.addzero.lsi.jimmer.dto.dtoClientType
import site.addzero.lsi.jimmer.dto.dtoConverterTargetTypeOrNull
import site.addzero.lsi.jimmer.dto.foldProp
import site.addzero.lsi.jimmer.dto.dtoLoadedStateStorageNameOrNull
import site.addzero.lsi.jimmer.dto.generatedBaseContractKind
import site.addzero.lsi.jimmer.dto.generatedTargetType
import site.addzero.lsi.jimmer.dto.generatedTargetTypeOrNull
import site.addzero.lsi.jimmer.dto.generatedPolymorphicDtoBranchOrder
import site.addzero.lsi.jimmer.dto.isNestedSpecificationFragment
import site.addzero.lsi.jimmer.dto.hiddenFlatPropsInDeclarationOrder
import site.addzero.lsi.jimmer.dto.kotlinDefaultValueTextOrNull
import site.addzero.lsi.jimmer.dto.mergedType
import site.addzero.lsi.jimmer.dto.prop
import site.addzero.lsi.jimmer.dto.promotedPolymorphicRootPropOrNull
import site.addzero.lsi.jimmer.dto.requiresDynamicInputSerialization
import site.addzero.lsi.jimmer.dto.requiresHibernateValidatorEnhancement
import site.addzero.lsi.jimmer.dto.requiresInputBuilder
import site.addzero.lsi.jimmer.dto.requiresNonNullDraftWriteGuard
import site.addzero.lsi.jimmer.dto.requiredPropNames
import site.addzero.lsi.jimmer.dto.selectedPolymorphicInputDiscriminatorPropOrNull
import site.addzero.lsi.jimmer.dto.tailProp
import site.addzero.lsi.jimmer.dto.userProp
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetTypeName
import java.io.OutputStreamWriter
import java.util.*
import kotlin.math.min

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

    private val polymorphicBranch: Boolean
        get() = lsiPolymorphicBranch != null

    private val generatedDtoTypeNamesByTypeId: MutableMap<DtoTypeId, LsiPoetTypeName> =
        (parent?.generatedDtoTypeNamesByTypeId ?: rootDtoTypeNamesByTypeId).toMutableMap()

    private val interfacePropNames = interfaceContractResolution
        .contractFor(lsiDtoType)
        .requiredPropNames()
        .let {
            if (polymorphicBranch) {
                it + root.dtoType.props.map { prop -> prop.name }
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
        val currentTypeName = JimmerDtoPoetTypeNames.create(
            generatedDtoPackageName,
            generatedDtoSimpleNames,
        )
        val oldTypeName = generatedDtoTypeNamesByTypeId.putIfAbsent(lsiDtoType.id, currentTypeName)
        require(oldTypeName == null || oldTypeName == currentTypeName) {
            "Frozen DTO type '${lsiDtoType.id.value}' has conflicting generated names: " +
                "${oldTypeName?.canonicalName} and ${currentTypeName.canonicalName}"
        }
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
        if (dtoType.polymorphism != null) {
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
                        addImports()
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
                        addImports()
                        addType(buildPolymorphicType(baseContractKind))
                        addExtensions(includeBlockConverter = false)
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
                if (dtoType.modifiers.contains(DtoModifier.SEALED)) {
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
        val packages = sortedSetOf<String>().also {
            collectImports(dtoType, it)
        }
        for (pkg in packages) {
            addImport(pkg, "by")
        }
    }

    private fun collectImports(
        dtoType: DtoType<ImmutableType, ImmutableProp>,
        packages: SortedSet<String>,
    ) {
        val dtoBaseType = requireNotNull(dtoType.baseType) {
            "Generated DTO '${dtoType.qualifiedName ?: dtoType.name ?: "<anonymous>"}' " +
                "has no immutable base type"
        }
        packages += dtoBaseType.className.packageName
        for (prop in dtoType.dtoProps) {
            val targetType = prop.targetType
            if (targetType !== null && (!prop.isRecursive || targetType.isFocusedRecursion)) {
                collectImports(targetType, packages)
            } else {
                prop.baseProp.targetType?.className?.packageName?.let {
                    packages += it
                }
            }
        }
        for (foldProp in dtoType.foldProps) {
            collectImports(foldProp.targetType, packages)
        }
        dtoType.polymorphism?.let { polymorphism ->
            polymorphism.defaultBranch?.let {
                collectImports(it.dtoType, packages)
            }
            for (branch in polymorphism.typeBranches) {
                collectImports(branch.dtoType, packages)
            }
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
        val isSpecification = dtoType.modifiers.contains(DtoModifier.SPECIFICATION)
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

        for (prop in dtoType.props) {
            addProp(prop)
            if (prop is DtoProp<*, *>) {
                addStateProp(prop.asDtoProp())
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

        for (prop in dtoType.dtoProps) {
            typeBuilder.addSpecificationConverter(prop)
        }

        typeBuilder.addCopy()
        typeBuilder.addHashCode()
        typeBuilder.addEquals()
        typeBuilder.addToString()

        if (!isSpecification && (!polymorphicBranch || hasAccessorFields())) {
            typeBuilder.addType(
                TypeSpec
                    .companionObjectBuilder()
                    .addAnnotation(generatedAnnotation())
                    .apply {
                        if (!polymorphicBranch) {
                            addMetadata()
                        }
                        for (prop in dtoType.dtoProps) {
                            addAccessorField(prop)
                        }
                        for (prop in dtoType.foldProps) {
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
            val targetType = prop.targetType ?: continue
            if (!prop.isRecursive || targetType.isFocusedRecursion) {
                val lsiTargetType = lsiDtoType
                    .baseProp(lsiGraph, prop.name)
                    .generatedTargetType(lsiGraph)
                    ?: throw DtoException(
                        "Frozen DTO property \"${prop.name}\" has no generated target"
                    )
                val childSimpleName = targetSimpleName(prop)
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
        }
        for (foldProp in dtoType.foldProps) {
            if (polymorphicRootPropOrNull(foldProp) != null) {
                continue
            }
            val lsiTargetType = lsiDtoType
                .foldProp(lsiGraph, foldProp.name)
                .generatedTargetType(lsiGraph)
            val childSimpleName = targetSimpleName(foldProp)
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
        JimmerDtoPoetTypeNames.register(
            graph = lsiGraph,
            type = type,
            typeNamesByTypeId = generatedDtoTypeNamesByTypeId,
            typeName = JimmerDtoPoetTypeNames.create(generatedDtoPackageName, simpleNames),
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
        for (prop in dtoType.props) {
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
        val polymorphism = lsiDtoType.polymorphism
            ?: throw DtoException("Frozen DTO type is not polymorphic")
        val matches = polymorphism.branches.filter { candidate ->
            candidate.className == branch.className && candidate.kind.name == branch.kind.name
        }
        if (matches.size != 1) {
            throw DtoException(
                "Frozen DTO polymorphism must contain exactly one generated branch \"${branch.className}\""
            )
        }
        return matches.single()
    }

    private fun FileSpec.Builder.addExtensions(includeBlockConverter: Boolean = true) {
        if (!dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
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
                            metadataFetcherExpr()
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
                            metadataFetcherExpr()
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

    private fun CodeBlock.Builder.metadataFetcherExpr(
        sourceDtoType: DtoType<ImmutableType, ImmutableProp> = dtoType,
        sourceLsiType: LsiDtoType = lsiDtoType,
    ) {
        val sourceBaseType = requireNotNull(sourceDtoType.baseType) {
            "Generated DTO '${sourceDtoType.qualifiedName ?: sourceDtoType.name ?: "<anonymous>"}' " +
                "has no immutable base type"
        }
        add(
            "%T(%T::class).by {\n",
            NEW_FETCHER_FUN_CLASS_NAME,
            sourceBaseType.className
        )
        indent()
        addFetcherFields(sourceDtoType, sourceLsiType)
        if (sourceDtoType === dtoType) {
            dtoType.polymorphism?.let { polymorphism ->
                for (branch in polymorphism.typeBranches) {
                    addPolymorphicTypeFetcherBranch(branch)
                }
            }
        }
        unindent()
        add("}")
    }

    private fun CodeBlock.Builder.addFetcherFields(
        sourceDtoType: DtoType<ImmutableType, ImmutableProp>,
        sourceLsiType: LsiDtoType,
    ) {
        for (prop in sourceDtoType.dtoProps) {
            if (prop.nextProp === null) {
                addFetcherField(prop, sourceLsiType.baseProp(lsiGraph, prop.name))
            }
        }
        val hiddenLsiProps = sourceLsiType.hiddenFlatPropsInDeclarationOrder(lsiGraph)
        for (hiddenFlatProp in sourceDtoType.hiddenFlatProps) {
            if (!hiddenFlatProp.baseProp.isId) {
                addHiddenFetcherField(
                    hiddenFlatProp,
                    hiddenLsiProps.single { prop -> prop.name == hiddenFlatProp.name },
                )
            }
        }
        for (foldProp in sourceDtoType.foldProps) {
            val lsiFoldProp = sourceLsiType.foldProp(lsiGraph, foldProp.name)
            addFoldFetcherFields(foldProp.targetType, lsiFoldProp.generatedTargetType(lsiGraph))
        }
    }

    private fun CodeBlock.Builder.addPolymorphicTypeFetcherBranch(
        branch: DtoPolymorphicBranch<ImmutableType, ImmutableProp>,
    ) {
        val targetType = branch.targetType ?: error("Internal bug: default branch cannot be rendered as type branch")
        val branchLsiType = lsiPolymorphicBranch(branch).bodyType(lsiGraph)
        if (targetType == baseType) {
            addFetcherFields(branch.dtoType, branchLsiType)
            return
        }
        add("forType(%T::class) {\n", targetType.className)
        indent()
        addFetcherFields(branch.dtoType, branchLsiType)
        unindent()
        add("}\n")
    }

    private fun CodeBlock.Builder.addFoldFetcherFields(
        dtoType: DtoType<ImmutableType, ImmutableProp>,
        lsiType: LsiDtoType,
    ) {
        for (prop in dtoType.dtoProps) {
            if (prop.nextProp === null) {
                addFetcherField(prop, lsiType.baseProp(lsiGraph, prop.name))
            }
        }
        val hiddenLsiProps = lsiType.hiddenFlatPropsInDeclarationOrder(lsiGraph)
        for (hiddenFlatProp in dtoType.hiddenFlatProps) {
            if (!hiddenFlatProp.baseProp.isId) {
                addHiddenFetcherField(
                    hiddenFlatProp,
                    hiddenLsiProps.single { prop -> prop.name == hiddenFlatProp.name },
                )
            }
        }
        for (foldProp in dtoType.foldProps) {
            val lsiFoldProp = lsiType.foldProp(lsiGraph, foldProp.name)
            addFoldFetcherFields(foldProp.targetType, lsiFoldProp.generatedTargetType(lsiGraph))
        }
    }

    private fun CodeBlock.Builder.addFetcherField(
        prop: DtoProp<ImmutableType, ImmutableProp>,
        lsiProp: DtoBaseProp,
    ) {
        if (!prop.baseProp.isId) {
            val configured = lsiProp.config != null
            if (prop.target !== null) {
                if (prop.isRecursive) {
                    add("%N", "${prop.baseProp.name}*")
                    if (!configured) {
                        add("()")
                    }
                } else {
                    add(
                        "%N(%T.METADATA.fetcher)",
                        prop.baseProp.name,
                        propElementName(prop)
                    )
                }
            } else {
                add("%N", prop.baseProp.name)
                if (!configured) {
                    add("()")
                }
            }
            addConfigLambda(lsiProp)
            add("\n")
        }
    }

    private fun CodeBlock.Builder.addConfigLambda(
        prop: DtoBaseProp,
    ) {
        if (prop.config == null) {
            return
        }
        add(
            "%L",
            KspDtoConfigRenderer.render(
                prop = prop,
                graph = lsiGraph,
                immutableSchema = immutableSchema,
                workspace = workspace,
                configContractResolution = configContractResolution,
            )
        )
    }

    private fun CodeBlock.Builder.addHiddenFetcherField(
        prop: DtoProp<ImmutableType, ImmutableProp>,
        lsiProp: DtoBaseProp,
    ) {
        if ("flat" != prop.getFuncName()) {
            addFetcherField(prop, lsiProp)
            return
        }
        val targetDtoType = prop.getTargetType()!!
        val targetLsiType = requireNotNull(lsiProp.generatedTargetType(lsiGraph)) {
            "Frozen flat DTO property '${lsiProp.id.value}' has no generated target type"
        }
        add("%N {\n", prop.baseProp.name)
        indent()
        for (childProp in targetDtoType.dtoProps) {
            addHiddenFetcherField(childProp, targetLsiType.baseProp(lsiGraph, childProp.name))
        }
        val hiddenLsiProps = targetLsiType.hiddenFlatPropsInDeclarationOrder(lsiGraph)
        for (hiddenFlatProp in targetDtoType.hiddenFlatProps) {
            if (!hiddenFlatProp.baseProp.isId) {
                addHiddenFetcherField(
                    hiddenFlatProp,
                    hiddenLsiProps.single { child -> child.name == hiddenFlatProp.name },
                )
            }
        }
        for (foldProp in targetDtoType.foldProps) {
            val lsiFoldProp = targetLsiType.foldProp(lsiGraph, foldProp.name)
            addFoldFetcherFields(foldProp.targetType, lsiFoldProp.generatedTargetType(lsiGraph))
        }
        unindent()
        add("\n}\n")
    }

    private fun addStateProp(prop: DtoProp<ImmutableType, ImmutableProp>) {
        lsiDtoType
            .prop(lsiGraph, prop.name)
            .dtoLoadedStateStorageNameOrNull(lsiGraph, LsiLanguage.KOTLIN)
            ?.let {
            typeBuilder.addProperty(
                PropertySpec
                    .builder(it, BOOLEAN)
                    .addAnnotation(ApiIgnore::class)
                    .addAnnotation(
                        AnnotationSpec
                            .builder(ctx.jacksonTypes.jsonIgnore)
                            .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                            .build()
                    )
                    .mutable(mutable)
                    .initializer("%N", it)
                    .build()
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun addProp(prop: AbstractProp) {
        val typeName = propTypeName(prop)
        val lsiProp = lsiDtoType.prop(lsiGraph, prop.name)
        typeBuilder.addProperty(
            PropertySpec
                .builder(prop.name, typeName)
                .mutable(mutable)
                .apply {
                    if (interfacePropNames.contains(prop.name)) {
                        addModifiers(KModifier.OVERRIDE)
                    }
                    KspDtoDescriptionRenderer.render(lsiProp, lsiGraph)?.let { annotation ->
                        addAnnotation(annotation)
                    }
                    if (
                        !isBuilderRequired &&
                        lsiProp.annotations.none { annotation ->
                            annotation.typeId.value == ctx.jacksonTypes.jsonProperty.reflectionName()
                        }
                    ) {
                        addAnnotation(
                            AnnotationSpec
                                .builder(ctx.jacksonTypes.jsonProperty)
                                .useSiteTarget(AnnotationSpec.UseSiteTarget.PARAM)
                                .apply {
                                    addMember("%S", prop.name)
                                    if (!isGeneratedNullable(prop)) {
                                        addMember("required = true")
                                    }
                                }
                                .build()
                        )
                    }
                    if (prop is DtoProp<*, *>) {
                        val dtoProp = prop.asDtoProp()
                        if (dtoType.modifiers.contains(DtoModifier.INPUT) && dtoProp.inputModifier == DtoModifier.FIXED) {
                            addAnnotation(FIXED_INPUT_FIELD_CLASS_NAME)
                        }
                    }
                    addAnnotations(
                        KspDtoPropAnnotationRenderer.renderConcrete(
                            dtoProp = lsiProp,
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
                        lsiProp
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

    private fun TypeSpec.Builder.addAccessorDeclaration(prop: AbstractProp) {
        val typeName = propTypeName(prop)
        val lsiProp = lsiDtoType.prop(lsiGraph, prop.name)
        addProperty(
            PropertySpec
                .builder(prop.name, typeName)
                .addModifiers(KModifier.ABSTRACT)
                .apply {
                    if (interfacePropNames.contains(prop.name)) {
                        addModifiers(KModifier.OVERRIDE)
                    }
                    KspDtoDescriptionRenderer.render(lsiProp, lsiGraph)?.let { annotation ->
                        addAnnotation(annotation)
                    }
                    addAnnotations(
                        KspDtoPropAnnotationRenderer.renderAbstractAccessor(
                            dtoProp = lsiProp,
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
                    for (prop in dtoType.props) {
                        val lsiProp = lsiDtoType.prop(lsiGraph, prop.name)
                        addParameter(
                            ParameterSpec.builder(prop.name, propTypeName(prop))
                                .apply {
                                    val defaultValueText = (lsiProp as? DtoUserProp)?.defaultValueText
                                    if (defaultValueText != null) {
                                        defaultValue(defaultValueText)
                                    } else if (isGeneratedNullable(prop)) {
                                        defaultValue("null")
                                    } else if (propTypeName(prop) == BOOLEAN) {
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
                                            if (prop.isNullable) {
                                                defaultValue("%N !== null", prop.name)
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

    @Suppress("UNCHECKED_CAST")
    private fun addConverterConstructor() {
        typeBuilder.addFunction(
            FunSpec
                .constructorBuilder()
                .addParameter("base", baseType.className)
                .apply {
                    for (userProp in dtoType.userProps) {
                        val frozenUserProp = lsiDtoType.userProp(lsiGraph, userProp.name)
                        addParameter(
                            ParameterSpec
                                .builder(
                                    userProp.alias,
                                    KspDtoTypeRefRenderer.render(
                                        frozenUserProp.type,
                                        workspace,
                                    ),
                                )
                                .apply {
                                    frozenUserProp.kotlinDefaultValueTextOrNull()?.let(::defaultValue)
                                }
                                .build()
                        )
                    }
                }
                .callThisConstructor(dtoType.props.map { prop ->
                    CodeBlock
                        .builder()
                        .indent()
                        .add("\n")
                        .apply {
                            if (prop is FoldProp<*, *>) {
                                val foldProp = prop.asFoldProp()
                                if (foldProp.nullGuardProp != null) {
                                    add(
                                        "%N.get<%T>(base)?.let { %T(base) }",
                                        foldNullGuardAccessorFieldName(foldProp),
                                        ANY.copy(nullable = true),
                                        propTypeName(foldProp).copy(nullable = false)
                                    )
                                } else {
                                    add("%T(base)", propTypeName(foldProp).copy(nullable = false))
                                }
                            } else if (prop is DtoProp<*, *>) {
                                val dtoProp = prop.asDtoProp()
                                val lsiProp = lsiDtoType.baseProp(lsiGraph, dtoProp.name)
                                val stateInitializer = KspDtoLoadedStateRenderer.renderBaseInitializer(
                                    prop = lsiProp,
                                    graph = lsiGraph,
                                    accessorName = accessorFieldName(dtoProp.name),
                                    baseParameterName = "base",
                                )
                                val simple = isSimpleProp(dtoProp)
                                if (simple && stateInitializer == null) {
                                    add("base.%N", dtoProp.baseProp.name)
                                } else if (!dtoProp.isNullable && dtoProp.isBaseNullable) {
                                    add(
                                        "%N.get<%T>(\n",
                                        accessorFieldName(dtoProp.name),
                                        propTypeName(dtoProp)
                                    )
                                    indent()
                                    add("base,\n")
                                    add(
                                        "%S\n",
                                        "Cannot convert \"${baseType.className}\" to " +
                                                "\"${getDtoClassName()}\" because the cannot get non-null " +
                                                "value for \"${dtoProp.name}\""
                                    )
                                    unindent()
                                    add(")")
                                } else {
                                    add(
                                        "%N.get<%T>(base)",
                                        accessorFieldName(dtoProp.name),
                                        propTypeName(dtoProp)
                                    )
                                }
                                stateInitializer?.let { initializer ->
                                    add(if (simple) ",\n%L" else ",\n%L\n", initializer)
                                }
                            } else {
                                val userProp = prop as UserProp
                                add("%N", userProp.alias)
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
                .builder(if (baseType.isEntity) "toEntity" else "toImmutable")
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
                            if (baseType.isEntity) "toEntityImpl" else "toImmutableImpl"
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
                .builder(if (baseType.isEntity) "toEntities" else "toImmutables")
                .addAnnotation(generatedAnnotation(baseType.className))
                .receiver(ITERABLE.parameterizedBy(dtoClassName))
                .returns(LIST.parameterizedBy(baseType.className))
                .addStatement(
                    "return map(%T::%L)",
                    dtoClassName,
                    if (baseType.isEntity) "toEntity" else "toImmutable"
                )
                .build()
        )
    }

    private fun FileSpec.Builder.addToEntitiesEx() {
        addFunction(
            FunSpec
                .builder(if (baseType.isEntity) "toEntities" else "toImmutables")
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
                        if (baseType.isEntity) "toEntity" else "toImmutable"
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
                .builder(if (baseType.isEntity) "toEntity" else "toImmutable")
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
                        addDefaultPolymorphicInputToEntityBody(discriminatorProp, "block(this)")
                    } else {
                        beginControlFlow(
                            "return %M(%T::class).by",
                            NEW,
                            baseType.className
                        )
                        addStatement(
                            "%L(this)",
                            if (baseType.isEntity) "toEntityImpl" else "toImmutableImpl"
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
        extraStatement: String?,
    ) {
        for (concreteType in knownConcreteTypes(baseType)) {
            val value = concreteType.discriminatorValue ?: continue
            beginControlFlow(
                "if (%N == %T.get(%T::class.java).inheritanceInfo!!.discriminatorValue(%S))",
                discriminatorProp.name,
                IMMUTABLE_TYPE_CLASS_NAME,
                polymorphicRootType.className,
                value
            )
            beginControlFlow("return %M(%T::class).by", NEW, concreteType.className)
            addStatement("%L(this)", if (baseType.isEntity) "toEntityImpl" else "toImmutableImpl")
            if (extraStatement != null) {
                addStatement(extraStatement)
            }
            endControlFlow()
            endControlFlow()
        }
        addStatement(
            "throw %T(%S + %N + %S)",
            IllegalArgumentException::class,
            "Illegal discriminator value \"",
            discriminatorProp.name,
            "\" for polymorphic input DTO branch \"${getDtoClassName().canonicalName}\""
        )
    }

    private fun addToEntityImpl() {
        addApplyToDraft()
        typeBuilder.addFunction(
            FunSpec
                .builder(if (baseType.isEntity) "toEntityImpl" else "toImmutableImpl")
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
                    for (prop in dtoType.props) {
                        when (prop) {
                            is FoldProp<*, *> -> {
                                if (prop.isNullable) {
                                    addStatement("this.%N?.__applyTo(_draft)", prop.name)
                                } else {
                                    addStatement("this.%N.__applyTo(_draft)", prop.name)
                                }
                            }

                            is DtoProp<*, *> -> {
                                val dtoProp = prop.asDtoProp()
                                val baseProp = dtoProp.toTailProp().baseProp
                                if (baseProp.isKotlinFormula) {
                                    continue
                                }
                                if (dtoProp.nextProp == null && dtoProp.baseProp.isDiscriminator) {
                                    continue
                                }
                                val lsiProp = lsiDtoType.baseProp(lsiGraph, dtoProp.name)
                                val statePropName = lsiProp
                                    .dtoLoadedStateStorageNameOrNull(lsiGraph, LsiLanguage.KOTLIN)
                                val nonNullGuard = lsiProp.requiresNonNullDraftWriteGuard(lsiGraph)
                                if (statePropName != null) {
                                    beginControlFlow("if (%N)", statePropName)
                                } else if (nonNullGuard) {
                                    beginControlFlow("if (%N != null)", dtoProp.name)
                                }
                                addDraftAssignment(dtoProp, lsiProp, dtoProp.name)
                                if (statePropName != null || nonNullGuard) {
                                    endControlFlow()
                                }
                            }
                        }
                    }
                }
                .build()
        )
    }

    private fun FunSpec.Builder.addDraftAssignment(
        prop: DtoProp<ImmutableType, ImmutableProp>,
        lsiProp: DtoBaseProp,
        valueName: String,
    ) {
        val baseProp = prop.toTailProp().baseProp
        if (isSimpleProp(prop)) {
            addStatement("_draft.%N = %N", baseProp.name, valueName)
        } else {
            addCode(
                KspDtoDraftWriteRenderer.render(
                    prop = lsiProp,
                    graph = lsiGraph,
                    immutableSchema = immutableSchema,
                    workspace = workspace,
                    accessorName = accessorFieldName(prop.name),
                    draftName = "_draft",
                    valueName = valueName,
                ),
            )
        }
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
            if (branch.mergedTypeId != lsiDtoType.id) {
                throw DtoException("Frozen DTO polymorphic branch does not match generated branch")
            }
            return branch
        }

    private val polymorphicRootType: ImmutableType
        get() = baseType.inheritanceRoot ?: baseType

    private fun knownConcreteTypes(baseType: ImmutableType): List<ImmutableType> {
        val types = mutableListOf<ImmutableType>()
        if (baseType.isInstantiable) {
            types += baseType
        }
        for (type in ctx.types) {
            if (type !== baseType &&
                type.isEntity &&
                type.isInstantiable &&
                baseType.isAssignableFrom(type)
            ) {
                types += type
            }
        }
        return types.sortedBy { it.qualifiedName }
    }

    private fun ImmutableType.isAssignableFrom(type: ImmutableType): Boolean {
        var current: ImmutableType? = type
        while (current != null) {
            if (current === this) {
                return true
            }
            current = current.primarySuperType
        }
        return false
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
            FunSpec
                .builder("applyTo")
                .apply {
                    if (!isNestedSpecificationFragment) {
                        addParameter(
                            "args",
                            K_SPECIFICATION_ARGS_CLASS_NAME.parameterizedBy(baseType.className)
                        )
                        addModifiers(KModifier.OVERRIDE)
                        addStatement("val _applier = args.applier")
                    } else {
                        addParameter(
                            "_applier",
                            PREDICATE_APPLIER
                        )
                    }
                    var stack = emptyList<ImmutableProp>()
                    for (prop in dtoType.props) {
                        when (prop) {
                            is FoldProp<*, *> -> {
                                stack = addStackOperations(stack, emptyList())
                                if (isGeneratedNullable(prop)) {
                                    if (baseType.isEntity) {
                                        addStatement("this.%N?.applyTo(args)", prop.name)
                                    } else {
                                        addStatement("this.%N?.applyTo(_applier)", prop.name)
                                    }
                                } else {
                                    if (baseType.isEntity) {
                                        addStatement("this.%N.applyTo(args)", prop.name)
                                    } else {
                                        addStatement("this.%N.applyTo(_applier)", prop.name)
                                    }
                                }
                            }

                            is DtoProp<*, *> -> {
                                val dtoProp = prop.asDtoProp()
                                val newStack = mutableListOf<ImmutableProp>()
                                val tailProp = dtoProp.toTailProp()
                                var p: DtoProp<ImmutableType, ImmutableProp>? = dtoProp
                                while (p != null) {
                                    if (p !== tailProp || p.target != null) {
                                        newStack.add(p.getBaseProp())
                                    }
                                    p = p.getNextProp()
                                }
                                stack = addStackOperations(stack, newStack)
                                addPredicateOperation(dtoProp)
                            }
                        }
                    }
                    addStackOperations(stack, emptyList())
                }
                .build()
        )
    }

    private fun FunSpec.Builder.addStackOperations(
        stack: List<ImmutableProp>,
        newStack: List<ImmutableProp>,
    ): List<ImmutableProp> {
        val size = min(stack.size, newStack.size)
        var sameCount = size
        for (i in 0 until size) {
            if (stack[i] !== newStack[i]) {
                sameCount = i
                break
            }
        }
        for (i in stack.size - sameCount downTo 1) {
            addStatement("_applier.pop()")
        }
        for (prop in newStack.subList(sameCount, newStack.size)) {
            addStatement(
                "_applier.push(%T.%N.unwrap())",
                prop.declaringType.propsClassName,
                StringUtil.snake(prop.name, SnakeCase.UPPER)
            )
        }
        return newStack
    }

    private fun FunSpec.Builder.addPredicateOperation(prop: DtoProp<ImmutableType, ImmutableProp>) {
        val propName = prop.name
        val tailProp = prop.toTailProp()
        if (tailProp.target != null) {
            if (tailProp.baseProp.isAssociation(true)) {
                addStatement("this.%N?.let { it.applyTo(args.child()) }", propName)
            } else {
                addStatement("this.%N?.let { it.applyTo(args.applier) }", propName)
            }
            return
        }

        val funcName = when (tailProp.funcName) {
            null -> "eq"
            "id" -> "associatedIdEq"
            else -> tailProp.funcName
        }
        val ktFunName = when (funcName) {
            "null" -> "isNull"
            "notNull" -> "isNotNull"
            else -> funcName
        }

        addCode(
            CodeBlock.builder()
                .apply {
                    add("_applier.%N(", ktFunName)
                    if (Constants.MULTI_ARGS_FUNC_NAMES.contains(funcName)) {
                        add("arrayOf(")
                        tailProp.basePropMap.values.forEachIndexed { index, baseProp ->
                            if (index != 0) {
                                add(", ")
                            }
                            add(
                                "%T.%N.unwrap()",
                                baseProp.declaringType.propsClassName,
                                StringUtil.snake(baseProp.name, SnakeCase.UPPER)
                            )
                        }
                        add(")")
                    } else {
                        add(
                            "%T.%N.unwrap()",
                            tailProp.baseProp.declaringType.propsClassName,
                            StringUtil.snake(tailProp.baseProp.name, SnakeCase.UPPER)
                        )
                    }
                    if (isSpecificationConverterRequired(prop)) {
                        add(
                            ", %N(this.%N)",
                            StringUtil.identifier("_convert", propName),
                            propName
                        )
                    } else {
                        add(", this.%N", propName)
                    }
                    KspDtoSpecificationRenderer.renderLikeOptionArguments(
                        lsiDtoType.baseProp(lsiGraph, propName),
                        lsiGraph,
                    )?.let { arguments -> add("%L", arguments) }
                    add(")\n")
                }
                .build()
        )
    }

    private fun isSimpleProp(prop: DtoProp<ImmutableType, ImmutableProp>): Boolean {
        if (prop.getNextProp() != null) {
            return false
        }
        if (prop.baseProp.isDiscriminator) {
            return false
        }
        return if ((prop.isNullable() && (!prop.getBaseProp().isNullable || dtoType.modifiers.contains(DtoModifier.SPECIFICATION))) ||
            (lsiProp(prop).boundImmutableProp(lsiGraph, immutableSchema).converter != null &&
                    !dtoType.modifiers.contains(DtoModifier.INPUT) &&
                    !dtoType.modifiers.contains(DtoModifier.SPECIFICATION))
        ) {
            false
        } else {
            propTypeName(prop) == prop.getBaseProp().typeName()
        }
    }

    private fun hasAccessorFields(): Boolean =
        dtoType.dtoProps.any(::requiresAccessorField) ||
                dtoType.foldProps.any { it.nullGuardProp !== null }

    private fun TypeSpec.Builder.addAccessorField(prop: DtoProp<ImmutableType, ImmutableProp>) {
        if (!requiresAccessorField(prop)) {
            return
        }
        val lsiProp = lsiDtoType.baseProp(lsiGraph, prop.name)
        addAccessorField(
            prop,
            accessorFieldName(prop.name),
            lsiProp.acceptsNullInAccessor(lsiGraph),
            true,
            lsiProp,
        )
    }

    private fun requiresAccessorField(prop: DtoProp<ImmutableType, ImmutableProp>): Boolean {
        if (!isSimpleProp(prop)) {
            return true
        }
        return lsiDtoType
            .baseProp(lsiGraph, prop.name)
            .dtoLoadedStateStorageNameOrNull(lsiGraph, LsiLanguage.KOTLIN) != null
    }

    private fun TypeSpec.Builder.addFoldNullGuardAccessorField(prop: FoldProp<ImmutableType, ImmutableProp>) {
        val nullGuardProp = prop.nullGuardProp ?: return
        addAccessorField(
            nullGuardProp,
            foldNullGuardAccessorFieldName(prop),
            true,
            false,
            null,
        )
    }

    private fun TypeSpec.Builder.addAccessorField(
        prop: DtoProp<ImmutableType, ImmutableProp>,
        fieldName: String,
        acceptNull: Boolean,
        withConverters: Boolean,
        lsiProp: DtoBaseProp?,
    ) {
        val builder = PropertySpec.builder(
            fieldName,
            DTO_PROP_ACCESSOR,
            KModifier.PRIVATE
        ).initializer(
            CodeBlock
                .builder()
                .apply {
                    add("%T(", DTO_PROP_ACCESSOR)
                    indent()

                    add("\n%L", acceptNull)

                    if (prop.nextProp === null) {
                        add(
                            ",\nintArrayOf(%T.%N)",
                            baseType.draftClassName("$"),
                            prop.baseProp.slotName
                        )
                    } else {
                        add(",\nintArrayOf(")
                        indent()
                        var p: DtoProp<ImmutableType, ImmutableProp>? = prop
                        while (p !== null) {
                            if (p !== prop) {
                                add(",")
                            }
                            add(
                                "\n%T.%N",
                                p.baseProp.declaringType.draftClassName("$"),
                                p.baseProp.slotName
                            )
                            p = p.nextProp
                        }
                        unindent()
                        add("\n)")
                    }

                    val tailProp = prop.toTailProp()
                    val converterLsiProp = if (withConverters) {
                        requireNotNull(lsiProp) {
                            "Frozen DTO property is required for converter accessors"
                        }
                    } else {
                        null
                    }
                    val tailBaseProp = tailProp.baseProp
                    if (withConverters && prop.isIdOnly) {
                        if (dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
                            add(",\nnull")
                        } else {
                            add(
                                ",\n%T.%N(%T::class.java, ",
                                DTO_PROP_ACCESSOR,
                                if (tailBaseProp.isList) "idListGetter" else "idReferenceGetter",
                                tailBaseProp.targetTypeName(overrideNullable = false)
                            )
                            addConverterLoading(prop, false)
                            add(")")
                            add(
                                ",\n%T.%N(%T::class.java, ",
                                DTO_PROP_ACCESSOR,
                                if (tailBaseProp.isList) "idListSetter" else "idReferenceSetter",
                                tailBaseProp.targetTypeName(overrideNullable = false)
                            )
                            addConverterLoading(prop, false)
                            add(")")
                        }
                    } else if (withConverters && tailProp.target != null) {
                        if (dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
                            add(",\nnull")
                        } else {
                            val reusableTargetType = lsiTailProp(prop).targetTypeReference != null
                            if (reusableTargetType || tailProp.targetType!!.polymorphism !== null) {
                                add(
                                    ",\n%T.%N<%T, %L>(%T.METADATA.converter)",
                                    DTO_PROP_ACCESSOR,
                                    if (tailBaseProp.isList) "objectListGetter" else "objectReferenceGetter",
                                    tailBaseProp.targetTypeName(overrideNullable = false),
                                    propElementName(prop),
                                    propElementName(prop)
                                )
                            } else {
                                add(
                                    ",\n%T.%N<%T, %L> {",
                                    DTO_PROP_ACCESSOR,
                                    if (tailBaseProp.isList) "objectListGetter" else "objectReferenceGetter",
                                    tailBaseProp.targetTypeName(overrideNullable = false),
                                    propElementName(prop)
                                )
                                indent()
                                add("\n%L(it)", propElementName(prop))
                                unindent()
                                add("\n}")
                            }

                            add(
                                ",\n%T.%N<%T, %L> {",
                                DTO_PROP_ACCESSOR,
                                if (tailBaseProp.isList) "objectListSetter" else "objectReferenceSetter",
                                tailBaseProp.targetTypeName(overrideNullable = false),
                                propElementName(prop)
                            )
                            indent()
                            add(
                                "\nit.%N()",
                                if (reusableTargetType) {
                                    "toImmutable"
                                } else if (tailBaseProp.targetType!!.isEntity) {
                                    "toEntity"
                                } else {
                                    "toImmutable"
                                }
                            )
                            unindent()
                            add("\n}")
                        }
                    } else if (converterLsiProp?.enumType != null) {
                        if (dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
                            add(",\nnull")
                        } else {
                            add(",\n")
                            KspDtoEnumRenderer.appendEnumToScalarLambda(
                                this,
                                converterLsiProp,
                                lsiGraph,
                                immutableSchema,
                                workspace,
                            )
                        }
                        add(",\n")
                        KspDtoEnumRenderer.appendScalarToEnumLambda(
                            this,
                            converterLsiProp,
                            lsiGraph,
                            immutableSchema,
                            workspace,
                        )
                    } else if (
                        withConverters &&
                        lsiProp(prop).dtoConverterTargetTypeOrNull(lsiGraph, immutableSchema) != null
                    ) {
                        add(",\n{ ")
                        addConverterLoading(prop, true)
                        add(".output(it) }")
                        add(",\n{ ")
                        addConverterLoading(prop, true)
                        add(".input(it) }")
                    }

                    unindent()
                    add("\n)")
                }
                .build()
        )
        addProperty(builder.build())
    }

    private fun TypeSpec.Builder.addSpecificationConverter(prop: DtoProp<ImmutableType, ImmutableProp>) {
        if (!isSpecificationConverterRequired(prop)) {
            return
        }
        val baseProp = prop.toTailProp().baseProp
        val baseTypeName = when (prop.funcName) {
            "id" -> baseProp.targetType!!.idProp!!.typeName().let {
                if (baseProp.isList && !dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
                    LIST.parameterizedBy(it)
                } else {
                    it
                }
            }

            "null", "notNull" -> BOOLEAN

            "valueIn", "valueNotIn" ->
                LIST.parameterizedBy(baseProp.typeName())

            "associatedIdEq", "associatedIdNe" ->
                baseProp.targetType!!.idProp!!.typeName()

            "associatedIdIn", "associatedIdNotIn" ->
                LIST.parameterizedBy(baseProp.targetType!!.idProp!!.typeName())

            else -> baseProp.typeName()
        }.copy(nullable = prop.isNullable)
        val builder = FunSpec
            .builder(StringUtil.identifier("_convert", prop.getName()))
            .addModifiers(KModifier.PUBLIC)
            .addParameter("value", propTypeName(prop))
            .returns(baseTypeName)
            .addCode(
                CodeBlock
                    .builder()
                    .apply {
                        if (prop.isNullable) {
                            beginControlFlow("if (value === null)")
                            addStatement("return null")
                            endControlFlow()
                        }
                        val lsiEnumProp = lsiEnumPropOrNull(prop)
                        if (lsiEnumProp != null) {
                            add("return ")
                            KspDtoEnumRenderer.appendScalarToEnumConversion(
                                this,
                                lsiEnumProp,
                                lsiGraph,
                                immutableSchema,
                                workspace,
                                "value",
                            )
                        } else {
                            add(
                                "return %T.%N.unwrap().%N<%T, %T>(%L).input(value)",
                                baseProp.declaringType.propsClassName,
                                StringUtil.snake(baseProp.name, SnakeCase.UPPER),
                                if (baseProp.isAssociation(true)) "getAssociatedIdConverter" else "getConverter",
                                baseTypeName,
                                propTypeName(prop).copy(nullable = false),
                                if (baseProp.isAssociation(true)) {
                                    if (prop.isFunc("associatedIdIn", "associatedIdNotIn")) "true" else "false"
                                } else {
                                    if (prop.isFunc("valueIn", "valueNotIn")) "true" else ""
                                }
                            )
                        }
                    }
                    .build()
            )
        addFunction(builder.build())
    }

    @Suppress("UNCHECKED_CAST")
    fun propTypeName(prop: AbstractProp): TypeName =
        when (prop) {
            is FoldProp<*, *> -> propTypeName(prop.asFoldProp())
            is DtoProp<*, *> -> propTypeName(prop.asDtoProp())
            is UserProp -> KspDtoTypeRefRenderer.render(
                lsiDtoType.userProp(lsiGraph, prop.name).type,
                workspace,
            )
            else -> error("Internal bug")
        }

    @Suppress("UNCHECKED_CAST")
    private fun AbstractProp.asDtoProp(): DtoProp<ImmutableType, ImmutableProp> =
        this as DtoProp<ImmutableType, ImmutableProp>

    @Suppress("UNCHECKED_CAST")
    private fun AbstractProp.asFoldProp(): FoldProp<ImmutableType, ImmutableProp> =
        this as FoldProp<ImmutableType, ImmutableProp>

    private fun propTypeName(prop: FoldProp<ImmutableType, ImmutableProp>): TypeName {
        val polymorphicRootProp = polymorphicRootPropOrNull(prop)
        val typeName = if (polymorphicRootProp != null) {
            generatedTargetTypeName(polymorphicRootProp)
        } else {
            getDtoClassName(targetSimpleName(prop))
        }
        return typeName.copy(nullable = isGeneratedNullable(prop))
    }

    private fun isGeneratedNullable(prop: AbstractProp): Boolean =
        prop.isNullable ||
                (prop is FoldProp<*, *> && dtoType.modifiers.contains(DtoModifier.SPECIFICATION))

    private fun propTypeName(prop: DtoProp<ImmutableType, ImmutableProp>): TypeName {

        val baseProp = prop.toTailProp().baseProp
        val lsiEnumProp = lsiEnumPropOrNull(prop)
        if (lsiEnumProp != null) {
            return KspDtoEnumRenderer.renderScalarType(lsiEnumProp, workspace)
        }

        val converterTargetType = lsiProp(prop).dtoConverterTargetTypeOrNull(lsiGraph, immutableSchema)
        val converterTargetTypeName = converterTargetType?.let { type ->
            KspDtoTypeRefRenderer.render(type, workspace)
        }
        val propElementName = propElementName(prop)
        if (dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
            val funcName = prop.toTailProp().getFuncName()
            if (funcName != null) {
                when (funcName) {
                    "null", "notNull" ->
                        return BOOLEAN.copy(nullable = prop.isNullable)

                    "valueIn", "valueNotIn" ->
                        return COLLECTION.parameterizedBy(
                            converterTargetTypeName ?: propElementName.toList(baseProp.isList)
                        ).copy(nullable = prop.isNullable)

                    "id", "associatedIdEq", "associatedIdNe" ->
                        return KspDtoTypeRefRenderer.render(
                            lsiProp(prop).dtoAssociatedIdClientType(lsiGraph, immutableSchema),
                            workspace,
                        ).copy(nullable = prop.isNullable)

                    "associatedIdIn", "associatedIdNotIn" ->
                        return COLLECTION.parameterizedBy(
                            KspDtoTypeRefRenderer.render(
                                lsiProp(prop).dtoAssociatedIdClientType(lsiGraph, immutableSchema),
                                workspace,
                            )
                        )
                            .copy(nullable = prop.isNullable)
                }
            }
            if (baseProp.isAssociation(true)) {
                return propElementName.copy(nullable = prop.isNullable)
            }
        }
        if (converterTargetTypeName != null) {
            return converterTargetTypeName.copy(nullable = prop.isNullable)
        }

        return propElementName
            .toList(baseProp.isList && !(propElementName is ParameterizedTypeName && propElementName.rawType == LIST))
            .copy(nullable = prop.isNullable)
    }

    private fun propElementName(prop: DtoProp<ImmutableType, ImmutableProp>): TypeName {
        polymorphicRootPropOrNull(prop)?.let { polymorphicRootProp ->
            return generatedTargetTypeName(polymorphicRootProp)
        }
        val tailProp = prop.toTailProp()
        val lsiTailProp = lsiTailProp(prop)
        lsiTailProp.targetTypeReference?.let { targetTypeReference ->
            return KspDtoTypeRefRenderer.render(
                targetTypeReference,
                workspace,
                JimmerDtoPoetTypeNames.reusableTarget(targetTypeReference, rootDtoTypeNamesByTypeId),
            )
        }
        val targetType = tailProp.targetType
        if (targetType !== null) {
            if (tailProp.isRecursive && !targetType.isFocusedRecursion) {
                return getDtoClassName()
            }
            if (targetType.name === null) {
                val list: MutableList<String> = ArrayList()
                collectNames(list)
                if (!prop.isRecursive || targetType.isFocusedRecursion) {
                    list.add(targetSimpleName(tailProp))
                }
                return ClassName(
                    root.dtoType.packageName,
                    list[0],
                    *list.subList(1, list.size).toTypedArray()
                )
            }
            return ClassName(
                root.dtoType.packageName,
                targetType.name!!
            )
        }
        return KspDtoTypeRefRenderer.render(
            lsiProp(prop).dtoClientType(lsiGraph, immutableSchema),
            workspace,
        ).copy(nullable = false)
    }

    private fun lsiProp(prop: DtoProp<ImmutableType, ImmutableProp>) =
        lsiDtoType.prop(lsiGraph, prop.name) as site.addzero.lsi.jimmer.dto.DtoBaseProp

    private fun lsiEnumPropOrNull(
        prop: DtoProp<ImmutableType, ImmutableProp>,
    ): site.addzero.lsi.jimmer.dto.DtoBaseProp? {
        return lsiProp(prop).takeIf { lsiProp -> lsiProp.enumType != null }
    }

    private fun lsiTailProp(prop: DtoProp<ImmutableType, ImmutableProp>) =
        lsiProp(prop).tailProp(lsiGraph)

    private fun collectNames(list: MutableList<String>) {
        if (parent == null) {
            list.add(dtoType.name!!)
        } else if (innerClassName !== null) {
            parent.collectNames(list)
            list.add(innerClassName)
        }
    }

    private fun targetSimpleName(prop: DtoProp<ImmutableType, ImmutableProp>): String {
        val targetType = prop.targetType ?: throw IllegalArgumentException("prop is not association")
        if (prop.isRecursive && !targetType.isFocusedRecursion) {
            return innerClassName ?: dtoType.name ?: error("Internal bug: No target simple name")
        }
        return standardTargetSimpleName("TargetOf_${prop.name}")
    }

    private fun targetSimpleName(prop: FoldProp<ImmutableType, ImmutableProp>): String =
        standardTargetSimpleName("TargetOf_${prop.name}")

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

    private fun generatedTargetTypeName(prop: LsiDtoProp): TypeName {
        val targetType = prop.generatedTargetTypeOrNull(lsiGraph)
            ?: throw DtoException(
                "Promoted DTO root property has no generated target: \"${prop.name}\""
            )
        val typeName = JimmerDtoPoetTypeNames.requireRegistered(
            targetType,
            generatedDtoTypeNamesByTypeId,
        )
        return KspDtoTypeRefRenderer.render(typeName, workspace)
    }

    private fun accessorFieldName(propName: String): String =
        StringUtil.snake("${propName}Accessor", SnakeCase.UPPER)

    private fun foldNullGuardAccessorFieldName(prop: FoldProp<ImmutableType, ImmutableProp>): String =
        StringUtil.snake("${prop.name}NullGuardAccessor", SnakeCase.UPPER)

    private fun standardTargetSimpleName(targetSimpleName: String): String {
        var conflict = false
        var generator: DtoGenerator? = this
        while (generator != null) {
            if ((generator.innerClassName ?: generator.dtoType.name) == targetSimpleName) {
                conflict = true
                break
            }
            generator = generator.parent
        }
        if (!conflict) {
            return targetSimpleName
        }
        for (i in 2..99) {
            conflict = false
            val newTargetSimpleName = targetSimpleName + '_' + i
            generator = this
            while (generator != null) {
                if ((generator.innerClassName ?: generator.dtoType.name) == newTargetSimpleName) {
                    conflict = true
                    break
                }
                generator = generator.parent
            }
            if (!conflict) {
                return newTargetSimpleName
            }
        }
        throw AssertionError("Dto is too deep")
    }

    private fun CodeBlock.Builder.addConverterLoading(
        prop: DtoProp<ImmutableType, ImmutableProp>,
        forList: Boolean,
    ) {
        val baseProp: ImmutableProp = prop.toTailProp().getBaseProp()
        add(
            "%T.%N.unwrap().%L",
            baseProp.declaringType.propsClassName,
            StringUtil.snake(baseProp.name, SnakeCase.UPPER),
            if (prop.toTailProp().getBaseProp()
                    .isAssociation(true)
            ) {
                "getAssociatedIdConverter<Any, Any>($forList)"
            } else {
                "getConverter<Any, Any>()"
            }
        )
    }

    private fun isSpecificationConverterRequired(prop: DtoProp<ImmutableType, ImmutableProp>): Boolean {
        return if (!dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
            false
        } else {
            lsiEnumPropOrNull(prop) != null ||
                lsiProp(prop).dtoConverterTargetTypeOrNull(lsiGraph, immutableSchema) != null
        }
    }

    private fun TypeSpec.Builder.addCopy() {
        addFunction(
            FunSpec
                .builder("copy")
                .returns(getDtoClassName())
                .apply {
                    val args = mutableListOf<String>()
                    for (prop in dtoType.props) {
                        addParameter(
                            ParameterSpec.builder(prop.name, propTypeName(prop))
                                .defaultValue("this.%N", prop.name)
                                .build()
                        )
                        args += prop.name
                        lsiDtoType
                            .prop(lsiGraph, prop.name)
                            .dtoLoadedStateStorageNameOrNull(lsiGraph, LsiLanguage.KOTLIN)
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

    private val isNestedSpecificationFragment: Boolean
        get() = lsiDtoType.isNestedSpecificationFragment(immutableSchema)

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

        private fun TypeName.toList(isList: Boolean) =
            if (isList) {
                LIST.parameterizedBy(this.copy(nullable = false))
            } else {
                this
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
