package site.addzero.lsi.jimmer.immutable.metadata.generator

import site.addzero.lsi.codegen.ENTITY_MANAGER_LSI_CLASS_NAME
import site.addzero.lsi.codegen.GeneratedResourceArtifact
import site.addzero.lsi.codegen.JIMMER_MODULE
import site.addzero.lsi.poet.LsiCallExpression
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.LsiLambdaExpression
import site.addzero.lsi.poet.LsiLambdaMode
import site.addzero.lsi.poet.LsiLiteralExpression
import site.addzero.lsi.poet.LsiModifier
import site.addzero.lsi.poet.LsiNameExpression
import site.addzero.lsi.poet.LsiNullExpression
import site.addzero.lsi.poet.LsiPropertySpec
import site.addzero.lsi.poet.LsiTypeExpression
import site.addzero.lsi.poet.LsiTypeSpec
import site.addzero.lsi.poet.LsiTypeSpecKind
import java.io.File
import java.io.FileReader

/**
 * immutable module 资源/源码生成器。
 *
 * 覆盖来源：
 * - `project/compiler/immutable/jimmer-ksp-immutable/.../JimmerModuleGenerator`
 *
 * 迁移说明：
 * - 输入保持纯字符串/文件值对象，不暴露 `ImmutableType` / `LsiClass`
 * - `JimmerModule` 改为直接产出 `LsiFileSpec`，消除 immutable shared 主链上的 raw source 例外
 */
class JimmerModuleMetadataGenerator(
    private val existingEntitiesResourceFile: File?,
    private val packageName: String,
    private val entityQualifiedNames: List<String>,
    private val isModuleRequired: Boolean
) {
    fun generateOutput(): ImmutableGeneratedOutput {
        val list = entityQualifiedNames
        if (list.isEmpty()) {
            return ImmutableGeneratedOutput(
                sourceFileSpecs = emptyList(),
                resourceArtifacts = emptyList(),
            )
        }
        val qualifiedNames = sortedSetOf<String>()
        if (existingEntitiesResourceFile != null && existingEntitiesResourceFile.exists()) {
            FileReader(existingEntitiesResourceFile).use {
                qualifiedNames += it.readLines()
            }
        }
        qualifiedNames += entityQualifiedNames
        return ImmutableGeneratedOutput(
            sourceFileSpecs =
                if (isModuleRequired) {
                    listOf(moduleFileSpec())
                } else {
                    emptyList()
                },
            resourceArtifacts = listOf(
                GeneratedResourceArtifact(
                    path = "META-INF/jimmer/entities",
                    content = qualifiedNames.joinToString(separator = "\n", postfix = "\n")
                )
            ),
        )
    }

    private fun moduleFileSpec(): LsiFileSpec =
        LsiFileSpec(
            packageName = packageName,
            name = JIMMER_MODULE,
            types = listOf(moduleTypeSpec())
        )

    private fun moduleTypeSpec(): LsiTypeSpec =
        LsiTypeSpec(
            name = JIMMER_MODULE,
            kind = LsiTypeSpecKind.CLASS,
            callables = listOf(
                LsiCallableSpec(
                    kind = LsiCallableSpecKind.CONSTRUCTOR,
                    primary = true,
                    modifiers = setOf(LsiModifier.PRIVATE)
                )
            ),
            properties = listOf(
                LsiPropertySpec(
                    name = "ENTITY_MANAGER",
                    type = ENTITY_MANAGER_LSI_CLASS_NAME,
                    modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC, LsiModifier.FINAL),
                    initializer = LsiCallExpression(
                        receiver = LsiTypeExpression(ENTITY_MANAGER_LSI_CLASS_NAME),
                        name = "fromResources",
                        arguments = listOf(
                            LsiNullExpression,
                            packagePredicateExpression()
                        )
                    )
                )
            )
        )

    private fun packagePredicateExpression() =
        packageName
            .takeIf { it.isNotEmpty() }
            ?.let { nonEmptyPackageName ->
                LsiLambdaExpression(
                    mode = LsiLambdaMode.EXPRESSION,
                    parameterNames = listOf("type"),
                    expression = LsiCallExpression(
                        receiver = LsiCallExpression(
                            receiver = LsiNameExpression("type"),
                            name = "getName",
                        ),
                        name = "startsWith",
                        arguments = listOf(LsiLiteralExpression("$nonEmptyPackageName."))
                    )
                )
            }
            ?: LsiNullExpression
}
