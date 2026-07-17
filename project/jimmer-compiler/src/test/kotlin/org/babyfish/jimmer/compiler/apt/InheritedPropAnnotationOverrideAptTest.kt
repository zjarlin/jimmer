package org.babyfish.jimmer.compiler.apt

import java.io.File
import java.nio.charset.StandardCharsets
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.babyfish.jimmer.apt.Context
import org.babyfish.jimmer.apt.MetaException
import org.babyfish.jimmer.dto.compiler.SourceTypeFilter
import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default

class InheritedPropAnnotationOverrideAptTest {

    @Test
    fun `merges mapped superclass annotations by qualified name`() {
        val result = compile(
            targetTypeName = "demo.OverrideEntity",
            propName = "status",
            "demo/OverrideEntity.java" to """
                package demo;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;
                import org.babyfish.jimmer.sql.Column;
                import org.babyfish.jimmer.sql.Default;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.METHOD)
                @interface Marker {
                    String value();
                }

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.METHOD)
                @interface ParentMarker {}

                interface StatusContract<T> {
                    T getStatus();
                }

                @MappedSuperclass
                interface StatusBase<T extends CharSequence> extends StatusContract<T> {
                    @Override
                    @Marker("parent")
                    @ParentMarker
                    @Default("0")
                    @Column(name = "BASE_STATUS")
                    T getStatus();
                }

                @Entity
                interface OverrideEntity extends StatusBase<String> {
                    @Id
                    long getId();

                    @Marker("child")
                    @Default("1")
                    String getStatus();
                }
            """.trimIndent(),
        )

        assertTrue(result.success, result.diagnostics)
        val snapshot = assertNotNull(result.snapshot)
        assertEquals("java.lang.String", snapshot.returnType)
        assertEquals("1", snapshot.defaultValue)
        assertEquals("BASE_STATUS", snapshot.columnName)
        assertEquals("child", snapshot.annotationValues["demo.Marker"])
        assertTrue("demo.ParentMarker" in snapshot.annotationNames)
        assertEquals(1, snapshot.annotationNames.count { it == Default::class.java.name })
        assertEquals(1, snapshot.annotationNames.count { it == "demo.Marker" })
        assertFalse(Override::class.java.name in snapshot.annotationNames)
    }

    @Test
    fun `rejects mapped superclass property override`() {
        val result = compile(
            targetTypeName = "demo.InvalidEntity",
            propName = "name",
            "demo/InvalidEntity.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                interface RootBase {
                    String getName();
                }

                @MappedSuperclass
                interface InvalidBase extends RootBase {
                    @Override
                    String getName();
                }

                @Entity
                interface InvalidEntity extends InvalidBase {
                    @Id
                    long getId();
                }
            """.trimIndent(),
        )

        assertRejected(result, "overrides property of super type")
    }

    @Test
    fun `rejects indirectly inherited mapped superclass property override`() {
        val result = compile(
            targetTypeName = "demo.IndirectEntity",
            propName = "name",
            "demo/IndirectEntity.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                interface RootBase {
                    String getName();
                }

                @MappedSuperclass
                interface MiddleBase extends RootBase {}

                @Entity
                interface IndirectEntity extends MiddleBase {
                    @Id
                    long getId();

                    @Override
                    String getName();
                }
            """.trimIndent(),
        )

