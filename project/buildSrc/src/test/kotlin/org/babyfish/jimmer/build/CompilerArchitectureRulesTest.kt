package org.babyfish.jimmer.build

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompilerArchitectureRulesTest {

    @Test
    fun `允许 Kotlin 别名与 Java 静态导入`() {
        val violations = findCompilerArchitectureViolations(
            sources = listOf(
                source("Sample.kt", "import\tsite.addzero.lsi.model.LsiTypeRef\tas\tTypeRef"),
                source("Sample.java", "import   static   java.util.Collections.emptyList;"),
            ),
            rules = rules(),
        )

        assertTrue(violations.isEmpty(), violations.toString())
    }

    @Test
    fun `仅精确 APT 与 KSP 渲染器目录允许 Poet`() {
        val violations = findCompilerArchitectureViolations(
            sources = listOf(
                sourceAt(
                    "src/main/java/org/babyfish/jimmer/compiler/render/apt/AptRenderer.java",
                    "import com.squareup.javapoet.TypeSpec;",
                ),
                sourceAt(
                    "src/main/kotlin/org/babyfish/jimmer/compiler/render/ksp/KspRenderer.kt",
                    "import com.squareup.kotlinpoet.TypeSpec",
                ),
                sourceAt(
                    "src/main/kotlin/org/babyfish/jimmer/compiler/render/common/CommonRenderer.kt",
                    "import com.squareup.kotlinpoet.TypeSpec",
                ),
                sourceAt(
                    "src/main/java/org/babyfish/jimmer/apt/dto/LegacyRenderer.java",
                    "import com.squareup.javapoet.TypeSpec;",
                ),
            ),
            rules = CompilerArchitectureRules(
                allowedPlatformPathSegments = setOf("apt", "ksp"),
                allowedPoetRelativePathPrefixes = POET_RENDERER_PREFIXES,
            ),
        )

        assertEquals(
            listOf(
                "src/main/java/org/babyfish/jimmer/apt/dto/LegacyRenderer.java:1: com.squareup.javapoet",
                "src/main/kotlin/org/babyfish/jimmer/compiler/render/common/CommonRenderer.kt:1: com.squareup.kotlinpoet",
            ),
            violations,
        )
    }

    @Test
    fun `平台 writer 允许平台 API 但禁止 Poet`() {
        val violations = findCompilerArchitectureViolations(
            sources = listOf(
                sourceAt(
                    "src/main/kotlin/org/babyfish/jimmer/compiler/lsi/apt/AptGeneratedArtifactWriter.kt",
                    "import javax.annotation.processing.Filer",
                ),
                sourceAt(
                    "src/main/kotlin/org/babyfish/jimmer/compiler/lsi/ksp/KspGeneratedArtifactWriter.kt",
                    "import com.google.devtools.ksp.processing.CodeGenerator",
                ),
                sourceAt(
                    "src/main/kotlin/org/babyfish/jimmer/compiler/lsi/apt/InvalidAptWriter.kt",
                    listOf(
                        "import javax.annotation.processing.Filer",
                        "import com.squareup.javapoet.JavaFile",
                    ).joinToString("\n"),
                ),
                sourceAt(
                    "src/main/kotlin/org/babyfish/jimmer/compiler/lsi/ksp/InvalidKspWriter.kt",
                    listOf(
                        "import com.google.devtools.ksp.processing.CodeGenerator",
                        "import com.squareup.kotlinpoet.FileSpec",
                    ).joinToString("\n"),
                ),
            ),
            rules = CompilerArchitectureRules(
                allowedPlatformPathSegments = setOf("apt", "ksp"),
                allowedPoetRelativePathPrefixes = POET_RENDERER_PREFIXES,
            ),
        )

        assertEquals(
            listOf(
                "src/main/kotlin/org/babyfish/jimmer/compiler/lsi/apt/InvalidAptWriter.kt:2: com.squareup.javapoet",
                "src/main/kotlin/org/babyfish/jimmer/compiler/lsi/ksp/InvalidKspWriter.kt:2: com.squareup.kotlinpoet",
            ),
            violations,
        )
    }

    @Test
    fun `拒绝旧 APT 与 KSP 处理器包声明`() {
        val violations = findCompilerArchitectureViolations(
            sources = listOf(
                sourceAt(
                    "src/main/java/org/babyfish/jimmer/apt/Legacy.java",
                    "package org.babyfish.jimmer.apt;",
                ),
                sourceAt(
                    "src/main/kotlin/org/babyfish/jimmer/ksp/Legacy.kt",
                    "package org.babyfish.jimmer.ksp",
                ),
            ),
            rules = CompilerArchitectureRules(
                additionalForbiddenNamespaces = setOf(
                    "org.babyfish.jimmer.apt",
                    "org.babyfish.jimmer.ksp",
                ),
            ),
        )

        assertEquals(
            listOf(
                "src/main/java/org/babyfish/jimmer/apt/Legacy.java:1: org.babyfish.jimmer.apt",
                "src/main/kotlin/org/babyfish/jimmer/ksp/Legacy.kt:1: org.babyfish.jimmer.ksp",
            ),
            violations,
        )
    }

    @Test
    fun `拒绝平台 javac Poet 与白名单外导入`() {
        val violations = findCompilerArchitectureViolations(
            sources = listOf(
                source(
                    "Sample.java",
                    listOf(
                        "import javax.lang.model.element.TypeElement;",
                        "import com.sun.source.util.Trees;",
                        "import com.squareup.javapoet.TypeSpec;",
                        "import\torg.babyfish.jimmer.runtime.ImmutableSpi;",
                        "import java.util.List; import org.example.Forbidden;",
                        "import org.\n example.Other;",
                    ).joinToString("\n"),
                )
            ),
            rules = rules(),
        )

        assertEquals(
            listOf(
                "src/main/java/Sample.java:1: forbidden import javax.lang.model.element.TypeElement",
                "src/main/java/Sample.java:1: javax.lang.model",
                "src/main/java/Sample.java:2: com.sun.source",
                "src/main/java/Sample.java:2: forbidden import com.sun.source.util.Trees",
                "src/main/java/Sample.java:3: com.squareup.javapoet",
                "src/main/java/Sample.java:3: forbidden import com.squareup.javapoet.TypeSpec",
                "src/main/java/Sample.java:4: forbidden import org.babyfish.jimmer.runtime.ImmutableSpi",
                "src/main/java/Sample.java:4: org.babyfish.jimmer.runtime",
                "src/main/java/Sample.java:5: forbidden import org.example.Forbidden",
                "src/main/java/Sample.java:6: forbidden import org.example.Other",
            ),
            violations,
        )
    }

    @Test
    fun `忽略注释和字符串中的限定名`() {
        val violations = findCompilerArchitectureViolations(
            sources = listOf(
                source(
                    "Sample.kt",
                    listOf(
                        "// com.squareup.kotlinpoet.TypeSpec",
                        "val runtimeName = \"org.babyfish.jimmer.runtime.ImmutableSpi\"",
                        "val processorName = \"\"\"javax.annotation.processing.Processor\"\"\"",
                        "/* outer /* com.sun.tools.javac.Main */ com.squareup.javapoet.TypeSpec */",
                    ).joinToString("\n"),
                )
            ),
            rules = rules(),
        )

        assertTrue(violations.isEmpty(), violations.toString())
    }

    @Test
    fun `检查 Kotlin 字符串模板表达式中的真实代码`() {
        val violations = findCompilerArchitectureViolations(
            sources = listOf(
                source(
                    "Template.kt",
                    "val type = \"\${org.babyfish.jimmer.runtime.ImmutableSpi::class}\"",
                )
            ),
            rules = rules(),
        )

        assertEquals(
            listOf("src/main/kt/Template.kt:1: org.babyfish.jimmer.runtime"),
            violations,
        )
    }

    @Test
    fun `检查被注释与换行分隔的 Java 限定名`() {
        val violations = findCompilerArchitectureViolations(
            sources = listOf(
                source(
                    "Split.java",
                    listOf(
                        "class Split {",
                        "  javax.lang./* split */model.element.TypeElement first;",
                        "  com.squareup.",
                        "    kotlinpoet.TypeSpec second;",
                        "  /* outer /* inner */ javax.tools.JavaFileObject third;",
                        "}",
                    ).joinToString("\n"),
                )
            ),
            rules = rules(),
        )

        assertEquals(
            listOf(
                "src/main/java/Split.java:2: javax.lang.model",
                "src/main/java/Split.java:3: com.squareup.kotlinpoet",
                "src/main/java/Split.java:5: javax.tools",
            ),
            violations,
        )
    }

    @Test
    fun `拒绝全部平台渲染器与编译器边界`() {
        val forbiddenNamespaces = listOf(
            "javax.annotation.processing",
            "javax.lang.model",
            "javax.tools",
            "com.google.devtools.ksp",
            "com.sun.source",
            "com.sun.tools.javac",
            "com.squareup.javapoet",
            "com.squareup.kotlinpoet",
            "org.babyfish.jimmer.compiler",
            "site.addzero.lsi.poet",
        )

        forbiddenNamespaces.forEach { namespace ->
            val violations = findCompilerArchitectureViolations(
                sources = listOf(
                    source("Forbidden.kt", "val type = $namespace.Forbidden::class"),
                ),
                rules = rules().copy(
                    additionalForbiddenNamespaces = setOf(
                        "org.babyfish.jimmer.compiler.",
                        "site.addzero.lsi.poet.",
                    ),
                ),
            )

            assertTrue(
                violations.any { violation -> namespace in violation },
                "$namespace was not rejected: $violations",
            )
        }
    }

    @Test
    fun `仅 lsi-jimmer 允许 DTO 纯语义导入`() {
        val source = source(
            "Dto.kt",
            "import org.babyfish.jimmer.dto.compiler.DtoCompiler",
        )

        val coreViolations = findCompilerArchitectureViolations(
            listOf(source),
            rules(),
        )
        val jimmerViolations = findCompilerArchitectureViolations(
            listOf(source),
            rules(
                allowedImports = ALLOWED_IMPORTS + "org.babyfish.jimmer.dto.compiler.",
            ),
        )

        assertEquals(1, coreViolations.size)
        assertTrue(jimmerViolations.isEmpty(), jimmerViolations.toString())
    }

    @Test
    fun `依赖违规诊断稳定排序`() {
        val violations = findCompilerArchitectureViolations(
            sources = emptyList(),
            rules = rules().copy(
                directDependencyIds = setOf("project:lsi-core", "project:jimmer-core"),
                allowedDirectDependencyIds = setOf("project:lsi-core"),
                resolvedProjectDependencyIds = setOf("project:lsi-core", "project:jimmer-sql"),
                allowedResolvedProjectDependencyIds = setOf("project:lsi-core"),
                resolvedModuleDependencyIds = setOf(
                    "module:com.squareup:kotlinpoet",
                    "module:com.squareup:javapoet",
                    "module:com.sun:tools",
                    "module:com.google.devtools.ksp:symbol-processing-api",
                    "module:jdk.tools:jdk.tools",
                    "module:org.babyfish.jimmer:jimmer-dto-compiler",
                    "module:org.babyfish.jimmer:jimmer-spring-boot-starter",
                ),
                forbiddenModuleDependencyPrefixes = setOf(
                    "module:com.squareup:kotlinpoet",
                    "module:com.squareup:javapoet",
                    "module:com.sun:tools",
                    "module:com.google.devtools.ksp:",
                    "module:jdk.tools:jdk.tools",
                    "module:org.babyfish.jimmer:",
                ),
                allowedResolvedModuleDependencyIds = setOf(
                    "module:org.babyfish.jimmer:jimmer-dto-compiler",
                ),
            ),
        )

        assertEquals(
            listOf(
                "dependency: forbidden direct dependency project:jimmer-core",
                "dependency: forbidden resolved module dependency module:com.google.devtools.ksp:symbol-processing-api",
                "dependency: forbidden resolved module dependency module:com.squareup:javapoet",
                "dependency: forbidden resolved module dependency module:com.squareup:kotlinpoet",
                "dependency: forbidden resolved module dependency module:com.sun:tools",
                "dependency: forbidden resolved module dependency module:jdk.tools:jdk.tools",
                "dependency: forbidden resolved module dependency module:org.babyfish.jimmer:jimmer-spring-boot-starter",
                "dependency: forbidden resolved project dependency project:jimmer-sql",
            ),
            violations,
        )
    }

    @Test
    fun `捕获运行时传递项目依赖`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val lsi = ProjectBuilder.builder().withName("lsi").withParent(root).build()
        val bridge = ProjectBuilder.builder().withName("bridge").withParent(root).build()
        val forbidden = ProjectBuilder.builder().withName("forbidden").withParent(root).build()
        val late = ProjectBuilder.builder().withName("late").withParent(root).build()
        val lateConfiguration = ProjectBuilder.builder()
            .withName("late-configuration")
            .withParent(root)
            .build()
        listOf(lsi, bridge, forbidden, late, lateConfiguration).forEach { project ->
            project.pluginManager.apply("java-library")
        }
        bridge.dependencies.add(
            "runtimeOnly",
            bridge.dependencies.project(mapOf("path" to forbidden.path)),
        )
        lsi.dependencies.add(
            "implementation",
            lsi.dependencies.project(mapOf("path" to bridge.path)),
        )
        val task = lsi.tasks.register(
            "verifyArchitecture",
            VerifyCompilerArchitecture::class.java,
        ).get()

        task.captureDependencies(
            configurations = lsi.configurations,
            allowedDirectIds = setOf("project:bridge"),
            allowedResolvedProjectIds = setOf("project:bridge"),
            forbiddenResolvedModulePrefixes = emptySet(),
        )
        lsi.dependencies.add(
            "implementation",
            lsi.dependencies.project(mapOf("path" to late.path)),
        )
        val ksp = lsi.configurations.create("ksp")
        ksp.dependencies.add(
            lsi.dependencies.project(mapOf("path" to lateConfiguration.path)),
        )

        assertEquals(
            setOf("project:bridge", "project:late", "project:late-configuration"),
            task.directDependencyIds.get(),
        )
        assertEquals(
            setOf("project:bridge", "project:forbidden", "project:late"),
            task.resolvedProjectDependencyIds.get(),
        )
    }

    private fun source(fileName: String, content: String): CompilerArchitectureSource {
        val language = fileName.substringAfterLast('.')
        return CompilerArchitectureSource("src/main/$language/$fileName", content)
    }

    private fun sourceAt(relativePath: String, content: String): CompilerArchitectureSource {
        return CompilerArchitectureSource(relativePath, content)
    }

    private fun rules(
        allowedImports: Set<String> = ALLOWED_IMPORTS,
    ): CompilerArchitectureRules {
        return CompilerArchitectureRules(
            allowedImportPrefixes = allowedImports,
            additionalForbiddenNamespaces = setOf(
                "org.babyfish.jimmer.runtime.",
                "site.addzero.lsi.poet.",
            ),
        )
    }

    private companion object {
        val ALLOWED_IMPORTS = setOf(
            "java.",
            "kotlin.",
            "site.addzero.lsi.",
        )

        val POET_RENDERER_PREFIXES = setOf(
            "src/main/java/org/babyfish/jimmer/compiler/render/apt/",
            "src/main/java/org/babyfish/jimmer/compiler/render/ksp/",
            "src/main/kotlin/org/babyfish/jimmer/compiler/render/apt/",
            "src/main/kotlin/org/babyfish/jimmer/compiler/render/ksp/",
        )
    }
}
