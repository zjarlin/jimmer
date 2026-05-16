package site.addzero.lsi.poet

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KotlinPoetTopLevelRenderTest {

    @Test
    fun `render top-level extension property and function`() {
        val fileSpec = LsiFileSpec(
            packageName = "test.sample",
            name = "TopLevelExtensions",
            memberImports = listOf(
                LsiImportSpec(
                    packageName = "demo.fetcher",
                    name = "by"
                )
            ),
            topLevelProperties = listOf(
                LsiPropertySpec(
                    name = "answer",
                    type = LsiClassName.bestGuess("kotlin.Int"),
                    receiverType = LsiClassName.bestGuess("kotlin.String"),
                    getterStatements = listOf(
                        LsiReturnStatement(LsiLiteralExpression(42))
                    )
                )
            ),
            topLevelCallables = listOf(
                LsiCallableSpec(
                    kind = LsiCallableSpecKind.FUNCTION,
                    name = "echo",
                    receiverType = LsiClassName.bestGuess("kotlin.String"),
                    parameters = listOf(
                        LsiParameterSpec(
                            name = "suffix",
                            type = LsiClassName.bestGuess("kotlin.String")
                        )
                    ),
                    returnType = LsiClassName.bestGuess("kotlin.String"),
                    statements = listOf(
                        LsiReturnStatement(
                            LsiCodeExpression(
                                LsiCodeBlock.of("this + suffix")
                            )
                        )
                    )
                )
            )
        )
        val rendered = fileSpec.renderKotlinSource()

        assertTrue(rendered.contains("import demo.fetcher.`by`"), rendered)
        assertTrue(rendered.contains("public val String.answer: Int"), rendered)
        assertTrue(rendered.contains("public fun String.echo("), rendered)
        assertTrue(rendered.contains("= this + suffix"), rendered)
    }

    @Test
    fun `render mutable property setter body`() {
        val rendered = LsiPropertySpec(
            name = "name",
            type = LsiClassName.bestGuess("kotlin.String"),
            mutable = true,
            getterStatements = listOf(
                LsiReturnStatement(LsiNameExpression("field"))
            ),
            setterStatements = listOf(
                LsiAssignmentStatement(
                    target = LsiNameExpression("field"),
                    expression = LsiNameExpression("value")
                )
            )
        ).toKotlinPoet().toString()

        assertTrue(rendered.contains("get()"), rendered)
        assertTrue(rendered.contains("= field"), rendered)
        assertTrue(rendered.contains("set(`value`)") || rendered.contains("set(value)"), rendered)
        assertTrue(rendered.contains("field = value"), rendered)
    }

    @Test
    fun `render primary constructor statements as init block`() {
        val rendered = LsiTypeSpec(
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
        ).toKotlinPoet().toString()

        assertTrue(rendered.contains("init {"), rendered)
        assertTrue(rendered.contains("current = x"), rendered)
    }

    @Test
    fun `render float literal with explicit suffix`() {
        val rendered = LsiTypeSpec(
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
        ).toKotlinPoet().toString()

        assertTrue(rendered.contains("0.0F"), rendered)
    }

    @Test
    fun `render long literal with explicit suffix`() {
        val rendered = LsiTypeSpec(
            name = "LongHolder",
            kind = LsiTypeSpecKind.CLASS,
            properties = listOf(
                LsiPropertySpec(
                    name = "value",
                    type = LsiClassName.bestGuess("kotlin.Long"),
                    initializer = LsiLiteralExpression(0L),
                )
            )
        ).toKotlinPoet().toString()

        assertTrue(rendered.contains("0L"), rendered)
    }

    @Test
    fun `render length expression with comparison operators`() {
        val rendered = LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "isLongName",
            parameters = listOf(
                LsiParameterSpec("name", LsiClassName.bestGuess("kotlin.String"))
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
        ).toKotlinPoet().toString()

        assertTrue(rendered.contains("= (name.length > 3)"), rendered)
    }

    @Test
    fun `render if statement with semantic property set`() {
        val rendered = LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "applyName",
            parameters = listOf(
                LsiParameterSpec("holder", LsiClassName.bestGuess("test.sample.Holder")),
                LsiParameterSpec("name", LsiClassName.bestGuess("kotlin.String")),
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
        ).toKotlinPoet().toString()

        assertTrue(rendered.contains("if (name != null)"), rendered)
        assertTrue(rendered.contains("holder.name = name"), rendered)
    }

    @Test
    fun `render semantic property get as kotlin property access`() {
        val rendered = LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "resolveId",
            parameters = listOf(
                LsiParameterSpec("holder", LsiClassName.bestGuess("test.sample.Holder"))
            ),
            returnType = LsiClassName.bestGuess("kotlin.Long"),
            statements = listOf(
                LsiReturnStatement(
                    LsiPropertyGetExpression(
                        receiver = LsiPropertyGetExpression(
                            receiver = LsiNameExpression("holder"),
                            name = "store",
                            type = LsiClassName.bestGuess("test.sample.Store").copyNullable(true),
                        ),
                        name = "id",
                        type = LsiClassName.bestGuess("kotlin.Long"),
                    )
                )
            )
        ).toKotlinPoet().toString()

        assertTrue(rendered.contains("holder.store.id"), rendered)
    }

    @Test
    fun `render collection size and make id only expressions`() {
        val rendered = LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "toStore",
            parameters = listOf(
                LsiParameterSpec(
                    "items",
                    LsiParameterizedTypeName(
                        rawType = LsiClassName.bestGuess("kotlin.collections.List"),
                        typeArguments = listOf(LsiClassName.bestGuess("kotlin.Long"))
                    )
                ),
                                LsiParameterSpec("id", LsiClassName.bestGuess("kotlin.Long")),
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
        ).toKotlinPoet().toString()

        assertTrue(rendered.contains("items.size"), rendered)
        assertTrue(
            rendered.contains("makeIdOnly(Store::class, items[0])") ||
                rendered.contains("org.babyfish.jimmer.kt.makeIdOnly(test.sample.Store::class, items[0])"),
            rendered
        )
    }

    @Test
    fun `render when statement with throw default`() {
        val rendered = LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "resolve",
            parameters = listOf(LsiParameterSpec("prop", LsiClassName.bestGuess("kotlin.String"))),
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
        ).toKotlinPoet().toString()

        assertTrue(rendered.contains("when (prop)"), rendered)
        assertTrue(rendered.contains("\"id\" ->"), rendered)
        assertTrue(rendered.contains("return 1"), rendered)
        assertTrue(rendered.contains("else ->"), rendered)
        assertTrue(
            rendered.contains("throw IllegalArgumentException(\"bad\")") ||
                rendered.contains("throw java.lang.IllegalArgumentException(\"bad\")"),
            rendered
        )
    }

    @Test
    fun `render binary expressions including identity comparison`() {
        val rendered = LsiCallableSpec(
            kind = LsiCallableSpecKind.FUNCTION,
            name = "changed",
            returnType = LsiClassName.bestGuess("kotlin.Boolean"),
            parameters = listOf(
                LsiParameterSpec("left", LsiClassName.bestGuess("kotlin.Any").copyNullable(true)),
                LsiParameterSpec("right", LsiClassName.bestGuess("kotlin.Any").copyNullable(true)),
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
        ).toKotlinPoet().toString()

        assertTrue(rendered.contains("left !== right"), rendered)
        assertTrue(rendered.contains("&&"), rendered)
        assertTrue(rendered.contains("31 * 2"), rendered)
    }

    @Test
    fun `render try finally statement`() {
        val rendered = LsiCallableSpec(
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
        ).toKotlinPoet().toString()

        assertTrue(rendered.contains("try {"), rendered)
        assertTrue(rendered.contains("doWork()"), rendered)
        assertTrue(rendered.contains("finally {"), rendered)
        assertTrue(rendered.contains("done = true"), rendered)
    }

    @Test
    fun `render for range statement`() {
        val rendered = LsiCallableSpec(
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
        ).toKotlinPoet().toString()

        assertTrue(rendered.contains("for (propId in 0 until 10)"), rendered)
        assertTrue(rendered.contains("target.show(propId, true)"), rendered)
    }

    @Test
    fun `render static property into companion object`() {
        val rendered = LsiTypeSpec(
            name = "Holder",
            kind = LsiTypeSpecKind.CLASS,
            properties = listOf(
                LsiPropertySpec(
                    name = "token",
                    type = LsiClassName.bestGuess("kotlin.String"),
                    modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
                    initializer = LsiLiteralExpression("demo")
                )
            )
        ).toKotlinPoet().toString()

        assertTrue(rendered.contains("companion object"), rendered)
        assertTrue(
            rendered.contains("@JvmField") ||
                rendered.contains("@kotlin.jvm.JvmField"),
            rendered
        )
        assertTrue(
            rendered.contains("val token: String = \"demo\"") ||
                rendered.contains("val token: kotlin.String = \"demo\""),
            rendered
        )
    }

    @Test
    fun `render lambda with named parameter`() {
        val rendered = LsiPropertySpec(
            name = "matcher",
            type = LsiParameterizedTypeName(
                rawType = LsiClassName.bestGuess("kotlin.Function1"),
                typeArguments = listOf(
                    LsiClassName.bestGuess("java.lang.Class"),
                    LsiClassName.bestGuess("kotlin.Boolean")
                )
            ),
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
        ).toKotlinPoet().toString()

        assertTrue(rendered.contains("{ type ->"), rendered)
        assertTrue(rendered.contains("type.getName().startsWith(\"demo.\")"), rendered)
    }

    @Test
    fun `render labeled callable reference`() {
        val rendered = LsiFileSpec(
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
        ).renderKotlinSource()

        assertTrue(rendered.contains("this@BookView::toImmutableImpl"), rendered)
    }

    @Test
    fun `render array expression`() {
        val rendered = LsiFileSpec(
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
        ).renderKotlinSource()

        assertTrue(rendered.contains("arrayOf(a, b)"), rendered)
    }

    @Test
    fun `render int array expression`() {
        val rendered = LsiTypeSpec(
            name = "Sample",
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
        ).toKotlinPoet().toString()

        assertTrue(rendered.contains("intArrayOf(1, 2)"), rendered)
    }
}
