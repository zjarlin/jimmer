package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiVariance

class DtoTypeRefExtensionsTest {

    @Test
    fun `resolves builtins and generic variance for each target language`() {
        val listType = type(
            name = "List",
            arguments = listOf(argument(DtoVariance.INVARIANT, type("String"))),
        )
        val mutableListType = type(
            name = "MutableList",
            arguments = listOf(argument(DtoVariance.INVARIANT, type("String"))),
        )

        val javaList = listType.toLsiType(LsiLanguage.JAVA) as LsiDeclaredType
        val kotlinList = listType.toLsiType(LsiLanguage.KOTLIN) as LsiDeclaredType
        val javaMutableList = mutableListType.toLsiType(LsiLanguage.JAVA) as LsiDeclaredType

        assertEquals(LsiSymbolId.type("java.util.List"), javaList.declarationId)
        assertEquals(LsiVariance.OUT, javaList.arguments.single().variance)
        assertEquals(LsiSymbolId.type("kotlin.collections.List"), kotlinList.declarationId)
        assertEquals(LsiVariance.INVARIANT, kotlinList.arguments.single().variance)
        assertEquals(LsiSymbolId.type("java.util.List"), javaMutableList.declarationId)
        assertEquals(LsiVariance.INVARIANT, javaMutableList.arguments.single().variance)

        val qualifiedJavaList = type(
            name = "kotlin.collections.List",
            arguments = listOf(argument(DtoVariance.INVARIANT, type("String"))),
        ).toLsiType(LsiLanguage.JAVA) as LsiDeclaredType
        assertEquals(LsiSymbolId.type("java.util.List"), qualifiedJavaList.declarationId)
        assertEquals(LsiVariance.OUT, qualifiedJavaList.arguments.single().variance)

        val javaQualifiedJavaList = type(
            name = "java.util.List",
            arguments = listOf(argument(DtoVariance.INVARIANT, type("String"))),
        ).toLsiType(LsiLanguage.JAVA) as LsiDeclaredType
        assertEquals(LsiVariance.OUT, javaQualifiedJavaList.arguments.single().variance)

        val contravariant = type(
            name = "Comparator",
            arguments = listOf(argument(DtoVariance.IN, type("Int"))),
        ).toLsiType(LsiLanguage.JAVA) as LsiDeclaredType
        val argumentType = contravariant.arguments.single().type as LsiPrimitiveType
        assertEquals(LsiVariance.IN, contravariant.arguments.single().variance)
        assertTrue(argumentType.boxed)
    }

    @Test
    fun `preserves array shape nullability and exact custom type identity`() {
        val primitiveArray = type(
            name = "Array",
            arguments = listOf(argument(DtoVariance.INVARIANT, type("Int"))),
        ).toLsiType(LsiLanguage.KOTLIN) as LsiArrayType
        val nullablePrimitive = type("Int", nullable = true)
            .toLsiType(LsiLanguage.JAVA) as LsiPrimitiveType
        val customType = type("demo.Outer.Inner")
            .toLsiType(LsiLanguage.KOTLIN) as LsiDeclaredType

        assertEquals(LsiPrimitiveKind.INT, (primitiveArray.elementType as LsiPrimitiveType).kind)
        assertEquals(LsiPrimitiveKind.INT, nullablePrimitive.kind)
        assertTrue(nullablePrimitive.boxed)
        assertEquals(LsiSymbolId.type("demo.Outer.Inner"), customType.declarationId)

        val javaStarArray = starArray().toLsiType(LsiLanguage.JAVA) as LsiArrayType
        val kotlinStarArray = starArray().toLsiType(LsiLanguage.KOTLIN) as LsiDeclaredType
        assertEquals(
            LsiSymbolId.type("java.lang.Object"),
            (javaStarArray.elementType as LsiDeclaredType).declarationId,
        )
        assertEquals(LsiSymbolId.type("kotlin.Array"), kotlinStarArray.declarationId)
        assertEquals(LsiVariance.STAR, kotlinStarArray.arguments.single().variance)
        assertFalse((primitiveArray.elementType as LsiPrimitiveType).boxed)
    }

    @Test
    fun `rejects an unknown target language`() {
        assertFailsWith<IllegalArgumentException> {
            type("String").toLsiType(LsiLanguage.UNKNOWN)
        }
        assertFailsWith<IllegalArgumentException> {
            type("Array").toLsiType(LsiLanguage.JAVA)
        }
        assertFailsWith<IllegalArgumentException> {
            type("Int", arguments = listOf(argument(DtoVariance.INVARIANT, type("String"))))
                .toLsiType(LsiLanguage.JAVA)
        }
        assertFailsWith<IllegalArgumentException> {
            type("List").toLsiType(LsiLanguage.KOTLIN)
        }
        assertFailsWith<IllegalArgumentException> {
            type("Map", arguments = listOf(argument(DtoVariance.INVARIANT, type("String"))))
                .toLsiType(LsiLanguage.JAVA)
        }
    }

