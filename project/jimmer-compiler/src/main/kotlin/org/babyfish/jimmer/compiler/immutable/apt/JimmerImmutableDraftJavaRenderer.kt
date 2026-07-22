package org.babyfish.jimmer.compiler.immutable.apt

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.WildcardTypeName
import java.io.Serializable
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.List
import java.util.Objects
import javax.lang.model.element.Modifier
import org.babyfish.jimmer.CircularReferenceException
import org.babyfish.jimmer.Draft
import org.babyfish.jimmer.DraftConsumer
import org.babyfish.jimmer.ImmutableObjects
import org.babyfish.jimmer.UnloadedException
import org.babyfish.jimmer.client.Description
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftArtifactMetadata
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftCodegenSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftPropPlan
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftRuntimePropKind
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftTypePlan
import site.addzero.lsi.jimmer.ImmutableTypeKind
import org.babyfish.jimmer.compiler.render.apt.toJavaAnnotationSpecPreservingArgumentOrder
import org.babyfish.jimmer.compiler.render.apt.toJavaTypeName
import org.babyfish.jimmer.compiler.render.apt.toJavaTypeVariableName
import org.babyfish.jimmer.currentVersion
import org.babyfish.jimmer.impl.util.StringUtil
import org.babyfish.jimmer.internal.GeneratedBy
import org.babyfish.jimmer.jackson.ImmutableModuleRequiredException
import org.babyfish.jimmer.lang.OldChain
import org.babyfish.jimmer.meta.ImmutablePropCategory
import org.babyfish.jimmer.meta.PropId
import org.babyfish.jimmer.runtime.DraftContext
import org.babyfish.jimmer.runtime.DraftSpi
import org.babyfish.jimmer.runtime.ImmutableSpi
import org.babyfish.jimmer.runtime.Internal
import org.babyfish.jimmer.runtime.NonSharedList
import org.babyfish.jimmer.runtime.Visibility
import org.babyfish.jimmer.sql.collection.IdViewList
import org.babyfish.jimmer.sql.collection.ManyToManyViewList
import org.babyfish.jimmer.sql.collection.MutableIdViewList
import org.jspecify.annotations.NonNull
import org.jspecify.annotations.Nullable
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiVariance

internal class JimmerImmutableDraftJavaRenderer {

    fun render(
        schema: JimmerImmutableDraftCodegenSchema,
        type: JimmerImmutableDraftTypePlan,
    ): GeneratedArtifact {
        require(schema.typesById[type.typeId] == type) {
            "Immutable draft type '${type.typeId.value}' does not belong to the supplied schema"
        }
        val artifactMetadata = JimmerImmutableDraftArtifactMetadata(schema)
        val content = JavaFile.builder(
            type.qualifiedName.substringBeforeLast('.', missingDelimiterValue = ""),
            DraftTypeRenderer(schema, type).render(),
        )
            .indent("    ")
            .build()
            .toString()
        return GeneratedArtifact.source(
            kind = ArtifactKind.JAVA_SOURCE,
            qualifiedName = artifactMetadata.javaQualifiedName(type),
            content = content,
            aggregationMode = artifactMetadata.aggregationMode(type),
            originatingSymbols = type.artifactOriginatingSymbols,
            originatingSources = type.artifactOriginatingSources.toSet(),
            dependencySymbols = type.dependencySymbols,
            dependencySources = artifactMetadata.dependencySources(type),
        )
    }
}

