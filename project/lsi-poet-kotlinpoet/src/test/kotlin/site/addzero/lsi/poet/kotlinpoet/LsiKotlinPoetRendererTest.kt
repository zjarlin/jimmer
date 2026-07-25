package site.addzero.lsi.poet.kotlinpoet

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
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
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.poet.LsiPoetArtifact
import site.addzero.lsi.poet.LsiPoetAccessor
import site.addzero.lsi.poet.LsiPoetBodyStyle
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationArgumentLayout
import site.addzero.lsi.poet.LsiPoetAnnotationArrayStyle
import site.addzero.lsi.poet.LsiPoetAnnotationValue
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetConstructor
import site.addzero.lsi.poet.LsiPoetField
import site.addzero.lsi.poet.LsiPoetFile
import site.addzero.lsi.poet.LsiPoetFileNameStyle
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

class LsiKotlinPoetRendererTest {

    private val stringType = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))

    private val typeNames = listOf(
        LsiPoetTypeName(LsiSymbolId.type("java.lang.String"), "java.lang", listOf("String")),
        LsiPoetTypeName(LsiSymbolId.type("java.io.IOException"), "java.io", listOf("IOException")),
        LsiPoetTypeName(LsiSymbolId.type("kotlin.Suppress"), "kotlin", listOf("Suppress")),
        LsiPoetTypeName(LsiSymbolId.type("demo.annotation.Container"), "demo.annotation", listOf("Container")),
        LsiPoetTypeName(LsiSymbolId.type("demo.annotation.Nested"), "demo.annotation", listOf("Nested")),
        LsiPoetTypeName(LsiSymbolId.type("demo.annotation.Ordered"), "demo.annotation", listOf("Ordered")),
        LsiPoetTypeName(LsiSymbolId.type("demo.annotation.TypeMarker"), "demo.annotation", listOf("TypeMarker")),
        LsiPoetTypeName(LsiSymbolId.type("demo.external.External"), "demo.external", listOf("External")),
        LsiPoetTypeName(
            LsiSymbolId.type("demo.generated.QueryExtensions"),
            "demo.generated",
            listOf("QueryExtensions"),
        ),
        LsiPoetTypeName(LsiSymbolId.type("demo.Order"), "demo", listOf("Order")),
        LsiPoetTypeName(LsiSymbolId.type("demo.Source"), "demo", listOf("Source")),
    )

    @Test
    fun `renders an embeddable Kotlin code block exactly`() {
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

        val rendered = LsiKotlinPoetRenderer().renderCodeBlock(codeBlock, typeNames)

        assertEquals(
            CodeBlock::class.java,
            LsiKotlinPoetRenderer::class.java
                .getDeclaredMethod("renderCodeBlock", LsiPoetCodeBlock::class.java, List::class.java)
                .returnType,
        )
        assertEquals("consume(kotlin.String, \"value\")\n", rendered.toString())
    }

    @Test
    fun `appends control flow into an existing initializer state`() {
        val enumType = LsiDeclaredType(LsiSymbolId.type("demo.Order"))
        val codeBlock = LsiPoetCodeBlock.build {
            text("{")
            indent {
                line()
                beginControlFlow {
                    text("when (it as ")
                    type(enumType)
                    text(")")
                }
                statement {
                    type(enumType)
                    text(".FIRST -> 1")
                }
                statement {
                    type(enumType)
                    text(".SECOND -> 2")
                }
                endControlFlow()
            }
            text("}")
        }
        val initializer = CodeBlock.builder().apply {
            add("Wrapper(")
            indent()
            add("\n")
            LsiKotlinPoetRenderer().appendCodeBlock(this, codeBlock, typeNames)
            unindent()
            add("\n)")
        }.build()
        val rendered = TypeSpec.classBuilder("Container")
            .addProperty(
                PropertySpec.builder("mapping", ANY)
                    .initializer(initializer)
                    .build()
            )
            .build()
            .toString()

        assertEquals(
            """
                public class Container {
                  public val mapping: kotlin.Any = Wrapper(
                    {
                      when (it as demo.Order) {
                        demo.Order.FIRST -> 1
                        demo.Order.SECOND -> 2
                      }
                    }
                  )
                }

            """.trimIndent(),
            rendered,
        )
    }

    @Test
    fun `renders an embeddable Kotlin type structure exactly`() {
        val type = LsiPoetType(
            name = "Marker",
            kind = LsiPoetTypeKind.INTERFACE,
            modifiers = setOf(LsiPoetModifier.PUBLIC),
        )

        val rendered = LsiKotlinPoetRenderer().renderType(type, emptyList())

        assertEquals(
            TypeSpec::class.java,
            LsiKotlinPoetRenderer::class.java
                .getDeclaredMethod("renderType", LsiPoetType::class.java, List::class.java)
                .returnType,
        )
        assertEquals("public interface Marker\n", rendered.toString())
    }

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
        assertPublicApiDoesNotExposeOtherPoet(LsiKotlinPoetRenderer::class.java)
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
            typeNames = typeNames,
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

    @Test
    fun `renders escaped declaration names and explicit imports exactly`() {
        val artifact = LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.KOTLIN,
                packageName = "demo.generated",
                fileName = "FetcherDsl",
                imports = listOf(LsiPoetImport("demo.child", "by")),
                members = listOf(
                    LsiPoetType(
                        name = "Order-ItemFetcherDsl",
                        kind = LsiPoetTypeKind.CLASS,
                        nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
                        members = listOf(
                            LsiPoetProperty(
                                name = "emptyOrder-ItemFetcher",
                                type = stringType,
                                mutable = false,
                                nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
                                initializer = LsiPoetCodeBlock.build { string("empty") },
                            ),
                            LsiPoetFunction(
                                name = "children*",
                                nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
                            ),
                        ),
                    )
                ),
            ),
            typeNames = typeNames,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )

        val content = LsiKotlinPoetRenderer().render(artifact).content

        assertEquals(
            """
                package demo.generated

                import demo.child.`by`
                import kotlin.String

                public class `Order-ItemFetcherDsl` {
                    public val `emptyOrder-ItemFetcher`: String = "empty"

                    public fun `children*`() {
                    }
                }
            """.trimIndent(),
            content.trimIndent(),
        )
    }

    @Test
    fun `renders escaped parameters named setters and annotation array factory calls`() {
        val type = LsiPoetType(
            name = "EscapedParameters",
            kind = LsiPoetTypeKind.CLASS,
            annotations = listOf(
                LsiPoetAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Container"),
                    arguments = listOf(
                        LsiPoetAnnotationArgument.Named(
                            name = "literalValues",
                            value = LsiPoetAnnotationValue.ArrayValue(
                                listOf(LsiPoetAnnotationValue.StringValue("literal"))
                            ),
                        ),
                        LsiPoetAnnotationArgument.Named(
                            name = "factoryValues",
                            value = LsiPoetAnnotationValue.ArrayValue(
                                elements = listOf(LsiPoetAnnotationValue.StringValue("factory")),
                                sourceStyle = LsiPoetAnnotationArrayStyle.KOTLIN_ARRAY_OF,
                            ),
                        ),
                    ),
                )
            ),
            members = listOf(
                LsiPoetProperty(
                    name = "display-name",
                    type = stringType,
                    mutable = true,
                    nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
                    initializer = LsiPoetCodeBlock.build { string("") },
                    setter = LsiPoetAccessor(
                        setterParameterName = "display-name",
                        setterParameterNameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
                        body = LsiPoetCodeBlock.build {
                            statement {
                                text("println(")
                                name("display-name")
                                text(")")
                            }
                        },
                    ),
                ),
                LsiPoetFunction(
                    name = "update",
                    parameters = listOf(
                        LsiPoetParameter(
                            name = "display-name",
                            type = stringType,
                            nameStyle = LsiPoetNameStyle.KOTLIN_ESCAPED,
                        )
                    ),
                ),
            ),
        )

        val content = LsiKotlinPoetRenderer().render(artifact(type, "EscapedParameters")).content

        assertContains(content, "literalValues = [\"literal\"]")
        assertContains(content, "factoryValues = arrayOf(\"factory\")")
        assertContains(content, "set(`display-name`)")
        assertContains(content, "fun update(`display-name`: String)")
    }

    @Test
    fun `preserves raw source stem in generated artifact path`() {
        val artifact = LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.KOTLIN,
                packageName = "demo.generated",
                fileName = "order-item.partFetcher",
                fileNameStyle = LsiPoetFileNameStyle.KOTLIN_SOURCE_STEM,
                members = listOf(LsiPoetType("OrderFetcher", LsiPoetTypeKind.CLASS)),
            ),
            typeNames = typeNames,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Order")),
        )

        val generated = LsiKotlinPoetRenderer().render(artifact)

        assertEquals("demo/generated/order-item.partFetcher.kt", generated.path)
        assertContains(generated.content, "public class OrderFetcher")
    }

    @Test
    fun `renders inline reified type parameters exactly`() {
        val parameterId = LsiSymbolId.typeParameter(
            LsiSymbolId.type("demo.generated.QueryExtensions"),
            "S",
        )
        val function = LsiPoetFunction(
            name = "query",
            modifiers = setOf(LsiPoetModifier.INLINE),
            typeParameters = listOf(
                LsiTypeParameter(
                    id = parameterId,
                    name = "S",
                    upperBounds = listOf(stringType),
                )
            ),
            reifiedTypeParameterIds = setOf(parameterId),
            returnType = LsiTypeParameterRef(parameterId),
            body = LsiPoetCodeBlock.build {
                returnValue { text("error(\"unused\")") }
            },
        )
        val artifact = LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.KOTLIN,
                packageName = "demo.generated",
                fileName = "QueryExtensions",
                members = listOf(function),
            ),
            typeNames = typeNames,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )

        val content = LsiKotlinPoetRenderer().render(artifact).content

        assertContains(content, "public inline fun <reified S : String> query(): S = error(\"unused\")")
    }

    @Test
    fun `renders fully qualified Kotlin type references without imports`() {
        val externalType = LsiDeclaredType(LsiSymbolId.type("demo.external.External"))
        val function = LsiPoetFunction(
            name = "typeName",
            body = LsiPoetCodeBlock.build {
                statement {
                    type(externalType, LsiPoetTypeReferenceStyle.FULLY_QUALIFIED)
                    text("::class")
                }
            },
        )
        val file = LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.KOTLIN,
                packageName = "demo.generated",
                fileName = "Qualified",
                members = listOf(function),
            ),
            typeNames = typeNames,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )

        val content = LsiKotlinPoetRenderer().render(file).content

        assertContains(content, "demo.`external`.External::class")
        assertTrue("import demo.external.External" !in content)
    }

    @Test
    fun `renders explicit expression and single line layout`() {
        val type = LsiPoetType(
            name = "Layout",
            kind = LsiPoetTypeKind.CLASS,
            annotations = listOf(
                LsiPoetAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Ordered"),
                    arguments = listOf("dummy", "id", "name").map { value ->
                        LsiPoetAnnotationArgument.Positional(
                            LsiPoetAnnotationValue.StringValue(value)
                        )
                    },
                    argumentLayout = LsiPoetAnnotationArgumentLayout.SINGLE_LINE,
                )
            ),
            members = listOf(
                LsiPoetProperty(
                    name = "created",
                    type = stringType,
                    mutable = false,
                    initializer = LsiPoetCodeBlock.build {
                        preserveExplicitIndentation()
                        text("Factory")
                        line()
                        indent { text(".create()") }
                    },
                ),
                LsiPoetProperty(
                    name = "missing",
                    type = stringType,
                    mutable = false,
                    getter = LsiPoetAccessor(
                        body = LsiPoetCodeBlock.build { text("throw IllegalStateException()") },
                        bodyStyle = LsiPoetBodyStyle.EXPRESSION,
                    ),
                ),
                LsiPoetFunction(
                    name = "pick",
                    returnType = stringType,
                    body = LsiPoetCodeBlock.build { text("when (value) { else -> value }") },
                    bodyStyle = LsiPoetBodyStyle.EXPRESSION,
                ),
            ),
        )

        val content = LsiKotlinPoetRenderer().render(artifact(type, "Layout")).content

        assertContains(content, "@Ordered(\"dummy\", \"id\", \"name\")")
        assertContains(content, "public val created: String = Factory\n        .create()")
        assertContains(content, "get() = throw IllegalStateException()")
        assertContains(content, "public fun pick(): String = when (value) { else -> value }")
    }

    private fun artifact(type: LsiPoetType, fileName: String): LsiPoetArtifact {
        return LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.KOTLIN,
                packageName = "demo.generated",
                fileName = fileName,
                members = listOf(type),
            ),
            typeNames = typeNames,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )
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
                    argumentLayout = LsiPoetAnnotationArgumentLayout.MULTI_LINE,
                )
            ),
        )

        val content = LsiKotlinPoetRenderer().render(artifact(type, "MultilineAnnotation")).content

        assertContains(
            content,
            """
                @Container(
                    `value` = [
                        "dto-name"
                    ]
                )
            """.trimIndent(),
        )
    }

    @Test
    fun `renders line separated annotation array elements without moving brackets`() {
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

        val rendered = LsiKotlinPoetRenderer().renderAnnotation(
            annotation,
            listOf(LsiPoetTypeName(annotation.type, "demo.annotation", listOf("Container"))),
        )

        assertEquals("@demo.`annotation`.Container([\"first\",\n\"second\"])", rendered.toString())
    }

    @Test
    fun `renders compact multiline annotation array elements inside brackets`() {
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

        val rendered = LsiKotlinPoetRenderer().renderAnnotation(
            annotation,
            listOf(LsiPoetTypeName(annotation.type, "demo.annotation", listOf("Container"))),
        )

        assertEquals(
            "@demo.`annotation`.Container([\n  \"first\",\n  \"second\"\n])",
            rendered.toString(),
        )
    }

    @Test
    fun `escapes keyword annotation argument names at every annotation boundary`() {
        val keywordArgument = LsiAnnotationArgument(
            value = LsiAnnotationValue.StringValue("core"),
            origin = LsiAnnotationArgumentOrigin.EXPLICIT,
        )
        val nested = LsiPoetAnnotation(
            type = LsiSymbolId.type("demo.annotation.Nested"),
            arguments = listOf(
                LsiPoetAnnotationArgument.Named(
                    name = "when",
                    value = LsiPoetAnnotationValue.StringValue("nested"),
                )
            ),
        )
        val type = LsiPoetType(
            name = "KeywordAnnotation",
            kind = LsiPoetTypeKind.CLASS,
            annotations = listOf(
                LsiPoetAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Container"),
                    arguments = listOf(
                        LsiPoetAnnotationArgument.Named(
                            name = "when",
                            value = LsiPoetAnnotationValue.StringValue("source"),
                        ),
                        LsiPoetAnnotationArgument.Named(
                            name = "nested",
                            value = LsiPoetAnnotationValue.NestedAnnotationValue(nested),
                        ),
                    ),
                )
            ),
            members = listOf(
                LsiPoetProperty(
                    name = "value",
                    type = stringType.copy(
                        annotations = listOf(
                            LsiAnnotation(
                                type = LsiSymbolId.type("demo.annotation.Container"),
                                arguments = mapOf("when" to keywordArgument),
                            )
                        ),
                    ),
                    mutable = false,
                )
            ),
        )

        val content = LsiKotlinPoetRenderer().render(artifact(type, "KeywordAnnotation")).content

        assertContains(content, "`when` = \"source\"")
        assertContains(content, "Nested(`when` = \"nested\")")
        assertContains(content, "Container(`when` = \"core\") String")
    }

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

        assertTrue(exposedTypes.none { exposedType -> exposedType.name.startsWith("com.squareup.javapoet.") })
    }
}
