package site.addzero.lsi.poet.javapoet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.poet.LsiPoetArtifact
import site.addzero.lsi.poet.LsiPoetCodeBlock
import site.addzero.lsi.poet.LsiPoetConstructor
import site.addzero.lsi.poet.LsiPoetField
import site.addzero.lsi.poet.LsiPoetFunction
import site.addzero.lsi.poet.LsiPoetMember
import site.addzero.lsi.poet.LsiPoetModifier
import site.addzero.lsi.poet.LsiPoetParameter
import site.addzero.lsi.poet.LsiPoetProperty
import site.addzero.lsi.poet.LsiPoetType
import site.addzero.lsi.poet.LsiPoetTypeKind
import site.addzero.lsi.poet.LsiPoetFile

class LsiJavaPoetRendererTest {

    private val stringType = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))

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
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
            originatingSources = setOf(LsiSource.of("demo/Source.java", LsiLanguage.JAVA)),
        )

        val generated = LsiJavaPoetRenderer().render(artifact)

        assertEquals(GeneratedArtifact::class.java, LsiJavaPoetRenderer::class.java
            .getDeclaredMethod("render", LsiPoetArtifact::class.java).returnType)
        assertPublicApiDoesNotExposePoet(LsiJavaPoetRenderer::class.java)
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

    private fun artifact(member: LsiPoetMember, fileName: String): LsiPoetArtifact {
        val type = if (member is LsiPoetType) member else {
            LsiPoetType(fileName, LsiPoetTypeKind.CLASS, members = listOf(member))
        }
        return LsiPoetArtifact(
            file = LsiPoetFile(
                language = LsiLanguage.JAVA,
                packageName = "demo.generated",
                fileName = fileName,
                members = listOf(type),
            ),
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )
    }

    private fun assertPublicApiDoesNotExposePoet(type: Class<*>) {
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

        assertTrue(exposedTypes.none { exposedType -> exposedType.name.startsWith("com.squareup.") })
    }
}
