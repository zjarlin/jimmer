package org.babyfish.jimmer.compiler.dto

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.apt.JimmerProcessor
import org.babyfish.jimmer.compiler.ksp.JimmerProcessorProvider
import org.babyfish.jimmer.runtime.ImmutableSpi
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler

class JimmerDtoToStringCompilationTest {

    @Test
    fun `apt generated dto object methods compile and match runtime contracts`() {
        val classesDir = compileApt()

        assertEquals(EXPECTED_SNAPSHOTS, runtimeSnapshots(classesDir))
        assertEqualityAndHashContracts(classesDir)
        assertHibernateValidatorContracts(classesDir, kotlin = false)
        assertFuzzyDraftWriteContracts(classesDir)
        assertDynamicConstructorLoadedStateContracts(classesDir)
    }

    @Test
    fun `ksp generated dto object methods compile and match runtime contracts`() {
        val classesDir = compileKsp()

        assertEquals(EXPECTED_SNAPSHOTS, runtimeSnapshots(classesDir))
        assertEqualityAndHashContracts(classesDir)
        assertHibernateValidatorContracts(classesDir, kotlin = true)
        assertFuzzyDraftWriteContracts(classesDir)
        assertDynamicConstructorLoadedStateContracts(classesDir)
    }

    private fun compileApt(): File {
        val projectDir = fixtureProject("jimmer-dto-to-string-apt")
        val sourceFiles = writeSources(
            projectDir.resolve("src/main/java"),
            mapOf("demo/Sample.java" to JAVA_SOURCE),
        )
        writeDtoSource(projectDir)
        val processingClassesDir = projectDir.resolve("build/processing-classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to run DTO toString APT tests")
        val succeeded = compiler.getStandardFileManager(
            diagnostics,
            null,
            StandardCharsets.UTF_8,
        ).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(processingClassesDir))
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, listOf(generatedDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf(
                    "-proc:only",
                    "-classpath",
                    runtimeClasspathText(),
                    "-Ajimmer.dto.hibernateValidatorEnhancement=true",
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(JimmerProcessor()))
            task.call()
        }
        assertTrue(succeeded, diagnostics.toErrorMessage())
        val generatedFiles = generatedDir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "java" }
            .sortedBy(File::getAbsolutePath)
            .toList()
        assertGeneratedDtoFiles(generatedFiles, "java", "APT")
        val derivedSource = writeSources(
            projectDir.resolve("src/runtime/java"),
            mapOf("demo/dto/DerivedEmptyInput.java" to JAVA_DERIVED_SOURCE),
        )
        return compileWithJavac(projectDir, sourceFiles + generatedFiles + derivedSource)
    }

    private fun compileKsp(): File {
        val projectDir = fixtureProject("jimmer-dto-to-string-ksp")
        val sourceFiles = writeSources(
            projectDir.resolve("src/main/kotlin"),
            mapOf("demo/Sample.kt" to KOTLIN_SOURCE),
        )
        writeDtoSource(projectDir)
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-dto-to-string-compilation"
            sourceRoots = sourceFiles
            libraries = runtimeClasspath()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            this.kotlinOutputDir = kotlinOutputDir
            resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
            processorOptions = mapOf(
                "jimmer.dto.mutable" to "true",
                "jimmer.dto.hibernateValidatorEnhancement" to "true",
            )
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()
        val processingExitCode = KotlinSymbolProcessing(
            configuration,
            listOf(JimmerProcessorProvider()),
            logger,
        ).execute()
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, processingExitCode, logger.text())
        val generatedFiles = kotlinOutputDir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .sortedBy(File::getAbsolutePath)
            .toList()
        assertGeneratedDtoFiles(generatedFiles, "kt", "KSP")
        val derivedSource = writeSources(
            projectDir.resolve("src/runtime/kotlin"),
            mapOf("demo/dto/DerivedEmptyInput.kt" to KOTLIN_DERIVED_SOURCE),
        )
        return compileWithK2(projectDir, sourceFiles + generatedFiles + derivedSource)
    }

    private fun compileWithJavac(
        projectDir: File,
        sourceFiles: List<File>,
    ): File {
        val classesDir = projectDir.resolve("build/compiled-classes").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to compile generated DTO sources")
        val succeeded = compiler.getStandardFileManager(
            diagnostics,
            null,
            StandardCharsets.UTF_8,
        ).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf("-proc:none", "-classpath", runtimeClasspathText()),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            ).call()
        }
        assertTrue(succeeded, diagnostics.toErrorMessage())
        return classesDir
    }

    private fun compileWithK2(
        projectDir: File,
        sourceFiles: List<File>,
    ): File {
        val classesDir = projectDir.resolve("build/compiled-classes").apply(File::mkdirs)
        val messages = ByteArrayOutputStream()
        val arguments = buildList {
            add("-no-stdlib")
            add("-no-reflect")
            add("-jvm-target")
            add("17")
            add("-classpath")
            add(runtimeClasspathText())
            add("-d")
            add(classesDir.absolutePath)
            sourceFiles.mapTo(this) { file -> file.absolutePath }
        }
        val exitCode = PrintStream(messages, true, StandardCharsets.UTF_8).use { stream ->
            K2JVMCompiler().exec(stream, *arguments.toTypedArray())
        }
        assertEquals(ExitCode.OK, exitCode, messages.toString(StandardCharsets.UTF_8))
        return classesDir
    }

    private fun runtimeSnapshots(classesDir: File): List<String> {
        val urls = arrayOf(classesDir.toURI().toURL())
        return URLClassLoader(urls, javaClass.classLoader).use { classLoader ->
            val plain = newPlainArrayInput(
                classLoader = classLoader,
                chars = charArrayOf('A', 'Z'),
                numbers = intArrayOf(1, 2),
                labels = arrayOf("one", "two"),
                stars = arrayOf<Any>("star", 1),
                keyword = "keyword",
            )
            val dynamicEmpty = newDto(classLoader, "DynamicShadowInput")
            val dynamicLoadedNull = newDto(classLoader, "DynamicShadowInput").apply {
                setProperty("builder", null)
            }
            val dynamicMixed = newDto(classLoader, "DynamicShadowInput").apply {
                setMixedValues()
            }
            val fuzzyEmpty = newDto(classLoader, "FuzzyShadowInput")
            val fuzzyBuilder = newDto(classLoader, "FuzzyShadowInput").apply {
                setProperty("builder", "builder-value")
            }
            val fuzzyMixed = newDto(classLoader, "FuzzyShadowInput").apply {
                setMixedValues()
            }
            listOf(
                plain,
                dynamicEmpty,
                dynamicLoadedNull,
                dynamicMixed,
                fuzzyEmpty,
                fuzzyBuilder,
                fuzzyMixed,
            ).map { value -> value.toString().normalizeArrayIdentities() }
        }
    }

    private fun assertEqualityAndHashContracts(classesDir: File) {
        val urls = arrayOf(classesDir.toURI().toURL())
        URLClassLoader(urls, javaClass.classLoader).use { classLoader ->
            val specification = newDto(classLoader, "SampleSpecification")
            assertEquals(
                classLoader.loadClass("demo.Sample"),
                specification.javaClass.getMethod("entityType").invoke(specification),
            )

            val arrays = List(3) {
                newPlainArrayInput(
                    classLoader = classLoader,
                    chars = charArrayOf('A', 'Z'),
                    numbers = intArrayOf(1, 2),
                    labels = arrayOf("one", "two"),
                    stars = arrayOf<Any>("star", 1),
                    keyword = "keyword",
                )
            }
            assertEqualWithHash(arrays[0], arrays[1])
            assertEqualWithHash(arrays[1], arrays[2])
            assertEqualWithHash(arrays[0], arrays[2])
            assertNotEqualBothWays(
                arrays[0],
                newPlainArrayInput(
                    classLoader = classLoader,
                    chars = charArrayOf('A', 'Z'),
                    numbers = intArrayOf(1, 3),
                    labels = arrayOf("one", "two"),
                    stars = arrayOf<Any>("star", 1),
                    keyword = "keyword",
                ),
            )

            val hiddenLeft = newDto(classLoader, "DynamicShadowInput").apply {
                setBackingField("numbers", intArrayOf(1, 2))
            }
            val hiddenRight = newDto(classLoader, "DynamicShadowInput").apply {
                setBackingField("numbers", intArrayOf(3, 4))
            }
            assertEqualWithHash(hiddenLeft, hiddenRight)
            assertNotEqualBothWays(
                newDto(classLoader, "DynamicShadowInput"),
                newDto(classLoader, "DynamicShadowInput").apply {
                    setProperty("builder", null)
                },
            )

            val loadedArraysLeft = newDto(classLoader, "DynamicShadowInput").apply {
                setProperty("numbers", intArrayOf(1, 2))
            }
            val loadedArraysRight = newDto(classLoader, "DynamicShadowInput").apply {
                setProperty("numbers", intArrayOf(1, 2))
            }
            assertEqualWithHash(loadedArraysLeft, loadedArraysRight)
            assertNotEqualBothWays(
                loadedArraysLeft,
                newDto(classLoader, "DynamicShadowInput").apply {
                    setProperty("numbers", intArrayOf(2, 1))
                },
            )

            val floatingNaNLeft = floatingInput(classLoader, Float.NaN, Double.NaN)
            val floatingNaNRight = floatingInput(classLoader, Float.NaN, Double.NaN)
            assertEqualWithHash(floatingNaNLeft, floatingNaNRight)
            assertNotEqualBothWays(
                floatingInput(classLoader, 0.0f, 0.0),
                floatingInput(classLoader, -0.0f, -0.0),
            )

            val emptyLeft = newDto(classLoader, "EmptyInput")
            val emptyRight = newDto(classLoader, "EmptyInput")
            assertEqualWithHash(emptyLeft, emptyRight)
            val derived = classLoader
                .loadClass("demo.dto.DerivedEmptyInput")
                .getConstructor()
                .newInstance()
            assertNotEqualBothWays(emptyLeft, derived)

            val collisionLeft = newDto(classLoader, "DynamicShadowInput").apply {
                setMixedValues()
            }
            val collisionRight = newDto(classLoader, "DynamicShadowInput").apply {
                setMixedValues()
            }
            assertEqualWithHash(collisionLeft, collisionRight)
            assertTrue(collisionLeft == collisionLeft)
        }
    }

    private fun assertFuzzyDraftWriteContracts(classesDir: File) {
        val urls = arrayOf(classesDir.toURI().toURL())
        URLClassLoader(urls, javaClass.classLoader).use { classLoader ->
            val emptyInput = newDto(classLoader, "FuzzyShadowInput")
            val emptyEntity = emptyInput.javaClass
                .getMethod("toEntity")
                .invoke(emptyInput) as ImmutableSpi
            assertFalse(emptyEntity.__isLoaded("nullableText"))

            val input = newDto(classLoader, "FuzzyShadowInput").apply {
                setProperty("nullableText", "specified")
            }
            val specifiedEntity = input.javaClass.getMethod("toEntity").invoke(input) as ImmutableSpi
            assertTrue(specifiedEntity.__isLoaded("nullableText"))
            assertEquals("specified", specifiedEntity.__get("nullableText"))
        }
    }

    private fun assertDynamicConstructorLoadedStateContracts(classesDir: File) {
        val urls = arrayOf(classesDir.toURI().toURL())
        URLClassLoader(urls, javaClass.classLoader).use { classLoader ->
            val dtoType = classLoader.loadClass("demo.dto.DynamicShadowInput")
            val entityType = classLoader.loadClass("demo.Sample")
            val converterConstructor = dtoType.getConstructor(entityType)

            fun Any.toEntity(): ImmutableSpi =
                javaClass.getMethod("toEntity").invoke(this) as ImmutableSpi

            fun Any.isDtoPropLoaded(name: String): Boolean =
                javaClass.getMethod("is${name.replaceFirstChar(Char::titlecase)}Loaded").invoke(this) as Boolean

            val unloadedBase = newDto(classLoader, "DynamicShadowInput").toEntity()
            assertFalse(unloadedBase.__isLoaded("nullableText"))
            assertFalse(unloadedBase.__isLoaded("numbers"))
            val unloadedCopy = converterConstructor.newInstance(unloadedBase)
            assertFalse(unloadedCopy.isDtoPropLoaded("nullableText"))
            assertFalse(unloadedCopy.isDtoPropLoaded("numbers"))
            val unloadedRoundTrip = unloadedCopy.toEntity()
            assertFalse(unloadedRoundTrip.__isLoaded("nullableText"))
            assertFalse(unloadedRoundTrip.__isLoaded("numbers"))

            val loadedNullBase = newDto(classLoader, "DynamicShadowInput").apply {
                setProperty("nullableText", null)
            }.toEntity()
            assertTrue(loadedNullBase.__isLoaded("nullableText"))
            assertEquals(null, loadedNullBase.__get("nullableText"))
            val loadedNullCopy = converterConstructor.newInstance(loadedNullBase)
            assertTrue(loadedNullCopy.isDtoPropLoaded("nullableText"))
            assertEquals(null, loadedNullCopy.javaClass.getMethod("getNullableText").invoke(loadedNullCopy))
            val loadedNullRoundTrip = loadedNullCopy.toEntity()
            assertTrue(loadedNullRoundTrip.__isLoaded("nullableText"))
            assertEquals(null, loadedNullRoundTrip.__get("nullableText"))

            val expectedNumbers = intArrayOf(1, 2)
            val loadedNumbersBase = newDto(classLoader, "DynamicShadowInput").apply {
                setProperty("numbers", expectedNumbers)
            }.toEntity()
            val loadedNumbersCopy = converterConstructor.newInstance(loadedNumbersBase)
            assertTrue(loadedNumbersCopy.isDtoPropLoaded("numbers"))
            assertContentEquals(
                expectedNumbers,
                loadedNumbersCopy.javaClass.getMethod("getNumbers").invoke(loadedNumbersCopy) as IntArray,
            )
            val loadedNumbersRoundTrip = loadedNumbersCopy.toEntity()
            assertTrue(loadedNumbersRoundTrip.__isLoaded("numbers"))
            assertContentEquals(expectedNumbers, loadedNumbersRoundTrip.__get("numbers") as IntArray)
        }
    }

    private fun assertHibernateValidatorContracts(classesDir: File, kotlin: Boolean) {
        val urls = arrayOf(classesDir.toURI().toURL())
        URLClassLoader(urls, javaClass.classLoader).use { classLoader ->
            val input = newDto(classLoader, "ValidatorInput").apply {
                setProperty("name", "dto-name")
                setProperty("active", true)
                setProperty("isEnabled", true)
                setProperty("when", "keyword")
                setProperty("is1", true)
                setProperty("isDisplayName", "display-name")
            }
            val enhancedType = classLoader.loadClass(
                "org.hibernate.validator.engine.HibernateValidatorEnhancedBean",
            )
            assertTrue(enhancedType.isInstance(input))
            val fieldValue = enhancedType.getMethod(
                "\$\$_hibernateValidator_getFieldValue",
                String::class.java,
            )
            val getterValue = enhancedType.getMethod(
                "\$\$_hibernateValidator_getGetterValue",
                String::class.java,
            )

            val fieldValues = linkedMapOf(
                "name" to "dto-name",
                "active" to true,
                "isEnabled" to true,
                "when" to "keyword",
                "is1" to true,
                "isDisplayName" to "display-name",
            )
            fieldValues.forEach { (name, expected) ->
                assertEquals(expected, fieldValue.invoke(input, name))
            }
            val getterValues = linkedMapOf(
                "getName" to "dto-name",
                "getActive" to true,
                (if (kotlin) "isEnabled" else "getIsEnabled") to true,
                "getWhen" to "keyword",
                (if (kotlin) "is1" else "getIs1") to true,
                (if (kotlin) "isDisplayName" else "getIsDisplayName") to "display-name",
            )
            getterValues.forEach { (name, expected) ->
                val actualGetter = input.javaClass.getMethod(name)
                assertEquals(expected, actualGetter.invoke(input))
                assertEquals(expected, getterValue.invoke(input, name))
            }

            val wrongBooleanGetter = if (kotlin) "getIsEnabled" else "isEnabled"
            assertUnknownHibernateValidatorMember(getterValue, input, wrongBooleanGetter)
            val wrongNumericGetter = if (kotlin) "getIs1" else "is1"
            assertUnknownHibernateValidatorMember(getterValue, input, wrongNumericGetter)
            val wrongStringGetter = if (kotlin) "getIsDisplayName" else "isDisplayName"
            assertUnknownHibernateValidatorMember(getterValue, input, wrongStringGetter)
            assertUnknownHibernateValidatorMember(fieldValue, input, "missing")
        }
    }

    private fun assertUnknownHibernateValidatorMember(
        method: java.lang.reflect.Method,
        input: Any,
        name: String,
    ) {
        val exception = assertFailsWith<InvocationTargetException> {
            method.invoke(input, name)
        }
        assertTrue(exception.cause is IllegalArgumentException)
    }

    private fun newPlainArrayInput(
        classLoader: ClassLoader,
        chars: CharArray,
        numbers: IntArray,
        labels: Array<String>,
        stars: Array<*>,
        keyword: String,
    ): Any {
        val type = classLoader.loadClass("demo.dto.PlainArrayInput")
        val primaryConstructor = type.constructors.singleOrNull { constructor ->
            constructor.parameterTypes.contentEquals(
                arrayOf(
                    CharArray::class.java,
                    IntArray::class.java,
                    String::class.java,
                    arrayOf<String>()::class.java,
                    arrayOf<Any>()::class.java,
                ),
            )
        }
        if (primaryConstructor != null) {
            return primaryConstructor.newInstance(chars, numbers, keyword, labels, stars)
        }
        return type.getConstructor().newInstance().apply {
            setProperty("chars", chars)
            setProperty("numbers", numbers)
            setProperty("labels", labels)
            setProperty("stars", stars)
            setProperty("when", keyword)
        }
    }

    private fun floatingInput(
        classLoader: ClassLoader,
        floatValue: Float,
        doubleValue: Double,
    ): Any {
        val type = classLoader.loadClass("demo.dto.FloatingInput")
        val primaryConstructor = type.constructors.singleOrNull { constructor ->
            constructor.parameterTypes.contentEquals(
                arrayOf(Float::class.javaPrimitiveType, Double::class.javaPrimitiveType),
            )
        }
        if (primaryConstructor != null) {
            return primaryConstructor.newInstance(floatValue, doubleValue)
        }
        return type.getConstructor().newInstance().apply {
            setProperty("floatValue", floatValue)
            setProperty("doubleValue", doubleValue)
        }
    }

    private fun newDto(classLoader: ClassLoader, simpleName: String): Any {
        return classLoader
            .loadClass("demo.dto.$simpleName")
            .getConstructor()
            .newInstance()
    }

    private fun Any.setMixedValues() {
        setProperty("separator", "separator-value")
        setProperty("_sp", "sp-value")
        setProperty("when", "keyword")
        setProperty("hash", "hash-value")
        setProperty("_hash", "underscore-hash-value")
        setProperty("o", "o-value")
        setProperty("other", "other-value")
        setProperty("_other", "underscore-other-value")
        setProperty("javaClass", "java-class-value")
        setProperty("chars", charArrayOf('A', 'Z'))
        setProperty("numbers", intArrayOf(1, 2))
    }

    private fun Any.setBackingField(name: String, value: Any?) {
        val field = generateSequence(javaClass as Class<*>?) { type -> type.superclass }
            .mapNotNull { type -> type.declaredFields.singleOrNull { field -> field.name == name } }
            .firstOrNull()
            ?: error("There is no backing field '$name' on ${javaClass.name}")
        field.isAccessible = true
        field.set(this, value)
    }

    private fun assertEqualWithHash(left: Any, right: Any) {
        assertTrue(left == right, "$left must equal $right")
        assertTrue(right == left, "$right must equal $left")
        assertEquals(left.hashCode(), right.hashCode(), "Equal DTOs must have equal hashes")
    }

    private fun assertNotEqualBothWays(left: Any, right: Any) {
        assertFalse(left == right, "$left must not equal $right")
        assertFalse(right == left, "$right must not equal $left")
    }

    private fun Any.setProperty(name: String, value: Any?) {
        val setterSuffix = name.replaceFirstChar { character ->
            if (character.isLowerCase()) {
                character.titlecase()
            } else {
                character.toString()
            }
        }
        val setterNames = buildSet {
            add("set$setterSuffix")
            if (name.startsWith("is") && name.length > 2 && !name[2].isLowerCase()) {
                add("set${name.substring(2)}")
            }
        }
        val setter = javaClass.methods.singleOrNull { method ->
            method.name in setterNames && method.parameterCount == 1
        } ?: error("There is no unique setter in $setterNames on ${javaClass.name}")
        setter.invoke(this, value)
    }

    private fun assertGeneratedDtoFiles(
        generatedFiles: List<File>,
        extension: String,
        platform: String,
    ) {
        val actualNames = generatedFiles.map(File::getName).toSet()
        val expectedNames = DTO_SIMPLE_NAMES.mapTo(linkedSetOf()) { name -> "$name.$extension" }
        assertTrue(
            actualNames.containsAll(expectedNames),
            "$platform did not generate DTO files: ${expectedNames - actualNames}",
        )
    }

    private fun fixtureProject(prefix: String): File {
        return createTempDirectory(prefix = prefix)
            .toFile()
            .resolve("fixture")
            .apply(File::mkdirs)
    }

    private fun writeSources(
        sourceRoot: File,
        sources: Map<String, String>,
    ): List<File> {
        return sources.map { (relativePath, content) ->
            sourceRoot.resolve(relativePath).apply {
                parentFile.mkdirs()
                writeText(content)
            }
        }
    }

    private fun writeDtoSource(projectDir: File) {
        projectDir.resolve("src/main/dto/demo/Sample.dto").apply {
            parentFile.mkdirs()
            writeText(DTO_SOURCE)
        }
    }

    private fun String.normalizeArrayIdentities(): String {
        return replace(INT_ARRAY_IDENTITY_PATTERN, "<int-array>")
            .replace(STRING_ARRAY_IDENTITY_PATTERN, "<string-array>")
            .replace(OBJECT_ARRAY_IDENTITY_PATTERN, "<object-array>")
    }

    private fun DiagnosticCollector<JavaFileObject>.toErrorMessage(): String {
        return diagnostics.joinToString("\n") { diagnostic ->
            "${diagnostic.kind} ${diagnostic.source?.name.orEmpty()}:" +
                "${diagnostic.lineNumber}:${diagnostic.columnNumber} ${diagnostic.getMessage(null)}"
        }
    }

    private class CapturingKspLogger : KSPLogger {
        private val messages = mutableListOf<String>()

        override fun logging(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun info(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun warn(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun error(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun exception(e: Throwable) {
            messages += e.stackTraceToString()
        }

        fun text(): String = messages.joinToString("\n")
    }

    private companion object {
        val DTO_SIMPLE_NAMES = listOf(
            "PlainArrayInput",
            "DynamicShadowInput",
            "FuzzyShadowInput",
            "FloatingInput",
            "EmptyInput",
            "SampleSpecification",
            "ValidatorInput",
        )

        val EXPECTED_SNAPSHOTS = listOf(
            "PlainArrayInput(chars=AZ, numbers=<int-array>, when=keyword, " +
                "labels=<string-array>, stars=<object-array>)",
            "DynamicShadowInput()",
            "DynamicShadowInput(builder=null)",
            "DynamicShadowInput(separator=separator-value, _sp=sp-value, when=keyword, " +
                "hash=hash-value, _hash=underscore-hash-value, o=o-value, other=other-value, " +
                "_other=underscore-other-value, javaClass=java-class-value, " +
                "chars=AZ, numbers=<int-array>)",
            "FuzzyShadowInput()",
            "FuzzyShadowInput(builder=builder-value)",
            "FuzzyShadowInput(separator=separator-value, _sp=sp-value, when=keyword, " +
                "hash=hash-value, _hash=underscore-hash-value, o=o-value, other=other-value, " +
                "_other=underscore-other-value, javaClass=java-class-value, " +
                "chars=AZ, numbers=<int-array>)",
        )

        val INT_ARRAY_IDENTITY_PATTERN = Regex("""\[I@[0-9a-fA-F]+""")
        val STRING_ARRAY_IDENTITY_PATTERN = Regex("""\[Ljava\.lang\.String;@[0-9a-fA-F]+""")
        val OBJECT_ARRAY_IDENTITY_PATTERN = Regex("""\[Ljava\.lang\.Object;@[0-9a-fA-F]+""")

        val JAVA_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.jspecify.annotations.Nullable;

            @Entity
            public interface Sample {
                @Id
                long id();

                String name();

                String description();

                String note();

                String marker();

                String hashValue();

                String underscoreHashValue();

                String oValue();

                String otherValue();

                String underscoreOtherValue();

                String javaClassValue();

                char[] chars();

                int[] numbers();

                String validationName();

                boolean active();

                boolean enabled();

                boolean flag1();

                String displayName();

                @Nullable
                String nullableText();
            }
        """.trimIndent()

        val KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id

            @Entity
            interface Sample {
                @Id
                val id: Long

                val name: String

                val description: String

                val note: String

                val marker: String

                val hashValue: String

                val underscoreHashValue: String

                val oValue: String

                val otherValue: String

                val underscoreOtherValue: String

                val javaClassValue: String

                val chars: CharArray

                val numbers: IntArray

                val validationName: String

                val active: Boolean

                val enabled: Boolean

                val flag1: Boolean

                val displayName: String

                val nullableText: String?
            }
        """.trimIndent()

        val DTO_SOURCE = """
            package demo.dto

            input PlainArrayInput {
                chars
                numbers
                labels: Array<String>
                stars: Array<*>
                marker as when
            }

            dynamic input DynamicShadowInput {
                name? as builder
                description? as separator
                note? as _sp
                marker? as when
                hashValue? as hash
                underscoreHashValue? as _hash
                oValue? as o
                otherValue? as other
                underscoreOtherValue? as _other
                javaClassValue? as javaClass
                chars?
                numbers?
                nullableText
            }

            fuzzy input FuzzyShadowInput {
                name? as builder
                description? as separator
                note? as _sp
                marker? as when
                hashValue? as hash
                underscoreHashValue? as _hash
                oValue? as o
                otherValue? as other
                underscoreOtherValue? as _other
                javaClassValue? as javaClass
                chars?
                numbers?
                nullableText
            }

            input FloatingInput {
                floatValue: Float
                doubleValue: Double
            }

            input EmptyInput {
            }

            dynamic input ValidatorInput {
                validationName? as name
                active?
                enabled? as isEnabled
                marker? as when
                flag1? as is1
                displayName? as isDisplayName
            }

            specification SampleSpecification {
                name
            }
        """.trimIndent()

        val JAVA_DERIVED_SOURCE = """
            package demo.dto;

            public class DerivedEmptyInput extends EmptyInput {
            }
        """.trimIndent()

        val KOTLIN_DERIVED_SOURCE = """
            package demo.dto

            public class DerivedEmptyInput : EmptyInput()
        """.trimIndent()

        fun runtimeClasspath(): List<File> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .filter(String::isNotBlank)
                .map(::File)
                .filter(File::exists)
        }

        fun runtimeClasspathText(): String = runtimeClasspath().joinToString(File.pathSeparator)
    }
}
