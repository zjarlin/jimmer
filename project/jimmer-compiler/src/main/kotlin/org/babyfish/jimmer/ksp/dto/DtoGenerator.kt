package org.babyfish.jimmer.ksp.dto

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.babyfish.jimmer.client.ApiIgnore
import org.babyfish.jimmer.compiler.dto.JimmerDtoJacksonVersion
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.compiler.render.ksp.KspDtoInputBuilderRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoPropAnnotationRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoSerializerRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoTypeAnnotationRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoTypeRefRenderer
import org.babyfish.jimmer.dto.compiler.*
import org.babyfish.jimmer.dto.compiler.PropConfig.PathNode
import org.babyfish.jimmer.dto.compiler.PropConfig.Predicate
import org.babyfish.jimmer.dto.compiler.PropConfig.Predicate.*
import org.babyfish.jimmer.impl.util.StringUtil
import org.babyfish.jimmer.impl.util.StringUtil.SnakeCase
import org.babyfish.jimmer.ksp.Context
import org.babyfish.jimmer.ksp.immutable.generator.*
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableProp
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableType
import org.babyfish.jimmer.ksp.util.ConverterMetadata
import org.babyfish.jimmer.ksp.util.GenericParser
import org.babyfish.jimmer.ksp.util.generatedAnnotation
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoConfigContractKind
import site.addzero.lsi.jimmer.dto.DtoConfigContractResolution
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution
import site.addzero.lsi.jimmer.dto.DtoType as LsiDtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoUserProp
import site.addzero.lsi.jimmer.dto.baseProp
import site.addzero.lsi.jimmer.dto.configImplementationTypeOrNull
import site.addzero.lsi.jimmer.dto.contractFor
import site.addzero.lsi.jimmer.dto.foldProp
import site.addzero.lsi.jimmer.dto.dtoLoadedStateStorageNameOrNull
import site.addzero.lsi.jimmer.dto.generatedTargetType
import site.addzero.lsi.jimmer.dto.hasTypeAnnotation
import site.addzero.lsi.jimmer.dto.isNestedSpecificationFragment
import site.addzero.lsi.jimmer.dto.kotlinDefaultValueTextOrNull
import site.addzero.lsi.jimmer.dto.mergedType
import site.addzero.lsi.jimmer.dto.prop
import site.addzero.lsi.jimmer.dto.requiresDynamicInputSerialization
import site.addzero.lsi.jimmer.dto.requiresHibernateValidatorEnhancement
import site.addzero.lsi.jimmer.dto.requiresInputBuilder
import site.addzero.lsi.jimmer.dto.requiredPropNames
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
    private val polymorphicBranch: Boolean = false,
    private val polymorphicBranchKind: DtoPolymorphicBranch.Kind? = null,
    private val polymorphicBranchOrder: Int = -1,
) {
    private val root: DtoGenerator = parent?.root ?: this

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
        polymorphicBranch: Boolean = false,
        polymorphicBranchKind: DtoPolymorphicBranch.Kind? = null,
        polymorphicBranchOrder: Int = -1,
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
        polymorphicBranch = polymorphicBranch,
        polymorphicBranchKind = polymorphicBranchKind,
        polymorphicBranchOrder = polymorphicBranchOrder,
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
                        addAnnotation(
                            AnnotationSpec
                                .builder(GENERATED_POLYMORPHIC_DTO_BRANCH_CLASS_NAME)
                                .addMember("value = %T::class", polymorphicSuperInterfaceName!!)
                                .addMember("order = %L", polymorphicBranchOrder)
                                .build()
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
        if (!dtoType.baseType.isEntity) {
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
                        addType(buildPolymorphicType())
                        addExtensions(includeBlockConverter = false)
                    }.build()
                val writer = OutputStreamWriter(it, Charsets.UTF_8)
                fileSpec.writeTo(writer)
                writer.flush()
            }
        } else if (innerClassName !== null && parent !== null) {
            parent.typeBuilder.addType(buildPolymorphicType())
        }
    }

    private fun buildPolymorphicType(): TypeSpec {
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
        builder.addJacksonPolymorphicInputRootAnnotationsIfNecessary()
        _typeBuilder = builder
        try {
            addDoc()
            addPolymorphicMembers()
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
        packages += dtoType.baseType.className.packageName
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

    private fun TypeSpec.Builder.addJacksonPolymorphicInputRootAnnotationsIfNecessary() {
        val polymorphism = dtoType.polymorphism ?: return
        if (!isPolymorphicInputRoot) {
            return
        }
        if (!hasTypeAnnotation(lsiDtoType, ctx.jacksonTypes.jsonTypeInfo)) {
            addJacksonTypeInfo(polymorphism)
        }
        if (!hasTypeAnnotation(lsiDtoType, ctx.jacksonTypes.jsonSubTypes)) {
            addJacksonSubTypes(polymorphism)
        }
    }

    private fun TypeSpec.Builder.addJacksonTypeInfo(
        polymorphism: DtoPolymorphism<ImmutableType, ImmutableProp>
    ) {
        val discriminatorProp = selectedPolymorphicInputDiscriminatorProp(dtoType)
        val property = discriminatorProp?.name ?: rootDiscriminatorPropName ?: return
        addAnnotation(
            AnnotationSpec
                .builder(ctx.jacksonTypes.jsonTypeInfo)
                .addMember("use = %T.Id.NAME", ctx.jacksonTypes.jsonTypeInfo)
                .addMember(
                    "include = %T.As.%L",
                    ctx.jacksonTypes.jsonTypeInfo,
                    if (discriminatorProp !== null) "EXISTING_PROPERTY" else "PROPERTY"
                )
                .addMember("property = %S", property)
                .apply {
                    if (discriminatorProp !== null) {
                        addMember("visible = true")
                    }
                    polymorphism.defaultBranch?.let {
                        addMember("defaultImpl = %T::class", getDtoClassName(it.className))
                    }
                }
                .build()
        )
    }

    private fun TypeSpec.Builder.addJacksonSubTypes(
        polymorphism: DtoPolymorphism<ImmutableType, ImmutableProp>
    ) {
        if (polymorphism.typeBranches.isEmpty()) {
            return
        }
        val typeAnnotationName = ctx.jacksonTypes.jsonSubTypes.nestedClass("Type")
        val block = CodeBlock.builder()
        for ((index, branch) in polymorphism.typeBranches.withIndex()) {
            if (index != 0) {
                block.add(",\n")
            }
            val typeAnnotation = AnnotationSpec
                .builder(typeAnnotationName)
                .addMember("value = %T::class", getDtoClassName(branch.className))
                .build()
            block.add("%L", typeAnnotation)
        }
        addAnnotation(
            AnnotationSpec
                .builder(ctx.jacksonTypes.jsonSubTypes)
                .addMember("value = [%L]", block.build())
                .build()
        )
    }

    private fun TypeSpec.Builder.addJacksonPolymorphicTypeNameIfNecessary() {
        if (!polymorphicBranch ||
            !dtoType.modifiers.contains(DtoModifier.INPUT) ||
            !isTypedPolymorphicInputBranch ||
            hasTypeAnnotation(root.lsiDtoType, ctx.jacksonTypes.jsonSubTypes) ||
            hasTypeAnnotation(lsiDtoType, ctx.jacksonTypes.jsonTypeName)
        ) {
            return
        }
        dtoType.baseType.discriminatorValue?.let {
            addAnnotation(
                AnnotationSpec
                    .builder(ctx.jacksonTypes.jsonTypeName)
                    .addMember("%S", it)
                    .build()
            )
        }
    }

    private val isPolymorphicInputRoot: Boolean
        get() = dtoType.modifiers.contains(DtoModifier.INPUT) &&
                dtoType.polymorphism !== null &&
                dtoType.baseType.isEntity

    private fun hasTypeAnnotation(
        dtoType: LsiDtoType,
        annotationType: ClassName
    ): Boolean {
        return dtoType.hasTypeAnnotation(
            annotationContract = annotationContract,
            annotationTypeId = LsiSymbolId.type(annotationType.reflectionName()),
        )
    }

    private fun selectedPolymorphicInputDiscriminatorProp(
        dtoType: DtoType<ImmutableType, ImmutableProp>
    ): DtoProp<ImmutableType, ImmutableProp>? {
        if (!dtoType.modifiers.contains(DtoModifier.INPUT) ||
            !dtoType.baseType.isEntity ||
            dtoType.baseType.inheritanceRoot === null
        ) {
            return null
        }
        var result: DtoProp<ImmutableType, ImmutableProp>? = null
        for (prop in dtoType.props) {
            if (prop is DtoProp<*, *>) {
                val dtoProp = prop.asDtoProp()
                if (dtoProp.nextProp === null && dtoProp.baseProp.isDiscriminator) {
                    val old = result
                    if (old !== null && old.name != dtoProp.name) {
                        throw DtoException(
                            "Discriminator property cannot be selected by polymorphic input DTO " +
                                    "\"${dtoType.name}\" more than once"
                        )
                    }
                    result = dtoProp
                }
            }
        }
        return result
    }

    private val rootDiscriminatorPropName: String?
        get() = polymorphicRootType.properties.values.firstOrNull { it.isDiscriminator }?.name

    private fun addDoc() {
        typeDocumentation()?.let {
            typeBuilder.addAnnotation(
                AnnotationSpec
                    .builder(DESCRIPTION_CLASS_NAME)
                    .addMember("value = %S", it)
                    .build()
            )
        }
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
        } else if (!isNestedSpecificationFragment && dtoType.baseType.isEntity) {
            typeBuilder.addSuperinterface(
                when {
                    isSpecification ->
                        K_SPECIFICATION_CLASS_NAME

                    dtoType.modifiers.contains(DtoModifier.INPUT) ->
                        INPUT_CLASS_NAME

                    else ->
                        VIEW_CLASS_NAME
                }.parameterizedBy(
                    dtoType.baseType.className
                )
            )
        }
        if (!isNestedSpecificationFragment && dtoType.baseType.isEmbeddable) {
            typeBuilder.addSuperinterface(
                EMBEDDED_DTO_CLASS_NAME.parameterizedBy(
                    dtoType.baseType.className
                )
            )
        }
        for (typeRef in lsiDtoType.superInterfaces) {
            typeBuilder.addSuperinterface(KspDtoTypeRefRenderer.render(typeRef, workspace))
        }
        if (isHibernateValidatorEnhancementRequired) {
            typeBuilder.addSuperinterface(HIBERNATE_VALIDATOR_ENHANCED_BEAN)
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
            typeBuilder.addHibernateValidatorEnhancement(false)
            typeBuilder.addHibernateValidatorEnhancement(true)
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
            if (polymorphicRootFoldPropOrNull(foldProp) != null) {
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

    private fun addPolymorphicMembers() {
        typeBuilder.addSuperinterface(
            (if (dtoType.modifiers.contains(DtoModifier.INPUT)) {
                INPUT_CLASS_NAME
            } else {
                VIEW_CLASS_NAME
            }).parameterizedBy(dtoType.baseType.className)
        )
        for (typeRef in lsiDtoType.superInterfaces) {
            typeBuilder.addSuperinterface(KspDtoTypeRefRenderer.render(typeRef, workspace))
        }
        if (isHibernateValidatorEnhancementRequired) {
            typeBuilder.addSuperinterface(HIBERNATE_VALIDATOR_ENHANCED_BEAN)
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
                    addPolymorphicMetadata(polymorphism)
                }
                .build()
        )
        var branchOrder = 0
        polymorphism.defaultBranch?.let { branch ->
            generatePolymorphicBranch(branch, getDtoClassName(), branchOrder++)
        }
        for (branch in polymorphism.typeBranches) {
            generatePolymorphicBranch(branch, getDtoClassName(), branchOrder++)
        }
    }

    private fun generatePolymorphicBranch(
        branch: DtoPolymorphicBranch<ImmutableType, ImmutableProp>,
        superInterfaceName: TypeName,
        branchOrder: Int,
    ) {
        DtoGenerator(
            ctx = ctx,
            mutable = mutable,
            dtoType = dtoType.mergedWith(branch.dtoType),
            lsiDtoType = lsiMergedPolymorphicType(branch),
            parent = this,
            innerClassName = branch.className,
            polymorphicSuperInterfaceName = superInterfaceName,
            polymorphicBranch = true,
            polymorphicBranchKind = branch.kind,
            polymorphicBranchOrder = branchOrder,
        ).generate(emptyList())
    }

    private fun lsiMergedPolymorphicType(
        branch: DtoPolymorphicBranch<ImmutableType, ImmutableProp>,
    ): LsiDtoType {
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
        return matches.single().mergedType(lsiGraph)
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
                        dtoType.baseType.className,
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
                                dtoType.baseType.className, getDtoClassName()
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

    private fun TypeSpec.Builder.addPolymorphicMetadata(
        polymorphism: DtoPolymorphism<ImmutableType, ImmutableProp>
    ) {
        addProperty(
            PropertySpec
                .builder(
                    "METADATA",
                    DTO_METADATA_CLASS_NAME.parameterizedBy(
                        dtoType.baseType.className,
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
                                dtoType.baseType.className,
                                getDtoClassName()
                            )
                            indent()
                            add("%T::class.java,\n", getDtoClassName())
                            metadataFetcherExpr()
                            add(",\n")
                            polymorphicConverterExpr(polymorphism)
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
    ) {
        add(
            "%T(%T::class).by {\n",
            NEW_FETCHER_FUN_CLASS_NAME,
            sourceDtoType.baseType.className
        )
        indent()
        addFetcherFields(sourceDtoType)
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
    ) {
        for (prop in sourceDtoType.dtoProps) {
            if (prop.nextProp === null) {
                addFetcherField(prop)
            }
        }
        for (hiddenFlatProp in sourceDtoType.hiddenFlatProps) {
            if (!hiddenFlatProp.baseProp.isId) {
                addHiddenFetcherField(hiddenFlatProp)
            }
        }
        for (foldProp in sourceDtoType.foldProps) {
            addFoldFetcherFields(foldProp.targetType)
        }
    }

    private fun CodeBlock.Builder.addPolymorphicTypeFetcherBranch(
        branch: DtoPolymorphicBranch<ImmutableType, ImmutableProp>,
    ) {
        val targetType = branch.targetType ?: error("Internal bug: default branch cannot be rendered as type branch")
        if (targetType == dtoType.baseType) {
            addFetcherFields(branch.dtoType)
            return
        }
        add("forType(%T::class) {\n", targetType.className)
        indent()
        addFetcherFields(branch.dtoType)
        unindent()
        add("}\n")
    }

    private fun CodeBlock.Builder.polymorphicConverterExpr(
        polymorphism: DtoPolymorphism<ImmutableType, ImmutableProp>,
    ) {
        add("{ base ->\n")
        indent()
        addStatement("val actualType = (base as %T).__type().javaClass", IMMUTABLE_SPI_CLASS_NAME)
        beginControlFlow("when (actualType)")
        for (branch in polymorphism.typeBranches) {
            val targetType =
                branch.targetType ?: error("Internal bug: default branch cannot be rendered as type branch")
            addStatement(
                "%T::class.java -> %T(base as %T)",
                targetType.className,
                getDtoClassName(branch.className),
                targetType.className
            )
        }
        val defaultBranch = polymorphism.defaultBranch
        if (defaultBranch !== null) {
            addStatement("else -> %T(base)", getDtoClassName(defaultBranch.className))
        } else {
            addStatement(
                "else -> throw %T(%S + actualType.name + %S)",
                IllegalArgumentException::class,
                "Cannot convert entity object to polymorphic DTO \"" +
                        getDtoClassName().canonicalName +
                        "\" because there is no branch for actual entity type \"",
                "\""
            )
        }
        endControlFlow()
        unindent()
        add("}")
    }

    private fun CodeBlock.Builder.addFoldFetcherFields(dtoType: DtoType<ImmutableType, ImmutableProp>) {
        for (prop in dtoType.dtoProps) {
            if (prop.nextProp === null) {
                addFetcherField(prop)
            }
        }
        for (hiddenFlatProp in dtoType.hiddenFlatProps) {
            if (!hiddenFlatProp.baseProp.isId) {
                addHiddenFetcherField(hiddenFlatProp)
            }
        }
        for (foldProp in dtoType.foldProps) {
            addFoldFetcherFields(foldProp.targetType)
        }
    }

    private fun CodeBlock.Builder.addFetcherField(prop: DtoProp<ImmutableType, ImmutableProp>) {
        if (!prop.baseProp.isId) {
            if (prop.target !== null) {
                if (prop.isRecursive) {
                    add("`%L*`", prop.baseProp.name)
                    if (prop.config == null) {
                        add("()")
                    }
                } else {
                    add(
                        "%L(%T.METADATA.fetcher)",
                        prop.baseProp.name,
                        propElementName(prop)
                    )
                }
            } else {
                add("%L", prop.baseProp.name)
                if (prop.config == null) {
                    add("()")
                }
            }
            addConfigLambda(prop)
            add("\n")
        }
    }

    private fun CodeBlock.Builder.addConfigLambda(
        prop: DtoProp<ImmutableType, ImmutableProp>,
    ) {
        val cfg = prop.getConfig() ?: return
        val lsiProp = lsiDtoType.baseProp(lsiGraph, prop.name)
        val filterType = lsiProp.configImplementationTypeOrNull(
            graph = lsiGraph,
            resolution = configContractResolution,
            kind = DtoConfigContractKind.FILTER,
        )
        val recursionType = lsiProp.configImplementationTypeOrNull(
            graph = lsiGraph,
            resolution = configContractResolution,
            kind = DtoConfigContractKind.RECURSION,
        )
        add(" {")
        indent()
        when {
            cfg.predicate != null || cfg.orderItems.isNotEmpty() -> {
                add("\nfilter {")
                indent()
                cfg.predicate?.let {
                    val realPredicates = if (it is And) {
                        it.predicates
                    } else {
                        listOf(it)
                    }
                    for (realPredicate in realPredicates) {
                        add("\nwhere(\n")
                        indent()
                        addPredicate(realPredicate)
                        unindent()
                        add("\n)")
                    }
                }
                cfg.orderItems.takeIf { it.isNotEmpty() }?.let {
                    add("\norderBy(")
                    indent()
                    for (i in it.indices) {
                        if (i != 0) {
                            add(", ")
                        }
                        add("\n")
                        addPropPath(it[i].path)
                        if (it[i].isDesc) {
                            add(".%M()", MemberName(EXPRESSION_PACKAGE, "desc"))
                        } else {
                            add(".%M()", MemberName(EXPRESSION_PACKAGE, "asc"))
                        }
                    }
                    unindent()
                    add("\n)")
                }
                unindent()
                add("\n}")
            }

            filterType != null -> {
                val filterTypeName = KspDtoTypeRefRenderer.render(filterType, workspace)
                add("\nfilter(%L())", filterTypeName.toString())
            }
        }
        if (recursionType != null) {
            val recursionTypeName = KspDtoTypeRefRenderer.render(recursionType, workspace)
            add("\nrecursive(%L())", recursionTypeName.toString())
        }
        if (cfg.fetchType !== "AUTO") {
            add("\nfetchType(%T.%L)", REFERENCE_FETCH_TYPE_CLASS_NAME, cfg.fetchType)
        }
        if (cfg.limit != Int.MAX_VALUE) {
            if (cfg.offset != 0) {
                add("\nlimit(%L, %L)", cfg.limit, cfg.offset)
            } else {
                add("\nlimit(%L)", cfg.limit)
            }
        }
        if (cfg.batch != 0) {
            add("\nbatch(%L)", cfg.batch)
        }
        if (cfg.depth != Int.MAX_VALUE) {
            add("\ndepth(%L)", cfg.depth)
        }
        unindent()
        add("\n}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun CodeBlock.Builder.addPredicate(predicate: Predicate) {
        when (predicate) {
            is And -> {
                add("%M(\n", MemberName(EXPRESSION_PACKAGE, "and"))
                indent()
                for (i in predicate.predicates.indices) {
                    if (i != 0) {
                        add(",\n")
                    }
                    addPredicate(predicate.predicates[i])
                }
                unindent()
                add("\n)")
            }

            is Or -> {
                add("%M(\n", MemberName(EXPRESSION_PACKAGE, "or"))
                indent()
                for (i in predicate.predicates.indices) {
                    if (i != 0) {
                        add(",\n")
                    }
                    addPredicate(predicate.predicates[i])
                }
                unindent()
                add("\n)")
            }

            is Cmp<*> -> {
                addPropPath(predicate.path as List<PathNode<ImmutableProp>>)
                val ktOp = MemberName(
                    EXPRESSION_PACKAGE,
                    when (predicate.operator) {
                        "=" -> "eq"
                        "<>" -> "ne"
                        "<" -> "lt"
                        "<=" -> "le"
                        ">" -> "gt"
                        ">=" -> "ge"
                        else -> predicate.operator
                    }
                )
                if (predicate.value is String) {
                    add(" %M %S", ktOp, predicate.value)
                } else {
                    val prop = predicate.path[predicate.path.size - 1].prop as ImmutableProp
                    when (prop.typeName(overrideNullable = false)) {
                        LONG -> add(" %M %LL", ktOp, predicate.value)
                        FLOAT -> add(" %M %LF", ktOp, predicate.value)
                        DOUBLE -> add(" %M %LD", ktOp, predicate.value)
                        BIG_INTEGER_CLASS_NAME -> add(
                            " %M %T(%S)",
                            ktOp,
                            BIG_INTEGER_CLASS_NAME,
                            predicate.value
                        )

                        BIG_DECIMAL_CLASS_NAME -> add(
                            " %M %T(%S)",
                            ktOp,
                            BIG_DECIMAL_CLASS_NAME,
                            predicate.value
                        )

                        else -> add(" %M %L", ktOp, predicate.value)
                    }
                }
            }

            is Nullity<*> -> {
                addPropPath(predicate.path as List<PathNode<ImmutableProp>>)
                if (predicate.isNegative) {
                    add(".%M()", MemberName(EXPRESSION_PACKAGE, "isNotNull"))
                } else {
                    add(".%M()", MemberName(EXPRESSION_PACKAGE, "isNull"))
                }
            }

            else -> throw DtoException("Illegal predicate type: ${predicate::class.qualifiedName}")
        }
    }

    private fun CodeBlock.Builder.addPropPath(pathNodes: List<PathNode<ImmutableProp>>) {
        add("table")
        for (pathNode in pathNodes) {
            val prop = pathNode.prop
            val packageName = prop.declaringType.packageName
            val name = if (pathNode.isAssociatedId) {
                "${prop.name}Id"
            } else {
                prop.name
            }
            add(".%M", MemberName(packageName, name))
        }
    }

    private fun CodeBlock.Builder.addHiddenFetcherField(prop: DtoProp<ImmutableType, ImmutableProp>) {
        if ("flat" != prop.getFuncName()) {
            addFetcherField(prop)
            return
        }
        val targetDtoType = prop.getTargetType()!!
        add("%L {\n", prop.baseProp.name)
        indent()
        for (childProp in targetDtoType.dtoProps) {
            addHiddenFetcherField(childProp)
        }
        for (hiddenFlatProp in targetDtoType.hiddenFlatProps) {
            if (!hiddenFlatProp.baseProp.isId) {
                addHiddenFetcherField(hiddenFlatProp)
            }
        }
        for (foldProp in targetDtoType.foldProps) {
            addFoldFetcherFields(foldProp.targetType)
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
                    .initializer(it)
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
                    val doc = propDocumentation(prop)
                    doc?.let {
                        addAnnotation(
                            AnnotationSpec
                                .builder(DESCRIPTION_CLASS_NAME)
                                .addMember("value = %S", it)
                                .build()
                        )
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
                    initializer(prop.name)
                    if (mutable) {
                        lsiProp
                            .dtoLoadedStateStorageNameOrNull(lsiGraph, LsiLanguage.KOTLIN)
                            ?.let { stateProp ->
                            val name = prop.name.takeIf { it != "field" } ?: "value"
                            setter(
                                FunSpec
                                    .setterBuilder()
                                    .addParameter(name, typeName)
                                    .addStatement("field = %L", name)
                                    .addStatement("%L = true", stateProp)
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
                    val doc = propDocumentation(prop)
                    doc?.let {
                        addAnnotation(
                            AnnotationSpec
                                .builder(DESCRIPTION_CLASS_NAME)
                                .addMember("value = %S", it)
                                .build()
                        )
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
                                                defaultValue("%L !== null", prop.name)
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
                .addParameter("base", dtoType.baseType.className)
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
                                        "%L.get<%T>(base)?.let { %T(base) }",
                                        foldNullGuardAccessorFieldName(foldProp),
                                        ANY.copy(nullable = true),
                                        propTypeName(foldProp).copy(nullable = false)
                                    )
                                } else {
                                    add("%T(base)", propTypeName(foldProp).copy(nullable = false))
                                }
                            } else if (prop is DtoProp<*, *>) {
                                val dtoProp = prop.asDtoProp()
                                if (isSimpleProp(dtoProp)) {
                                    add("base.%L", dtoProp.baseProp.name)
                                } else if (!dtoProp.isNullable && dtoProp.isBaseNullable) {
                                    add(
                                        "%L.get<%T>(\n",
                                        accessorFieldName(dtoProp.name),
                                        propTypeName(dtoProp)
                                    )
                                    indent()
                                    add("base,\n")
                                    add(
                                        "%S\n",
                                        "Cannot convert \"${dtoType.baseType.className}\" to " +
                                                "\"${getDtoClassName()}\" because the cannot get non-null " +
                                                "value for \"${dtoProp.name}\""
                                    )
                                    unindent()
                                    add(")")
                                } else {
                                    add(
                                        "%L.get<%T>(base)",
                                        accessorFieldName(dtoProp.name),
                                        propTypeName(dtoProp)
                                    )
                                }
                                lsiDtoType
                                    .prop(lsiGraph, dtoProp.name)
                                    .dtoLoadedStateStorageNameOrNull(lsiGraph, LsiLanguage.KOTLIN)
                                    ?.let {
                                    if (isSimpleProp(dtoProp)) {
                                        add(
                                            ",\n%T.%L.isLoaded(base)",
                                            dtoType.baseType.propsClassName,
                                            StringUtil.snake(dtoProp.baseProp.name, SnakeCase.UPPER)
                                        )
                                    } else {
                                        add(
                                            ",\n%L.isLoaded(base)\n",
                                            accessorFieldName(dtoProp.name)
                                        )
                                    }
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
                .builder(if (dtoType.baseType.isEntity) "toEntity" else "toImmutable")
                .addModifiers(KModifier.OVERRIDE)
                .returns(dtoType.baseType.className)
                .apply {
                    if (discriminatorProp !== null && isDefaultPolymorphicInputBranch) {
                        addDefaultPolymorphicInputToEntityBody(discriminatorProp, null)
                    } else {
                        addStatement(
                            "return %M(%T::class).by(null, false, this@%L::%L)",
                            NEW,
                            dtoType.baseType.className,
                            innerClassName ?: dtoType.name!!,
                            if (dtoType.baseType.isEntity) "toEntityImpl" else "toImmutableImpl"
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
                .builder(if (dtoType.baseType.isEntity) "toEntities" else "toImmutables")
                .addAnnotation(generatedAnnotation(dtoType.baseType.className))
                .receiver(ITERABLE.parameterizedBy(dtoClassName))
                .returns(LIST.parameterizedBy(dtoType.baseType.className))
                .addStatement(
                    "return map(%T::%L)",
                    dtoClassName,
                    if (dtoType.baseType.isEntity) "toEntity" else "toImmutable"
                )
                .build()
        )
    }

    private fun FileSpec.Builder.addToEntitiesEx() {
        addFunction(
            FunSpec
                .builder(if (dtoType.baseType.isEntity) "toEntities" else "toImmutables")
                .addAnnotation(generatedAnnotation(dtoType.baseType.className))
                .receiver(ITERABLE.parameterizedBy(getDtoClassName()))
                .returns(LIST.parameterizedBy(dtoType.baseType.className))
                .addParameter(
                    "block",
                    LambdaTypeName.get(
                        dtoType.baseType.draftClassName,
                        emptyList(),
                        UNIT
                    ),
                )
                .apply {
                    beginControlFlow("return map")
                    addStatement(
                        "it.%L(block)",
                        if (dtoType.baseType.isEntity) "toEntity" else "toImmutable"
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
                .builder(if (dtoType.baseType.isEntity) "toEntity" else "toImmutable")
                .addParameter(
                    "block",
                    LambdaTypeName.get(
                        dtoType.baseType.draftClassName,
                        emptyList(),
                        UNIT
                    ),
                )
                .returns(dtoType.baseType.className)
                .apply {
                    if (discriminatorProp !== null && isDefaultPolymorphicInputBranch) {
                        addDefaultPolymorphicInputToEntityBody(discriminatorProp, "block(this)")
                    } else {
                        beginControlFlow(
                            "return %M(%T::class).by",
                            NEW,
                            dtoType.baseType.className
                        )
                        addStatement(
                            "%L(this)",
                            if (dtoType.baseType.isEntity) "toEntityImpl" else "toImmutableImpl"
                        )
                        addStatement("block(this)")
                        endControlFlow()
                    }
                }
                .build()
        )
    }

    private fun FunSpec.Builder.addDefaultPolymorphicInputToEntityBody(
        discriminatorProp: DtoProp<ImmutableType, ImmutableProp>,
        extraStatement: String?,
    ) {
        for (concreteType in knownConcreteTypes(dtoType.baseType)) {
            val value = concreteType.discriminatorValue ?: continue
            beginControlFlow(
                "if (%L == %T.get(%T::class.java).inheritanceInfo!!.discriminatorValue(%S))",
                discriminatorProp.name,
                IMMUTABLE_TYPE_CLASS_NAME,
                polymorphicRootType.className,
                value
            )
            beginControlFlow("return %M(%T::class).by", NEW, concreteType.className)
            addStatement("%L(this)", if (dtoType.baseType.isEntity) "toEntityImpl" else "toImmutableImpl")
            if (extraStatement != null) {
                addStatement(extraStatement)
            }
            endControlFlow()
            endControlFlow()
        }
        addStatement(
            "throw %T(%S + %L + %S)",
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
                .builder(if (dtoType.baseType.isEntity) "toEntityImpl" else "toImmutableImpl")
                .addKdoc(DOC_EXPLICIT_FUN)
                .addModifiers(KModifier.PRIVATE)
                .addParameter("_draft", dtoType.baseType.draftClassName)
                .addStatement("this.__applyTo(_draft)")
                .build()
        )
    }

    private fun addApplyToDraft() {
        typeBuilder.addFunction(
            FunSpec
                .builder("__applyTo")
                .addModifiers(KModifier.INTERNAL)
                .addParameter("_draft", dtoType.baseType.draftClassName)
                .apply {
                    polymorphicInputDiscriminatorProp()
                        ?.takeIf { isTypedPolymorphicInputBranch }
                        ?.let { addTypedPolymorphicInputDiscriminatorValidation(it) }
                    for (prop in dtoType.props) {
                        when (prop) {
                            is FoldProp<*, *> -> {
                                if (prop.isNullable) {
                                    addStatement("this.%L?.__applyTo(_draft)", prop.name)
                                } else {
                                    addStatement("this.%L.__applyTo(_draft)", prop.name)
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
                                val statePropName = lsiDtoType
                                    .prop(lsiGraph, dtoProp.name)
                                    .dtoLoadedStateStorageNameOrNull(lsiGraph, LsiLanguage.KOTLIN)
                                if (statePropName !== null) {
                                    beginControlFlow("if (%L)", statePropName)
                                    addDraftAssignment(dtoProp, dtoProp.name)
                                    endControlFlow()
                                } else {
                                    addDraftAssignment(dtoProp, dtoProp.name)
                                }
                            }
                        }
                    }
                }
                .build()
        )
    }

    private fun FunSpec.Builder.addTypedPolymorphicInputDiscriminatorValidation(
        discriminatorProp: DtoProp<ImmutableType, ImmutableProp>
    ) {
        val value = dtoType.baseType.discriminatorValue ?: return
        beginControlFlow(
            "if (%L != %T.get(%T::class.java).inheritanceInfo!!.discriminatorValue(%S))",
            discriminatorProp.name,
            IMMUTABLE_TYPE_CLASS_NAME,
            polymorphicRootType.className,
            value
        )
        addStatement(
            "throw %T(%S + %L + %S)",
            IllegalArgumentException::class,
            "Discriminator value \"",
            discriminatorProp.name,
            "\" does not match polymorphic input DTO branch \"${getDtoClassName().canonicalName}\" " +
                    "whose entity type is \"${dtoType.baseType.qualifiedName}\""
        )
        endControlFlow()
    }

    private fun FunSpec.Builder.addDraftAssignment(prop: DtoProp<ImmutableType, ImmutableProp>, valueExpr: String) {
        val baseProp = prop.toTailProp().baseProp
        if (isSimpleProp(prop)) {
            addStatement("_draft.%L = %L", baseProp.name, valueExpr)
        } else {
            if (prop.isNullable && baseProp.let { it.isList && it.isAssociation(true) }) {
                addStatement(
                    "%L.set(_draft, %L)",
                    accessorFieldName(prop.name),
                    valueExpr
                )
            } else {
                addStatement(
                    "%L.set(_draft, %L)",
                    accessorFieldName(prop.name),
                    valueExpr
                )
            }
        }
    }

    private fun polymorphicInputDiscriminatorProp(): DtoProp<ImmutableType, ImmutableProp>? {
        if (!dtoType.modifiers.contains(DtoModifier.INPUT) ||
            !polymorphicBranch ||
            !dtoType.baseType.isEntity ||
            dtoType.baseType.inheritanceRoot == null
        ) {
            return null
        }
        return dtoType.props
            .asSequence()
            .filterIsInstance<DtoProp<ImmutableType, ImmutableProp>>()
            .firstOrNull { it.nextProp == null && it.baseProp.isDiscriminator }
    }

    private val isDefaultPolymorphicInputBranch: Boolean
        get() = polymorphicBranchKind == DtoPolymorphicBranch.Kind.DEFAULT

    private val isTypedPolymorphicInputBranch: Boolean
        get() = polymorphicBranchKind == DtoPolymorphicBranch.Kind.TYPE

    private val polymorphicRootType: ImmutableType
        get() = dtoType.baseType.inheritanceRoot ?: dtoType.baseType

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
            FunSpec
                .builder("entityType")
                .apply {
                    if (!isNestedSpecificationFragment) {
                        addModifiers(KModifier.OVERRIDE)
                    }
                }
                .returns(
                    CLASS_CLASS_NAME.parameterizedBy(
                        dtoType.baseType.className
                    )
                )
                .addStatement("return %T::class.java", dtoType.baseType.className)
                .build()
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
                            K_SPECIFICATION_ARGS_CLASS_NAME.parameterizedBy(dtoType.baseType.className)
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
                                    if (dtoType.baseType.isEntity) {
                                        addStatement("this.%L?.applyTo(args)", prop.name)
                                    } else {
                                        addStatement("this.%L?.applyTo(_applier)", prop.name)
                                    }
                                } else {
                                    if (dtoType.baseType.isEntity) {
                                        addStatement("this.%L.applyTo(args)", prop.name)
                                    } else {
                                        addStatement("this.%L.applyTo(_applier)", prop.name)
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
                "_applier.push(%T.%L.unwrap())",
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
                addStatement("this.%L?.let { it.applyTo(args.child()) }", propName)
            } else {
                addStatement("this.%L?.let { it.applyTo(args.applier) }", propName)
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
                    add("_applier.%L(", ktFunName)
                    if (Constants.MULTI_ARGS_FUNC_NAMES.contains(funcName)) {
                        add("arrayOf(")
                        tailProp.basePropMap.values.forEachIndexed { index, baseProp ->
                            if (index != 0) {
                                add(", ")
                            }
                            add(
                                "%T.%L.unwrap()",
                                baseProp.declaringType.propsClassName,
                                StringUtil.snake(baseProp.name, SnakeCase.UPPER)
                            )
                        }
                        add(")")
                    } else {
                        add(
                            "%T.%L.unwrap()",
                            tailProp.baseProp.declaringType.propsClassName,
                            StringUtil.snake(tailProp.baseProp.name, SnakeCase.UPPER)
                        )
                    }
                    if (isSpecificationConverterRequired(tailProp)) {
                        add(
                            ", %L(this.%L)",
                            StringUtil.identifier("_convert", propName),
                            propName
                        )
                    } else {
                        add(", this.%L", propName)
                    }
                    if (funcName == "like") {
                        add(", ")
                        add(if (tailProp.likeOptions.contains(LikeOption.INSENSITIVE)) "true" else "false")
                        add(", ")
                        add(if (tailProp.likeOptions.contains(LikeOption.MATCH_START)) "true" else "false")
                        add(", ")
                        add(if (tailProp.likeOptions.contains(LikeOption.MATCH_END)) "true" else "false")
                    }
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
            (prop.baseProp.converterMetadata !== null &&
                    !dtoType.modifiers.contains(DtoModifier.INPUT) &&
                    !dtoType.modifiers.contains(DtoModifier.SPECIFICATION))
        ) {
            false
        } else {
            propTypeName(prop) == prop.getBaseProp().typeName()
        }
    }

    private fun hasAccessorFields(): Boolean =
        dtoType.dtoProps.any { !isSimpleProp(it) } ||
                dtoType.foldProps.any { it.nullGuardProp !== null }

    private fun TypeSpec.Builder.addAccessorField(prop: DtoProp<ImmutableType, ImmutableProp>) {
        if (isSimpleProp(prop)) {
            return
        }
        addAccessorField(
            prop,
            accessorFieldName(prop.name),
            accessorAcceptsNull(prop),
            true
        )
    }

    private fun TypeSpec.Builder.addFoldNullGuardAccessorField(prop: FoldProp<ImmutableType, ImmutableProp>) {
        val nullGuardProp = prop.nullGuardProp ?: return
        addAccessorField(
            nullGuardProp,
            foldNullGuardAccessorFieldName(prop),
            true,
            false
        )
    }

    private fun TypeSpec.Builder.addAccessorField(
        prop: DtoProp<ImmutableType, ImmutableProp>,
        fieldName: String,
        acceptNull: Boolean,
        withConverters: Boolean,
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
                            ",\nintArrayOf(%T.%L)",
                            dtoType.baseType.draftClassName("$"),
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
                                "\n%T.%L",
                                p.baseProp.declaringType.draftClassName("$"),
                                p.baseProp.slotName
                            )
                            p = p.nextProp
                        }
                        unindent()
                        add("\n)")
                    }

                    val tailProp = prop.toTailProp()
                    val tailBaseProp = tailProp.baseProp
                    if (withConverters && prop.isIdOnly) {
                        if (dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
                            add(",\nnull")
                        } else {
                            add(
                                ",\n%T.%L(%T::class.java, ",
                                DTO_PROP_ACCESSOR,
                                if (tailBaseProp.isList) "idListGetter" else "idReferenceGetter",
                                tailBaseProp.targetTypeName(overrideNullable = false)
                            )
                            addConverterLoading(prop, false)
                            add(")")
                            add(
                                ",\n%T.%L(%T::class.java, ",
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
                                    ",\n%T.%L<%T, %L>(%T.METADATA.converter)",
                                    DTO_PROP_ACCESSOR,
                                    if (tailBaseProp.isList) "objectListGetter" else "objectReferenceGetter",
                                    tailBaseProp.targetTypeName(overrideNullable = false),
                                    propElementName(prop),
                                    propElementName(prop)
                                )
                            } else {
                                add(
                                    ",\n%T.%L<%T, %L> {",
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
                                ",\n%T.%L<%T, %L> {",
                                DTO_PROP_ACCESSOR,
                                if (tailBaseProp.isList) "objectListSetter" else "objectReferenceSetter",
                                tailBaseProp.targetTypeName(overrideNullable = false),
                                propElementName(prop)
                            )
                            indent()
                            add(
                                "\nit.%L()",
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
                    } else if (withConverters && prop.enumType !== null) {
                        val enumType = prop.enumType!!
                        val enumTypeName = tailBaseProp.targetTypeName(overrideNullable = false)
                        if (dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
                            add(",\nnull")
                        } else {
                            add(",\n{\n")
                            indent()
                            beginControlFlow("when (it as %T)", enumTypeName)
                            for ((en, v) in enumType.valueMap) {
                                addStatement("%T.%L -> %L", enumTypeName, en, v)
                            }
                            endControlFlow()
                            unindent()
                            add("}")
                        }
                        add(",\n{\n")
                        indent()
                        addValueToEnum(prop)
                        unindent()
                        add("}")
                    } else if (withConverters && prop.dtoConverterMetadata != null) {
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

    private fun accessorAcceptsNull(prop: DtoProp<ImmutableType, ImmutableProp>): Boolean =
        !(prop.isNullable() && (!prop.toTailProp().getBaseProp().isNullable ||
                dtoType.modifiers.contains(DtoModifier.SPECIFICATION) ||
                dtoType.modifiers.contains(DtoModifier.FUZZY) ||
                prop.inputModifier == DtoModifier.FUZZY
                )
                )

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
                        if (prop.enumType !== null) {
                            add("return ")
                            addValueToEnum(prop, "value")
                        } else {
                            add(
                                "return %T.%L.unwrap().%L<%T, %T>(%L).input(value)",
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

    private fun TypeSpec.Builder.addHibernateValidatorEnhancement(getter: Boolean) {
        addFunction(
            FunSpec
                .builder(
                    "\$\$_hibernateValidator_get${
                        if (getter) "Getter" else "Field"
                    }Value"
                )
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("name", STRING)
                .returns(ANY.copy(nullable = true))
                .beginControlFlow("return when(name)")
                .apply {
                    for (prop in dtoType.props) {
                        addStatement(
                            "%S -> %L",
                            if (getter) {
                                StringUtil.identifier(
                                    if (propTypeName(prop) == BOOLEAN) "is" else "get",
                                    prop.name
                                )
                            } else {
                                prop.name
                            },
                            prop.name
                        )
                    }
                }
                .addStatement(
                    "else -> throw IllegalArgumentException(%L)",
                    "\"No ${if (getter) "getter" else "field"} named \\\"\${name}\\\"\""
                )
                .endControlFlow()
                .build()
        )
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
        val polymorphicRootProp = polymorphicRootFoldPropOrNull(prop)
        val typeName = if (polymorphicRootProp != null) {
            val polymorphicOwner = requireNotNull(parent)
            polymorphicOwner.getDtoClassName(polymorphicOwner.targetSimpleName(polymorphicRootProp))
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
        val enumType = prop.enumType
        if (enumType !== null) {
            return (if (enumType.isNumeric) INT else STRING).copy(nullable = prop.isNullable)
        }

        val metadata = prop.dtoConverterMetadata
        val propElementName = propElementName(prop)
        if (dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
            val funcName = prop.toTailProp().getFuncName()
            if (funcName != null) {
                when (funcName) {
                    "null", "notNull" ->
                        return BOOLEAN.copy(nullable = prop.isNullable)

                    "valueIn", "valueNotIn" ->
                        return COLLECTION.parameterizedBy(
                            metadata?.targetTypeName ?: propElementName.toList(baseProp.isList)
                        ).copy(nullable = prop.isNullable)

                    "id", "associatedIdEq", "associatedIdNe" ->
                        return baseProp.targetType!!.idProp!!.clientClassName.copy(nullable = prop.isNullable)

                    "associatedIdIn", "associatedIdNotIn" ->
                        return COLLECTION.parameterizedBy(baseProp.targetType!!.idProp!!.clientClassName)
                            .copy(nullable = prop.isNullable)
                }
            }
            if (baseProp.isAssociation(true)) {
                return propElementName.copy(nullable = prop.isNullable)
            }
        }
        if (metadata != null) {
            return metadata.targetTypeName.copy(nullable = prop.isNullable)
        }

        return propElementName
            .toList(baseProp.isList && !(propElementName is ParameterizedTypeName && propElementName.rawType == LIST))
            .copy(nullable = prop.isNullable)
    }

    private fun propElementName(prop: DtoProp<ImmutableType, ImmutableProp>): TypeName {
        polymorphicRootPropOrNull(prop)?.let { polymorphicRootProp ->
            val polymorphicOwner = requireNotNull(parent)
            return polymorphicOwner.getDtoClassName(
                polymorphicOwner.targetSimpleName(polymorphicRootProp)
            )
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
        val baseProp = tailProp.baseProp
        return if (tailProp.isIdOnly) {
            baseProp.targetType!!.idProp!!.clientClassName
        } else if (baseProp.idViewBaseProp !== null) {
            baseProp.idViewBaseProp!!.targetType!!.idProp!!.clientClassName
        } else {
            tailProp.baseProp.clientClassName
        }.copy(nullable = false)
    }

    private fun lsiTailProp(prop: DtoProp<ImmutableType, ImmutableProp>) =
        (lsiDtoType.prop(lsiGraph, prop.name) as site.addzero.lsi.jimmer.dto.DtoBaseProp)
            .tailProp(lsiGraph)

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

    private fun polymorphicRootPropOrNull(
        prop: DtoProp<ImmutableType, ImmutableProp>,
    ): DtoProp<ImmutableType, ImmutableProp>? {
        if (!polymorphicBranch) {
            return null
        }
        return parent?.dtoType?.dtoProps?.singleOrNull { rootProp ->
            val targetType = rootProp.targetType
            rootProp.name == prop.name &&
                targetType != null &&
                (!rootProp.isRecursive || targetType.isFocusedRecursion)
        }
    }

    private fun polymorphicRootFoldPropOrNull(
        prop: FoldProp<ImmutableType, ImmutableProp>,
    ): FoldProp<ImmutableType, ImmutableProp>? {
        if (!polymorphicBranch) {
            return null
        }
        return parent?.dtoType?.foldProps?.singleOrNull { rootProp -> rootProp.name == prop.name }
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

    private fun CodeBlock.Builder.addValueToEnum(
        prop: DtoProp<ImmutableType, ImmutableProp>,
        variableName: String = "it"
    ) {
        beginControlFlow(
            "when ($variableName as %T)",
            if (propTypeName(prop).copy(nullable = false) == INT) INT else STRING
        )
        val enumTypeName = prop.toTailProp().baseProp.typeName(overrideNullable = false)
        for ((v, en) in prop.enumType!!.constantMap) {
            addStatement("%L -> %T.%L", v, enumTypeName, en)
        }
        addStatement("else -> throw IllegalArgumentException(")
        indent()
        addStatement("%S + $variableName + %S", "Illegal value \"", "\" for the enum type \"$enumTypeName\"")
        unindent()
        add(")\n")
        endControlFlow()
    }

    private fun CodeBlock.Builder.addConverterLoading(
        prop: DtoProp<ImmutableType, ImmutableProp>,
        forList: Boolean,
    ) {
        val baseProp: ImmutableProp = prop.toTailProp().getBaseProp()
        add(
            "%T.%L.unwrap().%L",
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
            prop.getEnumType() != null || prop.dtoConverterMetadata != null
        }
    }

    private val DtoProp<ImmutableType, ImmutableProp>.dtoConverterMetadata: ConverterMetadata?
        get() {
            val funcName = getFuncName()
            if ("null" == funcName || "notNull" == funcName) {
                return null
            }
            val baseProp = toTailProp().getBaseProp()
            val resolver = baseProp.ctx.resolver
            val metadata = baseProp.converterMetadata
            if (metadata != null) {
                return metadata
            }
            if ("id" == funcName) {
                val metadata = baseProp.targetType!!.idProp!!.converterMetadata
                if (metadata != null && baseProp.isList && !dtoType.modifiers.contains(DtoModifier.SPECIFICATION)) {
                    return metadata.toListMetadata(resolver)
                }
                return metadata
            }
            if ("associatedInEq" == funcName || "associatedInNe" == funcName) {
                return baseProp.targetType!!.idProp!!.converterMetadata
            }
            if ("associatedIdIn" == funcName || "associatedIdNotIn" == funcName) {
                return baseProp.targetType!!.idProp!!.converterMetadata?.toListMetadata(resolver)
            }
            if (baseProp.idViewBaseProp !== null) {
                return baseProp.idViewBaseProp!!.targetType!!.idProp!!.converterMetadata?.let {
                    if (baseProp.isList) it.toListMetadata(resolver) else it
                }
            }
            return null
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
                                .defaultValue("this.${prop.name}")
                                .build()
                        )
                        args += prop.name
                        lsiDtoType
                            .prop(lsiGraph, prop.name)
                            .dtoLoadedStateStorageNameOrNull(lsiGraph, LsiLanguage.KOTLIN)
                            ?.let {
                            addParameter(
                                ParameterSpec.builder(it, BOOLEAN)
                                    .defaultValue("this.$it")
                                    .build()
                            )
                            args += it
                        }
                    }
                    addStatement("return %T(%L)", getDtoClassName(), args.joinToString())
                }
                .build()
        )
    }

    private fun TypeSpec.Builder.addHashCode() {
        addFunction(
            FunSpec
                .builder("hashCode")
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .returns(INT)
                .addCode(
                    CodeBlock
                        .builder()
                        .apply {
                            dtoType.props.forEachIndexed { index, prop ->
                                val hashCodeFunName = if (propTypeName(prop).isArray()) {
                                    "contentHashCode"
                                } else {
                                    "hashCode"
                                }
                                addStatement(
                                    "%L %L",
                                    if (index == 0) "var _hash =" else "_hash = 31 * _hash +",
                                    if (prop.isNullable) {
                                        "(${prop.alias}?.$hashCodeFunName() ?: 0)"
                                    } else {
                                        "${prop.alias}.$hashCodeFunName()"
                                    }
                                )
                                lsiDtoType
                                    .prop(lsiGraph, prop.name)
                                    .dtoLoadedStateStorageNameOrNull(lsiGraph, LsiLanguage.KOTLIN)
                                    ?.let {
                                    addStatement("_hash = _hash * 31 + %L.hashCode()", it)
                                }
                            }
                            addStatement("return _hash")
                        }
                        .build()
                )
                .build()
        )
    }

    private fun TypeSpec.Builder.addEquals() {
        addFunction(
            FunSpec
                .builder("equals")
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .addParameter("other", ANY.copy(nullable = true))
                .returns(BOOLEAN)
                .addCode(
                    CodeBlock.builder()
                        .apply {
                            addStatement("val _other = other as? %T ?: return false", getDtoClassName())
                            dtoType.props.forEachIndexed { index, prop ->
                                if (index == 0) {
                                    add("return ")
                                }
                                val statePropName = lsiDtoType
                                    .prop(lsiGraph, prop.name)
                                    .dtoLoadedStateStorageNameOrNull(lsiGraph, LsiLanguage.KOTLIN)
                                if (statePropName !== null) {
                                    add("%L == _other.%L && (\n", statePropName, statePropName)
                                    indent()
                                    add("!%L || ", statePropName)
                                }
                                if (propTypeName(prop).isArray()) {
                                    add("%L.contentEquals(_other.%L)", prop.alias, prop.alias)
                                } else {
                                    add("%L == _other.%L", prop.alias, prop.alias)
                                }
                                if (statePropName !== null) {
                                    unindent()
                                    add("\n)")
                                }
                                if (index + 1 < dtoType.props.size) {
                                    add(" &&")
                                }
                                add("\n")
                            }
                        }
                        .build()
                )
                .build()
        )
    }

    private fun TypeSpec.Builder.addToString() {
        addFunction(
            FunSpec.builder("toString")
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .returns(STRING)
                .addCode(
                    CodeBlock
                        .builder()
                        .apply {
                            val hashCondProps = dtoType.modifiers.contains(DtoModifier.INPUT) &&
                                    dtoType.dtoProps.any {
                                        lsiDtoType
                                            .prop(lsiGraph, it.name)
                                            .dtoLoadedStateStorageNameOrNull(
                                                lsiGraph,
                                                LsiLanguage.KOTLIN,
                                            ) != null || it.inputModifier == DtoModifier.FUZZY
                                    }
                            if (hashCondProps) {
                                addStatement("val builder = StringBuilder()")
                                addStatement("var separator = \"\"")
                                addStatement("builder.append(%S).append('(')", simpleNamePart())
                                for (prop in dtoType.props) {
                                    val stateFieldName = lsiDtoType
                                        .prop(lsiGraph, prop.name)
                                        .dtoLoadedStateStorageNameOrNull(
                                            lsiGraph,
                                            LsiLanguage.KOTLIN,
                                        )
                                    if (stateFieldName != null) {
                                        beginControlFlow("if (%L)", stateFieldName)
                                    } else if (prop is DtoProp<*, *> && prop.getInputModifier() == DtoModifier.FUZZY) {
                                        beginControlFlow("if (%L != null)", prop.getName())
                                    }
                                    if (prop.getName() == "builder") {
                                        addStatement(
                                            "builder.append(separator).append(%S).append(this.%L)",
                                            prop.getName() + '=',
                                            prop.getName()
                                        )
                                        addStatement("separator = \", \"")
                                    } else {
                                        addStatement(
                                            "builder.append(separator).append(%S).append(%L)",
                                            prop.getName() + '=',
                                            prop.getName()
                                        )
                                        addStatement("separator = \", \"")
                                    }
                                    if (stateFieldName != null || (prop is DtoProp<*, *> && prop.getInputModifier() == DtoModifier.FUZZY)) {
                                        endControlFlow()
                                    }
                                }
                                addStatement("builder.append(')')")
                                addStatement("return builder.toString()")
                            } else {
                                add("return %S +\n", simpleNamePart() + "(")
                                dtoType.props.forEachIndexed { index, prop ->
                                    add(
                                        "    %S + %L + \n",
                                        (if (index == 0) "" else ", ") + prop.name + '=',
                                        prop.name
                                    )
                                }
                                add("    %S\n", ")")
                            }
                        }
                        .build()
                )
                .build()
        )
    }

    private fun simpleNamePart(): String =
        (innerClassName ?: dtoType.name!!).let { name ->
            parent
                ?.let { "${it.simpleNamePart()}.$name" }
                ?: name
        }

    private fun typeDocumentation(): String? =
        lsiDtoType.documentation
            ?.takeIf(String::isNotEmpty)
            ?.replace("%", "%%")

    private fun propDocumentation(prop: AbstractProp): String? =
        lsiDtoType
            .prop(lsiGraph, prop.name)
            .documentation
            ?.takeIf(String::isNotEmpty)
            ?.replace("%", "%%")

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

        private val EXPRESSION_PACKAGE = "org.babyfish.jimmer.sql.kt.ast.expression"

    }
}
