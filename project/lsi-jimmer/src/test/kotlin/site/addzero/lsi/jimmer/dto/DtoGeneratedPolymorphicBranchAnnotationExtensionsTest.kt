package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType

class DtoGeneratedPolymorphicBranchAnnotationExtensionsTest {

    @Test
    fun `generates stable default and type branch annotations`() {
        val defaultBranch = branch(
            kind = DtoPolymorphicBranchKind.DEFAULT,
            className = "Default",
        )
        val typeBranch = branch(
            kind = DtoPolymorphicBranchKind.TYPE,
            className = "Person",
            targetBaseTypeId = PERSON_TYPE_ID,
        )
        val rootType = rootType(defaultBranch, typeBranch)

        val polymorphism = requireNotNull(rootType.polymorphism)
        val annotations = polymorphism.branches.map { branch ->
            branch.generatedPolymorphicDtoBranchAnnotation(rootType, GENERATED_ROOT_TYPE_ID)
        }

        assertEquals(listOf(0, 1), annotations.map { annotation ->
            assertIs<LsiAnnotationValue.IntValue>(
                annotation.arguments.getValue("order").value,
            ).value
        })
        annotations.forEach { annotation ->
            assertEquals(GENERATED_POLYMORPHIC_DTO_BRANCH_ANNOTATION, annotation.type)
            assertEquals(listOf("value", "order"), annotation.explicitArgumentNamesInSourceOrder)
            assertEquals(
                LsiAnnotationArgumentOrigin.EXPLICIT,
                annotation.arguments.getValue("value").origin,
            )
            assertEquals(
                LsiAnnotationArgumentOrigin.EXPLICIT,
                annotation.arguments.getValue("order").origin,
            )
            val classValue = assertIs<LsiAnnotationValue.ClassValue>(
                annotation.arguments.getValue("value").value,
            )
            assertEquals(
                GENERATED_ROOT_TYPE_ID,
                assertIs<LsiDeclaredType>(classValue.type).declarationId,
            )
        }
    }

    @Test
    fun `rejects branch from another polymorphic root`() {
        val rootType = rootType(
            branch(
                kind = DtoPolymorphicBranchKind.DEFAULT,
                className = "Default",
            )
        )
        val foreignBranch = branch(
            kind = DtoPolymorphicBranchKind.TYPE,
            className = "Foreign",
            targetBaseTypeId = FOREIGN_TYPE_ID,
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            foreignBranch.generatedPolymorphicDtoBranchAnnotation(rootType, GENERATED_ROOT_TYPE_ID)
        }

        assertTrue(exception.message.orEmpty().contains("branch", ignoreCase = true))
    }

    @Test
    fun `accepts structurally equal frozen branch snapshot`() {
        val branch = branch(
            kind = DtoPolymorphicBranchKind.DEFAULT,
            className = "Default",
        )
        val rootType = rootType(branch)
        val equalCopy = branch.copy()
        assertEquals(branch, equalCopy)

        val annotation = equalCopy.generatedPolymorphicDtoBranchAnnotation(
            rootType,
            GENERATED_ROOT_TYPE_ID,
        )

        assertEquals(
            0,
            assertIs<LsiAnnotationValue.IntValue>(
                annotation.arguments.getValue("order").value,
            ).value,
        )
    }

    @Test
    fun `rejects branch with different stable identity`() {
        val branch = branch(
            kind = DtoPolymorphicBranchKind.DEFAULT,
            className = "Default",
        )
        val rootType = rootType(branch)
        val foreignSnapshot = branch.copy(
            bodyTypeId = DtoTypeId("foreign#body"),
            mergedTypeId = DtoTypeId("foreign#merged"),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            foreignSnapshot.generatedPolymorphicDtoBranchAnnotation(rootType, GENERATED_ROOT_TYPE_ID)
        }

        assertTrue(exception.message.orEmpty().contains("stable id"))
    }

    @Test
    fun `rejects duplicate stable branch ids`() {
        val branch = branch(
            kind = DtoPolymorphicBranchKind.DEFAULT,
            className = "Default",
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            DtoPolymorphism(
                exhaustive = true,
                branches = listOf(
                    branch,
                    branch.copy(
                        kind = DtoPolymorphicBranchKind.TYPE,
                        targetBaseTypeId = PERSON_TYPE_ID,
                        className = "Person",
                    ),
                ),
            )
        }

        assertTrue(exception.message.orEmpty().contains("body type ids"))
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
        val FOREIGN_TYPE_ID = LsiSymbolId.type("foreign.Client")
        val GENERATED_ROOT_TYPE_ID = LsiSymbolId.type("demo.dto.ClientInput")
        val GENERATED_POLYMORPHIC_DTO_BRANCH_ANNOTATION =
            LsiSymbolId.type("org.babyfish.jimmer.internal.GeneratedPolymorphicDtoBranch")
    }
}
