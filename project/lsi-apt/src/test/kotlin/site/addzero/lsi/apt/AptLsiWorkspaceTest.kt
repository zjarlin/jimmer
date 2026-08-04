package site.addzero.lsi.apt

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFrontendDocumentationConvention
import site.addzero.lsi.model.LsiFrontendOptions
import site.addzero.lsi.model.LsiGeneratedPeerDocumentationConvention
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPackageAnnotationScope
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVariance
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace
import java.io.File
import java.nio.charset.StandardCharsets
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.lang.model.element.ExecutableElement
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AptLsiWorkspaceTest {

    @Test
    fun `deduplicates one java annotation projected as method and return type use`() {
        val compilation = compile(
            "demo/Marker.java" to """
                package demo;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;

                @Target({ElementType.METHOD, ElementType.TYPE_USE})
                public @interface Marker {
                    String value();
                }
            """.trimIndent(),
            "demo/Model.java" to """
                package demo;

                interface Model {
                    @Marker("value")
                    String name();
                }
            """.trimIndent(),
        )

        assertTrue(compilation.success, compilation.diagnostics)
        val annotations = compilation.workspace
            .requireProperty(LsiSymbolId.type("demo.Model"), "name")
            .annotations

        assertEquals(1, annotations.size)
        assertEquals(LsiSymbolId.type("demo.Marker"), annotations.single().type)
        assertEquals(LsiAnnotationUseSiteTarget.METHOD, annotations.single().useSiteTarget)
    }

    @Test
    fun `freezes description after direct doc comment`() {
        val compilation = compile(
            "demo/DocumentedModels.java" to """
                package demo;

                import test.lsi.Description;

                @Description("annotated type")
                interface AnnotatedModel {
                    @Description("annotated property")
                    String name();
                }

                /** direct type */
                @Description("ignored type")
                interface DirectModel {
                    /** direct property */
                    @Description("ignored property")
                    String name();
                }
            """.trimIndent(),
        )

        assertTrue(compilation.success, compilation.diagnostics)
        val annotatedTypeId = LsiSymbolId.type("demo.AnnotatedModel")
        val directTypeId = LsiSymbolId.type("demo.DirectModel")
        assertEquals(
            "annotated type",
            assertIs<LsiTypeDeclaration>(compilation.workspace[annotatedTypeId]).documentation,
        )
        assertEquals(
            null,
            assertIs<LsiTypeDeclaration>(compilation.workspace[annotatedTypeId]).sourceDocumentation,
        )
        assertEquals(
            "annotated property",
            compilation.workspace.requireProperty(annotatedTypeId, "name").documentation,
        )
        assertEquals(
            null,
            compilation.workspace.requireProperty(annotatedTypeId, "name").sourceDocumentation,
        )
        assertEquals(
            "direct type",
            assertIs<LsiTypeDeclaration>(compilation.workspace[directTypeId]).documentation,
        )
        assertEquals(
            "direct type",
            assertIs<LsiTypeDeclaration>(compilation.workspace[directTypeId]).sourceDocumentation,
        )
        assertEquals(
            "direct property",
            compilation.workspace.requireProperty(directTypeId, "name").documentation,
        )
        assertEquals(
            "direct property",
            compilation.workspace.requireProperty(directTypeId, "name").sourceDocumentation,
        )
    }

    @Test
    fun `freezes annotated package scopes with and without root types`() {
        val compilation = compile(
            "marker/PackageMarker.java" to """
                package marker;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;

                @Target(ElementType.PACKAGE)
                public @interface PackageMarker {
                    String value();
                }
            """.trimIndent(),
            "only/package-info.java" to """
                @marker.PackageMarker("package-only")
                package only;
            """.trimIndent(),
            "rooted/package-info.java" to """
                @marker.PackageMarker("rooted")
                package rooted;
            """.trimIndent(),
            "rooted/Model.java" to """
                package rooted;

                interface Model {}
            """.trimIndent(),
        )

        assertTrue(compilation.success, compilation.diagnostics)
        assertEquals(
            setOf("only", "rooted"),
            compilation.workspace.annotationScopes.mapTo(linkedSetOf()) { scope -> scope.packageName },
        )
        val packageOnlyScope = assertIs<LsiPackageAnnotationScope>(
            compilation.workspace.annotationScope(LsiSymbolId.packageScope("only")),
        )
        val annotation = packageOnlyScope.annotations.single()
        assertEquals(LsiSymbolId.type("marker.PackageMarker"), annotation.type)
        assertEquals(LsiAnnotationUseSiteTarget.PACKAGE, annotation.useSiteTarget)
        assertEquals(
            LsiAnnotationValue.StringValue("package-only"),
            annotation.arguments.getValue("value").value,
        )
        assertTrue(packageOnlyScope.origin.source?.path?.endsWith("only/package-info.java") == true)
    }

    @Test
    fun `freezes binary documentation from generated peer contract`() {
        val dependency = compileDependency(
            "demo/BinaryBook.java" to """
                package demo;

                import test.lsi.Entity;
                import test.lsi.Id;

                @Entity
                public interface BinaryBook {
                    @Id
                    long id();

                    String name();
                }
            """.trimIndent(),
            "demo/BinaryBookPeer.java" to """
                package demo;

                import test.lsi.Description;

                @Description("binary type")
                public interface BinaryBookPeer {
                    @Description("binary property")
                    BinaryBookPeer setName(String name);
                }
            """.trimIndent(),
        )
        val compilation = compile(
            "demo/BinaryConsumer.java" to """
                package demo;

                interface BinaryConsumer {
                    BinaryBook book();
                }
            """.trimIndent(),
            additionalClasspath = listOf(dependency),
        )

        assertTrue(compilation.success, compilation.diagnostics)
        val bookId = LsiSymbolId.type("demo.BinaryBook")
        assertEquals(
            "binary type",
            assertIs<LsiTypeDeclaration>(compilation.workspace[bookId]).documentation,
        )
        assertEquals(
            "binary property",
            compilation.workspace.requireProperty(bookId, "name").documentation,
        )
    }

    @Test
    fun `freezes source and binary declarations with java frontend projection language`() {
        val compilation = compile(
            "demo/Projection.java" to """
                package demo;

                import java.lang.annotation.RetentionPolicy;

                interface Projection {
                    RetentionPolicy policy();
                }
            """.trimIndent(),
        )

        assertTrue(compilation.success, compilation.diagnostics)
        val sourceType = assertIs<LsiTypeDeclaration>(
            compilation.workspace[LsiSymbolId.type("demo.Projection")]
        )
        val binaryType = assertIs<LsiTypeDeclaration>(
            compilation.workspace[LsiSymbolId.type("java.lang.annotation.RetentionPolicy")]
        )
        assertEquals(LsiOriginKind.SOURCE, sourceType.origin.kind)
        assertEquals(LsiLanguage.JAVA, sourceType.origin.language)
        assertEquals(LsiOriginKind.BINARY, binaryType.origin.kind)
        assertEquals(LsiLanguage.JAVA, binaryType.origin.language)
    }

    @Test
    fun `freezes nested type use nullability`() {
        val compilation = compile(
            "demo/NullableModel.java" to """
                package demo;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;
                import java.util.List;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE_USE)
                @interface Nullable {}

                interface NullableModel {
                    List<@Nullable Long> values();
                }
            """.trimIndent(),
        )

        assertTrue(compilation.success, compilation.diagnostics)
        val values = compilation.workspace.requireProperty(
            LsiSymbolId.type("demo.NullableModel"),
            "values",
        )
        val listType = assertIs<LsiDeclaredType>(values.type)
        val elementType = assertIs<LsiPrimitiveType>(listType.arguments.single().type)
        assertEquals(LsiPrimitiveKind.LONG, elementType.kind)
        assertEquals(LsiNullability.NULLABLE, elementType.nullability)
    }

    @Test
    fun `direct type extension uses supplied frontend nullability policy`() {
        val compilation = compile(
            "demo/DirectNullable.java" to """
                package demo;

                interface DirectNullable {
                    @test.lsi.TypeNullable String value();
                }
            """.trimIndent(),
        )

        assertTrue(compilation.success, compilation.diagnostics)
        assertEquals(LsiNullability.NULLABLE, compilation.directType?.nullability)
    }

    @Test
    fun `applies keep is prefix while freezing java boolean getters`() {
        val source = "demo/Switches.java" to """
            package demo;

            interface Switches {
                boolean isActive();

                Boolean isEnabled();

                String getURL();
            }
        """.trimIndent()

        val defaultWorkspace = compile(source).workspace
        val keepPrefixWorkspace = compile(
            source,
            compilerOptions = mapOf(TEST_KEEP_IS_PREFIX_OPTION to "true"),
        ).workspace
        val ownerId = LsiSymbolId.type("demo.Switches")

        assertEquals("isActive", defaultWorkspace.requireProperty(ownerId, "active").getterName)
        assertEquals("isEnabled", defaultWorkspace.requireProperty(ownerId, "enabled").getterName)
        assertEquals("getURL", defaultWorkspace.requireProperty(ownerId, "URL").getterName)
        assertEquals("isActive", keepPrefixWorkspace.requireProperty(ownerId, "isActive").getterName)
        assertEquals("isEnabled", keepPrefixWorkspace.requireProperty(ownerId, "isEnabled").getterName)
        assertEquals("getURL", keepPrefixWorkspace.requireProperty(ownerId, "URL").getterName)
    }

    @Test
    fun `freezes java declarations into immutable lsi`() {
        val compilation = compile(
            "demo/Models.java" to """
                package demo;

                import java.io.IOException;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;
                import java.util.List;
                import test.lsi.Default;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                @interface TypeMarker {}

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE_USE)
                @interface ReturnTag {}

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.FIELD)
                @interface FieldMarker {}

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.CONSTRUCTOR)
                @interface ConstructorMarker {}

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.PARAMETER)
                @interface ParameterMarker {}

                @Retention(RetentionPolicy.RUNTIME)
                @interface Nested {
                    String value();
                }

                enum Mode {
                    FIRST,
                    SECOND
                }

                class Model<T extends Number> {
                    static class Nested {}

                    class Inner {}

                    @FieldMarker
                    private static final String SECRET = "secret";

                    @FieldMarker
                    protected @ReturnTag T value;

                    @ConstructorMarker
                    private Model() {}

                    @ConstructorMarker
                    public <R extends CharSequence> Model(
                        @ParameterMarker T value,
                        R label,
                        int... codes
                    ) throws IOException {}
                }

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.METHOD)
                @interface Sample {
                    boolean enabled() default true;
                    byte byteValue() default 1;
                    short shortValue() default 2;
                    int intValue() default 3;
                    long longValue() default 4L;
                    float floatValue() default 5F;
                    double doubleValue() default 6D;
                    char charValue() default 'x';
                    String text() default "default";
                    Mode mode() default Mode.FIRST;
                    Class<?> type() default String.class;
                    Nested nested() default @Nested("nested-default");
                    int[] numbers() default { 1, 2 };
                }

                interface Root {
                    String getCode();
                }

                interface Parent<T extends Number> extends Root {
                    @Override
                    String getCode();

                    @Default("0")
                    @Sample
                    @ReturnTag
                    String getDisplayName();

                    T value();

                    void accept(T input);

                    <R extends CharSequence> R convert(
                        T input,
                        List<? extends R> values
                    ) throws IOException;
                }

                /**
                 * 子类型文档。
                 */
                @TypeMarker
                interface Child extends Parent<Integer> {
                    @Override
                    String getCode();

                    @Override
                    @Default("1")
                    @Sample(
                        enabled = false,
                        byteValue = 7,
                        shortValue = 8,
                        intValue = 9,
                        longValue = 10L,
                        floatValue = 11F,
                        doubleValue = 12D,
                        charValue = 'y',
                        text = "explicit",
                        mode = Mode.SECOND,
                        type = Integer.class,
                        nested = @Nested("nested-explicit"),
                        numbers = { 3, 4 }
                    )
                    @ReturnTag
                    String getDisplayName();

                    @Override
                    Integer value();

                    @Override
                    void accept(Integer input);

                    String title();

                    boolean isActive();

                    void consume(List<? super Number> values, int[] codes);

                    static String version() {
                        return "1";
                    }
                }
            """.trimIndent(),
        )

        assertTrue(compilation.success, compilation.diagnostics)
        val workspace = compilation.workspace
        val childId = LsiSymbolId.type("demo.Child")
        val parentId = LsiSymbolId.type("demo.Parent")
        val rootId = LsiSymbolId.type("demo.Root")
        val child = assertIs<LsiTypeDeclaration>(workspace[childId])
        val parent = assertIs<LsiTypeDeclaration>(workspace[parentId])

        val modelId = LsiSymbolId.type("demo.Model")
        val model = assertIs<LsiTypeDeclaration>(workspace[modelId])
        assertEquals(null, model.enclosingTypeId)
        assertFalse(model.dataClass)
        val nested = assertIs<LsiTypeDeclaration>(workspace[LsiSymbolId.type("demo.Model.Nested")])
        assertEquals(modelId, nested.enclosingTypeId)
        assertFalse(nested.requiresEnclosingInstance)
        assertFalse(nested.dataClass)
        val inner = assertIs<LsiTypeDeclaration>(workspace[LsiSymbolId.type("demo.Model.Inner")])
        assertEquals(modelId, inner.enclosingTypeId)
        assertTrue(inner.requiresEnclosingInstance)
        val fields = workspace.declarationsOfType<LsiField>()
            .filter { field -> field.ownerId == modelId }
        val secret = fields.single { field -> field.name == "SECRET" }
        assertEquals(LsiSymbolId.field(modelId, "SECRET"), secret.id)
        assertEquals(LsiVisibility.PRIVATE, secret.visibility)
        assertTrue(secret.static)
        assertFalse(secret.mutable)
        assertTrue(secret.annotations.any { annotation ->
            annotation.type == LsiSymbolId.type("demo.FieldMarker") &&
                annotation.useSiteTarget == LsiAnnotationUseSiteTarget.FIELD
        })
        assertTrue(secret.origin.source?.path?.endsWith("demo/Models.java") == true)
        assertNotNull(secret.location)

        val valueField = fields.single { field -> field.name == "value" }
        assertEquals(LsiVisibility.PROTECTED, valueField.visibility)
        assertTrue(valueField.mutable)
        assertFalse(valueField.static)
        assertEquals(
            model.typeParameters.single().id,
            assertIs<LsiTypeParameterRef>(valueField.type).parameterId,
        )
        assertTrue(valueField.annotations.any { annotation ->
            annotation.type == LsiSymbolId.type("demo.ReturnTag") &&
                annotation.useSiteTarget == LsiAnnotationUseSiteTarget.FIELD
        })

        val constructors = workspace.declarationsOfType<LsiConstructor>()
            .filter { constructor -> constructor.ownerId == modelId }
        val privateConstructor = constructors.single { constructor -> constructor.parameters.isEmpty() }
        assertEquals(LsiVisibility.PRIVATE, privateConstructor.visibility)
        assertEquals(LsiSymbolId.constructor(modelId), privateConstructor.id)
        val publicConstructor = constructors.single { constructor -> constructor.parameters.size == 3 }
        assertEquals(LsiVisibility.PUBLIC, publicConstructor.visibility)
        assertEquals(
            LsiSymbolId.constructor(
                modelId,
                listOf(
                    "parameter:type:demo.Model:0",
                    "parameter:method:<init>:0:type:java.lang.CharSequence",
                    "array:primitive:int",
                ),
            ),
            publicConstructor.id,
        )
        assertEquals(LsiAnnotationUseSiteTarget.CONSTRUCTOR, publicConstructor.annotations.single().useSiteTarget)
        assertEquals(
            LsiSymbolId.type("demo.ParameterMarker"),
            publicConstructor.parameters.first().annotations.single().type,
        )
        assertEquals(
            model.typeParameters.single().id,
            assertIs<LsiTypeParameterRef>(publicConstructor.parameters.first().type).parameterId,
        )
        assertTrue(publicConstructor.parameters.last().vararg)
        assertEquals(
            LsiPrimitiveKind.INT,
            assertIs<LsiPrimitiveType>(publicConstructor.parameters.last().type).kind,
        )
        assertEquals(
            LsiSymbolId.type("java.lang.CharSequence"),
            assertIs<LsiDeclaredType>(publicConstructor.typeParameters.single().upperBounds.single()).declarationId,
        )
        assertEquals(
            LsiSymbolId.type("java.io.IOException"),
            assertIs<LsiDeclaredType>(publicConstructor.thrownTypes.single()).declarationId,
        )
        assertTrue(publicConstructor.origin.source?.path?.endsWith("demo/Models.java") == true)
        assertNotNull(publicConstructor.location)
        assertTrue(model.memberIds.containsAll(fields.map(LsiField::id) + constructors.map(LsiConstructor::id)))

        assertEquals("子类型文档。", child.documentation)
        assertTrue(child.origin.source?.path?.endsWith("demo/Models.java") == true)
        assertNotNull(child.location)
        assertEquals(LsiAnnotationUseSiteTarget.TYPE, child.annotations.single().useSiteTarget)

        val parentTypeParameter = parent.typeParameters.single()
        val parentBoundValue = parentTypeParameter.upperBounds.single()
        val parentBound = assertIs<LsiDeclaredType>(parentBoundValue, parentBoundValue.toString())
        assertEquals(LsiSymbolId.type("java.lang.Number"), parentBound.declarationId)
        val childSuperType = assertIs<LsiDeclaredType>(child.superTypes.single())
        assertEquals(parentId, childSuperType.declarationId)
        val childSuperArgument = childSuperType.arguments.single()
        assertEquals(LsiVariance.INVARIANT, childSuperArgument.variance)
        val childSuperArgumentType = assertIs<LsiPrimitiveType>(childSuperArgument.type)
        assertEquals(LsiPrimitiveKind.INT, childSuperArgumentType.kind)
        assertEquals(LsiNullability.PLATFORM, childSuperArgumentType.nullability)

        val parentDisplayName = workspace.requireProperty(parentId, "displayName")
        val childDisplayName = workspace.requireProperty(childId, "displayName")
        assertEquals("getDisplayName", childDisplayName.getterName)
        assertEquals(
            listOf(parentDisplayName.id to 1),
            childDisplayName.overrides.map { override -> override.declarationId to override.distance },
        )
        val parentDefault = parentDisplayName.annotation("test.lsi.Default")
        assertEquals(
            LsiAnnotationValue.StringValue("0"),
            parentDefault.arguments.getValue("value").value,
        )
        assertEquals(
            LsiAnnotationArgumentOrigin.EXPLICIT,
            parentDefault.arguments.getValue("value").origin,
        )
        val childDefault = childDisplayName.annotation("test.lsi.Default")
        assertEquals(
            LsiAnnotationValue.StringValue("1"),
            childDefault.arguments.getValue("value").value,
        )

        val parentSample = parentDisplayName.annotation("demo.Sample")
        assertTrue(parentSample.arguments.values.all { argument ->
            argument.origin == LsiAnnotationArgumentOrigin.DEFAULT
        })
        assertEquals(
            LsiAnnotationValue.BooleanValue(true),
            parentSample.arguments.getValue("enabled").value,
        )
        assertEquals(
            LsiAnnotationValue.ByteValue(1),
            parentSample.arguments.getValue("byteValue").value,
        )
        assertEquals(
            LsiAnnotationValue.ShortValue(2),
            parentSample.arguments.getValue("shortValue").value,
        )
        assertEquals(
            LsiAnnotationValue.EnumValue(LsiSymbolId.type("demo.Mode"), "FIRST"),
            parentSample.arguments.getValue("mode").value,
        )
        val parentClassValue = assertIs<LsiAnnotationValue.ClassValue>(
            parentSample.arguments.getValue("type").value,
        )
        assertEquals(
            LsiSymbolId.type("java.lang.String"),
            assertIs<LsiDeclaredType>(parentClassValue.type).declarationId,
        )
        val parentNested = assertIs<LsiAnnotationValue.NestedAnnotationValue>(
            parentSample.arguments.getValue("nested").value,
        )
        assertEquals(
            LsiAnnotationValue.StringValue("nested-default"),
            parentNested.annotation.arguments.getValue("value").value,
        )
        assertEquals(
            listOf(LsiAnnotationValue.IntValue(1), LsiAnnotationValue.IntValue(2)),
            assertIs<LsiAnnotationValue.ArrayValue>(
                parentSample.arguments.getValue("numbers").value,
            ).elements,
        )

        val childSample = childDisplayName.annotation("demo.Sample")
        assertTrue(childSample.arguments.values.all { argument ->
            argument.origin == LsiAnnotationArgumentOrigin.EXPLICIT
        })
        assertEquals(
            LsiAnnotationValue.StringValue("explicit"),
            childSample.arguments.getValue("text").value,
        )
        assertEquals(
            LsiAnnotationValue.EnumValue(LsiSymbolId.type("demo.Mode"), "SECOND"),
            childSample.arguments.getValue("mode").value,
        )
        assertTrue(
            childDisplayName.annotations.any { annotation ->
                annotation.type == LsiSymbolId.type("demo.ReturnTag") &&
                    annotation.useSiteTarget == LsiAnnotationUseSiteTarget.RETURN_TYPE
            },
        )
        assertTrue(
            childDisplayName.annotations.any { annotation ->
                annotation.type == LsiSymbolId.type("demo.Sample") &&
                    annotation.useSiteTarget == LsiAnnotationUseSiteTarget.METHOD
            },
        )

        val rootCode = workspace.requireProperty(rootId, "code")
        val parentCode = workspace.requireProperty(parentId, "code")
        val childCode = workspace.requireProperty(childId, "code")
        assertEquals(
            listOf(parentCode.id to 1, rootCode.id to 2),
            childCode.overrides.map { override -> override.declarationId to override.distance },
        )

        val title = workspace.requireProperty(childId, "title")
        assertEquals("title", title.getterName)
        val active = workspace.requireProperty(childId, "active")
        assertEquals("isActive", active.getterName)
        assertEquals(LsiPrimitiveKind.BOOLEAN, assertIs<LsiPrimitiveType>(active.type).kind)
        val version = workspace.declarationsOfType<LsiFunction>()
            .single { function -> function.ownerId == childId && function.name == "version" }
        assertTrue(version.static)

        val parentValue = workspace.requireProperty(parentId, "value")
        assertEquals(
            parentTypeParameter.id,
            assertIs<LsiTypeParameterRef>(parentValue.type).parameterId,
        )
        val childValue = workspace.requireProperty(childId, "value")
        assertEquals(parentValue.id, childValue.overrides.single().declarationId)

        val parentAccept = workspace.requireFunction(parentId, "accept")
        val childAccept = workspace.requireFunction(childId, "accept")
        assertEquals(parentAccept.id, childAccept.overrides.single().declarationId)
        val convert = workspace.requireFunction(parentId, "convert")
        val methodTypeParameter = convert.typeParameters.single()
        assertEquals(
            LsiSymbolId.type("java.lang.CharSequence"),
            assertIs<LsiDeclaredType>(methodTypeParameter.upperBounds.single()).declarationId,
        )
        val valuesType = assertIs<LsiDeclaredType>(convert.parameters[1].type)
        val outputArgument = valuesType.arguments.single()
        assertEquals(LsiVariance.OUT, outputArgument.variance)
        assertEquals(
            methodTypeParameter.id,
            assertIs<LsiTypeParameterRef>(outputArgument.type).parameterId,
        )

        val consume = workspace.requireFunction(childId, "consume")
        assertEquals(LsiPrimitiveKind.VOID, assertIs<LsiPrimitiveType>(consume.returnType).kind)
        val inputArgument = assertIs<LsiDeclaredType>(consume.parameters[0].type).arguments.single()
        assertEquals(LsiVariance.IN, inputArgument.variance)
        assertEquals(
            LsiSymbolId.type("java.lang.Number"),
            assertIs<LsiDeclaredType>(inputArgument.type).declarationId,
        )
        val codes = assertIs<LsiArrayType>(consume.parameters[1].type)
        assertEquals(LsiPrimitiveKind.INT, assertIs<LsiPrimitiveType>(codes.elementType).kind)

        val mode = assertIs<LsiTypeDeclaration>(workspace[LsiSymbolId.type("demo.Mode")])
        assertEquals(listOf("FIRST", "SECOND"), mode.enumEntries.map { entry -> entry.name })
        assertTrue(mode.enumEntries.all { entry -> workspace.contains(entry.id) })
    }

    @Test
    fun `freezes external mapped superclass and annotation semantic closure`() {
        val dependencyClasses = compileDependency(
            "external/SemanticMarker.java" to """
                package external;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.ANNOTATION_TYPE)
                public @interface SemanticMarker {}
            """.trimIndent(),
            "external/ValidationRule.java" to """
                package external;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @SemanticMarker
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.METHOD)
                public @interface ValidationRule {}
            """.trimIndent(),
            "external/ExternalBase.java" to """
                package external;

                import java.time.Instant;
                import test.lsi.MappedSuperclass;

                @MappedSuperclass
                public interface ExternalBase<T> {

                    @ValidationRule
                    T value();

                    Instant createdAt();
                }
            """.trimIndent(),
            "external/ExternalMiddle.java" to """
                package external;

                public interface ExternalMiddle<M> extends ExternalBase<M> {

                    String ignored();
                }
            """.trimIndent(),
            "external/ExternalValue.java" to """
                package external;

                import test.lsi.Embeddable;

                @Embeddable
                public interface ExternalValue {

                    String label();
                }
            """.trimIndent(),
        )
        val compilation = compile(
            "demo/LocalModel.java" to """
                package demo;

                import external.ExternalMiddle;
                import external.ExternalValue;
                import test.lsi.Entity;

                @Entity
                abstract class LocalModel implements ExternalMiddle<String> {

                    abstract ExternalValue payload();
                }
            """.trimIndent(),
            additionalClasspath = listOf(dependencyClasses),
        )

        assertTrue(compilation.success, compilation.diagnostics)
        val workspace = compilation.workspace
        val externalBaseId = LsiSymbolId.type("external.ExternalBase")
        val externalBase = assertIs<LsiTypeDeclaration>(workspace[externalBaseId])
        assertTrue(externalBase.annotations.any { annotation ->
            annotation.type == LsiSymbolId.type("test.lsi.MappedSuperclass")
        })
        val typeParameter = externalBase.typeParameters.single()
        assertEquals(
            typeParameter.id,
            assertIs<LsiTypeParameterRef>(workspace.requireProperty(externalBaseId, "value").type).parameterId,
        )
        val validationRule = assertIs<LsiTypeDeclaration>(
            workspace[LsiSymbolId.type("external.ValidationRule")],
        )
        assertTrue(validationRule.annotations.any { annotation ->
            annotation.type == LsiSymbolId.type("external.SemanticMarker")
        })
        assertIs<LsiTypeDeclaration>(workspace[LsiSymbolId.type("external.SemanticMarker")])
        val externalMiddleId = LsiSymbolId.type("external.ExternalMiddle")
        val externalMiddle = assertIs<LsiTypeDeclaration>(workspace[externalMiddleId])
        assertTrue(externalMiddle.memberIds.isEmpty())
        assertTrue(workspace.declarationsOfType<LsiProperty>().none { property ->
            property.ownerId == externalMiddleId
        })
        val externalValueId = LsiSymbolId.type("external.ExternalValue")
        val externalValue = assertIs<LsiTypeDeclaration>(workspace[externalValueId])
        assertTrue(externalValue.annotations.any { annotation ->
            annotation.type == LsiSymbolId.type("test.lsi.Embeddable")
        })
        assertEquals("label", workspace.requireProperty(externalValueId, "label").name)
        val instantId = LsiSymbolId.type("java.time.Instant")
        assertTrue(assertIs<LsiTypeDeclaration>(workspace[instantId]).memberIds.isEmpty())
        assertNotNull(workspace.typeHierarchyEntry(instantId))
        val objectId = LsiSymbolId.type("java.lang.Object")
        assertTrue(assertIs<LsiTypeDeclaration>(workspace[objectId]).memberIds.isEmpty())
        assertTrue(workspace.declarationsOfType<LsiProperty>().none { property ->
            property.ownerId == objectId && property.name == "class"
        })
    }

    @Test
    fun `freezes javac error type without platform leakage`() {
        val compilation = compile(
            "demo/Broken.java" to """
                package demo;

                import java.util.List;

                interface Broken {
                    MissingType missing();

                    List<MissingType> many();
                }
            """.trimIndent(),
        )

        assertFalse(compilation.success)
        val brokenId = LsiSymbolId.type("demo.Broken")
        val missing = compilation.workspace.requireProperty(brokenId, "missing")
        assertEquals("MissingType", assertIs<LsiUnresolvedType>(missing.type).displayName)
        val many = compilation.workspace.requireProperty(brokenId, "many")
        val missingArgument = assertIs<LsiDeclaredType>(many.type, many.type.toString()).arguments.single()
        assertIs<LsiUnresolvedType>(missingArgument.type)
        assertTrue(compilation.diagnostics.contains("MissingType"))
    }

    @Test
    fun `uses erased callable signatures for generic overloads`() {
        val compilation = compile(
            "demo/Factory.java" to """
                package demo;

                import java.util.List;

                public class Factory {
                    public <E> void of(E first, E second) {}
                    public <E> void of(E first, E... rest) {}
                    public <T extends Number> void convert(T value) {}
                    public <T extends CharSequence> void convert(T value) {}
                    public void raw(int value) {}
                    public void boxed(Integer value) {}
                    public void primitiveArray(int[] values) {}
                    public void boxedArray(Integer[] values) {}
                    public void generic(List<Integer> values) {}
                }
            """.trimIndent(),
        )

        assertTrue(compilation.success, compilation.diagnostics)
        val ownerId = LsiSymbolId.type("demo.Factory")
        val functions = compilation.workspace.declarationsOfType<LsiFunction>()
            .filter { function -> function.ownerId == ownerId }
        assertEquals(
            setOf(
                LsiSymbolId.function(
                    ownerId,
                    "of",
                    listOf(
                        "parameter:method:of:0:type:java.lang.Object",
                        "parameter:method:of:0:type:java.lang.Object",
                    ),
                ),
                LsiSymbolId.function(
                    ownerId,
                    "of",
                    listOf(
                        "parameter:method:of:0:type:java.lang.Object",
                        "array:parameter:method:of:0:type:java.lang.Object",
                    ),
                ),
                LsiSymbolId.function(
                    ownerId,
                    "convert",
                    listOf("parameter:method:convert:0:type:java.lang.Number"),
                ),
                LsiSymbolId.function(
                    ownerId,
                    "convert",
                    listOf("parameter:method:convert:0:type:java.lang.CharSequence"),
                ),
                LsiSymbolId.function(ownerId, "raw", listOf("primitive:int")),
                LsiSymbolId.function(ownerId, "boxed", listOf("type:java.lang.Integer")),
                LsiSymbolId.function(ownerId, "primitiveArray", listOf("array:primitive:int")),
                LsiSymbolId.function(ownerId, "boxedArray", listOf("array:type:java.lang.Integer")),
                LsiSymbolId.function(
                    ownerId,
                    "generic",
                    listOf("type:java.util.List<type:java.lang.Integer>"),
                ),
            ),
            functions.mapTo(linkedSetOf(), LsiFunction::id),
        )
    }

    private fun compile(
        vararg sources: Pair<String, String>,
        additionalClasspath: Collection<File> = emptyList(),
        compilerOptions: Map<String, String> = emptyMap(),
    ): CompilationResult {
        val projectDir = createTempDirectory(prefix = "lsi-apt-test").toFile()
        val sourceDir = projectDir.resolve("src/main/java")
        val classesDir = projectDir.resolve("build/classes")
        for ((path, content) in TEST_ANNOTATION_SOURCES + sources.toMap()) {
            val sourceFile = sourceDir.resolve(path)
            sourceFile.parentFile.mkdirs()
            sourceFile.writeText(content)
        }
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT integration tests require a JDK compiler")
        val processor = CapturingProcessor()
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
                buildList {
                    add("-proc:only")
                    add("-classpath")
                    add(classpath(additionalClasspath))
                    compilerOptions.toSortedMap().forEach { (name, value) ->
                        add("-A$name=$value")
                    }
                },
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(processor))
            task.call()
        }
        return CompilationResult(
            success = success,
            workspace = processor.workspaces.firstOrNull { workspace ->
                workspace.declarations.isNotEmpty() || workspace.annotationScopes.isNotEmpty()
            }
                ?: LsiWorkspace.EMPTY,
            diagnostics = diagnostics.toErrorMessage(),
            directType = processor.directType,
        )
    }

    private fun compileDependency(vararg sources: Pair<String, String>): File {
        val projectDir = createTempDirectory(prefix = "lsi-apt-dependency").toFile()
        val sourceDir = projectDir.resolve("src/main/java")
        val classesDir = projectDir.resolve("build/classes")
        for ((path, content) in TEST_ANNOTATION_SOURCES + sources.toMap()) {
            val sourceFile = sourceDir.resolve(path)
            sourceFile.parentFile.mkdirs()
            sourceFile.writeText(content)
        }
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT integration tests require a JDK compiler")
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
                    "-proc:none",
                    "-classpath",
                    classpath(emptyList()),
                ),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.call()
        }
        assertTrue(success, diagnostics.toErrorMessage())
        return classesDir
    }

    private fun classpath(additionalClasspath: Collection<File>): String {
        return buildList {
            add(System.getProperty("java.class.path"))
            additionalClasspath.mapTo(this, File::getAbsolutePath)
        }.joinToString(File.pathSeparator)
    }

    private fun LsiWorkspace.requireProperty(ownerId: LsiSymbolId, name: String): LsiProperty {
        return declarationsOfType<LsiProperty>()
            .single { property -> property.ownerId == ownerId && property.name == name }
    }

    private fun LsiWorkspace.requireFunction(ownerId: LsiSymbolId, name: String): LsiFunction {
        return declarationsOfType<LsiFunction>()
            .single { function -> function.ownerId == ownerId && function.name == name }
    }

    private fun LsiProperty.annotation(qualifiedName: String): site.addzero.lsi.model.LsiAnnotation {
        return annotations.single { annotation ->
            annotation.type == LsiSymbolId.type(qualifiedName) &&
                annotation.useSiteTarget == LsiAnnotationUseSiteTarget.METHOD
        }
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
        val workspace: LsiWorkspace,
        val diagnostics: String,
        val directType: LsiTypeRef?,
    )

    private class CapturingProcessor : AbstractProcessor() {
        val workspaces = mutableListOf<LsiWorkspace>()
        var directType: LsiTypeRef? = null

        override fun getSupportedAnnotationTypes(): Set<String> = setOf("*")

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

        override fun process(
            annotations: Set<TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean {
            if (!roundEnvironment.processingOver()) {
                directType = processingEnv.elementUtils
                    .getTypeElement("demo.DirectNullable")
                    ?.enclosedElements
                    ?.filterIsInstance<ExecutableElement>()
                    ?.singleOrNull { method -> method.simpleName.contentEquals("value") }
                    ?.returnType
                    ?.toLsiType(
                        processingEnv,
                        processingEnv.options.toTestLsiFrontendOptions(),
                    )
                workspaces += roundEnvironment.toLsiWorkspace(
                    processingEnv,
                    processingEnv.options.toTestLsiFrontendOptions(),
                )
            }
            return false
        }
    }
}

