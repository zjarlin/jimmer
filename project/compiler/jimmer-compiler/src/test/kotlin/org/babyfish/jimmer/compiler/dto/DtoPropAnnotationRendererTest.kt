package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.render.apt.AptDtoPropAnnotationRenderer
import org.babyfish.jimmer.compiler.render.ksp.KspDtoPropAnnotationRenderer
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoAnnotation
import site.addzero.lsi.jimmer.dto.DtoAnnotationApplication
import site.addzero.lsi.jimmer.dto.DtoAnnotationArgument
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoAnnotationDeclaration
import site.addzero.lsi.jimmer.dto.DtoAnnotationOrigin
import site.addzero.lsi.jimmer.dto.DtoAnnotationPlacement
import site.addzero.lsi.jimmer.dto.DtoAnnotationValue
import site.addzero.lsi.jimmer.dto.DtoPropAnnotationPlan
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoTypeRef
import site.addzero.lsi.jimmer.dto.DtoUserProp
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationMember
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class DtoPropAnnotationRendererTest {

    @Test
    fun `renders repeated property annotations in contract order for apt and ksp shapes`() {
        val dtoProp = dtoProp()
        val contract = annotationContract()
        val workspace = workspace()
        val schema = ImmutableSchema(emptyList())

        val aptFields = AptDtoPropAnnotationRenderer.renderField(
            dtoProp,
            contract,
            schema,
            workspace,
            null,
        )
        val aptGetters = AptDtoPropAnnotationRenderer.renderGetter(
            dtoProp,
            contract,
            schema,
            workspace,
            null,
        )
        val kspConcrete = KspDtoPropAnnotationRenderer.renderConcrete(
            dtoProp,
            contract,
            schema,
            workspace,
            null,
        )
        val kspAbstract = KspDtoPropAnnotationRenderer.renderAbstractAccessor(
            dtoProp,
            contract,
            schema,
            workspace,
        )

        assertEquals(2, aptFields.size)
        assertEquals(aptFields.map(Any::toString), aptGetters.map(Any::toString))
        assertEquals(listOf("first", "second"), aptFields.map(::annotationValue))
        assertEquals(listOf("first", "second"), kspConcrete.map(::annotationValue))
        assertEquals(listOf("first", "second"), kspAbstract.map(::annotationValue))
        assertTrue(kspConcrete.all { annotation -> annotation.useSiteTarget?.name == "FIELD" })
        assertTrue(kspAbstract.all { annotation -> annotation.useSiteTarget?.name == "GET" })
        (aptFields + kspConcrete + kspAbstract).forEach { annotation ->
            val source = annotation.toString()
            assertTrue(source.indexOf("order") < source.indexOf("value"), source)
        }
    }

    private fun dtoProp(): DtoUserProp {
        return DtoUserProp(
            id = PROP_ID,
            ownerTypeId = TYPE_ID,
            name = "value",
            alias = "value",
            nullable = false,
            annotations = listOf(sourceAnnotation(1, "first"), sourceAnnotation(2, "second")),
            documentation = null,
            aliasLocation = LOCATION,
            type = DtoTypeRef("java.lang.String", emptyList(), false, LOCATION),
            defaultValueText = null,
        )
    }

    private fun sourceAnnotation(order: Int, value: String): DtoAnnotation {
        return DtoAnnotation(
            typeId = ANNOTATION_ID,
            arguments = listOf(
                DtoAnnotationArgument("order", DtoAnnotationValue.LiteralValue(order.toString())),
                DtoAnnotationArgument("value", DtoAnnotationValue.LiteralValue("\"$value\"")),
            ),
        )
    }

    private fun annotationContract(): DtoAnnotationContract {
        return DtoAnnotationContract(
            declarations = listOf(
                DtoAnnotationDeclaration(
                    typeId = ANNOTATION_ID,
                    language = LsiLanguage.JAVA,
                    targetDeclared = true,
                    allowedPlacements = PLACEMENTS,
                    argumentTypes = sortedMapOf(
                        "order" to LsiPrimitiveType(LsiPrimitiveKind.INT),
                        "value" to LsiDeclaredType(STRING_ID),
                    ),
                    kotlinValueVararg = false,
                    argumentNamesInDeclarationOrder = listOf("order", "value"),
                ),
            ),
            typePlans = emptyList(),
            propPlans = listOf(
                DtoPropAnnotationPlan(
                    propId = PROP_ID,
                    propertyApplications = listOf(
                        application(1, "first"),
                        application(2, "second"),
                    ),
                    builderSetterApplications = emptyList(),
                ),
            ),
            diagnostics = emptyList(),
        )
    }

    private fun application(order: Int, value: String): DtoAnnotationApplication {
        return DtoAnnotationApplication(
            annotation = LsiAnnotation(
                type = ANNOTATION_ID,
                arguments = sortedMapOf(
                    "order" to explicit(LsiAnnotationValue.IntValue(order)),
                    "value" to explicit(LsiAnnotationValue.StringValue(value)),
                ),
            ),
            origin = DtoAnnotationOrigin.DTO,
            sourceSymbolId = null,
            placements = PLACEMENTS,
        )
    }

    private fun explicit(value: LsiAnnotationValue): LsiAnnotationArgument {
        return LsiAnnotationArgument(value, LsiAnnotationArgumentOrigin.EXPLICIT)
    }

    private fun workspace(): LsiWorkspace {
        return LsiWorkspace(
            declarations = listOf(
                LsiTypeDeclaration(
                    id = ANNOTATION_ID,
                    name = "PropMarker",
                    qualifiedName = "demo.PropMarker",
                    kind = LsiTypeDeclarationKind.ANNOTATION,
                    annotationMembers = listOf(
                        LsiAnnotationMember("order", LsiPrimitiveType(LsiPrimitiveKind.INT), declarationIndex = 0),
                        LsiAnnotationMember("value", LsiDeclaredType(STRING_ID), declarationIndex = 1),
                    ),
                    origin = LsiOrigin(LsiOriginKind.BINARY, language = LsiLanguage.JAVA),
                ),
            ),
        )
    }

    private fun annotationValue(annotation: Any): String {
        val source = annotation.toString()
        return when {
            "\"first\"" in source -> "first"
            "\"second\"" in source -> "second"
            else -> error("Missing annotation value: $source")
        }
    }

    private companion object {
        val TYPE_ID = DtoTypeId("dto#sample")
        val PROP_ID = DtoPropId("dto#sample#value")
        val ANNOTATION_ID = LsiSymbolId.type("demo.PropMarker")
        val STRING_ID = LsiSymbolId.type("java.lang.String")
        val PLACEMENTS = listOf(DtoAnnotationPlacement.FIELD, DtoAnnotationPlacement.GETTER)
        val LOCATION = LsiLocation(
            source = LsiSource.of("demo/Sample.dto"),
            start = LsiPosition(1, 1),
        )
    }
}
