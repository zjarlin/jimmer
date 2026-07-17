package org.babyfish.jimmer.compiler.module.apt

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier
import org.babyfish.jimmer.compiler.module.JimmerModulePlatform
import org.babyfish.jimmer.compiler.module.JimmerModuleSchema
import org.babyfish.jimmer.compiler.module.JimmerModuleSummary
import org.babyfish.jimmer.compiler.module.JimmerModuleSummaryKind
import org.babyfish.jimmer.compiler.module.JimmerModuleSummaryMember
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.model.LsiWorkspace

class JimmerModuleJavaRenderer {

    fun render(
        schema: JimmerModuleSchema,
        workspace: LsiWorkspace,
    ): List<GeneratedArtifact> {
        require(schema.platform == JimmerModulePlatform.APT) {
            "Java module renderer requires an APT schema"
        }
        return schema.summaries.map { summary -> render(summary, workspace) }
    }

    private fun render(
        summary: JimmerModuleSummary,
        workspace: LsiWorkspace,
    ): GeneratedArtifact {
        val type = TypeSpec.interfaceBuilder(ClassName.get(summary.packageName, summary.simpleName))
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(GENERATED_BY).build())
            .apply {
                when (summary.kind) {
                    JimmerModuleSummaryKind.IMMUTABLES -> summary.members.forEach { member ->
                        addMethod(member.creator(withBase = false))
                        addMethod(member.creator(withBase = true))
                    }
                    JimmerModuleSummaryKind.TABLES -> summary.members.forEach { member ->
                        addField(member.singletonField("Table"))
                    }
                    JimmerModuleSummaryKind.TABLE_EXES -> summary.members.forEach { member ->
                        addField(member.singletonField("TableEx"))
                    }
                    JimmerModuleSummaryKind.FETCHERS -> summary.members.forEach { member ->
                        addField(member.singletonField("Fetcher"))
                    }
                }
            }
            .build()
        val content = JavaFile.builder(summary.packageName, type)
            .indent("    ")
            .build()
            .toString()
        val originatingSymbols = summary.dependencies.originatingTypeIds.toSet()
        return GeneratedArtifact.source(
            kind = ArtifactKind.JAVA_SOURCE,
            qualifiedName = summary.qualifiedName,
            content = content,
            aggregationMode = summary.dependencies.aggregationMode,
            originatingSymbols = originatingSymbols,
            originatingSources = workspace.originatingSources(originatingSymbols),
        )
    }
}

private fun JimmerModuleSummaryMember.creator(withBase: Boolean): MethodSpec {
    val immutableType = ClassName.get(packageName, simpleTypeName)
    val draftType = ClassName.get(packageName, simpleTypeName + "Draft")
    return MethodSpec.methodBuilder(generatedName)
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .apply {
            if (withBase) {
                addParameter(immutableType, "base")
            }
        }
        .addParameter(ParameterizedTypeName.get(DRAFT_CONSUMER, draftType), "block")
        .returns(immutableType)
        .addStatement(
            if (withBase) {
                "return \$T.\$\$.produce(base, block)"
            } else {
                "return \$T.\$\$.produce(block)"
            },
            draftType,
        )
        .build()
}

private fun JimmerModuleSummaryMember.singletonField(suffix: String): FieldSpec {
    val singletonType = ClassName.get(packageName, simpleTypeName + suffix)
    return FieldSpec.builder(
        singletonType,
        generatedName,
        Modifier.PUBLIC,
        Modifier.STATIC,
        Modifier.FINAL,
    )
        .initializer("\$T.\$\$", singletonType)
        .build()
}

private val JimmerModuleSummary.qualifiedName: String
    get() = if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"

private val GENERATED_BY = ClassName.get("org.babyfish.jimmer.internal", "GeneratedBy")
private val DRAFT_CONSUMER = ClassName.get("org.babyfish.jimmer", "DraftConsumer")