private const val TEST_KEEP_IS_PREFIX_OPTION = "lsi.test.keepIsPrefix"

private val TEST_NULLABLE_ANNOTATION_TYPE_ID = LsiSymbolId.type("test.lsi.TypeNullable")

private val TEST_MANAGED_TYPE_ANNOTATION_TYPE_IDS = setOf(
    LsiSymbolId.type("test.lsi.Entity"),
    LsiSymbolId.type("test.lsi.MappedSuperclass"),
    LsiSymbolId.type("test.lsi.Embeddable"),
)

private fun Map<String, String>.toTestLsiFrontendOptions(): LsiFrontendOptions {
    return LsiFrontendOptions(
        keepJavaBooleanGetterIsPrefix = this[TEST_KEEP_IS_PREFIX_OPTION] == "true",
        nullableAnnotationTypeIds = setOf(TEST_NULLABLE_ANNOTATION_TYPE_ID),
        fullExternalDeclarationAnnotationTypeIds = TEST_MANAGED_TYPE_ANNOTATION_TYPE_IDS,
        documentationConvention = LsiFrontendDocumentationConvention(
            annotationTypeId = LsiSymbolId.type("test.lsi.Description"),
            generatedPeer = LsiGeneratedPeerDocumentationConvention(
                ownerAnnotationTypeIds = TEST_MANAGED_TYPE_ANNOTATION_TYPE_IDS,
                typeSuffix = "Peer",
            ),
        ),
    )
}

private val TEST_ANNOTATION_SOURCES = mapOf(
    "test/lsi/Description.java" to """
        package test.lsi;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;

        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.TYPE, ElementType.METHOD})
        public @interface Description { String value(); }
    """.trimIndent(),
    "test/lsi/Entity.java" to """
        package test.lsi;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.TYPE)
        public @interface Entity {}
    """.trimIndent(),
    "test/lsi/Id.java" to """
        package test.lsi;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.METHOD)
        public @interface Id {}
    """.trimIndent(),
    "test/lsi/Default.java" to """
        package test.lsi;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.METHOD)
        public @interface Default { String value(); }
    """.trimIndent(),
    "test/lsi/MappedSuperclass.java" to """
        package test.lsi;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.TYPE)
        public @interface MappedSuperclass {}
    """.trimIndent(),
    "test/lsi/Embeddable.java" to """
        package test.lsi;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.TYPE)
        public @interface Embeddable {}
    """.trimIndent(),
    "test/lsi/TypeNullable.java" to """
        package test.lsi;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.TYPE_USE)
        public @interface TypeNullable {}
    """.trimIndent(),
)
