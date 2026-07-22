package org.babyfish.jimmer.compiler.module

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetArtifact
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetField
import site.addzero.lsi.poet.LsiPoetFile
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetParameter
import site.addzero.lsi.poet.LsiPoetProperty
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.poet.LsiPoetTypeKind

internal fun JimmerModuleSchema.toLsiPoetArtifacts(
    workspace: LsiWorkspace,
): List<LsiPoetArtifact> {
    return when (platform) {
        JimmerModulePlatform.APT -> summaries.map { summary -> summary.toLsiPoet(workspace) }
        JimmerModulePlatform.KSP -> module?.let { source -> listOf(source.toLsiPoet(workspace)) }.orEmpty()
    }
}

private fun JimmerModuleSummary.toLsiPoet(workspace: LsiWorkspace): LsiPoetArtifact {
    val typeMembers = when (kind) {
        JimmerModuleSummaryKind.IMMUTABLES -> members.flatMap { member ->
            listOf(
                member.toCreator(withBase = false),
                member.toCreator(withBase = true),
            )
        }
        JimmerModuleSummaryKind.TABLES -> members.map { member -> member.toSingletonField("Table") }
        JimmerModuleSummaryKind.TABLE_EXES -> members.map { member -> member.toSingletonField("TableEx") }
        JimmerModuleSummaryKind.FETCHERS -> members.map { member -> member.toSingletonField("Fetcher") }
    }
    val originatingSymbols = dependencies.originatingTypeIds.toSet()
    return LsiPoetArtifact(
        file = LsiPoetFile(
            language = LsiLanguage.JAVA,
            packageName = packageName,
            fileName = simpleName,
            members = listOf(
                LsiPoetType(
                    name = simpleName,
                    kind = LsiPoetTypeKind.INTERFACE,
                    annotations = listOf(LsiAnnotation(GENERATED_BY_ID)),
                    modifiers = setOf(LsiPoetModifier.PUBLIC),
                    members = typeMembers,
                )
            ),
        ),
        aggregationMode = dependencies.aggregationMode,
        originatingSymbols = originatingSymbols,
        originatingSources = workspace.originatingSources(originatingSymbols),
    )
}

private fun JimmerModuleSummaryMember.toCreator(withBase: Boolean): LsiPoetFunction {
    val immutableType = LsiDeclaredType(typeId)
    val draftType = LsiDeclaredType(LsiSymbolId.type(qualifiedTypeName + "Draft"))
    val parameters = buildList {
        if (withBase) {
            add(LsiPoetParameter("base", immutableType))
        }
        add(
            LsiPoetParameter(
                name = "block",
                type = LsiDeclaredType(
                    declarationId = DRAFT_CONSUMER_ID,
                    arguments = listOf(LsiTypeArgument.invariant(draftType)),
                ),
            )
        )
    }
    return LsiPoetFunction(
        name = generatedName,
        modifiers = setOf(
            LsiPoetModifier.PUBLIC,
            LsiPoetModifier.STATIC,
        ),
        parameters = parameters,
        returnType = immutableType,
        body = LsiPoetCodeBlock.build {
            statement {
                text("return ")
                type(draftType)
                text(".$.produce(")
                if (withBase) {
                    name("base")
                    text(", ")
                }
                name("block")
                text(")")
            }
        },
    )
}

private fun JimmerModuleSummaryMember.toSingletonField(suffix: String): LsiPoetField {
    val singletonType = LsiDeclaredType(LsiSymbolId.type(qualifiedTypeName + suffix))
    return LsiPoetField(
        name = generatedName,
        type = singletonType,
        modifiers = setOf(
            LsiPoetModifier.PUBLIC,
            LsiPoetModifier.STATIC,
            LsiPoetModifier.FINAL,
        ),
        initializer = LsiPoetCodeBlock.build {
            type(singletonType)
            text(".$")
        },
    )
}

private fun JimmerModuleSource.toLsiPoet(workspace: LsiWorkspace): LsiPoetArtifact {
    val moduleType = LsiDeclaredType(LsiSymbolId.type(qualifiedName))
    val originatingSymbols = dependencies.originatingTypeIds.toSet()
    return LsiPoetArtifact(
        file = LsiPoetFile(
            language = LsiLanguage.KOTLIN,
            packageName = packageName,
            fileName = simpleName,
            members = listOf(
                LsiPoetType(
                    name = simpleName,
                    kind = LsiPoetTypeKind.CLASS,
                    modifiers = setOf(LsiPoetModifier.PRIVATE),
                ),
                LsiPoetProperty(
                    name = "ENTITY_MANAGER",
                    type = ENTITY_MANAGER_TYPE,
                    mutable = false,
                    initializer = initializer(moduleType),
                ),
            ),
        ),
        aggregationMode = dependencies.aggregationMode,
        originatingSymbols = originatingSymbols,
        originatingSources = workspace.originatingSources(originatingSymbols),
    )
}

private fun JimmerModuleSource.initializer(moduleType: LsiTypeRef): LsiPoetCodeBlock {
    return LsiPoetCodeBlock.build {
        type(ENTITY_MANAGER_TYPE)
        text(".fromResources(")
        line()
        indent {
            type(moduleType)
            text("::class.java.classLoader")
            line()
        }
        text(")")
        entityNamePrefix?.let { prefix ->
            beginControlFlow {}
            text("it.name.startsWith(")
            string(prefix)
            text(")")
            line()
            endControlFlow()
        }
    }
}

private val JimmerModuleSource.qualifiedName: String
    get() = if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"

private val GENERATED_BY_ID = LsiSymbolId.type("org.babyfish.jimmer.internal.GeneratedBy")
private val DRAFT_CONSUMER_ID = LsiSymbolId.type("org.babyfish.jimmer.DraftConsumer")
private val ENTITY_MANAGER_TYPE = LsiDeclaredType(
    LsiSymbolId.type("org.babyfish.jimmer.sql.runtime.EntityManager")
)
