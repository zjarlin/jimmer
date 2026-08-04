package site.addzero.lsi.poet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class LsiPoetTypeNameTest {

    @Test
    fun `使用显式边界创建顶层生成类型名`() {
        assertEquals(
            LsiPoetTypeName(
                typeId = LsiSymbolId.type("Demo.API.Result"),
                packageName = "Demo.API",
                simpleNames = listOf("Result"),
            ),
            generatedTopLevelPoetTypeName("Demo.API", "Result"),
        )
        assertEquals(
            LsiPoetTypeName(
                typeId = LsiSymbolId.type("Result"),
                packageName = "",
                simpleNames = listOf("Result"),
            ),
            generatedTopLevelPoetTypeName("", "Result"),
        )
    }

    @Test
    fun `保留大写包和小写嵌套类型的精确边界`() {
        val typeName = LsiPoetTypeName(
            typeId = LsiSymbolId.type("Demo.API.order.item"),
            packageName = "Demo.API",
            simpleNames = listOf("order", "item"),
        )

        assertEquals("Demo.API.order.item", typeName.canonicalName)
    }

    @Test
    fun `保留需要 Kotlin 转义的源码类型名`() {
        val typeName = LsiPoetTypeName(
            typeId = LsiSymbolId.type("demo.escaped.Order-Item.Draft Impl"),
            packageName = "demo.escaped",
            simpleNames = listOf("Order-Item", "Draft Impl"),
        )

        assertEquals("demo.escaped.Order-Item.Draft Impl", typeName.canonicalName)
    }

    @Test
    fun `拒绝与稳定类型身份不一致的源码名`() {
        assertFailsWith<IllegalArgumentException> {
            LsiPoetTypeName(
                typeId = LsiSymbolId.type("demo.order.item"),
                packageName = "demo.order",
                simpleNames = listOf("Item"),
            )
        }
    }

    @Test
    fun `从声明嵌套链解析并合并显式生成类型名`() {
        val outerId = LsiSymbolId.type("Demo.API.order")
        val nestedId = LsiSymbolId.type("Demo.API.order.item")
        val generatedId = LsiSymbolId.type("Generated.Package.result.row")
        val workspace = LsiWorkspace(
            declarations = listOf(
                typeDeclaration(outerId, "order"),
                typeDeclaration(nestedId, "item", outerId),
            ),
        )
        val generated = LsiPoetTypeName(
            typeId = generatedId,
            packageName = "Generated.Package",
            simpleNames = listOf("result", "row"),
        )

        assertEquals(
            listOf(
                LsiPoetTypeName(nestedId, "Demo.API", listOf("order", "item")),
                generated,
            ),
            workspace.toLsiPoetTypeNames(
                typeIds = listOf(nestedId, generatedId),
                additional = listOf(generated),
            ),
        )
    }

    @Test
    fun `从冻结嵌套声明派生同级生成类型名`() {
        val outerId = LsiSymbolId.type("Demo.API.order")
        val nestedId = LsiSymbolId.type("Demo.API.order.item")
        val generatedId = LsiSymbolId.type("Demo.API.order.itemTable.Remote")
        val workspace = LsiWorkspace(
            declarations = listOf(
                typeDeclaration(outerId, "order"),
                typeDeclaration(nestedId, "item", outerId),
            ),
        )

        assertEquals(
            LsiPoetTypeName(
                typeId = generatedId,
                packageName = "Demo.API",
                simpleNames = listOf("order", "itemTable", "Remote"),
            ),
            workspace.generatedSiblingPoetTypeName(
                sourceTypeId = nestedId,
                generatedTypeId = generatedId,
                simpleNameSuffix = "Table",
                nestedSimpleNames = listOf("Remote"),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            workspace.generatedSiblingPoetTypeName(
                sourceTypeId = nestedId,
                generatedTypeId = generatedId,
                simpleNameSuffix = "Table.Remote",
            )
        }
    }

    @Test
    fun `忽略未请求的显式名称并拒绝与声明边界冲突`() {
        val typeId = LsiSymbolId.type("demo.order.item")
        val workspace = LsiWorkspace(
            declarations = listOf(typeDeclaration(typeId, "item")),
        )
        val conflicting = LsiPoetTypeName(
            typeId = typeId,
            packageName = "demo",
            simpleNames = listOf("order", "item"),
        )
        val unused = LsiPoetTypeName(
            typeId = LsiSymbolId.type("generated.value"),
            packageName = "generated",
            simpleNames = listOf("value"),
        )

        assertEquals(emptyList(), LsiWorkspace.EMPTY.toLsiPoetTypeNames(emptyList(), listOf(unused)))
        assertFailsWith<IllegalArgumentException> {
            workspace.toLsiPoetTypeNames(listOf(typeId), listOf(conflicting))
        }
        assertFailsWith<IllegalArgumentException> {
            LsiWorkspace.EMPTY.toLsiPoetTypeNames(emptyList(), listOf(unused, unused))
        }
    }

    @Test
    fun `声明缺失时不回退到类型标识字符串拆分`() {
        assertFailsWith<IllegalArgumentException> {
            LsiWorkspace.EMPTY.toLsiPoetTypeNames(
                listOf(LsiSymbolId.type("demo.missing.type")),
            )
        }
    }

    @Test
    fun `拒绝限定名与 enclosing 链不一致的声明`() {
        val outerId = LsiSymbolId.type("other.order")
        val nestedId = LsiSymbolId.type("demo.order.item")
        val workspace = LsiWorkspace(
            declarations = listOf(
                typeDeclaration(outerId, "order"),
                typeDeclaration(nestedId, "item", outerId),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            workspace.toLsiPoetTypeNames(listOf(nestedId))
        }
    }

    @Test
    fun `源码产物拒绝重复和缺失类型名`() {
        val referencedId = LsiSymbolId.type("demo.value")
        val typeName = LsiPoetTypeName(referencedId, "demo", listOf("value"))
        val file = LsiPoetFile(
            language = LsiLanguage.KOTLIN,
            packageName = "demo.generated",
            fileName = "Result",
            members = listOf(
                LsiPoetFunction(
                    name = "value",
                    returnType = LsiDeclaredType(referencedId),
                )
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            LsiPoetArtifact(
                file = file,
                typeNames = emptyList(),
                aggregationMode = ArtifactAggregationMode.AGGREGATING,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LsiPoetArtifact(
                file = file,
                typeNames = listOf(typeName, typeName),
                aggregationMode = ArtifactAggregationMode.AGGREGATING,
            )
        }
    }

    private fun typeDeclaration(
        id: LsiSymbolId,
        name: String,
        enclosingTypeId: LsiSymbolId? = null,
    ): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = id,
            name = name,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.CLASS,
            enclosingTypeId = enclosingTypeId,
            origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
        )
    }
}
