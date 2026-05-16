package site.addzero.lsi.poet

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JavaPoetBoundaryGuardTest {

    @Test
    fun `reject top-level kotlin declarations`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            LsiFileSpec(
                packageName = "test.sample",
                name = "TopLevelExtensions",
                memberImports = listOf(
                    LsiImportSpec(
                        packageName = "demo.fetcher",
                        name = "by"
                    )
                ),
                topLevelCallables = listOf(
                    LsiCallableSpec(
                        kind = LsiCallableSpecKind.FUNCTION,
                        name = "echo",
                        receiverType = LsiClassName.bestGuess("kotlin.String"),
                        returnType = LsiClassName.bestGuess("kotlin.String")
                    )
                )
            ).renderJavaSource()
        }

        assertTrue(error.message!!.contains("member imports"))
    }

    @Test
    fun `reject extension members`() {
        val functionError = assertThrows(IllegalArgumentException::class.java) {
            LsiCallableSpec(
                kind = LsiCallableSpecKind.FUNCTION,
                name = "echo",
                receiverType = LsiClassName.bestGuess("kotlin.String"),
                returnType = LsiClassName.bestGuess("kotlin.String")
            ).toJavaPoet()
        }
        assertTrue(functionError.message!!.contains("extension functions"))

        val propertyError = assertThrows(IllegalArgumentException::class.java) {
            LsiPropertySpec(
                name = "answer",
                type = LsiClassName.bestGuess("kotlin.Int"),
                receiverType = LsiClassName.bestGuess("kotlin.String")
            ).toJavaPoet()
        }
        assertTrue(propertyError.message!!.contains("extension properties"))
    }

    @Test
    fun `render property setter statements`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Sample",
            types = listOf(
                LsiTypeSpec(
                    name = "Sample",
                    kind = LsiTypeSpecKind.CLASS,
                    properties = listOf(
                        LsiPropertySpec(
                            name = "name",
                            type = LsiClassName.bestGuess("java.lang.String"),
                            modifiers = setOf(LsiModifier.PUBLIC),
                            mutable = true,
                            getterStatements = listOf(
                                LsiReturnStatement(LsiNameExpression("currentName"))
                            ),
                            setterStatements = listOf(
                                LsiAssignmentStatement(
                                    target = LsiNameExpression("currentName"),
                                    expression = LsiNameExpression("value")
                                )
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("public String getName()"), source)
        assertTrue(source.contains("return currentName;"), source)
        assertTrue(source.contains("public void setName(String value)"), source)
        assertTrue(source.contains("currentName = value;"), source)
    }

    @Test
    fun `render primary constructor statements in java constructor body`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Sample",
            types = listOf(
                LsiTypeSpec(
                    name = "Sample",
                    kind = LsiTypeSpecKind.CLASS,
                    properties = listOf(
                        LsiPropertySpec(
                            name = "current",
                            type = LsiClassName.bestGuess("kotlin.Int"),
                            modifiers = setOf(LsiModifier.PRIVATE),
                            mutable = true,
                            initializer = LsiLiteralExpression(0),
                        )
                    ),
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.CONSTRUCTOR,
                            primary = true,
                            parameters = listOf(
                                LsiParameterSpec("x", LsiClassName.bestGuess("kotlin.Int"))
                            ),
                            statements = listOf(
                                LsiAssignmentStatement(
                                    target = LsiNameExpression("current"),
                                    expression = LsiNameExpression("x"),
                                )
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("public Sample(int x)"), source)
        assertTrue(source.contains("current = x;"), source)
    }

    @Test
    fun `render float literal with explicit suffix`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "FloatHolder",
            types = listOf(
                LsiTypeSpec(
                    name = "FloatHolder",
                    kind = LsiTypeSpecKind.CLASS,
                    properties = listOf(
                        LsiPropertySpec(
                            name = "ratio",
                            type = LsiClassName.bestGuess("kotlin.Float"),
                            modifiers = setOf(LsiModifier.PRIVATE),
                            initializer = LsiLiteralExpression(0F),
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(
            source.contains("float ratio = 0.0F;") || source.contains("Float ratio = 0.0F;"),
            source,
        )
    }

    @Test
    fun `render long literal with explicit suffix`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "LongHolder",
            types = listOf(
                LsiTypeSpec(
                    name = "LongHolder",
                    kind = LsiTypeSpecKind.CLASS,
                    properties = listOf(
                        LsiPropertySpec(
                            name = "value",
                            type = LsiClassName.bestGuess("kotlin.Long"),
                            modifiers = setOf(LsiModifier.PRIVATE),
                            initializer = LsiLiteralExpression(0L),
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(
            source.contains("long value = 0L;") || source.contains("Long value = 0L;"),
            source,
        )
    }

    @Test
    fun `render length expression with comparison operators for java renderer`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Sample",
            types = listOf(
                LsiTypeSpec(
                    name = "Sample",
                    kind = LsiTypeSpecKind.CLASS,
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "isLongName",
                            parameters = listOf(
                                LsiParameterSpec("name", LsiClassName.bestGuess("java.lang.String"))
                            ),
                            returnType = LsiClassName.bestGuess("kotlin.Boolean"),
                            statements = listOf(
                                LsiReturnStatement(
                                    LsiBinaryExpression(
                                        left = LsiLengthExpression(LsiNameExpression("name")),
                                        operator = LsiBinaryOperator.GREATER_THAN,
                                        right = LsiLiteralExpression(3),
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("return (name.length() > 3);"), source)
    }

    @Test
    fun `render semantic property get as java accessor call`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Sample",
            types = listOf(
                LsiTypeSpec(
                    name = "Sample",
                    kind = LsiTypeSpecKind.CLASS,
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "resolveId",
                            parameters = listOf(
                                LsiParameterSpec("holder", LsiClassName.bestGuess("test.sample.Holder"))
                            ),
                            returnType = LsiClassName.bestGuess("java.lang.Long"),
                            statements = listOf(
                                LsiReturnStatement(
                                    LsiPropertyGetExpression(
                                        receiver = LsiPropertyGetExpression(
                                            receiver = LsiNameExpression("holder"),
                                            name = "store",
                                            type = LsiClassName.bestGuess("test.sample.Store").copyNullable(true),
                                        ),
                                        name = "id",
                                        type = LsiClassName.bestGuess("java.lang.Long"),
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("return holder.getStore().getId();"), source)
    }

    @Test
    fun `render collection size and make id only expressions for java renderer`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Sample",
            types = listOf(
                LsiTypeSpec(
                    name = "Sample",
                    kind = LsiTypeSpecKind.CLASS,
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "toStore",
                            parameters = listOf(
                                LsiParameterSpec(
                                    "items",
                                    LsiParameterizedTypeName(
                                        rawType = LsiClassName.bestGuess("java.util.List"),
                                        typeArguments = listOf(LsiClassName.bestGuess("java.lang.Long"))
                                    )
                                ),
                                LsiParameterSpec("id", LsiClassName.bestGuess("java.lang.Long")),
                            ),
                            returnType = LsiClassName.bestGuess("test.sample.Store"),
                            statements = listOf(
                                LsiVariableDeclarationStatement(
                                    name = "size",
                                    type = LsiClassName.bestGuess("kotlin.Int"),
                                    initializer = LsiCollectionSizeExpression(LsiNameExpression("items")),
                                ),
                                LsiReturnStatement(
                                    LsiMakeIdOnlyExpression(
                                        targetType = LsiClassName.bestGuess("test.sample.Store"),
                                        idExpression = LsiCollectionElementExpression(
                                            receiver = LsiNameExpression("items"),
                                            index = LsiLiteralExpression(0),
                                        ),
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("int size = items.size();"), source)
        assertTrue(source.contains("return ImmutableObjects.makeIdOnly(Store.class, items.get(0));"), source)
    }

    @Test
    fun `render interface accessor bodies as default methods`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "BookView",
            types = listOf(
                LsiTypeSpec(
                    name = "BookView",
                    kind = LsiTypeSpecKind.INTERFACE,
                    properties = listOf(
                        LsiPropertySpec(
                            name = "name",
                            type = LsiClassName.bestGuess("java.lang.String"),
                            getterStatements = listOf(
                                LsiReturnStatement(LsiLiteralExpression("demo"))
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(
            source.contains("default String getName()") ||
                source.contains("public default String getName()"),
            source
        )
        assertTrue(source.contains("return \"demo\";"), source)
    }

    @Test
    fun `render interface callable bodies as default methods`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "BookResolver",
            types = listOf(
                LsiTypeSpec(
                    name = "BookResolver",
                    kind = LsiTypeSpecKind.INTERFACE,
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "resolve",
                            returnType = LsiClassName.bestGuess("java.lang.String"),
                            statements = listOf(
                                LsiReturnStatement(LsiLiteralExpression("ok"))
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(
            source.contains("default String resolve()") ||
                source.contains("public default String resolve()"),
            source
        )
        assertTrue(source.contains("return \"ok\";"), source)
    }

    @Test
    fun `render positional annotation arguments as java value members`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "BookView",
            types = listOf(
                LsiTypeSpec(
                    name = "BookView",
                    kind = LsiTypeSpecKind.CLASS,
                    annotations = listOf(
                        LsiAnnotationSpec(
                            type = LsiClassName.bestGuess("com.fasterxml.jackson.annotation.JsonPropertyOrder"),
                            positionalArguments = listOf(
                                LsiStringAnnotationValue("id"),
                                LsiStringAnnotationValue("name"),
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("@JsonPropertyOrder"), source)
        assertTrue(!source.contains("\$L ="), source)
        assertTrue(source.contains("\"id\""), source)
        assertTrue(source.contains("\"name\""), source)
    }

    @Test
    fun `render default mutable accessor property`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "CounterHolder",
            types = listOf(
                LsiTypeSpec(
                    name = "CounterHolder",
                    kind = LsiTypeSpecKind.CLASS,
                    properties = listOf(
                        LsiPropertySpec(
                            name = "count",
                            type = LsiClassName.bestGuess("kotlin.Int"),
                            modifiers = setOf(LsiModifier.PUBLIC),
                            mutable = true,
                            initializer = LsiLiteralExpression(0),
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("private int count = 0;"), source)
        assertTrue(source.contains("public int getCount()"), source)
        assertTrue(source.contains("return count;"), source)
        assertTrue(source.contains("public void setCount(int value)"), source)
        assertTrue(source.contains("this.count = value;"), source)
    }

    @Test
    fun `default visibility is public and const becomes final`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Constants",
            types = listOf(
                LsiTypeSpec(
                    name = "Constants",
                    kind = LsiTypeSpecKind.CLASS,
                    properties = listOf(
                        LsiPropertySpec(
                            name = "VALUE",
                            type = LsiClassName.bestGuess("java.lang.String"),
                            modifiers = setOf(LsiModifier.STATIC, LsiModifier.CONST),
                            initializer = LsiLiteralExpression("demo"),
                        )
                    ),
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.CONSTRUCTOR,
                        ),
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "echo",
                            returnType = LsiClassName.bestGuess("java.lang.String"),
                            statements = listOf(LsiReturnStatement(LsiLiteralExpression("ok"))),
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("public class Constants"), source)
        assertTrue(source.contains("public static final String VALUE = \"demo\";"), source)
        assertTrue(source.contains("public Constants()"), source)
        assertTrue(source.contains("public String echo()"), source)
    }

    @Test
    fun `translate kotlin style raw placeholders for java renderer`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Usage",
            types = listOf(
                LsiTypeSpec(
                    name = "Usage",
                    kind = LsiTypeSpecKind.CLASS,
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "value",
                            returnType = LsiClassName.bestGuess("java.lang.String"),
                            statements = listOf(
                                LsiReturnStatement(
                                    LsiCodeExpression(
                                        LsiCodeBlock.of("%T.%L", LsiClassName.bestGuess("test.sample.Constants"), "VALUE")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("return Constants.VALUE;"), source)
    }

    @Test
    fun `render safe cast expression for java renderer`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Usage",
            types = listOf(
                LsiTypeSpec(
                    name = "Usage",
                    kind = LsiTypeSpecKind.CLASS,
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "value",
                            returnType = LsiClassName.bestGuess("test.sample.Usage"),
                            parameters = listOf(
                                LsiParameterSpec("other", LsiClassName.bestGuess("java.lang.Object"))
                            ),
                            statements = listOf(
                                LsiReturnStatement(
                                    LsiSafeCastExpression(
                                        LsiClassName.bestGuess("test.sample.Usage"),
                                        LsiNameExpression("other")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(
            source.contains("return (other instanceof Usage ? (Usage) other : null);"),
            source
        )
    }

    @Test
    fun `render binary expressions for java renderer`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Usage",
            types = listOf(
                LsiTypeSpec(
                    name = "Usage",
                    kind = LsiTypeSpecKind.CLASS,
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "changed",
                            returnType = LsiClassName.bestGuess("kotlin.Boolean"),
                            parameters = listOf(
                                LsiParameterSpec("left", LsiClassName.bestGuess("java.lang.Object")),
                                LsiParameterSpec("right", LsiClassName.bestGuess("java.lang.Object")),
                            ),
                            statements = listOf(
                                LsiReturnStatement(
                                    LsiBinaryExpression(
                                        left = LsiBinaryExpression(
                                            left = LsiNameExpression("left"),
                                            operator = LsiBinaryOperator.IDENTITY_NOT_EQUALS,
                                            right = LsiNameExpression("right"),
                                        ),
                                        operator = LsiBinaryOperator.AND,
                                        right = LsiBinaryExpression(
                                            left = LsiLiteralExpression(31),
                                            operator = LsiBinaryOperator.TIMES,
                                            right = LsiLiteralExpression(2),
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("left != right"), source)
        assertTrue(source.contains("&&"), source)
        assertTrue(source.contains("31 * 2"), source)
    }

    @Test
    fun `render if statement with semantic property set for java renderer`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Usage",
            types = listOf(
                LsiTypeSpec(
                    name = "Usage",
                    kind = LsiTypeSpecKind.CLASS,
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "applyName",
                            parameters = listOf(
                                LsiParameterSpec("holder", LsiClassName.bestGuess("test.sample.Holder")),
                                LsiParameterSpec("name", LsiClassName.bestGuess("java.lang.String")),
                            ),
                            statements = listOf(
                                LsiIfStatement(
                                    condition = LsiCodeExpression(LsiCodeBlock.of("%L != null", LsiNameExpression("name"))),
                                    thenStatements = listOf(
                                        LsiPropertySetStatement(
                                            receiver = LsiNameExpression("holder"),
                                            name = "name",
                                            expression = LsiNameExpression("name"),
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("if (name != null)"), source)
        assertTrue(source.contains("holder.setName(name);"), source)
    }

    @Test
    fun `render try finally statement for java renderer`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Usage",
            types = listOf(
                LsiTypeSpec(
                    name = "Usage",
                    kind = LsiTypeSpecKind.CLASS,
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "resolve",
                            statements = listOf(
                                LsiTryStatement(
                                    tryStatements = listOf(
                                        LsiExpressionStatement(
                                            LsiCallExpression(name = "doWork")
                                        )
                                    ),
                                    finallyStatements = listOf(
                                        LsiAssignmentStatement(
                                            target = LsiNameExpression("done"),
                                            expression = LsiLiteralExpression(true),
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("try"), source)
        assertTrue(source.contains("doWork();"), source)
        assertTrue(source.contains("finally"), source)
        assertTrue(source.contains("done = true;"), source)
    }

    @Test
    fun `render for range statement for java renderer`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Usage",
            types = listOf(
                LsiTypeSpec(
                    name = "Usage",
                    kind = LsiTypeSpecKind.CLASS,
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "copyVisibility",
                            statements = listOf(
                                LsiForRangeStatement(
                                    variableName = "propId",
                                    from = LsiLiteralExpression(0),
                                    until = LsiLiteralExpression(10),
                                    statements = listOf(
                                        LsiExpressionStatement(
                                            LsiCallExpression(
                                                receiver = LsiNameExpression("target"),
                                                name = "show",
                                                arguments = listOf(
                                                    LsiNameExpression("propId"),
                                                    LsiLiteralExpression(true),
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("for (int propId = 0; propId < 10; propId++)"), source)
        assertTrue(source.contains("target.show(propId, true);"), source)
    }

    @Test
    fun `render switch statement with throw default for java renderer`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Usage",
            types = listOf(
                LsiTypeSpec(
                    name = "Usage",
                    kind = LsiTypeSpecKind.CLASS,
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "resolve",
                            parameters = listOf(LsiParameterSpec("prop", LsiClassName.bestGuess("java.lang.String"))),
                            returnType = LsiClassName.bestGuess("kotlin.Int"),
                            statements = listOf(
                                LsiWhenStatement(
                                    subject = LsiNameExpression("prop"),
                                    cases = listOf(
                                        LsiWhenCase(
                                            conditions = listOf(LsiLiteralExpression("id")),
                                            statements = listOf(LsiReturnStatement(LsiLiteralExpression(1))),
                                        ),
                                        LsiWhenCase(
                                            conditions = listOf(LsiLiteralExpression("name")),
                                            statements = listOf(LsiReturnStatement(LsiLiteralExpression(2))),
                                        ),
                                    ),
                                    elseStatements = listOf(
                                        LsiThrowStatement(
                                            LsiNewExpression(
                                                type = LsiClassName.bestGuess("java.lang.IllegalArgumentException"),
                                                arguments = listOf(LsiLiteralExpression("bad"))
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("switch (prop)"), source)
        assertTrue(source.contains("case \"id\":"), source)
        assertTrue(source.contains("return 1;"), source)
        assertTrue(source.contains("default:"), source)
        assertTrue(source.contains("throw new IllegalArgumentException(\"bad\");"), source)
    }

    @Test
    fun `reject kotlin raw code markers for java renderer`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            LsiFileSpec(
                packageName = "test.sample",
                name = "Usage",
                types = listOf(
                    LsiTypeSpec(
                        name = "Usage",
                        kind = LsiTypeSpecKind.CLASS,
                        callables = listOf(
                            LsiCallableSpec(
                                kind = LsiCallableSpecKind.FUNCTION,
                                name = "value",
                                statements = listOf(
                                    LsiExpressionStatement(
                                        LsiCodeExpression(
                                            LsiCodeBlock.of("val tmp = other?.toString() ?: error(%S)", "boom")
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            ).renderJavaSource()
        }

        assertTrue(error.message!!.contains("Kotlin-only raw code"), error.message)
        assertTrue(error.message!!.contains("callable 'value'"), error.message)
    }

    @Test
    fun `reject kotlin raw default argument code for java renderer`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            LsiFileSpec(
                packageName = "test.sample",
                name = "Usage",
                types = listOf(
                    LsiTypeSpec(
                        name = "Usage",
                        kind = LsiTypeSpecKind.CLASS,
                        callables = listOf(
                            LsiCallableSpec(
                                kind = LsiCallableSpecKind.FUNCTION,
                                name = "value",
                                parameters = listOf(
                                    LsiParameterSpec(
                                        name = "other",
                                        type = LsiClassName.bestGuess("java.lang.String"),
                                        defaultValue = LsiCodeBlock.of("other?.toString() ?: error(%S)", "boom"),
                                    )
                                ),
                                returnType = LsiClassName.bestGuess("java.lang.String"),
                                statements = listOf(LsiReturnStatement(LsiNullExpression))
                            )
                        )
                    )
                )
            ).renderJavaSource()
        }

        assertTrue(error.message!!.contains("Kotlin-only raw code"), error.message)
        assertTrue(error.message!!.contains("defaultValue"), error.message)
    }

    @Test
    fun `reject kotlin only type nodes`() {
        val objectError = assertThrows(IllegalArgumentException::class.java) {
            LsiTypeSpec(
                name = "Singleton",
                kind = LsiTypeSpecKind.OBJECT,
            ).toJavaPoet()
        }
        assertTrue(objectError.message!!.contains("OBJECT"))

        val lambdaError = assertThrows(IllegalStateException::class.java) {
            LsiLambdaTypeName(
                receiverType = LsiClassName.bestGuess("demo.Draft"),
                returnType = LsiClassName.bestGuess("kotlin.Unit"),
            ).toJavaPoet()
        }
        assertTrue(lambdaError.message!!.contains("LsiLambdaTypeName"))
    }

    @Test
    fun `render unlabeled callable reference and reject labeled callable reference`() {
        val rendered = assertDoesNotThrow<String> {
            LsiFileSpec(
                packageName = "test.sample",
                name = "FactoryHolder",
                types = listOf(
                    LsiTypeSpec(
                        name = "FactoryHolder",
                        kind = LsiTypeSpecKind.CLASS,
                        callables = listOf(
                            LsiCallableSpec(
                                kind = LsiCallableSpecKind.FUNCTION,
                                name = "supplier",
                                returnType = LsiClassName.bestGuess("java.lang.Object"),
                                statements = listOf(
                                    LsiReturnStatement(
                                        LsiCallableReferenceExpression(
                                            receiver = LsiNameExpression("factory"),
                                            name = "create",
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            ).renderJavaSource()
        }
        assertTrue(rendered.contains("factory::create"), rendered)

        val error = assertThrows(IllegalArgumentException::class.java) {
            LsiFileSpec(
                packageName = "test.sample",
                name = "BookView",
                types = listOf(
                    LsiTypeSpec(
                        name = "BookView",
                        kind = LsiTypeSpecKind.CLASS,
                        callables = listOf(
                            LsiCallableSpec(
                                kind = LsiCallableSpecKind.FUNCTION,
                                name = "toImmutable",
                                returnType = LsiClassName.bestGuess("test.sample.Book"),
                                statements = listOf(
                                    LsiReturnStatement(
                                        LsiCallableReferenceExpression(
                                            receiver = LsiThisExpression,
                                            name = "toImmutableImpl",
                                            receiverLabel = "BookView",
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            ).renderJavaSource()
        }
        assertTrue(error.message!!.contains("labeled callable reference"), error.message)
        assertTrue(error.message!!.contains("callable 'toImmutable'"), error.message)
    }

    @Test
    fun `render array expression`() {
        val rendered = assertDoesNotThrow<String> {
            LsiFileSpec(
                packageName = "test.sample",
                name = "ArrayHolder",
                types = listOf(
                    LsiTypeSpec(
                        name = "ArrayHolder",
                        kind = LsiTypeSpecKind.CLASS,
                        properties = listOf(
                            LsiPropertySpec(
                                name = "props",
                                type = LsiArrayTypeName(LsiClassName.bestGuess("org.babyfish.jimmer.meta.ImmutableProp")),
                                initializer = LsiArrayExpression(
                                    elementType = LsiClassName.bestGuess("org.babyfish.jimmer.meta.ImmutableProp"),
                                    elements = listOf(
                                        LsiNameExpression("a"),
                                        LsiNameExpression("b"),
                                    )
                                )
                            )
                        )
                    )
                )
            ).renderJavaSource()
        }

        assertTrue(rendered.contains("new ImmutableProp[] {a, b}"), rendered)
    }

    @Test
    fun `render int array expression`() {
        val rendered = assertDoesNotThrow<String> {
            LsiFileSpec(
                packageName = "test.sample",
                name = "IntArrayHolder",
                types = listOf(
                    LsiTypeSpec(
                        name = "IntArrayHolder",
                        kind = LsiTypeSpecKind.CLASS,
                        properties = listOf(
                            LsiPropertySpec(
                                name = "slots",
                                type = LsiClassName.bestGuess("kotlin.IntArray"),
                                initializer = LsiIntArrayExpression(
                                    elements = listOf(
                                        LsiLiteralExpression(1),
                                        LsiLiteralExpression(2),
                                    )
                                )
                            )
                        )
                    )
                )
            ).renderJavaSource()
        }

        assertTrue(rendered.contains("new int[] {1, 2}"), rendered)
    }

    @Test
    fun `reject nested kotlin only type nodes with path`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            LsiFileSpec(
                packageName = "test.sample",
                name = "FetcherDsl",
                types = listOf(
                    LsiTypeSpec(
                        name = "FetcherDsl",
                        kind = LsiTypeSpecKind.CLASS,
                        callables = listOf(
                            LsiCallableSpec(
                                kind = LsiCallableSpecKind.FUNCTION,
                                name = "by",
                                parameters = listOf(
                                    LsiParameterSpec(
                                        name = "block",
                                        type = LsiLambdaTypeName(
                                            receiverType = LsiClassName.bestGuess("demo.FetcherDsl"),
                                            returnType = LsiClassName.bestGuess("kotlin.Unit"),
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            ).renderJavaSource()
        }

        assertTrue(error.message!!.contains("LsiLambdaTypeName"))
        assertTrue(error.message!!.contains("parameter 'block'"))
    }

    @Test
    fun `allow get use site target on accessor property`() {
        val source = assertDoesNotThrow<String> {
            LsiFileSpec(
                packageName = "test.sample",
                name = "Book",
                types = listOf(
                    LsiTypeSpec(
                        name = "Book",
                        kind = LsiTypeSpecKind.CLASS,
                        properties = listOf(
                            LsiPropertySpec(
                                name = "id",
                                type = LsiClassName.bestGuess("java.lang.String"),
                                annotations = listOf(
                                    LsiAnnotationSpec(
                                        type = LsiClassName.bestGuess("com.fasterxml.jackson.annotation.JsonIgnore"),
                                        useSiteTarget = LsiAnnotationUseSiteTarget.GET,
                                    )
                                ),
                                modifiers = setOf(LsiModifier.PUBLIC),
                            )
                        )
                    )
                ),
            ).renderJavaSource()
        }

        assertTrue(source.contains("@JsonIgnore"))
        assertTrue(source.contains("getId()"))
    }

    @Test
    fun `allow field use site target on accessor backing field`() {
        val source = assertDoesNotThrow<String> {
            LsiFileSpec(
                packageName = "test.sample",
                name = "Book",
                types = listOf(
                    LsiTypeSpec(
                        name = "Book",
                        kind = LsiTypeSpecKind.CLASS,
                        properties = listOf(
                            LsiPropertySpec(
                                name = "id",
                                type = LsiClassName.bestGuess("java.lang.String"),
                                annotations = listOf(
                                    LsiAnnotationSpec(
                                        type = LsiClassName.bestGuess("com.fasterxml.jackson.annotation.JsonIgnore"),
                                        useSiteTarget = LsiAnnotationUseSiteTarget.FIELD,
                                    )
                                ),
                                modifiers = setOf(LsiModifier.PUBLIC),
                                backingFieldModifiers = setOf(LsiModifier.PUBLIC),
                            )
                        )
                    )
                ),
            ).renderJavaSource()
        }

        assertTrue(source.contains("@JsonIgnore\n    public final String id;"), source)
        assertTrue(source.contains("public String getId()"), source)
    }

    @Test
    fun `respect explicit backing field modifiers for accessor property`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Book",
            types = listOf(
                LsiTypeSpec(
                    name = "Book",
                    kind = LsiTypeSpecKind.CLASS,
                    properties = listOf(
                        LsiPropertySpec(
                            name = "name",
                            type = LsiClassName.bestGuess("java.lang.String"),
                            modifiers = setOf(LsiModifier.PUBLIC),
                            mutable = true,
                            backingFieldModifiers = setOf(LsiModifier.PROTECTED),
                        )
                    )
                )
            ),
        ).renderJavaSource()

        assertTrue(source.contains("protected String name;"), source)
        assertTrue(source.contains("public String getName()"), source)
        assertTrue(source.contains("public void setName(String value)"), source)
    }

    @Test
    fun `respect explicit backing field modifiers for direct field`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Book",
            types = listOf(
                LsiTypeSpec(
                    name = "Book",
                    kind = LsiTypeSpecKind.CLASS,
                    properties = listOf(
                        LsiPropertySpec(
                            name = "name",
                            type = LsiClassName.bestGuess("java.lang.String"),
                            modifiers = setOf(LsiModifier.PRIVATE),
                            backingFieldModifiers = setOf(LsiModifier.PROTECTED),
                        )
                    )
                )
            ),
        ).renderJavaSource()

        assertTrue(source.contains("protected final String name;"), source)
        assertTrue(!source.contains("getName()"), source)
    }

    @Test
    fun `render backing field when custom accessor explicitly asks for it`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Sample",
            types = listOf(
                LsiTypeSpec(
                    name = "Sample",
                    kind = LsiTypeSpecKind.CLASS,
                    properties = listOf(
                        LsiPropertySpec(
                            name = "name",
                            type = LsiClassName.bestGuess("java.lang.String"),
                            modifiers = setOf(LsiModifier.PUBLIC),
                            mutable = true,
                            getterStatements = listOf(
                                LsiReturnStatement(LsiNameExpression("name"))
                            ),
                            setterStatements = listOf(
                                LsiAssignmentStatement(
                                    target = LsiPropertyAccessExpression(LsiThisExpression, "name"),
                                    expression = LsiNameExpression("value")
                                )
                            ),
                            backingFieldModifiers = setOf(LsiModifier.PROTECTED),
                        )
                    )
                )
            ),
        ).renderJavaSource()

        assertTrue(source.contains("protected String name;"), source)
        assertTrue(source.contains("public String getName()"), source)
        assertTrue(source.contains("public void setName(String value)"), source)
    }

    @Test
    fun `reject use site target on direct field`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            LsiFileSpec(
                packageName = "test.sample",
                name = "Constants",
                types = listOf(
                    LsiTypeSpec(
                        name = "Constants",
                        kind = LsiTypeSpecKind.CLASS,
                        properties = listOf(
                            LsiPropertySpec(
                                name = "ID",
                                type = LsiClassName.bestGuess("java.lang.String"),
                                modifiers = setOf(LsiModifier.PRIVATE, LsiModifier.STATIC, LsiModifier.FINAL),
                                initializer = LsiLiteralExpression("demo"),
                                annotations = listOf(
                                    LsiAnnotationSpec(
                                        type = LsiClassName.bestGuess("com.fasterxml.jackson.annotation.JsonIgnore"),
                                        useSiteTarget = LsiAnnotationUseSiteTarget.GET,
                                    )
                                )
                            )
                        )
                    )
                )
            ).renderJavaSource()
        }

        assertTrue(error.message!!.contains("annotation target"))
        assertTrue(error.message!!.contains("FIELD"))
        assertTrue(error.message!!.contains("property 'ID'"))
    }

    @Test
    fun `render static field and named lambda`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Predicates",
            types = listOf(
                LsiTypeSpec(
                    name = "Predicates",
                    kind = LsiTypeSpecKind.CLASS,
                    properties = listOf(
                        LsiPropertySpec(
                            name = "MATCHER",
                            type = LsiParameterizedTypeName(
                                rawType = LsiClassName.bestGuess("java.util.function.Predicate"),
                                typeArguments = listOf(LsiClassName.bestGuess("java.lang.Class"))
                            ),
                            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC, LsiModifier.FINAL),
                            initializer = LsiLambdaExpression(
                                mode = LsiLambdaMode.EXPRESSION,
                                parameterNames = listOf("type"),
                                expression = LsiCallExpression(
                                    receiver = LsiCallExpression(
                                        receiver = LsiNameExpression("type"),
                                        name = "getName"
                                    ),
                                    name = "startsWith",
                                    arguments = listOf(LsiLiteralExpression("demo."))
                                )
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("static final Predicate<Class> MATCHER"), source)
        assertTrue(source.contains("type ->"), source)
        assertTrue(source.contains("type.getName().startsWith(\"demo.\")"), source)
    }

    @Test
    fun `synthesize java overloads for default arguments`() {
        val source = LsiFileSpec(
            packageName = "test.sample",
            name = "Factory",
            types = listOf(
                LsiTypeSpec(
                    name = "Factory",
                    kind = LsiTypeSpecKind.CLASS,
                    callables = listOf(
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.CONSTRUCTOR,
                            modifiers = setOf(LsiModifier.PUBLIC),
                            parameters = listOf(
                                LsiParameterSpec(
                                    name = "message",
                                    type = LsiClassName.bestGuess("java.lang.String"),
                                    defaultValue = LsiCodeBlock.of("null"),
                                ),
                                LsiParameterSpec(
                                    name = "flag",
                                    type = LsiClassName.bestGuess("kotlin.Boolean"),
                                    defaultValue = LsiCodeBlock.of("false"),
                                ),
                            )
                        ),
                        LsiCallableSpec(
                            kind = LsiCallableSpecKind.FUNCTION,
                            name = "create",
                            modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
                            parameters = listOf(
                                LsiParameterSpec(
                                    name = "message",
                                    type = LsiClassName.bestGuess("java.lang.String"),
                                    defaultValue = LsiCodeBlock.of("null"),
                                ),
                                LsiParameterSpec(
                                    name = "flag",
                                    type = LsiClassName.bestGuess("kotlin.Boolean"),
                                    defaultValue = LsiCodeBlock.of("false"),
                                ),
                                LsiParameterSpec(
                                    name = "code",
                                    type = LsiClassName.bestGuess("java.lang.String"),
                                ),
                            ),
                            returnType = LsiClassName.bestGuess("java.lang.String"),
                            statements = listOf(
                                LsiReturnStatement(LsiNameExpression("code"))
                            )
                        )
                    )
                )
            )
        ).renderJavaSource()

        assertTrue(source.contains("public Factory()"), source)
        assertTrue(source.contains("this(null, false);"), source)
        assertTrue(source.contains("public Factory(String message)"), source)
        assertTrue(source.contains("this(message, false);"), source)
        assertTrue(source.contains("public static String create(String message, String code)"), source)
        assertTrue(source.contains("return create(message, false, code);"), source)
        assertTrue(source.contains("public static String create(String code)"), source)
        assertTrue(source.contains("return create(null, false, code);"), source)
    }
}
