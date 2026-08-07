package org.babyfish.jimmer.compiler.dto

import com.squareup.kotlinpoet.CodeBlock as KotlinCodeBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import org.babyfish.jimmer.compiler.render.apt.AptDtoPolymorphicMetadataConverterRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoPolymorphicMetadataConverterRenderer
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.dto.DtoGraph
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import org.babyfish.jimmer.dto.compiler.DtoPolymorphicBranchKind
import site.addzero.lsi.jimmer.dto.DtoPolymorphism
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class DtoPolymorphicMetadataConverterPoetTest {

    @Test
    fun `renders type branches in declaration order and falls back to default branch`() {
        val fixture = fixture(
            branch(DtoPolymorphicBranchKind.DEFAULT, "Default"),
            branch(DtoPolymorphicBranchKind.TYPE, "Organization", ORGANIZATION_TYPE_ID),
            branch(DtoPolymorphicBranchKind.TYPE, "Person", PERSON_TYPE_ID),
        )

        assertEquals(
            """
                base -> {
                  java.lang.Class<?> actualType = ((org.babyfish.jimmer.runtime.ImmutableSpi)base).__type().getJavaClass();
                  if (actualType == demo.Organization.class) {
                    return new demo.dto.ClientInput.Organization((demo.Organization)base);
                  }
                  if (actualType == demo.Person.class) {
                    return new demo.dto.ClientInput.Person((demo.Person)base);
                  }
                  return new demo.dto.ClientInput.Default(base);
                }
            """.trimIndent(),
            fixture.renderJava().trimEnd(),
        )
        assertEquals(
            """
                { base ->
                  val actualType = (base as org.babyfish.jimmer.runtime.ImmutableSpi).__type().javaClass
                  when (actualType) {
                    demo.Organization::class.java -> demo.dto.ClientInput.Organization(base as demo.Organization)
                    demo.Person::class.java -> demo.dto.ClientInput.Person(base as demo.Person)
                    else -> demo.dto.ClientInput.Default(base)
                  }
                }
            """.trimIndent(),
            fixture.renderKotlin(),
        )
    }

    @Test
    fun `renders platform-specific missing branch failure exactly`() {
        val fixture = fixture(
            branch(DtoPolymorphicBranchKind.TYPE, "Organization", ORGANIZATION_TYPE_ID),
            branch(DtoPolymorphicBranchKind.TYPE, "Person", PERSON_TYPE_ID),
        )

        assertEquals(
            """
                base -> {
                  java.lang.Class<?> actualType = ((org.babyfish.jimmer.runtime.ImmutableSpi)base).__type().getJavaClass();
                  if (actualType == demo.Organization.class) {
                    return new demo.dto.ClientInput.Organization((demo.Organization)base);
                  }
                  if (actualType == demo.Person.class) {
                    return new demo.dto.ClientInput.Person((demo.Person)base);
                  }
                  throw new java.lang.IllegalArgumentException("Cannot convert entity object to polymorphic DTO \"demo.dto.ClientInput\" because there is no branch for actual entity type \"" + actualType.getName() + "\"");
                }
            """.trimIndent(),
            fixture.renderJava().trimEnd(),
        )
        assertEquals(
            """
                { base ->
                  val actualType = (base as org.babyfish.jimmer.runtime.ImmutableSpi).__type().javaClass
                  when (actualType) {
                    demo.Organization::class.java -> demo.dto.ClientInput.Organization(base as demo.Organization)
                    demo.Person::class.java -> demo.dto.ClientInput.Person(base as demo.Person)
                    else -> throw java.lang.IllegalArgumentException("Cannot convert entity object to polymorphic DTO \"demo.dto.ClientInput\" because there is no branch for actual entity type \"" + actualType.name + "\"")
                  }
                }
            """.trimIndent(),
            fixture.renderKotlin(),
        )
    }

    private fun fixture(vararg branches: DtoPolymorphicBranch): Fixture {
        val rootType = dtoType(
            id = ROOT_TYPE_ID,
            name = "ClientInput",
            polymorphism = DtoPolymorphism(
                exhaustive = false,
                branches = branches.toList(),
            ),
        )
        val branchTypes = branches.map { branch ->
            dtoType(
                id = branch.bodyTypeId,
                name = null,
                polymorphism = null,
            )
        }
        return Fixture(
            rootType = rootType,
            graph = DtoGraph(
                source = SOURCE,
                rootTypeIds = listOf(ROOT_TYPE_ID),
                types = (branchTypes + rootType).sortedBy(DtoType::id),
                props = emptyList(),
            ),
        )
    }

    private fun branch(
        kind: DtoPolymorphicBranchKind,
        className: String,
        targetBaseTypeId: LsiSymbolId? = null,
    ): DtoPolymorphicBranch {
        val typeId = DtoTypeId("dto#branch-$className")
        return DtoPolymorphicBranch(
            kind = kind,
            targetBaseTypeId = targetBaseTypeId,
            declaredClassName = className,
            className = className,
            bodyTypeId = typeId,
            mergedTypeId = typeId,
            implicit = false,
            location = LOCATION,
        )
    }

    private fun dtoType(
        id: DtoTypeId,
        name: String?,
        polymorphism: DtoPolymorphism?,
    ): DtoType {
        return DtoType(
            id = id,
            baseTypeId = CLIENT_TYPE_ID,
            packageName = "demo.dto",
            name = name,
            modifiers = setOf(DtoModifier.INPUT),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
            polymorphism = polymorphism,
        )
    }

    private data class Fixture(
        val rootType: DtoType,
        val graph: DtoGraph,
    ) {
        fun renderJava(): String {
            return AptDtoPolymorphicMetadataConverterRenderer.render(
                dtoType = rootType,
                graph = graph,
                workspace = WORKSPACE,
                generatedPackageName = "demo.dto",
                generatedRootSimpleNames = listOf("ClientInput"),
            ).toString()
        }

        fun renderKotlin(): String {
            return KotlinCodeBlock.builder().apply {
                KspDtoPolymorphicMetadataConverterRenderer.appendTo(
                    builder = this,
                    dtoType = rootType,
                    graph = graph,
                    workspace = WORKSPACE,
                    generatedPackageName = "demo.dto",
                    generatedRootSimpleNames = listOf("ClientInput"),
                )
            }.build().toString()
        }
    }

    private companion object {
        val SOURCE = LsiSource.of("demo/Client.dto", LsiLanguage.UNKNOWN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val ROOT_TYPE_ID = DtoTypeId("dto#root")
        val CLIENT_TYPE_ID = LsiSymbolId.type("demo.Client")
        val ORGANIZATION_TYPE_ID = LsiSymbolId.type("demo.Organization")
        val PERSON_TYPE_ID = LsiSymbolId.type("demo.Person")
        val WORKSPACE = LsiWorkspace(
            sources = listOf(SOURCE),
            declarations = listOf(ORGANIZATION_TYPE_ID, PERSON_TYPE_ID).map { typeId ->
                LsiTypeDeclaration(
                    id = typeId,
                    name = typeId.requireTypeQualifiedName().substringAfterLast('.'),
                    qualifiedName = typeId.requireTypeQualifiedName(),
                    kind = LsiTypeDeclarationKind.INTERFACE,
                    modality = LsiModality.ABSTRACT,
                    origin = LsiOrigin(LsiOriginKind.SOURCE, SOURCE),
                )
            },
        )
    }
}