private class DraftTypeRenderer(
    private val schema: JimmerImmutableDraftCodegenSchema,
    private val type: JimmerImmutableDraftTypePlan,
) {

    private val originalClass = type.originalClassName
    private val draftClass = type.draftClassName
    private val producerClass = draftClass.nestedClass(PRODUCER)
    private val implementorClass = producerClass.nestedClass(IMPLEMENTOR)
    private val implClass = producerClass.nestedClass(IMPL)
    private val draftImplClass = producerClass.nestedClass(DRAFT_IMPL)
    private val builderClass = draftClass.nestedClass(BUILDER)
    private val draftTypeName = type.draftTypeName
    private val legacyProps = buildList {
        type.idPropId?.let { propId -> add(type.propsById.getValue(propId)) }
        addAll(type.propsBySlot.filterNot { prop -> prop.propId == type.idPropId })
    }

    fun render(): TypeSpec {
        val builder = TypeSpec.interfaceBuilder(draftClass.simpleName())
            .addTypeVariables(type.typeParameters.map { parameter -> parameter.toJavaTypeVariableName() })
            .addSuperinterface(type.selfType.toJavaTypeName())
            .addAnnotation(type.generatedAnnotation())
        if (type.visibility == site.addzero.lsi.model.LsiVisibility.PUBLIC) {
            builder.addModifiers(Modifier.PUBLIC)
        }
        if (type.directSuperTypes.isEmpty()) {
            builder.addSuperinterface(Draft::class.java)
        } else {
            type.directSuperTypes.forEach { superType ->
                builder.addSuperinterface(superType.toDraftJavaTypeName())
            }
        }
        builder.addField(
            FieldSpec.builder(
                ClassName.get(type.packageName, "${draftClass.simpleName()}.Producer"),
                "\$",
            )
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("\$T.INSTANCE", producerClass)
                .build()
        )
        legacyProps.forEach { prop -> addDraftPropMembers(builder, prop) }
        builder.addType(producerType())
        if (!type.isMappedSuperclass) {
            builder.addType(builderType())
        }
        return builder.build()
    }

    private fun addDraftPropMembers(
        builder: TypeSpec.Builder,
        prop: JimmerImmutableDraftPropPlan,
    ) {
        if (prop.autoCreateSupported && prop.immutableReference && !prop.list) {
            builder.addMethod(draftGetter(prop, autoCreate = false))
        }
        if (prop.autoCreateSupported) {
            builder.addMethod(draftGetter(prop, autoCreate = true))
        }
        if (prop.writable) {
            builder.addMethod(draftSetter(prop))
        }
        addAssociatedIdMethods(builder, prop, withImplementation = false)
        if (prop.referenceMutationSupported) {
            builder.addMethod(draftReferenceMutationMethod(prop, withBase = false, withImplementation = false))
            builder.addMethod(draftReferenceMutationMethod(prop, withBase = true, withImplementation = false))
        }
    }

    private fun draftGetter(
        prop: JimmerImmutableDraftPropPlan,
        autoCreate: Boolean,
    ): MethodSpec {
        val builder = MethodSpec.methodBuilder(prop.sourceGetterName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(prop.draftTypeName(autoCreate))
        if (autoCreate) {
            builder.addParameter(TypeName.BOOLEAN, "autoCreate")
        } else if (prop.nullable) {
            builder.addAnnotation(Nullable::class.java)
        }
        return builder.build()
    }

    private fun draftSetter(prop: JimmerImmutableDraftPropPlan): MethodSpec {
        return MethodSpec.methodBuilder(prop.javaSetterName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addAnnotation(OldChain::class.java)
            .addParameter(prop.type.toJavaTypeName(), prop.codegenName)
            .returns(draftTypeName)
            .apply {
                prop.documentation?.takeIf(String::isNotEmpty)?.let { documentation ->
                    addJavadoc("\$L", documentation)
                }
            }
            .build()
    }

    private fun draftReferenceMutationMethod(
        prop: JimmerImmutableDraftPropPlan,
        withBase: Boolean,
        withImplementation: Boolean,
    ): MethodSpec {
        val methodName = if (prop.list) prop.javaAdderByName else prop.javaApplierName
        val builder = MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(OldChain::class.java)
            .returns(draftTypeName)
        if (!withImplementation) {
            builder.addModifiers(Modifier.ABSTRACT)
        } else {
            builder.addAnnotation(Override::class.java)
        }
        if (withBase) {
            builder.addParameter(prop.elementType.toJavaTypeName(), "base")
        }
        builder.addParameter(
            ParameterizedTypeName.get(DRAFT_CONSUMER, prop.draftElementTypeName()),
            "block",
        )
        if (withImplementation) {
            if (withBase) {
                if (prop.list) {
                    builder.addStatement(
                        "\$L(true).add((\$T)\$T.\$\$.produce(base, block))",
                        prop.sourceGetterName,
                        prop.draftElementTypeName(),
                        prop.draftElementTypeName(),
                    )
                } else {
                    builder.addStatement(
                        "\$L(\$T.\$\$.produce(base, block))",
                        prop.javaSetterName,
                        prop.draftElementTypeName(),
                    )
                }
            } else {
                builder.addStatement("\$L(null, block)", methodName)
            }
            builder.addStatement("return this")
        }
        return builder.build()
    }

    private fun producerType(): TypeSpec {
        val builder = TypeSpec.classBuilder(PRODUCER)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addAnnotation(type.generatedAnnotation())
        addProducerInstance(builder)
        if (!type.isMappedSuperclass) {
            addProducerSlots(builder)
        }
        addProducerRuntimeType(builder)
        builder.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
        if (!type.isMappedSuperclass) {
            builder.addMethod(produceMethod(withBase = false, resolveImmediately = false))
            builder.addMethod(produceMethod(withBase = true, resolveImmediately = false))
            builder.addMethod(produceMethod(withBase = false, resolveImmediately = true))
            builder.addMethod(produceMethod(withBase = true, resolveImmediately = true))
            builder.addType(implementorType())
            builder.addType(implType())
            builder.addType(draftImplType())
        }
        return builder.build()
    }

    private fun addProducerInstance(builder: TypeSpec.Builder) {
        builder.addField(
            FieldSpec.builder(producerClass, "INSTANCE", Modifier.STATIC, Modifier.FINAL)
                .initializer("new \$T()", producerClass)
                .build()
        )
    }

    private fun addProducerSlots(builder: TypeSpec.Builder) {
        legacyProps.forEach { prop ->
            val initializer = if (prop.runtimeOwnerTypeId == type.typeId) {
                CodeBlock.of("\$L", prop.slotIndex)
            } else {
                CodeBlock.of(
                    "\$T.\$L",
                    prop.runtimeOwnerTypeId.producerClassName(),
                    prop.slotName,
                )
            }
            builder.addField(
                FieldSpec.builder(
                    TypeName.INT,
                    prop.slotName,
                    Modifier.PUBLIC,
                    Modifier.STATIC,
                    Modifier.FINAL,
                )
                    .initializer(initializer)
                    .build()
            )
        }
    }

    private fun addProducerRuntimeType(builder: TypeSpec.Builder) {
        val initializer = CodeBlock.builder()
            .add("\$T\n", RUNTIME_IMMUTABLE_TYPE)
            .indent()
            .add(".newBuilder(\n")
            .indent()
            .add("\$S,\n", currentVersion())
            .add("\$T.class,\n", originalClass)
        addRuntimeSuperTypes(initializer)
        if (type.isMappedSuperclass) {
            initializer.add("null\n")
        } else {
            initializer.add(
                "(ctx, base) -> new \$T(ctx, (\$T)base)\n",
                draftImplClass,
                originalClass,
            )
        }
        initializer.unindent().add(")\n")
        if (!type.isMappedSuperclass) {
            type.runtimeRedefinedPropIds.forEach { propId ->
                val prop = type.propsById.getValue(propId)
                initializer.add(".redefine(\$S, \$L)\n", prop.name, prop.slotName)
            }
        }
        type.runtimeDeclaredPropIds.forEach { propId ->
            addRuntimeProp(initializer, type.propsById.getValue(propId))
        }
        initializer.add(".build()").unindent()
        builder.addField(
            FieldSpec.builder(
                RUNTIME_IMMUTABLE_TYPE,
                "TYPE",
                Modifier.PUBLIC,
                Modifier.STATIC,
                Modifier.FINAL,
            )
                .initializer(initializer.build())
                .build()
        )
    }

    private fun addRuntimeSuperTypes(initializer: CodeBlock.Builder) {
        when (type.directSuperTypes.size) {
            0 -> initializer.add("\$T.emptyList(),\n", Collections::class.java)
            1 -> initializer.add(
                "\$T.singleton(\$T.Producer.TYPE),\n",
                Collections::class.java,
                type.directSuperTypes.single().draftRawClassName(),
            )
            else -> {
                initializer.add("\$T.asList(\n", Arrays::class.java).indent()
                type.directSuperTypes.forEachIndexed { index, superType ->
                    if (index != 0) {
                        initializer.add(",\n")
                    }
                    initializer.add("\$T.Producer.TYPE", superType.draftRawClassName())
                }
                initializer.add("\n").unindent().add("),\n")
            }
        }
    }

    private fun addRuntimeProp(
        initializer: CodeBlock.Builder,
        prop: JimmerImmutableDraftPropPlan,
    ) {
        val slot = prop.metadataSlotIndex?.let { CodeBlock.of("\$L", prop.slotName) }
            ?: CodeBlock.of("-1")
        val elementType = prop.runtimeProp.metadataElementType.toJavaTypeName()
        when (prop.runtimeProp.kind) {
            JimmerImmutableDraftRuntimePropKind.ID -> initializer.add(
                ".id(\$L, \$S, \$T.class)\n",
                slot,
                prop.name,
                elementType,
            )
            JimmerImmutableDraftRuntimePropKind.VERSION -> initializer.add(
                ".version(\$L, \$S)\n",
                slot,
                prop.name,
            )
            JimmerImmutableDraftRuntimePropKind.LOGICAL_DELETED -> initializer.add(
                ".logicalDeleted(\$L, \$S, \$T.class, \$L)\n",
                slot,
                prop.name,
                elementType,
                prop.nullable,
            )
            JimmerImmutableDraftRuntimePropKind.KEY_SCALAR -> initializer.add(
                ".key(\$L, \$S, \$T.class, \$L)\n",
                slot,
                prop.name,
                elementType,
                prop.nullable,
            )
            JimmerImmutableDraftRuntimePropKind.KEY_REFERENCE -> initializer.add(
                ".keyReference(\$L, \$S, \$T.class, \$T.class, \$L)\n",
                slot,
                prop.name,
                requireNotNull(prop.runtimeProp.associationAnnotationTypeId).className(),
                elementType,
                prop.nullable,
            )
            JimmerImmutableDraftRuntimePropKind.ASSOCIATION -> initializer.add(
                ".add(\$L, \$S, \$T.class, \$T.class, \$L)\n",
                slot,
                prop.name,
                requireNotNull(prop.runtimeProp.associationAnnotationTypeId).className(),
                elementType,
                prop.nullable,
            )
            JimmerImmutableDraftRuntimePropKind.VALUE -> initializer.add(
                ".add(\$L, \$S, \$T.\$L, \$T.class, \$L)\n",
                slot,
                prop.name,
                ImmutablePropCategory::class.java,
                prop.runtimeProp.valueCategory.name,
                elementType,
                prop.nullable,
            )
        }
    }

    private fun produceMethod(
        withBase: Boolean,
        resolveImmediately: Boolean,
    ): MethodSpec {
        val builder = MethodSpec.methodBuilder("produce")
            .addModifiers(Modifier.PUBLIC)
            .returns(originalClass)
        if (withBase) {
            builder.addParameter(originalClass, "base")
        }
        if (resolveImmediately) {
            builder.addParameter(TypeName.BOOLEAN, "resolveImmediately")
        }
        builder.addParameter(ParameterizedTypeName.get(DRAFT_CONSUMER, draftClass), "block")
        val base = if (withBase) "base" else "null"
        if (resolveImmediately) {
            builder.addStatement(
                "return (\$T)\$T.produce(TYPE, \$L, resolveImmediately, block)",
                originalClass,
                Internal::class.java,
                base,
            )
        } else {
            builder.addStatement(
                "return (\$T)\$T.produce(TYPE, \$L, block)",
                originalClass,
                Internal::class.java,
                base,
            )
        }
        return builder.build()
    }

    private fun implementorType(): TypeSpec {
        val builder = TypeSpec.classBuilder(IMPLEMENTOR)
            .addAnnotation(type.generatedAnnotation())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.ABSTRACT)
            .addSuperinterface(originalClass)
            .addSuperinterface(ImmutableSpi::class.java)
            .addJavadoc("Class, not interface, for free-marker")
            .addAnnotation(jsonPropertyOrderAnnotation())
        legacyProps.forEach { prop ->
            prop.javaDeeperPropIdName?.let { fieldName ->
                builder.addField(
                    FieldSpec.builder(
                        PropId::class.java,
                        fieldName,
                        Modifier.PUBLIC,
                        Modifier.STATIC,
                        Modifier.FINAL,
                    )
                        .initializer(
                            "\$T.TYPE.getProp(\$S).getManyToManyViewBaseDeeperProp().getId()",
                            producerClass,
                            prop.name,
                        )
                        .build()
                )
            }
        }
        builder.addMethod(implementorGetMethod(byId = true))
        builder.addMethod(implementorGetMethod(byId = false))
        legacyProps.forEach { prop -> addImplementorGetter(builder, prop) }
        builder.addMethod(
            MethodSpec.methodBuilder("__type")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(Override::class.java)
                .returns(RUNTIME_IMMUTABLE_TYPE)
                .addStatement("return TYPE")
                .build()
        )
        builder.addMethod(
            MethodSpec.methodBuilder("getDummyPropForJacksonError__")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .returns(TypeName.INT)
                .addStatement("throw new \$T()", ImmutableModuleRequiredException::class.java)
                .build()
        )
        return builder.build()
    }

    private fun implementorGetMethod(byId: Boolean): MethodSpec {
        val parameterType = if (byId) TypeName.get(PropId::class.java) else TypeName.get(String::class.java)
        val builder = MethodSpec.methodBuilder("__get")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Override::class.java)
            .addParameter(parameterType, "prop")
            .returns(Any::class.java)
        if (byId) {
            builder.addStatement("int __propIndex = prop.asIndex()")
            builder.beginControlFlow("switch (__propIndex)")
            builder.addCode("case -1:\n\t\t")
            builder.addStatement("return __get(prop.asName())")
        } else {
            builder.beginControlFlow("switch (prop)")
        }
        type.propsBySlot.forEach { prop ->
            addCase(builder, prop, byId)
            val propType = prop.type.toJavaTypeName()
            if (propType.isPrimitive) {
                builder.addStatement("return (\$T)\$L()", propType.box(), prop.sourceGetterName)
            } else {
                builder.addStatement("return \$L()", prop.sourceGetterName)
            }
        }
        builder.addStatement(
            "default: throw new IllegalArgumentException(\$S + prop + \$S)",
            "Illegal property name for \"${type.qualifiedName}\": \"",
            "\"",
        )
        builder.endControlFlow()
        return builder.build()
    }

    private fun addImplementorGetter(
        builder: TypeSpec.Builder,
        prop: JimmerImmutableDraftPropPlan,
    ) {
        prop.manyToManyBasePropId?.let { basePropId ->
            val baseProp = type.propsById.getValue(basePropId)
            builder.addMethod(
                MethodSpec.methodBuilder(prop.sourceGetterName)
                    .addAnnotation(Override::class.java)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .returns(prop.type.toJavaTypeName())
                    .addStatement(
                        "return new \$T<>(\$L, \$L())",
                        ManyToManyViewList::class.java,
                        requireNotNull(prop.javaDeeperPropIdName),
                        baseProp.sourceGetterName,
                    )
                    .build()
            )
        }
        if (!prop.isJavaBeanStyle) {
            builder.addMethod(
                MethodSpec.methodBuilder(prop.javaBeanGetterName)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .returns(prop.type.toJavaTypeName())
                    .addStatement("return \$L()", prop.sourceGetterName)
                    .apply {
                        prop.annotationPlan.beanBridgeMethodAnnotations.forEach { annotation ->
                            addAnnotation(annotation.toJavaAnnotationSpecPreservingArgumentOrder())
                        }
                    }
                    .build()
            )
        }
    }

    private fun jsonPropertyOrderAnnotation(): AnnotationSpec {
        val values = buildList {
            add("dummyPropForJacksonError__")
            addAll(type.propsBySlot.map(JimmerImmutableDraftPropPlan::name))
        }
        return AnnotationSpec.builder(JSON_PROPERTY_ORDER)
            .addMember(
                "value",
                values.joinToString(prefix = "{", postfix = "}") { "\$S" },
                *values.toTypedArray(),
            )
            .build()
    }

    private fun implType(): TypeSpec {
        val builder = TypeSpec.classBuilder(IMPL)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .addAnnotation(type.generatedAnnotation())
            .superclass(implementorClass)
            .addSuperinterface(Cloneable::class.java)
            .addSuperinterface(Serializable::class.java)
        addImplFields(builder)
        addImplConstructor(builder)
        legacyProps.forEach { prop -> addImplGetter(builder, prop) }
        builder.addMethod(implCloneMethod())
        builder.addMethod(implIsLoadedMethod(byId = true))
        builder.addMethod(implIsLoadedMethod(byId = false))
        builder.addMethod(implIsVisibleMethod(byId = true))
        builder.addMethod(implIsVisibleMethod(byId = false))
        builder.addMethod(implHashCodeMethod(shallow = false))
        builder.addMethod(implHashCodeMethod(shallow = true))
        builder.addMethod(
            MethodSpec.methodBuilder("__hashCode")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .addParameter(TypeName.BOOLEAN, "shallow")
                .returns(TypeName.INT)
                .addStatement("return shallow ? __shallowHashCode() : hashCode()")
                .build()
        )
        builder.addMethod(implEqualsMethod(shallow = false))
        builder.addMethod(implEqualsMethod(shallow = true))
        builder.addMethod(
            MethodSpec.methodBuilder("__equals")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .addParameter(Any::class.java, "obj")
                .addParameter(TypeName.BOOLEAN, "shallow")
                .returns(TypeName.BOOLEAN)
                .addStatement("return shallow ? __shallowEquals(obj) : equals(obj)")
                .build()
        )
        builder.addMethod(
            MethodSpec.methodBuilder("toString")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .returns(String::class.java)
                .addStatement("return \$T.toString(this)", ImmutableObjects::class.java)
                .build()
        )
        return builder.build()
    }

    private fun addImplFields(builder: TypeSpec.Builder) {
        builder.addField(
            FieldSpec.builder(Visibility::class.java, VISIBILITY_FIELD)
                .addModifiers(Modifier.PRIVATE)
                .build()
        )
        legacyProps.forEach { prop ->
            prop.valueFieldName?.let { fieldName ->
                val fieldType = if (prop.list) {
                    ParameterizedTypeName.get(
                        ClassName.get(NonSharedList::class.java),
                        prop.elementType.toJavaTypeName().box(),
                    )
                } else {
                    prop.type.toJavaTypeName()
                }
                builder.addField(FieldSpec.builder(fieldType, fieldName).build())
            }
            prop.loadedStateFieldName?.let { fieldName ->
                builder.addField(
                    FieldSpec.builder(TypeName.BOOLEAN, fieldName)
                        .initializer("false")
                        .build()
                )
            }
        }
    }

    private fun addImplConstructor(builder: TypeSpec.Builder) {
        if (!type.requiresVisibilityState) {
            return
        }
        val constructor = MethodSpec.constructorBuilder()
            .addStatement(
                "\$L = \$T.of(\$L)",
                VISIBILITY_FIELD,
                Visibility::class.java,
                type.propsBySlot.size,
            )
        legacyProps.filterNot { prop -> prop.valueState.hasValue }.forEach { prop ->
            constructor.addStatement("\$L.show(\$L, false)", VISIBILITY_FIELD, prop.slotName)
        }
        builder.addMethod(constructor.build())
    }

    private fun addImplGetter(
        builder: TypeSpec.Builder,
        prop: JimmerImmutableDraftPropPlan,
    ) {
        if (prop.languageFormula || prop.manyToManyBasePropId != null) {
            return
        }
        val method = MethodSpec.methodBuilder(prop.sourceGetterName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .returns(prop.type.toJavaTypeName())
        if (!prop.isJavaBeanStyle) {
            method.addAnnotation(JSON_IGNORE)
        }
        if (prop.nullable) {
            method.addAnnotation(Nullable::class.java)
        }
        prop.sourceDocumentation?.takeIf(String::isNotEmpty)?.let { documentation ->
            method.addAnnotation(
                AnnotationSpec.builder(Description::class.java)
                    .addMember("value", "\$S", documentation)
                    .build()
            )
        }
        val basePropId = prop.idViewBasePropId
        if (basePropId != null) {
            val baseProp = type.propsById.getValue(basePropId)
            val targetType = requireNotNull(baseProp.targetTypeId).let(schema.typesById::getValue)
            val targetIdProp = targetType.propsById.getValue(requireNotNull(baseProp.targetIdPropId))
            if (baseProp.list) {
                method.addStatement(
                    "return new \$T<>(\$T.TYPE, \$L())",
                    IdViewList::class.java,
                    targetType.typeId.producerClassName(),
                    baseProp.sourceGetterName,
                )
            } else {
                method.addStatement(
                    "\$T __target = \$L()",
                    baseProp.elementType.toJavaTypeName(),
                    baseProp.sourceGetterName,
                )
                if (prop.nullable) {
                    method.addStatement(
                        "return __target != null ? __target.\$L() : null",
                        targetIdProp.sourceGetterName,
                    )
                } else {
                    method.addStatement("return __target.\$L()", targetIdProp.sourceGetterName)
                }
            }
        } else {
            val loadedState = prop.loadedStateFieldName
            if (loadedState != null) {
                method.beginControlFlow("if (!\$L)", loadedState)
            } else {
                method.beginControlFlow("if (\$L == null)", requireNotNull(prop.valueFieldName))
            }
            method.addStatement(
                "throw new \$T(\$T.class, \$S)",
                UnloadedException::class.java,
                originalClass,
                prop.name,
            )
            method.endControlFlow()
            method.addStatement("return \$L", requireNotNull(prop.valueFieldName))
        }
        builder.addMethod(method.build())
    }

    private fun implCloneMethod(): MethodSpec {
        return MethodSpec.methodBuilder("clone")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .returns(implClass)
            .beginControlFlow("try")
            .addStatement("\$T copy = (\$T) super.clone()", implClass, implClass)
            .addStatement("\$T originalVisibility = this.\$L", Visibility::class.java, VISIBILITY_FIELD)
            .beginControlFlow("if (originalVisibility != null)")
            .addStatement(
                "\$T newVisibility = \$T.of(\$L)",
                Visibility::class.java,
                Visibility::class.java,
                type.propsBySlot.size,
            )
            .beginControlFlow("for (int propId = 0; propId < \$L; propId++)", type.propsBySlot.size)
            .addStatement("newVisibility.show(propId, originalVisibility.visible(propId))")
            .endControlFlow()
            .addStatement("copy.\$L = newVisibility", VISIBILITY_FIELD)
            .nextControlFlow("else")
            .addStatement("copy.\$L = null", VISIBILITY_FIELD)
            .endControlFlow()
            .addStatement("return copy")
            .nextControlFlow("catch(\$T ex)", CloneNotSupportedException::class.java)
            .addStatement("throw new AssertionError(ex)")
            .endControlFlow()
            .build()
    }

    private fun implIsLoadedMethod(byId: Boolean): MethodSpec {
        val parameterType = if (byId) TypeName.get(PropId::class.java) else TypeName.get(String::class.java)
        val builder = MethodSpec.methodBuilder("__isLoaded")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .addParameter(parameterType, "prop")
            .returns(TypeName.BOOLEAN)
        if (byId) {
            builder.addStatement("int __propIndex = prop.asIndex()")
            builder.beginControlFlow("switch (__propIndex)")
            builder.addCode("case -1:\n\t\t")
            builder.addStatement("return __isLoaded(prop.asName())")
        } else {
            builder.beginControlFlow("switch (prop)")
        }
        type.propsBySlot.forEach { prop ->
            addCase(builder, prop, byId)
            builder.addStatement("return \$L", loadedExpression(prop))
        }
        builder.addStatement(
            "default: throw new IllegalArgumentException(\$S + prop + \$S)",
            "Illegal property name for \"${type.qualifiedName}\": \"",
            "\"",
        )
        builder.endControlFlow()
        return builder.build()
    }

    private fun loadedExpression(prop: JimmerImmutableDraftPropPlan): CodeBlock {
        prop.idViewBasePropId?.let { basePropId ->
            val baseProp = type.propsById.getValue(basePropId)
            val targetType = requireNotNull(baseProp.targetTypeId).let(schema.typesById::getValue)
            val targetIdProp = targetType.propsById.getValue(requireNotNull(baseProp.targetIdPropId))
            return if (baseProp.list) {
                CodeBlock.of(
                    "__isLoaded(\$T.byIndex(\$L)) && \$L().stream().allMatch(__each -> " +
                        "((\$T)__each).__isLoaded(\$T.byIndex(\$T.\$L)))",
                    PropId::class.java,
                    baseProp.slotName,
                    baseProp.sourceGetterName,
                    ImmutableSpi::class.java,
                    PropId::class.java,
                    targetType.typeId.producerClassName(),
                    targetIdProp.slotName,
                )
            } else {
                CodeBlock.of(
                    "__isLoaded(\$T.byIndex(\$L)) && (\$L() == null || " +
                        "((\$T)\$L()).__isLoaded(\$T.byIndex(\$T.\$L)))",
                    PropId::class.java,
                    baseProp.slotName,
                    baseProp.sourceGetterName,
                    ImmutableSpi::class.java,
                    baseProp.sourceGetterName,
                    PropId::class.java,
                    targetType.typeId.producerClassName(),
                    targetIdProp.slotName,
                )
            }
        }
        prop.manyToManyBasePropId?.let { basePropId ->
            val baseProp = type.propsById.getValue(basePropId)
            return CodeBlock.of(
                "__isLoaded(\$T.byIndex(\$L)) && \$L().stream().allMatch(__each -> " +
                    "((\$T)__each).__isLoaded(\$L))",
                PropId::class.java,
                baseProp.slotName,
                baseProp.sourceGetterName,
                ImmutableSpi::class.java,
                requireNotNull(prop.javaDeeperPropIdName),
            )
        }
        if (prop.languageFormula) {
            if (prop.formulaDependencyPaths.isEmpty()) {
                return CodeBlock.of("true")
            }
            return CodeBlock.builder()
                .apply {
                    prop.formulaDependencyPaths.forEachIndexed { index, path ->
                        if (index != 0) {
                            add(" && ")
                        }
                        if (path.size == 1) {
                            val dependency = propPlan(path.single())
                            add("__isLoaded(\$T.byIndex(\$L))", PropId::class.java, dependency.slotName)
                        } else {
                            add("\$T.isLoadedChain(this", ImmutableObjects::class.java)
                            path.forEach { dependencyId ->
                                val dependency = propPlan(dependencyId)
                                add(
                                    ", \$T.byIndex(\$T.\$L)",
                                    PropId::class.java,
                                    dependency.runtimeOwnerTypeId.producerClassName(),
                                    dependency.slotName,
                                )
                            }
                            add(")")
                        }
                    }
                }
                .build()
        }
        prop.loadedStateFieldName?.let { return CodeBlock.of("\$L", it) }
        return CodeBlock.of("\$L != null", requireNotNull(prop.valueFieldName))
    }

    private fun implIsVisibleMethod(byId: Boolean): MethodSpec {
        val parameterType = if (byId) TypeName.get(PropId::class.java) else TypeName.get(String::class.java)
        val builder = MethodSpec.methodBuilder("__isVisible")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .addParameter(parameterType, "prop")
            .returns(TypeName.BOOLEAN)
            .beginControlFlow("if (\$L == null)", VISIBILITY_FIELD)
            .addStatement("return true")
            .endControlFlow()
        if (byId) {
            builder.addStatement("int __propIndex = prop.asIndex()")
            builder.beginControlFlow("switch (__propIndex)")
            builder.addCode("case -1:\n\t\t")
            builder.addStatement("return __isVisible(prop.asName())")
        } else {
            builder.beginControlFlow("switch (prop)")
        }
        type.propsBySlot.forEach { prop ->
            addCase(builder, prop, byId)
            builder.addStatement("return \$L.visible(\$L)", VISIBILITY_FIELD, prop.slotName)
        }
        builder.addStatement("default: return true")
        builder.endControlFlow()
        return builder.build()
    }

    private fun implHashCodeMethod(shallow: Boolean): MethodSpec {
        val builder = MethodSpec.methodBuilder(if (shallow) "__shallowHashCode" else "hashCode")
            .addModifiers(if (shallow) Modifier.PRIVATE else Modifier.PUBLIC)
            .returns(TypeName.INT)
            .addStatement("int hash = \$L != null ? \$L.hashCode() : 0", VISIBILITY_FIELD, VISIBILITY_FIELD)
        if (!shallow) {
            builder.addAnnotation(Override::class.java)
        }
        legacyProps.filter { prop -> prop.valueState.hasValue }.forEach { prop ->
            val valueField = requireNotNull(prop.valueFieldName)
            if (prop.primitive) {
                builder.beginControlFlow("if (\$L)", requireNotNull(prop.loadedStateFieldName))
                builder.addStatement("hash = 31 * hash + \$T.hashCode(\$L)", prop.type.toJavaTypeName().box(), valueField)
                if (!shallow && type.idPropId == prop.propId) {
                    builder.addComment("If entity-id is loaded, return directly")
                    builder.addStatement("return hash")
                }
                builder.endControlFlow()
            } else if (shallow) {
                val condition = prop.loadedStateFieldName?.let { field -> "\$L" to arrayOf<Any>(field) }
                    ?: "\$L != null" to arrayOf<Any>(valueField)
                builder.beginControlFlow("if (${condition.first})", *condition.second)
                builder.addStatement("hash = 31 * hash + \$T.identityHashCode(\$L)", System::class.java, valueField)
                builder.endControlFlow()
            } else {
                val loadedState = prop.loadedStateFieldName
                if (loadedState != null) {
                    builder.beginControlFlow("if (\$L && \$L != null)", loadedState, valueField)
                } else {
                    builder.beginControlFlow("if (\$L != null)", valueField)
                }
                builder.addStatement("hash = 31 * hash + \$L.hashCode()", valueField)
                if (type.idPropId == prop.propId) {
                    builder.addComment("If entity-id is loaded, return directly")
                    builder.addStatement("return hash")
                }
                builder.endControlFlow()
            }
        }
        builder.addStatement("return hash")
        return builder.build()
    }

    private fun implEqualsMethod(shallow: Boolean): MethodSpec {
        val builder = MethodSpec.methodBuilder(if (shallow) "__shallowEquals" else "equals")
            .addModifiers(if (shallow) Modifier.PRIVATE else Modifier.PUBLIC)
            .addParameter(Any::class.java, "obj")
            .returns(TypeName.BOOLEAN)
        if (!shallow) {
            builder.addAnnotation(Override::class.java)
        }
        builder.beginControlFlow("if (obj == null || !(obj instanceof \$T))", implementorClass)
        builder.addStatement("return false")
        builder.endControlFlow()
        builder.addStatement("\$T __other = (\$T)obj", implementorClass, implementorClass)
        legacyProps.forEach { prop ->
            builder.beginControlFlow(
                "if (__isVisible(\$T.byIndex(\$L)) != __other.__isVisible(\$T.byIndex(\$L)))",
                PropId::class.java,
                prop.slotName,
                PropId::class.java,
                prop.slotName,
            )
            builder.addStatement("return false")
            builder.endControlFlow()
            if (!prop.valueState.hasValue) {
                return@forEach
            }
            val valueField = requireNotNull(prop.valueFieldName)
            val loadedName = prop.forcedLoadedStateName
            prop.loadedStateFieldName?.let { loadedState ->
                builder.addStatement("boolean \$L = this.\$L", loadedName, loadedState)
            } ?: builder.addStatement("boolean \$L = \$L != null", loadedName, valueField)
            builder.beginControlFlow(
                "if (\$L != __other.__isLoaded(\$T.byIndex(\$L)))",
                loadedName,
                PropId::class.java,
                prop.slotName,
            )
            builder.addStatement("return false")
            builder.endControlFlow()
            if (shallow || prop.primitive) {
                if (!shallow && type.idPropId == prop.propId) {
                    builder.beginControlFlow("if (\$L)", loadedName)
                    builder.addComment("If entity-id is loaded, return directly")
                    builder.addStatement("return \$L == __other.\$L()", valueField, prop.sourceGetterName)
                    builder.endControlFlow()
                } else {
                    builder.beginControlFlow(
                        "if (\$L && \$L != __other.\$L())",
                        loadedName,
                        valueField,
                        prop.sourceGetterName,
                    )
                    builder.addStatement("return false")
                    builder.endControlFlow()
                }
            } else if (type.idPropId == prop.propId) {
                builder.beginControlFlow("if (\$L)", loadedName)
                builder.addComment("If entity-id is loaded, return directly")
                builder.addStatement(
                    "return \$T.equals(\$L, __other.\$L())",
                    Objects::class.java,
                    valueField,
                    prop.sourceGetterName,
                )
                builder.endControlFlow()
            } else {
                builder.beginControlFlow(
                    "if (\$L && !\$T.equals(\$L, __other.\$L()))",
                    loadedName,
                    Objects::class.java,
                    valueField,
                    prop.sourceGetterName,
                )
                builder.addStatement("return false")
                builder.endControlFlow()
            }
        }
        builder.addStatement("return true")
        return builder.build()
    }

    private fun draftImplType(): TypeSpec {
        val builder = TypeSpec.classBuilder(DRAFT_IMPL)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .addAnnotation(type.generatedAnnotation())
            .superclass(implementorClass)
            .addSuperinterface(DraftSpi::class.java)
            .addSuperinterface(draftClass)
        addDraftImplFields(builder)
        JimmerImmutableDraftJavaValidationRenderer.addStaticFields(type, builder)
        builder.addMethod(draftImplConstructor())
        addDraftImplReadonlyMethods(builder)
        legacyProps.forEach { prop ->
            addDraftImplGetter(builder, prop)
            addDraftImplCreator(builder, prop)
            addDraftImplSetter(builder, prop)
            addAssociatedIdMethods(builder, prop, withImplementation = true)
            if (prop.referenceMutationSupported) {
                builder.addMethod(draftReferenceMutationMethod(prop, withBase = false, withImplementation = true))
                builder.addMethod(draftReferenceMutationMethod(prop, withBase = true, withImplementation = true))
            }
        }
        builder.addMethod(draftImplSetMethod(byId = true))
        builder.addMethod(draftImplSetMethod(byId = false))
        builder.addMethod(draftImplShowMethod(byId = true))
        builder.addMethod(draftImplShowMethod(byId = false))
        builder.addMethod(draftImplUnloadMethod(byId = true))
        builder.addMethod(draftImplUnloadMethod(byId = false))
        builder.addMethod(
            MethodSpec.methodBuilder("__draftContext")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .returns(DraftContext::class.java)
                .addStatement("return \$L", DRAFT_CONTEXT_FIELD)
                .build()
        )
        builder.addMethod(draftImplResolveMethod())
        builder.addMethod(
            MethodSpec.methodBuilder("__isResolved")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .returns(TypeName.BOOLEAN)
                .addStatement("return \$L != null", DRAFT_RESOLVED_FIELD)
                .build()
        )
        builder.addMethod(draftImplModifiedMethod())
        return builder.build()
    }

    private fun addDraftImplFields(builder: TypeSpec.Builder) {
        builder.addField(
            FieldSpec.builder(DraftContext::class.java, DRAFT_CONTEXT_FIELD, Modifier.PRIVATE).build()
        )
        builder.addField(FieldSpec.builder(implClass, DRAFT_BASE_FIELD, Modifier.PRIVATE).build())
        builder.addField(FieldSpec.builder(implClass, DRAFT_MODIFIED_FIELD, Modifier.PRIVATE).build())
        builder.addField(
            FieldSpec.builder(TypeName.BOOLEAN, DRAFT_RESOLVING_FIELD, Modifier.PRIVATE).build()
        )
        builder.addField(FieldSpec.builder(originalClass, DRAFT_RESOLVED_FIELD, Modifier.PRIVATE).build())
    }

    private fun draftImplConstructor(): MethodSpec {
        return MethodSpec.constructorBuilder()
            .addParameter(DraftContext::class.java, "ctx")
            .addParameter(originalClass, "base")
            .addStatement("\$L = ctx", DRAFT_CONTEXT_FIELD)
            .beginControlFlow("if (base != null)")
            .addStatement("\$L = (\$T)base", DRAFT_BASE_FIELD, implClass)
            .endControlFlow()
            .beginControlFlow("else")
            .addStatement("\$L = new \$T()", DRAFT_MODIFIED_FIELD, implClass)
            .endControlFlow()
            .build()
    }

    private fun addDraftImplReadonlyMethods(builder: TypeSpec.Builder) {
        builder.addMethod(readonlyDelegate("__isLoaded", PropId::class.java, TypeName.BOOLEAN, "prop"))
        builder.addMethod(readonlyDelegate("__isLoaded", String::class.java, TypeName.BOOLEAN, "prop"))
        builder.addMethod(
            MethodSpec.methodBuilder("__isVisible")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .addParameter(PropId::class.java, "prop")
                .returns(TypeName.BOOLEAN)
                .addStatement("return \$L.__isVisible(prop)", unmodifiedExpression)
                .build()
        )
        builder.addMethod(
            MethodSpec.methodBuilder("__isVisible")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .addParameter(String::class.java, "prop")
                .returns(TypeName.BOOLEAN)
                .addStatement("return \$L.__isVisible(prop)", unmodifiedExpression)
                .build()
        )
        builder.addMethod(
            MethodSpec.methodBuilder("hashCode")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .returns(TypeName.INT)
                .addStatement("return \$L.hashCode()", unmodifiedExpression)
                .build()
        )
        builder.addMethod(
            MethodSpec.methodBuilder("__hashCode")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .addParameter(TypeName.BOOLEAN, "shallow")
                .returns(TypeName.INT)
                .addStatement("return \$L.__hashCode(shallow)", unmodifiedExpression)
                .build()
        )
        builder.addMethod(
            MethodSpec.methodBuilder("equals")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .addParameter(Any::class.java, "obj")
                .returns(TypeName.BOOLEAN)
                .addStatement("return \$L.equals(obj)", unmodifiedExpression)
                .build()
        )
        builder.addMethod(
            MethodSpec.methodBuilder("__equals")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .addParameter(Any::class.java, "obj")
                .addParameter(TypeName.BOOLEAN, "shallow")
                .returns(TypeName.BOOLEAN)
                .addStatement("return \$L.__equals(obj, shallow)", unmodifiedExpression)
                .build()
        )
        builder.addMethod(
            MethodSpec.methodBuilder("toString")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .returns(String::class.java)
                .addStatement("return \$T.toString(this)", ImmutableObjects::class.java)
                .build()
        )
    }

    private fun readonlyDelegate(
        name: String,
        parameterType: Class<*>,
        returnType: TypeName,
        parameterName: String,
    ): MethodSpec {
        return MethodSpec.methodBuilder(name)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .addParameter(parameterType, parameterName)
            .returns(returnType)
            .addStatement("return \$L.\$L(\$L)", unmodifiedExpression, name, parameterName)
            .build()
    }

    private fun addDraftImplGetter(
        builder: TypeSpec.Builder,
        prop: JimmerImmutableDraftPropPlan,
    ) {
        if (prop.manyToManyBasePropId != null) {
            return
        }
        val method = MethodSpec.methodBuilder(prop.sourceGetterName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .returns(prop.draftTypeName(autoCreate = false))
        if (!prop.isJavaBeanStyle) {
            method.addAnnotation(JSON_IGNORE)
        }
        if (prop.nullable) {
            method.addAnnotation(Nullable::class.java)
        }
        prop.idViewBasePropId?.let { basePropId ->
            val baseProp = type.propsById.getValue(basePropId)
            val targetType = requireNotNull(baseProp.targetTypeId).let(schema.typesById::getValue)
            val targetIdProp = targetType.propsById.getValue(requireNotNull(baseProp.targetIdPropId))
            if (baseProp.list) {
                method.addStatement(
                    "\$T<\$T> __ids = new \$T<>(\$L().size())",
                    List::class.java,
                    targetIdProp.type.toJavaTypeName().box(),
                    ArrayList::class.java,
                    baseProp.sourceGetterName,
                )
                method.beginControlFlow(
                    "for (\$T __target : \$L())",
                    baseProp.elementType.toJavaTypeName(),
                    baseProp.sourceGetterName,
                )
                method.addStatement("__ids.add(__target.\$L())", targetIdProp.sourceGetterName)
                method.endControlFlow()
                method.addStatement("return __ids")
            } else {
                method.addStatement(
                    "\$T __target = \$L()",
                    baseProp.elementType.toJavaTypeName(),
                    baseProp.sourceGetterName,
                )
                if (prop.nullable) {
                    method.addStatement(
                        "return __target != null ? __target.\$L() : null",
                        targetIdProp.sourceGetterName,
                    )
                } else {
                    method.addStatement("return __target.\$L()", targetIdProp.sourceGetterName)
                }
            }
        } ?: run {
            when {
                prop.list -> method.addStatement(
                    "return \$L.toDraftList(\$L.\$L(), \$T.class, \$L)",
                    DRAFT_CONTEXT_FIELD,
                    unmodifiedExpression,
                    prop.sourceGetterName,
                    prop.elementType.toJavaTypeName(),
                    prop.immutableReference,
                )
                prop.immutableReference -> method.addStatement(
                    "return \$L.toDraftObject(\$L.\$L())",
                    DRAFT_CONTEXT_FIELD,
                    unmodifiedExpression,
                    prop.sourceGetterName,
                )
                else -> method.addStatement(
                    "return \$L.\$L()",
                    unmodifiedExpression,
                    prop.sourceGetterName,
                )
            }
        }
        builder.addMethod(method.build())
    }

    private fun addDraftImplCreator(
        builder: TypeSpec.Builder,
        prop: JimmerImmutableDraftPropPlan,
    ) {
        if (!prop.autoCreateSupported) {
            return
        }
        val realProp = prop.idViewBasePropId?.let(type.propsById::getValue) ?: prop
        val method = MethodSpec.methodBuilder(prop.sourceGetterName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .addParameter(TypeName.BOOLEAN, "autoCreate")
            .returns(prop.draftTypeName(autoCreate = true))
        if (prop.nullable) {
            method.beginControlFlow(
                "if (autoCreate && (!__isLoaded(\$T.byIndex(\$L)) || \$L() == null))",
                PropId::class.java,
                realProp.slotName,
                realProp.sourceGetterName,
            )
        } else {
            method.beginControlFlow(
                "if (autoCreate && !__isLoaded(\$T.byIndex(\$L)))",
                PropId::class.java,
                realProp.slotName,
            )
        }
        if (prop.list) {
            method.addStatement("\$L(new \$T<>())", realProp.javaSetterName, ArrayList::class.java)
        } else {
            method.addStatement(
                "\$L(\$T.\$\$.produce(null, null))",
                realProp.javaSetterName,
                realProp.draftElementTypeName(),
            )
        }
        method.endControlFlow()
        if (prop.list) {
            if (realProp.propId != prop.propId) {
                val targetType = requireNotNull(realProp.targetTypeId).let(schema.typesById::getValue)
                method.addStatement(
                    "return new \$T<>(\$T.TYPE, \$L())",
                    MutableIdViewList::class.java,
                    targetType.typeId.producerClassName(),
                    realProp.sourceGetterName,
                )
            } else {
                method.addStatement(
                    "return \$L.toDraftList(\$L.\$L(), \$T.class, \$L)",
                    DRAFT_CONTEXT_FIELD,
                    unmodifiedExpression,
                    prop.sourceGetterName,
                    prop.elementType.toJavaTypeName(),
                    prop.immutableReference,
                )
            }
        } else {
            method.addStatement(
                "return \$L.toDraftObject(\$L.\$L())",
                DRAFT_CONTEXT_FIELD,
                unmodifiedExpression,
                prop.sourceGetterName,
            )
        }
        builder.addMethod(method.build())
    }

    private fun addDraftImplSetter(
        builder: TypeSpec.Builder,
        prop: JimmerImmutableDraftPropPlan,
    ) {
        if (!prop.writable) {
            return
        }
        val method = MethodSpec.methodBuilder(prop.javaSetterName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .addParameter(prop.type.toJavaTypeName(), prop.codegenName)
            .returns(draftClass)
        addFrozenCheck(method)
        prop.idViewBasePropId?.let { basePropId ->
            val baseProp = type.propsById.getValue(basePropId)
            val targetType = requireNotNull(baseProp.targetTypeId).let(schema.typesById::getValue)
            val targetIdProp = targetType.propsById.getValue(requireNotNull(baseProp.targetIdPropId))
            if (!prop.primitive) {
                method.beginControlFlow("if (\$L != null)", prop.codegenName)
            }
            if (prop.list) {
                method.addStatement(
                    "\$T<\$T> __targets = new \$T<>(\$L.size())",
                    List::class.java,
                    baseProp.elementType.toJavaTypeName(),
                    ArrayList::class.java,
                    prop.codegenName,
                )
                method.beginControlFlow(
                    "for (\$T __id : \$L)",
                    targetIdProp.type.toJavaTypeName(),
                    prop.codegenName,
                )
                method.addStatement(
                    "__targets.add(\$T.makeIdOnly(\$T.class, __id))",
                    ImmutableObjects::class.java,
                    baseProp.elementType.toJavaTypeName(),
                )
                method.endControlFlow()
                method.addStatement("\$L(__targets)", baseProp.javaSetterName)
            } else {
                method.addStatement(
                    "\$L(\$T.makeIdOnly(\$T.class, \$L))",
                    baseProp.javaSetterName,
                    ImmutableObjects::class.java,
                    baseProp.elementType.toJavaTypeName(),
                    prop.codegenName,
                )
            }
            if (!prop.primitive) {
                method.nextControlFlow("else")
                if (prop.list) {
                    method.addStatement("\$L(\$T.emptyList())", baseProp.javaSetterName, Collections::class.java)
                } else {
                    method.addStatement("\$L(null)", baseProp.javaSetterName)
                }
                method.endControlFlow()
            }
        } ?: run {
            JimmerImmutableDraftJavaValidationRenderer.addValidation(
                type,
                prop,
                prop.codegenName,
                method,
            )
            method.addStatement("\$T __tmpModified = \$L()", implClass, DRAFT_MODIFIED_FIELD)
            if (prop.list) {
                method.addStatement(
                    "__tmpModified.\$L = \$T.of(__tmpModified.\$L, \$L)",
                    requireNotNull(prop.valueFieldName),
                    NonSharedList::class.java,
                    prop.valueFieldName,
                    prop.codegenName,
                )
            } else {
                method.addStatement(
                    "__tmpModified.\$L = \$L",
                    requireNotNull(prop.valueFieldName),
                    prop.codegenName,
                )
            }
            prop.loadedStateFieldName?.let { loadedState ->
                method.addStatement("__tmpModified.\$L = true", loadedState)
            }
        }
        method.addStatement("return this")
        builder.addMethod(method.build())
    }

    private fun addFrozenCheck(method: MethodSpec.Builder) {
        method.beginControlFlow("if (\$L != null)", DRAFT_RESOLVED_FIELD)
        method.addStatement(
            "throw new \$T(\$S)",
            IllegalStateException::class.java,
            FROZEN_MESSAGE,
        )
        method.endControlFlow()
    }

    private fun draftImplSetMethod(byId: Boolean): MethodSpec {
        val parameterType = if (byId) TypeName.get(PropId::class.java) else TypeName.get(String::class.java)
        val builder = MethodSpec.methodBuilder("__set")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(suppressAllAnnotation())
            .addAnnotation(Override::class.java)
            .addParameter(parameterType, "prop")
            .addParameter(Any::class.java, "value")
        if (byId) {
            builder.addStatement("int __propIndex = prop.asIndex()")
            builder.beginControlFlow("switch (__propIndex)")
            builder.addCode("case -1:\n\t\t")
            builder.addStatement("__set(prop.asName(), value)")
            builder.addStatement("return")
        } else {
            builder.beginControlFlow("switch (prop)")
        }
        type.propsBySlot.forEach { prop ->
            addCase(builder, prop, byId)
            val castType = prop.type.toJavaTypeName().box()
            when {
                prop.isDiscriminator -> {
                    builder.addStatement("\$T __tmpModified = \$L()", implClass, DRAFT_MODIFIED_FIELD)
                    builder.addStatement(
                        "__tmpModified.\$L = (\$T)value",
                        requireNotNull(prop.valueFieldName),
                        castType,
                    )
                    prop.loadedStateFieldName?.let { loadedState ->
                        builder.addStatement("__tmpModified.\$L = true", loadedState)
                    }
                    builder.addStatement("break")
                }
                prop.languageFormula || prop.manyToManyBasePropId != null -> builder.addStatement("break")
                prop.primitive -> {
                    builder.addStatement(
                        "if (value == null) throw new \$T(\$S);\n" +
                            "\$L((\$T)value);\n" +
                            "break",
                        IllegalArgumentException::class.java,
                        "'${prop.name}' cannot be null, if you want to set null, please use any annotation " +
                            "whose simple name is \"Nullable\" to decorate the property",
                        prop.javaSetterName,
                        castType,
                    )
                }
                prop.writable -> {
                    builder.addStatement("\$L((\$T)value);break", prop.javaSetterName, castType)
                }
                else -> builder.addStatement("break")
            }
        }
        builder.addStatement(
            "default: throw new IllegalArgumentException(\$S + prop + \$S)",
            "Illegal property ${if (byId) "id" else "name"} for \"${type.qualifiedName}\": \"",
            "\"",
        )
        builder.endControlFlow()
        return builder.build()
    }

    private fun draftImplShowMethod(byId: Boolean): MethodSpec {
        val parameterType = if (byId) TypeName.get(PropId::class.java) else TypeName.get(String::class.java)
        val builder = MethodSpec.methodBuilder("__show")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .addParameter(parameterType, "prop")
            .addParameter(TypeName.BOOLEAN, "visible")
        addFrozenCheck(builder)
        builder.addStatement("\$T __visibility = \$L.\$L", Visibility::class.java, unmodifiedExpression, VISIBILITY_FIELD)
        builder.beginControlFlow("if (__visibility == null)")
        builder.beginControlFlow("if (visible)")
        builder.addStatement("return")
        builder.endControlFlow()
        builder.addStatement(
            "\$L().\$L = __visibility = \$T.of(\$L)",
            DRAFT_MODIFIED_FIELD,
            VISIBILITY_FIELD,
            Visibility::class.java,
            type.propsBySlot.size,
        )
        builder.endControlFlow()
        if (byId) {
            builder.addStatement("int __propIndex = prop.asIndex()")
            builder.beginControlFlow("switch (__propIndex)")
            builder.addCode("case -1:\n\t\t")
            builder.addStatement("__show(prop.asName(), visible)")
            builder.addStatement("return")
        } else {
            builder.beginControlFlow("switch (prop)")
        }
        type.propsBySlot.forEach { prop ->
            addCase(builder, prop, byId)
            builder.addStatement("__visibility.show(\$L, visible);break", prop.slotName)
        }
        builder.addStatement(
            "default: throw new IllegalArgumentException(\n\$>\$S + \nprop + \n\$S\n\$<)",
            "Illegal property ${if (byId) "id" else "name"} for \"${type.qualifiedName}\": \"",
            "\",it does not exists",
        )
        builder.endControlFlow()
        return builder.build()
    }

    private fun draftImplUnloadMethod(byId: Boolean): MethodSpec {
        val parameterType = if (byId) TypeName.get(PropId::class.java) else TypeName.get(String::class.java)
        val builder = MethodSpec.methodBuilder("__unload")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .addParameter(parameterType, "prop")
        addFrozenCheck(builder)
        if (byId) {
            builder.addStatement("int __propIndex = prop.asIndex()")
            builder.beginControlFlow("switch (__propIndex)")
            builder.addCode("case -1:\n\t\t")
            builder.addStatement("__unload(prop.asName())")
            builder.addStatement("return")
        } else {
            builder.beginControlFlow("switch (prop)")
        }
        type.propsBySlot.forEach { prop ->
            addCase(builder, prop, byId)
            val basePropId = prop.idViewBasePropId ?: prop.manyToManyBasePropId
            when {
                basePropId != null -> {
                    val baseProp = type.propsById.getValue(basePropId)
                    builder.addStatement("__unload(\$T.byIndex(\$L));break", PropId::class.java, baseProp.slotName)
                }
                prop.languageFormula -> builder.addStatement("break")
                prop.loadedStateFieldName != null -> {
                    builder.addStatement(
                        "\$L().\$L = \$L",
                        DRAFT_MODIFIED_FIELD,
                        requireNotNull(prop.valueFieldName),
                        prop.unloadedValueLiteral,
                    )
                    builder.addStatement(
                        "\$L().\$L = false;break",
                        DRAFT_MODIFIED_FIELD,
                        prop.loadedStateFieldName,
                    )
                }
                prop.valueFieldName != null -> {
                    builder.addStatement(
                        "\$L().\$L = null;break",
                        DRAFT_MODIFIED_FIELD,
                        prop.valueFieldName,
                    )
                }
                else -> builder.addStatement("break")
            }
        }
        builder.addStatement(
            "default: throw new IllegalArgumentException(\$S + prop + \$S)",
            "Illegal property ${if (byId) "id" else "name"} for \"${type.qualifiedName}\": \"",
            "\", it does not exist or its loaded state is not controllable",
        )
        builder.endControlFlow()
        return builder.build()
    }

    private fun draftImplResolveMethod(): MethodSpec {
        val builder = MethodSpec.methodBuilder("__resolve")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .returns(Any::class.java)
        builder.beginControlFlow("if (\$L != null)", DRAFT_RESOLVED_FIELD)
        builder.addStatement("return \$L", DRAFT_RESOLVED_FIELD)
        builder.endControlFlow()
        builder.beginControlFlow("if (\$L)", DRAFT_RESOLVING_FIELD)
        builder.addStatement("throw new \$T()", CircularReferenceException::class.java)
        builder.endControlFlow()
        builder.addStatement("\$L = true", DRAFT_RESOLVING_FIELD)
        builder.beginControlFlow("try")
        addResolveCode(builder)
        builder.endControlFlow()
        builder.beginControlFlow("finally")
        builder.addStatement("\$L = false", DRAFT_RESOLVING_FIELD)
        builder.endControlFlow()
        return builder.build()
    }

    private fun addResolveCode(builder: MethodSpec.Builder) {
        builder.addStatement("\$T base = \$L", implementorClass, DRAFT_BASE_FIELD)
        builder.addStatement("\$T __tmpModified = \$L", implClass, DRAFT_MODIFIED_FIELD)
        val resolvableProps = legacyProps.filter { prop ->
            prop.valueState.hasValue && (prop.immutableReference || prop.list)
        }
        if (resolvableProps.isNotEmpty()) {
            builder.beginControlFlow("if (__tmpModified == null)")
            resolvableProps.forEach { prop ->
                builder.beginControlFlow(
                    "if (base.__isLoaded(\$T.byIndex(\$L)))",
                    PropId::class.java,
                    prop.slotName,
                )
                builder.addStatement(
                    "\$T oldValue = base.\$L()",
                    prop.type.toJavaTypeName(),
                    prop.sourceGetterName,
                )
                builder.addStatement(
                    "\$T newValue = \$L.\$L(oldValue)",
                    prop.type.toJavaTypeName(),
                    DRAFT_CONTEXT_FIELD,
                    if (prop.list) "resolveList" else "resolveObject",
                )
                builder.beginControlFlow("if (oldValue != newValue)")
                builder.addStatement("\$L(newValue)", prop.javaSetterName)
                builder.endControlFlow()
                builder.endControlFlow()
            }
            builder.addStatement("__tmpModified = \$L", DRAFT_MODIFIED_FIELD)
            builder.nextControlFlow("else")
            resolvableProps.forEach { prop ->
                val valueField = requireNotNull(prop.valueFieldName)
                if (prop.list) {
                    builder.addStatement(
                        "__tmpModified.\$L = \$T.of(__tmpModified.\$L, \$L.resolveList(__tmpModified.\$L))",
                        valueField,
                        NonSharedList::class.java,
                        valueField,
                        DRAFT_CONTEXT_FIELD,
                        valueField,
                    )
                } else {
                    builder.addStatement(
                        "__tmpModified.\$L = \$L.resolveObject(__tmpModified.\$L)",
                        valueField,
                        DRAFT_CONTEXT_FIELD,
                        valueField,
                    )
                }
            }
            builder.endControlFlow()
        }
        builder.beginControlFlow("if (\$L != null && __tmpModified == null)", DRAFT_BASE_FIELD)
        builder.addStatement("this.\$L = base", DRAFT_RESOLVED_FIELD)
        builder.addStatement("return base")
        builder.endControlFlow()
        JimmerImmutableDraftJavaValidationRenderer.addTypeValidation(type, "__tmpModified", builder)
        builder.addStatement("this.\$L = __tmpModified", DRAFT_RESOLVED_FIELD)
        builder.addStatement("return __tmpModified")
    }

    private fun draftImplModifiedMethod(): MethodSpec {
        return MethodSpec.methodBuilder(DRAFT_MODIFIED_FIELD)
            .returns(implClass)
            .addStatement("\$T __tmpModified = \$L", implClass, DRAFT_MODIFIED_FIELD)
            .beginControlFlow("if (__tmpModified == null)")
            .addStatement("__tmpModified = \$L.clone()", DRAFT_BASE_FIELD)
            .addStatement("\$L = __tmpModified", DRAFT_MODIFIED_FIELD)
            .endControlFlow()
            .addStatement("return __tmpModified")
            .build()
    }

    private fun builderType(): TypeSpec {
        val builder = TypeSpec.classBuilder(BUILDER)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addAnnotation(type.generatedAnnotation())
        builder.addField(
            FieldSpec.builder(draftImplClass, "__draft")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build()
        )
        builder.addMethod(
            MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("this(null)")
                .build()
        )
        val baseConstructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(
                ParameterSpec.builder(originalClass, "base")
                    .addAnnotation(Nullable::class.java)
                    .build()
            )
            .addStatement("__draft = new \$T(null, base)", draftImplClass)
        legacyProps.filter(JimmerImmutableDraftPropPlan::visibilityControllable).forEach { prop ->
            baseConstructor.addStatement(
                "__draft.__show(\$T.byIndex(\$T.\$L), false)",
                PropId::class.java,
                producerClass,
                prop.slotName,
            )
        }
        builder.addMethod(baseConstructor.build())
        legacyProps.filter(JimmerImmutableDraftPropPlan::writable).forEach { prop ->
            builder.addMethod(builderSetter(prop))
        }
        builder.addMethod(
            MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(originalClass)
                .addStatement("return (\$T)__draft.\$L()", originalClass, DRAFT_MODIFIED_FIELD)
                .build()
        )
        return builder.build()
    }

    private fun builderSetter(prop: JimmerImmutableDraftPropPlan): MethodSpec {
        val boxedType = prop.type.toJavaTypeName().box()
            .annotated(
                AnnotationSpec.builder(if (prop.nullable) Nullable::class.java else NonNull::class.java).build()
            )
        val method = MethodSpec.methodBuilder(prop.codegenName)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(boxedType, prop.codegenName)
            .returns(builderClass)
        prop.annotationPlan.builderMethodAnnotations.forEach { annotation ->
            method.addAnnotation(annotation.toJavaAnnotationSpecPreservingArgumentOrder())
        }
        if (prop.nullable) {
            method.addStatement("__draft.\$L(\$L)", prop.javaSetterName, prop.codegenName)
            if (prop.visibilityControllable) {
                method.addStatement(
                    "__draft.__show(\$T.byIndex(\$T.\$L), true)",
                    PropId::class.java,
                    producerClass,
                    prop.slotName,
                )
            }
        } else {
            method.beginControlFlow("if (\$L != null)", prop.codegenName)
            method.addStatement("__draft.\$L(\$L)", prop.javaSetterName, prop.codegenName)
            if (prop.visibilityControllable) {
                method.addStatement(
                    "__draft.__show(\$T.byIndex(\$T.\$L), true)",
                    PropId::class.java,
                    producerClass,
                    prop.slotName,
                )
            }
            method.endControlFlow()
        }
        method.addStatement("return this")
        return method.build()
    }

    private fun addAssociatedIdMethods(
        builder: TypeSpec.Builder,
        prop: JimmerImmutableDraftPropPlan,
        withImplementation: Boolean,
    ) {
        val contract = prop.associatedId ?: return
        val targetType = requireNotNull(prop.targetTypeId).let(schema.typesById::getValue)
        val targetIdProp = targetType.propsById.getValue(contract.targetIdPropId)
        val idType = targetIdProp.type.toJavaTypeName().let { typeName ->
            if (prop.nullable) typeName.box() else typeName
        }
        val getterName = StringUtil.identifier(prop.sourceGetterName, "Id")
        val getter = MethodSpec.methodBuilder(getterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(idType)
            .addAnnotation(JSON_IGNORE)
        if (!idType.isPrimitive) {
            getter.addAnnotation(if (prop.nullable) Nullable::class.java else NonNull::class.java)
        }
        if (!withImplementation) {
            getter.addModifiers(Modifier.ABSTRACT)
        } else {
            getter.addAnnotation(Override::class.java)
            if (prop.nullable) {
                getter.addStatement("\$T value = \$L()", targetType.originalClassName, prop.sourceGetterName)
                getter.beginControlFlow("if (value == null)")
                getter.addStatement("return null")
                getter.endControlFlow()
                getter.addStatement("return value.\$L()", targetIdProp.sourceGetterName)
            } else {
                getter.addStatement("return \$L().\$L()", prop.sourceGetterName, targetIdProp.sourceGetterName)
            }
        }
        builder.addMethod(getter.build())

        val parameterName = contract.name
        val setter = MethodSpec.methodBuilder(StringUtil.identifier(prop.javaSetterName, "Id"))
            .addModifiers(Modifier.PUBLIC)
            .addParameter(
                ParameterSpec.builder(idType, parameterName)
                    .apply {
                        if (!idType.isPrimitive) {
                            addAnnotation(if (prop.nullable) Nullable::class.java else NonNull::class.java)
                        }
                    }
                    .build()
            )
            .returns(prop.sourceDeclaringTypeId.draftClassName())
            .addAnnotation(OldChain::class.java)
        if (!withImplementation) {
            setter.addModifiers(Modifier.ABSTRACT)
        } else {
            setter.addAnnotation(Override::class.java)
            if (prop.nullable) {
                setter.beginControlFlow("if (\$L == null)", parameterName)
                setter.addStatement("\$L(null)", prop.javaSetterName)
                setter.addStatement("return this")
                setter.endControlFlow()
                setter.addStatement(
                    "\$L(true).\$L(\$L)",
                    prop.sourceGetterName,
                    targetIdProp.javaSetterName,
                    parameterName,
                )
            } else {
                setter.addStatement(
                    "\$L(true).\$L(\$T.requireNonNull(\$L, \$S))",
                    prop.sourceGetterName,
                    targetIdProp.javaSetterName,
                    Objects::class.java,
                    parameterName,
                    "\"${prop.name}\" cannot be null",
                )
            }
            setter.addStatement("return this")
        }
        builder.addMethod(setter.build())
    }

    private fun addCase(
        builder: MethodSpec.Builder,
        prop: JimmerImmutableDraftPropPlan,
        byId: Boolean,
    ) {
        if (byId) {
            builder.addCode("case \$L:\n\t\t", prop.slotName)
        } else {
            builder.addCode("case \$S:\n\t\t", prop.name)
        }
    }

    private fun JimmerImmutableDraftPropPlan.draftElementTypeName(): TypeName {
        return if (immutableReference && !genericTarget && targetTypeId != null) {
            targetTypeId.draftClassName()
        } else {
            elementType.toJavaTypeName()
        }
    }

    private fun JimmerImmutableDraftPropPlan.draftTypeName(autoCreate: Boolean): TypeName {
        if (list && !autoCreate) {
            return type.toJavaTypeName()
        }
        val elementTypeName = draftElementTypeName()
        return if (list) {
            ParameterizedTypeName.get(LIST, elementTypeName.box())
        } else {
            elementTypeName
        }
    }

    private val JimmerImmutableDraftPropPlan.isJavaBeanStyle: Boolean
        get() = accessorStyle == org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftAccessorStyle.JAVA_BEAN_GET ||
            accessorStyle == org.babyfish.jimmer.compiler.immutable.JimmerImmutableDraftAccessorStyle.JAVA_BEAN_IS

    private val JimmerImmutableDraftTypePlan.isMappedSuperclass: Boolean
        get() = kind == ImmutableTypeKind.MAPPED_SUPERCLASS

    private val JimmerImmutableDraftTypePlan.packageName: String
        get() = qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")

    private val JimmerImmutableDraftTypePlan.originalClassName: ClassName
        get() = ClassName.bestGuess(qualifiedName)

    private val JimmerImmutableDraftTypePlan.draftClassName: ClassName
        get() = ClassName.bestGuess("${qualifiedName}Draft")

    private val JimmerImmutableDraftTypePlan.draftTypeName: TypeName
        get() = if (typeParameters.isEmpty()) {
            draftClassName
        } else {
            ParameterizedTypeName.get(
                draftClassName,
                *typeParameters.map { parameter -> parameter.toJavaTypeVariableName() }.toTypedArray(),
            )
        }

    private fun JimmerImmutableDraftTypePlan.generatedAnnotation(): AnnotationSpec {
        return AnnotationSpec.builder(GeneratedBy::class.java)
            .addMember("type", "\$T.class", originalClassName)
            .build()
    }

    private fun LsiDeclaredType.toDraftJavaTypeName(): TypeName {
        val rawType = draftRawClassName()
        if (arguments.isEmpty()) {
            return rawType
        }
        return ParameterizedTypeName.get(
            rawType,
            *arguments.map { argument -> argument.toJavaTypeName() }.toTypedArray(),
        )
    }

    private fun LsiTypeArgument.toJavaTypeName(): TypeName {
        return when (variance) {
            LsiVariance.STAR -> WildcardTypeName.subtypeOf(Any::class.java)
            LsiVariance.INVARIANT -> requireNotNull(type).toJavaTypeName().box()
            LsiVariance.IN -> WildcardTypeName.supertypeOf(requireNotNull(type).toJavaTypeName().box())
            LsiVariance.OUT -> WildcardTypeName.subtypeOf(requireNotNull(type).toJavaTypeName().box())
        }
    }

    private fun propPlan(propId: LsiSymbolId): JimmerImmutableDraftPropPlan {
        return schema.types.asSequence()
            .mapNotNull { candidate -> candidate.propsById[propId] }
            .firstOrNull()
            ?: error("Cannot resolve immutable draft property '${propId.value}'")
    }

    private val JimmerImmutableDraftPropPlan.forcedLoadedStateName: String
        get() = loadedStateFieldName ?: "__${codegenName}Loaded"

    private val JimmerImmutableDraftPropPlan.isDiscriminator: Boolean
        get() = !writable &&
            !languageFormula &&
            manyToManyBasePropId == null &&
            valueState.hasValue

    private val JimmerImmutableDraftPropPlan.unloadedValueLiteral: CodeBlock
        get() {
            val primitiveType = type as? LsiPrimitiveType ?: return CodeBlock.of("null")
            if (primitiveType.boxed) {
                return CodeBlock.of("null")
            }
            return when (primitiveType.kind) {
                LsiPrimitiveKind.BOOLEAN -> CodeBlock.of("false")
                LsiPrimitiveKind.CHAR -> CodeBlock.of("'\\u0000'")
                LsiPrimitiveKind.BYTE,
                LsiPrimitiveKind.SHORT,
                LsiPrimitiveKind.INT,
                LsiPrimitiveKind.LONG,
                LsiPrimitiveKind.FLOAT,
                LsiPrimitiveKind.DOUBLE,
                -> CodeBlock.of("0")
                LsiPrimitiveKind.UNIT,
                LsiPrimitiveKind.VOID,
                -> CodeBlock.of("null")
            }
        }

    private val unmodifiedExpression: CodeBlock
        get() = CodeBlock.of(
            "(\$L!= null ? \$L : \$L)",
            DRAFT_MODIFIED_FIELD,
            DRAFT_MODIFIED_FIELD,
            DRAFT_BASE_FIELD,
        )

    private fun suppressAllAnnotation(): AnnotationSpec {
        return AnnotationSpec.builder(SuppressWarnings::class.java)
            .addMember("value", "\$S", "all")
            .build()
    }

    private fun LsiDeclaredType.draftRawClassName(): ClassName = declarationId.draftClassName()

    private fun LsiSymbolId.draftClassName(): ClassName =
        ClassName.bestGuess("${requireTypeQualifiedName()}Draft")

    private fun LsiSymbolId.producerClassName(): ClassName = draftClassName().nestedClass(PRODUCER)

    private fun LsiSymbolId.className(): ClassName = ClassName.bestGuess(requireTypeQualifiedName())

    companion object {
        private const val PRODUCER = "Producer"
        private const val IMPLEMENTOR = "Implementor"
        private const val IMPL = "Impl"
        private const val DRAFT_IMPL = "DraftImpl"
        private const val BUILDER = "Builder"
        private const val VISIBILITY_FIELD = "__visibility"
        private const val DRAFT_CONTEXT_FIELD = "__ctx"
        private const val DRAFT_BASE_FIELD = "__base"
        private const val DRAFT_MODIFIED_FIELD = "__modified"
        private const val DRAFT_RESOLVING_FIELD = "__resolving"
        private const val DRAFT_RESOLVED_FIELD = "__resolved"
        private const val FROZEN_MESSAGE = "The current draft has been resolved so it cannot be modified"

        private val DRAFT_CONSUMER = ClassName.get(DraftConsumer::class.java)
        private val LIST = ClassName.get(List::class.java)
        private val RUNTIME_IMMUTABLE_TYPE = ClassName.get(org.babyfish.jimmer.meta.ImmutableType::class.java)
        private val JSON_IGNORE = ClassName.get("com.fasterxml.jackson.annotation", "JsonIgnore")
        private val JSON_PROPERTY_ORDER = ClassName.get("com.fasterxml.jackson.annotation", "JsonPropertyOrder")
    }
}
