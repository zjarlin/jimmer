package org.babyfish.jimmer.compiler.module.ksp

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.babyfish.jimmer.compiler.module.JimmerModulePlatform
import org.babyfish.jimmer.compiler.module.JimmerModuleSchema
import org.babyfish.jimmer.compiler.module.JimmerModuleSource
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.model.LsiWorkspace

class JimmerModuleKotlinRenderer {

    fun render(
        schema: JimmerModuleSchema,
        workspace: LsiWorkspace,
    ): List<GeneratedArtifact> {
        require(schema.platform == JimmerModulePlatform.KSP) {
            "Kotlin module renderer requires a KSP schema"
        }
        val module = schema.module ?: return emptyList()
        return listOf(render(module, workspace))
    }

    private fun render(
        module: JimmerModuleSource,
        workspace: LsiWorkspace,
    ): GeneratedArtifact {
        val moduleType = ClassName(module.packageName, module.simpleName)
        val content = FileSpec.builder(module.packageName, module.simpleName)
            .indent("    ")
            .addType(
                TypeSpec.classBuilder(module.simpleName)
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("ENTITY_MANAGER", ENTITY_MANAGER)
                    .initializer(module.initializer(moduleType))
                    .build()
            )
            .build()
            .toString()
        val originatingSymbols = module.dependencies.originatingTypeIds.toSet()
        return GeneratedArtifact.source(
            kind = ArtifactKind.KOTLIN_SOURCE,
            qualifiedName = moduleType.canonicalName,
            content = content,
            aggregationMode = module.dependencies.aggregationMode,
            originatingSymbols = originatingSymbols,
            originatingSources = workspace.originatingSources(originatingSymbols),
        )
    }
}

private fun JimmerModuleSource.initializer(moduleType: ClassName): CodeBlock {
    return CodeBlock.builder()
        .add("%T.fromResources(\n", ENTITY_MANAGER)
        .indent()
        .add("%T::class.java.classLoader\n", moduleType)
        .unindent()
        .add(")")
        .apply {
            entityNamePrefix?.let { prefix ->
                beginControlFlow("")
                add("it.name.startsWith(%S)\n", prefix)
                endControlFlow()
            }
        }
        .build()
}

private val ENTITY_MANAGER = ClassName("org.babyfish.jimmer.sql.runtime", "EntityManager")
