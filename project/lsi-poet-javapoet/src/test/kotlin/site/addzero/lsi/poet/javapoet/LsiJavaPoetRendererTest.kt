package site.addzero.lsi.poet.javapoet

import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.TypeSpec
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
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.poet.LsiPoetArtifact
import site.addzero.lsi.poet.LsiPoetBodyStyle
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationArgumentLayout
import site.addzero.lsi.poet.LsiPoetAnnotationArrayStyle
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
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.LsiPoetTypeReferenceStyle
import site.addzero.lsi.poet.LsiPoetFile

class LsiJavaPoetRendererTest {

    private val stringType = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))

    @Test
    fun `renders an embeddable Java code block exactly`() {
        val codeBlock = LsiPoetCodeBlock.build {
            statement {
                name("consume")
                text("(")
                type(stringType)
                text(", ")
                string("value")
                text(")")
            }
        }

        val rendered = LsiJavaPoetRenderer().renderCodeBlock(codeBlock, commonTypeNames)

        assertEquals(
            CodeBlock::class.java,
            LsiJavaPoetRenderer::class.java
                .getDeclaredMethod("renderCodeBlock", LsiPoetCodeBlock::class.java, List::class.java)
                .returnType,
        )
        assertEquals("consume(java.lang.String, \"value\");\n", rendered.toString())
    }

    @Test
    fun `renders an embeddable Java type structure exactly`() {
        val type = LsiPoetType(
            name = "Marker",
            kind = LsiPoetTypeKind.INTERFACE,
            modifiers = setOf(LsiPoetModifier.PUBLIC),
        )

        val rendered = LsiJavaPoetRenderer().renderType(type, emptyList())

        assertEquals(
            TypeSpec::class.java,
            LsiJavaPoetRenderer::class.java
                .getDeclaredMethod("renderType", LsiPoetType::class.java, List::class.java)
                .returnType,
        )
        assertEquals("public interface Marker {\n}\n", rendered.toString())
    }

    @Test
    fun `rejects package-relative type references without file context`() {
        val type = LsiPoetType(
            name = "Owner",
            kind = LsiPoetTypeKind.CLASS,
            members = listOf(
                LsiPoetField(
                    name = "nested",
                    type = LsiDeclaredType(LsiSymbolId.type("demo.generated.Owner.Nested")),
                    typeReferenceStyle = LsiPoetTypeReferenceStyle.SAME_PACKAGE_OUTER_QUALIFIED,
                ),
            ),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().renderType(
                type,
                listOf(
                    typeName(
                        LsiSymbolId.type("demo.generated.Owner.Nested"),
                        "demo.generated",
                        "Owner",
                        "Nested",
                    )
                ),
            )
        }

        assertContains(exception.message.orEmpty(), "requires file package context")
    }

    @Test
    fun `preserves package-relative nested type references in the default package`() {
        val type = LsiPoetType(
            name = "Owner",
            kind = LsiPoetTypeKind.CLASS,
            members = listOf(
                LsiPoetField(
                    name = "nested",
                    type = LsiDeclaredType(LsiSymbolId.type("Owner.Nested")),
                    typeReferenceStyle = LsiPoetTypeReferenceStyle.SAME_PACKAGE_OUTER_QUALIFIED,
                ),
                LsiPoetType(
                    name = "Nested",
                    kind = LsiPoetTypeKind.CLASS,
                    modifiers = setOf(LsiPoetModifier.STATIC),
                ),
            ),
        )

        val content = LsiJavaPoetRenderer().render(
            artifact(type, "Owner", packageName = "")
        ).content

        assertContains(content, "Nested nested;")
    }

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
            typeNames = commonTypeNames,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
            originatingSources = setOf(LsiSource.of("demo/Source.java", LsiLanguage.JAVA)),
        )

        val generated = LsiJavaPoetRenderer().render(artifact)

        assertEquals(GeneratedArtifact::class.java, LsiJavaPoetRenderer::class.java
            .getDeclaredMethod("render", LsiPoetArtifact::class.java).returnType)
        assertPublicApiDoesNotExposeOtherPoet(LsiJavaPoetRenderer::class.java)
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

        val escapedParameter = LsiPoetFunction(
            name = "consume",
            parameters = listOf(
                LsiPoetParameter(
                    name = "display-name",
                    type = stringType,
                    nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
                )
            ),
        )
        val escapedParameterException = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(escapedParameter, "EscapedParameter"))
        }
        assertContains(escapedParameterException.message.orEmpty(), "escaped Kotlin parameter name")

        val importedArtifact = LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.JAVA,
                packageName = "demo.generated",
                fileName = "Imported",
                imports = listOf(LsiPoetImport("demo.child", "by")),
                members = listOf(LsiPoetType("Imported", LsiPoetTypeKind.CLASS)),
            ),
            typeNames = emptyList(),
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )
        val importException = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(importedArtifact)
        }
        assertContains(importException.message.orEmpty(), "explicit imports")
    }

    @Test
    fun `rejects annotation array factory call source style`() {
        val type = LsiPoetType(
            name = "FactoryArray",
            kind = LsiPoetTypeKind.CLASS,
            annotations = listOf(
                LsiPoetAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Container"),
                    arguments = listOf(
                        LsiPoetAnnotationArgument.Named(
                            name = "groups",
                            value = LsiPoetAnnotationValue.ArrayValue(
                                elements = emptyList(),
                                sourceStyle = LsiPoetAnnotationArrayStyle.KOTLIN_ARRAY_OF,
                            ),
                        )
                    ),
                )
            ),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(type, "FactoryArray"))
        }

        assertContains(exception.message.orEmpty(), "annotation array factory call")
    }

    @Test
    fun `renders typed values in multiline annotation arrays`() {
        val type = LsiPoetType(
            name = "MultilineAnnotation",
            kind = LsiPoetTypeKind.CLASS,
            annotations = listOf(
                LsiPoetAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Container"),
                    arguments = listOf(
                        LsiPoetAnnotationArgument.Named(
                            name = "value",
                            value = LsiPoetAnnotationValue.ArrayValue(
                                elements = listOf(
                                    LsiPoetAnnotationValue.StringValue("dto-name")
                                ),
                                sourceStyle = LsiPoetAnnotationArrayStyle.MULTI_LINE_LITERAL,
                            ),
                        )
                    ),
                )
            ),
        )

        val content = LsiJavaPoetRenderer().render(artifact(type, "MultilineAnnotation")).content

        assertContains(
            content,
            """
                @Container({
                            "dto-name"
                        })
            """.trimIndent(),
        )
    }

    @Test
    fun `renders line separated annotation array elements without moving braces`() {
        val annotation = LsiPoetAnnotation(
            type = LsiSymbolId.type("demo.annotation.Container"),
            arguments = listOf(
                LsiPoetAnnotationArgument.Positional(
                    LsiPoetAnnotationValue.ArrayValue(
                        elements = listOf(
                            LsiPoetAnnotationValue.StringValue("first"),
                            LsiPoetAnnotationValue.StringValue("second"),
                        ),
                        sourceStyle = LsiPoetAnnotationArrayStyle.LINE_SEPARATED_LITERAL,
                    )
                )
            ),
        )

        val rendered = LsiJavaPoetRenderer().renderAnnotation(
            annotation,
            listOf(LsiPoetTypeName(annotation.type, "demo.annotation", listOf("Container"))),
        )

        assertEquals("@demo.annotation.Container({\"first\",\n    \"second\"})", rendered.toString())
    }

    @Test
    fun `renders compact multiline annotation array elements inside braces`() {
        val annotation = LsiPoetAnnotation(
            type = LsiSymbolId.type("demo.annotation.Container"),
            arguments = listOf(
                LsiPoetAnnotationArgument.Positional(
                    LsiPoetAnnotationValue.ArrayValue(
                        elements = listOf(
                            LsiPoetAnnotationValue.StringValue("first"),
                            LsiPoetAnnotationValue.StringValue("second"),
                        ),
                        sourceStyle = LsiPoetAnnotationArrayStyle.COMPACT_MULTI_LINE_LITERAL,
                    )
                )
            ),
        )

        val rendered = LsiJavaPoetRenderer().renderAnnotation(
            annotation,
            listOf(LsiPoetTypeName(annotation.type, "demo.annotation", listOf("Container"))),
        )

        assertEquals(
            "@demo.annotation.Container({\n      \"first\",\n      \"second\"\n    })",
            rendered.toString(),
        )
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

    @Test
    fun `renders fully qualified type references without symbol prefixes`() {
        val listType = LsiDeclaredType(
            declarationId = LsiSymbolId.type("java.util.List"),
            arguments = listOf(LsiTypeArgument.invariant(stringType)),
        )
        val function = LsiPoetFunction(
            name = "cast",
            modifiers = setOf(LsiPoetModifier.PUBLIC),
            parameters = listOf(LsiPoetParameter("value", LsiDeclaredType(LsiSymbolId.type("java.lang.Object")))),
            returnType = listType,
            body = LsiPoetCodeBlock.build {
                returnValue {
                    text("(")
                    type(listType, LsiPoetTypeReferenceStyle.FULLY_QUALIFIED)
                    text(")value")
                }
            },
        )

        val content = LsiJavaPoetRenderer().render(artifact(function, "Qualified")).content

        assertContains(content, "return (java.util.List<java.lang.String>)value;")
        assertTrue("type:" !in content)
    }

    @Test
    fun `preserves same package outer qualification in a field type`() {
        val producerType = LsiDeclaredType(
            LsiSymbolId.type("demo.generated.BasicBookDraft.Producer")
        )
        val draftType = LsiPoetType(
            name = "BasicBookDraft",
            kind = LsiPoetTypeKind.INTERFACE,
            members = listOf(
                LsiPoetField(
                    name = "$",
                    type = producerType,
                    modifiers = setOf(
                        LsiPoetModifier.PUBLIC,
                        LsiPoetModifier.STATIC,
                        LsiPoetModifier.FINAL,
                    ),
                    initializer = LsiPoetCodeBlock.build {
                        type(producerType)
                        text(".INSTANCE")
                    },
                    typeReferenceStyle = LsiPoetTypeReferenceStyle.SAME_PACKAGE_OUTER_QUALIFIED,
                ),
                LsiPoetType(
                    name = "Producer",
                    kind = LsiPoetTypeKind.CLASS,
                    modifiers = setOf(LsiPoetModifier.PUBLIC, LsiPoetModifier.STATIC),
                ),
            ),
        )

        val content = LsiJavaPoetRenderer().render(artifact(draftType, "BasicBookDraft")).content

        assertContains(content, "BasicBookDraft.Producer $ = Producer.INSTANCE;")
        assertTrue("import demo.generated.BasicBookDraft.Producer;" !in content)
        assertEquals(
            LsiSymbolId.type("demo.generated.BasicBookDraft.Producer"),
            producerType.declarationId,
        )
    }

    @Test
    fun `renders exact uppercase package lowercase and deeply nested names`() {
        val lowercaseId = LsiSymbolId.type("UPPER.pkg.lowercase")
        val nestedId = LsiSymbolId.type("UPPER.pkg.outer.middle.inner")
        val annotationId = LsiSymbolId.type("UPPER.meta.marker")
        val enumId = LsiSymbolId.type("UPPER.values.outer.mode")
        val type = LsiPoetType(
            name = "ExactNames",
            kind = LsiPoetTypeKind.CLASS,
            annotations = listOf(
                LsiPoetAnnotation(
                    type = annotationId,
                    arguments = listOf(
                        LsiPoetAnnotationArgument.Named(
                            name = "kind",
                            value = LsiPoetAnnotationValue.EnumValue(enumId, "ON"),
                        ),
                        LsiPoetAnnotationArgument.Named(
                            name = "target",
                            value = LsiPoetAnnotationValue.ClassValue(LsiDeclaredType(lowercaseId)),
                        ),
                    ),
                )
            ),
            members = listOf(
                LsiPoetFunction(name = "lower", returnType = LsiDeclaredType(lowercaseId)),
                LsiPoetFunction(name = "nested", returnType = LsiDeclaredType(nestedId)),
            ),
        )
        val artifact = LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.JAVA,
                packageName = "demo.generated",
                fileName = "ExactNames",
                members = listOf(type),
            ),
            typeNames = listOf(
                typeName(lowercaseId, "UPPER.pkg", "lowercase"),
                typeName(nestedId, "UPPER.pkg", "outer", "middle", "inner"),
                typeName(annotationId, "UPPER.meta", "marker"),
                typeName(enumId, "UPPER.values", "outer", "mode"),
            ),
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )

        val content = LsiJavaPoetRenderer().render(artifact).content

        assertContains(content, "import UPPER.meta.marker;")
        assertContains(content, "import UPPER.pkg.lowercase;")
        assertContains(content, "import UPPER.values.outer;")
        assertContains(content, "kind = outer.mode.ON")
        assertContains(content, "target = lowercase.class")
        assertContains(content, "lowercase lower()")
        assertContains(content, "UPPER.pkg.outer.middle.inner nested()")
    }

    @Test
    fun `rejects a declared type without an exact source name`() {
        val type = LsiPoetType(
            name = "MissingName",
            kind = LsiPoetTypeKind.CLASS,
            members = listOf(LsiPoetFunction(name = "value", returnType = stringType)),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().renderType(type, emptyList())
        }

        assertContains(exception.message.orEmpty(), "requires exactly one source type name")
        assertContains(exception.message.orEmpty(), "java.lang.String")
    }

    @Test
    fun `rejects expression bodies at the Java adapter boundary`() {
        val function = LsiPoetFunction(
            name = "message",
            returnType = stringType,
            body = LsiPoetCodeBlock.build { string("ok") },
            bodyStyle = LsiPoetBodyStyle.EXPRESSION,
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(function, "Expression"))
        }

        assertContains(exception.message.orEmpty(), "expression function body")
    }

    @Test
    fun `rejects Kotlin-only layout hints at the Java adapter boundary`() {
        val annotationType = LsiPoetType(
            name = "Annotated",
            kind = LsiPoetTypeKind.CLASS,
            annotations = listOf(
                LsiPoetAnnotation(
                    type = LsiSymbolId.type("demo.Ordered"),
                    argumentLayout = LsiPoetAnnotationArgumentLayout.SINGLE_LINE,
                )
            ),
        )
        val annotationException = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(annotationType, "Annotated"))
        }
        assertContains(annotationException.message.orEmpty(), "forced annotation layout")

        val explicitlyIndentedFunction = LsiPoetFunction(
            name = "create",
            body = LsiPoetCodeBlock.build {
                preserveExplicitIndentation()
                statement { text("Factory.create()") }
            },
        )
        val indentationException = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(explicitlyIndentedFunction, "Indented"))
        }
        assertContains(indentationException.message.orEmpty(), "explicit Kotlin code indentation")
    }

    private fun artifact(
        member: LsiPoetMember,
        fileName: String,
        packageName: String = "demo.generated",
    ): LsiPoetArtifact {
        val type = if (member is LsiPoetType) member else {
            LsiPoetType(fileName, LsiPoetTypeKind.CLASS, members = listOf(member))
        }
        return LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.JAVA,
                packageName = packageName,
                fileName = fileName,
                members = listOf(type),
            ),
            typeNames = commonTypeNames,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )
    }

    private fun typeName(
        typeId: LsiSymbolId,
        packageName: String,
        vararg simpleNames: String,
    ): LsiPoetTypeName = LsiPoetTypeName(typeId, packageName, simpleNames.toList())

    private val commonTypeNames = listOf(
        typeName(LsiSymbolId.type("Owner.Nested"), "", "Owner", "Nested"),
        typeName(LsiSymbolId.type("demo.Ordered"), "demo", "Ordered"),
        typeName(LsiSymbolId.type("demo.annotation.Container"), "demo.annotation", "Container"),
        typeName(LsiSymbolId.type("demo.annotation.Label"), "demo.annotation", "Label"),
        typeName(LsiSymbolId.type("demo.annotation.Nested"), "demo.annotation", "Nested"),
        typeName(LsiSymbolId.type("demo.annotation.TypeMarker"), "demo.annotation", "TypeMarker"),
        typeName(
            LsiSymbolId.type("demo.generated.BasicBookDraft.Producer"),
            "demo.generated",
            "BasicBookDraft",
            "Producer",
        ),
        typeName(
            LsiSymbolId.type("demo.generated.Owner.Nested"),
            "demo.generated",
            "Owner",
            "Nested",
        ),
        typeName(LsiSymbolId.type("java.io.IOException"), "java.io", "IOException"),
        typeName(LsiSymbolId.type("java.lang.Object"), "java.lang", "Object"),
        typeName(LsiSymbolId.type("java.lang.String"), "java.lang", "String"),
        typeName(LsiSymbolId.type("java.util.List"), "java.util", "List"),
    )

    private fun assertPublicApiDoesNotExposeOtherPoet(type: Class<*>) {
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

        assertTrue(exposedTypes.none { exposedType -> exposedType.name.startsWith("com.squareup.kotlinpoet.") })
    }
}
