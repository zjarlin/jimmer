package site.addzero.lsi.poet.javapoet

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.poet.LsiPoetArtifact
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationValue
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetConstructor
import site.addzero.lsi.poet.LsiPoetField
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetImport
import site.addzero.lsi.poet.LsiPoetMember
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetNameStyle
import site.addzero.lsi.poet.LsiPoetParameter
import site.addzero.lsi.poet.LsiPoetProperty
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.poet.LsiPoetTypeKind
import site.addzero.lsi.poet.LsiPoetFile

class LsiJavaPoetRendererTest {

    private val stringType = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))

    @Test
    fun `renders a Java class through a GeneratedArtifact boundary`() {
        val type = LsiPoetType(
            name = "Greeting",
            kind = LsiPoetTypeKind.CLASS,
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            members = listOf(
                LsiPoetField(
                    name = "name",
                    type = stringType,
                    modifiers = setOf(LsiPoetModifier.PRIVATE, LsiPoetModifier.FINAL),
                ),
                LsiPoetConstructor(
                    modifiers = setOf(LsiPoetModifier.PUBLIC),
                    parameters = listOf(LsiPoetParameter("name", stringType)),
                    body = LsiPoetCodeBlock.build {
                        text("this.")
                        name("name")
                        text(" = ")
                        name("name")
                        text(";")
                        line()
                    },
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
                        text(";")
                        line()
                    },
                ),
            ),
        )
        val artifact = LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.JAVA,
                packageName = "demo.generated",
                fileName = "Greeting",
                members = listOf(type),
            ),
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
            originatingSources = setOf(LsiSource.of("demo/Source.java", LsiLanguage.JAVA)),
        )

        val generated = LsiJavaPoetRenderer().render(artifact)

        assertEquals(GeneratedArtifact::class.java, LsiJavaPoetRenderer::class.java
            .getDeclaredMethod("render", LsiPoetArtifact::class.java).returnType)
        assertPublicApiDoesNotExposePoet(LsiJavaPoetRenderer::class.java)
        assertEquals("demo/generated/Greeting.java", generated.path)
        assertEquals(
            """
                package demo.generated;

                import java.lang.String;

                public class Greeting {
                    private final String name;

                    public Greeting(String name) {
                        this.name = name;
                    }

                    public String message() {
                        return "Hello " + name;
                    }
                }
            """.trimIndent(),
            generated.content.trimIndent(),
        )
    }

    @Test
    fun `rejects Kotlin properties and unresolved types`() {
        val property = LsiPoetProperty(
            name = "name",
            type = stringType,
            mutable = false,
        )
        val propertyType = LsiPoetType(
            name = "PropertyHolder",
            kind = LsiPoetTypeKind.CLASS,
            members = listOf(property),
        )
        val propertyArtifact = artifact(propertyType, "PropertyHolder")
        assertFailsWith<IllegalStateException> {
            LsiJavaPoetRenderer().render(propertyArtifact)
        }

        val unresolvedType = LsiPoetType(
            name = "Broken",
            kind = LsiPoetTypeKind.CLASS,
            members = listOf(
                LsiPoetFunction(
                    name = "value",
                    returnType = site.addzero.lsi.model.LsiUnresolvedType("Missing"),
                )
            ),
        )
        val exception = assertFailsWith<IllegalStateException> {
            LsiJavaPoetRenderer().render(artifact(unresolvedType, "Broken"))
        }
        assertTrue(exception.message.orEmpty().contains("unresolved"))
    }

    @Test
    fun `renders a single Java positional argument as value`() {
        val type = LsiPoetType(
            name = "Annotated",
            kind = LsiPoetTypeKind.CLASS,
            annotations = listOf(
                LsiPoetAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Label"),
                    arguments = listOf(
                        LsiPoetAnnotationArgument.Positional(
                            LsiPoetAnnotationValue.StringValue("book")
                        )
                    ),
                )
            ),
        )

        val generated = LsiJavaPoetRenderer().render(artifact(type, "Annotated"))

        assertContains(generated.content, "@Label(\"book\")")
    }

    @Test
    fun `rejects Java positional and named argument combinations`() {
        val type = LsiPoetType(
            name = "InvalidAnnotation",
            kind = LsiPoetTypeKind.CLASS,
            annotations = listOf(
                LsiPoetAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Label"),
                    arguments = listOf(
                        LsiPoetAnnotationArgument.Positional(
                            LsiPoetAnnotationValue.StringValue("book")
                        ),
                        LsiPoetAnnotationArgument.Named(
                            name = "level",
                            value = LsiPoetAnnotationValue.StringValue("warning"),
                        ),
                    ),
                )
            ),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(type, "InvalidAnnotation"))
        }

        assertContains(exception.message.orEmpty(), "cannot combine positional and named")
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

        val generated = LsiJavaPoetRenderer().render(artifact(type, "NestedAnnotation"))

        assertContains(generated.content, "nested = @Nested(\"inside\")")
        assertContains(generated.content, "@TypeMarker String value()")
    }

    @Test
    fun `renders constructor contracts override vararg and structural control flow`() {
        val ownerId = LsiSymbolId.type("demo.generated.Service")
        val parameterId = LsiSymbolId.typeParameter(ownerId, "T")
        val exceptionType = LsiDeclaredType(LsiSymbolId.type("java.io.IOException"))
        val type = LsiPoetType(
            name = "Service",
            kind = LsiPoetTypeKind.CLASS,
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            members = listOf(
                LsiPoetConstructor(
                    modifiers = setOf(LsiPoetModifier.PUBLIC),
                    typeParameters = listOf(LsiTypeParameter(parameterId, "T")),
                    parameters = listOf(
                        LsiPoetParameter(
                            name = "values",
                            type = LsiTypeParameterRef(parameterId),
                            modifiers = setOf(LsiPoetModifier.VARARG),
                        )
                    ),
                    thrownTypes = listOf(exceptionType),
                ),
                LsiPoetFunction(
                    name = "consume",
                    modifiers = setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.OVERRIDE),
                    parameters = listOf(
                        LsiPoetParameter(
                            name = "values",
                            type = stringType,
                            modifiers = setOf(LsiPoetModifier.VARARG),
                        )
                    ),
                    returnType = LsiPrimitiveType(LsiPrimitiveKind.VOID),
                    body = LsiPoetCodeBlock.build {
                        beginControlFlow {
                            text("if (")
                            name("values")
                            text(".length == 0)")
                        }
                        statement { text("return") }
                        endControlFlow()
                    },
                ),
            ),
        )

        val content = LsiJavaPoetRenderer().render(artifact(type, "Service")).content

        assertTrue("public <T> Service(T... values) throws IOException" in content)
        assertTrue("@Override\n    public void consume(String... values)" in content)
        assertTrue("if (values.length == 0) {\n            return;\n        }" in content)
    }

    @Test
    fun `renders structural return with Java termination`() {
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

        val content = LsiJavaPoetRenderer().render(artifact(type, "Returns")).content

        assertContains(content, "return \"ok\";")
    }

    @Test
    fun `renders a returned braced expression with an inline suffix`() {
        val type = LsiPoetType(
            name = "ReturnsBlock",
            kind = LsiPoetTypeKind.CLASS,
            members = listOf(
                LsiPoetFunction(
                    name = "message",
                    returnType = stringType,
                    body = LsiPoetCodeBlock.build {
                        returnBracedExpression(
                            prefix = { text("call(") },
                            body = { statement { string("ok") } },
                            suffix = { text(")") },
                        )
                    },
                )
            ),
        )

        val content = LsiJavaPoetRenderer().render(artifact(type, "ReturnsBlock")).content

        assertContains(content, "return call( {\n            \"ok\";\n        });")
    }

    @Test
    fun `rejects Kotlin only declaration names and explicit imports`() {
        val escapedFunction = LsiPoetFunction(
            name = "children*",
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
        )
        val escapedException = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(escapedFunction, "Escaped"))
        }
        assertContains(escapedException.message.orEmpty(), "escaped Kotlin function name")

        val escapedType = LsiPoetType(
            name = "Order-ItemFetcherDsl",
            kind = LsiPoetTypeKind.CLASS,
            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
        )
        val escapedTypeException = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(
                artifact(
                    LsiPoetType(
                        name = "Escaped",
                        kind = LsiPoetTypeKind.CLASS,
                        members = listOf(escapedType),
                    ),
                    "Escaped",
                )
            )
        }
        assertContains(escapedTypeException.message.orEmpty(), "escaped Kotlin type name")

        val importedArtifact = LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.JAVA,
                packageName = "demo.generated",
                fileName = "Imported",
                imports = listOf(LsiPoetImport("demo.child", "by")),
                members = listOf(LsiPoetType("Imported", LsiPoetTypeKind.CLASS)),
            ),
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )
        val importException = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(importedArtifact)
        }
        assertContains(importException.message.orEmpty(), "explicit imports")
    }

    @Test
    fun `rejects reified type parameters`() {
        val parameterId = LsiSymbolId.typeParameter(
            LsiSymbolId.type("demo.generated.Reified"),
            "S",
        )
        val function = LsiPoetFunction(
            name = "query",
            modifiers = setOf(LsiPoetModifier.INLINE),
            typeParameters = listOf(LsiTypeParameter(parameterId, "S")),
            reifiedTypeParameterIds = setOf(parameterId),
            returnType = LsiTypeParameterRef(parameterId),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(function, "Reified"))
        }

        assertContains(exception.message.orEmpty(), "reified type parameters")
    }

    private fun artifact(member: LsiPoetMember, fileName: String): LsiPoetArtifact {
        val type = if (member is LsiPoetType) member else {
            LsiPoetType(fileName, LsiPoetTypeKind.CLASS, members = listOf(member))
        }
        return LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.JAVA,
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
