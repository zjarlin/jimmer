package org.babyfish.jimmer.compiler.transactional

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import site.addzero.lsi.compiler.CompilerFeatureLoader
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.transactional.TransactionalConstructor
import site.addzero.lsi.jimmer.transactional.TransactionalMethod
import site.addzero.lsi.jimmer.transactional.TransactionalMethodSourceKind
import site.addzero.lsi.jimmer.transactional.TransactionalParameter
import site.addzero.lsi.jimmer.transactional.TransactionalSchema
import site.addzero.lsi.jimmer.transactional.TransactionalSqlClient
import site.addzero.lsi.jimmer.transactional.TransactionalType
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class TransactionalRendererTest {

    @Test
    fun `java renderer matches legacy golden and compiles`() {
        val (schema, workspace) = javaFixture()
        val artifact = LsiJavaPoetRenderer().render(
            schema.toLsiSourceArtifacts(workspace).single()
        )

        assertContentEquals(golden("ServiceATx.java"), artifact.content.encodeToByteArray())
        assertEquals(setOf(JAVA_SERVICE_ID), artifact.originatingSymbols)
        assertEquals(workspace.sources.toSet(), artifact.originatingSources)
        assertTransactionalDependencies(schema.types.single(), artifact.dependencySymbols)
        assertEquals(workspace.sources.toSet(), artifact.dependencySources)
        compileJava(artifact.content)
    }

    @Test
    fun `kotlin renderer matches legacy golden and compiles`() {
        val (schema, workspace) = kotlinFixture()
        val artifact = LsiKotlinPoetRenderer().render(
            schema.toLsiSourceArtifacts(workspace).single()
        )

        assertContentEquals(golden("ServiceATx.kt"), artifact.content.encodeToByteArray())
        assertEquals(setOf(KOTLIN_SERVICE_ID), artifact.originatingSymbols)
        assertEquals(workspace.sources.toSet(), artifact.originatingSources)
        assertTransactionalDependencies(schema.types.single(), artifact.dependencySymbols)
        assertEquals(workspace.sources.toSet(), artifact.dependencySources)
        compileKotlin(artifact.content)
    }

    @Test
    fun `transactional feature is loaded with shared compiler`() {
        val featureKeys = CompilerFeatureLoader.load().map { feature -> feature.key }

        assertTrue(TransactionalFeature.Key in featureKeys)
    }

    @Test
    fun `renderers preserve return type annotations`() {
        val returnAnnotation = LsiAnnotation(
            type = LsiSymbolId.type("demo.TypeMarker"),
            useSiteTarget = LsiAnnotationUseSiteTarget.RETURN_TYPE,
        )
        val markerDeclaration = annotationDeclaration("TypeMarker", "FUNCTION")
        val (javaSchema, javaWorkspace) = javaFixture()
        val resolvedJavaWorkspace = javaWorkspace.withDeclaration(markerDeclaration)
        val javaArtifact = LsiJavaPoetRenderer().render(
            javaSchema
                .withAnnotatedReturnType(returnAnnotation)
                .toLsiSourceArtifacts(resolvedJavaWorkspace)
                .single()
        )
        val (kotlinSchema, kotlinWorkspace) = kotlinFixture()
        val resolvedKotlinWorkspace = kotlinWorkspace.withDeclaration(markerDeclaration)
        val kotlinArtifact = LsiKotlinPoetRenderer().render(
            kotlinSchema
                .withAnnotatedReturnType(returnAnnotation)
                .toLsiSourceArtifacts(resolvedKotlinWorkspace)
                .single()
        )

        assertEquals(1, javaArtifact.content.lineSequence().count { line -> "@TypeMarker" in line })
        assertEquals(1, kotlinArtifact.content.lineSequence().count { line -> "@TypeMarker" in line })
    }

    @Test
    fun `renderers preserve exact uppercase package and lowercase type boundaries`() {
        val outerId = LsiSymbolId.type("UPPER.pkg.lowercase")
        val nestedId = LsiSymbolId.type("UPPER.pkg.lowercase.item")
        val outerDeclaration = LsiTypeDeclaration(
            id = outerId,
            name = "lowercase",
            qualifiedName = "UPPER.pkg.lowercase",
            kind = LsiTypeDeclarationKind.CLASS,
            origin = LsiOrigin(LsiOriginKind.BINARY),
        )
        val nestedDeclaration = LsiTypeDeclaration(
            id = nestedId,
            name = "item",
            qualifiedName = "UPPER.pkg.lowercase.item",
            kind = LsiTypeDeclarationKind.CLASS,
            enclosingTypeId = outerId,
            origin = LsiOrigin(LsiOriginKind.BINARY),
        )
        val expectedTypeNames = listOf(
            LsiTypeName(outerId, "UPPER.pkg", listOf("lowercase")),
            LsiTypeName(nestedId, "UPPER.pkg", listOf("lowercase", "item")),
        )

        val (javaSchema, javaWorkspace) = javaFixture()
        val javaPoetArtifact = javaSchema
            .withMethodReturnTypes(LsiDeclaredType(outerId), LsiDeclaredType(nestedId))
            .toLsiSourceArtifacts(
                javaWorkspace.withDeclaration(outerDeclaration).withDeclaration(nestedDeclaration)
            )
            .single()
        val (kotlinSchema, kotlinWorkspace) = kotlinFixture()
        val kotlinPoetArtifact = kotlinSchema
            .withMethodReturnTypes(LsiDeclaredType(outerId), LsiDeclaredType(nestedId))
            .toLsiSourceArtifacts(
                kotlinWorkspace.withDeclaration(outerDeclaration).withDeclaration(nestedDeclaration)
            )
            .single()

        expectedTypeNames.forEach { expected ->
            assertEquals(expected, javaPoetArtifact.typeNames.single { it.typeId == expected.typeId })
            assertEquals(expected, kotlinPoetArtifact.typeNames.single { it.typeId == expected.typeId })
        }
        val javaContent = LsiJavaPoetRenderer().render(javaPoetArtifact).content
        val kotlinContent = LsiKotlinPoetRenderer().render(kotlinPoetArtifact).content
        assertContains(javaContent, "import UPPER.pkg.lowercase;")
        assertContains(javaContent, "public lowercase a()")
        assertContains(javaContent, "lowercase.item b()")
        assertContains(kotlinContent, "import UPPER.pkg.lowercase")
        assertContains(kotlinContent, "override fun a(): lowercase")
        assertContains(kotlinContent, "internal override fun b(): lowercase.item")
    }

    @Test
    fun `kotlin renderer consumes frozen constructor parameter annotations`() {
        val parameterMarker = annotationDeclaration("ParameterMarker", "VALUE_PARAMETER")
        val getterOnlyMarker = annotationDeclaration("ParameterMarker", "PROPERTY_GETTER")
        val (schema, workspace) = kotlinFixture()
        val annotatedSchema = schema.copy(
            types = schema.types.map { type ->
                type.copy(
                    constructors = type.constructors.map { constructor ->
                        constructor.copy(
                            parameters = constructor.parameters.map { parameter ->
                                parameter.copy(annotations = listOf(LsiAnnotation(parameterMarker.id)))
                            },
                        )
                    },
                )
            },
        )

        val parameterWorkspace = LsiWorkspace(
            sources = workspace.sources + listOfNotNull(parameterMarker.origin.source),
            declarations = workspace.declarations + parameterMarker,
        )
        val getterWorkspace = LsiWorkspace(
            sources = workspace.sources + listOfNotNull(getterOnlyMarker.origin.source),
            declarations = workspace.declarations + getterOnlyMarker,
        )
        val parameterArtifact = LsiKotlinPoetRenderer().render(
            annotatedSchema.toLsiSourceArtifacts(parameterWorkspace).single()
        )
        val getterArtifact = LsiKotlinPoetRenderer().render(
            annotatedSchema.toLsiSourceArtifacts(getterWorkspace).single()
        )

        assertContains(parameterArtifact.content, "@ParameterMarker")
        assertEquals(parameterArtifact.content, getterArtifact.content)
    }

    @Test
    fun `lowering preserves constructor and vararg platform contracts`() {
        val (javaSchema, javaWorkspace) = javaFixture()
        val javaType = javaSchema.types.single()
        val javaConstructor = javaType.constructors.single()
        val typeParameterId = LsiSymbolId.typeParameter(javaConstructor.id, "T")
        val javaVararg = TransactionalParameter(
            id = LsiSymbolId.parameter(javaConstructor.id, 1, "values"),
            name = "values",
            index = 1,
            type = LsiTypeParameterRef(typeParameterId),
            vararg = true,
            hasDefault = false,
            annotations = emptyList(),
        )
        val javaArtifact = LsiJavaPoetRenderer().render(
            javaSchema.copy(
                types = listOf(
                    javaType.copy(
                        constructors = listOf(
                            javaConstructor.copy(
                                typeParameters = listOf(LsiTypeParameter(typeParameterId, "T")),
                                parameters = javaConstructor.parameters + javaVararg,
                                thrownTypes = listOf(LsiDeclaredType(IO_EXCEPTION)),
                            )
                        ),
                    )
                )
            ).toLsiSourceArtifacts(javaWorkspace).single()
        )

        assertContains(
            javaArtifact.content,
            "<T> ServiceATx(JSqlClient sqlClient, T... values) throws IOException",
        )
        assertContains(javaArtifact.content, "super(sqlClient, values);")
        assertFalse("public <T> ServiceATx" in javaArtifact.content)

        val (kotlinSchema, kotlinWorkspace) = kotlinFixture()
        val kotlinType = kotlinSchema.types.single()
        val kotlinConstructor = kotlinType.constructors.single()
        val kotlinMethod = kotlinType.methods.first()
        val kotlinVararg = TransactionalParameter(
            id = LsiSymbolId.parameter(kotlinMethod.id, 0, "values"),
            name = "values",
            index = 0,
            type = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
            vararg = true,
            hasDefault = false,
            annotations = emptyList(),
        )
        val kotlinArtifact = LsiKotlinPoetRenderer().render(
            kotlinSchema.copy(
                types = listOf(
                    kotlinType.copy(
                        constructors = listOf(
                            kotlinConstructor.copy(
                                primary = false,
                                visibility = LsiVisibility.PROTECTED,
                                thrownTypes = listOf(LsiDeclaredType(IO_EXCEPTION)),
                            )
                        ),
                        methods = listOf(
                            kotlinMethod.copy(
                                parameters = listOf(kotlinVararg),
                                thrownTypes = listOf(LsiDeclaredType(IO_EXCEPTION)),
                            )
                        ) + kotlinType.methods.drop(1),
                    )
                )
            ).toLsiSourceArtifacts(kotlinWorkspace).single()
        )

        assertContains(kotlinArtifact.content, "protected constructor(sqlClient: KSqlClient) : super(sqlClient)")
        assertContains(kotlinArtifact.content, "override fun a(vararg values: String): Unit")
        assertContains(kotlinArtifact.content, "super.a(*values)")
        assertFalse("@Throws" in kotlinArtifact.content)
    }

    private fun javaFixture(): Pair<TransactionalSchema, LsiWorkspace> {
        val source = LsiSource.of("org/babyfish/jimmer/sql/transaction/ServiceA.java", LsiLanguage.JAVA)
        val constructorId = LsiSymbolId.constructor(
            JAVA_SERVICE_ID,
            listOf("type:org.babyfish.jimmer.sql.JSqlClient"),
        )
        val constructorParameter = parameter(
            constructorId,
            "sqlClient",
            LsiDeclaredType(J_SQL_CLIENT),
        )
        val type = transactionalType(
            serviceId = JAVA_SERVICE_ID,
            packageName = "org.babyfish.jimmer.sql.transaction",
            sqlClientType = LsiDeclaredType(J_SQL_CLIENT),
            language = LsiLanguage.JAVA,
            constructor = TransactionalConstructor(
                id = constructorId,
                primary = false,
                visibility = LsiVisibility.PUBLIC,
                parameters = listOf(constructorParameter),
                typeParameters = emptyList(),
                thrownTypes = emptyList(),
                documentation = null,
                copiedAnnotations = emptyList(),
            ),
            methods = listOf(
                method(
                    id = LsiSymbolId.property(JAVA_SERVICE_ID, "a"),
                    name = "a",
                    sourceKind = TransactionalMethodSourceKind.PROPERTY_GETTER,
                    visibility = LsiVisibility.PUBLIC,
                    returnType = LsiPrimitiveType(LsiPrimitiveKind.INT),
                    propagation = "MANDATORY",
                    classLevel = true,
                ),
                method(
                    id = LsiSymbolId.function(JAVA_SERVICE_ID, "b"),
                    name = "b",
                    visibility = LsiVisibility.PACKAGE_PRIVATE,
                    returnType = LsiPrimitiveType(LsiPrimitiveKind.VOID),
                    propagation = "REQUIRES_NEW",
                    classLevel = false,
                ),
            ),
        )
        return TransactionalSchema(listOf(type)) to workspace(JAVA_SERVICE_ID, source)
    }

    private fun kotlinFixture(): Pair<TransactionalSchema, LsiWorkspace> {
        val source = LsiSource.of("org/babyfish/jimmer/sql/kt/transaction/ServiceA.kt", LsiLanguage.KOTLIN)
        val constructorId = LsiSymbolId.constructor(
            KOTLIN_SERVICE_ID,
            listOf("type:org.babyfish.jimmer.sql.kt.KSqlClient"),
        )
        val constructorParameter = parameter(
            constructorId,
            "sqlClient",
            LsiDeclaredType(K_SQL_CLIENT),
        )
        val type = transactionalType(
            serviceId = KOTLIN_SERVICE_ID,
            packageName = "org.babyfish.jimmer.sql.kt.transaction",
            sqlClientType = LsiDeclaredType(K_SQL_CLIENT),
            language = LsiLanguage.KOTLIN,
            constructor = TransactionalConstructor(
                id = constructorId,
                primary = true,
                visibility = LsiVisibility.PUBLIC,
                parameters = listOf(constructorParameter),
                typeParameters = emptyList(),
                thrownTypes = emptyList(),
                documentation = null,
                copiedAnnotations = emptyList(),
            ),
            methods = listOf(
                method(
                    id = LsiSymbolId.function(KOTLIN_SERVICE_ID, "a"),
                    name = "a",
                    visibility = LsiVisibility.PUBLIC,
                    returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
                    propagation = "MANDATORY",
                    classLevel = true,
                ),
                method(
                    id = LsiSymbolId.function(KOTLIN_SERVICE_ID, "b"),
                    name = "b",
                    visibility = LsiVisibility.INTERNAL,
                    returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
                    propagation = "REQUIRES_NEW",
                    classLevel = false,
                ),
            ),
        )
        return TransactionalSchema(listOf(type)) to workspace(KOTLIN_SERVICE_ID, source)
    }

    private fun transactionalType(
        serviceId: LsiSymbolId,
        packageName: String,
        sqlClientType: LsiDeclaredType,
        language: LsiLanguage,
        constructor: TransactionalConstructor,
        methods: List<TransactionalMethod>,
    ): TransactionalType {
        return TransactionalType(
            id = serviceId,
            qualifiedName = "$packageName.ServiceA",
            packageName = packageName,
            simpleName = "ServiceA",
            generatedSimpleName = "ServiceATx",
            visibility = LsiVisibility.PUBLIC,
            modality = LsiModality.OPEN,
            copiedAnnotations = emptyList(),
            targetAnnotationTypeId = LsiSymbolId.type("$packageName.Component"),
            sqlClient = TransactionalSqlClient(
                logicalId = LsiSymbolId.property(serviceId, "sqlClient"),
                declarationId = if (language == LsiLanguage.JAVA) {
                    LsiSymbolId.field(serviceId, "sqlClient")
                } else {
                    LsiSymbolId.property(serviceId, "sqlClient")
                },
                name = "sqlClient",
                type = sqlClientType,
                language = language,
            ),
            constructors = listOf(constructor),
            methods = methods,
        )
    }

    private fun method(
        id: LsiSymbolId,
        name: String,
        sourceKind: TransactionalMethodSourceKind = TransactionalMethodSourceKind.FUNCTION,
        visibility: LsiVisibility,
        returnType: LsiTypeRef,
        propagation: String,
        classLevel: Boolean,
    ): TransactionalMethod {
        return TransactionalMethod(
            id = id,
            name = name,
            sourceKind = sourceKind,
            visibility = visibility,
            modality = LsiModality.OPEN,
            returnType = returnType,
            parameters = emptyList(),
            typeParameters = emptyList(),
            thrownTypes = emptyList(),
            documentation = null,
            copiedAnnotations = emptyList(),
            propagation = propagation,
            classLevel = classLevel,
        )
    }

    private fun parameter(
        callableId: LsiSymbolId,
        name: String,
        type: LsiDeclaredType,
    ): TransactionalParameter {
        return TransactionalParameter(
            id = LsiSymbolId.parameter(callableId, 0, name),
            name = name,
            index = 0,
            type = type,
            vararg = false,
            hasDefault = false,
            annotations = emptyList(),
        )
    }

    private fun workspace(serviceId: LsiSymbolId, source: LsiSource): LsiWorkspace {
        val packageName = serviceId.requireTypeQualifiedName().substringBeforeLast('.')
        return LsiWorkspace(
            sources = listOf(source),
            declarations = listOf(
                LsiTypeDeclaration(
                    id = serviceId,
                    name = "ServiceA",
                    qualifiedName = source.path.removeSuffix(".${source.path.substringAfterLast('.')}").replace('/', '.'),
                    kind = LsiTypeDeclarationKind.CLASS,
                    modality = LsiModality.OPEN,
                    origin = LsiOrigin(LsiOriginKind.SOURCE, source),
                ),
                LsiTypeDeclaration(
                    id = LsiSymbolId.type("$packageName.Component"),
                    name = "Component",
                    qualifiedName = "$packageName.Component",
                    kind = LsiTypeDeclarationKind.ANNOTATION,
                    origin = LsiOrigin(LsiOriginKind.SOURCE, source),
                ),
            ),
        )
    }

    private fun annotationDeclaration(
        simpleName: String,
        target: String,
    ): LsiTypeDeclaration {
        val source = LsiSource.of("demo/$simpleName.kt", LsiLanguage.KOTLIN)
        return LsiTypeDeclaration(
            id = LsiSymbolId.type("demo.$simpleName"),
            name = simpleName,
            qualifiedName = "demo.$simpleName",
            kind = LsiTypeDeclarationKind.ANNOTATION,
            annotations = listOf(
                LsiAnnotation(
                    type = KOTLIN_TARGET,
                    arguments = mapOf(
                        "allowedTargets" to LsiAnnotationArgument(
                            value = LsiAnnotationValue.ArrayValue(
                                listOf(LsiAnnotationValue.EnumValue(KOTLIN_ANNOTATION_TARGET, target)),
                            ),
                            origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                        ),
                    ),
                ),
            ),
            origin = LsiOrigin(LsiOriginKind.SOURCE, source),
        )
    }

    private fun LsiWorkspace.withDeclaration(declaration: LsiTypeDeclaration): LsiWorkspace {
        return LsiWorkspace(
            sources = sources + listOfNotNull(declaration.origin.source),
            declarations = declarations + declaration,
        )
    }

    private fun TransactionalSchema.withAnnotatedReturnType(
        annotation: LsiAnnotation,
    ): TransactionalSchema {
        return copy(
            types = types.map { type ->
                type.copy(
                    methods = type.methods.mapIndexed { index, method ->
                        if (index == 0) {
                            method.copy(
                                returnType = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
                                copiedAnnotations = method.copiedAnnotations + annotation,
                            )
                        } else {
                            method
                        }
                    }
                )
            }
        )
    }

    private fun TransactionalSchema.withMethodReturnTypes(
        vararg returnTypes: LsiTypeRef,
    ): TransactionalSchema {
        return copy(
            types = types.map { type ->
                require(type.methods.size == returnTypes.size) {
                    "测试返回类型数量必须与事务方法数量一致"
                }
                type.copy(
                    methods = type.methods.mapIndexed { index, method ->
                        method.copy(returnType = returnTypes[index])
                    },
                )
            },
        )
    }

    private fun golden(name: String): ByteArray {
        return requireNotNull(javaClass.getResource("/transactional/$name")).readBytes()
    }

    private fun compileJava(content: String) {
        val projectDir = createTempDirectory(prefix = "jimmer-transactional-renderer-test").toFile()
        val sourceDir = projectDir.resolve("src/org/babyfish/jimmer/sql/transaction")
        val generatedSource = sourceDir.resolve("ServiceATx.java")
        val serviceSource = sourceDir.resolve("ServiceA.java")
        val output = projectDir.resolve("classes")
        sourceDir.mkdirs()
        generatedSource.writeText(content)
        serviceSource.writeText(
            """
            package org.babyfish.jimmer.sql.transaction;

            import org.babyfish.jimmer.sql.JSqlClient;

            @interface Component {}

            public class ServiceA {
                protected final JSqlClient sqlClient;

                public ServiceA(JSqlClient sqlClient) {
                    this.sqlClient = sqlClient;
                }

                public int a() {
                    return 0;
                }

                void b() {}
            }
            """.trimIndent()
        )
        output.mkdirs()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler() ?: error("Renderer test requires a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { manager ->
            manager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(output))
            compiler.getTask(
                null,
                manager,
                diagnostics,
                listOf("-classpath", System.getProperty("java.class.path")),
                null,
                manager.getJavaFileObjects(generatedSource, serviceSource),
            ).call()
        }
        assertTrue(success, diagnostics.diagnostics.joinToString("\n"))
    }

    private fun compileKotlin(content: String) {
        val projectDir = createTempDirectory(prefix = "jimmer-transactional-kotlin-renderer-test").toFile()
        val sourceDir = projectDir.resolve("src/org/babyfish/jimmer/sql/kt/transaction").apply { mkdirs() }
        val generatedSource = sourceDir.resolve("ServiceATx.kt").apply { writeText(content) }
        val serviceSource = sourceDir.resolve("ServiceA.kt").apply {
            writeText(
                """
                package org.babyfish.jimmer.sql.kt.transaction

                import org.babyfish.jimmer.sql.kt.KSqlClient

                annotation class Component

                open class ServiceA(protected val sqlClient: KSqlClient) {
                    open fun a(): Unit = Unit

                    internal open fun b(): Unit = Unit
                }
                """.trimIndent()
            )
        }
        val output = projectDir.resolve("classes").apply { mkdirs() }
        val messages = ByteArrayOutputStream()
        val exitCode = PrintStream(messages, true, StandardCharsets.UTF_8).use { stream ->
            K2JVMCompiler().exec(
                stream,
                "-no-stdlib",
                "-no-reflect",
                "-classpath",
                System.getProperty("java.class.path"),
                "-d",
                output.absolutePath,
                generatedSource.absolutePath,
                serviceSource.absolutePath,
            )
        }
        assertEquals(ExitCode.OK, exitCode, messages.toString(StandardCharsets.UTF_8))
    }

    private fun assertTransactionalDependencies(
        type: TransactionalType,
        dependencySymbols: Set<LsiSymbolId>,
    ) {
        val expected = buildSet {
            add(type.id)
            add(type.sqlClient.logicalId)
            add(type.sqlClient.declarationId)
            add((type.sqlClient.type as LsiDeclaredType).declarationId)
            add(PROPAGATION)
            add(
                if (type.sqlClient.language == LsiLanguage.JAVA) {
                    JAVA_OVERRIDE
                } else {
                    KOTLIN_SUPPRESS
                }
            )
            type.targetAnnotationTypeId?.let(::add)
            type.constructors.forEach { constructor ->
                add(constructor.id)
                constructor.parameters.mapTo(this, TransactionalParameter::id)
            }
            type.methods.mapTo(this, TransactionalMethod::id)
        }
        assertTrue(dependencySymbols.containsAll(expected), dependencySymbols.toString())
    }

    private companion object {
        val JAVA_SERVICE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.transaction.ServiceA")
        val KOTLIN_SERVICE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.transaction.ServiceA")
        val J_SQL_CLIENT = LsiSymbolId.type("org.babyfish.jimmer.sql.JSqlClient")
        val K_SQL_CLIENT = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.KSqlClient")
        val PROPAGATION = LsiSymbolId.type("org.babyfish.jimmer.sql.transaction.Propagation")
        val IO_EXCEPTION = LsiSymbolId.type("java.io.IOException")
        val JAVA_OVERRIDE = LsiSymbolId.type("java.lang.Override")
        val KOTLIN_SUPPRESS = LsiSymbolId.type("kotlin.Suppress")
        val KOTLIN_TARGET = LsiSymbolId.type("kotlin.annotation.Target")
        val KOTLIN_ANNOTATION_TARGET = LsiSymbolId.type("kotlin.annotation.AnnotationTarget")
    }
}
