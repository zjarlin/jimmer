package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationMember
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace

class DtoAnnotationValueContractTest {

    @Test
    fun `freezes scalar literals into typed lsi annotation values`() {
        val annotationType = LsiSymbolId.type("demo.TypedValues")
        val members = listOf(
            member("booleanValue", primitive(LsiPrimitiveKind.BOOLEAN)),
            member("byteValue", primitive(LsiPrimitiveKind.BYTE)),
            member("charValue", primitive(LsiPrimitiveKind.CHAR)),
            member("doubleValue", primitive(LsiPrimitiveKind.DOUBLE)),
            member("floatValue", primitive(LsiPrimitiveKind.FLOAT)),
            member("intValue", primitive(LsiPrimitiveKind.INT)),
            member("longValue", primitive(LsiPrimitiveKind.LONG)),
            member("shortValue", primitive(LsiPrimitiveKind.SHORT)),
            member("stringValue", LsiDeclaredType(STRING_TYPE)),
        )
        val annotation = dtoAnnotation(
            annotationType,
            mapOf(
                "booleanValue" to "true",
                "byteValue" to "127",
                "charValue" to "'\\n'",
                "doubleValue" to "-2.5e3",
                "floatValue" to "1.25",
                "intValue" to "2147483647",
                "longValue" to "-9223372036854775808",
                "shortValue" to "-32768",
                "stringValue" to "\"line\\nquote:\\\" slash:\\\\ unicode:\\u4e2d\"",
            ),
        )

        val contract = resolve(annotationDeclaration(annotationType, members), annotation)
        val values = contract.singleAnnotation(annotationType).arguments.mapValues { (_, argument) ->
            argument.value
        }

        assertTrue(contract.diagnostics.isEmpty(), contract.diagnostics.joinToString { it.message })
        assertEquals(
            mapOf(
                "booleanValue" to LsiAnnotationValue.BooleanValue(true),
                "byteValue" to LsiAnnotationValue.ByteValue(127),
                "charValue" to LsiAnnotationValue.CharValue('\n'),
                "doubleValue" to LsiAnnotationValue.DoubleValue(-2.5e3),
                "floatValue" to LsiAnnotationValue.FloatValue(1.25f),
                "intValue" to LsiAnnotationValue.IntValue(Int.MAX_VALUE),
                "longValue" to LsiAnnotationValue.LongValue(Long.MIN_VALUE),
                "shortValue" to LsiAnnotationValue.ShortValue(Short.MIN_VALUE),
                "stringValue" to LsiAnnotationValue.StringValue(
                    "line\nquote:\" slash:\\ unicode:\u4e2d",
                ),
            ),
            values,
        )
        assertTrue(
            contract.singleAnnotation(annotationType).arguments.values.all { argument ->
                argument.origin == LsiAnnotationArgumentOrigin.EXPLICIT
            }
        )
    }

    @Test
    fun `normalizes a single literal into an annotation array`() {
        val annotationType = LsiSymbolId.type("demo.ArrayValue")
        val declaration = annotationDeclaration(
            annotationType,
            listOf(member("value", LsiArrayType(primitive(LsiPrimitiveKind.INT)))),
        )

        val contract = resolve(declaration, dtoAnnotation(annotationType, mapOf("value" to "7")))
        val value = contract.singleAnnotation(annotationType).arguments.getValue("value").value

        assertTrue(contract.diagnostics.isEmpty(), contract.diagnostics.joinToString { it.message })
        assertEquals(
            LsiAnnotationValue.ArrayValue(listOf(LsiAnnotationValue.IntValue(7))),
            value,
        )
    }

    @Test
    fun `reports annotation argument type for overflow and invalid literals`() {
        val annotationType = LsiSymbolId.type("demo.InvalidValues")
        val members = listOf(
            member("booleanValue", primitive(LsiPrimitiveKind.BOOLEAN)),
            member("byteValue", primitive(LsiPrimitiveKind.BYTE)),
            member("floatValue", primitive(LsiPrimitiveKind.FLOAT)),
            member("intValue", primitive(LsiPrimitiveKind.INT)),
            member("longValue", primitive(LsiPrimitiveKind.LONG)),
            member("stringValue", LsiDeclaredType(STRING_TYPE)),
        )
        val annotation = dtoAnnotation(
            annotationType,
            mapOf(
                "booleanValue" to "1",
                "byteValue" to "128",
                "floatValue" to "1e999",
                "intValue" to "2147483648",
                "longValue" to "9223372036854775808",
                "stringValue" to "not-quoted",
            ),
        )

        val contract = resolve(annotationDeclaration(annotationType, members), annotation)
        val typeDiagnostics = contract.diagnostics.filter { diagnostic ->
            diagnostic.code == ARGUMENT_TYPE_CODE
        }

        assertEquals(6, typeDiagnostics.size)
        assertEquals(
            members.mapTo(sortedSetOf(), LsiAnnotationMember::name),
            typeDiagnostics.mapTo(sortedSetOf()) { diagnostic -> diagnostic.details.getValue("argument") },
        )
        assertTrue(typeDiagnostics.all { diagnostic ->
            diagnostic.details["annotationType"] == annotationType.value
        })
    }