    @Test
    fun `allows raw builtin type refs for annotation class literals`() {
        val rawList = type("List")
        val classLiteral = DtoAnnotationValue.TypeValue(rawList)

        assertEquals(rawList, classLiteral.type)
        assertFailsWith<IllegalArgumentException> {
            rawList.toLsiType(LsiLanguage.KOTLIN)
        }
    }

    @Test
    fun `resolves kotlin user property default values from frozen types`() {
        assertEquals("explicit()", userProp(type("demo.Value"), "explicit()").kotlinDefaultValueTextOrNull())
        assertEquals("null", userProp(type("demo.Value", nullable = true)).kotlinDefaultValueTextOrNull())
        assertEquals("false", userProp(type("Boolean")).kotlinDefaultValueTextOrNull())
        assertEquals("intArrayOf()", userProp(arrayOf(type("Int"))).kotlinDefaultValueTextOrNull())
        assertEquals("emptyArray()", userProp(arrayOf(type("Int", nullable = true))).kotlinDefaultValueTextOrNull())
        assertEquals("emptyArray<Any?>()", userProp(starArray()).kotlinDefaultValueTextOrNull())
        assertEquals("emptyList<Any?>()", userProp(collectionOf("List", null)).kotlinDefaultValueTextOrNull())
        assertEquals("mutableListOf<Any?>()", userProp(collectionOf("MutableList", null)).kotlinDefaultValueTextOrNull())
        assertEquals("emptySet()", userProp(collectionOf("Set", type("String"))).kotlinDefaultValueTextOrNull())
        assertEquals("mutableSetOf()", userProp(collectionOf("MutableSet", type("String"))).kotlinDefaultValueTextOrNull())
        assertEquals(
            "emptyMap()",
            userProp(mapOf("Map", type("String"), type("Int"))).kotlinDefaultValueTextOrNull(),
        )
        assertEquals(
            "mutableMapOf()",
            userProp(mapOf("MutableMap", type("String"), type("Int"))).kotlinDefaultValueTextOrNull(),
        )
        assertEquals(null, userProp(type("demo.Value")).kotlinDefaultValueTextOrNull())
    }

    private fun userProp(
        type: DtoTypeRef,
        defaultValueText: String? = null,
    ): DtoUserProp = DtoUserProp(
        id = DtoPropId("demo.Types.value"),
        ownerTypeId = DtoTypeId("demo.Types"),
        name = "value",
        alias = "value",
        nullable = type.nullable,
        annotations = emptyList(),
        documentation = null,
        aliasLocation = LOCATION,
        type = type,
        defaultValueText = defaultValueText,
    )

    private fun arrayOf(componentType: DtoTypeRef): DtoTypeRef = type(
        name = "Array",
        arguments = listOf(argument(DtoVariance.INVARIANT, componentType)),
    )

    private fun collectionOf(name: String, elementType: DtoTypeRef?): DtoTypeRef = type(
        name = name,
        arguments = listOf(
            argument(
                variance = if (elementType == null) DtoVariance.STAR else DtoVariance.INVARIANT,
                type = elementType,
            ),
        ),
    )

    private fun mapOf(
        name: String,
        keyType: DtoTypeRef,
        valueType: DtoTypeRef,
    ): DtoTypeRef = type(
        name = name,
        arguments = listOf(
            argument(DtoVariance.INVARIANT, keyType),
            argument(DtoVariance.INVARIANT, valueType),
        ),
    )

    private fun starArray(): DtoTypeRef = type(
        name = "Array",
        arguments = listOf(argument(DtoVariance.STAR, null)),
    )

    private fun type(
        name: String,
        arguments: List<DtoTypeArgument> = emptyList(),
        nullable: Boolean = false,
    ): DtoTypeRef = DtoTypeRef(
        typeName = name,
        arguments = arguments,
        nullable = nullable,
        location = LOCATION,
    )

    private fun argument(
        variance: DtoVariance,
        type: DtoTypeRef?,
    ): DtoTypeArgument = DtoTypeArgument(
        variance = variance,
        type = type,
    )

    private companion object {
        val LOCATION = LsiLocation(
            source = LsiSource.of("demo/src/main/dto/Types.dto"),
            start = LsiPosition(1, 1),
        )
    }
}
