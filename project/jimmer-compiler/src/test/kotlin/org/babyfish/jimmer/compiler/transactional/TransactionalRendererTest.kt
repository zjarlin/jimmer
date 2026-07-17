package org.babyfish.jimmer.compiler.transactional

import java.nio.charset.StandardCharsets
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProviders
import org.babyfish.jimmer.compiler.transactional.apt.TransactionalJavaRenderer
import org.babyfish.jimmer.compiler.transactional.ksp.TransactionalKotlinRenderer
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace

class TransactionalRendererTest {

    @Test
    fun `java renderer matches legacy golden and compiles`() {
        val (schema, workspace) = javaFixture()
        val artifact = TransactionalJavaRenderer().render(schema, workspace).single()

        assertEquals(golden("ServiceATx.java"), artifact.content)
        assertEquals(setOf(JAVA_SERVICE_ID), artifact.originatingSymbols)
        assertEquals(workspace.sources.toSet(), artifact.originatingSources)
        compileJava(artifact.content)
    }

    @Test
    fun `kotlin renderer matches legacy golden`() {
        val (schema, workspace) = kotlinFixture()
        val artifact = TransactionalKotlinRenderer().render(schema, workspace).single()

        assertEquals(golden("ServiceATx.kt"), artifact.content)
        assertEquals(setOf(KOTLIN_SERVICE_ID), artifact.originatingSymbols)
        assertEquals(workspace.sources.toSet(), artifact.originatingSources)
    }

    @Test
    fun `transactional feature is loaded with shared compiler`() {
        val featureIds = JimmerCompilerFeatureProviders.load().map { provider -> provider.descriptor.id }

        assertTrue("transactional" in featureIds)
    }

    @Test
    fun `renderers preserve return type annotations`() {
        val returnAnnotation = LsiAnnotation(
            type = LsiSymbolId.type("demo.TypeMarker"),
            useSiteTarget = LsiAnnotationUseSiteTarget.RETURN_TYPE,
        )
        val (javaSchema, javaWorkspace) = javaFixture()
        val javaArtifact = TransactionalJavaRenderer().render(
            javaSchema.withAnnotatedReturnType(returnAnnotation),
            javaWorkspace,
        ).single()
        val (kotlinSchema, kotlinWorkspace) = kotlinFixture()
        val kotlinArtifact = TransactionalKotlinRenderer().render(
            kotlinSchema.withAnnotatedReturnType(returnAnnotation),
            kotlinWorkspace,
        ).single()

        assertEquals(1, javaArtifact.content.lineSequence().count { line -> "@TypeMarker" in line })
        assertEquals(1, kotlinArtifact.content.lineSequence().count { line -> "@TypeMarker" in line })
    }

    private fun javaFixture(): Pair<TransactionalPrecompiledSchema, LsiWorkspace> {
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
            platform = TransactionalPlatform.JAVA,
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
        return TransactionalPrecompiledSchema(listOf(type)) to workspace(JAVA_SERVICE_ID, source)
    }

    private fun kotlinFixture(): Pair<TransactionalPrecompiledSchema, LsiWorkspace> {
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
            platform = TransactionalPlatform.KOTLIN,
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
        return TransactionalPrecompiledSchema(listOf(type)) to workspace(KOTLIN_SERVICE_ID, source)
    }

    private fun transactionalType(
        serviceId: LsiSymbolId,
        packageName: String,
        sqlClientType: LsiDeclaredType,
        platform: TransactionalPlatform,
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
                declarationId = if (platform == TransactionalPlatform.JAVA) {
                    LsiSymbolId.field(serviceId, "sqlClient")
                } else {
                    LsiSymbolId.property(serviceId, "sqlClient")
                },
                name = "sqlClient",
                type = sqlClientType,
                platform = platform,
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
                )
            ),
        )
    }

    private fun TransactionalPrecompiledSchema.withAnnotatedReturnType(
        annotation: LsiAnnotation,
    ): TransactionalPrecompiledSchema {
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

    private fun golden(name: String): String {
        return requireNotNull(javaClass.getResource("/transactional/$name")).readText()
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

    private companion object {
        val JAVA_SERVICE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.transaction.ServiceA")
        val KOTLIN_SERVICE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.transaction.ServiceA")
        val J_SQL_CLIENT = LsiSymbolId.type("org.babyfish.jimmer.sql.JSqlClient")
        val K_SQL_CLIENT = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.KSqlClient")
    }
}