        assertRejected(result, "overrides property of super type")
    }

    @Test
    fun `rejects entity superclass property override`() {
        val result = compile(
            targetTypeName = "demo.DerivedEntity",
            propName = "name",
            "demo/DerivedEntity.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Discriminator;
                import org.babyfish.jimmer.sql.DiscriminatorValue;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.Inheritance;
                import org.babyfish.jimmer.sql.InheritanceType;

                @Entity
                @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
                interface RootEntity {
                    @Id
                    long getId();

                    @Discriminator
                    String getType();

                    String getName();
                }

                @Entity
                @DiscriminatorValue("DERIVED")
                interface DerivedEntity extends RootEntity {
                    @Override
                    String getName();
                }
            """.trimIndent(),
        )

        assertRejected(result, "overrides property of super type")
    }

    @Test
    fun `rejects generic substituted return type change`() {
        val result = compile(
            targetTypeName = "demo.GenericMismatchEntity",
            propName = "value",
            "demo/GenericMismatchEntity.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                interface GenericBase<T extends CharSequence> {
                    T getValue();
                }

                @Entity
                interface GenericMismatchEntity extends GenericBase<CharSequence> {
                    @Id
                    long getId();

                    @Override
                    String getValue();
                }
            """.trimIndent(),
        )

        assertRejected(result, "return type is different from inherited property")
    }

    @Test
    fun `rejects nullability change`() {
        val result = compile(
            targetTypeName = "demo.NullableMismatchEntity",
            propName = "value",
            "demo/NullableMismatchEntity.java" to """
                package demo;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;
                import org.jetbrains.annotations.Nullable;

                @MappedSuperclass
                interface NullableBase {
                    String getValue();
                }

                @Entity
                interface NullableMismatchEntity extends NullableBase {
                    @Id
                    long getId();

                    @Override
                    @Nullable
                    String getValue();
                }
            """.trimIndent(),
        )

        assertRejected(result, "cannot change its category, nullability or primary mapping annotation")
    }

    @Test
    fun `rejects list category change`() {
        val result = compile(
            targetTypeName = "demo.ListMismatchEntity",
            propName = "tags",
            "demo/ListMismatchEntity.java" to """
                package demo;

                import java.util.List;
                import org.babyfish.jimmer.Scalar;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                interface ListBase {
                    List<String> getTags();
                }

                @Entity
                interface ListMismatchEntity extends ListBase {
                    @Id
                    long getId();

                    @Override
                    @Scalar
                    List<String> getTags();
                }
            """.trimIndent(),
        )

        assertRejected(result, "cannot change its category, nullability or primary mapping annotation")
    }

    @Test
    fun `rejects primary mapping category change`() {
        val result = compile(
            targetTypeName = "demo.FormulaMismatchEntity",
            propName = "name",
            "demo/FormulaMismatchEntity.java" to """
                package demo;

                import org.babyfish.jimmer.Formula;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                interface BasicBase {
                    String getName();
                }

                @Entity
                interface FormulaMismatchEntity extends BasicBase {
                    @Id
                    long getId();

                    @Override
                    @Formula(sql = "NAME")
                    String getName();
                }
            """.trimIndent(),
        )

        assertRejected(result, "cannot change its category, nullability or primary mapping annotation")
    }

    @Test
    fun `rejects formula implementation category change`() {
        val result = compile(
            targetTypeName = "demo.JavaFormulaMismatchEntity",
            propName = "name",
            "demo/JavaFormulaMismatchEntity.java" to """
                package demo;

                import org.babyfish.jimmer.Formula;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.Id;
                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                interface SqlFormulaBase {
                    @Formula(sql = "NAME")
                    String getName();
                }

                @Entity
                interface JavaFormulaMismatchEntity extends SqlFormulaBase {
                    @Id
                    long getId();

                    @Override
                    @Formula(dependencies = {"id"})
                    default String getName() {
                        return "";
                    }
                }
            """.trimIndent(),
        )

        assertRejected(result, "cannot change its category, nullability or primary mapping annotation")
    }

    private fun assertRejected(result: CompilationResult, message: String) {
        assertFalse(result.success, result.diagnostics)
        assertContains(result.error.orEmpty(), message)
    }

    private fun compile(
        targetTypeName: String,
        propName: String,
        vararg sources: Pair<String, String>,
    ): CompilationResult {
        val projectDir = createTempDirectory(prefix = "jimmer-compiler-apt-override-test").toFile()
        val sourceDir = projectDir.resolve("src/main/java")
        val classesDir = projectDir.resolve("build/classes")
        for ((path, content) in sources) {
            writeJavaSource(sourceDir, path, content)
        }
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT integration tests require a JDK compiler")
        val processor = CapturingImmutableProcessor(targetTypeName, propName)
        val sourceFiles = sourceDir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "java" }
            .toList()
        classesDir.mkdirs()
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf(
                    "-proc:only",
                    "-classpath",
                    System.getProperty("java.class.path"),
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(processor))
            task.call()
        }
        return CompilationResult(
            success = success,
            snapshot = processor.snapshot,
            error = processor.error,
            diagnostics = diagnostics.toErrorMessage(),
        )
    }

    private fun writeJavaSource(
        sourceDir: File,
        path: String,
        content: String,
    ) {
        val file = sourceDir.resolve(path)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun DiagnosticCollector<JavaFileObject>.toErrorMessage(): String {
        return diagnostics.joinToString(separator = "\n") { diagnostic ->
            val source = diagnostic.source?.name.orEmpty()
            val position = if (diagnostic.lineNumber > 0) {
                "${diagnostic.lineNumber}:${diagnostic.columnNumber}"
            } else {
                "?:?"
            }
            "${diagnostic.kind} $source:$position ${diagnostic.getMessage(null)}"
        }
    }

    private data class CompilationResult(
        val success: Boolean,
        val snapshot: PropSnapshot?,
        val error: String?,
        val diagnostics: String,
    )

    private data class PropSnapshot(
        val returnType: String,
        val defaultValue: String?,
        val columnName: String?,
        val annotationNames: List<String>,
        val annotationValues: Map<String, String?>,
    )

    private class CapturingImmutableProcessor(
        private val targetTypeName: String,
        private val propName: String,
    ) : AbstractProcessor() {
        var snapshot: PropSnapshot? = null
            private set

        var error: String? = null
            private set

        private var processed = false

        override fun getSupportedAnnotationTypes(): Set<String> = setOf("*")

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

        override fun process(
            annotations: Set<TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean {
            if (processed || roundEnvironment.processingOver()) {
                return false
            }
            val typeElement = processingEnv.elementUtils.getTypeElement(targetTypeName) ?: return false
            processed = true
            try {
                val context = Context(
                    processingEnv.elementUtils,
                    processingEnv.typeUtils,
                    processingEnv.filer,
                    false,
                    SourceTypeFilter(null, null),
                    false,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    Modifier.PRIVATE,
                )
                val prop = context.getImmutableType(typeElement).props.getValue(propName)
                val annotationNames = prop.annotations.map { annotation -> annotation.annotationTypeName() }
                snapshot = PropSnapshot(
                    returnType = prop.returnType.toString(),
                    defaultValue = prop.getAnnotation(Default::class.java)?.value,
                    columnName = prop.getAnnotation(Column::class.java)?.name,
                    annotationNames = annotationNames,
                    annotationValues = prop.annotations.associate { annotation ->
                        annotation.annotationTypeName() to annotation.stringValue("value")
                    },
                )
            } catch (ex: MetaException) {
                error = ex.message
                processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, ex.message, ex.element)
            }
            return false
        }

        private fun AnnotationMirror.annotationTypeName(): String {
            return (annotationType.asElement() as TypeElement).qualifiedName.toString()
        }

        private fun AnnotationMirror.stringValue(name: String): String? {
            return elementValues.entries
                .firstOrNull { (element, _) -> element.simpleName.contentEquals(name) }
                ?.value
                ?.value
                ?.toString()
        }
    }
}
