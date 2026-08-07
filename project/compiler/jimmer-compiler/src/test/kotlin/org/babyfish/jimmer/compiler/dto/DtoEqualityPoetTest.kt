package org.babyfish.jimmer.compiler.dto

import kotlin.test.Test
import kotlin.test.assertContains
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoModifier
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.jimmer.dto.DtoPropId
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeArgument
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoTypeRef
import site.addzero.lsi.jimmer.dto.DtoUserProp
import site.addzero.lsi.jimmer.dto.DtoVariance
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class DtoEqualityPoetTest {

    @Test
    fun `renders empty DTO object methods`() {
        val graph = graph()
        val type = graph.types.single()
        val typeName = JimmerDtoPoetTypeNames.create("demo.dto", listOf("EmptyInput"))

        val javaHash = LsiJavaPoetRenderer().renderFunction(
            type.toDtoHashCodePoetFunction(graph, SCHEMA, LsiLanguage.JAVA),
            dtoEqualityPoetTypeNames(),
        ).toString()
        val javaEquals = LsiJavaPoetRenderer().renderFunction(
            type.toDtoEqualsPoetFunction(graph, SCHEMA, LsiLanguage.JAVA, typeName),
            dtoEqualityPoetTypeNames(typeName),
        ).toString()
        val kotlinHash = LsiKotlinPoetRenderer().renderFunction(
            type.toDtoHashCodePoetFunction(graph, SCHEMA, LsiLanguage.KOTLIN),
            dtoEqualityPoetTypeNames(),
        ).toString()
        val kotlinEquals = LsiKotlinPoetRenderer().renderFunction(
            type.toDtoEqualsPoetFunction(graph, SCHEMA, LsiLanguage.KOTLIN, typeName),
            dtoEqualityPoetTypeNames(typeName),
        ).toString()

        assertContains(javaHash, "return 0;")
        assertContains(javaEquals, "return true;")
        assertContains(kotlinHash, "hashCode(): kotlin.Int = 0")
        assertContains(kotlinEquals, "return true")
    }

    @Test
    fun `renders content arrays exact classes and collision-free local names`() {
        val graph = graph(
            userProp("hash", scalarType("Int")),
            userProp("_hash", scalarType("Int")),
            userProp("o", scalarType("String")),
            userProp("other", scalarType("String")),
            userProp("_other", scalarType("String")),
            userProp("javaClass", scalarType("String")),
            userProp("when", arrayType("Int")),
            userProp("starArray", starArrayType()),
        )
        val type = graph.types.single()
        val typeName = JimmerDtoPoetTypeNames.create("demo.dto", listOf("CollisionInput"))

        val javaHash = LsiJavaPoetRenderer().renderFunction(
            type.toDtoHashCodePoetFunction(graph, SCHEMA, LsiLanguage.JAVA),
            dtoEqualityPoetTypeNames(),
        ).toString()
        val javaEquals = LsiJavaPoetRenderer().renderFunction(
            type.toDtoEqualsPoetFunction(graph, SCHEMA, LsiLanguage.JAVA, typeName),
            dtoEqualityPoetTypeNames(typeName),
        ).toString()
        val kotlinHash = LsiKotlinPoetRenderer().renderFunction(
            type.toDtoHashCodePoetFunction(graph, SCHEMA, LsiLanguage.KOTLIN),
            dtoEqualityPoetTypeNames(),
        ).toString()
        val kotlinEquals = LsiKotlinPoetRenderer().renderFunction(
            type.toDtoEqualsPoetFunction(graph, SCHEMA, LsiLanguage.KOTLIN, typeName),
            dtoEqualityPoetTypeNames(typeName),
        ).toString()

        assertContains(javaHash, "int hash_2 = java.util.Objects.hashCode(this.hash);")
        assertContains(javaHash, "java.util.Arrays.hashCode(this.when)")
        assertContains(javaHash, "java.util.Arrays.hashCode(this.starArray)")
        assertContains(javaEquals, "boolean equals(java.lang.Object o_2)")
        assertContains(
            javaEquals,
            "demo.dto.CollisionInput other_2 = (demo.dto.CollisionInput) o_2;",
        )
        assertContains(javaEquals, "java.util.Arrays.equals(this.when, other_2.when)")
        assertContains(javaEquals, "java.util.Arrays.equals(this.starArray, other_2.starArray)")

        assertContains(kotlinHash, "var _hash_2 = java.util.Objects.hashCode(this.hash)")
        assertContains(kotlinHash, "java.util.Arrays.hashCode(this.`when`)")
        assertContains(kotlinHash, "java.util.Arrays.hashCode(this.starArray)")
        assertContains(kotlinEquals, "equals(other_2: kotlin.Any?)")
        assertContains(kotlinEquals, "this::class != other_2::class")
        assertContains(kotlinEquals, "val _other_2 = other_2 as demo.dto.CollisionInput")
        assertContains(kotlinEquals, "java.util.Arrays.equals(this.`when`, _other_2.`when`)")
        assertContains(kotlinEquals, "java.util.Arrays.equals(this.starArray, _other_2.starArray)")
    }

    private fun graph(vararg props: DtoUserProp): DtoGraph {
        val type = DtoType(
            id = TYPE_ID,
            baseTypeId = BASE_TYPE_ID,
            packageName = "demo.dto",
            name = "CollisionInput",
            modifiers = setOf(DtoModifier.INPUT),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = props.map(DtoProp::id),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        return DtoGraph(
            source = SOURCE,
            rootTypeIds = listOf(TYPE_ID),
            types = listOf(type),
            props = props.sortedBy(DtoProp::id),
        )
    }

    private fun userProp(
        name: String,
        type: DtoTypeRef,
    ): DtoUserProp {
        return DtoUserProp(
            id = DtoPropId("dto#$name"),
            ownerTypeId = TYPE_ID,
            name = name,
            alias = name,
            nullable = type.nullable,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = LOCATION,
            type = type,
            defaultValueText = null,
        )
    }

    private fun scalarType(name: String): DtoTypeRef {
        return DtoTypeRef(name, emptyList(), nullable = false, location = LOCATION)
    }

    private fun arrayType(elementName: String): DtoTypeRef {
        return DtoTypeRef(
            typeName = "Array",
            arguments = listOf(
                DtoTypeArgument(
                    variance = DtoVariance.INVARIANT,
                    type = scalarType(elementName),
                ),
            ),
            nullable = false,
            location = LOCATION,
        )
    }

    private fun starArrayType(): DtoTypeRef {
        return DtoTypeRef(
            typeName = "Array",
            arguments = listOf(DtoTypeArgument(DtoVariance.STAR, type = null)),
            nullable = false,
            location = LOCATION,
        )
    }

    private companion object {
        val SOURCE = LsiSource.of("src/main/dto/demo/Collision.dto", LsiLanguage.KOTLIN)
        val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1), LsiPosition(1, 1))
        val TYPE_ID = DtoTypeId("demo.dto.CollisionInput")
        val BASE_TYPE_ID = LsiSymbolId.type("demo.Sample")
        val SCHEMA = ImmutableSchema(emptyList())
    }
}