    @Test
    fun `keeps omitted annotation arguments absent`() {
        val requiredType = LsiSymbolId.type("demo.RequiredValue")
        val defaultType = LsiSymbolId.type("demo.DefaultValue")
        val varargType = LsiSymbolId.type("demo.VarargValue")
        val declarations = listOf(
            annotationDeclaration(
                requiredType,
                listOf(member("value", primitive(LsiPrimitiveKind.INT))),
            ),
            annotationDeclaration(
                defaultType,
                listOf(member("value", primitive(LsiPrimitiveKind.INT), hasDefault = true)),
            ),
            annotationDeclaration(
                type = varargType,
                members = listOf(
                    member(
                        name = "value",
                        type = LsiArrayType(LsiDeclaredType(STRING_TYPE)),
                        vararg = true,
                    )
                ),
                language = LsiLanguage.KOTLIN,
            ),
        )

        val contract = resolve(
            declarations = declarations,
            annotations = listOf(
                DtoAnnotation(requiredType, emptyList()),
                DtoAnnotation(defaultType, emptyList()),
                DtoAnnotation(varargType, emptyList()),
            ),
        )

        assertTrue(contract.diagnostics.isEmpty(), contract.diagnostics.joinToString { it.message })
        assertEquals(
            setOf(requiredType, defaultType, varargType),
            contract.typePlans.single().applications.mapTo(hashSetOf()) { application ->
                application.annotation.type
            },
        )
        assertTrue(contract.typePlans.single().applications.all { application ->
            application.annotation.arguments.isEmpty()
        })
    }

    private fun resolve(
        declaration: LsiTypeDeclaration,
        annotation: DtoAnnotation,
    ): DtoAnnotationContract {
        return resolve(listOf(declaration), listOf(annotation))
    }

    private fun resolve(
        declarations: List<LsiTypeDeclaration>,
        annotations: List<DtoAnnotation>,
    ): DtoAnnotationContract {
        val source = LsiSource.of("demo/Typed.dto")
        val type = DtoType(
            id = DTO_TYPE,
            baseTypeId = null,
            packageName = "demo",
            name = "TypedDto",
            modifiers = emptySet(),
            annotations = annotations,
            superInterfaces = emptyList(),
            documentation = null,
            location = LsiLocation(source, LsiPosition(1, 1)),
            focusedRecursion = false,
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        val graph = DtoGraph(
            source = source,
            rootTypeIds = listOf(DTO_TYPE),
            types = listOf(type),
            props = emptyList(),
        )
        val workspace = LsiWorkspace(declarations = declarations)
        return workspace.resolveDtoAnnotationContract(graph, ImmutableSchema(emptyList()))
    }

    private fun annotationDeclaration(
        type: LsiSymbolId,
        members: List<LsiAnnotationMember>,
        language: LsiLanguage = LsiLanguage.JAVA,
    ): LsiTypeDeclaration {
        val qualifiedName = type.requireTypeQualifiedName()
        val extension = if (language == LsiLanguage.KOTLIN) "kt" else "java"
        val source = LsiSource.of(
            path = qualifiedName.replace('.', '/') + ".$extension",
            language = language,
        )
        return LsiTypeDeclaration(
            id = type,
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.ANNOTATION,
            modality = LsiModality.FINAL,
            annotationMembers = members.sortedBy(LsiAnnotationMember::name),
            origin = LsiOrigin(
                kind = LsiOriginKind.SOURCE,
                source = source,
                language = language,
            ),
        )
    }

    private fun member(
        name: String,
        type: LsiTypeRef,
        vararg: Boolean = false,
        hasDefault: Boolean = false,
    ): LsiAnnotationMember {
        return LsiAnnotationMember(
            name = name,
            type = type,
            vararg = vararg,
            hasDefault = hasDefault,
        )
    }

    private fun dtoAnnotation(
        type: LsiSymbolId,
        literals: Map<String, String>,
    ): DtoAnnotation {
        return DtoAnnotation(
            typeId = type,
            arguments = literals.entries.map { (name, code) ->
                DtoAnnotationArgument(name, DtoAnnotationValue.LiteralValue(code))
            },
        )
    }

    private fun primitive(kind: LsiPrimitiveKind): LsiPrimitiveType = LsiPrimitiveType(kind)

    private fun DtoAnnotationContract.singleAnnotation(type: LsiSymbolId): LsiAnnotation {
        return typePlans.single().applications
            .single { application -> application.annotation.type == type }
            .annotation
    }

    private companion object {
        const val ARGUMENT_TYPE_CODE = "jimmer.dto.annotation.argument-type"

        val DTO_TYPE = DtoTypeId("demo/Typed.dto#root")
        val STRING_TYPE = LsiSymbolId.type("java.lang.String")
    }
}
