package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import org.babyfish.jimmer.dto.compiler.DtoPolymorphicBranchKind
import site.addzero.lsi.jimmer.dto.DtoPolymorphism
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiSourceAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentNameStyle
import site.addzero.lsi.model.LsiAnnotationValue

class DtoPolymorphicBranchPoetTest {

    @Test
    fun `lowers generated branch marker to exact neutral poet annotation`() {
        val defaultBranch = branch(
            kind = DtoPolymorphicBranchKind.DEFAULT,
            className = "Default",
        )
        val personBranch = branch(
            kind = DtoPolymorphicBranchKind.TYPE,
            className = "Person",
            targetBaseTypeId = PERSON_TYPE_ID,
        )
        val rootType = rootType(defaultBranch, personBranch)
        val generatedRootTypeName = JimmerDtoPoetTypeNames.create(
            packageName = "demo.dto",
            simpleNames = listOf("Envelope", "ClientInput"),
        )

        val annotation = personBranch.toGeneratedPolymorphicDtoBranchPoetAnnotation(
            rootType = rootType,
            generatedRootTypeName = generatedRootTypeName,
        )

        assertEquals(GENERATED_POLYMORPHIC_DTO_BRANCH_ANNOTATION, annotation.type)
        assertEquals(listOf("value", "order"), annotation.sourceArguments.map { argument ->
            assertIs<LsiSourceAnnotationArgument.Named>(argument).name
        })
        annotation.sourceArguments.forEach { argument ->
            assertEquals(
                LsiAnnotationArgumentNameStyle.VERBATIM,
                assertIs<LsiSourceAnnotationArgument.Named>(argument).nameStyle,
            )
        }
        val classValue = assertIs<LsiAnnotationValue.ClassValue>(
            assertIs<LsiSourceAnnotationArgument.Named>(annotation.sourceArguments[0]).value,
        )
        assertEquals(
            generatedRootTypeName.typeId,
            assertIs<LsiDeclaredType>(classValue.type).declarationId,
        )
        assertEquals(
            1,
            assertIs<LsiAnnotationValue.IntValue>(
                assertIs<LsiSourceAnnotationArgument.Named>(annotation.sourceArguments[1]).value,
            ).value,
        )
        assertEquals(
            listOf(GENERATED_POLYMORPHIC_DTO_BRANCH_ANNOTATION, generatedRootTypeName.typeId),
            polymorphicDtoBranchPoetTypeNames(generatedRootTypeName).map { typeName -> typeName.typeId },
        )
    }

    private fun rootType(
        vararg branches: DtoPolymorphicBranch,
    ): DtoType {
        return DtoType(
            id = ROOT_TYPE_ID,
            baseTypeId = BASE_TYPE_ID,
            packageName = "demo.dto",
            name = "ClientInput",
            modifiers = setOf(DtoModifier.INPUT),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
            polymorphism = DtoPolymorphism(
                exhaustive = true,
                branches = branches.toList(),
            ),
        )
    }

    private fun branch(
        kind: DtoPolymorphicBranchKind,
        className: String,
        targetBaseTypeId: LsiSymbolId? = null,
    ): DtoPolymorphicBranch {
        return DtoPolymorphicBranch(
            kind = kind,
            targetBaseTypeId = targetBaseTypeId,
            declaredClassName = null,
            className = className,
            bodyTypeId = DtoTypeId("dto#body-$className"),
            mergedTypeId = DtoTypeId("dto#merged-$className"),
            implicit = false,
            location = LOCATION,
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("demo/src/main/dto/Client.dto")
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))
        val ROOT_TYPE_ID = DtoTypeId("dto#root")
        val BASE_TYPE_ID = LsiSymbolId.type("demo.Client")
        val PERSON_TYPE_ID = LsiSymbolId.type("demo.Person")
        val GENERATED_POLYMORPHIC_DTO_BRANCH_ANNOTATION =
            LsiSymbolId.type("org.babyfish.jimmer.internal.GeneratedPolymorphicDtoBranch")
    }
}
