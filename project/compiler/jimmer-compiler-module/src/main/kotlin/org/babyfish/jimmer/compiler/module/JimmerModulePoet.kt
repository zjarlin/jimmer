package org.babyfish.jimmer.compiler.module

import site.addzero.lsi.anno.sourceLsiAnnotation

import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiTypeArgument
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.file.LsiFile
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.method.LsiParameter
import site.addzero.lsi.field.LsiProperty
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.referencedTypeIds
import site.addzero.lsi.clazz.toLsiClasses

internal fun JimmerModuleSchema.toLsiSourceArtifacts(
    workspace: LsiWorkspace,
): List<LsiSourceArtifact> {
    return when (platform) {
        CompilerPlatform.APT -> summaries.map { summary -> summary.toLsiPoet(workspace) }
        CompilerPlatform.KSP -> module?.let { source -> listOf(source.toLsiPoet(workspace)) }.orEmpty()
        CompilerPlatform.UNKNOWN -> error(
            "Jimmer module schema requires an APT or KSP platform"
        )
    }
}

private fun JimmerModuleSummary.toLsiPoet(workspace: LsiWorkspace): LsiSourceArtifact {
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
    val file = LsiFile(
        language = LsiLanguage.JAVA,
        packageName = packageName,
        fileName = simpleName,
        members = listOf(
            LsiClass(
                name = simpleName,
                kind = LsiTypeDeclarationKind.INTERFACE,
                annotations = listOf(sourceLsiAnnotation(GENERATED_BY_ID)),
                modifiers = setOf(LsiModifier.PUBLIC),
                members = typeMembers,
            )
        ),
    )
    return LsiSourceArtifact(
        file = file,
        typeNames = workspace.toLsiClasses(
            file.referencedTypeIds,
            additional = additionalTypeNames(),
        ),
        aggregationMode = dependencies.aggregationMode,
        originatingSymbols = originatingSymbols,
        originatingSources = workspace.originatingSources(originatingSymbols),
    )
}

private fun JimmerModuleSummary.additionalTypeNames(): List<LsiClass> {
    return buildList {
        add(LsiClass(GENERATED_BY_ID, "org.babyfish.jimmer.internal", listOf("GeneratedBy")))
        add(LsiClass(DRAFT_CONSUMER_ID, "org.babyfish.jimmer", listOf("DraftConsumer")))
        members.forEach { member ->
            add(
                LsiClass(
                    typeId = LsiSymbolId.type(member.qualifiedTypeName + "Draft"),
                    packageName = member.packageName,
                    simpleNames = listOf(member.simpleTypeName + "Draft"),
                )
            )
            listOf("Table", "TableEx", "Fetcher").forEach { suffix ->
                add(
                    LsiClass(
                        typeId = LsiSymbolId.type(member.qualifiedTypeName + suffix),
                        packageName = member.packageName,
                        simpleNames = listOf(member.simpleTypeName + suffix),
                    )
                )
            }
        }
    }
}

private fun JimmerModuleSummaryMember.toCreator(withBase: Boolean): LsiMethod {
    val immutableType = LsiDeclaredType(typeId)
    val draftType = LsiDeclaredType(LsiSymbolId.type(qualifiedTypeName + "Draft"))
    val parameters = buildList {
        if (withBase) {
            add(LsiParameter("base", immutableType))
        }
        add(
            LsiParameter(
                name = "block",
                type = LsiDeclaredType(
                    declarationId = DRAFT_CONSUMER_ID,
                    arguments = listOf(LsiTypeArgument.invariant(draftType)),
                ),
            )
        )
    }
    return LsiMethod(
        name = generatedName,
        modifiers = setOf(
            LsiModifier.PUBLIC,
            LsiModifier.STATIC,
        ),
        parameters = parameters,
        returnType = immutableType,
        body = LsiCodeBlock.build {
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

private fun JimmerModuleSummaryMember.toSingletonField(suffix: String): LsiField {
    val singletonType = LsiDeclaredType(LsiSymbolId.type(qualifiedTypeName + suffix))
    return LsiField(
        name = generatedName,
        type = singletonType,
        modifiers = setOf(
            LsiModifier.PUBLIC,
            LsiModifier.STATIC,
            LsiModifier.FINAL,
        ),
        initializer = LsiCodeBlock.build {
            type(singletonType)
            text(".$")
        },
    )
}

private fun JimmerModuleSource.toLsiPoet(workspace: LsiWorkspace): LsiSourceArtifact {
    val moduleType = LsiDeclaredType(LsiSymbolId.type(qualifiedName))
    val originatingSymbols = dependencies.originatingTypeIds.toSet()
    val file = LsiFile(
        language = LsiLanguage.KOTLIN,
        packageName = packageName,
        fileName = simpleName,
        members = listOf(
            LsiClass(
                name = simpleName,
                kind = LsiTypeDeclarationKind.CLASS,
                modifiers = setOf(LsiModifier.PRIVATE),
            ),
            LsiProperty(
                name = "ENTITY_MANAGER",
                type = ENTITY_MANAGER_TYPE,
                mutable = false,
                initializer = initializer(moduleType),
            ),
        ),
    )
    return LsiSourceArtifact(
        file = file,
        typeNames = workspace.toLsiClasses(
            file.referencedTypeIds,
            additional = listOf(
                LsiClass(
                    typeId = moduleType.declarationId,
                    packageName = packageName,
                    simpleNames = listOf(simpleName),
                ),
                LsiClass(
                    typeId = ENTITY_MANAGER_TYPE.declarationId,
                    packageName = "org.babyfish.jimmer.sql.runtime",
                    simpleNames = listOf("EntityManager"),
                ),
            ),
        ),
        aggregationMode = dependencies.aggregationMode,
        originatingSymbols = originatingSymbols,
        originatingSources = workspace.originatingSources(originatingSymbols),
    )
}

private fun JimmerModuleSource.initializer(moduleType: LsiType): LsiCodeBlock {
    return LsiCodeBlock.build {
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
