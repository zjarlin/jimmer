package site.addzero.lsi.poet.kotlinpoet

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.poet.LsiPoetArtifact
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationValue
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetConstructor
import site.addzero.lsi.poet.LsiPoetField
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetMember
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetParameter
import site.addzero.lsi.poet.LsiPoetProperty
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.poet.LsiPoetTypeKind
import site.addzero.lsi.poet.LsiPoetFile

class LsiKotlinPoetRendererTest {

    private val stringType = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))

    @Test
    fun `renders a Kotlin class through a GeneratedArtifact boundary`() {
        val type = LsiPoetType(
            name = "Greeting",
            kind = LsiPoetTypeKind.CLASS,
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            primaryConstructor = LsiPoetConstructor(
                parameters = listOf(LsiPoetParameter("name", stringType)),
            ),
            members = listOf(
                LsiPoetProperty(
                    name = "name",
                    type = stringType,
                    mutable = false,
                    modifiers = setOf(LsiPoetModifier.PRIVATE),
                    initializer = LsiPoetCodeBlock.build { name("name") },
                ),
                LsiPoetFunction(
                    name = "message",
                    modifiers = setOf(LsiPoetModifier.PUBLIC),
                    returnType = stringType,
                    body = LsiPoetCodeBlock.build {
                        text("return ")
                        string("Hello ")
                        text(" + ")
                        name("name")
                        line()
                    },
                ),
            ),
        )
        val artifact = artifact(type, "Greeting")

        val generated = LsiKotlinPoetRenderer().render(artifact)

        assertEquals(GeneratedArtifact::class.java, LsiKotlinPoetRenderer::class.java
            .getDeclaredMethod("render", LsiPoetArtifact::class.java).returnType)
        assertPublicApiDoesNotExposePoet(LsiKotlinPoetRenderer::class.java)
        assertEquals("demo/generated/Greeting.kt", generated.path)
        assertEquals(
            """
                package demo.generated

                import kotlin.String

                public class Greeting(
                    private val name: String,
                ) {
                    public fun message(): String {
                        return "Hello " + name
                    }
                }
            """.trimIndent(),
            generated.content.trimIndent(),
        )
    }

    @Test
    fun `rejects Java fields and unresolved types`() {
        val fieldType = LsiPoetType(
            name = "FieldHolder",
            kind = LsiPoetTypeKind.CLASS,
            members = listOf(
                LsiPoetField("name", stringType),
            ),
        )
        assertFailsWith<IllegalStateException> {
            LsiKotlinPoetRenderer().render(artifact(fieldType, "FieldHolder"))
        }

        val unresolvedType = LsiPoetType(
            name = "Broken",
            kind = LsiPoetTypeKind.CLASS,
            members = listOf(
                LsiPoetFunction(
                    name = "value",
                    returnType = LsiUnresolvedType("Missing"),
                )
            ),
        )
        val exception = assertFailsWith<IllegalStateException> {
            LsiKotlinPoetRenderer().render(artifact(unresolvedType, "Broken"))
        }
        assertTrue(exception.message.orEmpty().contains("unresolved"))
    }

    @Test
    fun `renders positional file suppression without a member name`() {
        val type = LsiPoetType(
            name = "Suppressed",
            kind = LsiPoetTypeKind.CLASS,
        )
        val artifact = LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.KOTLIN,
                packageName = "demo.generated",
                fileName = "Suppressed",
                annotations = listOf(
                    LsiPoetAnnotation(
                        type = LsiSymbolId.type("kotlin.Suppress"),
                        arguments = listOf(
                            LsiPoetAnnotationArgument.Positional(
                                LsiPoetAnnotationValue.StringValue("warnings")
                            )
                        ),
                        useSiteTarget = LsiAnnotationUseSiteTarget.FILE,
                    )
                ),
                members = listOf(type),
            ),
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )

        val generated = LsiKotlinPoetRenderer().render(artifact)

        assertContains(generated.content, "@file:Suppress(\"warnings\")")
        assertFalse("value =" in generated.content)
    }

    @Test
    fun `renders nested source annotation and core type annotation`() {
        val nested = LsiPoetAnnotation(
            type = LsiSymbolId.type("demo.annotation.Nested"),
            arguments = listOf(
                LsiPoetAnnotationArgument.Positional(LsiPoetAnnotationValue.StringValue("inside"))
            ),
        )
        val annotatedType = stringType.copy(
            annotations = listOf(LsiAnnotation(LsiSymbolId.type("demo.annotation.TypeMarker")))
        )
        val type = LsiPoetType(
            name = "NestedAnnotation",
            kind = LsiPoetTypeKind.CLASS,
            annotations = listOf(
                LsiPoetAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Container"),
                    arguments = listOf(
                        LsiPoetAnnotationArgument.Named(
                            name = "nested",
                            value = LsiPoetAnnotationValue.NestedAnnotationValue(nested),
                        )
                    ),
                )
            ),
            members = listOf(LsiPoetFunction(name = "value", returnType = annotatedType)),
        )

        val generated = LsiKotlinPoetRenderer().render(artifact(type, "NestedAnnotation"))

        assertContains(generated.content, "@Container(nested = Nested(\"inside\"))")
        assertFalse("@Nested(\"inside\")" in generated.content)
        assertContains(generated.content, "fun `value`(): @TypeMarker String")
    }

    @Test
    fun `renders constructor throws vararg override and structural control flow`() {
        val exceptionType = LsiDeclaredType(LsiSymbolId.type("java.io.IOException"))
        val type = LsiPoetType(
            name = "Service",
            kind = LsiPoetTypeKind.CLASS,
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            primaryConstructor = LsiPoetConstructor(
                parameters = listOf(
                    LsiPoetParameter(
                        name = "values",
                        type = stringType,
                        modifiers = setOf(LsiPoetModifier.VARARG),
                    )
                ),
                thrownTypes = listOf(exceptionType),
            ),
            members = listOf(
                LsiPoetFunction(
                    name = "consume",
                    modifiers = setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.OVERRIDE),
                    returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
                    body = LsiPoetCodeBlock.build {
                        beginControlFlow { text("if (values.isEmpty())") }
                        statement { text("return") }
                        endControlFlow()
                    },
                )
            ),
        )

        val content = LsiKotlinPoetRenderer().render(artifact(type, "Service")).content

        assertTrue("@Throws(IOException::class)" in content, content)
        assertTrue("vararg values: String" in content, content)
        assertTrue("public override fun consume()" in content, content)
        assertTrue("if (values.isEmpty()) {\n            return\n        }" in content, content)
    }

    @Test
    fun `renders structural return as a Kotlin expression body`() {
        val type = LsiPoetType(
            name = "Returns",
            kind = LsiPoetTypeKind.CLASS,
            members = listOf(
                LsiPoetFunction(
                    name = "message",
                    returnType = stringType,
                    body = LsiPoetCodeBlock.build {
                        returnValue { string("ok") }
                    },
                )
            ),
        )

        val content = LsiKotlinPoetRenderer().render(artifact(type, "Returns")).content

        assertContains(content, "public fun message(): String = \"ok\"")
    }

    @Test
    fun `renders a returned braced expression as a Kotlin expression body`() {
        val type = LsiPoetType(
            name = "ReturnsBlock",
            kind = LsiPoetTypeKind.CLASS,
            members = listOf(
                LsiPoetFunction(
                    name = "message",
                    returnType = stringType,
                    body = LsiPoetCodeBlock.build {
                        returnBracedExpression(
                            prefix = { text("call()") },
                            body = { statement { string("ok") } },
                        )
                    },
                )
            ),
        )

        val content = LsiKotlinPoetRenderer().render(artifact(type, "ReturnsBlock")).content

        assertContains(content, "public fun message(): String = call() {")
    }

    private fun artifact(type: LsiPoetType, fileName: String): LsiPoetArtifact {
        return LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.KOTLIN,
                packageName = "demo.generated",
                fileName = fileName,
                members = listOf(type),
            ),
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )
    }

    private fun assertPublicApiDoesNotExposePoet(type: Class<*>) {
        val methodTypes = type.declaredMethods
            .filter { method -> java.lang.reflect.Modifier.isPublic(method.modifiers) }
            .flatMap { method -> listOf(method.returnType) + method.parameterTypes }
        val constructorTypes = type.declaredConstructors
            .filter { constructor -> java.lang.reflect.Modifier.isPublic(constructor.modifiers) }
            .flatMap { constructor -> constructor.parameterTypes.toList() }
        val fieldTypes = type.declaredFields
            .filter { field -> java.lang.reflect.Modifier.isPublic(field.modifiers) }
            .map { field -> field.type }
        val exposedTypes = methodTypes + constructorTypes + fieldTypes

        assertTrue(exposedTypes.none { exposedType -> exposedType.name.startsWith("com.squareup.") })
    }
}
